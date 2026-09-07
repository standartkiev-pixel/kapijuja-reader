package com.kapijuja.reader

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.BreakIterator
import java.util.Locale

class ReaderActivity : Activity() {
    private lateinit var scroll: ScrollView
    private lateinit var textView: TextView
    private lateinit var listenButton: Button
    private lateinit var player: LinearLayout
    private lateinit var playPause: Button
    private lateinit var engineButton: Button
    private lateinit var speedButton: Button

    private var text: String = ""
    private var title: String = ""
    private var source: String = ""
    private var libraryId: String? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var selectedEngine: String? = null
    private var segments: List<Segment> = emptyList()
    private var currentSegment = 0
    private var isPlaying = false
    private var speechRate = 1.0f
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KapijujaUiTheme.applyWindow(this)
        loadInput()
        segments = segmentText(text)
        buildScreen()
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

        listenButton = Button(this).apply {
            text = "Слушать"
            textSize = 16f
            setOnClickListener { startOrResume() }
        }
        KapijujaUiTheme.button(this, listenButton, primary = true)
        top.addView(listenButton, LinearLayout.LayoutParams(dp(110), dp(54)))
        root.addView(top)

        if (source.isNotBlank()) {
            val sourceView = TextView(this).apply {
                text = source
                textSize = 12f
                maxLines = 1
                setPadding(dp(6), dp(10), dp(6), dp(8))
            }
            KapijujaUiTheme.secondary(sourceView)
            root.addView(sourceView)
        }

        scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        textView = TextView(this).apply {
            this.text = this@ReaderActivity.text
            textSize = 20f
            setTextColor(KapijujaUiTheme.SILVER)
            setLineSpacing(dp(5).toFloat(), 1.08f)
            setPadding(dp(10), dp(14), dp(10), dp(32))
            textIsSelectable = true
        }
        scroll.addView(textView)
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
        controls.addView(speedButton, LinearLayout.LayoutParams(0, dp(56), 0.7f).apply {
            marginEnd = dp(7)
        })

        engineButton = Button(this).apply {
            text = "Голос"
            setOnClickListener { showEnginePicker() }
        }
        KapijujaUiTheme.button(this, engineButton)
        controls.addView(engineButton, LinearLayout.LayoutParams(0, dp(56), 1f))

        player.addView(controls)

        val save = Button(this).apply {
            text = "Сохранить MP3"
            textSize = 15f
            setOnClickListener {
                Toast.makeText(
                    this@ReaderActivity,
                    "Экспорт MP3 подключим вместе с Silero / Microsoft / Google на следующем шаге.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        KapijujaUiTheme.button(this, save)
        player.addView(save, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(8) })

        root.addView(player)
        setContentView(root)
    }

    private fun startOrResume() {
        if (text.isBlank()) return
        if (libraryId == null) {
            libraryId = LibraryStore.add(this, title, source, text)
        }
        player.visibility = View.VISIBLE

        if (!ttsReady) {
            initTts {
                speakFrom(currentSegment)
            }
        } else {
            speakFrom(currentSegment)
        }
    }

    private fun initTts(onReady: () -> Unit) {
        tts?.shutdown()
        ttsReady = false
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setSpeechRate(speechRate)
                installProgressListener()
                engineButton.text = tts?.defaultEngineLabel() ?: "Голос"
                onReady()
            } else {
                Toast.makeText(this, "TTS движок не запустился", Toast.LENGTH_LONG).show()
            }
        }
        tts = if (selectedEngine == null) {
            TextToSpeech(this, listener)
        } else {
            TextToSpeech(this, listener, selectedEngine)
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
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    isPlaying = false
                    playPause.text = "Продолжить"
                }
            }
        })
    }

    private fun speakFrom(index: Int) {
        if (!ttsReady || segments.isEmpty()) return
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

    private fun pauseSpeech() {
        tts?.stop()
        isPlaying = false
        playPause.text = "Продолжить"
        listenButton.text = "Слушать"
    }

    private fun highlight(index: Int) {
        if (index !in segments.indices) return
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
        if (isPlaying) speakFrom(currentSegment)
    }

    private fun showEnginePicker() {
        if (!ttsReady) {
            initTts { showEnginePicker() }
            return
        }
        val engines = tts?.engines.orEmpty()
        if (engines.isEmpty()) {
            Toast.makeText(this, "TTS движки не найдены", Toast.LENGTH_SHORT).show()
            return
        }
        val names = engines.map { it.label ?: it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Android TTS движок")
            .setItems(names) { _, which ->
                selectedEngine = engines[which].name
                engineButton.text = names[which]
                val wasPlaying = isPlaying
                pauseSpeech()
                initTts {
                    if (wasPlaying) speakFrom(currentSegment)
                }
            }
            .show()
    }

    private fun TextToSpeech.defaultEngineLabel(): String? {
        val pkg = defaultEngine ?: return null
        return engines.firstOrNull { it.name == pkg }?.label?.toString() ?: pkg
    }

    private fun segmentText(value: String): List<Segment> {
        if (value.isBlank()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int) = KapijujaUiTheme.dp(this, v)

    data class Segment(val start: Int, val end: Int, val spoken: String)

    companion object {
        const val EXTRA_LIBRARY_ID = "library_id"
        const val EXTRA_DRAFT_PATH = "draft_path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SOURCE = "source"
    }
}
