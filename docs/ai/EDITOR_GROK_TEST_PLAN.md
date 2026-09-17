# Grok editor — device smoke test

Use this after installing a build that contains the live editor playback feature.

1. Select xAI Grok TTS and open a long Russian text.
2. Start playback somewhere in the middle, pause, enter Edit. The editor should open near that reading position, not at the document end.
3. Confirm edit mode uses the compact top toolbar and hides the large bottom player. The toolbar should show Listen/Pause, a dedicated stress button, Grok menu, and Save.
4. Tap inside the text or select a word so the Android keyboard opens. The compact toolbar must remain visible above the text; the keyboard must resize the editor instead of covering the toolbar.
5. Put the caret immediately after a vowel and tap the dedicated `´` stress button. The same word must contain only one combining acute after the operation.
6. Tap the compact Listen/Pause control. Playback should use the current unsaved editor text without leaving edit mode.
7. While audio is playing, edit another word. Pause/restart and verify the changed draft is used.
8. Select a phrase, open `Grok ▾`, then choose the delivery submenu and apply Slow / Emphasis / another wrapping action. Verify opening and closing tags surround the selection.
9. Open `Grok ▾` and verify pauses/breath/sound commands are grouped in their own submenu instead of one very long first-level list.
10. Put the cursor inside a tagged phrase and start playback. The request should include the opening tag (Reader moves its effective playback start back to the tag boundary).
11. Save with the compact `✓` button. The keyboard should close and the normal Reader controls should return. The Reader should stay near the edited position rather than reset to the beginning.
12. In edit mode the Save text / Save audio row should be hidden. Outside edit mode it should be visible again.
13. Switch away from xAI Grok. The dedicated stress and Grok menu buttons must disappear, while compact Listen/Pause and Save remain usable.
14. Verify the Reader header is compact: back and settings are icon-sized and a long title uses one ellipsized line instead of taking two large rows.
