package com.kapijuja.reader

import android.content.Context
import android.os.Build
import java.util.Locale

object UiText {
    fun language(context: Context): String {
        val override = SettingsStore.uiLanguage(context)
        if (override != SettingsStore.UI_LANGUAGE_SYSTEM) return override

        val config = context.resources.configuration
        val locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.locales[0]
            } else {
                @Suppress("DEPRECATION")
                config.locale
            }

        return when (locale?.language?.lowercase(Locale.ROOT)) {
            "ru" -> SettingsStore.UI_LANGUAGE_RU
            "pl" -> SettingsStore.UI_LANGUAGE_PL
            else -> SettingsStore.UI_LANGUAGE_EN
        }
    }

    fun get(
        context: Context,
        ru: String,
        pl: String,
        en: String
    ): String =
        when (language(context)) {
            SettingsStore.UI_LANGUAGE_RU -> ru
            SettingsStore.UI_LANGUAGE_PL -> pl
            else -> en
        }

    fun get(context: Context, ru: String, en: String): String =
        when (language(context)) {
            SettingsStore.UI_LANGUAGE_RU -> ru
            SettingsStore.UI_LANGUAGE_PL -> polish(ru) ?: en
            else -> en
        }

    fun languageLabel(context: Context): String =
        when (language(context)) {
            SettingsStore.UI_LANGUAGE_RU -> "Русский"
            SettingsStore.UI_LANGUAGE_PL -> "Polski"
            else -> "English"
        }

    fun localizeMessage(context: Context, message: String): String {
        if (message.isBlank() || language(context) == SettingsStore.UI_LANGUAGE_RU) {
            return message
        }

        val pl = language(context) == SettingsStore.UI_LANGUAGE_PL

        fun pick(polish: String, english: String) =
            if (pl) polish else english

        return when {
            message == "OpenAI доступен. DNS, интернет и API key работают." ->
                pick(
                    "OpenAI jest dostępny. DNS, internet i klucz API działają.",
                    "OpenAI is available. DNS, internet and the API key work."
                )

            message == "Azure Speech доступен; русский Dmitry Neural найден." ->
                pick(
                    "Azure Speech jest dostępny; znaleziono rosyjski głos Dmitry Neural.",
                    "Azure Speech is available; Russian Dmitry Neural was found."
                )

            message == "Azure Speech доступен; список голосов получен." ->
                pick(
                    "Azure Speech jest dostępny; pobrano listę głosów.",
                    "Azure Speech is available; the voice list was received."
                )

            message == "Google Gemini 2.5 Flash TTS доступен." ->
                pick(
                    "Google Gemini 2.5 Flash TTS jest dostępny.",
                    "Google Gemini 2.5 Flash TTS is available."
                )

            message == "Пустой текст" ->
                pick("Pusty tekst", "Empty text")

            message == "Не удалось открыть файл" ->
                pick("Nie udało się otworzyć pliku", "Could not open file")

            message == "PDF будет подключён на следующем шаге" ->
                pick(
                    "Obsługa PDF zostanie dodana w kolejnym kroku.",
                    "PDF support will be added in the next step."
                )

            message == "Старый DOC будет подключён на следующем шаге" ->
                pick(
                    "Obsługa starego formatu DOC zostanie dodana w kolejnym kroku.",
                    "Legacy DOC support will be added in the next step."
                )

            message == "Пока поддерживаются TXT/MD/HTML/DOCX" ->
                pick(
                    "Obecnie obsługiwane są TXT/MD/HTML/DOCX.",
                    "Currently supported: TXT/MD/HTML/DOCX."
                )

            message == "В DOCX не найден текст" ->
                pick(
                    "Nie znaleziono tekstu w DOCX.",
                    "No text was found in the DOCX."
                )

            message.contains("таймаут ожидания аудио", ignoreCase = true) ->
                pick(
                    "Microsoft Edge TTS: przekroczono czas oczekiwania na audio.",
                    "Microsoft Edge TTS: audio response timed out."
                )

            message.startsWith("OpenAI API key не указан") ->
                pick(
                    "Nie podano klucza API OpenAI.",
                    "OpenAI API key is missing."
                )

            message.startsWith("Azure Speech key не указан") ->
                pick(
                    "Nie podano klucza Azure Speech.",
                    "Azure Speech key is missing."
                )

            message.startsWith("Google Gemini API key не указан") ->
                pick(
                    "Nie podano klucza API Google Gemini.",
                    "Google Gemini API key is missing."
                )

            message.startsWith("Голос Microsoft Edge не выбран") ->
                pick(
                    "Nie wybrano głosu Microsoft Edge.",
                    "Microsoft Edge voice is not selected."
                )

            message.startsWith("Google voice не выбран") ->
                pick(
                    "Nie wybrano głosu Google.",
                    "Google voice is not selected."
                )

            message.startsWith("Azure voice не выбран") ->
                pick(
                    "Nie wybrano głosu Azure.",
                    "Azure voice is not selected."
                )

            message.startsWith("Не удалось") ->
                pick(
                    "Nie udało się: " + message.removePrefix("Не удалось").trimStart(':', ' '),
                    "Failed: " + message.removePrefix("Не удалось").trimStart(':', ' ')
                )

            else -> message
        }
    }

    private fun polish(ru: String): String? =
        when (ru) {
            "⚙ Настройки" -> "⚙ Ustawienia"
            "Все изменения сохраняются автоматически при выходе." ->
                "Wszystkie zmiany są zapisywane automatycznie przy wyjściu."
            "Движок" -> "Silnik"
            "Голос" -> "Głos"
            "Редактировать" -> "Edytuj"
            "Сохранить" -> "Zapisz"
            "Редактирование" -> "Edycja"
            "Слушать" -> "Słuchaj"
            "Пауза" -> "Pauza"
            "Продолжить" -> "Wznów"
            "Сначала" -> "Od początku"
            "Чтение завершено." -> "Czytanie zakończone."
            "Ошибка Android TTS." -> "Błąd Android TTS."
            "Сохранить файл" -> "Zapisz tekst"
            "Сохранить WAV" -> "Zapisz WAV"
            "Сохранить MP3" -> "Zapisz MP3"
            "Отменяется…" -> "Anulowanie…"
            "Отмена создания файла…" -> "Anulowanie tworzenia pliku…"
            "Создание аудиофайла отменено." -> "Tworzenie pliku audio anulowano."
            "Пустой текст" -> "Pusty tekst"
            "WAV полностью записан • 100%" -> "WAV zapisany • 100%"
            "WAV сохранён • Android TTS" -> "WAV zapisany • Android TTS"
            "WAV готов" -> "WAV gotowy"
            "Файл полностью создан и записан." -> "Plik został utworzony i zapisany."
            "Ошибка создания WAV" -> "Błąd tworzenia WAV"
            "Ошибка Android TTS" -> "Błąd Android TTS"
            "WAV не создан" -> "Nie utworzono WAV"
            "Неизвестная ошибка" -> "Nieznany błąd"
            "Аудиоэкспорт" -> "Eksport audio"
            "Для этого движка аудиоэкспорт пока недоступен." ->
                "Eksport audio dla tego silnika nie jest jeszcze dostępny."
            "Ошибка создания MP3" -> "Błąd tworzenia MP3"
            "MP3 не создан" -> "Nie utworzono MP3"
            "MP3 полностью записан • 100%" -> "MP3 zapisany • 100%"
            "Библиотека" -> "Biblioteka"
            "Текстов пока нет.\nНажмите +, чтобы открыть документ, вставить текст или ссылку." ->
                "Nie ma jeszcze tekstów.\nNaciśnij +, aby otworzyć dokument, wkleić tekst lub link."
            "Добавить текст" -> "Dodaj tekst"
            "Открыть документ" -> "Otwórz dokument"
            "Ввести текст" -> "Wpisz tekst"
            "Вставить ссылку" -> "Wklej link"
            "Название (необязательно)" -> "Tytuł (opcjonalnie)"
            "Вставьте или напишите текст" -> "Wklej lub wpisz tekst"
            "Открыть" -> "Otwórz"
            "Загрузить текст" -> "Pobierz tekst"
            "Фоновое чтение" -> "Czytanie w tle"
            "Чтобы чтение не замораживалось при выключенном экране, разрешите уведомления и снимите ограничение батареи для Kapijuja Reader." ->
                "Aby czytanie nie zatrzymywało się po wyłączeniu ekranu, zezwól na powiadomienia i wyłącz ograniczenia baterii dla Kapijuja Reader."
            "Позже" -> "Później"
            "Настроить" -> "Skonfiguruj"
            "Защита от расходов" -> "Ochrona przed kosztami"
            "Порог, после которого приложение обязательно покажет ориентировочную стоимость перед генерацией, €:" ->
                "Próg, powyżej którego aplikacja pokaże szacowany koszt przed generowaniem, €:"
            "Сервис" -> "Serwis"
            "Проверить OpenAI" -> "Sprawdź OpenAI"
            "Проверить Azure Speech" -> "Sprawdź Azure Speech"
            "Проверить Google Gemini TTS" -> "Sprawdź Google Gemini TTS"
            "Показать журнал диагностики" -> "Pokaż dziennik diagnostyczny"
            "Очистить" -> "Wyczyść"
            "Закрыть" -> "Zamknij"
            "Выберите движок" -> "Wybierz silnik"
            "Выбрать голос" -> "Wybierz głos"
            "Android TTS — системный" -> "Android TTS — systemowy"
            "Google Android TTS — по умолчанию" -> "Google Android TTS — domyślny"
            "Движок пока не активен" -> "Silnik nie jest jeszcze aktywny"
            "Этот движок пока не активен." -> "Ten silnik nie jest jeszcze aktywny."
            "Для этого движка список голосов пока недоступен" ->
                "Lista głosów dla tego silnika nie jest jeszcze dostępna"
            "Не удалось открыть TTS движок" -> "Nie udało się otworzyć silnika TTS"
            "Движок не вернул список голосов" -> "Silnik nie zwrócił listy głosów"
            "Текст сохранён." -> "Tekst zapisany."
            "Текст сохранён" -> "Tekst zapisany"
            "Текст пустой" -> "Tekst jest pusty"
            "Ключ хранится только в данных приложения на этом устройстве. GitHub Secret в APK не встраивается." ->
                "Klucz jest przechowywany tylko w danych aplikacji na tym urządzeniu. GitHub Secret nie jest osadzany w APK."
            "Инструкция голосу" -> "Instrukcja dla głosu"
            "Инструкция голосу Google" -> "Instrukcja dla głosu Google"
            "Для Azure используется отдельный Speech key и region. Если создать ресурс Free (F0), стандартные Neural-голоса можно тестировать в бесплатной квоте." ->
                "Azure używa osobnego klucza Speech i regionu. W zasobie Free (F0) standardowe głosy Neural można testować w bezpłatnym limicie."
            "Region — точный идентификатор из Azure Location/Region: только латинские буквы и цифры без пробелов. По умолчанию: switzerlandnorth." ->
                "Region to dokładny identyfikator Azure Location/Region: tylko małe litery łacińskie i cyfry, bez spacji. Domyślnie: switzerlandnorth."
            "Google Gemini TTS поддерживает русский и имеет бесплатный Developer API tier. API key создаётся в Google AI Studio. Внутри APK ключ не хранится." ->
                "Google Gemini TTS obsługuje rosyjski i ma bezpłatny poziom Developer API. Klucz API tworzy się w Google AI Studio. Klucz nie jest przechowywany w APK."
            "RHVoice — бесплатно, офлайн" -> "RHVoice — bezpłatny, offline"
            "RHVoice — установить бесплатно" -> "RHVoice — zainstaluj bezpłatnie"
            "Microsoft Edge — бесплатно" -> "Microsoft Edge — bezpłatny"
            "Silero v5.5 — эксперимент" -> "Silero v5.5 — eksperyment"
            "Microsoft Azure — нужен credential" -> "Microsoft Azure — wymagane dane dostępowe"
            else -> null
        }
}
