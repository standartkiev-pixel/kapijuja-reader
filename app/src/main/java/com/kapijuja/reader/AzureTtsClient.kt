package com.kapijuja.reader

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object AzureTtsClient {
    private const val MAX_TEXT_CHARS = 2500

    fun synthesize(
        speechKey: String,
        region: String,
        text: String,
        voice: String,
        speed: Float = 1.0f,
        context: Context? = null
    ): ByteArray {
        require(speechKey.isNotBlank()) {
            "Azure Speech key не указан"
        }
        require(region.matches(Regex("[a-z0-9-]+"))) {
            "Azure region не указан или содержит недопустимые символы"
        }
        require(text.isNotBlank()) {
            "Пустой текст"
        }
        require(voice.isNotBlank()) {
            "Azure voice не выбран"
        }

        val chunks = splitForApi(text)
        val output = java.io.ByteArrayOutputStream()

        chunks.forEachIndexed { index, chunk ->
            AppDiagnostics.info(
                context,
                "Azure TTS request: region=$region voice=$voice chars=${chunk.length} chunk=${index + 1}/${chunks.size}"
            )
            output.write(
                synthesizeOnce(
                    speechKey = speechKey,
                    region = region,
                    text = chunk,
                    voice = voice,
                    speed = speed,
                    context = context
                )
            )
        }

        return output.toByteArray()
    }

    private fun synthesizeOnce(
        speechKey: String,
        region: String,
        text: String,
        voice: String,
        speed: Float,
        context: Context?
    ): ByteArray {
        val endpoint =
            "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"

        val connection =
            URL(endpoint).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty(
                "Ocp-Apim-Subscription-Key",
                speechKey
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/ssml+xml"
            )
            connection.setRequestProperty(
                "X-Microsoft-OutputFormat",
                "audio-24khz-48kbitrate-mono-mp3"
            )
            connection.setRequestProperty(
                "User-Agent",
                "KapijujaReader/0.1"
            )
            connection.setRequestProperty(
                "Accept",
                "audio/mpeg"
            )

            val rate = ratePercent(speed)
            val ssml =
                "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>" +
                    "<voice name='${escapeXml(voice)}'>" +
                    "<prosody rate='$rate'>" +
                    escapeXml(text) +
                    "</prosody></voice></speak>"

            connection.outputStream.use {
                it.write(
                    ssml.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val code = connection.responseCode
            AppDiagnostics.info(
                context,
                "Azure TTS HTTP $code"
            )

            if (code !in 200..299) {
                val error =
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                throw IllegalStateException(
                    when (code) {
                        401, 403 ->
                            "Azure: ключ/регион отклонены (HTTP $code)"
                        429 ->
                            "Azure: достигнут F0/rate limit (HTTP 429)"
                        in 500..599 ->
                            "Azure: временная серверная ошибка HTTP $code"
                        else ->
                            "Azure HTTP $code: ${error.take(300)}"
                    }
                )
            }

            val bytes =
                connection.inputStream
                    .use { it.readBytes() }

            if (bytes.size < 256) {
                throw IllegalStateException(
                    "Azure вернул слишком короткий аудиофайл (${bytes.size} байт)"
                )
            }

            AppDiagnostics.info(
                context,
                "Azure audio received: ${bytes.size} bytes"
            )
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    fun checkAccess(
        speechKey: String,
        region: String,
        context: Context? = null
    ): String {
        require(speechKey.isNotBlank()) {
            "Azure Speech key не указан"
        }
        require(region.matches(Regex("[a-z0-9-]+"))) {
            "Azure region не указан"
        }

        val endpoint =
            "https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list"
        val connection =
            URL(endpoint).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Ocp-Apim-Subscription-Key",
                speechKey
            )

            val code = connection.responseCode
            AppDiagnostics.info(
                context,
                "Azure test HTTP $code"
            )

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Azure test HTTP $code"
                )
            }

            val body =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            return if (
                body.contains(
                    "ru-RU-DmitryNeural"
                )
            ) {
                "Azure Speech доступен; русский Dmitry Neural найден."
            } else {
                "Azure Speech доступен; список голосов получен."
            }
        } finally {
            connection.disconnect()
        }
    }

    fun splitForApi(
        text: String,
        maxChars: Int = MAX_TEXT_CHARS
    ): List<String> {
        val value = text.trim()
        if (value.length <= maxChars) {
            return listOf(value)
        }

        val result =
            mutableListOf<String>()
        var cursor = 0

        while (cursor < value.length) {
            var end =
                minOf(
                    cursor + maxChars,
                    value.length
                )

            if (end < value.length) {
                val sentence =
                    value.lastIndexOfAny(
                        charArrayOf(
                            '.',
                            '!',
                            '?',
                            '\n'
                        ),
                        end - 1
                    )

                val space =
                    value.lastIndexOf(
                        ' ',
                        end - 1
                    )

                end =
                    when {
                        sentence >
                            cursor +
                            maxChars / 2 ->
                            sentence + 1

                        space >
                            cursor +
                            maxChars / 2 ->
                            space + 1

                        else ->
                            end
                    }
            }

            val chunk =
                value.substring(
                    cursor,
                    end
                ).trim()

            if (chunk.isNotBlank()) {
                result += chunk
            }

            cursor = end
        }

        return result
    }

    private fun ratePercent(
        speed: Float
    ): String {
        val percent =
            (
                (
                    speed
                        .coerceIn(
                            0.5f,
                            2.0f
                        ) -
                        1f
                    ) *
                    100f
                )
                .toInt()
                .coerceIn(
                    -50,
                    100
                )

        return if (percent >= 0) {
            "+$percent%"
        } else {
            "$percent%"
        }
    }

    private fun escapeXml(
        value: String
    ): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
            .replace("\"", "&quot;")
}
