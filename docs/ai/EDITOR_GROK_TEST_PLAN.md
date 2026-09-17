# Grok editor — device smoke test

Use this after installing a build that contains the live editor playback feature.

1. Select xAI Grok TTS and open a long Russian text.
2. Start playback somewhere in the middle, pause, enter Edit. The editor should open near that reading position, not at the document end.
3. Put the caret immediately after a vowel and choose the first Grok action (stress). The same word must contain only one combining acute after the operation.
4. Tap Listen from cursor. Playback should use the current unsaved editor text without leaving edit mode.
5. While audio is playing, edit another word. Pause/restart and verify the changed draft is used.
6. Select a phrase and apply Slow / Emphasis / another wrapping action. Verify opening and closing tags surround the selection.
7. Put the cursor inside that tagged phrase and start playback. The request should include the opening tag (Reader moves its effective playback start back to the tag boundary).
8. Save the edit. The Reader should stay near the edited position rather than reset to the beginning.
9. In edit mode the Save text / Save audio row should be hidden. Outside edit mode it should be visible again.
10. Switch away from xAI Grok. The Grok stress/delivery button must disappear.
