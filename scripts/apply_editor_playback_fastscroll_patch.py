#!/usr/bin/env python3
from pathlib import Path

reader_path = Path("app/src/main/java/com/kapijuja/reader/ReaderActivity.kt")
reader = reader_path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


reader = replace_once(
    reader,
    """    private var openAiRunAudioMillis = 0L\n    private var pendingExportEngine: String? = null\n""",
    """    private var openAiRunAudioMillis = 0L\n    private var playbackStartOffsetOverride: Int? = null\n    private var pendingExportEngine: String? = null\n""",
    "playback offset field",
)

reader = replace_once(
    reader,
    """        builtLanguage = UiText.language(this)\n        loadInput()\n        segments = segmentText(text)\n""",
    """        builtLanguage = UiText.language(this)\n        try {\n            loadInput()\n            ReaderLimits.requireDisplaySafe(text)\n        } catch (error: IllegalArgumentException) {\n            title = t(\"Слишком большой документ\", \"Zbyt duży dokument\", \"Document too large\")\n            source = \"\"\n            libraryId = null\n            text = error.message ?: t(\n                \"Документ не загружен из-за ограничения размера.\",\n                \"Dokument nie został wczytany z powodu limitu rozmiaru.\",\n                \"The document was not loaded because it exceeds the safety limit.\"\n            )\n        }\n        segments = segmentText(text)\n""",
    "safe load",
)

reader = replace_once(
    reader,
    """        scroll = ScrollView(this).apply {\n            isFillViewport = true\n        }\n""",
    """        scroll = ScrollView(this).apply {\n            isFillViewport = true\n            isVerticalScrollBarEnabled = false\n        }\n""",
    "scroll config",
)

reader = replace_once(
    reader,
    """            setPadding(dp(10), dp(12), dp(10), dp(28))\n""",
    """            setPadding(dp(10), dp(12), dp(30), dp(28))\n""",
    "text fast-scroll clearance",
)

reader = replace_once(
    reader,
    """        KapijujaUiTheme.input(this, editor)\n\n        editorToolbar = LinearLayout(this).apply {\n""",
    """        KapijujaUiTheme.input(this, editor)\n        editor.setPadding(editor.paddingLeft, editor.paddingTop, dp(30), editor.paddingBottom)\n\n        editorToolbar = LinearLayout(this).apply {\n""",
    "editor fast-scroll clearance",
)

reader = replace_once(
    reader,
    """        editorListenButton = Button(this).apply {\n            text = \"▶ ‖\"\n""",
    """        editorListenButton = Button(this).apply {\n            text = \"▶\"\n""",
    "editor play label",
)

reader = replace_once(
    reader,
    """        scroll.addView(contentFrame)\n        root.addView(\n            scroll,\n            LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                0,\n                1f\n            )\n        )\n""",
    """        scroll.addView(contentFrame)\n        val scrollHost = FrameLayout(this)\n        scrollHost.addView(\n            scroll,\n            FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                ViewGroup.LayoutParams.MATCH_PARENT\n            )\n        )\n        val fastScroller = ReaderFastScroller(this).apply { attachTo(scroll) }\n        scrollHost.addView(\n            fastScroller,\n            FrameLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT).apply {\n                gravity = Gravity.END\n            }\n        )\n        root.addView(\n            scrollHost,\n            LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                0,\n                1f\n            )\n        )\n""",
    "fast scroll host",
)

# Compact the normal bottom player without changing its functionality.
reader = replace_once(
    reader,
    """            setPadding(dp(10), dp(9), dp(10), dp(9))\n""",
    """            setPadding(dp(7), dp(5), dp(7), dp(5))\n""",
    "player padding",
)
reader = replace_once(reader, "LinearLayout.LayoutParams(0, dp(54), 1f).apply {", "LinearLayout.LayoutParams(0, dp(46), 1f).apply {", "play height")
reader = replace_once(reader, "LinearLayout.LayoutParams(0, dp(54), 0.65f).apply {", "LinearLayout.LayoutParams(0, dp(46), 0.65f).apply {", "speed height")
reader = replace_once(reader, "controls.addView(editButton, LinearLayout.LayoutParams(0, dp(54), 1.15f))", "controls.addView(editButton, LinearLayout.LayoutParams(0, dp(46), 1.15f))", "edit height")
reader = replace_once(reader, "setPadding(0, dp(7), 0, 0)\n        }\n\n        engineButton", "setPadding(0, dp(4), 0, 0)\n        }\n\n        engineButton", "voice row gap")
reader = replace_once(reader, "LinearLayout.LayoutParams(0, dp(52), 1f).apply {", "LinearLayout.LayoutParams(0, dp(42), 1f).apply {", "engine height")
reader = replace_once(reader, "voiceRow.addView(voiceButton, LinearLayout.LayoutParams(0, dp(52), 1f))", "voiceRow.addView(voiceButton, LinearLayout.LayoutParams(0, dp(42), 1f))", "voice height")
reader = replace_once(reader, "setPadding(0, dp(7), 0, 0)\n        }\n\n        val saveText", "setPadding(0, dp(4), 0, 0)\n        }\n\n        val saveText", "save row gap")
reader = replace_once(reader, "LinearLayout.LayoutParams(0, dp(50), 1f).apply {", "LinearLayout.LayoutParams(0, dp(42), 1f).apply {", "save text height")
reader = replace_once(reader, """                dp(50),\n                1f\n            )\n""", """                dp(42),\n                1f\n            )\n""", "save audio height")

reader = replace_once(
    reader,
    """        currentSegment = index\n        player.visibility = View.VISIBLE\n""",
    """        currentSegment = index\n        playbackStartOffsetOverride = playbackOffset\n        player.visibility = View.VISIBLE\n""",
    "tap exact start",
)

reader = replace_once(
    reader,
    """        if (!editMode && libraryId == null) {\n            libraryId = LibraryStore.add(this, title, source, text)\n        }\n        player.visibility = View.VISIBLE\n\n        when (val engine = SettingsStore.engine(this)) {\n""",
    """        if (!editMode && libraryId == null) {\n            libraryId = LibraryStore.add(this, title, source, text)\n        }\n        if (editMode) {\n            player.visibility = View.GONE\n            editorToolbar.visibility = View.VISIBLE\n        } else {\n            player.visibility = View.VISIBLE\n        }\n\n        when (val engine = SettingsStore.engine(this)) {\n""",
    "keep editor visible while listening",
)

reader = replace_once(
    reader,
    """    private fun startAndroidTts() {\n        if (!ttsReady) {\n            initAndroidTts { speakAndroidFrom(currentSegment) }\n""",
    """    private fun startAndroidTts() {\n        if (!ttsReady) {\n            showPlaybackPreparingUi()\n            initAndroidTts { speakAndroidFrom(currentSegment) }\n""",
    "android preparing",
)

reader = replace_once(
    reader,
    """                Toast.makeText(\n                    this,\n                    t(\n                        \"TTS движок не запустился\",\n""",
    """                stopPlaybackBusyIndicators()\n                Toast.makeText(\n                    this,\n                    t(\n                        \"TTS движок не запустился\",\n""",
    "android init failure indicator",
)

reader = replace_once(
    reader,
    """                        currentSegment = index\n                        isPlaying = true\n                        playPause.text = t(\"Пауза\", \"Pause\")\n                        listenButton.text = \"Читается\"\n""",
    """                        currentSegment = index\n                        isPlaying = true\n                        showPlaybackPlayingUi()\n                        listenButton.text = \"Читается\"\n""",
    "android onStart UI",
)

reader = replace_once(
    reader,
    """                            isPlaying = false\n                            currentSegment = 0\n                            playPause.text = t(\"Сначала\", \"Start over\")\n""",
    """                            isPlaying = false\n                            currentSegment = 0\n                            playbackStartOffsetOverride = null\n                            stopPlaybackBusyIndicators()\n                            playPause.text = t(\"Сначала\", \"Start over\")\n""",
    "android completion UI",
)

reader = replace_once(
    reader,
    """                    mainHandler.post {\n                        isPlaying = false\n                        playPause.text = t(\"Продолжить\", \"Resume\")\n""",
    """                    mainHandler.post {\n                        isPlaying = false\n                        stopPlaybackBusyIndicators()\n                        playPause.text = t(\"Продолжить\", \"Resume\")\n""",
    "android error UI",
)

reader = replace_once(
    reader,
    """            tts?.speak(\n                segments[i].spoken,\n""",
    """            tts?.speak(\n                spokenSegmentForPlayback(i, start),\n""",
    "android exact first segment",
)

reader = replace_once(
    reader,
    """        isPlaying = true\n        playPause.text = t(\"Пауза\", \"Pause\")\n        listenButton.text = \"Читается\"\n""",
    """        isPlaying = true\n        showPlaybackPlayingUi()\n        listenButton.text = \"Читается\"\n""",
    "android speaking UI",
)

reader = replace_once(
    reader,
    """    private fun remainingText(): String =\n        if (currentSegment in segments.indices) {\n            text.substring(segments[currentSegment].start)\n        } else {\n            text\n        }\n""",
    """    private fun remainingText(): String {\n        val exact = playbackStartOffsetOverride?.coerceIn(0, text.length)\n        if (exact != null) return text.substring(exact)\n        return if (currentSegment in segments.indices) {\n            text.substring(segments[currentSegment].start)\n        } else {\n            text\n        }\n    }\n""",
    "remaining exact text",
)

reader = replace_once(
    reader,
    """        val token = generationToken\n        isPlaying = true\n        playPause.text = t(\"Пауза\", \"Pause\")\n        listenButton.text = \"Готовится…\"\n""",
    """        val token = generationToken\n        isPlaying = true\n        showPlaybackPreparingUi()\n        listenButton.text = \"Готовится…\"\n""",
    "cloud preparing UI",
)

reader = replace_once(
    reader,
    """    private fun buildCloudChunk(\n""",
    """    private fun spokenSegmentForPlayback(index: Int, startIndex: Int): String {\n        val segment = segments[index]\n        val exact = playbackStartOffsetOverride\n        val raw =\n            if (\n                index == startIndex &&\n                exact != null &&\n                exact >= segment.start &&\n                exact < segment.end\n            ) {\n                text.substring(exact, segment.end)\n            } else {\n                segment.spoken\n            }\n        return raw.replace(Regex(\"\\\\s+\"), \" \" ).trim()\n    }\n\n    private fun buildCloudChunk(\n""",
    "exact spoken helper",
)

reader = replace_once(
    reader,
    """            val sentence = segments[i].spoken.replace(Regex(\"\\\\s+\"), \" \" ).trim()\n""",
    """            val sentence = spokenSegmentForPlayback(i, start)\n""",
    "cloud exact first segment",
)

reader = replace_once(
    reader,
    """                    isPlaying = false\n                    playPause.text = t(\"Продолжить\", \"Wznów\", \"Resume\")\n""",
    """                    isPlaying = false\n                    stopPlaybackBusyIndicators()\n                    playPause.text = t(\"Продолжить\", \"Wznów\", \"Resume\")\n""",
    "cloud synthesis failure UI",
)

reader = replace_once(
    reader,
    """                start()\n                scheduleChunkHighlights(chunk = chunk, durationMs = duration.toLong(), token = token)\n""",
    """                start()\n                showPlaybackPlayingUi()\n                scheduleChunkHighlights(chunk = chunk, durationMs = duration.toLong(), token = token)\n""",
    "cloud playback UI",
)

reader = replace_once(
    reader,
    """                        this@ReaderActivity.isPlaying = false\n                        currentSegment = 0\n                        playPause.text = t(\"Сначала\", \"Start over\")\n""",
    """                        this@ReaderActivity.isPlaying = false\n                        currentSegment = 0\n                        playbackStartOffsetOverride = null\n                        stopPlaybackBusyIndicators()\n                        playPause.text = t(\"Сначала\", \"Start over\")\n""",
    "cloud completion UI",
)

reader = replace_once(
    reader,
    """                    this@ReaderActivity.isPlaying = false\n                    playPause.text = \"Продолжить\"\n""",
    """                    this@ReaderActivity.isPlaying = false\n                    stopPlaybackBusyIndicators()\n                    playPause.text = \"Продолжить\"\n""",
    "media playback error UI",
)

reader = replace_once(
    reader,
    """    private fun estimatedOpenAiRunCost(): Double =\n        openAiRunAudioMillis / 60_000.0 * 0.013\n\n    private fun pauseSpeech() {\n""",
    """    private fun estimatedOpenAiRunCost(): Double =\n        openAiRunAudioMillis / 60_000.0 * 0.013\n\n    private fun showPlaybackPreparingUi() {\n        if (::playPause.isInitialized) PlaybackBusyIndicator.start(playPause)\n        if (editMode && ::editorListenButton.isInitialized) {\n            PlaybackBusyIndicator.start(editorListenButton)\n        }\n    }\n\n    private fun showPlaybackPlayingUi() {\n        if (::playPause.isInitialized) {\n            PlaybackBusyIndicator.stop(playPause, t(\"Пауза\", \"Pause\"))\n        }\n        if (::editorListenButton.isInitialized) {\n            PlaybackBusyIndicator.stop(editorListenButton, if (editMode) \"‖\" else \"▶\")\n        }\n    }\n\n    private fun stopPlaybackBusyIndicators() {\n        if (::playPause.isInitialized) PlaybackBusyIndicator.stop(playPause, null)\n        if (::editorListenButton.isInitialized) PlaybackBusyIndicator.stop(editorListenButton, \"▶\")\n    }\n\n    private fun pauseSpeech() {\n""",
    "playback indicator helpers",
)

reader = replace_once(
    reader,
    """        isPlaying = false\n        if (wasPlaying) syncPlaybackNotification()\n""",
    """        isPlaying = false\n        stopPlaybackBusyIndicators()\n        if (wasPlaying) syncPlaybackNotification()\n""",
    "pause stops indicator",
)

reader = replace_once(
    reader,
    """        val draft = editor.text.toString()\n        if (draft.isBlank()) {\n""",
    """        val draft = editor.text.toString()\n        val draftChanged = draft != text\n        if (draft.isBlank()) {\n""",
    "editor draft change",
)

reader = replace_once(
    reader,
    """        val cursor =\n            maxOf(editor.selectionStart, editor.selectionEnd)\n                .coerceIn(0, text.length)\n""",
    """        val selectionStart = editor.selectionStart.coerceIn(0, text.length)\n        val selectionEnd = editor.selectionEnd.coerceIn(0, text.length)\n        val cursor =\n            if (selectionStart != selectionEnd) {\n                minOf(selectionStart, selectionEnd)\n            } else {\n                selectionStart\n            }\n""",
    "editor selection start",
)

reader = replace_once(
    reader,
    """        currentSegment = findSegmentForOffset(playbackOffset)\n        openAiConfirmedHash = null\n        xaiConfirmedHash = null\n""",
    """        currentSegment = findSegmentForOffset(playbackOffset)\n        playbackStartOffsetOverride = playbackOffset\n        if (draftChanged) {\n            openAiConfirmedHash = null\n            xaiConfirmedHash = null\n        }\n""",
    "editor exact playback state",
)

reader = replace_once(
    reader,
    """        text = updated\n        segments = segmentText(text)\n        currentSegment = findSegmentForOffset(savedOffset)\n""",
    """        text = updated\n        segments = segmentText(text)\n        currentSegment = findSegmentForOffset(savedOffset)\n        playbackStartOffsetOverride = null\n""",
    "save resets exact start",
)

reader_path.write_text(reader, encoding="utf-8")

main_path = Path("app/src/main/java/com/kapijuja/reader/MainActivity.kt")
main = main_path.read_text(encoding="utf-8")
main = replace_once(
    main,
    """    private fun openDraft(title: String, source: String, text: String) {\n        val dir = File(cacheDir, \"drafts\").apply { mkdirs() }\n""",
    """    private fun openDraft(title: String, source: String, text: String) {\n        if (text.length > ReaderLimits.MAX_DOCUMENT_CHARS) {\n            AlertDialog.Builder(this)\n                .setTitle(t(\"Файл слишком большой\", \"Plik jest zbyt duży\", \"File too large\"))\n                .setMessage(\n                    t(\n                        \"В тексте ${text.length} символов. Для защиты памяти Android один документ ограничен ${ReaderLimits.MAX_DOCUMENT_CHARS} символами. Разделите файл на несколько частей.\",\n                        \"Tekst ma ${text.length} znaków. Dla ochrony pamięci Android jeden dokument jest ograniczony do ${ReaderLimits.MAX_DOCUMENT_CHARS} znaków. Podziel plik na części.\",\n                        \"The text contains ${text.length} characters. To protect Android memory, one document is limited to ${ReaderLimits.MAX_DOCUMENT_CHARS} characters. Split the file into parts.\"\n                    )\n                )\n                .setPositiveButton(\"OK\", null)\n                .show()\n            return\n        }\n        if (text.length > ReaderLimits.WARN_DOCUMENT_CHARS) {\n            Toast.makeText(\n                this,\n                t(\n                    \"Большой документ: быстрый ползунок справа поможет перемещаться по тексту.\",\n                    \"Duży dokument: szybki suwak po prawej ułatwi poruszanie się po tekście.\",\n                    \"Large document: use the fast handle on the right to move through the text.\"\n                ),\n                Toast.LENGTH_LONG\n            ).show()\n        }\n        val dir = File(cacheDir, \"drafts\").apply { mkdirs() }\n""",
    "main document guard",
)
main_path.write_text(main, encoding="utf-8")
print("Applied editor playback, fast-scroll and large-document patch")
