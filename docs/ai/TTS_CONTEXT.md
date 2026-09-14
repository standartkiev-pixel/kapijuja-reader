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
- experimental local Silero v5.5 — `SileroRuntime.kt`.

## Files to open first

Provider implementation: open only the client/helper for that provider.

Provider settings/defaults/API keys: `SettingsStore.kt` plus the relevant range of `SettingsActivity.kt`.

Playback orchestration: search `ReaderActivity.kt` for the provider name or the specific start/synthesize function; do not read the whole Activity.

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
- Edge is treated as a no-direct-user-charge route but depends on Microsoft's service behavior.
- Azure uses the user's Speech key and region.
- Google Gemini TTS currently synthesizes PCM and exports WAV through the project's WAV tools.
- Silero is experimental local inference; keep it isolated from cloud-client assumptions.
- Long audio export must stream/chunk rather than accumulate an entire book in RAM.

## Adding another provider

Prefer this shape:

1. new provider client/helper file;
2. minimal constants/defaults in `SettingsStore.kt`;
3. provider UI section extracted or added surgically in Settings;
4. small dispatch integration in Reader;
5. provider-specific tests for pure request/chunk/format logic where practical;
6. update this document and the current handoff.

Do not copy a whole existing provider implementation into `ReaderActivity.kt`.
