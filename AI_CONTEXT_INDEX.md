# Kapijuja Reader — AI context index

This is the first file an AI-assisted development session should read.

Use progressive context loading: understand the task first, then open only the subsystem files needed for that task. Do not read the entire repository and do not load the large legacy Activities end-to-end unless a structural refactor specifically requires it.

## Always read

1. `AI_CONTEXT_INDEX.md` — this file.
2. `AI_DEVELOPMENT_RULES.md` — architecture, file-size and safe-edit rules.
3. `NEXT_CHAT_HANDOFF_2026-09-17.txt` — current short handoff.
4. `docs/ai/HANDOFF_POLICY.md` when maintaining/rotating AI documentation.

`PROJECT_HANDOFF.md` is legacy historical material. Search it for an old topic; do not load it in full and do not use it as an append-only session log.

## Route by task

### TTS engine / provider work
Read `docs/ai/TTS_CONTEXT.md`, then only the provider client being changed plus `CloudTtsDispatcher.kt` and `SettingsStore.kt` when routing/configuration changes. Open the relevant region of `SettingsActivity.kt` only if UI/credentials/voice selection must change.

For xAI Grok stress/pronunciation/speech-tag editing, start with `GrokEditorMarkup.kt` and `GrokEditorToolbar.kt`; do not preload the whole Reader Activity.

### Reader playback / edit-mode listening / position / highlighting / export
Read `docs/ai/READER_CONTEXT.md`. Current playback UX also uses `PlaybackBusyIndicator.kt` and `ReaderFastScroller.kt`. Search `ReaderActivity.kt` for the exact responsibility before opening a range. Prefer existing bridge/service/client/dispatcher/editor files over adding more code to `ReaderActivity.kt`.

### Settings / provider configuration / library retention
Read `docs/ai/SETTINGS_CONTEXT.md`. Search `SettingsActivity.kt` and open only the relevant region. Provider connection checks are routed through `ProviderConnectionTester.kt`. Prefer extracting cohesive settings components instead of extending the monolith.

### Library / import / large-document safety
Start with `LibraryStore.kt`, `ReaderLimits.kt`, `DocumentTextExtractor.kt`, `HtmlExtractor.kt`, and the relevant `MainActivity.kt` region. Document bodies are separate `.txt` files; `index.json` is metadata only. SQLite is not currently required. Do not load playback/export code unless the change crosses that boundary.

### Silero
Start with `SileroRuntime.kt` and `docs/ai/TTS_CONTEXT.md`. Then inspect only the Reader/Settings call sites that invoke Silero.

## Large legacy files

Current migration targets:

- `ReaderActivity.kt` — historically about 106 KB. Provider/editor helpers have been extracted, but it remains a legacy coordinator. Extract cohesive playback/editor responsibilities rather than growing it indefinitely.
- `SettingsActivity.kt` — historically about 48 KB and should not grow for unrelated work.

Never rewrite either file wholesale for a local change. Search first, fetch/read only the necessary line range, and prefer a small extraction if the change would make the file grow.

## Context budget rule

A normal task should fit in this sequence:

1. index + architecture rules + current handoff;
2. one subsystem context file;
3. one or a few directly relevant source files or source ranges;
4. tests/build configuration only when needed.

Do not preload unrelated provider clients, both giant Activities, full history, CI, and all services into one context window.

## Documentation maintenance

Follow `docs/ai/HANDOFF_POLICY.md`. Keep the current handoff short; rotate completed detail into `docs/ai/history/` instead of appending forever. Keep subsystem context documents concise and split them before they become large. Raw build logs and diffs do not belong in handoff files.
