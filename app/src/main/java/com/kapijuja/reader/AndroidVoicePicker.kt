package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Voice chooser for Android TTS engines. Shows Russian and Ukrainian voices
 * first with readable labels (gender, online/offline, downloaded or not) and
 * keeps the long list of other languages behind one extra item.
 */
object AndroidVoicePicker {
    private class Row(val id: String, val info: AndroidVoiceLabels.Info)

    fun show(
        activity: Activity,
        voices: Collection<Voice>,
        current: String,
        onPicked: (id: String, label: String) -> Unit,
        onClosed: () -> Unit = {}
    ) {
        val displayLocale = displayLocale(activity)
        val rows =
            voices
                .map { Row(it.name, describe(it, displayLocale)) }
                .sortedWith { a, b -> AndroidVoiceLabels.order.compare(a.info, b.info) }

        val preferred = rows.filter { AndroidVoiceLabels.isPreferred(it.info) }
        if (preferred.isEmpty() || preferred.size == rows.size) {
            showList(activity, rows, null, current, onPicked, onClosed)
        } else {
            showList(activity, preferred, rows, current, onPicked, onClosed)
        }
    }

    private fun showList(
        activity: Activity,
        rows: List<Row>,
        allRows: List<Row>?,
        current: String,
        onPicked: (String, String) -> Unit,
        onClosed: () -> Unit
    ) {
        val t = { ru: String, pl: String, en: String -> UiText.get(activity, ru, pl, en) }
        val labels = rows.map { AndroidVoiceLabels.label(it.info, t) }.toMutableList()
        if (allRows != null) {
            labels += t(
                "Другие языки (${allRows.size - rows.size})…",
                "Inne języki (${allRows.size - rows.size})…",
                "Other languages (${allRows.size - rows.size})…"
            )
        }

        var handedOver = false
        AlertDialog.Builder(activity)
            .setTitle(t("Голос", "Głos", "Voice"))
            .setSingleChoiceItems(
                labels.toTypedArray(),
                rows.indexOfFirst { it.id == current }
            ) { dialog, which ->
                if (which >= rows.size) {
                    handedOver = true
                    dialog.dismiss()
                    showList(activity, allRows.orEmpty(), null, current, onPicked, onClosed)
                } else {
                    onPicked(rows[which].id, labels[which])
                    dialog.dismiss()
                }
            }
            .setNegativeButton(t("Отмена", "Anuluj", "Cancel"), null)
            .setOnDismissListener { if (!handedOver) onClosed() }
            .show()
    }

    /** Readable label for an already selected voice id, without a TTS probe. */
    fun labelForId(activity: Activity, voiceId: String): String {
        val match = Regex("^([a-zA-Z]{2,3})[-_]([a-zA-Z0-9]{2,4})(?:[-_].*)?$").find(voiceId)
            ?: return voiceId
        val locale = Locale(match.groupValues[1], match.groupValues[2].uppercase())
        if (locale.getDisplayLanguage(Locale.ENGLISH).equals(locale.language, ignoreCase = true)) {
            return voiceId
        }
        return AndroidVoiceLabels.label(
            AndroidVoiceLabels.Info(
                name = voiceId,
                language = locale.language,
                languageName = locale.getDisplayName(displayLocale(activity)),
                needsNetwork = voiceId.lowercase().endsWith("-network"),
                notInstalled = false
            )
        ) { ru, pl, en -> UiText.get(activity, ru, pl, en) }
    }

    private fun describe(voice: Voice, displayLocale: Locale): AndroidVoiceLabels.Info {
        val locale = voice.locale
        return AndroidVoiceLabels.Info(
            name = voice.name,
            language = locale?.language.orEmpty(),
            languageName = locale?.getDisplayName(displayLocale).orEmpty(),
            needsNetwork = voice.isNetworkConnectionRequired,
            notInstalled =
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
        )
    }

    private fun displayLocale(activity: Activity): Locale =
        when (UiText.language(activity)) {
            SettingsStore.UI_LANGUAGE_RU -> Locale("ru")
            SettingsStore.UI_LANGUAGE_PL -> Locale("pl")
            else -> Locale.ENGLISH
        }
}
