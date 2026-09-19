# Kapijuja Reader

Free and open-source Android text reader with local and cloud TTS engines.

## Download

[Download the latest test APK](https://github.com/standartkiev-pixel/kapijuja-reader/releases/download/kapijuja-reader-latest-test/kapijuja-reader.apk)

The current experimental APK targets 64-bit ARM Android devices.

## Development handoff and architecture

For AI-assisted development, use progressive context loading instead of reading the whole repository:

- [AI_CONTEXT_INDEX.md](AI_CONTEXT_INDEX.md) — **start here**; routes a task to the minimum relevant files
- [AI_DEVELOPMENT_RULES.md](AI_DEVELOPMENT_RULES.md) — source-size, modularity, testing, and safe-edit rules
- [NEXT_CHAT_HANDOFF_2026-09-12.txt](NEXT_CHAT_HANDOFF_2026-09-12.txt) — short current-state handoff
- [docs/ai/TTS_CONTEXT.md](docs/ai/TTS_CONTEXT.md) — TTS/provider routing
- [docs/ai/READER_CONTEXT.md](docs/ai/READER_CONTEXT.md) — playback/export/Reader routing
- [docs/ai/SETTINGS_CONTEXT.md](docs/ai/SETTINGS_CONTEXT.md) — settings/library routing
- [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md) — longer historical/implementation record; search it by topic rather than loading it in full by default

The repository intentionally prevents already-oversized legacy source files from silently growing further. Refactoring should be incremental and behavior-preserving, with characterization tests added before large structural moves.

## Features

- document, pasted-text, and URL import
- local text library
- sentence highlighting, autoscroll, and tap-to-start
- foreground playback and media notification controls
- WAV and MP3 export, depending on the selected engine
- Android TTS and RHVoice
- Microsoft Edge Read Aloud
- Microsoft Azure Speech
- OpenAI TTS
- Google Gemini TTS

## Peace statement / Заявление о мире

The authors of Kapijuja Reader oppose war in every form. We reject the idea
that protecting some people should require killing others. If you believe that
human beings must be defended through the killing of other human beings, we
respectfully ask you to remain apart from us and this project. This project
stands for peace, human life, cooperation, and nonviolence.

Авторы Kapijuja Reader выступают против войны в любом её виде. Мы отвергаем
идею о том, что защита одних людей должна требовать убийства других. Если вы
считаете, что людей необходимо защищать посредством убийства других людей,
мы просим вас оставаться в стороне от нас и этого проекта. Этот проект
выступает за мир, человеческую жизнь, сотрудничество и ненасилие.

## License

Kapijuja Reader is free and open-source software licensed under the
[GNU General Public License v3.0 or later](LICENSE).

SPDX-License-Identifier: GPL-3.0-or-later

## API keys

OpenAI, Azure Speech, and Google Gemini API keys are entered by the user and
stored locally on the Android device. Never commit API keys, signing keys, or
local configuration files to this repository.
