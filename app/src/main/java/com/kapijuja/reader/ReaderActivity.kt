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
    private lateinit var editButton: Button
    private lateinit var engineButton: Button
    private lateinit var voiceButton: Button
    private lateinit var speedButton: Button
    private lateinit var saveAudioButton: Button
    private lateinit var saveRow: LinearLayout
    private lateinit var editorToolbar: LinearLayout
    private lateinit var editorListenButton: Button
    private lateinit var grokStressButton: Button
    private lateinit var grokEditToolsButton: Button
    private var sourceView: TextView? = null
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
    private val prefetchedCloudFiles =
        java.util.concurrent.ConcurrentHashMap<Int, File>()
    private val prefetchingCloudSegments =
        java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    private var segments: List<Segment> = emptyList()
    private var currentSegment = 0
    private var isPlaying = false
    private var editMode = false
    private var speechRate = 1.0f
    private var openAiConfirmedHash: Int? = null
    private var xaiConfirmedHash: Int? = null
    private var openAiRunAudioMillis = 0L
    private var playbackStartOffsetOverride: Int? = null
    private var pendingExportEngine: String? = null
    @Volatile private var exportCancelled = false
    @Volatile private var exportInProgress = false
    private var currentExportFormat = "MP3"
    private var exportThread: Thread? = null
    private val ttsExportLatches =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CountDownLatch>()
    private val ttsExportFailures =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapDownAt = 0L
    private var builtLanguage = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    private val playbackController = object : PlaybackBridge.Controller {
        override fun toggleFromNotification() {
            mainHandler.post {
                if (isPlaying) pauseSpeech() else startOrResume()
            }
        }

        override fun stopFromNotification() {
            mainHandler.post {
                pauseSpeech()
                PlaybackBridge.stop(this@ReaderActivity)
            }
        }
    }

    private val exportController = object : ExportBridge.Controller {
        override fun cancelExportFromNotification() {
            mainHandler.post {
                cancelAudioExport()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KapijujaUiTheme.applyWindow(this)
        PlaybackBridge.controller = playbackController
        ExportBridge.controller = exportController
        builtLanguage = UiText.language(this)
        try {
            loadInput()
            ReaderLimits.requireDisplaySafe(text)
        } catch (error: IllegalArgumentException) {
            title = t("Слишком большой документ", "Zbyt duży dokument", "Document too large")
            source = ""
            libraryId = null
            text = error.message ?: t(
                "Документ не загружен из-за ограничения размера.",
                "Dokument nie został wczytany z powodu limitu rozmiaru.",
                "The document was not loaded because it exceeds the safety limit."
            )
        }
        segments = segmentText(text)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (builtLanguage.isNotBlank() && builtLanguage != UiText.language(this)) {
            recreate()
            return
        }

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
            title = item?.title ?: t("Текст", "Tekst", "Text")
            source = item?.source.orEmpty()
            text = LibraryStore.text(this, libraryId!!)
        } else {
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                .ifBlank { t("Текст", "Tekst", "Text") }
            source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
            val path = intent.getStringExtra(EXTRA_DRAFT_PATH)
            text = path?.let { File(it).takeIf(File::exists)?.readText() }.orEmpty()
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.kapijuja_screen_bg)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        KapijujaUiTheme.applySafeArea(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = Button(this).apply {
            text = "‹"
            textSize = 24f
            contentDescription = t("Назад", "Wstecz", "Back")
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { finish() }
        }
        KapijujaUiTheme.button(this, back)
        top.addView(back, LinearLayout.LayoutParams(dp(46), dp(44)))

        val heading = TextView(this).apply {
            text = title
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(9), 0, dp(6), 0)
        }
        KapijujaUiTheme.title(heading)
        top.addView(
            heading,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val settings = Button(this).apply {
            text = "⚙"
            textSize = 20f
            contentDescription = t("Настройки", "Ustawienia", "Settings")
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, settings)
        top.addView(settings, LinearLayout.LayoutParams(dp(46), dp(44)))
        root.addView(top)

        listenButton = Button(this).apply {
            visibility = View.GONE
        }

        if (source.isNotBlank()) {
            val sourceLabel = TextView(this).apply {
                text = source
                textSize = 12f
                maxLines = 1
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            KapijujaUiTheme.secondary(sourceLabel)
            sourceView = sourceLabel
            root.addView(sourceLabel)
        }

        scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }

        contentFrame = FrameLayout(this)

        textView = TextView(this).apply {
            text = this@ReaderActivity.text
            textSize = 20f
            setTextColor(KapijujaUiTheme.SILVER)
            setLineSpacing(dp(5).toFloat(), 1.08f)
            setPadding(dp(10), dp(12), dp(30), dp(28))
            setTextIsSelectable(true)
        }
        installTapStart()

        editor = EditText(this).apply {
            visibility = View.GONE
            textSize = 20f
            gravity = Gravity.TOP
            minLines = 6
            setLineSpacing(dp(5).toFloat(), 1.08f)
        }
        KapijujaUiTheme.input(this, editor)
        editor.setPadding(editor.paddingLeft, editor.paddingTop, dp(30), editor.paddingBottom)

        editorToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = KapijujaUiTheme.panel(
                this@ReaderActivity,
                KapijujaUiTheme.PANEL_DARK,
                KapijujaUiTheme.BLUE,
                1,
                12
            )
        }

        editorListenButton = Button(this).apply {
            text = "▶"
            textSize = 15f
            contentDescription =
                t("Слушать или пауза", "Czytaj lub pauza", "Listen or pause")
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                if (isPlaying) pauseSpeech() else startOrResume()
            }
        }
        KapijujaUiTheme.button(this, editorListenButton, primary = true)
        editorToolbar.addView(
            editorListenButton,
            LinearLayout.LayoutParams(dp(58), dp(38)).apply { marginEnd = dp(5) }
        )

        grokStressButton =
            GrokEditorToolbar.createStressButton(
                activity = this,
                editor = editor,
                setResultText = { value -> resultText.text = value },
                pauseIfPlaying = { if (isPlaying) pauseSpeech() },
                scrollToOffset = { offset -> scrollEditorToOffset(offset) }
            )
        editorToolbar.addView(
            grokStressButton,
            LinearLayout.LayoutParams(dp(46), dp(38)).apply { marginEnd = dp(5) }
        )

        grokEditToolsButton =
            GrokEditorToolbar.create(
                activity = this,
                editor = editor,
                setResultText = { value -> resultText.text = value },
                pauseIfPlaying = { if (isPlaying) pauseSpeech() },
                scrollToOffset = { offset -> scrollEditorToOffset(offset) }
            )
        editorToolbar.addView(
            grokEditToolsButton,
            LinearLayout.LayoutParams(dp(82), dp(38)).apply { marginEnd = dp(5) }
        )

        val editorSaveButton = Button(this).apply {
            text = "✓"
            textSize = 20f
            contentDescription = t("Сохранить", "Zapisz", "Save")
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener { saveEditedText() }
        }
        KapijujaUiTheme.button(this, editorSaveButton)
        editorToolbar.addView(
            editorSaveButton,
            LinearLayout.LayoutParams(dp(48), dp(38))
        )
        root.addView(
            editorToolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { topMargin = dp(4); bottomMargin = dp(4) }
        )

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
        val scrollHost = FrameLayout(this)
        scrollHost.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val fastScroller = ReaderFastScroller(this).apply { attachTo(scroll) }
        scrollHost.addView(
            fastScroller,
            FrameLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
        )
        root.addView(
            scrollHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        player = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            setPadding(dp(7), dp(5), dp(7), dp(5))
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
            text = t("Слушать", "Listen")
            setOnClickListener {
                if (isPlaying) {
                    pauseSpeech()
                } else {
                    startOrResume()
                }
            }
        }
        KapijujaUiTheme.button(this, playPause, primary = true)
        controls.addView(
            playPause,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
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
            LinearLayout.LayoutParams(0, dp(46), 0.65f).apply {
                marginEnd = dp(7)
            }
        )

        editButton = Button(this).apply {
            text = t("Редактировать", "Edit")
            setOnClickListener {
                if (editMode) saveEditedText() else enterEditMode()
            }
        }
        KapijujaUiTheme.button(this, editButton)
        controls.addView(editButton, LinearLayout.LayoutParams(0, dp(46), 1.15f))
        player.addView(controls)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        engineButton = Button(this).apply {
            text = t("Движок", "Engine")
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, engineButton)
        voiceRow.addView(
            engineButton,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(7)
            }
        )

        voiceButton = Button(this).apply {
            text = t("Голос", "Voice")
            setOnClickListener {
                pauseSpeech()
                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))
            }
        }
        KapijujaUiTheme.button(this, voiceButton)
        voiceRow.addView(voiceButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        player.addView(voiceRow)

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
                dp(16)
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

        saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        val saveText = Button(this).apply {
            text = t("Сохранить файл", "Save text")
            textSize = 14f
            setOnClickListener { requestTextExport() }
        }
        KapijujaUiTheme.button(this, saveText)
        saveRow.addView(
            saveText,
            LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(7)
            }
        )

        saveAudioButton = Button(this).apply {
            text = t("Сохранить MP3", "Save MP3")
            textSize = 14f
            setOnClickListener {
                if (exportInProgress) cancelAudioExport() else requestAudioExport()
            }
        }
        KapijujaUiTheme.button(
            this,
            saveAudioButton
        )
        saveRow.addView(
            saveAudioButton,
            LinearLayout.LayoutParams(
                0,
                dp(42),
                1f
            )
        )
        player.addView(saveRow)

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

        val playbackOffset =
            if (SettingsStore.engine(this) == SettingsStore.ENGINE_XAI) {
                GrokEditorMarkup.playbackStartOffset(text, offset)
            } else {
                offset
            }
        val index = segments.indexOfFirst { playbackOffset < it.end }
            .let { if (it >= 0) it else segments.lastIndex }

        val wasPlaying = isPlaying
        if (wasPlaying) pauseSpeech()

        currentSegment = index
        playbackStartOffsetOverride = playbackOffset
        player.visibility = View.VISIBLE
        highlight(index)
        listenButton.text = if (wasPlaying) "Готовится…" else "Слушать отсюда"
        playPause.text = t("Продолжить", "Resume")
        resultText.text =
            t(
                "Старт: предложение ${index + 1} из ${segments.size}",
                "Start: zdanie ${index + 1} z ${segments.size}",
                "Start: sentence ${index + 1} of ${segments.size}"
            )

        AppDiagnostics.info(
            this,
            "Playback cursor selected: offset=$offset segment=$index playing=$wasPlaying"
        )

        if (wasPlaying) {
            startOrResume()
        }
    }

    private fun startOrResume() {
        if (editMode && !prepareEditorPlaybackSnapshot()) return
        if (text.isBlank()) return

        if (!editMode && libraryId == null) {
            libraryId = LibraryStore.add(this, title, source, text)
        }
        if (editMode) {
            player.visibility = View.GONE
            editorToolbar.visibility = View.VISIBLE
        } else {
            player.visibility = View.VISIBLE
        }

        when (val engine = SettingsStore.engine(this)) {
            SettingsStore.ENGINE_OPENAI -> startOpenAiWithGuard()
            SettingsStore.ENGINE_XAI -> startXaiWithGuard()
            SettingsStore.ENGINE_EDGE -> startEdgeFrom(currentSegment)
            SettingsStore.ENGINE_SILERO -> startSileroFrom(currentSegment)
            SettingsStore.ENGINE_AZURE -> startAzureFrom(currentSegment)
            SettingsStore.ENGINE_GOOGLE -> startGoogleFrom(currentSegment)
            else -> {
                if (!engine.startsWith("android:")) {
                    showUnavailable(
                        t(
                            "Неизвестный движок: $engine",
                            "Nieznany silnik: $engine",
                            "Unknown engine: $engine"
                        )
                    )
                    return
                }
                startAndroidTts()
            }
        }
    }

    private fun showUnavailable(message: String) {
        isPlaying = false
        listenButton.text = "Слушать"
        playPause.text = t("Продолжить", "Resume")
        resultText.text = message
        AlertDialog.Builder(this)
            .setTitle(t("Движок пока не активен", "Silnik nie jest jeszcze aktywny", "Engine not active yet"))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startAndroidTts() {
        if (!ttsReady) {
            showPlaybackPreparingUi()
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
                    val available = tts?.voices.orEmpty()
                    val voice =
                        available.firstOrNull { it.name == configuredVoice }
                            ?: available.firstOrNull {
                                it.locale?.language == "ru" &&
                                    it.name.contains("network", ignoreCase = true)
                            }
                            ?: available.firstOrNull { it.locale?.language == "ru" }

                    if (voice != null) {
                        tts?.voice = voice
                        appliedVoice = voice.name
                        if (voice.name != configuredVoice) {
                            SettingsStore.setVoice(this, engineId, voice.name)
                        }
                        AppDiagnostics.info(
                            this,
                            "Android TTS voice applied: engine=$engineId voice=${voice.name} locale=${voice.locale}"
                        )
                    } else {
                        AppDiagnostics.error(
                            this,
                            "Configured Android voice not found: engine=$engineId voice=$configuredVoice"
                        )
                    }
                }

                activeAndroidEngine = engineId
                activeAndroidVoice = configuredVoice.ifBlank { appliedVoice }
                installProgressListener()
                updateEngineLabels()
                onReady()
            } else {
                AppDiagnostics.error(
                    this,
                    "Android TTS init failed: engine=$engineId status=$status"
                )
                stopPlaybackBusyIndicators()
                Toast.makeText(
                    this,
                    t(
                        "TTS движок не запустился",
                        "Nie udało się uruchomić silnika TTS",
                        "TTS engine failed to start"
                    ),
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
                    if (utteranceId?.startsWith("file_") == true) return
                    val index = utteranceId?.substringAfter("seg_")?.toIntOrNull() ?: return
                    mainHandler.post {
                        currentSegment = index
                        isPlaying = true
                        showPlaybackPlayingUi()
                        listenButton.text = "Читается"
                        highlight(index)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("file_") == true) {
                        ttsExportLatches[utteranceId]?.countDown()
                        return
                    }
                    val index = utteranceId?.substringAfter("seg_")?.toIntOrNull() ?: return
                    if (index == segments.lastIndex) {
                        mainHandler.post {
                            isPlaying = false
                            currentSegment = 0
                            playbackStartOffsetOverride = null
                            stopPlaybackBusyIndicators()
                            playPause.text = t("Сначала", "Start over")
                            listenButton.text = "Слушать"
                            resultText.text = t("Чтение завершено.", "Reading complete.")
                            stopPlaybackNotification()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    if (utteranceId?.startsWith("file_") == true) {
                        ttsExportFailures.add(utteranceId)
                        ttsExportLatches[utteranceId]?.countDown()
                        return
                    }
                    mainHandler.post {
                        isPlaying = false
                        stopPlaybackBusyIndicators()
                        playPause.text = t("Продолжить", "Resume")
                        listenButton.text = "Слушать"
                        resultText.text = t("Ошибка Android TTS.", "Android TTS error.")
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
            val mode = if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(
                spokenSegmentForPlayback(i, start),
                mode,
                null,
                "seg_$i"
            )
        }

        isPlaying = true
        showPlaybackPlayingUi()
        listenButton.text = "Читается"
        resultText.text = ""
        syncPlaybackNotification()
    }

    private fun startOpenAiWithGuard() {
        val apiKey = SettingsStore.openAiKey(this)
        if (apiKey.isBlank()) {
            showMissingKey(
                title = "OpenAI API key",
                message = t(
                    "Введите API key в Настройках. GitHub Secret внутрь APK не встраивается.",
                    "Wprowadź klucz API w Ustawieniach. GitHub Secret nie jest osadzany w APK.",
                    "Enter the API key in Settings. GitHub Secrets are not embedded in the APK."
                )
            )
            return
        }

        val remaining = remainingText()
        val cost = OpenAiTtsClient.estimatedCostEuro(remaining)
        val threshold = SettingsStore.confirmEuro(this)
        val hash = text.hashCode()

        if (openAiConfirmedHash == hash || cost < threshold) {
            startOpenAiFrom(currentSegment)
            return
        }

        val mins = OpenAiTtsClient.estimatedMinutes(remaining)
        AlertDialog.Builder(this)
            .setTitle(t("Платная озвучка OpenAI", "Płatna synteza OpenAI", "Paid OpenAI narration"))
            .setMessage(
                t(
                    "Осталось примерно ${String.format(Locale.US, "%.1f", mins)} мин. Ориентировочная стоимость ≈ €${String.format(Locale.US, "%.2f", cost)}. Продолжить?",
                    "Pozostało około ${String.format(Locale.US, "%.1f", mins)} min. Szacowany koszt ≈ €${String.format(Locale.US, "%.2f", cost)}. Kontynuować?",
                    "About ${String.format(Locale.US, "%.1f", mins)} min remain. Estimated cost ≈ €${String.format(Locale.US, "%.2f", cost)}. Continue?"
                )
            )
            .setNegativeButton(t("Нет", "Nie", "No"), null)
            .setPositiveButton(t("Озвучить", "Generuj", "Generate")) { _, _ ->
                openAiConfirmedHash = hash
                startOpenAiFrom(currentSegment)
            }
            .show()
    }

    private fun startXaiWithGuard() {
        if (SettingsStore.xaiApiKey(this).isBlank()) {
            showMissingKey(
                title = "xAI Grok TTS",
                message = t(
                    "Введите xAI API key в Настройках.",
                    "Wprowadź klucz API xAI w Ustawieniach.",
                    "Enter the xAI API key in Settings."
                )
            )
            return
        }

        val remaining = remainingText()
        val cost = XaiTtsClient.estimatedCostEuro(remaining)
        val threshold = SettingsStore.confirmEuro(this)
        val hash = text.hashCode()

        if (xaiConfirmedHash == hash || cost < threshold) {
            startXaiFrom(currentSegment)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(t("Платная озвучка xAI Grok", "Płatna synteza xAI Grok", "Paid xAI Grok narration"))
            .setMessage(
                t(
                    "Осталось ${remaining.length} символов. По текущему тарифу xAI ориентировочная стоимость ≈ €${String.format(Locale.US, "%.2f", cost)}. Продолжить?",
                    "Pozostało ${remaining.length} znaków. Przy bieżącej stawce xAI szacowany koszt ≈ €${String.format(Locale.US, "%.2f", cost)}. Kontynuować?",
                    "${remaining.length} characters remain. At the current xAI rate the estimated cost is ≈ €${String.format(Locale.US, "%.2f", cost)}. Continue?"
                )
            )
            .setNegativeButton(t("Нет", "Nie", "No"), null)
            .setPositiveButton(t("Озвучить", "Generuj", "Generate")) { _, _ ->
                xaiConfirmedHash = hash
                startXaiFrom(currentSegment)
            }
            .show()
    }

    private fun remainingText(): String {
        val exact = playbackStartOffsetOverride?.coerceIn(0, text.length)
        if (exact != null) return text.substring(exact)
        return if (currentSegment in segments.indices) {
            text.substring(segments[currentSegment].start)
        } else {
            text
        }
    }

    private fun showMissingKey(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(t("Отмена", "Anuluj", "Cancel"), null)
            .setPositiveButton(t("Настройки", "Ustawienia", "Settings")) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }

    private fun startOpenAiFrom(index: Int) {
        openAiRunAudioMillis = 0L
        startCloudFrom(index, SettingsStore.ENGINE_OPENAI)
    }

    private fun startXaiFrom(index: Int) {
        startCloudFrom(index, SettingsStore.ENGINE_XAI)
    }

    private fun startEdgeFrom(index: Int) {
        startCloudFrom(index, SettingsStore.ENGINE_EDGE)
    }

    private fun startSileroFrom(index: Int) {
        startCloudFrom(index, SettingsStore.ENGINE_SILERO)
    }

    private fun startAzureFrom(index: Int) {
        val key = SettingsStore.azureSpeechKey(this)
        val region = SettingsStore.azureRegion(this)

        if (key.isBlank() || region.isBlank()) {
            showMissingKey(
                title = "Azure Speech",
                message = t(
                    "Введите Azure Speech key и region в Настройках.",
                    "Wprowadź klucz Azure Speech i region w Ustawieniach.",
                    "Enter the Azure Speech key and region in Settings."
                )
            )
            return
        }

        startCloudFrom(index, SettingsStore.ENGINE_AZURE)
    }

    private fun startGoogleFrom(index: Int) {
        if (SettingsStore.googleApiKey(this).isBlank()) {
            showMissingKey(
                title = "Google Gemini TTS",
                message = t(
                    "Введите Google Gemini API key в Настройках.",
                    "Wprowadź klucz API Google Gemini w Ustawieniach.",
                    "Enter the Google Gemini API key in Settings."
                )
            )
            return
        }

        startCloudFrom(index, SettingsStore.ENGINE_GOOGLE)
    }

    private fun startCloudFrom(index: Int, engine: String) {
        if (segments.isEmpty()) return

        tts?.stop()
        ttsReady = false
        stopMediaOnly()
        clearCloudPrefetch()
        generationToken += 1

        val token = generationToken
        isPlaying = true
        showPlaybackPreparingUi()
        listenButton.text = "Готовится…"
        syncPlaybackNotification()

        resultText.text =
            when (engine) {
                SettingsStore.ENGINE_EDGE ->
                    t("Microsoft Edge: буферизация…", "Microsoft Edge: buforowanie…", "Microsoft Edge: buffering…")
                SettingsStore.ENGINE_AZURE ->
                    t("Azure Speech: буферизация…", "Azure Speech: buforowanie…", "Azure Speech: buffering…")
                SettingsStore.ENGINE_GOOGLE ->
                    t("Google Gemini: буферизация…", "Google Gemini: buforowanie…", "Google Gemini: buffering…")
                SettingsStore.ENGINE_XAI ->
                    t("xAI Grok: буферизация…", "xAI Grok: buforowanie…", "xAI Grok: buffering…")
                SettingsStore.ENGINE_SILERO ->
                    t(
                        "Silero v5.5: подготовка локального голоса…",
                        "Silero v5.5: przygotowanie lokalnego głosu…",
                        "Silero v5.5: preparing local voice…"
                    )
                else ->
                    t("OpenAI: буферизация…", "OpenAI: buforowanie…", "OpenAI: buffering…")
            }

        playCloudChunk(
            startIndex = index.coerceIn(0, segments.lastIndex),
            token = token,
            engine = engine
        )
    }

    private fun spokenSegmentForPlayback(index: Int, startIndex: Int): String {
        val segment = segments[index]
        val exact = playbackStartOffsetOverride
        val raw =
            if (
                index == startIndex &&
                exact != null &&
                exact >= segment.start &&
                exact < segment.end
            ) {
                text.substring(exact, segment.end)
            } else {
                segment.spoken
            }
        return raw.replace(Regex("\\s+"), " ").trim()
    }

    private fun buildCloudChunk(
        startIndex: Int,
        engine: String,
        maxChars: Int = 720
    ): CloudChunk {
        val start = startIndex.coerceIn(0, segments.lastIndex)
        var end = start
        val builder = StringBuilder()

        for (i in start..segments.lastIndex) {
            val sentence = spokenSegmentForPlayback(i, start)
            if (sentence.isBlank()) {
                end = i
                continue
            }

            val extra = if (builder.isEmpty()) sentence.length else sentence.length + 1
            val grokTagOpen =
                engine == SettingsStore.ENGINE_XAI &&
                    GrokEditorMarkup.hasUnclosedWrappingTag(builder.toString())
            if (
                builder.isNotEmpty() &&
                builder.length + extra > maxChars &&
                !grokTagOpen
            ) {
                break
            }

            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(sentence)
            end = i

            if (engine == SettingsStore.ENGINE_XAI) {
                require(builder.length <= XaiTtsClient.MAX_REQUEST_CHARS) {
                    "Grok-тег охватывает слишком длинный фрагмент. Разбейте его на несколько частей."
                }
            }

            val canBreakHere =
                engine != SettingsStore.ENGINE_XAI ||
                    !GrokEditorMarkup.hasUnclosedWrappingTag(builder.toString())
            if (builder.length >= maxChars * 3 / 4 && canBreakHere) break
        }

        if (builder.isEmpty()) {
            builder.append(segments[start].spoken.replace(Regex("\\s+"), " ").trim())
            end = start
        }

        return CloudChunk(startSegment = start, endSegment = end, spoken = builder.toString())
    }

    private fun playCloudChunk(startIndex: Int, token: Int, engine: String) {
        if (token != generationToken || startIndex !in segments.indices) return

        val chunk = buildCloudChunk(startIndex, engine)
        currentSegment = chunk.startSegment
        highlight(chunk.startSegment)

        val cached = prefetchedCloudFiles.remove(chunk.startSegment)
        if (cached != null && cached.exists()) {
            AppDiagnostics.info(
                this,
                "Cloud prebuffer hit: engine=$engine start=${chunk.startSegment} end=${chunk.endSegment} bytes=${cached.length()}"
            )
            playCloudFile(chunk = chunk, file = cached, token = token, engine = engine)
            return
        }

        listenButton.text = "Готовится…"

        Thread {
            try {
                val bytes = synthesizeCloudChunk(engine = engine, text = chunk.spoken)
                if (token != generationToken) return@Thread

                val file = writeCloudTempFile(
                    engine = engine,
                    token = token,
                    startSegment = chunk.startSegment,
                    bytes = bytes
                )

                mainHandler.post {
                    if (token != generationToken) {
                        file.delete()
                        return@post
                    }
                    playCloudFile(chunk = chunk, file = file, token = token, engine = engine)
                }
            } catch (error: Throwable) {
                AppDiagnostics.error(
                    this@ReaderActivity,
                    "Cloud TTS chunk failed: engine=$engine start=${chunk.startSegment} end=${chunk.endSegment}",
                    error
                )

                mainHandler.post {
                    if (token != generationToken) return@post
                    isPlaying = false
                    stopPlaybackBusyIndicators()
                    playPause.text = t("Продолжить", "Wznów", "Resume")
                    listenButton.text = "Слушать"
                    val message = UiText.localizeMessage(
                        this,
                        error.message ?: t("Ошибка TTS", "Błąd TTS", "TTS error")
                    )
                    resultText.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun synthesizeCloudChunk(engine: String, text: String): ByteArray =
        CloudTtsDispatcher.synthesize(
            context = this,
            engine = engine,
            text = text,
            speed = speechRate
        )

    private fun writeCloudTempFile(
        engine: String,
        token: Int,
        startSegment: Int,
        bytes: ByteArray
    ): File {
        val spec = CloudTtsDispatcher.tempAudioSpec(engine)
        return File(
            cacheDir,
            "${spec.prefix}_tts_${token}_${startSegment}.${spec.extension}"
        ).apply {
            writeBytes(bytes)
        }
    }

    private fun playCloudFile(
        chunk: CloudChunk,
        file: File,
        token: Int,
        engine: String
    ) {
        if (token != generationToken) {
            file.delete()
            return
        }

        stopMediaOnly()
        activeTempFile = file

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)

                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    if (activeTempFile == file) activeTempFile = null
                    file.delete()
                    if (token != generationToken) return@setOnCompletionListener

                    val next = chunk.endSegment + 1
                    if (next > segments.lastIndex) {
                        this@ReaderActivity.isPlaying = false
                        currentSegment = 0
                        playbackStartOffsetOverride = null
                        stopPlaybackBusyIndicators()
                        playPause.text = t("Сначала", "Start over")
                        listenButton.text = "Слушать"
                        resultText.text = completionText(engine)
                        stopPlaybackNotification()
                    } else {
                        playCloudChunk(startIndex = next, token = token, engine = engine)
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    AppDiagnostics.error(
                        this@ReaderActivity,
                        "MediaPlayer error: engine=$engine what=$what extra=$extra fileExists=${file.exists()} size=${file.length()}"
                    )
                    mp.release()
                    mediaPlayer = null
                    if (activeTempFile == file) activeTempFile = null
                    file.delete()
                    this@ReaderActivity.isPlaying = false
                    stopPlaybackBusyIndicators()
                    playPause.text = "Продолжить"
                    listenButton.text = "Слушать"
                    resultText.text = t("Ошибка воспроизведения.", "Błąd odtwarzania.", "Playback error.")
                    true
                }

                prepare()
                if (engine == SettingsStore.ENGINE_OPENAI) {
                    openAiRunAudioMillis += duration.toLong()
                }
                start()
                showPlaybackPlayingUi()
                scheduleChunkHighlights(chunk = chunk, durationMs = duration.toLong(), token = token)
            }

            AppDiagnostics.info(
                this@ReaderActivity,
                "Cloud playback started: engine=$engine start=${chunk.startSegment} end=${chunk.endSegment} file=${file.name} bytes=${file.length()}"
            )

            listenButton.text = "Читается"
            resultText.text =
                when (engine) {
                    SettingsStore.ENGINE_OPENAI -> "OpenAI • ${chunk.startSegment + 1}–${chunk.endSegment + 1}/${segments.size}"
                    SettingsStore.ENGINE_EDGE -> "Microsoft Edge • ${chunk.startSegment + 1}–${chunk.endSegment + 1}/${segments.size}"
                    SettingsStore.ENGINE_AZURE -> "Azure Speech • ${chunk.startSegment + 1}–${chunk.endSegment + 1}/${segments.size}"
                    SettingsStore.ENGINE_GOOGLE -> "Google Gemini • ${chunk.startSegment + 1}–${chunk.endSegment + 1}/${segments.size}"
                    SettingsStore.ENGINE_XAI -> "xAI Grok • ${chunk.startSegment + 1}–${chunk.endSegment + 1}/${segments.size}"
                    SettingsStore.ENGINE_SILERO -> "Silero v5.5 • ${chunk.startSegment + 1}–${chunk.endSegment + 1}/${segments.size}"
                    else -> "TTS"
                }

            if (!CloudTtsDispatcher.isPaidPrefetchSensitive(engine)) {
                prefetchCloudChunk(
                    startIndex = chunk.endSegment + 1,
                    token = token,
                    engine = engine
                )
            }
        } catch (error: Throwable) {
            if (activeTempFile == file) activeTempFile = null
            file.delete()
            mediaPlayer?.release()
            mediaPlayer = null
            this@ReaderActivity.isPlaying = false
            playPause.text = "Продолжить"
            listenButton.text = "Слушать"
            resultText.text = t("Ошибка воспроизведения.", "Błąd odtwarzania.", "Playback error.")

            AppDiagnostics.error(
                this@ReaderActivity,
                "Cloud MediaPlayer setup failed: engine=$engine",
                error
            )
            Toast.makeText(
                this@ReaderActivity,
                "Ошибка воспроизведения: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun prefetchCloudChunk(startIndex: Int, token: Int, engine: String) {
        if (
            startIndex !in segments.indices ||
            token != generationToken ||
            prefetchedCloudFiles.containsKey(startIndex) ||
            !prefetchingCloudSegments.add(startIndex)
        ) {
            return
        }

        val chunk = buildCloudChunk(startIndex, engine)
        Thread {
            try {
                val bytes = synthesizeCloudChunk(engine = engine, text = chunk.spoken)
                if (token != generationToken) return@Thread

                val file = writeCloudTempFile(
                    engine = engine,
                    token = token,
                    startSegment = chunk.startSegment,
                    bytes = bytes
                )

                if (token == generationToken) {
                    prefetchedCloudFiles[chunk.startSegment] = file
                    AppDiagnostics.info(
                        this@ReaderActivity,
                        "Cloud prebuffer ready: engine=$engine start=${chunk.startSegment} end=${chunk.endSegment} bytes=${file.length()}"
                    )
                } else {
                    file.delete()
                }
            } catch (error: Throwable) {
                AppDiagnostics.error(
                    this@ReaderActivity,
                    "Cloud prebuffer failed: engine=$engine start=$startIndex",
                    error
                )
            } finally {
                prefetchingCloudSegments.remove(startIndex)
            }
        }.start()
    }

    private fun scheduleChunkHighlights(chunk: CloudChunk, durationMs: Long, token: Int) {
        val indices = (chunk.startSegment..chunk.endSegment).toList()
        if (indices.size <= 1 || durationMs <= 0) {
            currentSegment = chunk.startSegment
            highlight(chunk.startSegment)
            return
        }

        val weights = indices.map {
            segments[it].spoken.count { ch -> !ch.isWhitespace() }.coerceAtLeast(1)
        }
        val total = weights.sum().coerceAtLeast(1)
        var cumulative = 0

        indices.forEachIndexed { position, segmentIndex ->
            val delay = if (position == 0) 0L else durationMs * cumulative / total
            mainHandler.postDelayed(
                {
                    if (token == generationToken && isPlaying) {
                        currentSegment = segmentIndex
                        highlight(segmentIndex)
                    }
                },
                delay
            )
            cumulative += weights[position]
        }
    }

    private fun completionText(engine: String): String =
        when (engine) {
            SettingsStore.ENGINE_OPENAI ->
                t(
                    "Чтение завершено • OpenAI ≈ €${String.format(Locale.US, "%.3f", estimatedOpenAiRunCost())}",
                    "Czytanie zakończone • OpenAI ≈ €${String.format(Locale.US, "%.3f", estimatedOpenAiRunCost())}",
                    "Reading complete • OpenAI ≈ €${String.format(Locale.US, "%.3f", estimatedOpenAiRunCost())}"
                )
            SettingsStore.ENGINE_XAI ->
                t(
                    "Чтение завершено • xAI Grok TTS",
                    "Czytanie zakończone • xAI Grok TTS",
                    "Reading complete • xAI Grok TTS"
                )
            SettingsStore.ENGINE_EDGE ->
                t(
                    "Чтение завершено • Microsoft Edge: бесплатно",
                    "Czytanie zakończone • Microsoft Edge: bezpłatnie",
                    "Reading complete • Microsoft Edge: free"
                )
            SettingsStore.ENGINE_AZURE ->
                t("Чтение завершено • Azure Speech", "Czytanie zakończone • Azure Speech", "Reading complete • Azure Speech")
            SettingsStore.ENGINE_GOOGLE ->
                t("Чтение завершено • Google Gemini TTS", "Czytanie zakończone • Google Gemini TTS", "Reading complete • Google Gemini TTS")
            SettingsStore.ENGINE_SILERO ->
                t("Чтение завершено • Silero v5.5 локально", "Czytanie zakończone • Silero v5.5 lokalnie", "Reading complete • Silero v5.5 local")
            else ->
                t("Чтение завершено.", "Czytanie zakończone.", "Reading complete.")
        }

    private fun clearCloudPrefetch() {
        prefetchedCloudFiles.values.forEach { it.delete() }
        prefetchedCloudFiles.clear()
        prefetchingCloudSegments.clear()
    }

    private fun estimatedOpenAiRunCost(): Double =
        openAiRunAudioMillis / 60_000.0 * 0.013

    private fun showPlaybackPreparingUi() {
        if (::playPause.isInitialized) PlaybackBusyIndicator.start(playPause)
        if (editMode && ::editorListenButton.isInitialized) {
            PlaybackBusyIndicator.start(editorListenButton)
        }
    }

    private fun showPlaybackPlayingUi() {
        if (::playPause.isInitialized) {
            PlaybackBusyIndicator.stop(playPause, t("Пауза", "Pause"))
        }
        if (::editorListenButton.isInitialized) {
            PlaybackBusyIndicator.stop(editorListenButton, if (editMode) "‖" else "▶")
        }
    }

    private fun stopPlaybackBusyIndicators() {
        if (::playPause.isInitialized) PlaybackBusyIndicator.stop(playPause, null)
        if (::editorListenButton.isInitialized) PlaybackBusyIndicator.stop(editorListenButton, "▶")
    }

    private fun pauseSpeech() {
        val wasPlaying = isPlaying
        generationToken += 1
        tts?.stop()
        stopMediaOnly()
        clearCloudPrefetch()
        isPlaying = false
        stopPlaybackBusyIndicators()
        if (wasPlaying) syncPlaybackNotification()

        if (::playPause.isInitialized) {
            playPause.text =
                if (editMode) {
                    t(
                        "Слушать от курсора",
                        "Czytaj od kursora",
                        "Listen from cursor"
                    )
                } else {
                    t("Продолжить", "Resume")
                }
        }
        if (::listenButton.isInitialized && !editMode) listenButton.text = "Слушать"

        if (
            ::resultText.isInitialized &&
            SettingsStore.engine(this) == SettingsStore.ENGINE_OPENAI &&
            openAiRunAudioMillis > 0
        ) {
            resultText.text =
                t(
                    "OpenAI уже сгенерировано ≈ €${String.format(Locale.US, "%.3f", estimatedOpenAiRunCost())}",
                    "OpenAI już wygenerowano ≈ €${String.format(Locale.US, "%.3f", estimatedOpenAiRunCost())}",
                    "OpenAI generated so far ≈ €${String.format(Locale.US, "%.3f", estimatedOpenAiRunCost())}"
                )
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
        val editOffset =
            segments.getOrNull(currentSegment)?.start
                ?.coerceIn(0, text.length)
                ?: 0

        pauseSpeech()
        editMode = true
        editor.setText(text)
        val safeOffset = editOffset.coerceIn(0, editor.text.length)
        editor.setSelection(safeOffset)
        textView.visibility = View.GONE
        editor.visibility = View.VISIBLE
        sourceView?.visibility = View.GONE
        editorToolbar.visibility = View.VISIBLE
        player.visibility = View.GONE
        editButton.text = t("Сохранить", "Save")
        playPause.isEnabled = true
        playPause.text =
            t(
                "Слушать от курсора",
                "Czytaj od kursora",
                "Listen from cursor"
            )
        engineButton.isEnabled = true
        voiceButton.isEnabled = true
        saveAudioButton.isEnabled = false
        saveRow.visibility = View.GONE
        updateEditorToolVisibility()
        editor.requestFocus()
        scrollEditorToOffset(safeOffset)
    }

    private fun saveEditedText() {
        if (isPlaying) pauseSpeech()

        val raw = editor.text.toString()
        val leadingTrim = raw.length - raw.trimStart().length
        val updated = raw.trim()
        if (updated.isBlank()) {
            Toast.makeText(
                this,
                t("Текст пустой", "Tekst jest pusty", "Text is empty"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val editorOffset =
            maxOf(editor.selectionStart, editor.selectionEnd)
                .coerceAtLeast(0)
        val savedOffset =
            (editorOffset - leadingTrim).coerceIn(0, updated.length)

        text = updated
        segments = segmentText(text)
        currentSegment = findSegmentForOffset(savedOffset)
        playbackStartOffsetOverride = null
        openAiConfirmedHash = null
        xaiConfirmedHash = null

        if (libraryId == null) {
            libraryId = LibraryStore.add(this, title, source, text)
        } else {
            LibraryStore.update(this, libraryId!!, title, source, text)
        }

        textView.text = text
        editor.visibility = View.GONE
        textView.visibility = View.VISIBLE
        editMode = false
        editButton.text = t("Редактировать", "Edit")
        playPause.isEnabled = true
        playPause.text = t("Слушать", "Listen")
        engineButton.isEnabled = true
        voiceButton.isEnabled = true
        saveAudioButton.isEnabled = true
        saveRow.visibility = View.VISIBLE
        editorToolbar.visibility = View.GONE
        sourceView?.visibility = if (source.isNotBlank()) View.VISIBLE else View.GONE
        player.visibility = View.VISIBLE
        hideEditorKeyboard()
        updateEditorToolVisibility()
        resultText.text = t("Текст сохранён.", "Text saved.")
        if (segments.isNotEmpty()) highlight(currentSegment)

        Toast.makeText(
            this,
            t("Текст сохранён", "Tekst zapisany", "Text saved"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun prepareEditorPlaybackSnapshot(): Boolean {
        val draft = editor.text.toString()
        val draftChanged = draft != text
        if (draft.isBlank()) {
            Toast.makeText(
                this,
                t("Текст пустой", "Tekst jest pusty", "Text is empty"),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        text = draft
        segments = segmentText(text)
        if (segments.isEmpty()) return false

        val selectionStart = editor.selectionStart.coerceIn(0, text.length)
        val selectionEnd = editor.selectionEnd.coerceIn(0, text.length)
        val cursor =
            if (selectionStart != selectionEnd) {
                minOf(selectionStart, selectionEnd)
            } else {
                selectionStart
            }
        val playbackOffset =
            if (SettingsStore.engine(this) == SettingsStore.ENGINE_XAI) {
                GrokEditorMarkup.playbackStartOffset(draft, cursor)
            } else {
                cursor
            }
        currentSegment = findSegmentForOffset(playbackOffset)
        playbackStartOffsetOverride = playbackOffset
        if (draftChanged) {
            openAiConfirmedHash = null
            xaiConfirmedHash = null
        }
        resultText.text =
            t(
                "Редактор • чтение от курсора",
                "Edytor • czytanie od kursora",
                "Editor • reading from cursor"
            )
        return true
    }

    private fun findSegmentForOffset(offset: Int): Int {
        if (segments.isEmpty()) return 0
        val safe = offset.coerceIn(0, text.length)
        return segments.indexOfFirst { safe < it.end }
            .let { if (it >= 0) it else segments.lastIndex }
    }

    private fun scrollEditorToOffset(offset: Int) {
        editor.post {
            val layout = editor.layout ?: return@post
            val safe = offset.coerceIn(0, editor.text.length)
            val line = layout.getLineForOffset(safe)
            val y =
                (layout.getLineTop(line) - scroll.height / 3)
                    .coerceAtLeast(0)
            scroll.scrollTo(0, y)
        }
    }

    private fun updateEditorToolVisibility() {
        if (!::grokEditToolsButton.isInitialized || !::grokStressButton.isInitialized) return
        GrokEditorToolbar.updateVisibility(
            stressButton = grokStressButton,
            menuButton = grokEditToolsButton,
            editMode = editMode,
            engine = SettingsStore.engine(this)
        )
    }

    private fun hideEditorKeyboard() {
        val input =
            getSystemService(INPUT_METHOD_SERVICE) as?
                android.view.inputmethod.InputMethodManager
        input?.hideSoftInputFromWindow(editor.windowToken, 0)
        editor.clearFocus()
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
            val index = currentSegment
            pauseSpeech()
            currentSegment = index

            if (editMode) {
                startOrResume()
            } else {
                when {
                    engine == SettingsStore.ENGINE_OPENAI -> startOpenAiFrom(currentSegment)
                    engine == SettingsStore.ENGINE_XAI -> startXaiFrom(currentSegment)
                    engine == SettingsStore.ENGINE_EDGE -> startEdgeFrom(currentSegment)
                    engine == SettingsStore.ENGINE_AZURE -> startAzureFrom(currentSegment)
                    engine == SettingsStore.ENGINE_GOOGLE -> startGoogleFrom(currentSegment)
                    engine == SettingsStore.ENGINE_SILERO -> startSileroFrom(currentSegment)
                    engine.startsWith("android:") -> startAndroidTts()
                }
            }
        }
    }

    private fun updateEngineLabels() {
        val engine = SettingsStore.engine(this)
        engineButton.text =
            when {
                engine == SettingsStore.ENGINE_OPENAI -> "OpenAI"
                engine == SettingsStore.ENGINE_XAI -> "xAI Grok"
                engine == SettingsStore.ENGINE_EDGE -> "Edge"
                engine == SettingsStore.ENGINE_SILERO -> "Silero"
                engine == SettingsStore.ENGINE_AZURE -> "Azure"
                engine == SettingsStore.ENGINE_GOOGLE -> "Google Gemini"
                engine == SettingsStore.DEFAULT_ENGINE -> "Android TTS"
                engine.startsWith("android:") ->
                    engine.removePrefix("android:").substringAfterLast('.').take(18)
                else -> t("Движок", "Engine")
            }

        if (::saveAudioButton.isInitialized) {
            saveAudioButton.text =
                if (exportInProgress) {
                    t("Отменить $currentExportFormat", "Anuluj $currentExportFormat", "Cancel $currentExportFormat")
                } else if (
                    engine == SettingsStore.ENGINE_GOOGLE ||
                    engine == SettingsStore.ENGINE_SILERO ||
                    engine.startsWith("android:")
                ) {
                    t("Сохранить WAV", "Zapisz WAV", "Save WAV")
                } else {
                    t("Сохранить MP3", "Zapisz MP3", "Save MP3")
                }
        }

        val voice = SettingsStore.voice(this)
        val voices =
            if (engine == SettingsStore.ENGINE_SILERO) {
                SileroRuntime.voiceChoices(this)
            } else {
                VoiceCatalog.staticVoices(engine)
            }
        voiceButton.text =
            voices.firstOrNull { it.id == voice }?.label?.take(20)
                ?: voice.ifBlank { t("Голос", "Voice") }.take(20)

        updateEditorToolVisibility()
    }

    private class ExportCancelledException : RuntimeException()

    private fun beginAudioExport(format: String) {
        exportCancelled = false
        exportInProgress = true
        currentExportFormat = format
        saveAudioButton.isEnabled = true
        saveAudioButton.text = t("Отменить $format", "Anuluj $format", "Cancel $format")
        exportProgress.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        exportProgress.progress = 0
        progressText.text = t("Создание $format: 0%", "Tworzenie $format: 0%", "Creating $format: 0%")
        resultText.text = ""
        startExportForegroundService(format)
    }

    private fun restoreAudioExportButton() {
        exportInProgress = false
        exportThread = null
        saveAudioButton.isEnabled = true
        updateEngineLabels()
        stopExportForegroundService()
    }

    private fun cancelAudioExport() {
        if (!exportInProgress) return
        exportCancelled = true
        saveAudioButton.isEnabled = false
        saveAudioButton.text = t("Отменяется…", "Anulowanie…", "Cancelling…")
        progressText.text = t("Отмена создания файла…", "Anulowanie tworzenia pliku…", "Cancelling audio export…")
        tts?.stop()
        ttsExportLatches.values.forEach { it.countDown() }
        exportThread?.interrupt()
    }

    private fun startExportForegroundService(format: String) {
        try {
            startForegroundService(
                Intent(this, ReaderExportService::class.java).apply {
                    action = ReaderExportService.ACTION_START
                    putExtra(ReaderExportService.EXTRA_FORMAT, format)
                    putExtra(ReaderExportService.EXTRA_PERCENT, 0)
                }
            )
        } catch (error: Throwable) {
            AppDiagnostics.error(this, "Could not start audio export foreground service", error)
        }
    }

    private fun updateExportForegroundService(percent: Int) {
        if (!exportInProgress) return
        try {
            startService(
                Intent(this, ReaderExportService::class.java).apply {
                    action = ReaderExportService.ACTION_PROGRESS
                    putExtra(ReaderExportService.EXTRA_FORMAT, currentExportFormat)
                    putExtra(ReaderExportService.EXTRA_PERCENT, percent.coerceIn(0, 100))
                }
            )
        } catch (_: Throwable) {
        }
    }

    private fun stopExportForegroundService() {
        try {
            startService(
                Intent(this, ReaderExportService::class.java).apply {
                    action = ReaderExportService.ACTION_STOP
                }
            )
        } catch (_: Throwable) {
        }
    }

    private fun checkExportCancelled() {
        if (exportCancelled || Thread.currentThread().isInterrupted) {
            throw ExportCancelledException()
        }
    }

    private fun showExportCancelled(format: String) {
        restoreAudioExportButton()
        progressText.text = t("$format отменён", "Eksport $format anulowany", "$format export cancelled")
        resultText.text = t(
            "Создание аудиофайла отменено.",
            "Tworzenie pliku audio anulowano.",
            "Audio export cancelled."
        )
        mainHandler.postDelayed(
            {
                if (!exportInProgress) {
                    exportProgress.visibility = View.GONE
                    progressText.visibility = View.GONE
                }
            },
            2200
        )
    }

    private fun splitForAndroidFile(value: String, maxChars: Int = 2600): List<String> {
        val pieces = segmentText(value).map { it.spoken.trim() }.filter { it.isNotBlank() }
        if (pieces.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val builder = StringBuilder()

        for (piece in pieces) {
            if (builder.isNotEmpty() && builder.length + 1 + piece.length > maxChars) {
                result += builder.toString()
                builder.setLength(0)
            }

            if (piece.length > maxChars) {
                if (builder.isNotEmpty()) {
                    result += builder.toString()
                    builder.setLength(0)
                }
                var start = 0
                while (start < piece.length) {
                    val end = minOf(start + maxChars, piece.length)
                    result += piece.substring(start, end)
                    start = end
                }
            } else {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(piece)
            }
        }

        if (builder.isNotEmpty()) result += builder.toString()
        return result
    }

    private fun exportAndroidWav(uri: Uri) {
        pauseSpeech()
        player.visibility = View.VISIBLE
        beginAudioExport("WAV")

        if (!ttsReady) {
            initAndroidTts { startAndroidWavExport(uri) }
        } else {
            startAndroidWavExport(uri)
        }
    }

    private fun startAndroidWavExport(uri: Uri) {
        val chunks = splitForAndroidFile(text)
        if (chunks.isEmpty()) {
            restoreAudioExportButton()
            resultText.text = t("Пустой текст", "Empty text")
            return
        }

        exportThread = Thread {
            val dir = File(cacheDir, "android_tts_export").apply { mkdirs() }
            val files = mutableListOf<File>()

            try {
                chunks.forEachIndexed { index, chunk ->
                    checkExportCancelled()

                    val file = File(dir, "part_${System.nanoTime()}_$index.wav")
                    val utteranceId = "file_${System.nanoTime()}_$index"
                    val latch = java.util.concurrent.CountDownLatch(1)
                    ttsExportLatches[utteranceId] = latch
                    ttsExportFailures.remove(utteranceId)

                    val status = tts?.synthesizeToFile(chunk, null, file, utteranceId) ?: TextToSpeech.ERROR
                    if (status != TextToSpeech.SUCCESS) {
                        ttsExportLatches.remove(utteranceId)
                        error(t("Android TTS не начал создание WAV", "Android TTS nie rozpoczął tworzenia WAV", "Android TTS did not start WAV synthesis"))
                    }

                    while (!latch.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        checkExportCancelled()
                    }
                    ttsExportLatches.remove(utteranceId)
                    checkExportCancelled()

                    if (utteranceId in ttsExportFailures) {
                        ttsExportFailures.remove(utteranceId)
                        error(t("Android TTS не смог создать WAV-фрагмент", "Android TTS nie utworzył fragmentu WAV", "Android TTS could not create a WAV fragment"))
                    }
                    if (!file.exists() || file.length() < 44) {
                        error(t("Android TTS вернул пустой WAV-фрагмент", "Android TTS zwrócił pusty fragment WAV", "Android TTS returned an empty WAV fragment"))
                    }

                    files += file
                    val percent = ((index + 1) * 90 / chunks.size).coerceIn(0, 90)
                    mainHandler.post {
                        exportProgress.progress = percent
                        updateExportForegroundService(percent)
                        progressText.text = t(
                            "Создание WAV: ${index + 1}/${chunks.size} • $percent%",
                            "Tworzenie WAV: ${index + 1}/${chunks.size} • $percent%",
                            "Creating WAV: ${index + 1}/${chunks.size} • $percent%"
                        )
                    }
                }

                checkExportCancelled()
                contentResolver.openOutputStream(uri)?.use { output ->
                    WavTools.joinTo(files, output)
                } ?: error(t("Не удалось открыть файл", "Nie udało się otworzyć pliku", "Could not open file"))
                checkExportCancelled()

                mainHandler.post {
                    exportProgress.progress = 100
                    progressText.text = t("WAV полностью записан • 100%", "WAV zapisany • 100%", "WAV complete • 100%")
                    resultText.text = t("WAV сохранён • Android TTS", "WAV zapisany • Android TTS", "WAV saved • Android TTS")
                    restoreAudioExportButton()

                    AlertDialog.Builder(this)
                        .setTitle(t("WAV готов", "WAV gotowy", "WAV ready"))
                        .setMessage(t("Файл полностью создан и записан.", "Plik został utworzony i zapisany.", "The file has been created and saved."))
                        .setPositiveButton("OK", null)
                        .show()

                    mainHandler.postDelayed(
                        {
                            exportProgress.visibility = View.GONE
                            progressText.visibility = View.GONE
                        },
                        4500
                    )
                }
            } catch (_: ExportCancelledException) {
                mainHandler.post { showExportCancelled("WAV") }
            } catch (_: InterruptedException) {
                mainHandler.post { showExportCancelled("WAV") }
            } catch (error: Throwable) {
                if (exportCancelled) {
                    mainHandler.post { showExportCancelled("WAV") }
                } else {
                    AppDiagnostics.error(this@ReaderActivity, "Android WAV export failed", error)
                    mainHandler.post {
                        restoreAudioExportButton()
                        progressText.text = t("Ошибка создания WAV", "Błąd tworzenia WAV", "WAV export error")
                        val message = UiText.localizeMessage(
                            this,
                            error.message ?: t("Ошибка Android TTS", "Błąd Android TTS", "Android TTS error")
                        )
                        resultText.text = message
                        AlertDialog.Builder(this)
                            .setTitle(t("WAV не создан", "Nie utworzono WAV", "WAV not created"))
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } finally {
                ttsExportLatches.clear()
                files.forEach { it.delete() }
            }
        }.also { it.start() }
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
        val engine = SettingsStore.engine(this)

        when (engine) {
            SettingsStore.ENGINE_OPENAI -> {
                if (SettingsStore.openAiKey(this).isBlank()) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    return
                }

                val cost = OpenAiTtsClient.estimatedCostEuro(text)
                val mins = OpenAiTtsClient.estimatedMinutes(text)

                AlertDialog.Builder(this)
                    .setTitle(t("Создать MP3 через OpenAI?", "Utworzyć MP3 przez OpenAI?", "Create MP3 with OpenAI?"))
                    .setMessage(
                        t(
                            "Отдельная генерация всего текста: ≈ ${String.format(Locale.US, "%.1f", mins)} мин, ориентировочно €${String.format(Locale.US, "%.2f", cost)}.",
                            "Osobne generowanie całego tekstu: ≈ ${String.format(Locale.US, "%.1f", mins)} min, szacunkowo €${String.format(Locale.US, "%.2f", cost)}.",
                            "Separate generation of the full text: ≈ ${String.format(Locale.US, "%.1f", mins)} min, estimated €${String.format(Locale.US, "%.2f", cost)}."
                        )
                    )
                    .setNegativeButton(t("Нет", "Nie", "No"), null)
                    .setPositiveButton(t("Создать", "Utwórz", "Create")) { _, _ ->
                        pendingExportEngine = SettingsStore.ENGINE_OPENAI
                        chooseAudioDestination()
                    }
                    .show()
            }

            SettingsStore.ENGINE_XAI -> {
                if (SettingsStore.xaiApiKey(this).isBlank()) {
                    showMissingKey(
                        title = "xAI Grok TTS",
                        message = t(
                            "Введите xAI API key в Настройках.",
                            "Wprowadź klucz API xAI w Ustawieniach.",
                            "Enter the xAI API key in Settings."
                        )
                    )
                    return
                }

                val cost = XaiTtsClient.estimatedCostEuro(text)
                AlertDialog.Builder(this)
                    .setTitle(t("Создать MP3 через xAI Grok?", "Utworzyć MP3 przez xAI Grok?", "Create MP3 with xAI Grok?"))
                    .setMessage(
                        t(
                            "Отдельная генерация всего текста: ${text.length} символов, ориентировочно €${String.format(Locale.US, "%.2f", cost)}.",
                            "Osobne generowanie całego tekstu: ${text.length} znaków, szacunkowo €${String.format(Locale.US, "%.2f", cost)}.",
                            "Separate generation of the full text: ${text.length} characters, estimated €${String.format(Locale.US, "%.2f", cost)}."
                        )
                    )
                    .setNegativeButton(t("Нет", "Nie", "No"), null)
                    .setPositiveButton(t("Создать", "Utwórz", "Create")) { _, _ ->
                        pendingExportEngine = SettingsStore.ENGINE_XAI
                        chooseAudioDestination()
                    }
                    .show()
            }

            SettingsStore.ENGINE_EDGE -> {
                pendingExportEngine = SettingsStore.ENGINE_EDGE
                chooseAudioDestination()
            }

            SettingsStore.ENGINE_GOOGLE -> {
                if (SettingsStore.googleApiKey(this).isBlank()) {
                    showMissingKey(
                        title = "Google Gemini TTS",
                        message = t(
                            "Введите Google Gemini API key в Настройках.",
                            "Wprowadź klucz API Google Gemini w Ustawieniach.",
                            "Enter the Google Gemini API key in Settings."
                        )
                    )
                    return
                }
                pendingExportEngine = SettingsStore.ENGINE_GOOGLE
                chooseAudioDestination()
            }

            SettingsStore.ENGINE_AZURE -> {
                if (
                    SettingsStore.azureSpeechKey(this).isBlank() ||
                    SettingsStore.azureRegion(this).isBlank()
                ) {
                    showMissingKey(
                        title = "Azure Speech",
                        message = t(
                            "Введите Azure Speech key и region в Настройках.",
                            "Wprowadź klucz Azure Speech i region w Ustawieniach.",
                            "Enter the Azure Speech key and region in Settings."
                        )
                    )
                    return
                }
                pendingExportEngine = SettingsStore.ENGINE_AZURE
                chooseAudioDestination()
            }

            else -> {
                if (engine.startsWith("android:")) {
                    pendingExportEngine = engine
                    chooseAudioDestination()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(t("Аудиоэкспорт", "Eksport audio", "Audio export"))
                        .setMessage(
                            t(
                                "Для этого движка аудиоэкспорт пока недоступен.",
                                "Eksport audio dla tego silnika nie jest jeszcze dostępny.",
                                "Audio export is not available for this engine yet."
                            )
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun chooseAudioDestination() {
        val engine = pendingExportEngine ?: SettingsStore.engine(this)
        val isWav = engine == SettingsStore.ENGINE_GOOGLE || engine.startsWith("android:")

        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (isWav) "audio/wav" else "audio/mpeg"
                putExtra(
                    Intent.EXTRA_TITLE,
                    safeFileName(title) + if (isWav) ".wav" else ".mp3"
                )
            },
            REQ_SAVE_AUDIO
        )
    }

    @Deprecated("legacy result handling is sufficient for this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_SAVE_TEXT -> exportText(uri)
            REQ_SAVE_AUDIO -> exportMp3(uri, pendingExportEngine ?: SettingsStore.engine(this))
        }
    }

    private fun exportText(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: error(t("Не удалось открыть файл", "Nie udało się otworzyć pliku", "Could not open file"))

            resultText.text = t("Текстовый файл сохранён.", "Plik tekstowy zapisany.", "Text file saved.")
            Toast.makeText(this, t("Файл сохранён", "Plik zapisany", "File saved"), Toast.LENGTH_SHORT).show()
        } catch (error: Throwable) {
            val detail = UiText.localizeMessage(this, error.message ?: "")
            resultText.text = t("Ошибка сохранения: $detail", "Błąd zapisu: $detail", "Save error: $detail")
            Toast.makeText(
                this,
                detail.ifBlank { t("Ошибка сохранения", "Błąd zapisu", "Save error") },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportMp3(uri: Uri, engine: String) {
        if (engine == SettingsStore.ENGINE_GOOGLE) {
            exportGoogleWav(uri)
            return
        }
        if (engine.startsWith("android:")) {
            exportAndroidWav(uri)
            return
        }

        pauseSpeech()
        player.visibility = View.VISIBLE
        listenButton.text = "Создаётся MP3…"
        beginAudioExport("MP3")

        val chunks = CloudTtsDispatcher.splitForExport(engine, text)
        exportThread = Thread {
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    chunks.forEachIndexed { index, chunk ->
                        checkExportCancelled()
                        val bytes = CloudTtsDispatcher.synthesize(
                            context = this@ReaderActivity,
                            engine = engine,
                            text = chunk,
                            speed = speechRate
                        )
                        checkExportCancelled()

                        output.write(
                            if (index == 0) bytes
                            else OpenAiTtsClient.stripLeadingId3(bytes)
                        )
                        output.flush()

                        val percent = ((index + 1) * 100 / chunks.size).coerceIn(0, 100)
                        mainHandler.post {
                            exportProgress.progress = percent
                            updateExportForegroundService(percent)
                            progressText.text = t(
                                "Создание MP3: ${index + 1}/${chunks.size} • $percent%",
                                "Tworzenie MP3: ${index + 1}/${chunks.size} • $percent%",
                                "Creating MP3: ${index + 1}/${chunks.size} • $percent%"
                            )
                            listenButton.text = "MP3 $percent%"
                        }
                    }
                } ?: error("Не удалось открыть файл")

                mainHandler.post {
                    exportProgress.progress = 100
                    updateExportForegroundService(100)
                    progressText.text = t("MP3 полностью записан • 100%", "MP3 zapisany • 100%", "MP3 complete • 100%")
                    listenButton.text = "Слушать"
                    restoreAudioExportButton()

                    val costLine =
                        when (engine) {
                            SettingsStore.ENGINE_OPENAI ->
                                t(
                                    "Ориентировочная стоимость этой генерации ≈ €${String.format(Locale.US, "%.2f", OpenAiTtsClient.estimatedCostEuro(text))}",
                                    "Szacowany koszt tej generacji ≈ €${String.format(Locale.US, "%.2f", OpenAiTtsClient.estimatedCostEuro(text))}",
                                    "Estimated cost of this generation ≈ €${String.format(Locale.US, "%.2f", OpenAiTtsClient.estimatedCostEuro(text))}"
                                )
                            SettingsStore.ENGINE_XAI ->
                                t(
                                    "xAI Grok: ориентировочная стоимость ≈ €${String.format(Locale.US, "%.2f", XaiTtsClient.estimatedCostEuro(text))}",
                                    "xAI Grok: szacowany koszt ≈ €${String.format(Locale.US, "%.2f", XaiTtsClient.estimatedCostEuro(text))}",
                                    "xAI Grok: estimated cost ≈ €${String.format(Locale.US, "%.2f", XaiTtsClient.estimatedCostEuro(text))}"
                                )
                            SettingsStore.ENGINE_EDGE ->
                                t("Microsoft Edge: бесплатно", "Microsoft Edge: bezpłatnie", "Microsoft Edge: free")
                            else ->
                                t("Azure Speech: F0 бесплатно в пределах квоты", "Azure Speech: F0 bezpłatnie w ramach limitu", "Azure Speech: F0 free within quota")
                        }

                    resultText.text = t("MP3 сохранён. $costLine", "MP3 zapisany. $costLine", "MP3 saved. $costLine")

                    AlertDialog.Builder(this)
                        .setTitle(t("MP3 готов", "MP3 gotowy", "MP3 ready"))
                        .setMessage(
                            t(
                                "Файл полностью создан и записан.\n\n$costLine",
                                "Plik został utworzony i zapisany.\n\n$costLine",
                                "The file has been created and saved.\n\n$costLine"
                            )
                        )
                        .setPositiveButton("OK", null)
                        .show()

                    mainHandler.postDelayed(
                        {
                            exportProgress.visibility = View.GONE
                            progressText.visibility = View.GONE
                        },
                        4500
                    )
                }
            } catch (_: ExportCancelledException) {
                mainHandler.post { showExportCancelled("MP3") }
            } catch (_: InterruptedException) {
                mainHandler.post { showExportCancelled("MP3") }
            } catch (error: Throwable) {
                if (exportCancelled) {
                    mainHandler.post { showExportCancelled("MP3") }
                } else {
                    AppDiagnostics.error(this@ReaderActivity, "MP3 export failed: engine=$engine", error)
                    mainHandler.post {
                        restoreAudioExportButton()
                        listenButton.text = "Слушать"
                        exportProgress.visibility = View.VISIBLE
                        progressText.visibility = View.VISIBLE
                        progressText.text = t("Ошибка создания MP3", "Błąd tworzenia MP3", "MP3 export error")
                        val message = UiText.localizeMessage(
                            this,
                            error.message ?: t("Ошибка создания MP3", "Błąd tworzenia MP3", "MP3 export error")
                        )
                        resultText.text = message
                        AlertDialog.Builder(this)
                            .setTitle(t("MP3 не создан", "Nie utworzono MP3", "MP3 not created"))
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }.also { it.start() }
    }

    private fun exportGoogleWav(uri: Uri) {
        pauseSpeech()
        player.visibility = View.VISIBLE
        listenButton.text = "Создаётся WAV…"
        beginAudioExport("WAV")

        val voice = SettingsStore.voice(this).ifBlank { "Gacrux" }
        val chunks = GoogleGeminiTtsClient.splitForApi(text)

        exportThread = Thread {
            val raw = File(cacheDir, "google_pcm_${System.nanoTime()}.raw")

            try {
                raw.outputStream().buffered().use { pcm ->
                    chunks.forEachIndexed { index, chunk ->
                        checkExportCancelled()

                        val bytes = GoogleGeminiTtsClient.synthesizePcm(
                            apiKey = SettingsStore.googleApiKey(this),
                            text = chunk,
                            voice = voice,
                            instructions = SettingsStore.googleInstructions(this),
                            context = this@ReaderActivity
                        )

                        checkExportCancelled()
                        pcm.write(bytes)
                        pcm.flush()

                        val percent = ((index + 1) * 90 / chunks.size).coerceIn(0, 90)
                        mainHandler.post {
                            exportProgress.progress = percent
                            progressText.text = t(
                                "Создание WAV: ${index + 1}/${chunks.size} • $percent%",
                                "Tworzenie WAV: ${index + 1}/${chunks.size} • $percent%",
                                "Creating WAV: ${index + 1}/${chunks.size} • $percent%"
                            )
                        }
                    }
                }

                checkExportCancelled()
                contentResolver.openOutputStream(uri)?.use { output ->
                    WavTools.pcmToWavTo(
                        pcmFile = raw,
                        output = output,
                        sampleRate = GoogleGeminiTtsClient.PCM_SAMPLE_RATE,
                        channels = GoogleGeminiTtsClient.PCM_CHANNELS,
                        bitsPerSample = GoogleGeminiTtsClient.PCM_BITS_PER_SAMPLE
                    )
                } ?: error(t("Не удалось открыть файл", "Nie udało się otworzyć pliku", "Could not open file"))

                checkExportCancelled()
                mainHandler.post {
                    exportProgress.progress = 100
                    updateExportForegroundService(100)
                    progressText.text = t("WAV полностью записан • 100%", "WAV zapisany • 100%", "WAV complete • 100%")
                    resultText.text = t(
                        "WAV сохранён • Google Gemini TTS",
                        "WAV zapisany • Google Gemini TTS",
                        "WAV saved • Google Gemini TTS"
                    )
                    restoreAudioExportButton()

                    AlertDialog.Builder(this)
                        .setTitle(t("WAV готов", "WAV gotowy", "WAV ready"))
                        .setMessage(t("Файл полностью создан и записан.", "Plik został utworzony i zapisany.", "The file has been created and saved."))
                        .setPositiveButton("OK", null)
                        .show()

                    mainHandler.postDelayed(
                        {
                            exportProgress.visibility = View.GONE
                            progressText.visibility = View.GONE
                        },
                        4500
                    )
                }
            } catch (_: ExportCancelledException) {
                mainHandler.post { showExportCancelled("WAV") }
            } catch (_: InterruptedException) {
                mainHandler.post { showExportCancelled("WAV") }
            } catch (error: Throwable) {
                if (exportCancelled) {
                    mainHandler.post { showExportCancelled("WAV") }
                } else {
                    AppDiagnostics.error(this@ReaderActivity, "Google WAV export failed", error)
                    mainHandler.post {
                        restoreAudioExportButton()
                        val message = UiText.localizeMessage(
                            this,
                            error.message ?: t("Ошибка Google Gemini", "Błąd Google Gemini", "Google Gemini error")
                        )
                        progressText.text = t("Ошибка создания WAV", "Błąd tworzenia WAV", "WAV export error")
                        resultText.text = message

                        AlertDialog.Builder(this)
                            .setTitle(t("WAV не создан", "Nie utworzono WAV", "WAV not created"))
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } finally {
                raw.delete()
            }
        }.also { it.start() }
    }

    private fun safeFileName(value: String): String =
        value
            .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
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

        if (result.isEmpty()) {
            addSegmentParts(value, 0, value.length, result)
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
            while (s < end && value[s].isWhitespace()) s++
            if (s >= end) break

            var e = minOf(s + 2800, end)
            if (e < end) {
                val candidate = value.lastIndexOfAny(
                    charArrayOf(' ', '\n', ',', ';'),
                    e
                )
                if (candidate > s + 600) e = candidate + 1
            }

            while (e > s && value[e - 1].isWhitespace()) e--
            if (e > s) {
                out.add(Segment(s, e, value.substring(s, e)))
            }
            s = maxOf(e, s + 1)
        }
    }

    override fun onDestroy() {
        generationToken += 1
        if (exportInProgress) {
            exportCancelled = true
            ttsExportLatches.values.forEach { it.countDown() }
            exportThread?.interrupt()
        }
        tts?.stop()
        tts?.shutdown()
        stopMediaOnly()
        stopPlaybackNotification()
        if (PlaybackBridge.controller === playbackController) PlaybackBridge.controller = null
        if (ExportBridge.controller === exportController) ExportBridge.controller = null
        if (!exportInProgress) stopExportForegroundService()
        super.onDestroy()
    }

    private fun syncPlaybackNotification() {
        if (::playPause.isInitialized) {
            PlaybackBridge.sync(this, title, isPlaying)
        }
    }

    private fun stopPlaybackNotification() {
        PlaybackBridge.stop(this)
    }

    private fun t(ru: String, en: String) = UiText.get(this, ru, en)

    private fun t(ru: String, pl: String, en: String) = UiText.get(this, ru, pl, en)

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)

    data class Segment(
        val start: Int,
        val end: Int,
        val spoken: String
    )

    data class CloudChunk(
        val startSegment: Int,
        val endSegment: Int,
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
