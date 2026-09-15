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

    val azure = listOf(
        VoiceChoice(
            "ru-RU-DmitryNeural",
            "Dmitry Neural — мужской, подходит F0"
        ),
        VoiceChoice(
            "ru-RU-SvetlanaNeural",
            "Svetlana Neural — подходит F0"
        ),
        VoiceChoice(
            "ru-RU-DariyaNeural",
            "Dariya Neural — подходит F0"
        ),
        VoiceChoice(
            "ru-RU-Lev:MAI-Voice-2-Flash",
            "Lev HD Flash — мужской, не входит в F0"
        ),
        VoiceChoice(
            "ru-RU-Lev:MAI-Voice-2",
            "Lev HD — мужской, не входит в F0"
        )
    )

    val google = listOf(
        VoiceChoice("Gacrux", "Gacrux — mature"),
        VoiceChoice("Charon", "Charon — informative"),
        VoiceChoice("Orus", "Orus — firm"),
        VoiceChoice("Algenib", "Algenib — gravelly"),
        VoiceChoice("Schedar", "Schedar — even"),
        VoiceChoice("Sadaltager", "Sadaltager — knowledgeable"),
        VoiceChoice("Sulafat", "Sulafat — warm"),
        VoiceChoice("Iapetus", "Iapetus — clear"),
        VoiceChoice("Alnilam", "Alnilam — firm"),
        VoiceChoice("Rasalgethi", "Rasalgethi — informative"),
        VoiceChoice("Puck", "Puck — upbeat"),
        VoiceChoice("Fenrir", "Fenrir — excitable"),
        VoiceChoice("Kore", "Kore — firm"),
        VoiceChoice("Zephyr", "Zephyr — bright"),
        VoiceChoice("Leda", "Leda — youthful"),
        VoiceChoice("Aoede", "Aoede — breezy"),
        VoiceChoice("Callirrhoe", "Callirrhoe — easy-going"),
        VoiceChoice("Autonoe", "Autonoe — bright"),
        VoiceChoice("Enceladus", "Enceladus — breathy"),
        VoiceChoice("Umbriel", "Umbriel — easy-going"),
        VoiceChoice("Algieba", "Algieba — smooth"),
        VoiceChoice("Despina", "Despina — smooth"),
        VoiceChoice("Erinome", "Erinome — clear"),
        VoiceChoice("Laomedeia", "Laomedeia — upbeat"),
        VoiceChoice("Achernar", "Achernar — soft"),
        VoiceChoice("Pulcherrima", "Pulcherrima — forward"),
        VoiceChoice("Achird", "Achird — friendly"),
        VoiceChoice("Zubenelgenubi", "Zubenelgenubi — casual"),
        VoiceChoice("Vindemiatrix", "Vindemiatrix — gentle"),
        VoiceChoice("Sadachbia", "Sadachbia — lively")
    )

    val xai = listOf(
        VoiceChoice("orion", "Orion — rich, cinematic, resonant"),
        VoiceChoice("lux", "Lux — calm, grounded, wise"),
        VoiceChoice("perseus", "Perseus — strong, confident"),
        VoiceChoice("rigel", "Rigel — precise, professional"),
        VoiceChoice("naksh", "Naksh — warm, thoughtful, wise"),
        VoiceChoice("atlas", "Atlas — commanding, reassuring"),
        VoiceChoice("sal", "Sal — smooth, balanced"),
        VoiceChoice("rex", "Rex — confident, clear"),
        VoiceChoice("leo", "Leo — authoritative, strong"),
        VoiceChoice("lumen", "Lumen — warm, articulate"),
        VoiceChoice("castor", "Castor — easygoing"),
        VoiceChoice("ursa", "Ursa — friendly, warm"),
        VoiceChoice("liora", "Liora — calm, grounded"),
        VoiceChoice("aurora", "Aurora — serene, steady"),
        VoiceChoice("carina", "Carina — soft, soothing"),
        VoiceChoice("luna", "Luna — gentle, patient"),
        VoiceChoice("celeste", "Celeste — reassuring"),
        VoiceChoice("ara", "Ara — warm, friendly"),
        VoiceChoice("altair", "Altair — elegant, refined"),
        VoiceChoice("kepler", "Kepler — charismatic"),
        VoiceChoice("cosmo", "Cosmo — bright, clear"),
        VoiceChoice("iris", "Iris — friendly, upbeat"),
        VoiceChoice("helios", "Helios — energetic"),
        VoiceChoice("zenith", "Zenith — sharp, focused"),
        VoiceChoice("helix", "Helix — bold, dynamic"),
        VoiceChoice("zagan", "Zagan — powerful, dramatic"),
        VoiceChoice("sirius", "Sirius — playful"),
        VoiceChoice("eve", "Eve — energetic, upbeat")
    )

    val edge = listOf(
        VoiceChoice("ru-RU-DmitryNeural", "Dmitry Neural — мужской"),
        VoiceChoice("ru-RU-SvetlanaNeural", "Svetlana Neural"),
        VoiceChoice("ru-RU-DariyaNeural", "Dariya Neural")
    )

    fun staticVoices(engine: String): List<VoiceChoice> = when (engine) {
        SettingsStore.ENGINE_OPENAI -> openAi
        SettingsStore.ENGINE_EDGE -> edge
        SettingsStore.ENGINE_AZURE -> azure
        SettingsStore.ENGINE_GOOGLE -> google
        SettingsStore.ENGINE_XAI -> xai
        else -> emptyList()
    }

    fun defaultVoice(engine: String): String = when (engine) {
        SettingsStore.DEFAULT_ENGINE -> SettingsStore.DEFAULT_GOOGLE_ANDROID_VOICE
        SettingsStore.ENGINE_OPENAI -> "cedar"
        SettingsStore.ENGINE_SILERO -> "eugene"
        SettingsStore.ENGINE_EDGE -> "ru-RU-DmitryNeural"
        SettingsStore.ENGINE_AZURE -> "ru-RU-DmitryNeural"
        SettingsStore.ENGINE_GOOGLE -> "Gacrux"
        SettingsStore.ENGINE_XAI -> "orion"
        else -> ""
    }
}
