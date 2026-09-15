package com.kapijuja.reader

import android.app.Activity
import android.widget.Toast

object ProviderConnectionTester {
    fun test(
        activity: Activity,
        engine: String,
        onStatus: (String) -> Unit
    ) {
        val validationError = validationError(activity, engine)
        if (validationError != null) {
            Toast.makeText(activity, validationError, Toast.LENGTH_SHORT).show()
            return
        }

        onStatus(checkingText(activity, engine))

        Thread {
            try {
                val result = checkAccess(activity, engine)
                activity.runOnUiThread {
                    val localized = UiText.localizeMessage(activity, result)
                    onStatus(localized)
                    Toast.makeText(activity, localized, Toast.LENGTH_LONG).show()
                }
            } catch (error: Throwable) {
                AppDiagnostics.error(
                    activity,
                    "Provider connection test failed: engine=$engine",
                    error
                )
                activity.runOnUiThread {
                    val message =
                        UiText.localizeMessage(
                            activity,
                            error.message ?: providerName(engine) + " error"
                        )
                    onStatus(message)
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun validationError(
        activity: Activity,
        engine: String
    ): String? =
        when (engine) {
            SettingsStore.ENGINE_OPENAI ->
                if (SettingsStore.openAiKey(activity).isBlank()) {
                    t(
                        activity,
                        "Сначала введите OpenAI API key",
                        "Najpierw wprowadź klucz API OpenAI",
                        "Enter the OpenAI API key first"
                    )
                } else {
                    null
                }

            SettingsStore.ENGINE_AZURE ->
                if (
                    SettingsStore.azureSpeechKey(activity).isBlank() ||
                    SettingsStore.azureRegion(activity).isBlank()
                ) {
                    t(
                        activity,
                        "Введите Azure Speech key и region",
                        "Wprowadź klucz Azure Speech i region",
                        "Enter the Azure Speech key and region"
                    )
                } else {
                    null
                }

            SettingsStore.ENGINE_GOOGLE ->
                if (SettingsStore.googleApiKey(activity).isBlank()) {
                    t(
                        activity,
                        "Введите Google Gemini API key",
                        "Wprowadź klucz API Google Gemini",
                        "Enter the Google Gemini API key"
                    )
                } else {
                    null
                }

            SettingsStore.ENGINE_XAI ->
                if (SettingsStore.xaiApiKey(activity).isBlank()) {
                    t(
                        activity,
                        "Введите xAI API key",
                        "Wprowadź klucz API xAI",
                        "Enter the xAI API key"
                    )
                } else {
                    null
                }

            else -> "Unsupported provider: $engine"
        }

    private fun checkingText(
        activity: Activity,
        engine: String
    ): String =
        when (engine) {
            SettingsStore.ENGINE_OPENAI ->
                t(activity, "Проверка api.openai.com…", "Sprawdzanie api.openai.com…", "Checking api.openai.com…")
            SettingsStore.ENGINE_AZURE ->
                t(activity, "Проверка Azure Speech…", "Sprawdzanie Azure Speech…", "Checking Azure Speech…")
            SettingsStore.ENGINE_GOOGLE ->
                t(activity, "Проверка Google Gemini TTS…", "Sprawdzanie Google Gemini TTS…", "Checking Google Gemini TTS…")
            SettingsStore.ENGINE_XAI ->
                t(activity, "Проверка xAI Grok TTS…", "Sprawdzanie xAI Grok TTS…", "Checking xAI Grok TTS…")
            else -> providerName(engine)
        }

    private fun checkAccess(
        activity: Activity,
        engine: String
    ): String =
        when (engine) {
            SettingsStore.ENGINE_OPENAI ->
                OpenAiTtsClient.checkAccess(
                    SettingsStore.openAiKey(activity),
                    activity
                )
            SettingsStore.ENGINE_AZURE ->
                AzureTtsClient.checkAccess(
                    speechKey = SettingsStore.azureSpeechKey(activity),
                    region = SettingsStore.azureRegion(activity),
                    context = activity
                )
            SettingsStore.ENGINE_GOOGLE ->
                GoogleGeminiTtsClient.checkAccess(
                    apiKey = SettingsStore.googleApiKey(activity),
                    context = activity
                )
            SettingsStore.ENGINE_XAI ->
                XaiTtsClient.checkAccess(
                    apiKey = SettingsStore.xaiApiKey(activity),
                    context = activity
                )
            else -> error("Unsupported provider: $engine")
        }

    private fun providerName(engine: String): String =
        when (engine) {
            SettingsStore.ENGINE_OPENAI -> "OpenAI"
            SettingsStore.ENGINE_AZURE -> "Azure Speech"
            SettingsStore.ENGINE_GOOGLE -> "Google Gemini TTS"
            SettingsStore.ENGINE_XAI -> "xAI Grok TTS"
            else -> engine
        }

    private fun t(
        activity: Activity,
        ru: String,
        pl: String,
        en: String
    ): String = UiText.get(activity, ru, pl, en)
}
