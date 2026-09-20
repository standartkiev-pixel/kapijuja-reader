# Kapijuja Reader — reader/playback/export context

Use this routing note for tasks involving reading position, highlighting, playback, edit-mode listening, fast navigation, notifications, audio generation, or export.

## Main rule

`ReaderActivity.kt` is a legacy monolith and a migration target, not the place to keep accumulating logic. Search it for the exact method/provider/state involved and open only that range plus direct dependencies.

## Existing neighboring modules

- `PlaybackBridge.kt` — notification/controller bridge.
- `ReaderPlaybackService.kt` — foreground playback/service behavior.
- `PlaybackBusyIndicator.kt` — compact rotating-triangle waiting state while TTS is preparing.
- `ReaderFastScroller.kt` — draggable right-side fast-scroll handle for long documents.
- `ExportBridge.kt` / `ReaderExportService.kt` — export control and foreground service.
- `WavTools.kt` — WAV/PCM utilities.
- provider clients — cloud TTS protocol and provider-specific splitting.
- `GrokEditorMarkup.kt` — pure stress-mark and Grok speech-tag transformations plus balanced tag/chunk helpers.
- `GrokEditorToolbar.kt` — compact Grok editor controls/menu; keep Grok-specific editor UI out of `ReaderActivity.kt`.
- `ReaderLimits.kt` — document/source/library safety ceilings.

## Edit-mode search

Edit mode has a compact search control at the top of the screen, never at the bottom.

- the magnifier button lives in the compact editor toolbar;
- opening it reveals a small search row directly under that toolbar and above the document;
- search uses the current unsaved editor text;
- matching is case-insensitive;
- previous/next wrap around the document so repeated corrections can be visited in sequence;
- a found match is selected in the editor and the document scrolls to it;
- closing search returns focus to the editor without moving controls below the keyboard;
- search logic/UI lives in `ReaderEditorSearchController.kt`, not in the legacy ReaderActivity.

## Edit-mode playback

Edit mode is also a listening/proofreading mode.

- entering edit mode keeps the current playback/text region instead of jumping to document end;
- the current unsaved editor snapshot is used for auditioning;
- playback starts from the exact caret position, not merely the beginning of its sentence;
- if a selection exists, playback starts at the beginning of the selection; selection-only playback is not required;
- xAI Grok may move the effective start backward to an opening wrapping tag so `<slow>`, `<emphasis>`, etc. remain valid;
- only the first segment is sliced at the exact start; later segments are spoken in full;
- the same exact-first-segment rule applies to Android TTS and cloud TTS;
- editor stays visible while audio is preparing/playing;
- user may edit while audio plays, then pause/restart to audition the changed draft;
- saving preserves position instead of resetting to sentence 1.

The compact editor toolbar is above the scroll area. The normal bottom player is hidden in edit mode. `adjustResize` keeps the toolbar accessible with the keyboard open.

## Waiting/progress UI

For cloud generation/Android engine initialization, use `PlaybackBusyIndicator`: animate the Play control while waiting, then show Pause once audio actually starts. Do not invent fake network percentages when a provider does not report transfer/generation progress.

Audio export is different: export owns real item/chunk progress and may show a percentage.

## Long-document navigation

The built-in Android scrollbar is only a visual indicator. `ReaderFastScroller` overlays a real draggable thumb on the right side of the Reader scroll area. It is intended for jumping through book-sized texts and hides itself on short documents.

## Grok pronunciation markup

Stress placement uses Unicode combining acute U+0301. Put the caret immediately after the target vowel (or select one vowel). Existing stress in the same word is removed before the new mark is inserted; other words are preserved.

Wrapping tags surround selected text; inline events are inserted at the cursor. Playback/export chunking must not split open Grok wrapping tags. If playback starts inside a tagged span, include the necessary opening tag.

Do not add a pronunciation dictionary UI unless explicitly requested. Current product direction is explicit per-occurrence correction because identical spelling can need different stress by context.

## Responsibilities still inside ReaderActivity

The Activity still coordinates screen/lifecycle, tap-to-start, segmentation, editor playback snapshot, Android TTS lifecycle, cloud sequencing, playback state, highlighting and export UI. Extract these gradually rather than growing the Activity.

Preferred future extraction order:
1. text segmentation / segment model;
2. editor session + exact-start state;
3. cloud playback coordinator;
4. Android TTS adapter;
5. highlighting/autoscroll;
6. export state/progress.

## Do not

- do not rewrite `ReaderActivity.kt` in full;
- do not put Grok transformation/menu code back into it;
- do not put fast-scroll drawing/touch logic back into it;
- do not move HTTP code into the Activity;
- do not create a second competing playback state machine;
- do not load `SettingsActivity.kt` unless the task really touches settings.
