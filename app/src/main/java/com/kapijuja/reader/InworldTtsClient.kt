package com.kapijuja.reader

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object InworldTtsClient {
    private const val PROVIDER = "Inworld"
    private const val ENDPOINT = "https://api.inworld.ai/tts/v1/voice"
    private const val VOICES_ENDPOINT = "https://api.inworld.ai/tts/v1/voices"

    const val DEFAULT_MODEL = "inworld-tts-2"
    const val DEFAULT_VOICE = "Ashley"

    const val KEY_URL = "https://platform.inworld.ai/api-keys"
    const val BILLING_URL = "https://platform.inworld.ai/billing"

    // The synchronous endpoint accepts up to 2,000 UTF-16 code units.
    private const val MAX_REQUEST_CHARS = 1_800

    val models = listOf(
        VoiceChoice("inworld-tts-2", "TTS-2 — лучшее произношение"),
        VoiceChoice("inworld-tts-2-flash", "TTS-2 Flash — быстрее и дешевле")
    )

    val fallbackVoices = listOf(
        VoiceChoice(DEFAULT_VOICE, "Ashley — женский")
    )

    /** Published on-demand list price, USD per 1M characters. Display estimate only. */
    fun priceUsdPerMillionChars(model: String): Double =
        if (model.contains("flash")) 15.0 else 25.0

    fun synthesize(
        apiKey: String,
        model: String,
        text: String,
        voice: String,
        speed: Float = 1.0f,
        language: String = SettingsStore.SPEECH_LANGUAGE_AUTO,
        context: Context? = null
    ): ByteArray {
        require(apiKey.isNotBlank()) { "Inworld API key не указан в настройках" }
        require(text.isNotBlank()) { "Пустой текст" }
        require(voice.isNotBlank()) { "Inworld: голос не выбран" }

        val parts = OpenAiTtsClient.splitForApi(text, MAX_REQUEST_CHARS)
        AppDiagnostics.info(
            context,
            "Inworld TTS request: model=$model voice=$voice chars=${text.length} parts=${parts.size} speed=$speed lang=$language"
        )

        return CloudHttp.joinMp3(
            parts.map { part ->
                CloudHttp.withConnectionRetry(context, PROVIDER) {
                    synthesizeOnce(apiKey, model, part, voice, speed, language, context)
                }
            }
        )
    }

    private fun synthesizeOnce(
        apiKey: String,
        model: String,
        text: String,
        voice: String,
        speed: Float,
        language: String,
        context: Context?
    ): ByteArray {
        val body = JSONObject()
            .put("text", text)
            .put("voiceId", voice)
            .put("modelId", model)
            .put(
                "audioConfig",
                JSONObject()
                    .put("audioEncoding", "MP3")
                    .put("bitRate", 128_000)
                    .put("speakingRate", speed.toDouble().coerceIn(0.5, 1.5))
            )
        when (language) {
            SettingsStore.SPEECH_LANGUAGE_RU -> body.put("language", "ru-RU")
            SettingsStore.SPEECH_LANGUAGE_UK -> body.put("language", "uk-UA")
        }

        val response =
            CloudHttp.request("POST", ENDPOINT, authHeaders(apiKey), body.toString())
        AppDiagnostics.info(context, "Inworld TTS HTTP ${response.code}")
        if (!response.ok) throw IllegalStateException(describeError(response))

        val encoded = JSONObject(response.text()).optString("audioContent")
        if (encoded.isBlank()) {
            throw IllegalStateException("Inworld не вернул аудио в ответе")
        }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.size < 128) {
            throw IllegalStateException(
                "Inworld вернул слишком короткий аудиофайл (${bytes.size} байт)"
            )
        }
        return bytes
    }

    /** Downloads the voices available to this workspace, Russian/Ukrainian first. */
    fun fetchVoices(apiKey: String, context: Context? = null): List<VoiceChoice> {
        require(apiKey.isNotBlank()) { "Inworld API key не указан" }

        val response =
            CloudHttp.withConnectionRetry(context, PROVIDER) {
                CloudHttp.request(
                    "GET",
                    VOICES_ENDPOINT,
                    authHeaders(apiKey),
                    readTimeoutMs = 30_000
                )
            }
        AppDiagnostics.info(context, "Inworld voices HTTP ${response.code}")
        if (!response.ok) throw IllegalStateException(describeError(response))

        val voices = JSONObject(response.text()).optJSONArray("voices") ?: JSONArray()
        val rows = mutableListOf<Pair<Boolean, VoiceChoice>>()
        for (i in 0 until voices.length()) {
            val voice = voices.optJSONObject(i) ?: continue
            val id = voice.optString("voiceId")
            if (id.isBlank()) continue

            val languages = stringList(voice.optJSONArray("languages")).map { it.lowercase().take(2) }
            val slavic = listOf("ru", "uk").filter { it in languages }
            val tags = stringList(voice.optJSONArray("tags")).map {
                when (it.lowercase()) {
                    "male" -> "мужской"
                    "female" -> "женский"
                    else -> it
                }
            }
            val details =
                listOfNotNull(
                    (if (slavic.isNotEmpty()) slavic else languages.take(1))
                        .joinToString("/").uppercase().takeIf { it.isNotBlank() }
                ) + tags.take(3)

            val name = voice.optString("displayName").ifBlank { id }
            val label = if (details.isEmpty()) name else "$name — ${details.joinToString(", ")}"
            rows += slavic.isNotEmpty() to VoiceChoice(id, label)
        }

        return rows
            .sortedWith(compareByDescending<Pair<Boolean, VoiceChoice>> { it.first }.thenBy { it.second.label.lowercase() })
            .map { it.second }
            .distinctBy { it.id }
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    // The Portal shows a ready-to-use "Basic (Base64)" credential. Accept it
    // with or without the "Basic " prefix so a pasted header value also works.
    private fun authHeaders(apiKey: String): Map<String, String> {
        val credential =
            apiKey.trim().let {
                if (it.startsWith("Basic ", ignoreCase = true)) it.substring(6).trim() else it
            }
        return mapOf(
            "Authorization" to "Basic $credential",
            "Accept" to "application/json"
        )
    }

    private fun describeError(response: CloudHttp.Response): String {
        val raw = response.text()
        val detail =
            runCatching { JSONObject(raw).optString("message") }
                .getOrDefault("")
                .ifBlank { raw }
                .take(300)

        return when (response.code) {
            401 ->
                "Inworld: API key отклонён (HTTP 401). Скопируйте значение Basic (Base64) из раздела API Keys. $detail"
            402 ->
                "Inworld: на счёте закончились кредиты — пополните баланс (HTTP 402). $detail"
            403 ->
                "Inworld: доступ запрещён или не оплачен (HTTP 403). $detail"
            429 ->
                "Inworld: слишком много запросов или исчерпан лимит (HTTP 429). $detail"
            in 500..599 ->
                "Inworld: временная серверная ошибка HTTP ${response.code}"
            else ->
                "Inworld HTTP ${response.code}: $detail"
        }
    }
}
