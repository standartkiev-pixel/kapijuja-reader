package com.kapijuja.reader

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenAiTtsClient {
    private const val ENDPOINT = "https://api.openai.com/v1/audio/speech"
    private const val MODEL = "gpt-4o-mini-tts"

    fun synthesize(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        speed: Float = 1.0f
    ): ByteArray {
        require(apiKey.isNotBlank()) { "OpenAI API key не указан в настройках" }
        require(text.isNotBlank()) { "Пустой текст" }

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
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
        if (code !in 200..299) {
            val errorText = connection.errorStream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            throw IllegalStateException(
                when (code) {
                    401 -> "OpenAI: неверный или недействующий API key"
                    429 -> "OpenAI: достигнут лимит или слишком много запросов"
                    else -> "OpenAI HTTP $code: ${errorText.take(300)}"
                }
            )
        }
        return connection.inputStream.use { it.readBytes() }
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
