# Kapijuja Reader — reader/playback/export context

Use this routing note for tasks involving reading position, highlighting, playback, notifications, audio generation, or export.

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

## Responsibilities still inside ReaderActivity

The oversized Activity still mixes several concerns, including:

- screen construction and lifecycle;
- tap-to-start and text highlighting/autoscroll;
- sentence/text segmentation;
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
4. export state/progress logic;
5. Android TTS adapter;
6. cloud playback coordinator;
7. highlighting/autoscroll helpers.

After each extraction, build/test before moving the next responsibility.

## Do not

- do not rewrite `ReaderActivity.kt` in full;
- do not combine a structural extraction with unrelated UI changes;
- do not move HTTP code into the Activity;
- do not create a second competing playback/export state machine without removing the old responsibility;
- do not load `SettingsActivity.kt` unless the requested change actually touches settings.
