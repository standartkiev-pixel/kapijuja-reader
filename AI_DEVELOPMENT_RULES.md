# Kapijuja Reader — AI Development and Architecture Rules

These rules are part of the project, not optional style advice. They exist to keep the codebase reliable for both human and AI-assisted development as the project grows.

## Why this exists

Kapijuja Reader is already large enough that very big source files create a practical engineering risk: they consume model context, make local edits slower and less precise, increase the chance of accidental truncation during full-file rewrites, and make unrelated behavior easier to disturb.

The repository currently contains two legacy oversized files:

- `ReaderActivity.kt` — about 106 KB
- `SettingsActivity.kt` — about 48 KB

They must not continue growing. Refactoring them is now a planned engineering task.

## File-size policy

For handwritten Kotlin/Java production code:

- preferred size: roughly 200–500 lines per file;
- soft review threshold: 500 lines;
- normal hard limit: 800 lines or 60 KB, whichever comes first;
- generated code, large immutable data, and unavoidable protocol definitions may be exempt, but the exemption must be documented;
- existing oversized legacy files are allowed temporarily only as migration targets and should not grow further.

The objective is not to split code mechanically. A file should represent one coherent responsibility.

## Mandatory rule for AI-assisted edits

Do not solve a small change by rewriting an entire large source file.

When a file is already large:

1. search for the exact responsibility involved;
2. read only the relevant region and its direct dependencies;
3. prefer a surgical edit;
4. if the requested change would make the large file grow, extract a cohesive component first or as part of the same change;
5. compile and run tests after each extraction step.

This rule specifically exists to prevent context overload and accidental source truncation.

## Refactoring policy

Refactors must be behavior-preserving and incremental. Do not combine a large structural move with an unrelated feature change.

Before extracting logic from a large class, add characterization tests for the behavior being moved whenever practical. If the behavior is tightly coupled to Android UI, first extract the pure logic behind it and test that logic.

Do not rewrite `ReaderActivity.kt` or `SettingsActivity.kt` from scratch.

## Planned ReaderActivity split

`ReaderActivity.kt` should become a thin Android UI/lifecycle shell. Logic should gradually move into cohesive modules such as:

- text segmentation and reading position;
- playback state and orchestration;
- Android TTS adapter;
- cloud/local chunk playback coordinator;
- audio export coordinator;
- progress/cancellation state;
- text highlighting/autoscroll helpers.

Exact class names may change. Responsibility boundaries matter more than naming.

## Planned SettingsActivity split

`SettingsActivity.kt` should gradually delegate to smaller components for:

- engine selection;
- voice selection and voice discovery;
- provider credentials/status checks;
- language and library-retention settings;
- background/battery settings;
- diagnostics and service information.

Again, keep the Activity focused on Android lifecycle and screen wiring.

## Package structure

The current flat package is tolerated as legacy structure, but new substantial code should move toward feature/layer packages, for example:

- `ui.reader`
- `ui.settings`
- `playback`
- `tts.android`
- `tts.cloud`
- `tts.silero`
- `export`
- `library`
- `diagnostics`

Do not reorganize everything in one commit. Move packages incrementally when a module is already being touched and tests/builds can verify the change.

## Tests are part of architecture

Kapijuja Reader currently has much less automated test coverage than the keyboard project. Therefore a large split must not begin as a blind file-moving exercise.

Priority characterization/unit tests should cover pure logic such as:

- text segmentation and chunk boundaries;
- WAV header/stream joining behavior;
- export cancellation state;
- library retention and pruning;
- language/default-setting selection;
- engine/voice default selection;
- any extracted playback state machine logic.

Every new pure-logic module should normally arrive with tests.

## CI architecture sensor

The repository should keep an automated source-size check in CI. The check is intentionally simple: it cannot judge architecture, but it can detect the positive-feedback failure mode where already-large files silently grow forever.

The current legacy oversized files are temporary baselines. They may shrink; they should not grow beyond their recorded baseline. New handwritten source files must respect the normal hard limit.

After a legacy file is refactored below the normal limit, remove its exception rather than increasing the baseline.

## Context discipline for future ChatGPT sessions

Use progressive context loading.

1. Read `AI_CONTEXT_INDEX.md` first.
2. Read this file.
3. Read the short current handoff (`NEXT_CHAT_HANDOFF_2026-09-12.txt`, or its future replacement).
4. Open only the subsystem context document named by `AI_CONTEXT_INDEX.md`.
5. Search before opening large source files and read only the relevant ranges/direct dependencies.

`PROJECT_HANDOFF.md` is a long historical/implementation record. Search it for the specific topic when needed; do not load it end-to-end by default.

Do not preload both giant Activities, every provider client, the full project history, CI files, and unrelated services into one model context window.

When architecture or responsibilities change, update the relevant `docs/ai/*_CONTEXT.md` routing note and the handoff.

## Current priority order

1. Keep the currently working Reader behavior stable.
2. Keep the base APK lean; do not add large local runtimes or models to the default build.
3. Add characterization tests around logic that will be extracted.
4. Reduce `ReaderActivity.kt` in several safe passes.
5. Reduce `SettingsActivity.kt` in several safe passes.
6. Remove legacy size exceptions once those files are below the normal limit.

The target is not "more files". The target is smaller, cohesive, independently understandable modules that reduce regression risk and AI context pressure.
