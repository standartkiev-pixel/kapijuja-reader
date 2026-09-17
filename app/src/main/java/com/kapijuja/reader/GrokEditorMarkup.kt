package com.kapijuja.reader

/**
 * Pure text transformations used by the Grok-only editor toolbar.
 *
 * Keep this Android-free so stress placement and speech-tag insertion can be
 * unit-tested without an Activity or device.
 */
object GrokEditorMarkup {
    const val COMBINING_ACUTE = '\u0301'

    data class EditResult(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    data class Action(
        val id: String,
        val labelRu: String,
        val labelPl: String,
        val labelEn: String,
        val kind: Kind,
        val token: String = ""
    )

    enum class Kind {
        STRESS,
        SECTION,
        WRAP,
        INSERT
    }

    val actions: List<Action> = listOf(
        Action(
            id = "stress",
            labelRu = "´  Ударение — курсор сразу после гласной",
            labelPl = "´  Akcent — kursor zaraz po samogłosce",
            labelEn = "´  Stress — cursor immediately after the vowel",
            kind = Kind.STRESS
        ),
        Action("wrap_section", "— Выделенный фрагмент —", "— Zaznaczony fragment —", "— Selected text —", Kind.SECTION),
        Action("emphasis", "Акцент / выделение", "Akcent / podkreślenie", "Emphasis", Kind.WRAP, "emphasis"),
        Action("soft", "Тише", "Ciszej", "Soft", Kind.WRAP, "soft"),
        Action("whisper", "Шёпот", "Szept", "Whisper", Kind.WRAP, "whisper"),
        Action("loud", "Громче", "Głośniej", "Loud", Kind.WRAP, "loud"),
        Action("build_intensity", "Усиление / нарастание", "Narastanie intensywności", "Build intensity", Kind.WRAP, "build-intensity"),
        Action("decrease_intensity", "Ослабление / затухание", "Zmniejszanie intensywności", "Decrease intensity", Kind.WRAP, "decrease-intensity"),
        Action("slow", "Замедлить", "Zwolnij", "Slow down", Kind.WRAP, "slow"),
        Action("fast", "Ускорить", "Przyspiesz", "Speed up", Kind.WRAP, "fast"),
        Action("higher_pitch", "Выше тон", "Wyższy ton", "Higher pitch", Kind.WRAP, "higher-pitch"),
        Action("lower_pitch", "Ниже тон", "Niższy ton", "Lower pitch", Kind.WRAP, "lower-pitch"),
        Action("sing_song", "Напевно", "Śpiewnie", "Sing-song", Kind.WRAP, "sing-song"),
        Action("singing", "Пение", "Śpiew", "Singing", Kind.WRAP, "singing"),
        Action("insert_section", "— Вставить в позицию курсора —", "— Wstaw przy kursorze —", "— Insert at cursor —", Kind.SECTION),
        Action("pause", "Короткая пауза", "Krótka pauza", "Pause", Kind.INSERT, "[pause]"),
        Action("long_pause", "Длинная пауза", "Długa pauza", "Long pause", Kind.INSERT, "[long-pause]"),
        Action("breath", "Дыхание", "Oddech", "Breath", Kind.INSERT, "[breath]"),
        Action("inhale", "Вдох", "Wdech", "Inhale", Kind.INSERT, "[inhale]"),
        Action("exhale", "Выдох", "Wydech", "Exhale", Kind.INSERT, "[exhale]"),
        Action("sigh", "Вздох", "Westchnienie", "Sigh", Kind.INSERT, "[sigh]"),
        Action("laugh", "Смех", "Śmiech", "Laugh", Kind.INSERT, "[laugh]"),
        Action("chuckle", "Смешок", "Chichot", "Chuckle", Kind.INSERT, "[chuckle]"),
        Action("giggle", "Хихиканье", "Chichotanie", "Giggle", Kind.INSERT, "[giggle]"),
        Action("cry", "Плач", "Płacz", "Cry", Kind.INSERT, "[cry]"),
        Action("tsk", "Цоканье / tsk", "Cyknięcie / tsk", "Tsk", Kind.INSERT, "[tsk]"),
        Action("tongue_click", "Щелчок языком", "Kliknięcie językiem", "Tongue click", Kind.INSERT, "[tongue-click]"),
        Action("lip_smack", "Чмок / lip-smack", "Mlasknięcie", "Lip smack", Kind.INSERT, "[lip-smack]"),
        Action("hum_tune", "Напев без слов", "Nucenie", "Hum tune", Kind.INSERT, "[hum-tune]")
    )

    fun addStress(
        text: String,
        selectionStart: Int,
        selectionEnd: Int
    ): EditResult {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        val lo = minOf(start, end)
        val hi = maxOf(start, end)

        val vowelIndex = when {
            hi - lo == 1 && isVowel(text[lo]) -> lo
            hi == lo -> vowelBeforeCaret(text, lo)
            else -> -1
        }

        require(vowelIndex >= 0) {
            "Поставьте курсор сразу после гласной буквы или выделите одну гласную."
        }

        val left = wordStart(text, vowelIndex)
        val right = wordEnd(text, vowelIndex)
        require(left < right) { "Не удалось определить слово для ударения." }

        val word = text.substring(left, right)
        val marksBeforeVowel =
            text.substring(left, vowelIndex).count { it == COMBINING_ACUTE }
        val cleanVowelOffset = vowelIndex - left - marksBeforeVowel
        val cleanWord = word.filterNot { it == COMBINING_ACUTE }

        require(cleanVowelOffset in cleanWord.indices && isVowel(cleanWord[cleanVowelOffset])) {
            "Ударение можно поставить только на гласную букву."
        }

        val insertAt = cleanVowelOffset + 1
        val stressedWord =
            cleanWord.substring(0, insertAt) +
                COMBINING_ACUTE +
                cleanWord.substring(insertAt)

        val updated =
            text.substring(0, left) + stressedWord + text.substring(right)
        val caret = left + insertAt + 1

        return EditResult(updated, caret, caret)
    }

    fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        tag: String
    ): EditResult {
        val lo = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val hi = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        require(lo < hi) { "Сначала выделите слово или фрагмент текста." }
        require(tag.matches(Regex("[a-z-]+"))) { "Некорректный Grok-тег." }

        val open = "<$tag>"
        val close = "</$tag>"
        val updated =
            text.substring(0, lo) +
                open + text.substring(lo, hi) + close +
                text.substring(hi)

        // Keep the original text selected inside the new wrapper so another
        // Grok style can be nested without re-selecting the phrase.
        val innerStart = lo + open.length
        val innerEnd = innerStart + (hi - lo)
        return EditResult(updated, innerStart, innerEnd)
    }

    fun insertToken(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        token: String
    ): EditResult {
        val lo = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val hi = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        require(token.startsWith("[") && token.endsWith("]")) {
            "Некорректная Grok-команда."
        }

        val prefixSpace = lo > 0 && !text[lo - 1].isWhitespace()
        val suffixSpace = hi < text.length && !text[hi].isWhitespace()
        val inserted =
            (if (prefixSpace) " " else "") +
                token +
                (if (suffixSpace) " " else "")
        val updated = text.substring(0, lo) + inserted + text.substring(hi)
        val caret = lo + inserted.length
        return EditResult(updated, caret, caret)
    }

    fun label(action: Action, language: String): String =
        when (language) {
            SettingsStore.UI_LANGUAGE_RU -> action.labelRu
            SettingsStore.UI_LANGUAGE_PL -> action.labelPl
            else -> action.labelEn
        }

    private fun vowelBeforeCaret(text: String, caret: Int): Int {
        if (caret <= 0 || text.isEmpty()) return -1

        var index = caret - 1
        if (text[index] == COMBINING_ACUTE) index -= 1
        return if (index >= 0 && isVowel(text[index])) index else -1
    }

    private fun wordStart(text: String, index: Int): Int {
        var pos = index
        while (pos > 0 && isWordPart(text[pos - 1])) pos -= 1
        return pos
    }

    private fun wordEnd(text: String, index: Int): Int {
        var pos = index + 1
        while (pos < text.length && isWordPart(text[pos])) pos += 1
        return pos
    }

    private fun isWordPart(ch: Char): Boolean =
        ch.isLetter() || ch == COMBINING_ACUTE

    private fun isVowel(ch: Char): Boolean =
        ch in "аеёиоуыэюяАЕЁИОУЫЭЮЯіїєІЇЄaeiouyAEIOUY"
}
