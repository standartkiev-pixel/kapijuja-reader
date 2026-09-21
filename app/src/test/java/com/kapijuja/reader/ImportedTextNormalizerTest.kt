package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedTextNormalizerTest {
    @Test
    fun pdfVisualLinesAreMergedInsideParagraphs() {
        val raw = "Это первая строка\nпродолжение той же мысли.\n\nНовый абзац."
        assertEquals(
            "Это первая строка продолжение той же мысли.\n\nНовый абзац.",
            ImportedTextNormalizer.normalize(raw, mergeVisualLines = true)
        )
    }

    @Test
    fun pdfHyphenatedWordAcrossLineBreakIsRejoined() {
        val raw = "длинное предло-\nжение продолжается"
        assertEquals(
            "длинное предложение продолжается",
            ImportedTextNormalizer.normalize(raw, mergeVisualLines = true)
        )
    }

    @Test
    fun listItemsRemainSeparateParagraphs() {
        val raw = "Введение\n\n- первый пункт\n- второй пункт"
        assertEquals(
            "Введение\n\n- первый пункт\n\n- второй пункт",
            ImportedTextNormalizer.normalize(raw, mergeVisualLines = true)
        )
    }

    @Test
    fun wordParagraphBreaksArePreservedWithoutWhitespaceNoise() {
        val raw = " Первый   абзац \r\n\r\n Второй\tабзац "
        assertEquals(
            "Первый абзац\n\nВторой абзац",
            ImportedTextNormalizer.normalize(raw, mergeVisualLines = false)
        )
    }
}
