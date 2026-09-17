package com.kapijuja.reader

import android.content.Context
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object XaiTtsClient {
    private const val ENDPOINT = "https://api.x.ai/v1/tts"
    private const val VOICES_ENDPOINT = "https://api.x.ai/v1/tts/voices"

    // xAI unary/server-streamed TTS currently accepts up to 60,000 characters.
    // Keep export chunks much smaller for responsiveness, but expose the hard
    // limit so Reader chunk planning can avoid cutting an open wrapping tag.
    const val MAX_REQUEST_CHARS = 60_000
    private const val DEFAULT_EXPORT_CHARS = 12_000
    private const val PRICE_USD_PER_MILLION_CHARS = 15.0

    // Display-only estimate for the existing euro cost guard. xAI bills in USD.
    // Keep this conservative and refresh it when provider pricing UX is revisited.
    private const val DISPLAY_USD_TO_EUR = 0.87

    fun synthesize(
        apiKey: String,
        text: String,
        voice: String,
        speed: Float = 1.0f,
        context: Context? = null
    ): ByteArray {
        require(apiKey.isNotBlank()) { "xAI API key не указан в настройках" }
        require(text.isNotBlank()) { "Пустой текст" }
        require(voice.isNotBlank()) { "xAI voice не выбран" }
        require(text.length <= MAX_REQUEST_CHARS) {
            "xAI TTS: фрагмент слишком длинный (${text.length}; максимум $MAX_REQUEST_CHARS символов)"
        }

        AppDiagnostics.info(
            context,
            "xAI TTS request: voice=$voice chars=${text.length} speed=$speed"
        )

        return withConnectionRetry(context, "xAI TTS") {
            synthesizeOnce(
                apiKey = apiKey,
                text = text,
                voice = voice,
                speed = speed,
                context = context
            )
        }
    }

    fun checkAccess(
        apiKey: String,
        context: Context? = null
    ): String {
        require(apiKey.isNotBlank()) { "xAI API key не указан" }

        return withConnectionRetry(context, "xAI connection test") {
            val connection = URL(VOICES_ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 12_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Accept", "application/json")

                val code = connection.responseCode
                AppDiagnostics.info(context, "xAI voices test HTTP $code")

                when (code) {
                    200 -> {
                        val payload =
                            connection.inputStream.bufferedReader().use { it.readText() }
                        val count =
                            runCatching {
                                JSONObject(payload)
                                    .optJSONArray("voices")
                                    ?.length()
                                    ?: 0
                            }.getOrDefault(0)
                        "xAI Grok TTS доступен. API key работает; голосов получено: $count."
                    }
                    401 -> throw IllegalStateException("xAI: API key отклонён (HTTP 401)")
                    403 -> throw IllegalStateException("xAI: доступ запрещён для этого ключа/проекта (HTTP 403)")
                    429 -> throw IllegalStateException("xAI: достигнут rate/billing limit (HTTP 429)")
                    else -> {
                        val error = readError(connection)
                        throw IllegalStateException("xAI HTTP $code: ${error.take(400)}")
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
        speed: Float,
        context: Context?
    ): ByteArray {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "audio/mpeg")

            val body = JSONObject()
                .put("text", text)
                .put("voice_id", voice)
                .put("language", "ru")
                .put("speed", speed.toDouble().coerceIn(0.7, 1.5))
                .put("text_normalization", true)
                .put(
                    "output_format",
                    JSONObject()
                        .put("codec", "mp3")
                        .put("sample_rate", 24_000)
                        .put("bit_rate", 128_000)
                )

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            AppDiagnostics.info(context, "xAI TTS HTTP $code")

            if (code !in 200..299) {
                val errorText = readError(connection)
                throw IllegalStateException(
                    when (code) {
                        400 -> "xAI: запрос TTS отклонён. Проверьте голос/текст. ${errorText.take(240)}"
                        401 -> "xAI: неверный или недействующий API key"
                        403 -> "xAI: ключ не имеет доступа к TTS или billing"
                        429 -> "xAI: достигнут rate/billing limit. Запрос остановлен."
                        in 500..599 -> "xAI: временная серверная ошибка HTTP $code"
                        else -> "xAI HTTP $code: ${errorText.take(300)}"
                    }
                )
            }

            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size < 128) {
                throw IllegalStateException("xAI вернул слишком короткий аудиофайл (${bytes.size} байт)")
            }

            AppDiagnostics.info(context, "xAI audio received: ${bytes.size} bytes")
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    fun estimatedCostUsd(text: String): Double =
        text.length.coerceAtLeast(1) * PRICE_USD_PER_MILLION_CHARS / 1_000_000.0

    fun estimatedCostEuro(text: String): Double =
        estimatedCostUsd(text) * DISPLAY_USD_TO_EUR

    fun splitForApi(
        text: String,
        maxChars: Int = DEFAULT_EXPORT_CHARS
    ): List<String> {
        require(maxChars in 200..MAX_REQUEST_CHARS)
        if (text.isBlank()) return emptyList()
        if (text.length <= maxChars) return listOf(text.trim())

        val result = mutableListOf<String>()
        var cursor = 0

        while (cursor < text.length) {
            val hardEnd = minOf(cursor + MAX_REQUEST_CHARS, text.length)
            var end = minOf(cursor + maxChars, hardEnd)

            if (end < text.length) {
                val sentence =
                    text.lastIndexOfAny(
                        charArrayOf('.', '!', '?', '\n'),
                        end
                    )
                val space = text.lastIndexOf(' ', end)
                end = when {
                    sentence > cursor + maxChars / 2 -> sentence + 1
                    space > cursor + maxChars / 2 -> space + 1
                    else -> end
                }
            }

            end =
                GrokEditorMarkup.balancedChunkEnd(
                    text = text,
                    start = cursor,
                    preferredEnd = end,
                    hardEnd = hardEnd
                )

            require(end > cursor) {
                "xAI TTS: не удалось безопасно разбить текст на фрагменты"
            }

            result += text.substring(cursor, end).trim()
            cursor = end
        }

        return result.filter { it.isNotBlank() }
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
            } catch (error: UnknownHostException) {
                last = error
                AppDiagnostics.error(context, "$operation DNS attempt $attempt/3", error)
                if (attempt < 3) Thread.sleep(650L * attempt)
            } catch (error: ConnectException) {
                last = error
                AppDiagnostics.error(context, "$operation connect attempt $attempt/3", error)
                if (attempt < 3) Thread.sleep(650L * attempt)
            } catch (error: SocketTimeoutException) {
                AppDiagnostics.error(context, "$operation timeout", error)
                throw IllegalStateException(
                    "xAI: сервер не ответил вовремя. Повторите запрос вручную; автоматический повтор отключён, чтобы исключить двойную оплату.",
                    error
                )
            }
        }

        throw IllegalStateException(
            "xAI: не удалось соединиться с api.x.ai после 3 попыток. Проверьте интернет/DNS и попробуйте ещё раз.",
            last
        )
    }
}
