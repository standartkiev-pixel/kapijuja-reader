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

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 30f
            setOnClickListener { finish() }
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

        root.addView(sectionTitle("Движок"))
        engineButton = Button(this).apply {
            text = engineLabel(SettingsStore.engine(this@SettingsActivity))
            textSize = 17f
            setOnClickListener { chooseEngine() }
        }
        KapijujaUiTheme.button(this, engineButton, primary = true)
        root.addView(engineButton, fullButton())

        root.addView(sectionTitle("Голос"))
        voiceButton = Button(this).apply {
            text = SettingsStore.voice(this@SettingsActivity).ifBlank { "Выбрать голос" }
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
            text = "Для личного теста ключ можно ввести на устройстве. В финальной версии ключ не должен находиться внутри APK — заменим это на серверный relay."
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

        root.addView(sectionTitle("Защита от расходов"))
        val costNote = TextView(this).apply {
            text = "Перед дорогим чтением программа показывает примерную длительность и цену. Порог предупреждения, €:"
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

        val save = Button(this).apply {
            text = "Сохранить настройки"
            textSize = 18f
            setOnClickListener {
                SettingsStore.setOpenAiKey(this@SettingsActivity, keyInput.text.toString())
                SettingsStore.setOpenAiInstructions(this@SettingsActivity, instructionInput.text.toString())
                val limit = costInput.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.03
                SettingsStore.setConfirmEuro(this@SettingsActivity, limit)
                Toast.makeText(this@SettingsActivity, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        KapijujaUiTheme.button(this, save, primary = true)
        root.addView(save, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60)
        ).apply { topMargin = dp(12) })

        outer.addView(root)
        setContentView(outer)
        updateStatus()
    }

    private fun probeInstalledEngines() {
        probeTts?.shutdown()
        probeTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                installedEngines = probeTts?.engines.orEmpty()
                updateStatus()
            }
        }
    }

    private fun chooseEngine() {
        val dynamic = mutableListOf<Pair<String, String>>()
        dynamic += SettingsStore.DEFAULT_ENGINE to "Android TTS — системный"
        installedEngines.forEach {
            dynamic += "android:${it.name}" to "Android: ${it.label ?: it.name}"
        }
        dynamic += SettingsStore.ENGINE_OPENAI to "OpenAI — GPT-4o Mini TTS"
        dynamic += SettingsStore.ENGINE_SILERO to "Silero TTS v5.5 Russian"
        dynamic += SettingsStore.ENGINE_EDGE to "Microsoft Edge — тест"
        dynamic += SettingsStore.ENGINE_AZURE to "Microsoft Azure — тест"
        dynamic += SettingsStore.ENGINE_GOOGLE to "Google Cloud TTS — тест"

        AlertDialog.Builder(this)
            .setTitle("Выберите движок")
            .setItems(dynamic.map { it.second }.toTypedArray()) { _, index ->
                val id = dynamic[index].first
                SettingsStore.setEngine(this, id)
                SettingsStore.setVoice(this, VoiceCatalog.defaultVoice(id))
                engineButton.text = dynamic[index].second
                voiceButton.text = SettingsStore.voice(this).ifBlank { "Выбрать голос" }
                updateStatus()
            }
            .show()
    }

    private fun chooseVoice() {
        val engine = SettingsStore.engine(this)
        if (engine.startsWith("android:")) {
            val pkg = engine.removePrefix("android:").takeIf { it != "default" }
            loadAndroidVoices(pkg)
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
                SettingsStore.setVoice(this, voices[which].id)
                voiceButton.text = voices[which].label
            }
            .show()
    }

    private fun loadAndroidVoices(pkg: String?) {
        probeTts?.shutdown()
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, "Не удалось открыть TTS движок", Toast.LENGTH_SHORT).show()
                return@OnInitListener
            }
            val voices = probeTts?.voices.orEmpty()
                .sortedWith(compareByDescending<android.speech.tts.Voice> {
                    it.locale?.language == "ru"
                }.thenBy { it.name })
            if (voices.isEmpty()) {
                Toast.makeText(this, "Движок не вернул список голосов", Toast.LENGTH_SHORT).show()
                return@OnInitListener
            }
            AlertDialog.Builder(this)
                .setTitle("Голос")
                .setItems(voices.map {
                    val locale = it.locale?.toLanguageTag().orEmpty()
                    "${it.name}  $locale"
                }.toTypedArray()) { _, which ->
                    SettingsStore.setVoice(this, voices[which].name)
                    voiceButton.text = voices[which].name
                }
                .show()
        }
        probeTts = if (pkg == null) TextToSpeech(this, listener) else TextToSpeech(this, listener, pkg)
    }

    private fun updateStatus() {
        val engine = SettingsStore.engine(this)
        statusText.text = when {
            engine == SettingsStore.ENGINE_OPENAI ->
                "Подключён Speech API. Встроенные голоса берутся из текущего каталога OpenAI."
            engine == SettingsStore.ENGINE_SILERO ->
                "Голоса v5_5_ru уже заведены в каталог. Локальный runtime модели подключается следующим этапом."
            engine == SettingsStore.ENGINE_EDGE ->
                "Экспериментальный сетевой движок. Runtime будет подключён отдельным адаптером."
            engine == SettingsStore.ENGINE_AZURE ->
                "Каталог русских Azure-голосов добавлен. Для реального вызова потребуется Azure credential."
            engine == SettingsStore.ENGINE_GOOGLE ->
                "Каталог русских Google-голосов добавлен. Для реального вызова потребуется Google credential."
            engine.startsWith("android:") ->
                "Android-движки и их голоса считываются непосредственно с устройства. RHVoice появится здесь автоматически после установки."
            else -> ""
        }
    }

    private fun engineLabel(id: String): String = when {
        id == SettingsStore.DEFAULT_ENGINE -> "Android TTS — системный"
        id == SettingsStore.ENGINE_OPENAI -> "OpenAI — GPT-4o Mini TTS"
        id == SettingsStore.ENGINE_SILERO -> "Silero TTS v5.5 Russian"
        id == SettingsStore.ENGINE_EDGE -> "Microsoft Edge — тест"
        id == SettingsStore.ENGINE_AZURE -> "Microsoft Azure — тест"
        id == SettingsStore.ENGINE_GOOGLE -> "Google Cloud TTS — тест"
        id.startsWith("android:") -> "Android: ${id.removePrefix("android:")}"
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
        probeTts?.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)
}
