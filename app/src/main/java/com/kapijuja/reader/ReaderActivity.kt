package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.BreakIterator
import java.util.Locale

class ReaderActivity : Activity() {
    private lateinit var scroll: ScrollView
    private lateinit var contentFrame: FrameLayout
    private lateinit var textView: TextView
    private lateinit var editor: EditText
    private lateinit var listenButton: Button
    private lateinit var player: LinearLayout
    private lateinit var playPause: Button
    private lateinit var engineButton: Button
    private lateinit var voiceButton: Button
    private lateinit var speedButton: Button

    private var text: String = ""
    private var title: String = ""
    private var source: String = ""
    private var libraryId: String? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var activeAndroidEngine: String? = null
    private var activeAndroidVoice: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var openAiTempFile: File? = null
    private var generationToken = 0

    private var segments: List<Segment> = emptyList()
    private var currentSegment = 0
    private var isPlaying = false
    private var editMode = false
    private var speechRate = 1.0f
    private var openAiConfirmedHash: Int? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KapijujaUiTheme.applyWindow(this)
        loadInput()
        segments = segmentText(text)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        val selectedEngine = SettingsStore.engine(this)
        val selectedVoice = SettingsStore.voice(this)
        if (ttsReady && selectedEngine.startsWith("android:") &&
            (selectedEngine != activeAndroidEngine || selectedVoice != activeAndroidVoice)
        ) {
            AppDiagnostics.info(
                this,
                "Android TTS settings changed; reinitializing engine=$selectedEngine voice=$selectedVoice"
            )
            tts?.stop()
            tts?.shutdown()
            tts = null
            ttsReady = false
            activeAndroidEngine = null
            activeAndroidVoice = null
        }
        if (::engineButton.isInitialized) updateEngineLabels()
    }

    private fun loadInput() {
        libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID)
        if (libraryId != null) {
            val item = LibraryStore.item(this, libraryId!!)
            title = item?.title ?: "Текст"
            source = item?.source.orEmpty()
            text = LibraryStore.text(this, libraryId!!)
        } else {
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Текст" }
            source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
            val path = intent.getStringExtra(EXTRA_DRAFT_PATH)
            text = path?.let { File(it).takeIf(File::exists)?.readText() }.orEmpty()
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.kapijuja_screen_bg)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        KapijujaUiTheme.applySafeArea(root)

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

        val heading = TextView(this).apply {
            text = title
            textSize = 20f
            maxLines = 2
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(12), 0, dp(8), 0)
        }
        KapijujaUiTheme.title(heading)
        top.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val settings = Button(this).apply {
            text = "⚙ Настройки"
            textSize = 13f
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, settings)
        top.addView(settings, LinearLayout.LayoutParams(dp(120), dp(52)))
        root.addView(top)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, dp(4))
        }
        listenButton = Button(this).apply {
            text = "Слушать"
            textSize = 16f
            setOnClickListener {
                if (editMode) saveEditedText() else startOrResume()
            }
        }
        KapijujaUiTheme.button(this, listenButton, primary = true)
        actionRow.addView(listenButton, LinearLayout.LayoutParams(dp(128), dp(54)))
        root.addView(actionRow)

        if (source.isNotBlank()) {
            val sourceView = TextView(this).apply {
                text = source
                textSize = 12f
                maxLines = 1
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            KapijujaUiTheme.secondary(sourceView)
            root.addView(sourceView)
        }

        scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        contentFrame = FrameLayout(this)
        textView = TextView(this).apply {
            this.text = this@ReaderActivity.text
            textSize = 20f
            setTextColor(KapijujaUiTheme.SILVER)
            setLineSpacing(dp(5).toFloat(), 1.08f)
            setPadding(dp(10), dp(14), dp(10), dp(32))
            setTextIsSelectable(true)
        }
        editor = EditText(this).apply {
            visibility = View.GONE
            textSize = 20f
            gravity = Gravity.TOP
            minLines = 14
            setLineSpacing(dp(5).toFloat(), 1.08f)
        }
        KapijujaUiTheme.input(this, editor)

        contentFrame.addView(textView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        contentFrame.addView(editor, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        scroll.addView(contentFrame)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        player = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = KapijujaUiTheme.panel(
                this@ReaderActivity,
                KapijujaUiTheme.PANEL_DARK,
                KapijujaUiTheme.BLUE,
                1,
                18
            )
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        playPause = Button(this).apply {
            text = "Пауза"
            setOnClickListener {
                if (isPlaying) pauseSpeech() else startOrResume()
            }
        }
        KapijujaUiTheme.button(this, playPause, primary = true)
        controls.addView(playPause, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
            marginEnd = dp(7)
        })

        speedButton = Button(this).apply {
            text = "1.0x"
            setOnClickListener { cycleSpeed() }
        }
        KapijujaUiTheme.button(this, speedButton)
        controls.addView(speedButton, LinearLayout.LayoutParams(0, dp(56), 0.65f).apply {
            marginEnd = dp(7)
        })

        val edit = Button(this).apply {
            text = "Редактировать"
            setOnClickListener { enterEditMode() }
        }
        KapijujaUiTheme.button(this, edit)
        controls.addView(edit, LinearLayout.LayoutParams(0, dp(56), 1.15f))
        player.addView(controls)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        engineButton = Button(this).apply {
            text = "Движок"
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, engineButton)
        voiceRow.addView(engineButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            marginEnd = dp(7)
        })

        voiceButton = Button(this).apply {
            text = "Голос"
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, voiceButton)
        voiceRow.addView(voiceButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        player.addView(voiceRow)

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        val saveText = Button(this).apply {
            text = "Сохранить файл"
            textSize = 14f
            setOnClickListener { requestTextExport() }
        }
        KapijujaUiTheme.button(this, saveText)
        saveRow.addView(saveText, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginEnd = dp(7)
        })

        val saveAudio = Button(this).apply {
            text = "Сохранить MP3"
            textSize = 14f
            setOnClickListener { requestAudioExport() }
        }
        KapijujaUiTheme.button(this, saveAudio)
        saveRow.addView(saveAudio, LinearLayout.LayoutParams(0, dp(52), 1f))
        player.addView(saveRow)

        root.addView(player)
        setContentView(root)
        updateEngineLabels()
    }

    private fun startOrResume() {
        if (text.isBlank()) return
        if (editMode) return

        if (libraryId == null) {
            libraryId = LibraryStore.add(this, title, source, text)
        }
        player.visibility = View.VISIBLE

        when (val engine = SettingsStore.engine(this)) {
            SettingsStore.ENGINE_OPENAI -> startOpenAiWithGuard()
            SettingsStore.ENGINE_SILERO,
            SettingsStore.ENGINE_EDGE,
            SettingsStore.ENGINE_AZURE,
            SettingsStore.ENGINE_GOOGLE -> {
                Toast.makeText(
                    this,
                    "Каталог этого движка уже подключён. Runtime подключается следующим этапом; сейчас выберите Android TTS/RHVoice или OpenAI.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            else -> {
                if (!engine.startsWith("android:")) {
                    Toast.makeText(this, "Неизвестный движок", Toast.LENGTH_SHORT).show()
                    return
                }
                startAndroidTts()
            }
        }
    }

    private fun startAndroidTts() {
        if (!ttsReady) {
            initAndroidTts { speakAndroidFrom(currentSegment) }
        } else {
            speakAndroidFrom(currentSegment)
        }
    }

    private fun initAndroidTts(onReady: () -> Unit) {
        tts?.shutdown()
        ttsReady = false
        val engineId = SettingsStore.engine(this)
        val packageName = engineId.removePrefix("android:").takeIf { it != "default" }

        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setSpeechRate(speechRate)
                val configuredVoice = SettingsStore.voice(this)
                var appliedVoice = tts?.voice?.name.orEmpty()

                if (configuredVoice.isNotBlank()) {
                    val voice = tts?.voices?.firstOrNull { it.name == configuredVoice }
                    if (voice != null) {
                        tts?.voice = voice
                        appliedVoice = voice.name
                        AppDiagnostics.info(
                            this,
                            "Android TTS voice applied: engine=$engineId voice=${voice.name} locale=${voice.locale}"
                        )
                    } else {
                        AppDiagnostics.error(
                            this,
                            "Configured Android voice not found: engine=$engineId voice=$configuredVoice"
                        )
                        Toast.makeText(
                            this,
                            "Выбранный голос не найден в этом движке; используется голос по умолчанию.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                activeAndroidEngine = engineId
                activeAndroidVoice = configuredVoice.ifBlank { appliedVoice }
                installProgressListener()
                updateEngineLabels()
                onReady()
            } else {
                AppDiagnostics.error(this, "Android TTS init failed: engine=$engineId status=$status")
                Toast.makeText(this, "TTS движок не запустился", Toast.LENGTH_LONG).show()
            }
        }

        AppDiagnostics.info(this, "Initializing Android TTS: engine=$engineId package=$packageName")
        tts = if (packageName == null) {
            TextToSpeech(this, listener)
        } else {
            TextToSpeech(this, listener, packageName)
        }
    }

    private fun installProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val index = utteranceId?.substringAfter("seg_")?.toIntOrNull() ?: return
                mainHandler.post {
                    currentSegment = index
                    isPlaying = true
                    playPause.text = "Пауза"
                    highlight(index)
                }
            }

            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.substringAfter("seg_")?.toIntOrNull() ?: return
                if (index == segments.lastIndex) {
                    mainHandler.post {
                        isPlaying = false
                        currentSegment = 0
                        playPause.text = "Сначала"
                        listenButton.text = "Слушать"
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    isPlaying = false
                    playPause.text = "Продолжить"
                    listenButton.text = "Слушать"
                }
            }
        })
    }

    private fun speakAndroidFrom(index: Int) {
        if (!ttsReady || segments.isEmpty()) return
        stopMediaOnly()
        tts?.stop()
        val start = index.coerceIn(0, segments.lastIndex)
        for (i in start..segments.lastIndex) {
            val mode = if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(segments[i].spoken, mode, null, "seg_$i")
        }
        isPlaying = true
        playPause.text = "Пауза"
        listenButton.text = "Читается"
    }

    private fun startOpenAiWithGuard() {
        val apiKey = SettingsStore.openAiKey(this)
        if (apiKey.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("OpenAI API key")
                .setMessage("Ключ из GitHub Secret не помещается в APK из соображений безопасности. Для личного теста введите API key в Настройках; позднее заменим это на серверный relay.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Настройки") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .show()
            return
        }

        val cost = OpenAiTtsClient.estimatedCostEuro(text)
        val threshold = SettingsStore.confirmEuro(this)
        val hash = text.hashCode()

        if (openAiConfirmedHash == hash || cost < threshold) {
            startOpenAiFrom(currentSegment)
            return
        }

        val mins = OpenAiTtsClient.estimatedMinutes(text)
        AlertDialog.Builder(this)
            .setTitle("Платная озвучка OpenAI")
            .setMessage(
                "Текст примерно на ${String.format(Locale.US, "%.1f", mins)} мин. " +
                    "Ориентировочная стоимость ≈ €${String.format(Locale.US, "%.2f", cost)}. " +
                    "Фактическая стоимость зависит от длительности аудио. Продолжить?"
            )
            .setNegativeButton("Нет", null)
            .setPositiveButton("Озвучить") { _, _ ->
                openAiConfirmedHash = hash
                startOpenAiFrom(currentSegment)
            }
            .show()
    }

    private fun startOpenAiFrom(index: Int) {
        if (segments.isEmpty()) return
        tts?.stop()
        ttsReady = false
        stopMediaOnly()
        generationToken += 1
        val token = generationToken
        isPlaying = true
        playPause.text = "Пауза"
        listenButton.text = "Готовится…"
        playOpenAiSegment(index.coerceIn(0, segments.lastIndex), token)
    }

    private fun playOpenAiSegment(index: Int, token: Int) {
        if (token != generationToken || index !in segments.indices) return
        currentSegment = index
        highlight(index)
        val segment = segments[index]
        val apiKey = SettingsStore.openAiKey(this)
        val voice = SettingsStore.voice(this).ifBlank { "cedar" }
        val instructions = SettingsStore.openAiInstructions(this)

        Thread {
            try {
                val bytes = OpenAiTtsClient.synthesize(
                    apiKey = apiKey,
                    text = segment.spoken,
                    voice = voice,
                    instructions = instructions,
                    speed = speechRate,
                    context = this@ReaderActivity
                )
                if (token != generationToken) return@Thread

                val file = File(cacheDir, "openai_tts_$token.mp3")
                file.writeBytes(bytes)
                mainHandler.post {
                    if (token != generationToken) {
                        file.delete()
                        return@post
                    }
                    // Stop/delete the PREVIOUS player before registering the newly
                    // generated file. 0.1.1 did this in the opposite order and
                    // deleted its own MP3 immediately before setDataSource().
                    stopMediaOnly()
                    openAiTempFile = file

                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener {
                                it.release()
                                mediaPlayer = null
                                if (openAiTempFile == file) openAiTempFile = null
                                file.delete()
                                if (token != generationToken) return@setOnCompletionListener
                                if (index >= segments.lastIndex) {
                                    this@ReaderActivity.isPlaying = false
                                    currentSegment = 0
                                    playPause.text = "Сначала"
                                    listenButton.text = "Слушать"
                                } else {
                                    playOpenAiSegment(index + 1, token)
                                }
                            }
                            setOnErrorListener { mp, what, extra ->
                                AppDiagnostics.error(
                                    this@ReaderActivity,
                                    "MediaPlayer error: what=$what extra=$extra fileExists=${file.exists()} size=${file.length()}"
                                )
                                mp.release()
                                mediaPlayer = null
                                if (openAiTempFile == file) openAiTempFile = null
                                file.delete()
                                this@ReaderActivity.isPlaying = false
                                playPause.text = "Продолжить"
                                listenButton.text = "Слушать"
                                true
                            }
                            prepare()
                            start()
                        }
                        AppDiagnostics.info(
                            this@ReaderActivity,
                            "OpenAI playback started: segment=$index file=${file.name} bytes=${file.length()}"
                        )
                        listenButton.text = "Читается"
                    } catch (t: Throwable) {
                        if (openAiTempFile == file) openAiTempFile = null
                        file.delete()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        this@ReaderActivity.isPlaying = false
                        playPause.text = "Продолжить"
                        listenButton.text = "Слушать"
                        AppDiagnostics.error(this@ReaderActivity, "OpenAI MediaPlayer setup failed", t)
                        Toast.makeText(
                            this@ReaderActivity,
                            "Ошибка воспроизведения OpenAI: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (t: Throwable) {
                AppDiagnostics.error(this@ReaderActivity, "OpenAI TTS segment failed: index=$index", t)
                mainHandler.post {
                    if (token != generationToken) return@post
                    isPlaying = false
                    playPause.text = "Продолжить"
                    listenButton.text = "Слушать"
                    Toast.makeText(
                        this,
                        t.message ?: "Ошибка OpenAI TTS",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun pauseSpeech() {
        generationToken += 1
        tts?.stop()
        stopMediaOnly()
        isPlaying = false
        if (::playPause.isInitialized) playPause.text = "Продолжить"
        if (::listenButton.isInitialized && !editMode) listenButton.text = "Слушать"
    }

    private fun stopMediaOnly() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
        openAiTempFile?.delete()
        openAiTempFile = null
    }

    private fun enterEditMode() {
        pauseSpeech()
        editMode = true
        editor.setText(text)
        editor.setSelection(editor.text.length)
        textView.visibility = View.GONE
        editor.visibility = View.VISIBLE
        player.visibility = View.GONE
        listenButton.text = "Сохранить"
        editor.requestFocus()
    }

    private fun saveEditedText() {
        val updated = editor.text.toString().trim()
        if (updated.isBlank()) {
            Toast.makeText(this, "Текст пустой", Toast.LENGTH_SHORT).show()
            return
        }

        text = updated
        segments = segmentText(text)
        currentSegment = 0
        openAiConfirmedHash = null

        if (libraryId == null) {
            libraryId = LibraryStore.add(this, title, source, text)
        } else {
            LibraryStore.update(this, libraryId!!, title, source, text)
        }

        textView.text = text
        editor.visibility = View.GONE
        textView.visibility = View.VISIBLE
        editMode = false
        listenButton.text = "Слушать"
        player.visibility = View.VISIBLE
        Toast.makeText(this, "Текст сохранён", Toast.LENGTH_SHORT).show()
    }

    private fun highlight(index: Int) {
        if (index !in segments.indices || editMode) return
        val seg = segments[index]
        val span = SpannableString(text)
        span.setSpan(
            BackgroundColorSpan(Color.rgb(8, 54, 126)),
            seg.start,
            seg.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            ForegroundColorSpan(Color.WHITE),
            seg.start,
            seg.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView.text = span
        textView.post {
            val layout = textView.layout ?: return@post
            val line = layout.getLineForOffset(seg.start.coerceAtMost(text.length))
            val y = (layout.getLineTop(line) - scroll.height / 3).coerceAtLeast(0)
            scroll.smoothScrollTo(0, y)
        }
    }

    private fun cycleSpeed() {
        val values = floatArrayOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f)
        val next = values.firstOrNull { it > speechRate + 0.01f } ?: values.first()
        speechRate = next
        speedButton.text = String.format(Locale.US, "%.1fx", speechRate)
        tts?.setSpeechRate(speechRate)

        if (isPlaying) {
            val engine = SettingsStore.engine(this)
            pauseSpeech()
            if (engine == SettingsStore.ENGINE_OPENAI) {
                startOpenAiFrom(currentSegment)
            } else if (engine.startsWith("android:")) {
                startAndroidTts()
            }
        }
    }

    private fun updateEngineLabels() {
        val engine = SettingsStore.engine(this)
        engineButton.text = when {
            engine == SettingsStore.ENGINE_OPENAI -> "OpenAI"
            engine == SettingsStore.ENGINE_SILERO -> "Silero"
            engine == SettingsStore.ENGINE_EDGE -> "Edge"
            engine == SettingsStore.ENGINE_AZURE -> "Microsoft"
            engine == SettingsStore.ENGINE_GOOGLE -> "Google"
            engine == SettingsStore.DEFAULT_ENGINE -> "Android TTS"
            engine.startsWith("android:") -> engine.removePrefix("android:").substringAfterLast('.').take(18)
            else -> "Движок"
        }
        voiceButton.text = SettingsStore.voice(this).ifBlank { "Голос" }.take(20)
    }

    private fun requestTextExport() {
        if (editMode) saveEditedText()
        val fileName = safeFileName(title) + ".txt"
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, fileName)
            },
            REQ_SAVE_TEXT
        )
    }

    private fun requestAudioExport() {
        if (SettingsStore.engine(this) != SettingsStore.ENGINE_OPENAI) {
            Toast.makeText(
                this,
                "MP3-экспорт в этой версии уже работает для OpenAI. Для локальных Android/Silero добавим отдельный экспорт следующим этапом.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (SettingsStore.openAiKey(this).isBlank()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val cost = OpenAiTtsClient.estimatedCostEuro(text)
        val mins = OpenAiTtsClient.estimatedMinutes(text)
        AlertDialog.Builder(this)
            .setTitle("Создать MP3 через OpenAI?")
            .setMessage(
                "Это отдельная генерация всего текста: ≈ ${String.format(Locale.US, "%.1f", mins)} мин, " +
                    "ориентировочно €${String.format(Locale.US, "%.2f", cost)}."
            )
            .setNegativeButton("Нет", null)
            .setPositiveButton("Создать") { _, _ ->
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/mpeg"
                        putExtra(Intent.EXTRA_TITLE, safeFileName(title) + ".mp3")
                    },
                    REQ_SAVE_AUDIO
                )
            }
            .show()
    }

    @Deprecated("legacy result handling is sufficient for this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_SAVE_TEXT -> exportText(uri)
            REQ_SAVE_AUDIO -> exportOpenAiMp3(uri)
        }
    }

    private fun exportText(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("Не удалось открыть файл")
            Toast.makeText(this, "Файл сохранён", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, t.message ?: "Ошибка сохранения", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportOpenAiMp3(uri: Uri) {
        pauseSpeech()
        listenButton.text = "Создаётся MP3…"
        val apiKey = SettingsStore.openAiKey(this)
        val voice = SettingsStore.voice(this).ifBlank { "cedar" }
        val instructions = SettingsStore.openAiInstructions(this)
        val chunks = OpenAiTtsClient.splitForApi(text)

        Thread {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    chunks.forEachIndexed { index, chunk ->
                        val bytes = OpenAiTtsClient.synthesize(
                            apiKey = apiKey,
                            text = chunk,
                            voice = voice,
                            instructions = instructions,
                            speed = speechRate,
                            context = this@ReaderActivity
                        )
                        output.write(
                            if (index == 0) bytes else OpenAiTtsClient.stripLeadingId3(bytes)
                        )
                    }
                } ?: error("Не удалось открыть файл")

                mainHandler.post {
                    listenButton.text = "Слушать"
                    Toast.makeText(this, "MP3 сохранён", Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                mainHandler.post {
                    listenButton.text = "Слушать"
                    Toast.makeText(
                        this,
                        t.message ?: "Ошибка создания MP3",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
            .trim()
            .take(70)
            .ifBlank { "kapijuja-reader" }

    private fun segmentText(value: String): List<Segment> {
        if (value.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale("ru"))
        iterator.setText(value)
        val result = mutableListOf<Segment>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            addSegmentParts(value, start, end, result)
            start = end
            end = iterator.next()
        }
        if (result.isEmpty()) addSegmentParts(value, 0, value.length, result)
        return result
    }

    private fun addSegmentParts(value: String, start: Int, end: Int, out: MutableList<Segment>) {
        var s = start
        while (s < end) {
            while (s < end && value[s].isWhitespace()) s++
            if (s >= end) break
            var e = minOf(s + 2800, end)
            if (e < end) {
                val candidate = value.lastIndexOfAny(charArrayOf(' ', '\n', ',', ';'), e)
                if (candidate > s + 600) e = candidate + 1
            }
            while (e > s && value[e - 1].isWhitespace()) e--
            if (e > s) out.add(Segment(s, e, value.substring(s, e)))
            s = maxOf(e, s + 1)
        }
    }

    override fun onDestroy() {
        generationToken += 1
        tts?.stop()
        tts?.shutdown()
        stopMediaOnly()
        super.onDestroy()
    }

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)

    data class Segment(val start: Int, val end: Int, val spoken: String)

    companion object {
        const val EXTRA_LIBRARY_ID = "library_id"
        const val EXTRA_DRAFT_PATH = "draft_path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE = "source"

        private const val REQ_SAVE_TEXT = 1201
        private const val REQ_SAVE_AUDIO = 1202
    }
}
