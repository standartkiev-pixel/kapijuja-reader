package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var engineButton: Button
    private lateinit var voiceButton: Button
    private lateinit var statusText: TextView
    private lateinit var keyInput: EditText
    private lateinit var instructionInput: EditText
    private lateinit var costInput: EditText
    private lateinit var azureKeyInput: EditText
    private lateinit var azureRegionInput: EditText
    private lateinit var googleKeyInput: EditText
    private lateinit var googleInstructionInput: EditText
    private lateinit var serviceText: TextView
    private lateinit var languageButton: Button
    private lateinit var libraryLimitButton: Button
    private lateinit var libraryInfoText: TextView

    private var probeTts: TextToSpeech? = null
    private var installedEngines: List<TextToSpeech.EngineInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KapijujaUiTheme.applyWindow(this)
        buildScreen()
        probeInstalledEngines()
    }

    private fun buildScreen() {
        val outer = ScrollView(this).apply {
            setBackgroundResource(R.drawable.kapijuja_screen_bg)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        KapijujaUiTheme.applySafeArea(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 30f
            setOnClickListener {
                persistSettings()
                finish()
            }
        }
        KapijujaUiTheme.button(this, back)
        top.addView(back, LinearLayout.LayoutParams(dp(58), dp(54)))

        val title = TextView(this).apply {
            text = t("⚙ Настройки", "⚙ Settings")
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }
        KapijujaUiTheme.title(title)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        val autoSave = TextView(this).apply {
            text = t("Все изменения сохраняются автоматически при выходе.", "All changes are saved automatically when you leave.")
            textSize = 13f
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        KapijujaUiTheme.secondary(autoSave)
        root.addView(autoSave)

        SettingsStore.migrateDefaults(this)

        root.addView(
            sectionTitle(
                t(
                    "Язык интерфейса",
                    "Język interfejsu",
                    "Interface language"
                )
            )
        )
        languageButton = Button(this).apply {
            text = languageSettingLabel()
            textSize = 17f
            setOnClickListener { chooseLanguage() }
        }
        KapijujaUiTheme.button(this, languageButton)
        root.addView(languageButton, fullButton())

        root.addView(sectionTitle(t("Движок", "Engine")))
        val currentEngine = SettingsStore.engine(this)
        SettingsStore.ensureDefaultVoice(this, currentEngine, VoiceCatalog.defaultVoice(currentEngine))
        engineButton = Button(this).apply {
            text = engineLabel(SettingsStore.engine(this@SettingsActivity))
            textSize = 17f
            setOnClickListener { chooseEngine() }
        }
        KapijujaUiTheme.button(this, engineButton, primary = true)
        root.addView(engineButton, fullButton())

        root.addView(sectionTitle(t("Голос", "Voice")))
        voiceButton = Button(this).apply {
            text = currentVoiceLabel()
            textSize = 17f
            setOnClickListener { chooseVoice() }
        }
        KapijujaUiTheme.button(this, voiceButton)
        root.addView(voiceButton, fullButton())

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(dp(4), dp(5), dp(4), dp(12))
        }
        KapijujaUiTheme.secondary(statusText)
        root.addView(statusText)

        root.addView(
            sectionTitle(
                t(
                    "Библиотека",
                    "Biblioteka",
                    "Library"
                )
            )
        )
        libraryLimitButton = Button(this).apply {
            text = libraryLimitLabel(SettingsStore.libraryLimit(this@SettingsActivity))
            textSize = 17f
            setOnClickListener { chooseLibraryLimit() }
        }
        KapijujaUiTheme.button(this, libraryLimitButton)
        root.addView(libraryLimitButton, fullButton())

        libraryInfoText = TextView(this).apply {
            textSize = 13f
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        KapijujaUiTheme.secondary(libraryInfoText)
        root.addView(libraryInfoText)
        refreshLibraryInfo()

        root.addView(sectionTitle("OpenAI GPT-4o Mini TTS"))
        val note = TextView(this).apply {
            text = t("Ключ хранится только в данных приложения на этом устройстве. GitHub Secret в APK не встраивается.", "The key is stored only in this app data on this device. GitHub Secrets are never embedded in the APK.")
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        KapijujaUiTheme.secondary(note)
        root.addView(note)

        keyInput = EditText(this).apply {
            hint = "OpenAI API key (sk-...)"
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(SettingsStore.openAiKey(this@SettingsActivity))
        }
        KapijujaUiTheme.input(this, keyInput)
        root.addView(keyInput, inputParams())

        instructionInput = EditText(this).apply {
            hint = t("Инструкция голосу", "Voice instructions")
            textSize = 15f
            minLines = 4
            gravity = Gravity.TOP
            setText(SettingsStore.openAiInstructions(this@SettingsActivity))
        }
        KapijujaUiTheme.input(this, instructionInput)
        root.addView(instructionInput, inputParams())

        root.addView(sectionTitle("Microsoft Azure Speech"))

        val azureNote = TextView(this).apply {
            text =
                t(
                    "Для Azure используется отдельный Speech key и region. Если создать ресурс Free (F0), стандартные Neural-голоса можно тестировать в бесплатной квоте.",
                    "Azure uses its own Speech key and region. With a Free (F0) resource, standard Neural voices can be tested within the free quota."
                )
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        KapijujaUiTheme.secondary(azureNote)
        root.addView(azureNote)

        azureKeyInput = EditText(this).apply {
            hint = "Azure Speech key"
            textSize = 15f
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(
                SettingsStore.azureSpeechKey(
                    this@SettingsActivity
                )
            )
        }
        KapijujaUiTheme.input(this, azureKeyInput)
        root.addView(azureKeyInput, inputParams())

        azureRegionInput = EditText(this).apply {
            hint = "Azure region: switzerlandnorth"
            textSize = 15f
            setSingleLine(true)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(
                SettingsStore.azureRegion(
                    this@SettingsActivity
                )
            )
        }
        KapijujaUiTheme.input(this, azureRegionInput)
        root.addView(azureRegionInput, inputParams())

        val azureRegionNote = TextView(this).apply {
            text =
                t(
                    "Region — точный идентификатор из Azure Location/Region: только латинские буквы и цифры без пробелов. По умолчанию: switzerlandnorth.",
                    "Region is the exact Azure Location/Region identifier: lowercase letters and digits, no spaces. Default: switzerlandnorth."
                )
            textSize = 13f
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        KapijujaUiTheme.secondary(azureRegionNote)
        root.addView(azureRegionNote)

        root.addView(sectionTitle("Google Gemini 2.5 Flash TTS"))

        val googleNote = TextView(this).apply {
            text =
                t(
                    "Google Gemini TTS поддерживает русский и имеет бесплатный Developer API tier. API key создаётся в Google AI Studio. Внутри APK ключ не хранится.",
                    "Google Gemini TTS supports Russian and has a free Developer API tier. Create the API key in Google AI Studio. The key is not embedded in the APK."
                )
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        KapijujaUiTheme.secondary(googleNote)
        root.addView(googleNote)

        googleKeyInput = EditText(this).apply {
            hint = "Google Gemini API key"
            textSize = 15f
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(
                SettingsStore.googleApiKey(
                    this@SettingsActivity
                )
            )
        }
        KapijujaUiTheme.input(this, googleKeyInput)
        root.addView(googleKeyInput, inputParams())

        googleInstructionInput = EditText(this).apply {
            hint = t("Инструкция голосу Google", "Google voice instructions")
            textSize = 15f
            minLines = 4
            gravity = Gravity.TOP
            setText(
                SettingsStore.googleInstructions(
                    this@SettingsActivity
                )
            )
        }
        KapijujaUiTheme.input(this, googleInstructionInput)
        root.addView(googleInstructionInput, inputParams())

        root.addView(sectionTitle(t("Защита от расходов", "Cost protection")))
        val costNote = TextView(this).apply {
            text = t("Порог, после которого приложение обязательно покажет ориентировочную стоимость перед генерацией, €:", "Threshold above which the app must show the estimated generation cost, €:")
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        KapijujaUiTheme.secondary(costNote)
        root.addView(costNote)

        costInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", SettingsStore.confirmEuro(this@SettingsActivity)))
        }
        KapijujaUiTheme.input(this, costInput)
        root.addView(costInput, inputParams())

        root.addView(sectionTitle(t("Сервис", "Service")))

        val testOpenAi = Button(this).apply {
            text = t("Проверить OpenAI", "Test OpenAI")
            textSize = 16f
            setOnClickListener { testOpenAiConnection() }
        }
        KapijujaUiTheme.button(this, testOpenAi)
        root.addView(testOpenAi, fullButton())

        val testAzure = Button(this).apply {
            text = t("Проверить Azure Speech", "Test Azure Speech")
            textSize = 16f
            setOnClickListener {
                testAzureConnection()
            }
        }
        KapijujaUiTheme.button(this, testAzure)
        root.addView(testAzure, fullButton())

        val testGoogle = Button(this).apply {
            text = t("Проверить Google Gemini TTS", "Test Google Gemini TTS")
            textSize = 16f
            setOnClickListener {
                testGoogleConnection()
            }
        }
        KapijujaUiTheme.button(this, testGoogle)
        root.addView(testGoogle, fullButton())

        val showLog = Button(this).apply {
            text = t("Показать журнал диагностики", "Show diagnostics log")
            textSize = 16f
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Kapijuja Reader — диагностика")
                    .setMessage(AppDiagnostics.lastLines(this@SettingsActivity, 24))
                    .setNegativeButton(t("Очистить", "Clear")) { _, _ ->
                        AppDiagnostics.clear(this@SettingsActivity)
                        refreshServiceText()
                    }
                    .setPositiveButton(t("Закрыть", "Close"), null)
                    .show()
            }
        }
        KapijujaUiTheme.button(this, showLog)
        root.addView(showLog, fullButton())

        serviceText = TextView(this).apply {
            textSize = 13f
            setPadding(dp(4), dp(4), dp(4), dp(20))
        }
        KapijujaUiTheme.secondary(serviceText)
        root.addView(serviceText)

        outer.addView(root)
        setContentView(outer)
        updateStatus()
        refreshServiceText()
    }

    private fun persistSettings() {
        if (!::keyInput.isInitialized) return
        SettingsStore.setOpenAiKey(this, keyInput.text.toString())
        SettingsStore.setOpenAiInstructions(
            this,
            instructionInput.text.toString()
        )
        if (::azureKeyInput.isInitialized) {
            SettingsStore.setAzureSpeechKey(
                this,
                azureKeyInput.text.toString()
            )
            SettingsStore.setAzureRegion(
                this,
                azureRegionInput.text.toString()
            )
        }
        if (::googleKeyInput.isInitialized) {
            SettingsStore.setGoogleApiKey(
                this,
                googleKeyInput.text.toString()
            )
            SettingsStore.setGoogleInstructions(
                this,
                googleInstructionInput.text.toString()
            )
        }
        val limit = costInput.text.toString().replace(',', '.').toDoubleOrNull()
            ?: SettingsStore.confirmEuro(this)
        SettingsStore.setConfirmEuro(this, limit)
        AppDiagnostics.info(
            this,
            "Settings autosaved: engine=${SettingsStore.engine(this)} voice=${SettingsStore.voice(this)} openAiKey=${SettingsStore.openAiKey(this).isNotBlank()} azureKey=${SettingsStore.azureSpeechKey(this).isNotBlank()} azureRegion=${SettingsStore.azureRegion(this)} googleKey=${SettingsStore.googleApiKey(this).isNotBlank()}"
        )
    }

    override fun onPause() {
        persistSettings()
        super.onPause()
    }

    private fun probeInstalledEngines() {
        probeTts?.shutdown()
        probeTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                installedEngines = probeTts?.engines.orEmpty()
                AppDiagnostics.info(this, "Android TTS engines found: ${installedEngines.size}")
                updateStatus()
            } else {
                AppDiagnostics.error(this, "Android TTS engine probe failed: status=$status")
            }
        }
    }

    private fun chooseLanguage() {
        val ids =
            arrayOf(
                SettingsStore.UI_LANGUAGE_SYSTEM,
                SettingsStore.UI_LANGUAGE_RU,
                SettingsStore.UI_LANGUAGE_PL,
                SettingsStore.UI_LANGUAGE_EN
            )
        val labels =
            arrayOf(
                t(
                    "Автоматически — язык телефона",
                    "Automatycznie — język telefonu",
                    "Automatic — phone language"
                ),
                "Русский",
                "Polski",
                "English"
            )

        AlertDialog.Builder(this)
            .setTitle(
                t(
                    "Язык интерфейса",
                    "Język interfejsu",
                    "Interface language"
                )
            )
            .setSingleChoiceItems(
                labels,
                ids.indexOf(SettingsStore.uiLanguage(this))
                    .coerceAtLeast(0)
            ) { dialog, which ->
                persistSettings()
                SettingsStore.setUiLanguage(this, ids[which])
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun languageSettingLabel(): String {
        val selected = SettingsStore.uiLanguage(this)
        return when (selected) {
            SettingsStore.UI_LANGUAGE_RU -> "Русский"
            SettingsStore.UI_LANGUAGE_PL -> "Polski"
            SettingsStore.UI_LANGUAGE_EN -> "English"
            else ->
                t(
                    "Автоматически: ${UiText.languageLabel(this)}",
                    "Automatycznie: ${UiText.languageLabel(this)}",
                    "Automatic: ${UiText.languageLabel(this)}"
                )
        }
    }

    private fun chooseLibraryLimit() {
        val values = intArrayOf(100, 200, 500, 1000, 5000, 10000, 0)
        val labels = values.map { libraryLimitLabel(it) }.toTypedArray()
        val current = SettingsStore.libraryLimit(this)
        val selected = values.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(
                t(
                    "Сколько текстов хранить",
                    "Ile tekstów przechowywać",
                    "How many texts to keep"
                )
            )
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val value = values[which]
                SettingsStore.setLibraryLimit(this, value)
                val pruned = LibraryStore.enforceLimits(this)
                libraryLimitButton.text = libraryLimitLabel(value)
                refreshLibraryInfo(pruned)
                dialog.dismiss()
            }
            .show()
    }

    private fun libraryLimitLabel(value: Int): String =
        if (value == 0) {
            t(
                "Все — максимум 1 ГБ",
                "Wszystkie — maks. 1 GB",
                "All — max 1 GB"
            )
        } else {
            t(
                "Хранить последние $value",
                "Przechowuj ostatnie $value",
                "Keep latest $value"
            )
        }

    private fun refreshLibraryInfo(
        pruned: LibraryPruneResult? = null
    ) {
        if (!::libraryInfoText.isInitialized) return

        val items = LibraryStore.list(this).size
        val bytes = LibraryStore.totalBytes(this)
        val mb = bytes / (1024.0 * 1024.0)
        val base =
            t(
                "Сейчас: $items текстов, %.1f МБ. Даже в режиме «все» действует предел 1 ГБ. На главном экране тексты показываются порциями, поэтому длинный список не должен тормозить прокрутку.",
                "Teraz: $items tekstów, %.1f MB. Nawet w trybie „wszystkie” obowiązuje limit 1 GB. Na ekranie głównym teksty są wyświetlane partiami, więc długa lista nie powinna spowalniać przewijania.",
                "Now: $items texts, %.1f MB. Even in “all” mode there is a 1 GB cap. The main screen renders texts in batches so a long list should not slow scrolling."
            ).format(Locale.US, mb)

        val removed =
            if (pruned != null && pruned.removedItems > 0) {
                "\n" +
                    t(
                        "Удалено старых текстов: ${pruned.removedItems}.",
                        "Usunięto starych tekstów: ${pruned.removedItems}.",
                        "Old texts removed: ${pruned.removedItems}."
                    )
            } else {
                ""
            }

        libraryInfoText.text = base + removed
    }

    private fun chooseEngine() {
        val dynamic = mutableListOf<Pair<String, String>>()
        dynamic += SettingsStore.ENGINE_ANDROID_SYSTEM to t("Android TTS — системный", "Android TTS — system default")

        val rhVoiceInstalled =
            installedEngines.any {
                it.name == SettingsStore.RHVOICE_PACKAGE
            } ||
                RhVoiceHelper.isInstalled(this)

        if (rhVoiceInstalled) {
            dynamic += SettingsStore.ENGINE_RHVOICE to
                t("RHVoice — бесплатно, офлайн", "RHVoice — free, offline")
        } else {
            dynamic += "install:rhvoice" to
                t("RHVoice — установить бесплатно", "RHVoice — install free")
        }

        installedEngines
            .filter {
                it.name != SettingsStore.RHVOICE_PACKAGE
            }
            .forEach {
                dynamic +=
                    "android:${it.name}" to
                        "Android: ${it.label ?: it.name}"
            }

        dynamic += SettingsStore.ENGINE_OPENAI to
            "OpenAI — GPT-4o Mini TTS"
        dynamic += SettingsStore.ENGINE_EDGE to
            t("Microsoft Edge — бесплатно", "Microsoft Edge — free")
        dynamic += SettingsStore.ENGINE_SILERO to
            t("Silero v5.5 — эксперимент", "Silero v5.5 — experiment")
        dynamic += SettingsStore.ENGINE_AZURE to
            t("Microsoft Azure — нужен credential", "Microsoft Azure — credential required")
        dynamic += SettingsStore.ENGINE_GOOGLE to
            "Google Gemini 2.5 Flash TTS"

        val notReady = setOf(
            SettingsStore.ENGINE_SILERO
        )

        AlertDialog.Builder(this)
            .setTitle(t("Выберите движок", "Choose engine"))
            .setItems(dynamic.map { it.second }.toTypedArray()) { _, index ->
                val id = dynamic[index].first

                if (id == "install:rhvoice") {
                    RhVoiceHelper.showInstallDialog(
                        this@SettingsActivity
                    )
                    return@setItems
                }

                if (id in notReady) {
                    val message = when (id) {
                        SettingsStore.ENGINE_SILERO ->
                            t(
                                "Silero v5.5 пока не активирован в основном APK: Android PyTorch Lite runtime сам занимает около 72 МБ, а v5.5 распространяется как PyTorch package, не как готовый Android-модуль. Нужна отдельная адаптация модели, иначе мы просто раздуем APK без рабочего движка.",
                                "Silero v5.5 is not enabled in the base APK yet: Android PyTorch Lite alone is about 72 MB, and v5.5 is distributed as a PyTorch package rather than a ready Android module. The model needs separate adaptation; otherwise the APK would become huge without a working engine."
                            )
                        SettingsStore.ENGINE_AZURE ->
                            "Azure требует Speech key и region."
                        else ->
                            t("Этот движок пока не активен.", "This engine is not active yet.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle(t("Движок пока не активен", "Engine not active yet"))
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                    return@setItems
                }

                SettingsStore.setEngine(this, id)
                SettingsStore.ensureDefaultVoice(
                    this,
                    id,
                    VoiceCatalog.defaultVoice(id)
                )
                engineButton.text = dynamic[index].second
                voiceButton.text = currentVoiceLabel()
                AppDiagnostics.info(
                    this,
                    "Engine selected: $id"
                )
                updateStatus()
                refreshServiceText()

                if (id == SettingsStore.ENGINE_RHVOICE) {
                    loadAndroidVoices(
                        SettingsStore.RHVOICE_PACKAGE,
                        SettingsStore.ENGINE_RHVOICE
                    )
                }

                if (
                    id == SettingsStore.ENGINE_GOOGLE &&
                    SettingsStore.googleApiKey(this).isBlank()
                ) {
                    AlertDialog.Builder(this)
                        .setTitle("Google Gemini TTS")
                        .setMessage(
                            "Движок выбран. Вставьте ниже API key из Google AI Studio; настройки сохраняются автоматически."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }

                if (
                    id == SettingsStore.ENGINE_AZURE &&
                    (
                        SettingsStore.azureSpeechKey(this).isBlank() ||
                            SettingsStore.azureRegion(this).isBlank()
                        )
                ) {
                    AlertDialog.Builder(this)
                        .setTitle("Azure Speech")
                        .setMessage(
                            "Движок выбран. Введите ниже Azure Speech key и region; настройки сохраняются автоматически."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .show()
    }

    private fun chooseVoice() {
        val engine = SettingsStore.engine(this)
        if (engine.startsWith("android:")) {
            val pkg = engine.removePrefix("android:").takeIf { it != "default" }
            loadAndroidVoices(pkg, engine)
            return
        }

        val voices = VoiceCatalog.staticVoices(engine)
        if (voices.isEmpty()) {
            Toast.makeText(this, t("Для этого движка список голосов пока недоступен", "No voice list is available for this engine yet"), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(t("Голос", "Voice"))
            .setItems(voices.map { it.label }.toTypedArray()) { _, which ->
                SettingsStore.setVoice(this, engine, voices[which].id)
                voiceButton.text = voices[which].label
                AppDiagnostics.info(this, "Voice selected: engine=$engine voice=${voices[which].id}")
                refreshServiceText()
            }
            .show()
    }

    private fun loadAndroidVoices(pkg: String?, engine: String) {
        probeTts?.shutdown()
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, t("Не удалось открыть TTS движок", "Could not open TTS engine"), Toast.LENGTH_SHORT).show()
                AppDiagnostics.error(this, "Android voice probe failed: engine=$engine status=$status")
                return@OnInitListener
            }
            val voices =
                probeTts?.voices.orEmpty()
                    .sortedWith(
                        compareByDescending<android.speech.tts.Voice> {
                            engine ==
                                SettingsStore.ENGINE_RHVOICE &&
                                it.name.contains(
                                    "aleksandr",
                                    ignoreCase = true
                                )
                        }
                            .thenByDescending {
                                it.locale?.language == "ru"
                            }
                            .thenBy {
                                it.name
                            }
                    )

            if (voices.isEmpty()) {
                if (engine == SettingsStore.ENGINE_RHVOICE) {
                    AlertDialog.Builder(this)
                        .setTitle("RHVoice установлен")
                        .setMessage(
                            "Но голосовые пакеты ещё не найдены. " +
                                "Откройте RHVoice, загрузите русский язык и мужской голос Aleksandr-HQ, " +
                                "затем вернитесь в Kapijuja Reader."
                        )
                        .setNegativeButton(
                            "Позже",
                            null
                        )
                        .setPositiveButton(
                            "Открыть RHVoice"
                        ) { _, _ ->
                            RhVoiceHelper.openApp(
                                this@SettingsActivity
                            )
                        }
                        .show()
                } else {
                    Toast.makeText(
                        this,
                        t("Движок не вернул список голосов", "The engine returned no voices"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@OnInitListener
            }
            AlertDialog.Builder(this)
                .setTitle(t("Голос", "Voice"))
                .setItems(voices.map {
                    val locale = it.locale?.toLanguageTag().orEmpty()
                    "${it.name}  $locale"
                }.toTypedArray()) { _, which ->
                    SettingsStore.setVoice(this, engine, voices[which].name)
                    voiceButton.text = voices[which].name
                    AppDiagnostics.info(
                        this,
                        "Android voice selected: engine=$engine voice=${voices[which].name} locale=${voices[which].locale}"
                    )
                    refreshServiceText()
                }
                .show()
        }
        probeTts = if (pkg == null) TextToSpeech(this, listener) else TextToSpeech(this, listener, pkg)
    }

    private fun testOpenAiConnection() {
        persistSettings()
        val key = SettingsStore.openAiKey(this)
        if (key.isBlank()) {
            Toast.makeText(this, "Сначала введите OpenAI API key", Toast.LENGTH_SHORT).show()
            return
        }

        serviceText.text = "Проверка api.openai.com…"
        Thread {
            try {
                val result = OpenAiTtsClient.checkAccess(key, this)
                runOnUiThread {
                    serviceText.text = result
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                AppDiagnostics.error(this, "OpenAI test failed", t)
                runOnUiThread {
                    val message = t.message ?: "Ошибка OpenAI"
                    serviceText.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun testAzureConnection() {
        persistSettings()

        val key =
            SettingsStore.azureSpeechKey(this)
        val region =
            SettingsStore.azureRegion(this)

        if (key.isBlank() || region.isBlank()) {
            Toast.makeText(
                this,
                "Введите Azure Speech key и region",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        serviceText.text =
            "Проверка Azure Speech…"

        Thread {
            try {
                val result =
                    AzureTtsClient.checkAccess(
                        speechKey = key,
                        region = region,
                        context = this
                    )

                runOnUiThread {
                    serviceText.text = result
                    Toast.makeText(
                        this,
                        result,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (t: Throwable) {
                AppDiagnostics.error(
                    this,
                    "Azure test failed",
                    t
                )

                runOnUiThread {
                    val message =
                        t.message ?: "Ошибка Azure"
                    serviceText.text = message
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun testGoogleConnection() {
        persistSettings()

        val key =
            SettingsStore.googleApiKey(this)

        if (key.isBlank()) {
            Toast.makeText(
                this,
                "Введите Google Gemini API key",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        serviceText.text =
            "Проверка Google Gemini TTS…"

        Thread {
            try {
                val result =
                    GoogleGeminiTtsClient.checkAccess(
                        apiKey = key,
                        context = this
                    )

                runOnUiThread {
                    serviceText.text = result
                    Toast.makeText(
                        this,
                        result,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (t: Throwable) {
                AppDiagnostics.error(
                    this,
                    "Google Gemini test failed",
                    t
                )

                runOnUiThread {
                    val message =
                        t.message ?: "Ошибка Google Gemini"
                    serviceText.text = message
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun refreshServiceText() {
        if (!::serviceText.isInitialized) return
        val engine = SettingsStore.engine(this)
        val voice = SettingsStore.voice(this)
        serviceText.text =
            "Текущий движок: ${engineLabel(engine)}\n" +
                "Голос: ${voice.ifBlank { "не выбран" }}\n" +
                "OpenAI key: ${
                    if (
                        ::keyInput.isInitialized &&
                        keyInput.text.isNotBlank()
                    ) {
                        "введён"
                    } else if (
                        SettingsStore.openAiKey(this).isNotBlank()
                    ) {
                        "сохранён"
                    } else {
                        "нет"
                    }
                }\n" +
                "Azure: ${
                    if (
                        SettingsStore.azureSpeechKey(this).isNotBlank() &&
                        SettingsStore.azureRegion(this).isNotBlank()
                    ) {
                        "key + ${SettingsStore.azureRegion(this)}"
                    } else {
                        "не настроен"
                    }
                }\n" +
                "Google Gemini key: ${
                    if (
                        SettingsStore.googleApiKey(this).isNotBlank()
                    ) {
                        "сохранён"
                    } else {
                        "нет"
                    }
                }"
    }

    private fun updateStatus() {
        if (!::statusText.isInitialized) return
        val engine = SettingsStore.engine(this)
        statusText.text = when {
            engine == SettingsStore.ENGINE_OPENAI ->
                "Рабочий Speech API. При ошибке DNS выполняются до 3 безопасных попыток; таймаут не повторяется автоматически, чтобы исключить двойную оплату."
            engine == SettingsStore.ENGINE_SILERO ->
                "Голоса v5_5_ru заведены в каталог. Локальный runtime подключим следующим этапом."
            engine == SettingsStore.ENGINE_EDGE ->
                "Microsoft Edge Read Aloud подключён: бесплатная сетевая озвучка без API key. Это неофициальный endpoint Edge, поэтому протокол может измениться."
            engine == SettingsStore.ENGINE_AZURE ->
                "Azure Speech подключён через официальный REST API. Для бесплатного F0 используйте Dmitry/Svetlana/Dariya Neural; HD Lev не входит в F0."
            engine == SettingsStore.ENGINE_GOOGLE ->
                "Google Gemini 2.5 Flash TTS подключён через Developer API. Русский поддерживается; на Developer API сейчас есть бесплатный tier. Голос по умолчанию Gacrux (mature)."
            engine == SettingsStore.ENGINE_RHVOICE ->
                "RHVoice работает полностью офлайн и без API key. Для русского мужского чтения рекомендуем Aleksandr-HQ; движок и голосовые пакеты устанавливаются отдельно, поэтому Kapijuja Reader остаётся маленьким."

            engine.startsWith("android:") ->
                "Android-движки и голоса считываются с устройства. Выбранный голос перечитывается после каждого возврата из настроек."
            else -> ""
        }
    }

    private fun currentVoiceLabel(): String {
        val engine = SettingsStore.engine(this)
        val id = SettingsStore.voice(this, engine)
        if (id.isBlank()) return t("Выбрать голос", "Choose voice")
        return VoiceCatalog.staticVoices(engine).firstOrNull { it.id == id }?.label ?: id
    }

    private fun engineLabel(id: String): String = when {
        id == SettingsStore.DEFAULT_ENGINE -> t("Google Android TTS — по умолчанию", "Google Android TTS — default")
        id == SettingsStore.ENGINE_ANDROID_SYSTEM -> t("Android TTS — системный", "Android TTS — system default")
        id == SettingsStore.ENGINE_OPENAI -> "OpenAI — GPT-4o Mini TTS"
        id == SettingsStore.ENGINE_SILERO -> "Silero TTS v5.5 Russian"
        id == SettingsStore.ENGINE_EDGE -> "Microsoft Edge — тест"
        id == SettingsStore.ENGINE_AZURE ->
            "Microsoft Azure Speech"
        id == SettingsStore.ENGINE_GOOGLE ->
            "Google Gemini 2.5 Flash TTS"
        id == SettingsStore.ENGINE_RHVOICE ->
            "RHVoice — бесплатно, офлайн"
        id.startsWith("android:") ->
            "Android: ${id.removePrefix("android:")}"
        else -> id
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(4), dp(18), dp(4), dp(8))
        KapijujaUiTheme.title(this)
    }

    private fun fullButton() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
    ).apply { bottomMargin = dp(6) }

    private fun inputParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(10) }

    override fun onDestroy() {
        persistSettings()
        probeTts?.shutdown()
        super.onDestroy()
    }

    private fun t(ru: String, en: String) = UiText.get(this, ru, en)

    private fun t(ru: String, pl: String, en: String) =
        UiText.get(this, ru, pl, en)

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)
}
