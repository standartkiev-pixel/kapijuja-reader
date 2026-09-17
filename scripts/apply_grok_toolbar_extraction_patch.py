#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/kapijuja/reader/ReaderActivity.kt")
text = PATH.read_text(encoding="utf-8")
MARKER = "GrokEditorToolbar.create("
if MARKER in text:
    print("Grok toolbar extraction already applied")
    raise SystemExit(0)

button_start = text.find("        grokEditToolsButton = Button(this).apply {")
button_end = text.find("        exportProgress = ProgressBar(", button_start)
if button_start < 0 or button_end < 0:
    raise SystemExit("Could not locate Grok toolbar button block")

button_replacement = """        grokEditToolsButton =\n            GrokEditorToolbar.create(\n                activity = this,\n                editor = editor,\n                setResultText = { value -> resultText.text = value },\n                pauseIfPlaying = {\n                    if (isPlaying) pauseSpeech()\n                },\n                scrollToOffset = { offset ->\n                    scrollEditorToOffset(offset)\n                }\n            )\n        player.addView(\n            grokEditToolsButton,\n            LinearLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT,\n                dp(50)\n            ).apply { topMargin = dp(7) }\n        )\n\n"""
text = text[:button_start] + button_replacement + text[button_end:]

methods_start = text.find("    private fun updateEditorToolVisibility() {")
methods_end = text.find("    private fun highlight(index: Int) {", methods_start)
if methods_start < 0 or methods_end < 0:
    raise SystemExit("Could not locate Grok toolbar methods block")

methods_replacement = """    private fun updateEditorToolVisibility() {\n        if (!::grokEditToolsButton.isInitialized) return\n        GrokEditorToolbar.updateVisibility(\n            button = grokEditToolsButton,\n            editMode = editMode,\n            engine = SettingsStore.engine(this)\n        )\n    }\n\n"""
text = text[:methods_start] + methods_replacement + text[methods_end:]

PATH.write_text(text, encoding="utf-8")
print("Extracted Grok editor toolbar UI from ReaderActivity")
