# AI handoff and context-size policy

Kapijuja Reader uses progressive context loading. Handoff documents are routing aids, not an append-only transcript.

## Mandatory startup context

An AI development session should normally read only:

1. `AI_CONTEXT_INDEX.md`;
2. `AI_DEVELOPMENT_RULES.md`;
3. the current `NEXT_CHAT_HANDOFF_YYYY-MM-DD.txt`;
4. one relevant subsystem context document under `docs/ai/`.

Open source files by search/range. Do not preload the entire repository, `PROJECT_HANDOFF.md`, or both legacy Activities.

## Size ceilings

- Current `NEXT_CHAT_HANDOFF_*.txt`: target <= 15 KB, hard maintenance threshold 20 KB.
- A subsystem context file: target <= 20 KB, split before 25 KB.
- A single historical handoff note: target <= 20 KB.
- Do not paste build logs, full diffs, APK data, or large source listings into handoff documents.

These are documentation-maintenance limits, not runtime application limits.

## Rotation rule

When the current handoff approaches 20 KB:

1. Keep only current architecture, current user-visible behavior, active bugs, validation status, and next steps in the current handoff.
2. Move completed implementation history into a topic file under `docs/ai/history/`, for example `HANDOFF_2026-09-17_EDITOR.md`.
3. Link the history file from the subsystem context only when it is genuinely useful.
4. Never create a second mandatory startup history file. History remains search-on-demand.

`PROJECT_HANDOFF.md` is legacy historical material. Search it for old decisions; never append routine session notes to it and never load it in full by default.

## Source-code context rule

Large source files are migration targets rather than context dumps. Search for the exact method/responsibility and fetch only the needed range. If a local change adds a cohesive new responsibility, prefer a helper/component file over extending `ReaderActivity.kt` or `SettingsActivity.kt`.

## What belongs in a handoff

Keep: current file ownership, non-obvious product decisions, active provider constraints, active bugs, test/build state, important limits, and the next safe step.

Do not keep: conversational narration, repeated status messages, obsolete experiments, raw logs, temporary patch scripts, or details that are already obvious from a small source file.
