package com.kapijuja.reader

import android.content.Context
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object OpenAiTtsClient {
    private const val ENDPOINT = "https://api.openai.com/v1/audio/speech"
    private const val MODEL_ENDPOINT = "https://api.openai.com/v1/models/gpt-4o-mini-tts"
    private const val MODEL = "gpt-4o-mini-tts"

    fun synthesize(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        speed: Float = 1.0f,
        context: Context? = null
    ): ByteArray {
        require(apiKey.isNotBlank()) { "OpenAI API key не указан в настройках" }
        require(text.isNotBlank()) { "Пустой текст" }

        AppDiagnostics.info(
            context,
            "OpenAI TTS request: model=$MODEL voice=$voice chars=${text.length} speed=$speed"
        )

        return withConnectionRetry(context, "OpenAI TTS") {
            synthesizeOnce(apiKey, text, voice, instructions, speed, context)
        }
    }

    fun checkAccess(apiKey: String, context: Context? = null): String {
        require(apiKey.isNotBlank()) { "OpenAI API key не указан" }

        return withConnectionRetry(context, "OpenAI connection test") {
            val connection = URL(MODEL_ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Accept", "application/json")

                val code = connection.responseCode
                AppDiagnostics.info(context, "OpenAI test HTTP $code")
                when (code) {
                    200 -> "OpenAI доступен. DNS, интернет и API key работают."
                    401 -> throw IllegalStateException("OpenAI: API key отклонён (HTTP 401)")
                    403 -> throw IllegalStateException("OpenAI: доступ запрещён для этого ключа/проекта (HTTP 403)")
                    429 -> throw IllegalStateException("OpenAI: достигнут лимит или rate limit (HTTP 429)")
                    else -> {
                        val error = readError(connection)
                        throw IllegalStateException("OpenAI HTTP $code: ${error.take(300)}")
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun synthesizeOnce(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        speed: Float,
        context: Context?
    ): ByteArray {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "audio/mpeg")

            val body = JSONObject()
                .put("model", MODEL)
                .put("voice", voice)
                .put("input", text.take(4000))
                .put("response_format", "mp3")
                .put("speed", speed.toDouble().coerceIn(0.25, 4.0))

            if (instructions.isNotBlank()) body.put("instructions", instructions)

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            AppDiagnostics.info(context, "OpenAI TTS HTTP $code")
            if (code !in 200..299) {
                val errorText = readError(connection)
                throw IllegalStateException(
                    when (code) {
                        401 -> "OpenAI: неверный или недействующий API key"
                        403 -> "OpenAI: ключ не имеет доступа к TTS"
                        429 -> "OpenAI: достигнут денежный/rate limit. Запрос остановлен."
                        in 500..599 -> "OpenAI: временная серверная ошибка HTTP $code"
                        else -> "OpenAI HTTP $code: ${errorText.take(300)}"
                    }
                )
            }

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size < 128) {
                throw IllegalStateException("OpenAI вернул слишком короткий аудиофайл (${bytes.size} байт)")
            }
            AppDiagnostics.info(context, "OpenAI audio received: ${bytes.size} bytes")
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun readError(connection: HttpURLConnection): String =
        connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun <T> withConnectionRetry(
        context: Context?,
        operation: String,
        block: () -> T
    ): T {
        var last: Throwable? = null
        for (attempt in 1..3) {
            try {
                return block()
            } catch (e: UnknownHostException) {
                last = e
                AppDiagnostics.error(context, "$operation DNS attempt $attempt/3", e)
                if (attempt < 3) Thread.sleep(650L * attempt)
            } catch (e: ConnectException) {
                last = e
                AppDiagnostics.error(context, "$operation connect attempt $attempt/3", e)
                if (attempt < 3) Thread.sleep(650L * attempt)
            } catch (e: SocketTimeoutException) {
                AppDiagnostics.error(context, "$operation timeout", e)
                throw IllegalStateException(
                    "OpenAI: сервер не ответил вовремя. Повторите запрос; автоматический повтор отключён, чтобы исключить двойную оплату.",
                    e
                )
            }
        }
        throw IllegalStateException(
            "OpenAI: не удалось разрешить/соединиться с api.openai.com после 3 попыток. Проверьте интернет или DNS и попробуйте ещё раз.",
            last
        )
    }

    fun estimatedMinutes(text: String): Double =
        (text.length.coerceAtLeast(1) / 900.0).coerceAtLeast(0.05)

    fun estimatedCostEuro(text: String): Double =
        estimatedMinutes(text) * 0.013

    fun splitForApi(text: String, maxChars: Int = 3600): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            var end = minOf(cursor + maxChars, text.length)
            if (end < text.length) {
                val sentence = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), end)
                val space = text.lastIndexOf(' ', end)
                end = when {
                    sentence > cursor + maxChars / 2 -> sentence + 1
                    space > cursor + maxChars / 2 -> space + 1
                    else -> end
                }
            }
            result += text.substring(cursor, end).trim()
            cursor = end
        }
        return result.filter { it.isNotBlank() }
    }

    fun stripLeadingId3(bytes: ByteArray): ByteArray {
        if (bytes.size < 10 || bytes[0] != 'I'.code.toByte() ||
            bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()
        ) return bytes
        val size = ((bytes[6].toInt() and 0x7f) shl 21) or
            ((bytes[7].toInt() and 0x7f) shl 14) or
            ((bytes[8].toInt() and 0x7f) shl 7) or
            (bytes[9].toInt() and 0x7f)
        val offset = (10 + size).coerceAtMost(bytes.size)
        return bytes.copyOfRange(offset, bytes.size)
    }
}
