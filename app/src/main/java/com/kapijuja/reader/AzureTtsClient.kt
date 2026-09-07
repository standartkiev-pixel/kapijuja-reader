package com.kapijuja.reader

import android.content.Context
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale

object AzureTtsClient {
    // The bug report showed occasional connection aborts while reading ~2.4k-char
    // responses. Smaller chunks are more reliable on mobile networks and are also
    // fast enough to prebuffer while the previous chunk is playing.
    private const val MAX_TEXT_CHARS = 900

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
        require(SettingsStore.isValidAzureRegion(region)) {
            "Azure region должен быть точным идентификатором, например switzerlandnorth"
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
            output.write(
                synthesizeChunkWithRetry(
                    speechKey = speechKey,
                    region = region,
                    text = chunk,
                    voice = voice,
                    speed = speed,
                    context = context,
                    index = index,
                    count = chunks.size
                )
            )
        }

        return output.toByteArray()
    }

    private fun synthesizeChunkWithRetry(
        speechKey: String,
        region: String,
        text: String,
        voice: String,
        speed: Float,
        context: Context?,
        index: Int,
        count: Int
    ): ByteArray {
        var last: Throwable? = null

        for (attempt in 1..3) {
            try {
                AppDiagnostics.info(
                    context,
                    "Azure TTS request: region=$region voice=$voice chars=${text.length} chunk=${index + 1}/$count attempt=$attempt"
                )

                return synthesizeOnce(
                    speechKey = speechKey,
                    region = region,
                    text = text,
                    voice = voice,
                    speed = speed,
                    context = context
                )
            } catch (t: Throwable) {
                last = t

                if (
                    attempt < 3 &&
                    isRetryable(t)
                ) {
                    AppDiagnostics.error(
                        context,
                        "Azure transient failure; retry $attempt/3",
                        t
                    )
                    Thread.sleep(450L * attempt)
                    continue
                }

                throw t
            }
        }

        throw IllegalStateException(
            "Azure Speech: ${last?.message ?: "неизвестная ошибка"}",
            last
        )
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
            URL(endpoint)
                .openConnection() as HttpURLConnection

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

            // Collapse all line/paragraph whitespace into one space. Azure still
            // receives punctuation, but paragraph markup can no longer create a
            // multi-second dramatic pause by itself.
            val normalizedText =
                normalizeForSpeech(text)

            val ssml =
                "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>" +
                    "<voice name='${escapeXml(voice)}'>" +
                    "<prosody rate='$rate'>" +
                    escapeXml(normalizedText) +
                    "</prosody></voice></speak>"

            connection.outputStream.use {
                it.write(
                    ssml.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val code =
                connection.responseCode

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
        require(SettingsStore.isValidAzureRegion(region)) {
            "Azure region должен быть точным идентификатором, например switzerlandnorth"
        }

        val endpoint =
            "https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list"

        val connection =
            URL(endpoint)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Ocp-Apim-Subscription-Key",
                speechKey
            )

            val code =
                connection.responseCode

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
        val value =
            normalizeForSpeech(text)

        if (
            value.length <= maxChars
        ) {
            return listOf(value)
        }

        val result =
            mutableListOf<String>()
        var cursor = 0

        while (
            cursor < value.length
        ) {
            var end =
                minOf(
                    cursor + maxChars,
                    value.length
                )

            if (
                end < value.length
            ) {
                val sentence =
                    value.lastIndexOfAny(
                        charArrayOf(
                            '.',
                            '!',
                            '?'
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

            if (
                chunk.isNotBlank()
            ) {
                result += chunk
            }

            cursor = end
        }

        return result
    }

    private fun normalizeForSpeech(
        value: String
    ): String =
        value
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

    private fun isRetryable(
        error: Throwable
    ): Boolean {
        var current: Throwable? =
            error

        while (
            current != null
        ) {
            if (
                current is
                    UnknownHostException ||
                current is
                    SocketTimeoutException ||
                current is
                    SocketException
            ) {
                return true
            }

            val message =
                current.message
                    ?.lowercase(Locale.US)
                    .orEmpty()

            if (
                message.contains(
                    "software caused connection abort"
                ) ||
                message.contains(
                    "connection reset"
                ) ||
                message.contains(
                    "broken pipe"
                ) ||
                message.contains(
                    "timeout"
                )
            ) {
                return true
            }

            current =
                current.cause
        }

        return false
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
