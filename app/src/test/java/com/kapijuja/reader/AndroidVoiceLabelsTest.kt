package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVoiceLabelsTest {
    private val ru = { russian: String, _: String, _: String -> russian }

    private fun info(
        name: String,
        language: String = "ru",
        languageName: String = "русский (Россия)",
        network: Boolean = false,
        notInstalled: Boolean = false
    ) = AndroidVoiceLabels.Info(name, language, languageName, network, notInstalled)

    @Test
    fun googleRussianVoiceGetsGenderAndConnectivity() {
        assertEquals(
            "Русский (Россия) — мужской, онлайн · rud",
            AndroidVoiceLabels.label(info("ru-ru-x-rud-network", network = true), ru)
        )
        assertEquals(
            "Русский (Россия) — женский, офлайн · ruc",
            AndroidVoiceLabels.label(info("ru-ru-x-ruc-local"), ru)
        )
    }

    @Test
    fun notDownloadedVoiceIsMarked() {
        assertTrue(
            AndroidVoiceLabels
                .label(info("ru-ru-x-ruf-local", notInstalled = true), ru)
                .contains("не скачан")
        )
    }

    @Test
    fun defaultLanguageVoiceHasNoTechnicalTail() {
        assertEquals(
            "Русский (Россия) — основной голос, офлайн",
            AndroidVoiceLabels.label(info("ru-RU-language"), ru)
        )
    }

    @Test
    fun unknownEngineVoiceKeepsItsOwnName() {
        assertNull(AndroidVoiceLabels.gender("Aleksandr-hq"))
        assertEquals(
            "Русский — офлайн · Aleksandr-hq",
            AndroidVoiceLabels.label(info("Aleksandr-hq", languageName = "русский"), ru)
        )
    }

    @Test
    fun russianAndUkrainianComeFirstAndOfflineBeforeOnline() {
        val sorted =
            listOf(
                info("en-us-x-sfg-local", language = "en", languageName = "английский (США)"),
                info("uk-ua-x-hfd-local", language = "uk", languageName = "украинский (Украина)"),
                info("ru-ru-x-rud-network", network = true),
                info("ru-ru-x-rud-local")
            ).sortedWith(AndroidVoiceLabels.order)

        assertEquals(
            listOf(
                "ru-ru-x-rud-local",
                "ru-ru-x-rud-network",
                "uk-ua-x-hfd-local",
                "en-us-x-sfg-local"
            ),
            sorted.map { it.name }
        )
    }
}
