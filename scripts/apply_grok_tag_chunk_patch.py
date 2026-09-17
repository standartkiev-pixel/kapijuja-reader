#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/kapijuja/reader/ReaderActivity.kt")
text = PATH.read_text(encoding="utf-8")
MARKER = "GrokEditorMarkup.playbackStartOffset(draft, cursor)"
if MARKER in text:
    print("Grok tag-aware Reader patch already applied")
    raise SystemExit(0)


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    """        val index = segments.indexOfFirst { offset < it.end }\n            .let { if (it >= 0) it else segments.lastIndex }\n""",
    """        val playbackOffset =\n            if (SettingsStore.engine(this) == SettingsStore.ENGINE_XAI) {\n                GrokEditorMarkup.playbackStartOffset(text, offset)\n            } else {\n                offset\n            }\n        val index = segments.indexOfFirst { playbackOffset < it.end }\n            .let { if (it >= 0) it else segments.lastIndex }\n""",
    "tap start inside Grok tag",
)

replace_once(
    """    private fun buildCloudChunk(startIndex: Int, maxChars: Int = 720): CloudChunk {\n""",
    """    private fun buildCloudChunk(\n        startIndex: Int,\n        engine: String,\n        maxChars: Int = 720\n    ): CloudChunk {\n""",
    "buildCloudChunk signature",
)

replace_once(
    """            val extra = if (builder.isEmpty()) sentence.length else sentence.length + 1\n            if (builder.isNotEmpty() && builder.length + extra > maxChars) break\n\n            if (builder.isNotEmpty()) builder.append(' ')\n            builder.append(sentence)\n            end = i\n\n            if (builder.length >= maxChars * 3 / 4) break\n""",
    """            val extra = if (builder.isEmpty()) sentence.length else sentence.length + 1\n            val grokTagOpen =\n                engine == SettingsStore.ENGINE_XAI &&\n                    GrokEditorMarkup.hasUnclosedWrappingTag(builder.toString())\n            if (\n                builder.isNotEmpty() &&\n                builder.length + extra > maxChars &&\n                !grokTagOpen\n            ) {\n                break\n            }\n\n            if (builder.isNotEmpty()) builder.append(' ')\n            builder.append(sentence)\n            end = i\n\n            if (engine == SettingsStore.ENGINE_XAI) {\n                require(builder.length <= XaiTtsClient.MAX_REQUEST_CHARS) {\n                    \"Grok-тег охватывает слишком длинный фрагмент. Разбейте его на несколько частей.\"\n                }\n            }\n\n            val canBreakHere =\n                engine != SettingsStore.ENGINE_XAI ||\n                    !GrokEditorMarkup.hasUnclosedWrappingTag(builder.toString())\n            if (builder.length >= maxChars * 3 / 4 && canBreakHere) break\n""",
    "Grok balanced cloud chunk",
)

# There are exactly two planner call sites: active playback and speculative prefetch.
old = """        val chunk = buildCloudChunk(startIndex)\n"""
count = text.count(old)
if count != 2:
    raise SystemExit(f"buildCloudChunk call sites: expected 2, found {count}")
text = text.replace(
    old,
    """        val chunk = buildCloudChunk(startIndex, engine)\n""",
)

replace_once(
    """        val cursor =\n            maxOf(editor.selectionStart, editor.selectionEnd)\n                .coerceIn(0, text.length)\n        currentSegment = findSegmentForOffset(cursor)\n""",
    """        val cursor =\n            maxOf(editor.selectionStart, editor.selectionEnd)\n                .coerceIn(0, text.length)\n        val playbackOffset =\n            if (SettingsStore.engine(this) == SettingsStore.ENGINE_XAI) {\n                GrokEditorMarkup.playbackStartOffset(draft, cursor)\n            } else {\n                cursor\n            }\n        currentSegment = findSegmentForOffset(playbackOffset)\n""",
    "editor start inside Grok tag",
)

PATH.write_text(text, encoding="utf-8")
print("Applied Grok tag-aware playback/chunk patch")
