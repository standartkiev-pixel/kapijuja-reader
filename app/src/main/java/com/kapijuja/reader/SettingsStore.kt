package com.kapijuja.reader

import android.content.Context

object SettingsStore {
    private const val PREFS = "kapijuja_reader_settings"
    private const val KEY_ENGINE = "engine"
    private const val KEY_VOICE = "voice"
    private const val KEY_OPENAI_KEY = "openai_key"
    private const val KEY_OPENAI_INSTRUCTIONS = "openai_instructions"
    private const val KEY_CONFIRM_EURO = "confirm_euro"

    const val DEFAULT_ENGINE = "android:default"
    const val ENGINE_OPENAI = "cloud:openai"
    const val ENGINE_SILERO = "local:silero"
    const val ENGINE_EDGE = "cloud:edge"
    const val ENGINE_AZURE = "cloud:azure"
    const val ENGINE_GOOGLE = "cloud:google"

    fun engine(context: Context): String =
        prefs(context).getString(KEY_ENGINE, DEFAULT_ENGINE) ?: DEFAULT_ENGINE

    fun setEngine(context: Context, value: String) {
        prefs(context).edit().putString(KEY_ENGINE, value).apply()
    }

    fun voice(context: Context): String =
        prefs(context).getString(KEY_VOICE, "") ?: ""

    fun setVoice(context: Context, value: String) {
        prefs(context).edit().putString(KEY_VOICE, value).apply()
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
            .putLong(KEY_CONFIRM_EURO, java.lang.Double.doubleToRawLongBits(value.coerceAtLeast(0.0)))
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
