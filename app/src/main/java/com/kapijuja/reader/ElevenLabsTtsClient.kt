package com.kapijuja.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ElevenLabsTtsClient {
    private const val PROVIDER = "ElevenLabs"
    private const val BASE = "https://api.elevenlabs.io"

    const val DEFAULT_MODEL = "eleven_v3"

    // "George" from the ElevenLabs default voice library. The full list of the
    // account's voices is downloaded with fetchVoices().
    const val DEFAULT_VOICE = "JBFqnCBsd6RMkjVDRZzb"

    const val KEY_URL = "https://elevenlabs.io/app/settings/api-keys"
    // Pay-as-you-go "Top Up" lives in the Developers settings.
    const val BILLING_URL = "https://elevenlabs.io/app/developers"

    val models = listOf(
        VoiceChoice("eleven_v3", "Eleven v3 — самый выразительный"),
        VoiceChoice("eleven_v4", "Eleven v4 — новый, выразительный и быстрый"),
        VoiceChoice("eleven_multilingual_v2", "Multilingual v2 — ровный, для длинных текстов"),
        VoiceChoice("eleven_v4_turbo", "Eleven v4 Turbo — вдвое дешевле"),
        VoiceChoice("eleven_flash_v2_5", "Flash v2.5 — вдвое дешевле, проще")
    )

    val fallbackVoices = listOf(
        VoiceChoice(DEFAULT_VOICE, "George — мужской, тёплый"),
        VoiceChoice("nPczCjzI2devNBz1zQrb", "Brian — мужской, низкий"),
        VoiceChoice("onwK4e9ZLuTAKqWW03F9", "Daniel — мужской, дикторский"),
        VoiceChoice("EXAVITQu4vr4xnSDxMaL", "Sarah — женский, мягкий"),
        VoiceChoice("Xb7hH8MSUJpSbSDYk0k2", "Alice — женский, чёткий")
    )

    /** Published API list price, USD per 1M characters. Display estimate only. */
    fun priceUsdPerMillionChars(model: String): Double =
        if (model.contains("flash") || model.contains("turbo")) 40.0 else 80.0

    /** Conservative per-request limit; documented limits are higher. */
    fun maxRequestChars(model: String): Int =
        if (model == "eleven_v3") 4_500 else 9_000

    fun synthesize(
        apiKey: String,
        model: String,
        text: String,
        voice: String,
        speed: Float = 1.0f,
        language: String = SettingsStore.SPEECH_LANGUAGE_AUTO,
        context: Context? = null
    ): ByteArray {
        require(apiKey.isNotBlank()) { "ElevenLabs API key не указан в настройках" }
        require(text.isNotBlank()) { "Пустой текст" }
        require(voice.isNotBlank()) { "ElevenLabs: голос не выбран" }

        val parts = OpenAiTtsClient.splitForApi(text, maxRequestChars(model))
        AppDiagnostics.info(
            context,
            "ElevenLabs TTS request: model=$model voice=$voice chars=${text.length} parts=${parts.size} speed=$speed lang=$language"
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
        val url =
            "$BASE/v1/text-to-speech/" +
                java.net.URLEncoder.encode(voice, "UTF-8") +
                "?output_format=mp3_44100_128"
        val headers = mapOf("xi-api-key" to apiKey, "Accept" to "audio/mpeg")

        fun body(withSpeed: Boolean): String {
            val json = JSONObject()
                .put("text", text)
                .put("model_id", model)
            // language_code is documented as unsupported for multilingual_v2.
            if (
                language != SettingsStore.SPEECH_LANGUAGE_AUTO &&
                model != "eleven_multilingual_v2"
            ) {
                json.put("language_code", language)
            }
            if (withSpeed) {
                json.put(
                    "voice_settings",
                    JSONObject().put("speed", speed.toDouble().coerceIn(0.7, 1.2))
                )
            }
            return json.toString()
        }

        val wantsSpeed = kotlin.math.abs(speed - 1.0f) > 0.01f
        var response = CloudHttp.request("POST", url, headers, body(wantsSpeed))

        // Not every model accepts the speed setting. A rejected request is not
        // billed, so it is safe to repeat it once without the optional setting.
        if (wantsSpeed && (response.code == 400 || response.code == 422)) {
            AppDiagnostics.info(
                context,
                "ElevenLabs rejected speed for model=$model (HTTP ${response.code}); retrying without it"
            )
            response = CloudHttp.request("POST", url, headers, body(false))
        }

        AppDiagnostics.info(context, "ElevenLabs TTS HTTP ${response.code}")
        if (!response.ok) throw IllegalStateException(describeError(response))

        if (response.body.size < 128) {
            throw IllegalStateException(
                "ElevenLabs вернул слишком короткий аудиофайл (${response.body.size} байт)"
            )
        }
        return response.body
    }

    /** Downloads the voices available to this account, Russian/Ukrainian first. */
    fun fetchVoices(apiKey: String, context: Context? = null): List<VoiceChoice> {
        require(apiKey.isNotBlank()) { "ElevenLabs API key не указан" }

        val rows = mutableListOf<Pair<Boolean, VoiceChoice>>()
        var pageToken: String? = null

        for (page in 1..3) {
            val url =
                "$BASE/v2/voices?page_size=100" +
                    (pageToken?.let { "&next_page_token=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
            val response =
                CloudHttp.withConnectionRetry(context, PROVIDER) {
                    CloudHttp.request(
                        "GET",
                        url,
                        mapOf("xi-api-key" to apiKey, "Accept" to "application/json"),
                        readTimeoutMs = 30_000
                    )
                }
            AppDiagnostics.info(context, "ElevenLabs voices HTTP ${response.code}")
            if (!response.ok) throw IllegalStateException(describeError(response))

            val json = JSONObject(response.text())
            val voices = json.optJSONArray("voices") ?: JSONArray()
            for (i in 0 until voices.length()) {
                parseVoice(voices.optJSONObject(i) ?: continue)?.let { rows += it }
            }

            pageToken = json.optString("next_page_token").takeIf {
                json.optBoolean("has_more") && it.isNotBlank() && it != "null"
            }
            if (pageToken == null) break
        }

        return rows
            .sortedWith(compareByDescending<Pair<Boolean, VoiceChoice>> { it.first }.thenBy { it.second.label.lowercase() })
            .map { it.second }
            .distinctBy { it.id }
    }

    private fun parseVoice(voice: JSONObject): Pair<Boolean, VoiceChoice>? {
        val id = voice.optString("voice_id")
        if (id.isBlank()) return null

        val labels = voice.optJSONObject("labels")
        val languages = mutableSetOf<String>()
        labels?.optString("language")?.takeIf { it.isNotBlank() }?.let { languages += it.lowercase() }
        voice.optJSONArray("verified_languages")?.let { verified ->
            for (i in 0 until verified.length()) {
                verified.optJSONObject(i)?.optString("language")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { languages += it.lowercase() }
            }
        }

        val slavic = listOf("ru", "uk").filter { it in languages }
        val details =
            listOfNotNull(
                slavic.joinToString("/").uppercase().takeIf { it.isNotBlank() },
                genderLabel(labels?.optString("gender").orEmpty()),
                labels?.optString("descriptive")?.takeIf { it.isNotBlank() },
                labels?.optString("accent")?.takeIf { it.isNotBlank() && slavic.isEmpty() }
            )

        val name = voice.optString("name").ifBlank { id }
        val label = if (details.isEmpty()) name else "$name — ${details.joinToString(", ")}"
        return slavic.isNotEmpty() to VoiceChoice(id, label)
    }

    private fun genderLabel(value: String): String? =
        when (value.lowercase()) {
            "male" -> "мужской"
            "female" -> "женский"
            "" -> null
            else -> value
        }

    private fun describeError(response: CloudHttp.Response): String {
        val raw = response.text()
        val detail =
            runCatching {
                val json = JSONObject(raw)
                val node = json.optJSONObject("detail")
                (node?.optString("status").orEmpty() + " " + (node?.optString("message") ?: json.optString("detail"))).trim()
            }.getOrDefault("").ifBlank { raw }.take(300)

        return when {
            detail.contains("quota_exceeded") ->
                "ElevenLabs: на счёте закончились кредиты. Пополните баланс и повторите. $detail"
            response.code == 401 ->
                "ElevenLabs: API key неверный или не имеет доступа (HTTP 401). $detail"
            response.code == 402 ->
                "ElevenLabs: требуется оплата — пополните баланс (HTTP 402). $detail"
            response.code == 404 ->
                "ElevenLabs: голос или модель не найдены (HTTP 404). Выберите другой голос. $detail"
            response.code == 429 ->
                "ElevenLabs: слишком много запросов или исчерпан лимит (HTTP 429). $detail"
            response.code in 500..599 ->
                "ElevenLabs: временная серверная ошибка HTTP ${response.code}"
            else ->
                "ElevenLabs HTTP ${response.code}: $detail"
        }
    }
}
