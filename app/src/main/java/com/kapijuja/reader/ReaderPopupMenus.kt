package com.kapijuja.reader

import android.app.Activity
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast

object ReaderPopupMenus {
    // Guards against a download loop when the provider returns no voices.
    private var accountVoicesRequested = false

    private data class EngineChoice(
        val id: String,
        val label: String
    )

    fun showEngine(
        activity: Activity,
        anchor: View,
        onChanged: () -> Unit
    ) {
        val choices = buildList {
            add(
                EngineChoice(
                    SettingsStore.DEFAULT_ENGINE,
                    t(activity, "Google Android TTS", "Google Android TTS", "Google Android TTS")
                )
            )
            add(
                EngineChoice(
                    SettingsStore.ENGINE_ANDROID_SYSTEM,
                    t(activity, "Android TTS — системный", "Android TTS — systemowy", "Android TTS — system")
                )
            )
            if (RhVoiceHelper.isInstalled(activity)) {
                add(
                    EngineChoice(
                        SettingsStore.ENGINE_RHVOICE,
                        "RHVoice"
                    )
                )
            }
            add(EngineChoice(SettingsStore.ENGINE_EDGE, "Microsoft Edge"))
            add(EngineChoice(SettingsStore.ENGINE_AZURE, "Microsoft Azure"))
            add(EngineChoice(SettingsStore.ENGINE_OPENAI, "OpenAI"))
            add(EngineChoice(SettingsStore.ENGINE_GOOGLE, "Google Gemini"))
            add(EngineChoice(SettingsStore.ENGINE_XAI, "xAI Grok"))
            add(EngineChoice(SettingsStore.ENGINE_ELEVEN, "ElevenLabs"))
            add(EngineChoice(SettingsStore.ENGINE_INWORLD, "Inworld"))
        }

        val current = SettingsStore.engine(activity)
        val popup = PopupMenu(activity, anchor)
        choices.forEachIndexed { index, choice ->
            popup.menu
                .add(0, index + 1, index, choice.label)
                .apply {
                    isCheckable = true
                    isChecked = choice.id == current
                }
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            val choice = choices.getOrNull(item.itemId - 1)
                ?: return@setOnMenuItemClickListener false
            SettingsStore.setEngine(activity, choice.id)
            SettingsStore.ensureDefaultVoice(
                activity,
                choice.id,
                VoiceCatalog.defaultVoice(choice.id)
            )
            onChanged()
            true
        }
        popup.show()
    }

    fun showVoice(
        activity: Activity,
        anchor: View,
        onChanged: () -> Unit
    ) {
        val engine = SettingsStore.engine(activity)
        if (engine.startsWith("android:")) {
            showAndroidVoices(activity, anchor, engine, onChanged)
            return
        }

        // ElevenLabs/Inworld voices belong to the user's account: download the
        // list once, then reopen this menu with the real voices.
        if (
            CloudEngines.hasAccountVoices(engine) &&
            CloudEngines.cachedVoices(activity, engine).isEmpty() &&
            CloudEngines.apiKey(activity, engine).isNotBlank() &&
            !accountVoicesRequested
        ) {
            accountVoicesRequested = true
            Toast.makeText(
                activity,
                t(activity, "Загружаю список голосов…", "Pobieram listę głosów…", "Loading voices…"),
                Toast.LENGTH_SHORT
            ).show()
            Thread {
                runCatching { CloudEngines.refreshVoices(activity, engine) }
                    .onFailure { AppDiagnostics.error(activity, "Voice list download failed: engine=$engine", it) }
                activity.runOnUiThread {
                    if (!activity.isFinishing) {
                        onChanged()
                        showVoice(activity, anchor, onChanged)
                    }
                }
            }.start()
            return
        }
        accountVoicesRequested = false

        val voices = VoiceCatalog.voices(activity, engine)
        if (voices.isEmpty()) {
            Toast.makeText(
                activity,
                t(
                    activity,
                    "Для этого движка список голосов недоступен",
                    "Lista głosów dla tego silnika jest niedostępna",
                    "No voice list is available for this engine"
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val current = SettingsStore.voice(activity, engine)
        val popup = PopupMenu(activity, anchor)
        voices.forEachIndexed { index, voice ->
            popup.menu
                .add(0, index + 1, index, voice.label)
                .apply {
                    isCheckable = true
                    isChecked = voice.id == current
                }
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            val voice = voices.getOrNull(item.itemId - 1)
                ?: return@setOnMenuItemClickListener false
            SettingsStore.setVoice(activity, engine, voice.id)
            onChanged()
            true
        }
        popup.show()
    }

    private fun showAndroidVoices(
        activity: Activity,
        anchor: View,
        engine: String,
        onChanged: () -> Unit
    ) {
        val packageName =
            engine.removePrefix("android:")
                .takeIf { it != "default" }

        var probe: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(
                    activity,
                    t(
                        activity,
                        "Не удалось открыть список голосов",
                        "Nie udało się otworzyć listy głosów",
                        "Could not open the voice list"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                probe?.shutdown()
                return@OnInitListener
            }

            val voices = probe?.voices.orEmpty()
            if (voices.isEmpty()) {
                Toast.makeText(
                    activity,
                    t(
                        activity,
                        "Голоса не найдены",
                        "Nie znaleziono głosów",
                        "No voices found"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                probe?.shutdown()
                return@OnInitListener
            }

            AndroidVoicePicker.show(
                activity = activity,
                voices = voices,
                current = SettingsStore.voice(activity, engine),
                onPicked = { id, _ ->
                    SettingsStore.setVoice(activity, engine, id)
                    onChanged()
                },
                onClosed = { probe?.shutdown() }
            )
        }

        probe =
            if (packageName == null) {
                TextToSpeech(activity, listener)
            } else {
                TextToSpeech(activity, listener, packageName)
            }
    }

    private fun t(
        activity: Activity,
        ru: String,
        pl: String,
        en: String
    ): String = UiText.get(activity, ru, pl, en)
}
