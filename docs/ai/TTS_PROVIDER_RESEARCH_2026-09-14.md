# TTS provider research — 2026-09-14

Snapshot for Kapijuja Reader. Re-check prices/model availability before shipping because these services change quickly.

## Goal

Primary use: high-quality Russian narration in an Android reader, preferably at or below the effective cost of OpenAI GPT-4o Mini TTS, with an API suitable for chunked long-form playback/export.

For rough cross-provider comparison below, character-priced services are normalized with the provider/common approximation of about 1,000 characters per minute (about 60,000 characters per hour). Token-priced Gemini estimates use Google's published 25 audio tokens/second. EUR estimates use the 2026-09-14 USD/EUR rate and are approximate.

## Strong candidates

### xAI Grok TTS

- Official price: USD 15 / 1M input characters.
- Rough cost: about EUR 0.78 per hour at 60k chars/hour.
- Russian (`ru`) is explicitly supported.
- REST batch endpoint plus WebSocket streaming.
- Built-in expressive voices, speech/style tags, speed control, MP3/WAV/PCM and telephony formats.
- 15,000 characters maximum per unary request, so it fits the Reader chunking model well.
- Integration complexity: low/medium. A dedicated `XaiTtsClient.kt` should own HTTP/WebSocket protocol and voice discovery.

Status: HIGH PRIORITY for an A/B Russian voice test.

### Inworld Realtime TTS-2 Flash

- On-demand price: USD 15 / 1M characters; lower on paid volume tiers.
- Rough on-demand cost: about EUR 0.78/hour at 60k chars/hour.
- 200+ languages advertised; multilingual/cross-lingual voices and cloning.
- MP3, WAV/PCM and Opus; realtime API; timestamps and pronunciation controls.
- Independent Artificial Analysis provider-voice arena (2026-09-14 snapshot): TTS-2 Flash is near the top; full TTS-2 is even higher.
- Full TTS-2 on-demand costs USD 25 / 1M chars and adds richer steering.

Status: HIGH PRIORITY. Verify Russian pronunciation with our own Russian corpus before shipping; global arena scores are not Russian-specific.

### Google Gemini TTS

Already integrated as `gemini-2.5-flash-preview-tts`.

Gemini 2.5 Flash Preview TTS:
- USD 0.50 / 1M text tokens + USD 10 / 1M audio tokens standard.
- Audio output uses about 25 tokens/second; rough audio-output cost is USD 0.90/hour, about EUR 0.78/hour, input cost is small.
- Batch API halves the token prices, so long non-interactive export can be materially cheaper (roughly EUR 0.39/hour for audio output).

Gemini 3.1 Flash TTS Preview:
- USD 1 / 1M text tokens + USD 20 / 1M audio tokens standard; batch is half.
- Rough standard audio-output cost: USD 1.80/hour, about EUR 1.56/hour.
- Better naturalness/controllability/multilinguality and expressive audio tags than 2.5 according to Google.

Status: KEEP 2.5 as a strong value baseline. Test 3.1 as an optional quality mode. Investigate Batch API only for offline/full-file export, not immediate playback.

### Alibaba Qwen3 TTS Flash

- International price: about USD 0.10 per 10,000 characters = USD 10 / 1M chars.
- Rough cost: about EUR 0.52/hour at 60k chars/hour.
- Russian is explicitly supported for Qwen3 system voices.
- HTTP and realtime variants exist; instruction/voice-clone/design variants are available in the Qwen3 family.
- Independent global provider-voice quality for the basic Qwen3 Flash family is much lower than the current leaders, so cheap price alone is not enough.

Status: MEDIUM PRIORITY as a budget Russian engine. Must A/B Russian quality before integration.

### Alibaba Qwen-Audio 3.0 TTS Plus / Flash

- International pricing: Plus USD 0.20 / 10k chars = USD 20/M; Flash USD 0.15 / 10k = USD 15/M.
- Rough cost: Plus about EUR 1.04/hour; Flash about EUR 0.78/hour.
- Artificial Analysis snapshot places Qwen-Audio-3.0-TTS-Plus among the top global provider-voice models.
- API is WebSocket.
- System voices are primarily Chinese/English, but cloned voices support Russian; voice cloning and instruction control are supported.

Status: INTERESTING EXPERIMENTAL/PREMIUM OPTION, especially if we intentionally create a licensed/consented Russian narrator voice. More integration complexity than Grok/Inworld.

### Cartesia Sonic 3.6

- Artificial Analysis snapshot: current top-ranked provider voice model globally.
- Pricing is subscription/credits; Pro USD 5 includes about 133 TTS minutes, Startup USD 49 about 1,667 min, Scale USD 299 about 10,667 min.
- This works out to roughly USD 1.8–2.3/hour depending on tier, before unused-credit effects.
- Strong quality candidate, but not cheaper than our value engines.

Status: OPTIONAL PREMIUM benchmark, not first cost-saving integration.

## Not suitable now

### Kimi
Official Kimi API help explicitly says TTS and ASR are not currently supported. Kimi can help with text processing, but cannot be the speech backend today.

### Claude / Claude Code
Anthropic's public Claude platform is a text/vision/tool/agent platform; no public Claude TTS synthesis endpoint is currently part of the documented API. Claude Code is useful for coding/refactoring this project, not as a TTS provider.

### Speechify Simba 3.2
Excellent price/quality on current independent rankings, but Simba 3.2 is English-only. Simba 3.0 adds several European languages but not Russian. The older multilingual model is being retired in 2026. Do not build new Russian support on it.

### Deepgram current TTS
Current premium TTS language coverage does not make it a good Russian Reader target. Revisit only if Russian support changes.

### MiniMax
Russian-capable options exist, but current per-character pricing is substantially higher than Grok, Gemini 2.5, Inworld Flash, and Qwen3 for this project's long-form reading use case.

## Recommended implementation order

1. Keep and benchmark the existing Gemini 2.5 integration as the cost/quality reference.
2. Add xAI Grok TTS first: explicit Russian support, simple REST path, competitive cost, easy chunking.
3. Add Inworld TTS-2 Flash next and perform blind Russian A/B testing against Grok/Gemini/OpenAI/Azure.
4. Test Qwen3 TTS Flash as the low-cost alternative before deciding whether it deserves permanent UI exposure.
5. Test Qwen-Audio 3.0 Plus only if we want a dedicated cloned Russian narrator voice.
6. Keep Cartesia as a premium comparison rather than a default provider.

## Architecture note

Do not add provider HTTP/WebSocket implementation into `ReaderActivity.kt` or `SettingsActivity.kt`. Each provider should get its own client/helper and expose a small synthesis/voice-list surface. Reader dispatch and Settings wiring should remain minimal. The future provider abstraction should allow REST file generation and streaming providers without duplicating playback/export logic.
