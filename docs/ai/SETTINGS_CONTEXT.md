# Kapijuja Reader — settings/library context

Use this routing note for tasks involving engine selection, voices, credentials, language, library retention, battery/background settings, or diagnostics.

## Main rule

`SettingsActivity.kt` is a legacy oversized Activity. Search for the exact setting/provider first and open only that range. New provider-specific behavior should live in a helper/client or extracted settings component rather than making the Activity larger.

## Open by responsibility

- persistent settings/defaults: `SettingsStore.kt`;
- engine/API/voice protocol: the relevant provider client/helper;
- localization helper: `UiText.kt`;
- diagnostics: `AppDiagnostics.kt`;
- local library persistence/retention: `LibraryStore.kt`;
- background/export service behavior: `ReaderPlaybackService.kt` / `ReaderExportService.kt` as appropriate.

## Responsibilities to extract gradually

- engine selection UI;
- provider credentials and connection/status checks;
- voice discovery and selection;
- language selection;
- library retention/pruning settings;
- background/battery settings;
- diagnostics/service information.

Keep provider network calls out of generic screen-building code when possible.

## Adding a provider setting

1. add constants/defaults/accessors in `SettingsStore.kt`;
2. keep API validation/discovery in the provider client/helper;
3. add or extract only the provider's settings UI block;
4. avoid loading or rewriting unrelated Settings sections;
5. update `docs/ai/TTS_CONTEXT.md` and the current handoff.

## Context discipline

For a normal settings task, `AI_CONTEXT_INDEX.md` + this file + `SettingsStore.kt` + one provider helper + one small `SettingsActivity.kt` range should be enough. Do not preload the entire Reader playback stack.
