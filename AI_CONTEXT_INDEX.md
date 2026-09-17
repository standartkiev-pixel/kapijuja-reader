# Kapijuja Reader — AI context index

This is the first file an AI-assisted development session should read.

The purpose is progressive context loading: understand the task first, then open only the subsystem files needed for that task. Do not read the entire repository and do not load the large legacy Activities end-to-end unless a structural refactor specifically requires it.

## Always read

1. `AI_CONTEXT_INDEX.md` — this file.
2. `AI_DEVELOPMENT_RULES.md` — architecture, file-size and safe-edit rules.
3. `NEXT_CHAT_HANDOFF_2026-09-17.txt` — current short handoff.

`PROJECT_HANDOFF.md` is a long historical/implementation record. Search it for the topic you need; do not load it in full by default.

## Route by task

### TTS engine / provider work
Read `docs/ai/TTS_CONTEXT.md`, then only the provider client being changed plus `CloudTtsDispatcher.kt` and `SettingsStore.kt` when routing/configuration changes. Open the relevant region of `SettingsActivity.kt` only if UI/credentials/voice selection must change.

For xAI Grok stress/pronunciation/speech-tag editing, start with `GrokEditorMarkup.kt` and `GrokEditorToolbar.kt`; do not preload the whole Reader Activity.

### Reader playback / edit-mode listening / position / highlighting / export
Read `docs/ai/READER_CONTEXT.md`. Search `ReaderActivity.kt` for the exact responsibility before opening a range. Prefer existing bridge/service/client/dispatcher/editor files over adding more code to `ReaderActivity.kt`.

### Settings / provider configuration / library retention
Read `docs/ai/SETTINGS_CONTEXT.md`. Search `SettingsActivity.kt` and open only the relevant region. Provider connection checks are routed through `ProviderConnectionTester.kt`. Prefer extracting cohesive settings components instead of extending the monolith.

### Library / import
Start with `LibraryStore.kt`, `DocumentTextExtractor.kt`, `HtmlExtractor.kt`, and the relevant `MainActivity.kt` region. Do not load playback/export code unless the change crosses that boundary.

### Silero
Start with `SileroRuntime.kt` and `docs/ai/TTS_CONTEXT.md`. Then inspect only the Reader/Settings call sites that invoke Silero.

## Large legacy files

Current migration targets:

- `ReaderActivity.kt` — historically about 106 KB; after cloud dispatch extraction and the Grok editor work it is about 94 KB. Grok-specific text/UI logic is already split into dedicated files; extract editor session coordination next if this area grows again.
- `SettingsActivity.kt` — historically about 48 KB; after provider-test extraction it is about 47 KB.

Never rewrite either file wholesale for a local change. Search first, fetch/read only the necessary line range, and prefer a small extraction if the change would make the file grow.

## Context budget rule

A normal task should fit in this sequence:

1. index + architecture rules + current handoff;
2. one subsystem context file;
3. one or a few directly relevant source files or source ranges;
4. tests/build configuration only when needed.

Do not preload unrelated provider clients, both giant Activities, full history, CI, and all services into one context window.

## Documentation maintenance

When a responsibility moves to a new file, update the relevant subsystem context document and this index if routing changes. Keep subsystem context files short and factual. Historical detail belongs in `PROJECT_HANDOFF.md`, not in the mandatory startup context.
