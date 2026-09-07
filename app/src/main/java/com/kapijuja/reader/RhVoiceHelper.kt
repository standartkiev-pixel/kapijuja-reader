package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object RhVoiceHelper {
    private const val PLAY_URL =
        "https://play.google.com/store/apps/details?id=com.github.olga_yakovleva.rhvoice.android"
    private const val FDROID_URL =
        "https://f-droid.org/packages/com.github.olga_yakovleva.rhvoice.android/"

    fun isInstalled(activity: Activity): Boolean =
        try {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageInfo(
                SettingsStore.RHVOICE_PACKAGE,
                0
            )
            true
        } catch (_: Throwable) {
            false
        }

    fun showInstallDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("RHVoice — бесплатный локальный TTS")
            .setMessage(
                "RHVoice не требует API key и работает офлайн. " +
                    "Для русского рекомендуем начать с мужского Aleksandr-HQ. " +
                    "После установки RHVoice вернитесь в Kapijuja Reader — движок появится автоматически."
            )
            .setNegativeButton("Позже", null)
            .setNeutralButton("F-Droid") { _, _ ->
                openUrl(activity, FDROID_URL)
            }
            .setPositiveButton("Google Play") { _, _ ->
                openUrl(activity, PLAY_URL)
            }
            .show()
    }

    fun openApp(activity: Activity): Boolean {
        val launch =
            activity.packageManager.getLaunchIntentForPackage(
                SettingsStore.RHVOICE_PACKAGE
            )
                ?: return false

        activity.startActivity(launch)
        return true
    }

    private fun openUrl(
        activity: Activity,
        url: String
    ) {
        try {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        } catch (_: Throwable) {
            Toast.makeText(
                activity,
                "Не удалось открыть страницу RHVoice",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
