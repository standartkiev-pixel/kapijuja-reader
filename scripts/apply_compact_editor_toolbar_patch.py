#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/kapijuja/reader/ReaderActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    """    private lateinit var saveAudioButton: Button\n    private lateinit var saveRow: LinearLayout\n    private lateinit var grokEditToolsButton: Button\n    private lateinit var exportProgress: ProgressBar\n""",
    """    private lateinit var saveAudioButton: Button\n    private lateinit var saveRow: LinearLayout\n    private lateinit var editorToolbar: LinearLayout\n    private lateinit var editorListenButton: Button\n    private lateinit var grokStressButton: Button\n    private lateinit var grokEditToolsButton: Button\n    private var sourceView: TextView? = null\n    private lateinit var exportProgress: ProgressBar\n""",
    "fields",
)

replace_once(
    """        val root = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n            setBackgroundResource(R.drawable.kapijuja_screen_bg)\n            setPadding(dp(14), dp(8), dp(14), dp(10))\n        }\n""",
    """        val root = LinearLayout(this).apply {\n            orientation = LinearLayout.VERTICAL\n            setBackgroundResource(R.drawable.kapijuja_screen_bg)\n            setPadding(dp(10), dp(6), dp(10), dp(6))\n        }\n""",
    "root padding",
)

replace_once(
    """        val back = Button(this).apply {\n            text = \"‹\"\n            textSize = 30f\n            setOnClickListener { finish() }\n        }\n        KapijujaUiTheme.button(this, back)\n        top.addView(back, LinearLayout.LayoutParams(dp(58), dp(54)))\n\n        val heading = TextView(this).apply {\n            text = title\n            textSize = 20f\n            maxLines = 2\n            setTypeface(typeface, android.graphics.Typeface.BOLD)\n            setPadding(dp(12), 0, dp(8), 0)\n        }\n""",
    """        val back = Button(this).apply {\n            text = \"‹\"\n            textSize = 24f\n            contentDescription = t(\"Назад\", \"Wstecz\", \"Back\")\n            minWidth = 0\n            minimumWidth = 0\n            setPadding(0, 0, 0, 0)\n            setOnClickListener { finish() }\n        }\n        KapijujaUiTheme.button(this, back)\n        top.addView(back, LinearLayout.LayoutParams(dp(46), dp(44)))\n\n        val heading = TextView(this).apply {\n            text = title\n            textSize = 18f\n            maxLines = 1\n            ellipsize = android.text.TextUtils.TruncateAt.END\n            setTypeface(typeface, android.graphics.Typeface.BOLD)\n            setPadding(dp(9), 0, dp(6), 0)\n        }\n""",
    "compact header left",
)

replace_once(
    """        val settings = Button(this).apply {\n            text = t(\"⚙ Настройки\", \"⚙ Settings\")\n            textSize = 13f\n            setOnClickListener {\n                pauseSpeech()\n                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))\n            }\n        }\n        KapijujaUiTheme.button(this, settings)\n        top.addView(settings, LinearLayout.LayoutParams(dp(120), dp(52)))\n""",
    """        val settings = Button(this).apply {\n            text = \"⚙\"\n            textSize = 20f\n            contentDescription = t(\"Настройки\", \"Ustawienia\", \"Settings\")\n            minWidth = 0\n            minimumWidth = 0\n            setPadding(0, 0, 0, 0)\n            setOnClickListener {\n                pauseSpeech()\n                startActivity(Intent(this@ReaderActivity, SettingsActivity::class.java))\n            }\n        }\n        KapijujaUiTheme.button(this, settings)\n        top.addView(settings, LinearLayout.LayoutParams(dp(46), dp(44)))\n""",
    "compact settings",
)

replace_once(
    """        if (source.isNotBlank()) {\n            val sourceView = TextView(this).apply {\n                text = source\n                textSize = 12f\n                maxLines = 1\n                setPadding(dp(6), dp(5), dp(6), dp(5))\n            }\n            KapijujaUiTheme.secondary(sourceView)\n            root.addView(sourceView)\n        }\n""",
    """        if (source.isNotBlank()) {\n            val sourceLabel = TextView(this).apply {\n                text = source\n                textSize = 12f\n                maxLines = 1\n                setPadding(dp(4), dp(2), dp(4), dp(2))\n            }\n            KapijujaUiTheme.secondary(sourceLabel)\n            sourceView = sourceLabel\n            root.addView(sourceLabel)\n        }\n""",
    "source label",
)

replace_once(
    """        editor = EditText(this).apply {\n            visibility = View.GONE\n            textSize = 20f\n            gravity = Gravity.TOP\n            minLines = 14\n            setLineSpacing(dp(5).toFloat(), 1.08f)\n        }\n        KapijujaUiTheme.input(this, editor)\n\n""",
    """        editor = EditText(this).apply {\n            visibility = View.GONE\n            textSize = 20f\n            gravity = Gravity.TOP\n            minLines = 6\n            setLineSpacing(dp(5).toFloat(), 1.08f)\n        }\n        KapijujaUiTheme.input(this, editor)\n\n        editorToolbar = LinearLayout(this).apply {\n            orientation = LinearLayout.HORIZONTAL\n            gravity = Gravity.CENTER_VERTICAL\n            visibility = View.GONE\n            setPadding(dp(4), dp(3), dp(4), dp(3))\n            background = KapijujaUiTheme.panel(\n                this@ReaderActivity,\n                KapijujaUiTheme.PANEL_DARK,\n                KapijujaUiTheme.BLUE,\n                1,\n                12\n            )\n        }\n\n        editorListenButton = Button(this).apply {\n            text = \"▶ ‖\"\n            textSize = 15f\n            contentDescription =\n                t(\"Слушать или пауза\", \"Czytaj lub pauza\", \"Listen or pause\")\n            minWidth = 0\n            minimumWidth = 0\n            setPadding(0, 0, 0, 0)\n            isFocusable = false\n            isFocusableInTouchMode = false\n            setOnClickListener {\n                if (isPlaying) pauseSpeech() else startOrResume()\n            }\n        }\n        KapijujaUiTheme.button(this, editorListenButton, primary = true)\n        editorToolbar.addView(\n            editorListenButton,\n            LinearLayout.LayoutParams(dp(58), dp(38)).apply { marginEnd = dp(5) }\n        )\n\n        grokStressButton =\n            GrokEditorToolbar.createStressButton(\n                activity = this,\n                editor = editor,\n                setResultText = { value -> resultText.text = value },\n                pauseIfPlaying = { if (isPlaying) pauseSpeech() },\n                scrollToOffset = { offset -> scrollEditorToOffset(offset) }\n            )\n        editorToolbar.addView(\n            grokStressButton,\n            LinearLayout.LayoutParams(dp(46), dp(38)).apply { marginEnd = dp(5) }\n        )\n\n        grokEditToolsButton =\n            GrokEditorToolbar.create(\n                activity = this,\n                editor = editor,\n                setResultText = { value -> resultText.text = value },\n                pauseIfPlaying = { if (isPlaying) pauseSpeech() },\n                scrollToOffset = { offset -> scrollEditorToOffset(offset) }\n            )\n        editorToolbar.addView(\n            grokEditToolsButton,\n            LinearLayout.LayoutParams(dp(82), dp(38)).apply { marginEnd = dp(5) }\n        )\n\n        val editorSaveButton = Button(this).apply {\n            text = \"✓\"\n            textSize = 20f\n            contentDescription = t(\"Сохранить\", \"Zapisz\", \"Save\")\n            minWidth = 0\n            minimumWidth = 0\n            setPadding(0, 0, 0, 0)\n            isFocusable = false\n            isFocusableInTouchMode = false\n            setOnClickListener { saveEditedText() }\n        }\n        KapijujaUiTheme.button(this, editorSaveButton)\n        editorToolbar.addView(\n            editorSaveButton,\n            LinearLayout.LayoutParams(dp(48), dp(38))\n        )\n        root.addView(\n            editorToolbar,\n            LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                dp(44)\n            ).apply { topMargin = dp(4); bottomMargin = dp(4) }\n        )\n\n""",
    "editor and compact toolbar",
)

# The compact editor toolbar owns the Grok controls now; remove the old large
# Grok button from the bottom player panel.
replace_once(
    """        grokEditToolsButton =\n            GrokEditorToolbar.create(\n                activity = this,\n                editor = editor,\n                setResultText = { value -> resultText.text = value },\n                pauseIfPlaying = {\n                    if (isPlaying) pauseSpeech()\n                },\n                scrollToOffset = { offset ->\n                    scrollEditorToOffset(offset)\n                }\n            )\n        player.addView(\n            grokEditToolsButton,\n            LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                dp(50)\n            ).apply { topMargin = dp(7) }\n        )\n\n""",
    "",
    "remove bottom Grok button",
)

replace_once(
    """        textView.visibility = View.GONE\n        editor.visibility = View.VISIBLE\n        player.visibility = View.VISIBLE\n        editButton.text = t(\"Сохранить\", \"Save\")\n""",
    """        textView.visibility = View.GONE\n        editor.visibility = View.VISIBLE\n        sourceView?.visibility = View.GONE\n        editorToolbar.visibility = View.VISIBLE\n        player.visibility = View.GONE\n        editButton.text = t(\"Сохранить\", \"Save\")\n""",
    "enter edit compact mode",
)

replace_once(
    """        saveRow.visibility = View.VISIBLE\n        player.visibility = View.VISIBLE\n        updateEditorToolVisibility()\n        resultText.text = t(\"Текст сохранён.\", \"Text saved.\")\n""",
    """        saveRow.visibility = View.VISIBLE\n        editorToolbar.visibility = View.GONE\n        sourceView?.visibility = if (source.isNotBlank()) View.VISIBLE else View.GONE\n        player.visibility = View.VISIBLE\n        hideEditorKeyboard()\n        updateEditorToolVisibility()\n        resultText.text = t(\"Текст сохранён.\", \"Text saved.\")\n""",
    "leave edit compact mode",
)

replace_once(
    """    private fun updateEditorToolVisibility() {\n        if (!::grokEditToolsButton.isInitialized) return\n        GrokEditorToolbar.updateVisibility(\n            button = grokEditToolsButton,\n            editMode = editMode,\n            engine = SettingsStore.engine(this)\n        )\n    }\n\n""",
    """    private fun updateEditorToolVisibility() {\n        if (!::grokEditToolsButton.isInitialized || !::grokStressButton.isInitialized) return\n        GrokEditorToolbar.updateVisibility(\n            stressButton = grokStressButton,\n            menuButton = grokEditToolsButton,\n            editMode = editMode,\n            engine = SettingsStore.engine(this)\n        )\n    }\n\n    private fun hideEditorKeyboard() {\n        val input =\n            getSystemService(INPUT_METHOD_SERVICE) as?\n                android.view.inputmethod.InputMethodManager\n        input?.hideSoftInputFromWindow(editor.windowToken, 0)\n        editor.clearFocus()\n    }\n\n""",
    "editor visibility and keyboard",
)

path.write_text(text, encoding="utf-8")
print("Applied compact editor toolbar patch")
