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
    private lateinit var serviceText: TextView

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
            text = "⚙ Настройки"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }
        KapijujaUiTheme.title(title)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        val autoSave = TextView(this).apply {
            text = "Все изменения сохраняются автоматически при выходе."
            textSize = 13f
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        KapijujaUiTheme.secondary(autoSave)
        root.addView(autoSave)

        root.addView(sectionTitle("Движок"))
        val currentEngine = SettingsStore.engine(this)
        SettingsStore.ensureDefaultVoice(this, currentEngine, VoiceCatalog.defaultVoice(currentEngine))
        engineButton = Button(this).apply {
            text = engineLabel(SettingsStore.engine(this@SettingsActivity))
            textSize = 17f
            setOnClickListener { chooseEngine() }
        }
        KapijujaUiTheme.button(this, engineButton, primary = true)
        root.addView(engineButton, fullButton())

        root.addView(sectionTitle("Голос"))
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

        root.addView(sectionTitle("OpenAI GPT-4o Mini TTS"))
        val note = TextView(this).apply {
            text = "Ключ хранится только в данных приложения на этом устройстве. GitHub Secret в APK не встраивается."
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
            hint = "Инструкция голосу"
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
                "Для Azure используется отдельный Speech key и region. " +
                    "Если создать ресурс Free (F0), стандартные Neural-голоса можно тестировать в бесплатной квоте."
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
            hint = "Azure region, например westeurope"
            textSize = 15f
            setSingleLine(true)
            setText(
                SettingsStore.azureRegion(
                    this@SettingsActivity
                )
            )
        }
        KapijujaUiTheme.input(this, azureRegionInput)
        root.addView(azureRegionInput, inputParams())

        root.addView(sectionTitle("Защита от расходов"))
        val costNote = TextView(this).apply {
            text = "Порог, после которого приложение обязательно покажет ориентировочную стоимость перед генерацией, €:"
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

        root.addView(sectionTitle("Сервис"))

        val testOpenAi = Button(this).apply {
            text = "Проверить OpenAI"
            textSize = 16f
            setOnClickListener { testOpenAiConnection() }
        }
        KapijujaUiTheme.button(this, testOpenAi)
        root.addView(testOpenAi, fullButton())

        val testAzure = Button(this).apply {
            text = "Проверить Azure Speech"
            textSize = 16f
            setOnClickListener {
                testAzureConnection()
            }
        }
        KapijujaUiTheme.button(this, testAzure)
        root.addView(testAzure, fullButton())

        val showLog = Button(this).apply {
            text = "Показать журнал диагностики"
            textSize = 16f
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Kapijuja Reader — диагностика")
                    .setMessage(AppDiagnostics.lastLines(this@SettingsActivity, 24))
                    .setNegativeButton("Очистить") { _, _ ->
                        AppDiagnostics.clear(this@SettingsActivity)
                        refreshServiceText()
                    }
                    .setPositiveButton("Закрыть", null)
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
        val limit = costInput.text.toString().replace(',', '.').toDoubleOrNull()
            ?: SettingsStore.confirmEuro(this)
        SettingsStore.setConfirmEuro(this, limit)
        AppDiagnostics.info(
            this,
            "Settings autosaved: engine=${SettingsStore.engine(this)} voice=${SettingsStore.voice(this)} openAiKey=${SettingsStore.openAiKey(this).isNotBlank()} azureKey=${SettingsStore.azureSpeechKey(this).isNotBlank()} azureRegion=${SettingsStore.azureRegion(this)}"
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

    private fun chooseEngine() {
        val dynamic = mutableListOf<Pair<String, String>>()
        dynamic += SettingsStore.DEFAULT_ENGINE to "Android TTS — системный"

        val rhVoiceInstalled =
            installedEngines.any {
                it.name == SettingsStore.RHVOICE_PACKAGE
            } ||
                RhVoiceHelper.isInstalled(this)

        if (rhVoiceInstalled) {
            dynamic += SettingsStore.ENGINE_RHVOICE to
                "RHVoice — бесплатно, офлайн"
        } else {
            dynamic += "install:rhvoice" to
                "RHVoice — установить бесплатно"
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
            "Microsoft Edge — бесплатно"
        dynamic += SettingsStore.ENGINE_SILERO to
            "Silero v5.5 — скоро"
        dynamic += SettingsStore.ENGINE_AZURE to
            "Microsoft Azure — нужен credential"
        dynamic += SettingsStore.ENGINE_GOOGLE to
            "Google — нужен credential"

        val notReady = setOf(
            SettingsStore.ENGINE_SILERO,
            SettingsStore.ENGINE_GOOGLE
        )

        AlertDialog.Builder(this)
            .setTitle("Выберите движок")
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
                            "Silero пока не встроен в APK: модель и локальный runtime подключим отдельным этапом."
                        SettingsStore.ENGINE_AZURE ->
                            "Azure требует Speech key и region."
                        else ->
                            "Google cloud/AI требует отдельный Google credential. До его подключения движок не активируется."
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Движок пока не активен")
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
            Toast.makeText(this, "Для этого движка список голосов пока недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Голос")
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
                Toast.makeText(this, "Не удалось открыть TTS движок", Toast.LENGTH_SHORT).show()
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
                        "Движок не вернул список голосов",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@OnInitListener
            }
            AlertDialog.Builder(this)
                .setTitle("Голос")
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
                "Каталог русских Google-голосов есть; runtime/credential пока не подключён."
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
        if (id.isBlank()) return "Выбрать голос"
        return VoiceCatalog.staticVoices(engine).firstOrNull { it.id == id }?.label ?: id
    }

    private fun engineLabel(id: String): String = when {
        id == SettingsStore.DEFAULT_ENGINE -> "Android TTS — системный"
        id == SettingsStore.ENGINE_OPENAI -> "OpenAI — GPT-4o Mini TTS"
        id == SettingsStore.ENGINE_SILERO -> "Silero TTS v5.5 Russian"
        id == SettingsStore.ENGINE_EDGE -> "Microsoft Edge — тест"
        id == SettingsStore.ENGINE_AZURE ->
            "Microsoft Azure Speech"
        id == SettingsStore.ENGINE_GOOGLE ->
            "Google Cloud TTS — тест"
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

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)
}
