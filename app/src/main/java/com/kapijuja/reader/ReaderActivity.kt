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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs

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
    private lateinit var exportProgress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var resultText: TextView

    private var text: String = ""
    private var title: String = ""
    private var source: String = ""
    private var libraryId: String? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var activeAndroidEngine: String? = null
    private var activeAndroidVoice: String? = null

    private var mediaPlayer: MediaPlayer? = null
    private var activeTempFile: File? = null
    private var generationToken = 0

    private var segments: List<Segment> = emptyList()
    private var currentSegment = 0
    private var isPlaying = false
    private var editMode = false
    private var speechRate = 1.0f
    private var openAiConfirmedHash: Int? = null
    private var openAiRunAudioMillis = 0L
    private var pendingExportEngine: String? = null

    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapDownAt = 0L

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
            setPadding(dp(14), dp(8), dp(14), dp(10))
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
        top.addView(
            heading,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

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
            setPadding(0, dp(5), 0, dp(4))
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
                setPadding(dp(6), dp(5), dp(6), dp(5))
            }
            KapijujaUiTheme.secondary(sourceView)
            root.addView(sourceView)
        }

        scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        contentFrame = FrameLayout(this)

        textView = TextView(this).apply {
            text = this@ReaderActivity.text
            textSize = 20f
            setTextColor(KapijujaUiTheme.SILVER)
            setLineSpacing(dp(5).toFloat(), 1.08f)
            setPadding(dp(10), dp(12), dp(10), dp(28))
            setTextIsSelectable(true)
        }
        installTapStart()

        editor = EditText(this).apply {
            visibility = View.GONE
            textSize = 20f
            gravity = Gravity.TOP
            minLines = 14
            setLineSpacing(dp(5).toFloat(), 1.08f)
        }
        KapijujaUiTheme.input(this, editor)

        contentFrame.addView(
            textView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        contentFrame.addView(
            editor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        scroll.addView(contentFrame)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        player = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(9), dp(10), dp(9))
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
        controls.addView(
            playPause,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginEnd = dp(7)
            }
        )

        speedButton = Button(this).apply {
            text = "1.0x"
            setOnClickListener { cycleSpeed() }
        }
        KapijujaUiTheme.button(this, speedButton)
        controls.addView(
            speedButton,
            LinearLayout.LayoutParams(0, dp(54), 0.65f).apply {
                marginEnd = dp(7)
            }
        )

        val edit = Button(this).apply {
            text = "Редактировать"
            setOnClickListener { enterEditMode() }
        }
        KapijujaUiTheme.button(this, edit)
        controls.addView(edit, LinearLayout.LayoutParams(0, dp(54), 1.15f))
        player.addView(controls)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }

        engineButton = Button(this).apply {
            text = "Движок"
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, engineButton)
        voiceRow.addView(
            engineButton,
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(7)
            }
        )

        voiceButton = Button(this).apply {
            text = "Голос"
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, voiceButton)
        voiceRow.addView(voiceButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        player.addView(voiceRow)

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }

        val saveText = Button(this).apply {
            text = "Сохранить файл"
            textSize = 14f
            setOnClickListener { requestTextExport() }
        }
        KapijujaUiTheme.button(this, saveText)
        saveRow.addView(
            saveText,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                marginEnd = dp(7)
            }
        )

        val saveAudio = Button(this).apply {
            text = "Сохранить MP3"
            textSize = 14f
            setOnClickListener { requestAudioExport() }
        }
        KapijujaUiTheme.button(this, saveAudio)
        saveRow.addView(saveAudio, LinearLayout.LayoutParams(0, dp(50), 1f))
        player.addView(saveRow)

        exportProgress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        KapijujaUiTheme.progress(exportProgress)
        player.addView(
            exportProgress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
            ).apply {
                topMargin = dp(7)
            }
        )

        progressText = TextView(this).apply {
            textSize = 13f
            visibility = View.GONE
            setPadding(dp(3), dp(3), dp(3), 0)
        }
        KapijujaUiTheme.secondary(progressText)
        player.addView(progressText)

        resultText = TextView(this).apply {
            textSize = 13f
            setPadding(dp(3), dp(3), dp(3), 0)
        }
        KapijujaUiTheme.secondary(resultText)
        player.addView(resultText)

        root.addView(player)
        setContentView(root)
        updateEngineLabels()
    }

    private fun installTapStart() {
        textView.setOnTouchListener { _, event ->
            if (editMode) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tapDownX = event.x
                    tapDownY = event.y
                    tapDownAt = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - tapDownAt
                    val moved =
                        abs(event.x - tapDownX) + abs(event.y - tapDownY)
                    if (elapsed < 450 && moved < dp(18)) {
                        chooseStartAt(event.x, event.y)
                    }
                }
            }
            false
        }
    }

    private fun chooseStartAt(x: Float, y: Float) {
        val layout = textView.layout ?: return
        if (segments.isEmpty()) return

        val localY = (y - textView.totalPaddingTop).toInt().coerceAtLeast(0)
        val line = layout.getLineForVertical(localY)
            .coerceIn(0, layout.lineCount - 1)
        val localX = (x - textView.totalPaddingLeft).coerceAtLeast(0f)
        val offset = layout.getOffsetForHorizontal(line, localX)
            .coerceIn(0, text.length)

        val index = segments.indexOfFirst { offset < it.end }
            .let { if (it >= 0) it else segments.lastIndex }

        val wasPlaying = isPlaying
        if (wasPlaying) pauseSpeech()

        currentSegment = index
        player.visibility = View.VISIBLE
        highlight(index)
        listenButton.text = if (wasPlaying) "Готовится…" else "Слушать отсюда"
        playPause.text = "Продолжить"
        resultText.text = "Старт: предложение ${index + 1} из ${segments.size}"

        AppDiagnostics.info(
            this,
            "Playback cursor selected: offset=$offset segment=$index playing=$wasPlaying"
        )

        if (wasPlaying) {
            startOrResume()
        }
    }

    private fun startOrResume() {
        if (text.isBlank() || editMode) return

        if (libraryId == null) {
            libraryId = LibraryStore.add(this, title, source, text)
        }
        player.visibility = View.VISIBLE

        when (val engine = SettingsStore.engine(this)) {
            SettingsStore.ENGINE_OPENAI -> startOpenAiWithGuard()
            SettingsStore.ENGINE_EDGE -> startEdgeFrom(currentSegment)
            SettingsStore.ENGINE_SILERO ->
                showUnavailable(
                    "Silero пока не встроен в APK. Этот пункт больше не перебрасывает в настройки; локальный runtime подключим отдельно."
                )
            SettingsStore.ENGINE_AZURE ->
                startAzureFrom(currentSegment)
            SettingsStore.ENGINE_GOOGLE ->
                showUnavailable(
                    "Google TTS/AI требует отдельный Google credential. Сейчас этот профиль не активируется без него."
                )
            else -> {
                if (!engine.startsWith("android:")) {
                    showUnavailable("Неизвестный движок: $engine")
                    return
                }
                startAndroidTts()
            }
        }
    }

    private fun showUnavailable(message: String) {
        isPlaying = false
        listenButton.text = "Слушать"
        playPause.text = "Продолжить"
        resultText.text = message
        AlertDialog.Builder(this)
            .setTitle("Движок пока не активен")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
        val packageName =
            engineId.removePrefix("android:").takeIf { it != "default" }

        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setSpeechRate(speechRate)
                val configuredVoice = SettingsStore.voice(this)
                var appliedVoice = tts?.voice?.name.orEmpty()

                if (configuredVoice.isNotBlank()) {
                    val voice =
                        tts?.voices?.firstOrNull { it.name == configuredVoice }
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
                            "Выбранный голос не найден; используется голос по умолчанию.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                activeAndroidEngine = engineId
                activeAndroidVoice =
                    configuredVoice.ifBlank { appliedVoice }
                installProgressListener()
                updateEngineLabels()
                onReady()
            } else {
                AppDiagnostics.error(
                    this,
                    "Android TTS init failed: engine=$engineId status=$status"
                )
                Toast.makeText(
                    this,
                    "TTS движок не запустился",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        AppDiagnostics.info(
            this,
            "Initializing Android TTS: engine=$engineId package=$packageName"
        )
        tts =
            if (packageName == null) {
                TextToSpeech(this, listener)
            } else {
                TextToSpeech(this, listener, packageName)
            }
    }

    private fun installProgressListener() {
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index =
                        utteranceId?.substringAfter("seg_")?.toIntOrNull()
                            ?: return
                    mainHandler.post {
                        currentSegment = index
                        isPlaying = true
                        playPause.text = "Пауза"
                        listenButton.text = "Читается"
                        highlight(index)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    val index =
                        utteranceId?.substringAfter("seg_")?.toIntOrNull()
                            ?: return
                    if (index == segments.lastIndex) {
                        mainHandler.post {
                            isPlaying = false
                            currentSegment = 0
                            playPause.text = "Сначала"
                            listenButton.text = "Слушать"
                            resultText.text = "Чтение завершено."
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        isPlaying = false
                        playPause.text = "Продолжить"
                        listenButton.text = "Слушать"
                        resultText.text = "Ошибка Android TTS."
                    }
                }
            }
        )
    }

    private fun speakAndroidFrom(index: Int) {
        if (!ttsReady || segments.isEmpty()) return
        stopMediaOnly()
        tts?.stop()

        val start = index.coerceIn(0, segments.lastIndex)
        for (i in start..segments.lastIndex) {
            val mode =
                if (i == start) TextToSpeech.QUEUE_FLUSH
                else TextToSpeech.QUEUE_ADD
            tts?.speak(
                segments[i].spoken,
                mode,
                null,
                "seg_$i"
            )
        }

        isPlaying = true
        playPause.text = "Пауза"
        listenButton.text = "Читается"
        resultText.text = ""
    }

    private fun startOpenAiWithGuard() {
        val apiKey = SettingsStore.openAiKey(this)
        if (apiKey.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("OpenAI API key")
                .setMessage(
                    "Введите API key в Настройках. GitHub Secret внутрь APK не встраивается."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Настройки") { _, _ ->
                    startActivity(
                        Intent(
                            this,
                            SettingsActivity::class.java
                        )
                    )
                }
                .show()
            return
        }

        val remaining =
            if (currentSegment in segments.indices) {
                text.substring(segments[currentSegment].start)
            } else {
                text
            }

        val cost = OpenAiTtsClient.estimatedCostEuro(remaining)
        val threshold = SettingsStore.confirmEuro(this)
        val hash = text.hashCode()

        if (openAiConfirmedHash == hash || cost < threshold) {
            startOpenAiFrom(currentSegment)
            return
        }

        val mins = OpenAiTtsClient.estimatedMinutes(remaining)
        AlertDialog.Builder(this)
            .setTitle("Платная озвучка OpenAI")
            .setMessage(
                "Осталось примерно ${String.format(Locale.US, "%.1f", mins)} мин. " +
                    "Ориентировочная стоимость ≈ €${String.format(Locale.US, "%.2f", cost)}. " +
                    "Продолжить?"
            )
            .setNegativeButton("Нет", null)
            .setPositiveButton("Озвучить") { _, _ ->
                openAiConfirmedHash = hash
                startOpenAiFrom(currentSegment)
            }
            .show()
    }

    private fun startOpenAiFrom(index: Int) {
        openAiRunAudioMillis = 0L
        startCloudFrom(index, SettingsStore.ENGINE_OPENAI)
    }

    private fun startEdgeFrom(index: Int) {
        startCloudFrom(
            index,
            SettingsStore.ENGINE_EDGE
        )
    }

    private fun startAzureFrom(index: Int) {
        val key =
            SettingsStore.azureSpeechKey(this)
        val region =
            SettingsStore.azureRegion(this)

        if (key.isBlank() || region.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Azure Speech")
                .setMessage(
                    "Введите Azure Speech key и region в Настройках."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Настройки") { _, _ ->
                    startActivity(
                        Intent(
                            this,
                            SettingsActivity::class.java
                        )
                    )
                }
                .show()
            return
        }

        startCloudFrom(
            index,
            SettingsStore.ENGINE_AZURE
        )
    }

    private fun startCloudFrom(index: Int, engine: String) {
        if (segments.isEmpty()) return

        tts?.stop()
        ttsReady = false
        stopMediaOnly()
        generationToken += 1

        val token = generationToken
        isPlaying = true
        playPause.text = "Пауза"
        listenButton.text = "Готовится…"
        resultText.text =
            when (engine) {
                SettingsStore.ENGINE_EDGE ->
                    "Microsoft Edge: получение аудио…"
                SettingsStore.ENGINE_AZURE ->
                    "Azure Speech: получение аудио…"
                else ->
                    "OpenAI: получение аудио…"
            }

        playCloudSegment(
            index.coerceIn(0, segments.lastIndex),
            token,
            engine
        )
    }

    private fun playCloudSegment(
        index: Int,
        token: Int,
        engine: String
    ) {
        if (token != generationToken || index !in segments.indices) return

        currentSegment = index
        highlight(index)

        val segment = segments[index]
        val voice =
            SettingsStore.voice(this).ifBlank {
                when (engine) {
                    SettingsStore.ENGINE_EDGE,
                    SettingsStore.ENGINE_AZURE ->
                        "ru-RU-DmitryNeural"
                    else ->
                        "cedar"
                }
            }

        Thread {
            try {
                val bytes =
                    when (engine) {
                        SettingsStore.ENGINE_OPENAI ->
                            OpenAiTtsClient.synthesize(
                                apiKey = SettingsStore.openAiKey(this),
                                text = segment.spoken,
                                voice = voice,
                                instructions =
                                    SettingsStore.openAiInstructions(this),
                                speed = speechRate,
                                context = this@ReaderActivity
                            )

                        SettingsStore.ENGINE_EDGE ->
                            EdgeTtsClient.synthesize(
                                text = segment.spoken,
                                voice = voice,
                                speed = speechRate,
                                context = this@ReaderActivity
                            )

                        SettingsStore.ENGINE_AZURE ->
                            AzureTtsClient.synthesize(
                                speechKey =
                                    SettingsStore.azureSpeechKey(
                                        this
                                    ),
                                region =
                                    SettingsStore.azureRegion(
                                        this
                                    ),
                                text = segment.spoken,
                                voice = voice,
                                speed = speechRate,
                                context = this@ReaderActivity
                            )

                        else ->
                            error("Unsupported cloud engine: $engine")
                    }

                if (token != generationToken) return@Thread

                val prefix =
                    when (engine) {
                        SettingsStore.ENGINE_EDGE ->
                            "edge"
                        SettingsStore.ENGINE_AZURE ->
                            "azure"
                        else ->
                            "openai"
                    }
                val file =
                    File(
                        cacheDir,
                        "${prefix}_tts_${token}_${index}.mp3"
                    )
                file.writeBytes(bytes)

                mainHandler.post {
                    if (token != generationToken) {
                        file.delete()
                        return@post
                    }

                    stopMediaOnly()
                    activeTempFile = file

                    try {
                        mediaPlayer =
                            MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                setOnCompletionListener {
                                    it.release()
                                    mediaPlayer = null

                                    if (activeTempFile == file) {
                                        activeTempFile = null
                                    }
                                    file.delete()

                                    if (token != generationToken) {
                                        return@setOnCompletionListener
                                    }

                                    if (index >= segments.lastIndex) {
                                        this@ReaderActivity.isPlaying =
                                            false
                                        currentSegment = 0
                                        playPause.text = "Сначала"
                                        listenButton.text = "Слушать"

                                        resultText.text =
                                            if (engine ==
                                                SettingsStore.ENGINE_OPENAI
                                            ) {
                                                "Чтение завершено • OpenAI ≈ €${
                                                    String.format(
                                                        Locale.US,
                                                        "%.3f",
                                                        estimatedOpenAiRunCost()
                                                    )
                                                }"
                                            } else if (
                                                engine ==
                                                SettingsStore.ENGINE_EDGE
                                            ) {
                                                "Чтение завершено • Microsoft Edge: бесплатно"
                                            } else {
                                                "Чтение завершено • Azure Speech"
                                            }
                                    } else {
                                        playCloudSegment(
                                            index + 1,
                                            token,
                                            engine
                                        )
                                    }
                                }
                                setOnErrorListener { mp, what, extra ->
                                    AppDiagnostics.error(
                                        this@ReaderActivity,
                                        "MediaPlayer error: engine=$engine what=$what extra=$extra fileExists=${file.exists()} size=${file.length()}"
                                    )
                                    mp.release()
                                    mediaPlayer = null
                                    if (activeTempFile == file) {
                                        activeTempFile = null
                                    }
                                    file.delete()
                                    this@ReaderActivity.isPlaying =
                                        false
                                    playPause.text = "Продолжить"
                                    listenButton.text = "Слушать"
                                    resultText.text =
                                        "Ошибка воспроизведения."
                                    true
                                }
                                prepare()

                                if (engine ==
                                    SettingsStore.ENGINE_OPENAI
                                ) {
                                    openAiRunAudioMillis +=
                                        duration.toLong()
                                }

                                start()
                            }

                        AppDiagnostics.info(
                            this@ReaderActivity,
                            "Cloud playback started: engine=$engine segment=$index file=${file.name} bytes=${file.length()}"
                        )
                        listenButton.text = "Читается"
                        resultText.text =
                            when (engine) {
                                SettingsStore.ENGINE_OPENAI ->
                                    "OpenAI • предложение ${index + 1}/${segments.size}"
                                SettingsStore.ENGINE_EDGE ->
                                    "Microsoft Edge • предложение ${index + 1}/${segments.size}"
                                SettingsStore.ENGINE_AZURE ->
                                    "Azure Speech • предложение ${index + 1}/${segments.size}"
                                else ->
                                    "TTS • предложение ${index + 1}/${segments.size}"
                            }
                    } catch (t: Throwable) {
                        if (activeTempFile == file) {
                            activeTempFile = null
                        }
                        file.delete()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        this@ReaderActivity.isPlaying = false
                        playPause.text = "Продолжить"
                        listenButton.text = "Слушать"
                        resultText.text = "Ошибка воспроизведения."
                        AppDiagnostics.error(
                            this@ReaderActivity,
                            "Cloud MediaPlayer setup failed: engine=$engine",
                            t
                        )
                        Toast.makeText(
                            this@ReaderActivity,
                            "Ошибка воспроизведения: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (t: Throwable) {
                AppDiagnostics.error(
                    this@ReaderActivity,
                    "Cloud TTS segment failed: engine=$engine index=$index",
                    t
                )
                mainHandler.post {
                    if (token != generationToken) return@post
                    isPlaying = false
                    playPause.text = "Продолжить"
                    listenButton.text = "Слушать"
                    resultText.text =
                        t.message ?: "Ошибка TTS"
                    Toast.makeText(
                        this,
                        t.message ?: "Ошибка TTS",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun estimatedOpenAiRunCost(): Double =
        openAiRunAudioMillis / 60_000.0 * 0.013

    private fun pauseSpeech() {
        generationToken += 1
        tts?.stop()
        stopMediaOnly()
        isPlaying = false

        if (::playPause.isInitialized) {
            playPause.text = "Продолжить"
        }
        if (::listenButton.isInitialized && !editMode) {
            listenButton.text = "Слушать"
        }

        if (::resultText.isInitialized &&
            SettingsStore.engine(this) ==
            SettingsStore.ENGINE_OPENAI &&
            openAiRunAudioMillis > 0
        ) {
            resultText.text =
                "OpenAI уже сгенерировано ≈ €${
                    String.format(
                        Locale.US,
                        "%.3f",
                        estimatedOpenAiRunCost()
                    )
                }"
        }
    }

    private fun stopMediaOnly() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
        activeTempFile?.delete()
        activeTempFile = null
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
            Toast.makeText(
                this,
                "Текст пустой",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        text = updated
        segments = segmentText(text)
        currentSegment = 0
        openAiConfirmedHash = null

        if (libraryId == null) {
            libraryId =
                LibraryStore.add(
                    this,
                    title,
                    source,
                    text
                )
        } else {
            LibraryStore.update(
                this,
                libraryId!!,
                title,
                source,
                text
            )
        }

        textView.text = text
        editor.visibility = View.GONE
        textView.visibility = View.VISIBLE
        editMode = false
        listenButton.text = "Слушать"
        player.visibility = View.VISIBLE
        resultText.text = "Текст сохранён."

        Toast.makeText(
            this,
            "Текст сохранён",
            Toast.LENGTH_SHORT
        ).show()
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
            val line =
                layout.getLineForOffset(
                    seg.start.coerceAtMost(text.length)
                )
            val y =
                (
                    layout.getLineTop(line) -
                        scroll.height / 3
                    ).coerceAtLeast(0)
            scroll.smoothScrollTo(0, y)
        }
    }

    private fun cycleSpeed() {
        val values =
            floatArrayOf(
                0.8f,
                0.9f,
                1.0f,
                1.1f,
                1.2f
            )
        val next =
            values.firstOrNull {
                it > speechRate + 0.01f
            } ?: values.first()

        speechRate = next
        speedButton.text =
            String.format(
                Locale.US,
                "%.1fx",
                speechRate
            )
        tts?.setSpeechRate(speechRate)

        if (isPlaying) {
            val engine = SettingsStore.engine(this)
            val index = currentSegment
            pauseSpeech()
            currentSegment = index

            when {
                engine == SettingsStore.ENGINE_OPENAI ->
                    startOpenAiFrom(currentSegment)
                engine == SettingsStore.ENGINE_EDGE ->
                    startEdgeFrom(currentSegment)
                engine == SettingsStore.ENGINE_AZURE ->
                    startAzureFrom(currentSegment)
                engine.startsWith("android:") ->
                    startAndroidTts()
            }
        }
    }

    private fun updateEngineLabels() {
        val engine = SettingsStore.engine(this)
        engineButton.text =
            when {
                engine == SettingsStore.ENGINE_OPENAI ->
                    "OpenAI"
                engine == SettingsStore.ENGINE_EDGE ->
                    "Edge"
                engine == SettingsStore.ENGINE_SILERO ->
                    "Silero"
                engine == SettingsStore.ENGINE_AZURE ->
                    "Azure"
                engine == SettingsStore.ENGINE_GOOGLE ->
                    "Google"
                engine == SettingsStore.DEFAULT_ENGINE ->
                    "Android TTS"
                engine.startsWith("android:") ->
                    engine
                        .removePrefix("android:")
                        .substringAfterLast('.')
                        .take(18)
                else ->
                    "Движок"
            }

        val voice = SettingsStore.voice(this)
        voiceButton.text =
            VoiceCatalog.staticVoices(engine)
                .firstOrNull { it.id == voice }
                ?.label
                ?.take(20)
                ?: voice.ifBlank { "Голос" }.take(20)
    }

    private fun requestTextExport() {
        if (editMode) saveEditedText()
        val fileName =
            safeFileName(title) + ".txt"

        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TITLE,
                    fileName
                )
            },
            REQ_SAVE_TEXT
        )
    }

    private fun requestAudioExport() {
        val engine = SettingsStore.engine(this)

        when (engine) {
            SettingsStore.ENGINE_OPENAI -> {
                if (SettingsStore.openAiKey(this).isBlank()) {
                    startActivity(
                        Intent(
                            this,
                            SettingsActivity::class.java
                        )
                    )
                    return
                }

                val cost =
                    OpenAiTtsClient.estimatedCostEuro(text)
                val mins =
                    OpenAiTtsClient.estimatedMinutes(text)

                AlertDialog.Builder(this)
                    .setTitle("Создать MP3 через OpenAI?")
                    .setMessage(
                        "Отдельная генерация всего текста: ≈ ${String.format(Locale.US, "%.1f", mins)} мин, " +
                            "ориентировочно €${String.format(Locale.US, "%.2f", cost)}."
                    )
                    .setNegativeButton("Нет", null)
                    .setPositiveButton("Создать") { _, _ ->
                        pendingExportEngine =
                            SettingsStore.ENGINE_OPENAI
                        chooseAudioDestination()
                    }
                    .show()
            }

            SettingsStore.ENGINE_EDGE -> {
                pendingExportEngine =
                    SettingsStore.ENGINE_EDGE
                chooseAudioDestination()
            }

            SettingsStore.ENGINE_AZURE -> {
                if (
                    SettingsStore.azureSpeechKey(this).isBlank() ||
                    SettingsStore.azureRegion(this).isBlank()
                ) {
                    AlertDialog.Builder(this)
                        .setTitle("Azure Speech")
                        .setMessage(
                            "Введите Azure Speech key и region в Настройках."
                        )
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Настройки") { _, _ ->
                            startActivity(
                                Intent(
                                    this,
                                    SettingsActivity::class.java
                                )
                            )
                        }
                        .show()
                    return
                }

                pendingExportEngine =
                    SettingsStore.ENGINE_AZURE
                chooseAudioDestination()
            }

            else -> {
                AlertDialog.Builder(this)
                    .setTitle("MP3-экспорт")
                    .setMessage(
                        "В этой версии MP3-экспорт работает для OpenAI, Microsoft Edge и Azure Speech. " +
                            "Android/RHVoice, Silero и Google получат собственный экспорт после подключения соответствующего runtime."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun chooseAudioDestination() {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/mpeg"
                putExtra(
                    Intent.EXTRA_TITLE,
                    safeFileName(title) + ".mp3"
                )
            },
            REQ_SAVE_AUDIO
        )
    }

    @Deprecated("legacy result handling is sufficient for this prototype")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_SAVE_TEXT ->
                exportText(uri)

            REQ_SAVE_AUDIO ->
                exportMp3(
                    uri,
                    pendingExportEngine
                        ?: SettingsStore.engine(this)
                )
        }
    }

    private fun exportText(uri: Uri) {
        try {
            contentResolver
                .openOutputStream(uri)
                ?.use {
                    it.write(
                        text.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
                ?: error("Не удалось открыть файл")

            resultText.text =
                "Текстовый файл сохранён."
            Toast.makeText(
                this,
                "Файл сохранён",
                Toast.LENGTH_SHORT
            ).show()
        } catch (t: Throwable) {
            resultText.text =
                "Ошибка сохранения: ${t.message}"
            Toast.makeText(
                this,
                t.message ?: "Ошибка сохранения",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportMp3(
        uri: Uri,
        engine: String
    ) {
        pauseSpeech()
        player.visibility = View.VISIBLE
        listenButton.text = "Создаётся MP3…"

        exportProgress.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        exportProgress.progress = 0
        progressText.text = "Создание MP3: 0%"
        resultText.text = ""

        val voice =
            SettingsStore.voice(this).ifBlank {
                when (engine) {
                    SettingsStore.ENGINE_EDGE,
                    SettingsStore.ENGINE_AZURE ->
                        "ru-RU-DmitryNeural"
                    else ->
                        "cedar"
                }
            }

        val chunks =
            when (engine) {
                SettingsStore.ENGINE_EDGE ->
                    EdgeTtsClient.splitForApi(text)
                SettingsStore.ENGINE_AZURE ->
                    AzureTtsClient.splitForApi(text)
                else ->
                    OpenAiTtsClient.splitForApi(text)
            }

        Thread {
            try {
                contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->
                        chunks.forEachIndexed { index, chunk ->
                            val bytes =
                                when (engine) {
                                    SettingsStore.ENGINE_OPENAI ->
                                        OpenAiTtsClient.synthesize(
                                            apiKey =
                                                SettingsStore.openAiKey(
                                                    this
                                                ),
                                            text = chunk,
                                            voice = voice,
                                            instructions =
                                                SettingsStore
                                                    .openAiInstructions(
                                                        this
                                                    ),
                                            speed = speechRate,
                                            context =
                                                this@ReaderActivity
                                        )

                                    SettingsStore.ENGINE_EDGE ->
                                        EdgeTtsClient.synthesize(
                                            text = chunk,
                                            voice = voice,
                                            speed = speechRate,
                                            context =
                                                this@ReaderActivity
                                        )

                                    SettingsStore.ENGINE_AZURE ->
                                        AzureTtsClient.synthesize(
                                            speechKey =
                                                SettingsStore.azureSpeechKey(
                                                    this
                                                ),
                                            region =
                                                SettingsStore.azureRegion(
                                                    this
                                                ),
                                            text = chunk,
                                            voice = voice,
                                            speed = speechRate,
                                            context =
                                                this@ReaderActivity
                                        )

                                    else ->
                                        error(
                                            "MP3 export unsupported for $engine"
                                        )
                                }

                            output.write(
                                if (index == 0) {
                                    bytes
                                } else {
                                    OpenAiTtsClient
                                        .stripLeadingId3(bytes)
                                }
                            )
                            output.flush()

                            val percent =
                                (
                                    (index + 1) *
                                        100 /
                                        chunks.size
                                    ).coerceIn(0, 100)

                            mainHandler.post {
                                exportProgress.progress =
                                    percent
                                progressText.text =
                                    "Создание MP3: ${index + 1}/${chunks.size} • $percent%"
                                listenButton.text =
                                    "MP3 $percent%"
                            }
                        }
                    }
                    ?: error("Не удалось открыть файл")

                mainHandler.post {
                    exportProgress.progress = 100
                    progressText.text =
                        "MP3 полностью записан • 100%"
                    listenButton.text = "Слушать"

                    val costLine =
                        if (engine ==
                            SettingsStore.ENGINE_OPENAI
                        ) {
                            "Ориентировочная стоимость этой генерации ≈ €${
                                String.format(
                                    Locale.US,
                                    "%.2f",
                                    OpenAiTtsClient
                                        .estimatedCostEuro(text)
                                )
                            }"
                        } else if (
                            engine ==
                            SettingsStore.ENGINE_EDGE
                        ) {
                            "Microsoft Edge: бесплатно"
                        } else {
                            "Azure Speech: F0 бесплатно в пределах квоты"
                        }

                    resultText.text =
                        "MP3 сохранён. $costLine"

                    AlertDialog.Builder(this)
                        .setTitle("MP3 готов")
                        .setMessage(
                            "Файл полностью создан и записан.\n\n$costLine"
                        )
                        .setPositiveButton("OK", null)
                        .show()

                    mainHandler.postDelayed(
                        {
                            exportProgress.visibility =
                                View.GONE
                            progressText.visibility =
                                View.GONE
                        },
                        4500
                    )
                }
            } catch (t: Throwable) {
                AppDiagnostics.error(
                    this@ReaderActivity,
                    "MP3 export failed: engine=$engine",
                    t
                )
                mainHandler.post {
                    listenButton.text = "Слушать"
                    exportProgress.visibility =
                        View.VISIBLE
                    progressText.visibility =
                        View.VISIBLE
                    progressText.text =
                        "Ошибка создания MP3"
                    resultText.text =
                        t.message ?: "Ошибка создания MP3"

                    AlertDialog.Builder(this)
                        .setTitle("MP3 не создан")
                        .setMessage(
                            t.message
                                ?: "Неизвестная ошибка"
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun safeFileName(value: String): String =
        value
            .replace(
                Regex("[^\\p{L}\\p{N}._ -]"),
                "_"
            )
            .trim()
            .take(70)
            .ifBlank { "kapijuja-reader" }

    private fun segmentText(value: String): List<Segment> {
        if (value.isBlank()) return emptyList()

        val iterator =
            BreakIterator.getSentenceInstance(
                Locale("ru")
            )
        iterator.setText(value)

        val result =
            mutableListOf<Segment>()
        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            addSegmentParts(
                value,
                start,
                end,
                result
            )
            start = end
            end = iterator.next()
        }

        if (result.isEmpty()) {
            addSegmentParts(
                value,
                0,
                value.length,
                result
            )
        }
        return result
    }

    private fun addSegmentParts(
        value: String,
        start: Int,
        end: Int,
        out: MutableList<Segment>
    ) {
        var s = start
        while (s < end) {
            while (
                s < end &&
                value[s].isWhitespace()
            ) {
                s++
            }
            if (s >= end) break

            var e =
                minOf(
                    s + 2800,
                    end
                )

            if (e < end) {
                val candidate =
                    value.lastIndexOfAny(
                        charArrayOf(
                            ' ',
                            '\n',
                            ',',
                            ';'
                        ),
                        e
                    )
                if (candidate > s + 600) {
                    e = candidate + 1
                }
            }

            while (
                e > s &&
                value[e - 1].isWhitespace()
            ) {
                e--
            }

            if (e > s) {
                out.add(
                    Segment(
                        s,
                        e,
                        value.substring(s, e)
                    )
                )
            }
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

    private fun dp(v: Int) =
        KapijujaUiTheme.dp(this, v)

    data class Segment(
        val start: Int,
        val end: Int,
        val spoken: String
    )

    companion object {
        const val EXTRA_LIBRARY_ID = "library_id"
        const val EXTRA_DRAFT_PATH = "draft_path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE = "source"

        private const val REQ_SAVE_TEXT = 1201
        private const val REQ_SAVE_AUDIO = 1202
    }
}
