package com.kapijuja.reader

import android.content.Context
import android.os.Build
import java.util.Locale

object UiText {
    fun isRussian(context: Context): Boolean {
        val config = context.resources.configuration
        val locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.locales[0]
            } else {
                @Suppress("DEPRECATION")
                config.locale
            }
        return locale?.language?.lowercase(Locale.ROOT) == "ru"
    }

    fun get(context: Context, ru: String, en: String): String =
        if (isRussian(context)) ru else en
}
