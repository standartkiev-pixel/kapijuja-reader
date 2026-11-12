package com.kapijuja.reader

import android.content.Context

data class CloudAudioSpec(
    val prefix: String,
    val extension: String
)

object CloudTtsDispatcher {
    fun defaultVoice(engine: String): String =
        when (engine) {
            SettingsStore.ENGINE_EDGE,
            SettingsStore.ENGINE_AZURE ->
                "ru-RU-DmitryNeural"
            SettingsStore.ENGINE_GOOGLE ->
                "Gacrux"
            SettingsStore.ENGINE_XAI ->
                "orion"
            SettingsStore.ENGINE_ELEVEN ->
                ElevenLabsTtsClient.DEFAULT_VOICE
            SettingsStore.ENGINE_INWORLD ->
                InworldTtsClient.DEFAULT_VOICE
            else ->
                "cedar"
        }

    fun synthesize(
        context: Context,
        engine: String,
        text: String,
        speed: Float
    ): ByteArray {
        val voice =
            SettingsStore
                .voice(context, engine)
                .ifBlank { defaultVoice(engine) }

        return when (engine) {
            SettingsStore.ENGINE_OPENAI ->
                OpenAiTtsClient.synthesize(
                    apiKey = SettingsStore.openAiKey(context),
                    text = text,
                    voice = voice,
                    instructions = SettingsStore.openAiInstructions(context),
                    speed = speed,
                    context = context
                )

            SettingsStore.ENGINE_EDGE ->
                EdgeTtsClient.synthesize(
                    text = text,
                    voice = voice,
                    speed = speed,
                    context = context
                )

            SettingsStore.ENGINE_AZURE ->
                AzureTtsClient.synthesize(
                    speechKey = SettingsStore.azureSpeechKey(context),
                    region = SettingsStore.azureRegion(context),
                    text = text,
                    voice = voice,
                    speed = speed,
                    context = context
                )

            SettingsStore.ENGINE_GOOGLE ->
                GoogleGeminiTtsClient.synthesizeWav(
                    apiKey = SettingsStore.googleApiKey(context),
                    text = text,
                    voice = voice,
                    instructions = SettingsStore.googleInstructions(context),
                    context = context
                )

            SettingsStore.ENGINE_XAI ->
                XaiTtsClient.synthesize(
                    apiKey = SettingsStore.xaiApiKey(context),
                    text = text,
                    voice = voice,
                    speed = speed,
                    language = SettingsStore.speechLanguage(context),
                    context = context
                )

            SettingsStore.ENGINE_ELEVEN ->
                ElevenLabsTtsClient.synthesize(
                    apiKey = SettingsStore.elevenApiKey(context),
                    model = SettingsStore.elevenModel(context),
                    text = text,
                    voice = voice,
                    speed = speed,
                    language = SettingsStore.speechLanguage(context),
                    context = context
                )

            SettingsStore.ENGINE_INWORLD ->
                InworldTtsClient.synthesize(
                    apiKey = SettingsStore.inworldApiKey(context),
                    model = SettingsStore.inworldModel(context),
                    text = text,
                    voice = voice,
                    speed = speed,
                    language = SettingsStore.speechLanguage(context),
                    context = context
                )

            else ->
                error("Unsupported cloud engine: $engine")
        }
    }

    fun splitForExport(
        engine: String,
        text: String
    ): List<String> =
        when (engine) {
            SettingsStore.ENGINE_EDGE ->
                EdgeTtsClient.splitForApi(text)
            SettingsStore.ENGINE_AZURE ->
                AzureTtsClient.splitForApi(text)
            SettingsStore.ENGINE_XAI ->
                XaiTtsClient.splitForApi(text)
            SettingsStore.ENGINE_ELEVEN,
            SettingsStore.ENGINE_INWORLD ->
                // Small export parts keep each paid request short; the client
                // splits further if a provider limit requires it.
                OpenAiTtsClient.splitForApi(text, 1_800)
            else ->
                OpenAiTtsClient.splitForApi(text)
        }

    fun tempAudioSpec(engine: String): CloudAudioSpec =
        when (engine) {
            SettingsStore.ENGINE_EDGE ->
                CloudAudioSpec("edge", "mp3")
            SettingsStore.ENGINE_AZURE ->
                CloudAudioSpec("azure", "mp3")
            SettingsStore.ENGINE_GOOGLE ->
                CloudAudioSpec("google", "wav")
            SettingsStore.ENGINE_XAI ->
                CloudAudioSpec("xai", "mp3")
            SettingsStore.ENGINE_ELEVEN ->
                CloudAudioSpec("eleven", "mp3")
            SettingsStore.ENGINE_INWORLD ->
                CloudAudioSpec("inworld", "mp3")
            else ->
                CloudAudioSpec("openai", "mp3")
        }

    /** Engines where every generated chunk costs money. */
    fun isPaidPrefetchSensitive(engine: String): Boolean =
        engine == SettingsStore.ENGINE_OPENAI ||
            CloudEngines.isPerCharacter(engine)
}
