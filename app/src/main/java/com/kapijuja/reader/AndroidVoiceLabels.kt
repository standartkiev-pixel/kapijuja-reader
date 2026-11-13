package com.kapijuja.reader

/**
 * Turns raw Android TTS voice ids such as "ru-ru-x-rud-network" into labels a
 * person can choose from. Pure logic so it can be unit-tested without Android.
 */
object AndroidVoiceLabels {
    data class Info(
        val name: String,
        /** ISO 639-1 language code, e.g. "ru". */
        val language: String,
        /** Human-readable language/country, e.g. "русский (Россия)". */
        val languageName: String,
        val needsNetwork: Boolean,
        val notInstalled: Boolean
    )

    enum class Gender { MALE, FEMALE }

    /** Languages listed first; everything else goes behind "other languages". */
    val preferredLanguages = listOf("ru", "uk")

    // Speaker codes of the Google speech engine. Only languages this app is
    // mainly used for are listed; unknown codes simply show no gender.
    private val googleSpeakers = mapOf(
        "ruc" to Gender.FEMALE,
        "rud" to Gender.MALE,
        "rue" to Gender.FEMALE,
        "ruf" to Gender.MALE,
        "dfc" to Gender.FEMALE,
        "hfd" to Gender.FEMALE
    )

    private val googlePattern =
        Regex("^[a-z]{2,3}-[a-z0-9]{2,4}-x-([a-z0-9]+)-(network|local)$")

    fun speakerCode(name: String): String? =
        googlePattern.find(name.lowercase())?.groupValues?.get(1)

    fun gender(name: String): Gender? =
        speakerCode(name)?.let { googleSpeakers[it] }

    /**
     * @param t picks the UI-language variant: (ru, pl, en) -> text
     */
    fun label(info: Info, t: (String, String, String) -> String): String {
        val code = speakerCode(info.name)
        val isDefaultOfLanguage = info.name.lowercase().endsWith("-language")

        val parts = mutableListOf<String>()
        when (gender(info.name)) {
            Gender.MALE -> parts += t("мужской", "męski", "male")
            Gender.FEMALE -> parts += t("женский", "żeński", "female")
            null -> Unit
        }
        if (isDefaultOfLanguage) {
            parts += t("основной голос", "głos domyślny", "default voice")
        }
        parts +=
            if (info.needsNetwork) {
                t("онлайн", "online", "online")
            } else {
                t("офлайн", "offline", "offline")
            }
        if (info.notInstalled) {
            parts += t("не скачан", "niepobrany", "not downloaded")
        }

        // Keep a short technical tail so two otherwise equal voices differ.
        val tail =
            when {
                code != null -> code
                isDefaultOfLanguage -> null
                else -> info.name
            }

        val language =
            info.languageName
                .ifBlank { info.language }
                .replaceFirstChar { it.uppercase() }

        return buildString {
            if (language.isNotBlank()) append(language).append(" — ")
            append(parts.joinToString(", "))
            if (tail != null) append(" · ").append(tail)
        }
    }

    fun isPreferred(info: Info): Boolean =
        info.language.lowercase() in preferredLanguages

    /** Preferred languages first, downloaded and offline voices before others. */
    val order: Comparator<Info> =
        compareBy<Info> {
            preferredLanguages.indexOf(it.language.lowercase())
                .let { rank -> if (rank < 0) preferredLanguages.size else rank }
        }
            .thenBy { it.languageName.lowercase() }
            .thenBy { it.notInstalled }
            .thenBy { it.needsNetwork }
            .thenBy { it.name.lowercase() }
}
