# Kapijuja Reader — handoff для следующего чата

Дата состояния: 2026-09-07  
Репозиторий: `standartkiev-pixel/kapijuja-reader`  
Основная ветка: `main`  
Текущая версия: `0.1.7`  
`versionCode = 8`  
Android package / applicationId: `com.kapijuja.reader`


## Обновление 0.1.7 — 2026-09-08

Эта секция новее 0.1.6 и имеет приоритет при расхождениях.

### Исправлен OOM при Android TTS WAV

Пользовательский bugreport 2026-09-08 дал точный stack trace:

`java.lang.OutOfMemoryError` при попытке выделить примерно 242–245 МБ в
`ByteArrayOutputStream` -> `WavTools.join()` -> `ReaderActivity.startAndroidWavExport()`.

То есть Android TTS уже успешно создавал все WAV-фрагменты; падал наш финальный этап, который пытался собрать весь огромный PCM/WAV в один ByteArray в heap с лимитом около 256 МБ.

Исправление:
- `WavTools.joinTo(files, OutputStream)` больше не создаёт полный WAV в RAM;
- WAV header пишется один раз;
- data chunks копируются последовательно через буфер 64 KiB прямо в выбранный пользователем файл;
- память теперь практически не зависит от длины итогового WAV;
- временные fragment files удаляются после завершения/ошибки.

MP3 encoder в основной APK НЕ добавлен: причина ошибки была не в формате WAV. Это сохраняет приложение маленьким. Если позже нужен сжатый локальный export без тяжёлого LAME, отдельно рассмотреть Android MediaCodec/AAC-M4A.

### Google Gemini long WAV также переведён на streaming

Раньше Gemini export накапливал весь PCM в `ByteArrayOutputStream`, поэтому на очень длинном тексте мог прийти к тому же OOM.

Теперь:
- каждый полученный PCM chunk сразу пишется во временный raw PCM file;
- `WavTools.pcmToWavTo()` создаёт итоговый WAV потоково;
- используется 64 KiB buffer;
- temporary PCM удаляется в `finally`.

### Кнопка Cancel

Во время любого audio export `updateEngineLabels()` теперь не имеет права вернуть кнопке обычный Save label.

Состояние:
- MP3: `Отменить MP3 / Anuluj MP3 / Cancel MP3`
- WAV: `Отменить WAV / Anuluj WAV / Cancel WAV`

`currentExportFormat` сохраняет активный формат до завершения. После окончания/отмены снова показывается Save MP3/WAV.

### Языки RU / PL / EN

Добавлена настройка языка интерфейса:
- Automatic — язык телефона;
- Русский;
- Polski;
- English.

По умолчанию стоит Automatic. Выбор сохраняется в SharedPreferences и действует после перезапуска. Settings применяет язык сразу через `recreate()`; Main/Reader при возврате из Settings тоже проверяют изменение и пересоздаются.

`UiText.kt` теперь:
- определяет RU/PL/EN;
- поддерживает явные 3-язычные строки;
- содержит централизованный Polish dictionary для существующих двухъязычных строк;
- локализует распространённые service/provider/document error messages через `localizeMessage()`.

Переведены основные меню, Settings, Reader controls, export dialogs/progress/errors, provider checks/status, notification MediaStyle controls и library UI.

### Библиотека остаётся простой читалкой

Не добавлялась база данных, теги или сложная архивная система.

Добавлено:
- default retention = последние 200 текстов;
- варианты Settings: 100 / 200 / 500 / 1000 / 5000 / 10000 / All;
- даже `All` имеет hard safety ceiling 1 GiB;
- при превышении удаляются самые старые тексты;
- отредактированный текст считается недавно сохранённым;
- long press на карточке -> Delete confirmation;
- Main показывает карточки порциями по 80;
- `LibraryStore.excerpt()` читает только небольшой кусок файла вместо загрузки полного текста для каждой карточки.

Это не превращает Reader в document manager и не даёт длинной библиотеке создавать тысячи View одновременно.

### Следующая проверка на устройстве

Особенно проверить:
- тот же длинный Android network voice export, который раньше падал на ~242–245 МБ;
- итоговый WAV реально открывается и имеет полный хронометраж;
- Cancel WAV и Cancel MP3: label меняется сразу и остаётся Cancel до остановки;
- Google Gemini long WAV;
- переключение Automatic/Russian/Polish/English;
- Polish menus/service messages;
- retention 100/200 и удаление oldest;
- long press delete;
- library >80 items -> Show more;
- background notification controls после language switch.



## Обновление 0.1.6 — 2026-09-07

Эта секция новее остальных частей handoff и имеет приоритет при расхождениях.

### Дефолт Android TTS

Для ЧИСТОЙ установки теперь:
- engine: `android:com.google.android.tts`
- preferred voice: `ru-ru-x-rud-network`

Существующие пользовательские настройки не перетираются. Если точного network-голоса на другом телефоне нет, Reader выбирает другой русский network voice, затем любой русский voice.

Отдельно сохранён пункт `android:default` как системный Android TTS.

### Reader UI

Удалена дублирующая верхняя кнопка «Слушать». Справа сверху остались только «Настройки».

В нижнем player одна основная кнопка:
`Слушать -> Пауза -> Продолжить`.

Player виден сразу. Редактирование теперь использует кнопку «Редактировать -> Сохранить», при этом остальные конфликтующие controls временно блокируются.

Tap-to-start, подсветка, CloudChunk и prebuffer НЕ переделывались.

### RU / EN локализация

Добавлен лёгкий `UiText.kt`: если основной язык телефона русский — интерфейс русский, иначе английский.

Основные Main / Reader / Settings menus, player controls, background controls и export controls локализованы. Не добавлять тяжёлый localization framework ради этой задачи.

### Android TTS audio export

Android TTS теперь умеет сохранять выбранный голос, включая Google Android network voices, через официальный `TextToSpeech.synthesizeToFile()`.

Формат Android TTS: WAV, не MP3.

Причина: не добавлять MP3 encoder и не раздувать APK. Для длинного текста создаются WAV fragments, затем `WavTools.kt` локально объединяет RIFF/WAV в один файл без внешней зависимости.

Cloud:
- OpenAI -> MP3
- Edge -> MP3
- Azure -> MP3
- Google Gemini -> WAV
- Android TTS / RHVoice -> WAV

### Отмена экспорта

При создании аудио кнопка становится:
`Отменить MP3` или `Отменить WAV`.

Экспорт проверяет cancellation между chunks. Android TTS synthesis останавливается через `tts.stop()`; network request, уже ушедший в облако, может физически закончиться, но следующие chunks больше не отправляются.

ProgressBar перенесён выше export buttons и увеличен по толщине.

### Edge timeout из bugreport

Bugreport пользователя от 2026-09-07 оказался правильным.

В нём Edge export request около 2983 UTF-8 bytes подключался, но не возвращал audio до 65 s timeout, тогда как меньшие playback chunks проходили.

Поэтому `EdgeTtsClient.MAX_TEXT_BYTES` уменьшен с ~3200 до 1800 bytes для дополнительного mobile-network safety margin.

### Фоновое чтение

Добавлены:
- foreground service типа `mediaPlayback`
- `MediaSession`
- MediaStyle notification
- Pause / Resume / Stop из шторки
- первый prompt на notification permission (Android 13+)
- запрос снятия battery optimization
- permissions FOREGROUND_SERVICE / MEDIA_PLAYBACK / WAKE_LOCK

`INTERNET` уже был в manifest; это обычное install-time permission без runtime dialog.

В 0.1.6 service сохраняет текущую ReaderActivity playback architecture: TTS / MediaPlayer не переносились целиком в service, чтобы не ломать рабочий CloudChunk/prebuffer. На устройстве обязательно проверить screen-off + notification controls + 30-60 min background playback.

### Silero v5.5

НЕ объявлять рабочим.

Проверено: официальный v5.5 Russian model распространяется как PyTorch package `v5_5_ru.pt`, а не готовый Android TorchScript module. Android PyTorch Lite runtime сам по себе добавляет порядка 72 МБ ещё до модели, и стандартный Android `Module.load()` не является прямым loader для этого package format.

Поэтому runtime намеренно НЕ встроен в основной APK 0.1.6: иначе маленькое приложение резко раздуется, а v5.5 всё равно потребует отдельной conversion/adaptation стадии.

Silero оставлен одной из следующих экспериментальных задач. Если тестировать — лучше отдельный experimental build/flavor, чтобы его можно было полностью выпилить без влияния на base APK.

### GitHub Actions / APK

Actions artifact storage quota 2026-09-07 была переполнена. Сам APK при этом компилировался и подписывался успешно, падал только `actions/upload-artifact`.

Workflow изменён:
- artifact upload = best effort, retention 7 days
- дополнительно signed APK публикуется/перезаписывается в prerelease tag:
  `kapijuja-reader-latest-test`

Стабильная страница последнего тестового APK:
https://github.com/standartkiev-pixel/kapijuja-reader/releases/tag/kapijuja-reader-latest-test

Следующая проверка на устройстве:
- fresh install default Google Android TTS + `ru-ru-x-rud-network`
- single Listen/Pause/Resume button
- English UI при нерусской системной локали
- Android network voice -> WAV export
- cancel MP3/WAV
- длинный Edge MP3 после 1800-byte chunks
- background screen-off + notification Pause/Resume/Stop
- Azure/Google/OpenAI regression
- tap-to-start regression


## Что это за проект

Kapijuja Reader — уже работающая Android-читалка текста с несколькими TTS-движками. Проект не начинать заново. Работать поверх текущей ветки `main`, не менять радикально дизайн, не менять `applicationId`, не ломать текущую подпись APK.

Пользователю нравится текущий тёмно-синий «электронный» дизайн с ярко-синими кнопками и оранжевой рамкой у активной кнопки. Также очень важно сохранить маленький размер APK.

Последняя успешная сборка:

https://github.com/standartkiev-pixel/kapijuja-reader/actions/runs/34153423511

Artifact: `kapijuja-reader-apk`  
Внутри: `kapijuja-reader.apk`  
Размер artifact около 1.45 МБ.

## Что уже работает

На главном экране есть библиотека сохранённых текстов. Кнопка «+» открывает три варианта:
- Открыть документ
- Ввести текст
- Вставить ссылку

Тексты сохраняются через `LibraryStore`.

### Импорт документов

Сейчас реально поддерживаются:
- TXT
- MD
- HTML / HTM
- CSV
- JSON
- XML
- DOCX

DOCX читается компактно через ZIP + `word/document.xml`, без тяжёлой библиотеки.

Пока не поддерживаются:
- PDF
- старый бинарный DOC

Это видно в `DocumentTextExtractor.kt`: PDF и DOC пока возвращают сообщение, что будут подключены позже.

### Импорт сайта

`MainActivity` загружает HTML через `HttpURLConnection`, затем `HtmlExtractor` пытается выделить читаемый текст.

Функция работает, но иногда остаются menu / login / skip-to-content / footer / navigation. Это одна из следующих задач.

Изначально обсуждался вариант очистки страницы через ChatGPT, но текущая реализация URL extraction локальная и API не тратит.

### Редактор

Текст можно редактировать прямо из ReaderActivity. При переходе в редактор воспроизведение останавливается. После сохранения:
- обновляется LibraryStore
- перестраивается разбиение на предложения
- чтение снова доступно

### Чтение с места, куда нажал пользователь

Это уже хорошо работает и пользователь отдельно подтвердил, что функция удачная.

Тап по тексту:
- определяется character offset
- находится соответствующий Segment
- чтение переносится на это место
- если чтение уже шло, оно корректно переключается

Эту функцию не ломать.

### Подсветка и автопрокрутка

Текущее предложение подсвечивается синим и текст прокручивается к читаемому месту.

После перехода к большим cloud-chunk подсветка внутри блока переключается приблизительно по предложениям пропорционально их длине.

### Настройки

Кнопка «⚙ Настройки» есть на главном экране и в ReaderActivity.

Настройки сохраняются автоматически при выходе / onPause. Пользователь не хочет обязательную кнопку «Сохранить настройки».

Сохраняются:
- выбранный движок
- отдельный голос для каждого движка
- OpenAI API key
- OpenAI voice instructions
- порог предупреждения о стоимости
- Azure Speech key
- Azure region
- Google Gemini API key
- Google voice instructions

Голос хранится отдельно для каждого engine.

### Safe area

Раньше UI залезал под status bar и navigation bar. Исправлено через `WindowInsets` / `KapijujaUiTheme.applySafeArea()`.

Это не ломать.

# Подключённые движки

## Android TTS

Статус: работает, подтверждено пользователем.

Используется `android.speech.tts.TextToSpeech`. Подтягиваются реально установленные движки и голоса Android.

Раньше была ошибка: пользователь выбирал новый голос, настройка сохранялась, но уже созданный `TextToSpeech` продолжал говорить старым голосом.

Исправлено: при возврате из настроек сравниваются active engine + active voice, и при изменении TTS переинициализируется.

## RHVoice

Статус: архитектура подключена как внешний Android TTS.

Пакет:
`com.github.olga_yakovleva.rhvoice.android`

RHVoice не встраивается в APK, чтобы не раздувать приложение. Если пакет не установлен, Reader предлагает Google Play / F-Droid. После установки голоса подтягиваются через Android TTS API.

Рекомендуемый русский мужской голос:
`Aleksandr-HQ`

API key не нужен, работает офлайн.

Реальный пользовательский тест RHVoice ещё желательно продолжить.

## OpenAI GPT-4o Mini TTS

Статус: работает, подтверждено пользователем.

Файл:
`OpenAiTtsClient.kt`

Модель:
`gpt-4o-mini-tts`

Голоса:
`cedar, marin, onyx, echo, alloy, ash, ballad, coral, fable, nova, sage, shimmer, verse`

По умолчанию:
`cedar`

API key хранится локально в приложении. Не коммитить его в репозиторий и не зашивать в APK.

Есть оценка стоимости и подтверждение перед более дорогой генерацией.

Критический уже исправленный crash:
новый OpenAI MP3 успевал создаться, затем код назначал его activeTempFile, вызывал `stopMediaOnly()`, удалял этот же файл и только потом делал `MediaPlayer.setDataSource()`. Получался `FileNotFoundException / ENOENT`.

Исправлено: сначала удаляется старый player/file, затем регистрируется новый MP3.

Также встречался временный:
`Unable to resolve host api.openai.com`

Для DNS/connect добавлены retries. Timeout после отправленного платного запроса автоматически не повторяется, чтобы не получить двойную генерацию/оплату.

В Settings есть проверка OpenAI connection.

## Microsoft Edge Read Aloud

Статус: чтение работает, подтверждено пользователем.

Файл:
`EdgeTtsClient.kt`

Используется неофициальный Edge Read Aloud WebSocket endpoint. API key не нужен.

Русские голоса:
- `ru-RU-DmitryNeural`
- `ru-RU-SvetlanaNeural`
- `ru-RU-DariyaNeural`

По умолчанию:
`ru-RU-DmitryNeural`

Проблема длинного MP3 export:
короткие предложения работали, а длинный русский chunk мог рвать WebSocket с:
`Software caused connection abort`

Причина: лимит зависит от UTF-8 bytes, а не от Kotlin String length; кириллица обычно занимает больше одного byte.

Исправлено:
- chunking по UTF-8 bytes
- безопасный лимит около 3200 bytes
- retries для transient network failures

Длинный Edge export после этих исправлений желательно продолжать проверять.

## Microsoft Azure Speech

Статус: работает, качество пользователю нравится. После новой буферизации пользователь отдельно сообщил, что чтение теперь идёт без прежних больших задержек.

Файл:
`AzureTtsClient.kt`

Аутентификация:
- один Azure Speech key
- точный region identifier

Регион по умолчанию:
`switzerlandnorth`

Именно такой формат:
`switzerlandnorth`

Не:
`Switzerland North`  
не URL  
не произвольный текст.

Поле region нормализуется: только маленькие латинские буквы и цифры.

Пользователь реально создал Speech resource в Switzerland North.

Endpoint строится программно:
`https://{region}.tts.speech.microsoft.com/cognitiveservices/v1`

Голос Azure по умолчанию:
`ru-RU-DmitryNeural`

Это важный default для проекта.

Также есть:
- `ru-RU-SvetlanaNeural`
- `ru-RU-DariyaNeural`

Старый Lev HD на текущем F0/ресурсе давал HTTP 400. Добавлена миграция старой настройки Lev -> Dmitry.

В bugreport Dmitry давал Azure HTTP 200.

Ещё одна проблема:
длинный chunk около 2400 символов при export мог давать:
`java.net.SocketException: Software caused connection abort`

Исправлено:
- Azure chunk для export уменьшен примерно до 900 символов
- transient socket/timeout/reset повторяются
- переносы строк и большие пробелы нормализуются перед SSML

## Google Gemini 2.5 Flash TTS

Статус: реализован в версии 0.1.5, сборка успешна. Нужен следующий пользовательский тест.

Файл:
`GoogleGeminiTtsClient.kt`

Модель:
`gemini-2.5-flash-preview-tts`

Developer API endpoint:
`generativelanguage.googleapis.com`

Нужен Google Gemini API key из Google AI Studio.

В Settings уже есть:
- Google Gemini API key
- инструкция голосу
- кнопка проверки Google Gemini TTS

Язык:
`ru-RU`

Голос по умолчанию:
`Gacrux`

Каталог содержит 30 Gemini voices:
`Gacrux, Charon, Orus, Algenib, Schedar, Sadaltager, Sulafat, Iapetus, Alnilam, Rasalgethi, Puck, Fenrir, Kore, Zephyr, Leda, Aoede, Callirrhoe, Autonoe, Enceladus, Umbriel, Algieba, Despina, Erinome, Laomedeia, Achernar, Pulcherrima, Achird, Zubenelgenubi, Vindemiatrix, Sadachbia`.

Для первого теста русского зрелого/мужского звучания интереснее:
- Gacrux — mature
- Charon — informative
- Orus — firm
- Algenib — gravelly
- Schedar — even
- Sadaltager — knowledgeable

Gemini TTS отдаёт raw PCM 24 kHz mono 16-bit. Код собирает WAV самостоятельно, без тяжёлого encoder.

Поэтому при Google кнопка становится:
`Сохранить WAV`

У других облачных движков:
`Сохранить MP3`

Это сделано специально, чтобы сохранить маленький APK.

Нужно протестировать:
- API key validation
- русский playback
- Gacrux / Charon / Orus
- WAV export
- free-tier/rate-limit ошибки
- длинный текст
- паузы между блоками

## Silero TTS v5.5 Russian

Статус: runtime пока НЕ подключён.

Каталог голосов есть:
`eugene, aidar, baya, kseniya, xenia`

Но model/runtime в APK нет.

Не делать вид, что Silero работает, пока он реально не подключён.

Причина, почему отложили:
PyTorch / ONNX runtime + voice model могут увеличить APK с ~1.5 МБ до десятков или сотен МБ.

Если подключать позже, предпочтительно:
- загружать модель отдельно после установки
- использовать companion package
- исследовать sherpa-onnx / Piper / Silero с внешней моделью
- не класть тяжёлые модели прямо в base APK

# Главная проблема пауз и её решение

Это один из самых важных архитектурных моментов.

Раньше cloud TTS работал так:

предложение 1 -> API -> скачать audio -> проиграть -> после окончания запросить предложение 2 -> ждать сеть -> проиграть -> и т.д.

Из-за этого между точкой и следующим предложением было 2–4 секунды тишины. Между абзацами иногда ещё хуже.

В 0.1.5 схема изменена.

`ReaderActivity` объединяет несколько `Segment` в `CloudChunk` примерно до 700 символов.

В API уходит связный кусок из нескольких предложений.

Whitespace нормализуется:
переносы строк / абзацы -> обычный одиночный пробел.

Для бесплатных / quota-based engines:
- Edge
- Azure
- Google

следующий chunk генерируется заранее в фоне, пока текущий уже играет.

Используются:
- `prefetchedCloudFiles`
- `prefetchingCloudSegments`

Когда текущий chunk заканчивается, следующий обычно уже лежит в cache, поэтому разрыв практически исчезает.

OpenAI намеренно НЕ prefetch’ится заранее, чтобы не платить за следующий кусок, который пользователь может не дослушать. Но OpenAI тоже получает более крупные chunks, поэтому сетевых пауз меньше.

Пользователь после этой переработки сообщил:
«Теперь действительно работает всё хорошо, без задержек.»

Эту архитектуру сохранить.

# Экспорт аудио

OpenAI:
MP3

Edge:
MP3

Azure:
MP3

Google Gemini:
WAV

Есть ProgressBar с количеством chunks и процентом.

После окончания должно появляться явное окно:
- «MP3 готов»
или
- «WAV готов»

Это было сделано потому, что раньше пользователь видел «Создаётся MP3…» и не понимал, завершился процесс или завис.

# Диагностика

В Settings есть:
- Проверить OpenAI
- Проверить Azure Speech
- Проверить Google Gemini TTS
- Показать журнал диагностики

`AppDiagnostics` пишет:
- engine
- voice
- HTTP codes
- размер полученного audio
- запуск MediaPlayer
- сетевые ошибки
- prebuffer events

API keys в diagnostics НЕ писать.

# Важные файлы

`MainActivity.kt` — главный экран, Library, три способа импорта, URL download.

`ReaderActivity.kt` — tap-to-start, highlight, chunking, prebuffer, playback, edit, speed, audio export.

`SettingsActivity.kt` — engines, voices, keys, region, diagnostics, autosave.

`SettingsStore.kt` — SharedPreferences и defaults.

`VoiceCatalog.kt` — каталоги OpenAI, Edge, Azure, Google, Silero.

`OpenAiTtsClient.kt` — OpenAI TTS.

`EdgeTtsClient.kt` — Edge WebSocket TTS.

`AzureTtsClient.kt` — Azure REST Speech.

`GoogleGeminiTtsClient.kt` — Gemini Developer API TTS + PCM -> WAV.

`RhVoiceHelper.kt` — установка / обнаружение RHVoice.

`AppDiagnostics.kt` — локальный диагностический log.

`DocumentTextExtractor.kt` — document import.

`HtmlExtractor.kt` — HTML cleanup.

`KapijujaUiTheme.kt` — визуальный стиль и WindowInsets.

# API keys

OpenAI key хранится локально.  
Azure Speech key хранится локально.  
Google Gemini key хранится локально.

Не коммитить реальные keys в GitHub.
Не писать keys в logs.
Не зашивать пользовательские keys в BuildConfig / APK.

Если приложение станет распространяемым, для облачных платных providers надо рассмотреть backend relay/proxy.

# Что делать дальше

## Этап 1 — стабилизация 0.1.5

Проверить на реальном телефоне:
- Azure после новой chunk/prefetch схемы на длинном тексте
- Azure MP3 export после уменьшения chunk до ~900 символов
- Edge MP3 export на длинном русском тексте
- Google Gemini API key check
- Google playback
- Google WAV export
- Gacrux / Charon / Orus на одном и том же русском тексте
- tap-to-start на каждом cloud engine
- смену скорости во время чтения
- pause/resume после prebuffer

Если приходит новый bugreport ZIP — сначала анализировать конкретные timestamps/errors, а не менять архитектуру наугад.

## Этап 2 — улучшить web extraction

Нужно лучше удалять:
- menu
- navigation
- login
- cookies
- skip-to-content
- footer
- related articles

Предпочтительный путь: локальный readability-like scoring по text density / paragraphs / удалению nav-header-footer-form-button.

OpenAI можно оставить optional fallback для сложных страниц.

## Этап 3 — PDF

Реализовать text extraction из обычных текстовых PDF.

Желательно без огромной библиотеки.

OCR не делать по умолчанию; сначала поддержать только PDF, где есть текстовый слой.

## Этап 4 — старый DOC

Поддержать только если это можно сделать компактно.

DOCX уже работает.

## Этап 5 — сравнение cloud engines

Сравнить на одном и том же русском тексте:
- OpenAI
- Edge Dmitry
- Azure Dmitry
- Gemini Gacrux / Charon / Orus

После тестов решить, какие engines оставлять в основном меню.

## Этап 6 — локальные engines

Проверить RHVoice / Aleksandr-HQ.

Потом решить, нужен ли Silero.

Если нужен более neural local TTS, исследовать:
- sherpa-onnx
- Piper
- Silero
с моделью, загружаемой отдельно.

## Этап 7 — библиотека

Полезные будущие функции:
- удалить текст
- переименовать
- сортировка / дата
- сохранение позиции чтения
- «Продолжить с последнего места»

## Этап 8 — сохранение позиции

Сейчас tap-to-start работает.

Следующий логичный шаг: сохранять currentSegment / character offset в LibraryStore, чтобы после закрытия приложения текст продолжался с последнего места.

# Что нельзя сломать

1. Текущий дизайн.
2. Маленький APK.
3. `applicationId = com.kapijuja.reader`.
4. Постоянную подпись APK.
5. Tap-to-start.
6. Автосохранение настроек.
7. Отдельный voice для каждого engine.
8. WindowInsets.
9. Cloud chunk + prebuffer архитектуру.
10. Azure defaults:
   - region = `switzerlandnorth`
   - voice = `ru-RU-DmitryNeural`
11. API keys никогда не писать в log и не коммитить.
12. Не показывать engine как рабочий, пока runtime/API реально не подключён.

# Уже решённые критические баги

OpenAI:
`FileNotFoundException` из-за удаления нового MP3 перед `MediaPlayer`. Исправлено.

OpenAI:
временный DNS `Unable to resolve host api.openai.com`. Добавлены retries.

Android TTS:
выбранный голос не применялся к уже живому `TextToSpeech`. Исправлено переинициализацией.

UI:
верх и низ выходили за системные панели. Исправлено WindowInsets.

Edge:
длинный русский текст -> WebSocket connection abort. Исправлено UTF-8 byte chunking + retries.

Azure:
Lev HD -> HTTP 400. Default заменён на Dmitry Neural.

Azure:
длинный chunk -> `Software caused connection abort`. Уменьшен chunk + retries.

Cloud TTS:
2–4 секунды между предложениями. Причина — отдельный API request на каждое предложение. Исправлено CloudChunk + whitespace normalization + prebuffer.

Export:
не было понятно, завершилось ли создание файла. Добавлен ProgressBar + success dialog.

# Текущее подтверждённое состояние

Пользователь подтвердил:
- Android TTS работает
- OpenAI работает
- Microsoft Edge читает
- Azure работает и звучит хорошо
- tap-to-start работает очень хорошо
- после новой буферизации задержки между предложениями исчезли
- интерфейс нравится
- маленький размер APK особенно нравится

Реализовано, но требует следующего пользовательского теста:
- Google Gemini 2.5 Flash TTS
- Google WAV export
- RHVoice flow на конкретном устройстве
- длинный Edge/Azure export после последних chunk/retry fixes

# Инструкция следующему чату

Сначала открыть репозиторий `standartkiev-pixel/kapijuja-reader` и прочитать этот файл.

Не переписывать проект заново.

Перед изменениями смотреть текущий `main`.

Работать небольшими проверяемыми этапами.

После логического этапа делать commit.

Перед тестовой сборкой поднимать `versionCode` / `versionName`.

Дождаться успешного GitHub Actions build.

Пользователю давать ссылку на конкретный run/artifact с `kapijuja-reader.apk`.

Если пользователь присылает bugreport ZIP — анализировать конкретный logcat / timestamps / AppDiagnostics.

Продолжать сохранять компактность приложения и избегать тяжёлых зависимостей без необходимости.

Проект уже не макет. Это работающая Android-читалка с несколькими реальными TTS backend’ами. Дальше нужны стабилизация, качество чтения, новые backend’ы и улучшение импорта.
