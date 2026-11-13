package com.kapijuja.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provider facts shared by Reader, Settings and menus: display names, where to
 * get a key and pay, per-character cost estimates and account voice lists.
 * Keeps provider-specific branching out of the two legacy Activities.
 */
object CloudEngines {
    // Display-only conversion for the euro cost guard; providers bill in USD.
    private const val DISPLAY_USD_TO_EUR = 0.87

    class Links(val keyUrl: String, val billingUrl: String)

    fun name(engine: String): String =
        when (engine) {
            SettingsStore.ENGINE_OPENAI -> "OpenAI"
            SettingsStore.ENGINE_EDGE -> "Microsoft Edge"
            SettingsStore.ENGINE_AZURE -> "Azure Speech"
            SettingsStore.ENGINE_GOOGLE -> "Google Gemini"
            SettingsStore.ENGINE_XAI -> "xAI Grok"
            SettingsStore.ENGINE_ELEVEN -> "ElevenLabs"
            SettingsStore.ENGINE_INWORLD -> "Inworld"
            else -> "TTS"
        }

    /** Short label for the compact Reader toolbar; null for non-cloud engines. */
    fun shortName(engine: String): String? =
        when (engine) {
            SettingsStore.ENGINE_OPENAI -> "OpenAI"
            SettingsStore.ENGINE_EDGE -> "Edge"
            SettingsStore.ENGINE_AZURE -> "Azure"
            SettingsStore.ENGINE_GOOGLE -> "Gemini"
            SettingsStore.ENGINE_XAI -> "Grok"
            SettingsStore.ENGINE_ELEVEN -> "Eleven"
            SettingsStore.ENGINE_INWORLD -> "Inworld"
            else -> null
        }

    /** Engines billed per input character that share the generic cost guard. */
    fun isPerCharacter(engine: String): Boolean =
        engine == SettingsStore.ENGINE_XAI ||
            engine == SettingsStore.ENGINE_ELEVEN ||
            engine == SettingsStore.ENGINE_INWORLD

    fun apiKey(context: Context, engine: String): String =
        when (engine) {
            SettingsStore.ENGINE_OPENAI -> SettingsStore.openAiKey(context)
            SettingsStore.ENGINE_GOOGLE -> SettingsStore.googleApiKey(context)
            SettingsStore.ENGINE_XAI -> SettingsStore.xaiApiKey(context)
            SettingsStore.ENGINE_ELEVEN -> SettingsStore.elevenApiKey(context)
            SettingsStore.ENGINE_INWORLD -> SettingsStore.inworldApiKey(context)
            else -> ""
        }

    fun priceUsdPerMillionChars(context: Context, engine: String): Double =
        when (engine) {
            SettingsStore.ENGINE_ELEVEN ->
                ElevenLabsTtsClient.priceUsdPerMillionChars(SettingsStore.elevenModel(context))
            SettingsStore.ENGINE_INWORLD ->
                InworldTtsClient.priceUsdPerMillionChars(SettingsStore.inworldModel(context))
            else -> 0.0
        }

    fun costEuro(context: Context, engine: String, text: String): Double =
        if (engine == SettingsStore.ENGINE_XAI) {
            XaiTtsClient.estimatedCostEuro(text)
        } else {
            costEuro(text.length, priceUsdPerMillionChars(context, engine))
        }

    fun costEuro(chars: Int, priceUsdPerMillionChars: Double): Double =
        chars.coerceAtLeast(1) * priceUsdPerMillionChars / 1_000_000.0 * DISPLAY_USD_TO_EUR

    fun links(engine: String): Links? =
        when (engine) {
            SettingsStore.ENGINE_OPENAI ->
                Links(
                    "https://platform.openai.com/api-keys",
                    "https://platform.openai.com/settings/organization/billing/overview"
                )
            SettingsStore.ENGINE_AZURE ->
                Links(
                    "https://portal.azure.com/#create/Microsoft.CognitiveServicesSpeechServices",
                    "https://portal.azure.com/#view/Microsoft_Azure_CostManagement/Menu/~/overview"
                )
            SettingsStore.ENGINE_GOOGLE ->
                Links(
                    "https://aistudio.google.com/apikey",
                    "https://aistudio.google.com/usage"
                )
            SettingsStore.ENGINE_XAI ->
                Links("https://console.x.ai/team/default/api-keys", "https://console.x.ai/team/default/billing")
            SettingsStore.ENGINE_ELEVEN ->
                Links(ElevenLabsTtsClient.KEY_URL, ElevenLabsTtsClient.BILLING_URL)
            SettingsStore.ENGINE_INWORLD ->
                Links(InworldTtsClient.KEY_URL, InworldTtsClient.BILLING_URL)
            else -> null
        }

    // ---- account voice lists (ElevenLabs / Inworld) ----

    fun hasAccountVoices(engine: String): Boolean =
        engine == SettingsStore.ENGINE_ELEVEN ||
            engine == SettingsStore.ENGINE_INWORLD

    fun cachedVoices(context: Context, engine: String): List<VoiceChoice> =
        decodeVoices(SettingsStore.cachedVoices(context, engine))

    /**
     * Downloads the account's voice list and caches it. Network call: run off
     * the main thread. If the user still has the built-in default voice and
     * the provider offers a Russian/Ukrainian one, that voice becomes current.
     */
    fun refreshVoices(context: Context, engine: String): List<VoiceChoice> {
        val voices =
            when (engine) {
                SettingsStore.ENGINE_ELEVEN ->
                    ElevenLabsTtsClient.fetchVoices(SettingsStore.elevenApiKey(context), context)
                SettingsStore.ENGINE_INWORLD ->
                    InworldTtsClient.fetchVoices(SettingsStore.inworldApiKey(context), context)
                else -> emptyList()
            }
        if (voices.isEmpty()) return voices

        SettingsStore.setCachedVoices(context, engine, encodeVoices(voices))

        val current = SettingsStore.voice(context, engine)
        val first = voices.first()
        val firstIsSlavic = first.label.contains("RU") || first.label.contains("UK")
        if (
            current.isBlank() ||
            voices.none { it.id == current } ||
            (current == VoiceCatalog.defaultVoice(engine) && firstIsSlavic)
        ) {
            SettingsStore.setVoice(context, engine, first.id)
        }
        return voices
    }

    fun encodeVoices(voices: List<VoiceChoice>): String {
        val array = JSONArray()
        voices.forEach {
            array.put(JSONObject().put("id", it.id).put("label", it.label))
        }
        return array.toString()
    }

    fun decodeVoices(json: String): List<VoiceChoice> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                if (id.isBlank()) null else VoiceChoice(id, item.optString("label").ifBlank { id })
            }
        }.getOrDefault(emptyList())
    }
}
