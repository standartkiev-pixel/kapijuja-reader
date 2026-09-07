package com.kapijuja.reader

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object GoogleGeminiTtsClient {
    private const val MODEL =
        "gemini-2.5-flash-preview-tts"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val MODEL_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL"
    const val PCM_SAMPLE_RATE = 24_000
    const val PCM_CHANNELS = 1
    const val PCM_BITS_PER_SAMPLE = 16

    private const val SAMPLE_RATE = PCM_SAMPLE_RATE
    private const val CHANNELS = PCM_CHANNELS
    private const val BITS_PER_SAMPLE = PCM_BITS_PER_SAMPLE
    private const val MAX_CHARS = 1_200

    fun synthesizeWav(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        context: Context? = null
    ): ByteArray =
        pcmToWav(
            synthesizePcm(
                apiKey = apiKey,
                text = text,
                voice = voice,
                instructions = instructions,
                context = context
            )
        )

    fun synthesizePcm(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        context: Context? = null
    ): ByteArray {
        require(apiKey.isNotBlank()) {
            "Google Gemini API key не указан"
        }
        require(text.isNotBlank()) {
            "Пустой текст"
        }
        require(voice.isNotBlank()) {
            "Google voice не выбран"
        }

        val chunks = splitForApi(text)
        val output = ByteArrayOutputStream()

        chunks.forEachIndexed { index, chunk ->
            val bytes =
                synthesizeChunkWithRetry(
                    apiKey = apiKey,
                    text = chunk,
                    voice = voice,
                    instructions = instructions,
                    context = context,
                    index = index,
                    count = chunks.size
                )
            output.write(bytes)
        }

        return output.toByteArray()
    }

    private fun synthesizeChunkWithRetry(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        context: Context?,
        index: Int,
        count: Int
    ): ByteArray {
        var last: Throwable? = null

        for (attempt in 1..3) {
            try {
                AppDiagnostics.info(
                    context,
                    "Google Gemini TTS request: voice=$voice chars=${text.length} chunk=${index + 1}/$count attempt=$attempt"
                )

                return synthesizeOnce(
                    apiKey = apiKey,
                    text = text,
                    voice = voice,
                    instructions = instructions,
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
                        "Google Gemini transient failure; retry $attempt/3",
                        t
                    )
                    Thread.sleep(450L * attempt)
                    continue
                }

                throw t
            }
        }

        throw IllegalStateException(
            "Google Gemini TTS: ${last?.message ?: "неизвестная ошибка"}",
            last
        )
    }

    private fun synthesizeOnce(
        apiKey: String,
        text: String,
        voice: String,
        instructions: String,
        context: Context?
    ): ByteArray {
        val connection =
            URL(ENDPOINT)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty(
                "x-goog-api-key",
                apiKey
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val prompt =
                buildString {
                    if (
                        instructions.isNotBlank()
                    ) {
                        append(
                            instructions.trim()
                        )
                        append("\n\n")
                    }

                    append(
                        "Read exactly the following Russian text. " +
                            "Do not add, omit, explain, or summarize anything. " +
                            "Keep pauses between sentences short and natural.\n\n"
                    )
                    append(
                        normalizeForSpeech(text)
                    )
                }

            val body =
                JSONObject()
                    .put(
                        "contents",
                        org.json.JSONArray()
                            .put(
                                JSONObject()
                                    .put(
                                        "parts",
                                        org.json.JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put(
                                                        "text",
                                                        prompt
                                                    )
                                            )
                                    )
                            )
                    )
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put(
                                "responseModalities",
                                org.json.JSONArray()
                                    .put("AUDIO")
                            )
                            .put(
                                "speechConfig",
                                JSONObject()
                                    .put(
                                        "languageCode",
                                        "ru-RU"
                                    )
                                    .put(
                                        "voiceConfig",
                                        JSONObject()
                                            .put(
                                                "prebuiltVoiceConfig",
                                                JSONObject()
                                                    .put(
                                                        "voiceName",
                                                        voice
                                                    )
                                            )
                                    )
                            )
                    )

            connection.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val code =
                connection.responseCode

            AppDiagnostics.info(
                context,
                "Google Gemini TTS HTTP $code"
            )

            if (code !in 200..299) {
                val error =
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                throw IllegalStateException(
                    when (code) {
                        400 ->
                            "Google Gemini: запрос отклонён (HTTP 400): ${error.take(250)}"
                        401, 403 ->
                            "Google Gemini: API key отклонён или API недоступен (HTTP $code)"
                        429 ->
                            "Google Gemini: достигнут free-tier/rate limit (HTTP 429)"
                        in 500..599 ->
                            "Google Gemini: временная серверная ошибка HTTP $code"
                        else ->
                            "Google Gemini HTTP $code: ${error.take(250)}"
                    }
                )
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            val root =
                JSONObject(response)

            val candidates =
                root.optJSONArray(
                    "candidates"
                )
                    ?: error(
                        "Google Gemini не вернул candidates"
                    )

            if (candidates.length() == 0) {
                error(
                    "Google Gemini не вернул аудио"
                )
            }

            val parts =
                candidates
                    .getJSONObject(0)
                    .getJSONObject(
                        "content"
                    )
                    .getJSONArray(
                        "parts"
                    )

            var base64: String? = null
            var mime = ""

            for (
                i in 0 until parts.length()
            ) {
                val inline =
                    parts
                        .getJSONObject(i)
                        .optJSONObject(
                            "inlineData"
                        )
                        ?: continue

                base64 =
                    inline.optString(
                        "data",
                        ""
                    )
                mime =
                    inline.optString(
                        "mimeType",
                        ""
                    )

                if (
                    !base64.isNullOrBlank()
                ) {
                    break
                }
            }

            if (
                base64.isNullOrBlank()
            ) {
                error(
                    "Google Gemini ответил без audio inlineData"
                )
            }

            val pcm =
                Base64.decode(
                    base64,
                    Base64.DEFAULT
                )

            if (pcm.size < 256) {
                error(
                    "Google Gemini вернул слишком короткое аудио (${pcm.size} байт)"
                )
            }

            AppDiagnostics.info(
                context,
                "Google Gemini audio received: ${pcm.size} PCM bytes mime=$mime"
            )

            return pcm
        } finally {
            connection.disconnect()
        }
    }

    fun checkAccess(
        apiKey: String,
        context: Context? = null
    ): String {
        require(apiKey.isNotBlank()) {
            "Google Gemini API key не указан"
        }

        val connection =
            URL(MODEL_ENDPOINT)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "x-goog-api-key",
                apiKey
            )

            val code =
                connection.responseCode

            AppDiagnostics.info(
                context,
                "Google Gemini test HTTP $code"
            )

            if (code !in 200..299) {
                throw IllegalStateException(
                    when (code) {
                        401, 403 ->
                            "Google Gemini API key отклонён (HTTP $code)"
                        429 ->
                            "Google Gemini: достигнут rate limit (HTTP 429)"
                        else ->
                            "Google Gemini test HTTP $code"
                    }
                )
            }

            return "Google Gemini 2.5 Flash TTS доступен."
        } finally {
            connection.disconnect()
        }
    }

    fun splitForApi(
        text: String,
        maxChars: Int = MAX_CHARS
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

    fun pcmToWav(
        pcm: ByteArray
    ): ByteArray {
        val byteRate =
            SAMPLE_RATE *
                CHANNELS *
                BITS_PER_SAMPLE /
                8

        val blockAlign =
            CHANNELS *
                BITS_PER_SAMPLE /
                8

        val output =
            ByteArrayOutputStream(
                pcm.size + 44
            )

        fun writeAscii(
            value: String
        ) {
            output.write(
                value.toByteArray(
                    Charsets.US_ASCII
                )
            )
        }

        fun writeLeInt(
            value: Int
        ) {
            output.write(
                byteArrayOf(
                    (value and 0xff)
                        .toByte(),
                    (
                        value shr 8 and
                            0xff
                        )
                        .toByte(),
                    (
                        value shr 16 and
                            0xff
                        )
                        .toByte(),
                    (
                        value shr 24 and
                            0xff
                        )
                        .toByte()
                )
            )
        }

        fun writeLeShort(
            value: Int
        ) {
            output.write(
                byteArrayOf(
                    (value and 0xff)
                        .toByte(),
                    (
                        value shr 8 and
                            0xff
                        )
                        .toByte()
                )
            )
        }

        writeAscii("RIFF")
        writeLeInt(36 + pcm.size)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeLeInt(16)
        writeLeShort(1)
        writeLeShort(CHANNELS)
        writeLeInt(SAMPLE_RATE)
        writeLeInt(byteRate)
        writeLeShort(blockAlign)
        writeLeShort(BITS_PER_SAMPLE)
        writeAscii("data")
        writeLeInt(pcm.size)
        output.write(pcm)

        return output.toByteArray()
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
                    ?.lowercase()
                    .orEmpty()

            if (
                message.contains(
                    "temporary"
                ) ||
                message.contains(
                    "connection reset"
                ) ||
                message.contains(
                    "connection abort"
                )
            ) {
                return true
            }

            current =
                current.cause
        }

        return false
    }
}
