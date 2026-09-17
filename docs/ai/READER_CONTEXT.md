# Kapijuja Reader — reader/playback/export context

Use this routing note for tasks involving reading position, highlighting, playback, edit-mode listening, notifications, audio generation, or export.

## Main rule

`ReaderActivity.kt` is a legacy monolith and a migration target, not the place to keep accumulating logic. Search it for the exact method/provider/state involved and open only that range plus direct dependencies.

## Existing neighboring modules

- `PlaybackBridge.kt` — notification/controller bridge.
- `ReaderPlaybackService.kt` — foreground playback/service behavior.
- `ExportBridge.kt` — export cancellation/control bridge.
- `ReaderExportService.kt` — foreground long-running export support.
- `WavTools.kt` — WAV/PCM utilities.
- provider clients — cloud TTS protocol and provider-specific splitting.
- `SileroRuntime.kt` — local Silero inference.
- `GrokEditorMarkup.kt` — pure stress-mark and Grok speech-tag transformations plus balanced tag/chunk helpers.
- `GrokEditorToolbar.kt` — compact Grok-only editor dropdown/UI; keep Grok-specific editor UI out of `ReaderActivity.kt`.

## Edit-mode playback

Edit mode is also a listening/proofreading mode. Important behavior:

- entering edit mode keeps the current playback/text position instead of jumping to the end of a long document;
- the play button remains enabled and reads the current unsaved editor snapshot from the cursor position;
- the user can continue editing while audio is playing, then pause/restart to audition the new text;
- saving preserves the editor position instead of resetting the Reader to sentence 1;
- the normal `Save text` / `Save MP3/WAV` row is hidden while editing and restored after leaving edit mode;
- when xAI Grok is selected, a compact Grok editor toolbar is visible; it is hidden for all other engines.

For Grok editor behavior, start with `GrokEditorMarkup.kt` and `GrokEditorToolbar.kt`; only then inspect the small edit-mode call sites in `ReaderActivity.kt`.

## Grok pronunciation markup

The first Grok editor action is stress placement using Unicode combining acute U+0301. The caret is placed immediately after the target vowel (or one vowel is selected). Existing stress marks in the same word are removed before the new one is inserted. Stress in other words is preserved.

Grok wrapping tags are inserted around the selected text; inline events are inserted at the cursor. Playback/chunk planning must not split an open Grok wrapping tag. If playback starts inside a tagged span, the start is moved back far enough to include the opening tag.

Do not add a pronunciation dictionary UI unless explicitly requested. Current product direction is explicit per-occurrence stress/markup because identical spelling can require different stress depending on context.

## Responsibilities still inside ReaderActivity

The oversized Activity still mixes several concerns, including:

- screen construction and lifecycle;
- tap-to-start and text highlighting/autoscroll;
- sentence/text segmentation;
- editor session/playback snapshot coordination;
- Android TTS lifecycle and utterance callbacks;
- cloud playback sequencing/prefetch;
- local Silero playback dispatch;
- playback state;
- export destination and progress UI;
- Android/cloud export orchestration;
- cancellation state.

These should be extracted gradually, with behavior preserved.

## Safe extraction order

Prefer pure or nearly pure responsibilities first:

1. text segmentation / segment model;
2. filename and other pure formatting helpers;
3. chunk planning / playback state helpers;
4. editor session coordinator;
5. export state/progress logic;
6. Android TTS adapter;
7. cloud playback coordinator;
8. highlighting/autoscroll helpers.

After each extraction, build/test before moving the next responsibility.

## Do not

- do not rewrite `ReaderActivity.kt` in full;
- do not put Grok stress/tag transformation code back into `ReaderActivity.kt`;
- do not combine a structural extraction with unrelated UI changes;
- do not move HTTP code into the Activity;
- do not create a second competing playback/export state machine without removing the old responsibility;
- do not load `SettingsActivity.kt` unless the requested change actually touches settings.
