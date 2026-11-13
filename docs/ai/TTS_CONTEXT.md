# Kapijuja Reader — TTS subsystem context

Read this file before changing a speech engine/provider.

For the current provider/price/quality shortlist, read `TTS_PROVIDER_RESEARCH_2026-09-14.md` only when the task is provider selection, benchmarking, or adding a new provider. Do not preload it for unrelated Reader work.

## Current engines

The project currently has these TTS routes:

- Android system TTS — configured through `SettingsStore.kt`, driven from Reader playback.
- RHVoice — Android/local helper in `RhVoiceHelper.kt`.
- Microsoft Edge Read Aloud — `EdgeTtsClient.kt`.
- Microsoft Azure Speech — `AzureTtsClient.kt`.
- OpenAI TTS — `OpenAiTtsClient.kt`.
- Google Gemini TTS — `GoogleGeminiTtsClient.kt`.
- xAI Grok TTS — `XaiTtsClient.kt`.
- ElevenLabs — `ElevenLabsTtsClient.kt`.
- Inworld TTS — `InworldTtsClient.kt`.

## Files to open first

Provider implementation: open only the client/helper for that provider.

Shared cloud-provider routing now lives in `CloudTtsDispatcher.kt`. Open it when adding/removing a cloud engine from playback/export; do not duplicate protocol dispatch back into `ReaderActivity.kt`.

Provider connection-test boilerplate lives in `ProviderConnectionTester.kt`.

Provider settings/defaults/API keys: `SettingsStore.kt` plus the relevant range of `SettingsActivity.kt`.

For Grok pronunciation/stress/editor work, start with `GrokEditorMarkup.kt` and `GrokEditorToolbar.kt`; only then inspect the small Reader edit/playback call sites. Do not put Grok markup logic back into `ReaderActivity.kt`.

Playback orchestration: search `ReaderActivity.kt` for the provider name or the specific start/cost-guard function; do not read the whole Activity.

Audio export: search `ReaderActivity.kt` for the provider-specific export path and inspect `ReaderExportService.kt` only when foreground export behavior is involved.

## Design rules

Keep provider-specific protocol code inside a provider client/helper rather than `ReaderActivity.kt`.

Each cloud provider should own:

- endpoint/protocol details;
- request/response parsing;
- chunk sizing/splitting rules that are specific to its API;
- voice-list discovery if the provider supports it;
- provider-specific audio format constants;
- provider-specific cost estimation when available.

The Reader should orchestrate playback, not implement HTTP protocols.

Settings should store user configuration, not synthesize audio.

## Current important behavior

- OpenAI has a user-confirmation/cost path before potentially expensive generation.
- xAI Grok, ElevenLabs and Inworld are billed per character and share one generic cost guard in `ReaderActivity.startPerCharWithGuard(engine)`; names, keys, prices and links come from `CloudEngines.kt`.
- Gapless playback: `CloudPlaybackPolicy` lets every cloud engine prepare exactly one next chunk as soon as the current chunk starts playing. Stopping can leave one generated but unheard chunk (about 700 characters); the user accepted this on 2026-11-12 in exchange for no pauses. Never generate more than one chunk ahead.
- xAI uses the official REST `POST /v1/tts`, MP3 output and the current 60,000-character unary request limit. Export splitting is owned by `XaiTtsClient.kt`. xAI lists Russian but not Ukrainian: language is `ru`, or `auto` when the reading language is Ukrainian.
- xAI default Reader voice is `orion`; static voice choices are in `VoiceCatalog.kt`. The connection test uses `GET /v1/tts/voices`.
- ElevenLabs: `POST /v1/text-to-speech/{voice_id}`, header `xi-api-key`, MP3. Model is user-selectable (default `eleven_v3`). Voices are per account: `GET /v2/voices`, cached in settings via `CloudEngines.refreshVoices`.
- Inworld: `POST https://api.inworld.ai/tts/v1/voice`, `Authorization: Basic <key from Portal>`, JSON response with base64 `audioContent`, 2,000-character request limit (the client splits longer text and joins the MP3 parts). Voices: `GET /tts/v1/voices`.
- Shared HTTP/retry code for the two new providers is in `CloudHttp.kt` (DNS/connect retried, read timeout never retried to avoid double billing).
- Reading language (`SettingsStore.speechLanguage`: auto/ru/uk) is passed to ElevenLabs, Inworld and Grok. Gemini still sends `ru-RU`; Edge/Azure language follows the chosen voice (uk-UA Ostap/Polina were added).
- Settings: `ProviderSettingsSections.kt` owns the "get key / billing" link buttons for all providers, the reading-language choice and the ElevenLabs/Inworld sections.
- Android TTS voice menus use `AndroidVoicePicker.kt` + pure `AndroidVoiceLabels.kt` (language, gender for known Google speaker codes, online/offline, not downloaded; ru/uk first, other languages behind one item).
- Grok editor markup uses explicit Unicode stress marks plus the provider's documented wrapping/inline speech tags. The UI does not use a pronunciation dictionary.
- Grok wrapping tags are kept balanced across playback/export chunks; when playback starts inside a styled span, the Reader moves the effective start back to include the opening tag.
- Edge is treated as a no-direct-user-charge route but depends on Microsoft's service behavior.
- Azure uses the user's Speech key and region.
- Google Gemini TTS currently synthesizes PCM and exports WAV through the project's WAV tools.
- Long audio export must stream/chunk rather than accumulate an entire book in RAM.

## Architecture progress

The xAI integration and editor work keep provider-specific responsibilities outside the two legacy Activities:

- cloud synthesis/temp-file/export dispatch moved to `CloudTtsDispatcher.kt`;
- provider connectivity tests moved to `ProviderConnectionTester.kt`;
- Grok stress/tag transformations live in `GrokEditorMarkup.kt`;
- Grok editor dialog/UI lives in `GrokEditorToolbar.kt`;
- `ReaderActivity.kt` remains materially smaller than its historical 105,730-byte baseline;
- `SettingsActivity.kt` remains below its historical 47,506-byte baseline.

Continue this direction: provider additions should make the Activities stay flat or shrink where practical.

## Adding another provider

Prefer this shape:

1. new provider client/helper file;
2. minimal constants/defaults in `SettingsStore.kt`;
3. add the provider to `CloudTtsDispatcher.kt`;
4. provider UI section extracted or added surgically in Settings;
5. small Reader start/cost-guard integration only where user-flow behavior differs;
6. provider-specific tests for pure request/chunk/format logic where practical;
7. update this document and the current handoff.

Do not copy a whole existing provider implementation into `ReaderActivity.kt`.

## Playback chunking and pauses

Reader playback keeps cloud requests small (normal chunk target about 720 characters) instead of generating the whole remaining document in one paid request.

- the current chunk starts playing first;
- the next single chunk is requested immediately after playback of the current one starts;
- at most one future chunk is generated ahead;
- if a prefetched request is still running when playback reaches the boundary, Reader waits for that request instead of sending a duplicate paid request.
