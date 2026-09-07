package com.kapijuja.reader

import android.content.Context

object SettingsStore {
    private const val PREFS = "kapijuja_reader_settings"
    private const val KEY_ENGINE = "engine"
    private const val KEY_LEGACY_VOICE = "voice"
    private const val KEY_OPENAI_KEY = "openai_key"
    private const val KEY_OPENAI_INSTRUCTIONS = "openai_instructions"
    private const val KEY_CONFIRM_EURO = "confirm_euro"
    private const val KEY_AZURE_SPEECH_KEY = "azure_speech_key"
    private const val KEY_AZURE_REGION = "azure_region"
    private const val KEY_AZURE_DEFAULT_MIGRATED = "azure_default_migrated"
    private const val KEY_GOOGLE_API_KEY = "google_api_key"
    private const val KEY_GOOGLE_INSTRUCTIONS = "google_instructions"
    private const val KEY_UI_LANGUAGE = "ui_language"
    private const val KEY_LIBRARY_LIMIT = "library_limit"

    const val DEFAULT_ENGINE = "android:com.google.android.tts"
    const val ENGINE_ANDROID_SYSTEM = "android:default"
    const val DEFAULT_GOOGLE_ANDROID_VOICE = "ru-ru-x-rud-network"
    const val ENGINE_OPENAI = "cloud:openai"
    const val ENGINE_SILERO = "local:silero"
    const val ENGINE_EDGE = "cloud:edge"
    const val ENGINE_AZURE = "cloud:azure"
    const val ENGINE_GOOGLE = "cloud:google"

    const val DEFAULT_AZURE_REGION = "switzerlandnorth"

    const val UI_LANGUAGE_SYSTEM = "system"
    const val UI_LANGUAGE_RU = "ru"
    const val UI_LANGUAGE_PL = "pl"
    const val UI_LANGUAGE_EN = "en"

    const val DEFAULT_LIBRARY_LIMIT = 200

    const val RHVOICE_PACKAGE = "com.github.olga_yakovleva.rhvoice.android"
    const val ENGINE_RHVOICE = "android:com.github.olga_yakovleva.rhvoice.android"

    fun engine(context: Context): String =
        prefs(context).getString(KEY_ENGINE, DEFAULT_ENGINE) ?: DEFAULT_ENGINE

    fun setEngine(context: Context, value: String) {
        prefs(context).edit().putString(KEY_ENGINE, value).apply()
    }

    fun voice(context: Context): String = voice(context, engine(context))

    fun voice(context: Context, engine: String): String {
        val p = prefs(context)
        val perEngine = p.getString(voiceKey(engine), null)
        if (perEngine != null) return perEngine

        // One-time migration from 0.1.1 where a single global voice was used.
        val legacy = p.getString(KEY_LEGACY_VOICE, "") ?: ""
        if (legacy.isNotBlank()) {
            p.edit()
                .putString(voiceKey(engine), legacy)
                .remove(KEY_LEGACY_VOICE)
                .apply()
            return legacy
        }
        if (engine == DEFAULT_ENGINE) return DEFAULT_GOOGLE_ANDROID_VOICE
        return ""
    }

    fun setVoice(context: Context, value: String) {
        setVoice(context, engine(context), value)
    }

    fun setVoice(context: Context, engine: String, value: String) {
        prefs(context).edit()
            .putString(voiceKey(engine), value)
            .remove(KEY_LEGACY_VOICE)
            .apply()
    }

    fun ensureDefaultVoice(context: Context, engine: String, defaultVoice: String) {
        if (defaultVoice.isNotBlank() && voice(context, engine).isBlank()) {
            setVoice(context, engine, defaultVoice)
        }
    }

    fun openAiKey(context: Context): String =
        prefs(context).getString(KEY_OPENAI_KEY, "") ?: ""

    fun setOpenAiKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OPENAI_KEY, value.trim()).apply()
    }

    fun openAiInstructions(context: Context): String =
        prefs(context).getString(
            KEY_OPENAI_INSTRUCTIONS,
            "Read in Russian naturally, calmly and clearly. Use a deep, restrained, dignified male narration style. Do not sing. Keep references, numbers and punctuation neutral."
        ) ?: ""

    fun setOpenAiInstructions(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OPENAI_INSTRUCTIONS, value.trim()).apply()
    }

    fun confirmEuro(context: Context): Double =
        java.lang.Double.longBitsToDouble(
            prefs(context).getLong(KEY_CONFIRM_EURO, java.lang.Double.doubleToRawLongBits(0.03))
        )

    fun setConfirmEuro(context: Context, value: Double) {
        prefs(context).edit()
            .putLong(
                KEY_CONFIRM_EURO,
                java.lang.Double.doubleToRawLongBits(
                    value.coerceAtLeast(0.0)
                )
            )
            .apply()
    }

    fun azureSpeechKey(context: Context): String =
        prefs(context)
            .getString(KEY_AZURE_SPEECH_KEY, "")
            ?: ""

    fun setAzureSpeechKey(
        context: Context,
        value: String
    ) {
        prefs(context)
            .edit()
            .putString(
                KEY_AZURE_SPEECH_KEY,
                value.trim()
            )
            .apply()
    }

    fun azureRegion(context: Context): String {
        val p = prefs(context)
        return if (p.contains(KEY_AZURE_REGION)) {
            p.getString(KEY_AZURE_REGION, "") ?: ""
        } else {
            DEFAULT_AZURE_REGION
        }
    }

    fun setAzureRegion(
        context: Context,
        value: String
    ) {
        val normalized =
            value.trim()
                .lowercase()
                .filter { it in 'a'..'z' || it in '0'..'9' }

        prefs(context)
            .edit()
            .putString(
                KEY_AZURE_REGION,
                normalized
            )
            .apply()
    }

    fun isValidAzureRegion(value: String): Boolean {
        val v = value.trim()
        return v.isNotBlank() &&
            v.length in 4..32 &&
            v.all { it in 'a'..'z' || it in '0'..'9' }
    }

    fun migrateDefaults(context: Context) {
        val p = prefs(context)

        if (!p.getBoolean(KEY_AZURE_DEFAULT_MIGRATED, false)) {
            val current =
                voice(
                    context,
                    ENGINE_AZURE
                )

            if (
                current.isBlank() ||
                current.startsWith(
                    "ru-RU-Lev:",
                    ignoreCase = true
                )
            ) {
                setVoice(
                    context,
                    ENGINE_AZURE,
                    "ru-RU-DmitryNeural"
                )
            }

            p.edit()
                .putBoolean(
                    KEY_AZURE_DEFAULT_MIGRATED,
                    true
                )
                .apply()
        }
    }

    fun googleApiKey(context: Context): String =
        prefs(context)
            .getString(KEY_GOOGLE_API_KEY, "")
            ?: ""

    fun setGoogleApiKey(
        context: Context,
        value: String
    ) {
        prefs(context)
            .edit()
            .putString(
                KEY_GOOGLE_API_KEY,
                value.trim()
            )
            .apply()
    }

    fun googleInstructions(context: Context): String =
        prefs(context)
            .getString(
                KEY_GOOGLE_INSTRUCTIONS,
                "Read the Russian text naturally and continuously. Use a mature, calm, low male narration style. Keep sentence pauses natural and short. Do not add dramatic pauses at paragraph breaks."
            )
            ?: ""

    fun setGoogleInstructions(
        context: Context,
        value: String
    ) {
        prefs(context)
            .edit()
            .putString(
                KEY_GOOGLE_INSTRUCTIONS,
                value.trim()
            )
            .apply()
    }

    fun uiLanguage(context: Context): String =
        prefs(context).getString(KEY_UI_LANGUAGE, UI_LANGUAGE_SYSTEM)
            ?: UI_LANGUAGE_SYSTEM

    fun setUiLanguage(context: Context, value: String) {
        val normalized =
            if (value in setOf(
                    UI_LANGUAGE_SYSTEM,
                    UI_LANGUAGE_RU,
                    UI_LANGUAGE_PL,
                    UI_LANGUAGE_EN
                )
            ) {
                value
            } else {
                UI_LANGUAGE_SYSTEM
            }
        prefs(context).edit().putString(KEY_UI_LANGUAGE, normalized).apply()
    }

    fun libraryLimit(context: Context): Int =
        prefs(context).getInt(KEY_LIBRARY_LIMIT, DEFAULT_LIBRARY_LIMIT)

    fun setLibraryLimit(context: Context, value: Int) {
        val allowed = setOf(0, 100, 200, 500, 1000, 5000, 10000)
        val normalized =
            if (value in allowed) value else DEFAULT_LIBRARY_LIMIT
        prefs(context).edit().putInt(KEY_LIBRARY_LIMIT, normalized).apply()
    }

    private fun voiceKey(engine: String): String =
        "voice_" + engine.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
