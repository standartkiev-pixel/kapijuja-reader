package com.kapijuja.reader

import android.content.Context
import android.os.Build
import java.util.Locale

object UiText {
    fun language(context: Context): String {
        val override = SettingsStore.uiLanguage(context)
        if (override != SettingsStore.UI_LANGUAGE_SYSTEM) return override

        val config = context.resources.configuration
        val locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.locales[0]
            } else {
                @Suppress("DEPRECATION")
                config.locale
            }

        return when (locale?.language?.lowercase(Locale.ROOT)) {
            "ru" -> SettingsStore.UI_LANGUAGE_RU
            "pl" -> SettingsStore.UI_LANGUAGE_PL
            else -> SettingsStore.UI_LANGUAGE_EN
        }
    }

    fun get(
        context: Context,
        ru: String,
        pl: String,
        en: String
    ): String =
        when (language(context)) {
            SettingsStore.UI_LANGUAGE_RU -> ru
            SettingsStore.UI_LANGUAGE_PL -> pl
            else -> en
        }

    // Compatibility overload for strings not yet given an explicit Polish form.
    // Polish intentionally falls back to English rather than Russian.
    fun get(context: Context, ru: String, en: String): String =
        get(context, ru, en, en)

    fun languageLabel(context: Context): String =
        when (language(context)) {
            SettingsStore.UI_LANGUAGE_RU -> "Русский"
            SettingsStore.UI_LANGUAGE_PL -> "Polski"
            else -> "English"
        }
}
