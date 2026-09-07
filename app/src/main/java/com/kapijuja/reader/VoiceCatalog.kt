package com.kapijuja.reader

data class VoiceChoice(val id: String, val label: String)

object VoiceCatalog {
    val openAi = listOf(
        VoiceChoice("cedar", "Cedar"),
        VoiceChoice("marin", "Marin"),
        VoiceChoice("onyx", "Onyx"),
        VoiceChoice("echo", "Echo"),
        VoiceChoice("alloy", "Alloy"),
        VoiceChoice("ash", "Ash"),
        VoiceChoice("ballad", "Ballad"),
        VoiceChoice("coral", "Coral"),
        VoiceChoice("fable", "Fable"),
        VoiceChoice("nova", "Nova"),
        VoiceChoice("sage", "Sage"),
        VoiceChoice("shimmer", "Shimmer"),
        VoiceChoice("verse", "Verse")
    )

    val silero = listOf(
        VoiceChoice("eugene", "Eugene — мужской, глубокий"),
        VoiceChoice("aidar", "Aidar — мужской, нейтральный"),
        VoiceChoice("baya", "Baya"),
        VoiceChoice("kseniya", "Kseniya"),
        VoiceChoice("xenia", "Xenia")
    )

    val azure = listOf(
        VoiceChoice("ru-RU-Lev:MAI-Voice-2-Flash", "Lev HD Flash — мужской"),
        VoiceChoice("ru-RU-Lev:MAI-Voice-2", "Lev HD — мужской"),
        VoiceChoice("ru-RU-DmitryNeural", "Dmitry Neural — мужской"),
        VoiceChoice("ru-RU-SvetlanaNeural", "Svetlana Neural"),
        VoiceChoice("ru-RU-DariyaNeural", "Dariya Neural")
    )

    val google = listOf(
        VoiceChoice("ru-RU-Chirp3-HD-Charon", "Chirp 3 HD Charon — мужской"),
        VoiceChoice("ru-RU-Chirp3-HD-Fenrir", "Chirp 3 HD Fenrir — мужской"),
        VoiceChoice("ru-RU-Chirp3-HD-Orus", "Chirp 3 HD Orus — мужской"),
        VoiceChoice("ru-RU-Chirp3-HD-Puck", "Chirp 3 HD Puck — мужской"),
        VoiceChoice("ru-RU-Wavenet-B", "WaveNet B — мужской"),
        VoiceChoice("ru-RU-Wavenet-D", "WaveNet D — мужской"),
        VoiceChoice("ru-RU-Standard-B", "Standard B — мужской"),
        VoiceChoice("ru-RU-Standard-D", "Standard D — мужской")
    )

    val edge = listOf(
        VoiceChoice("ru-RU-DmitryNeural", "Dmitry Neural — мужской"),
        VoiceChoice("ru-RU-SvetlanaNeural", "Svetlana Neural"),
        VoiceChoice("ru-RU-DariyaNeural", "Dariya Neural")
    )

    fun staticVoices(engine: String): List<VoiceChoice> = when (engine) {
        SettingsStore.ENGINE_OPENAI -> openAi
        SettingsStore.ENGINE_SILERO -> silero
        SettingsStore.ENGINE_EDGE -> edge
        SettingsStore.ENGINE_AZURE -> azure
        SettingsStore.ENGINE_GOOGLE -> google
        else -> emptyList()
    }

    fun defaultVoice(engine: String): String = when (engine) {
        SettingsStore.ENGINE_OPENAI -> "cedar"
        SettingsStore.ENGINE_SILERO -> "eugene"
        SettingsStore.ENGINE_EDGE -> "ru-RU-DmitryNeural"
        SettingsStore.ENGINE_AZURE -> "ru-RU-Lev:MAI-Voice-2-Flash"
        SettingsStore.ENGINE_GOOGLE -> "ru-RU-Chirp3-HD-Charon"
        else -> ""
    }
}
