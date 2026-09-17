package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrokEditorMarkupTest {
    @Test
    fun addStressRemovesPreviousStressInSameWord() {
        val source = "молоко́"
        val targetCaret = 4 // after the second 'о'

        val result = GrokEditorMarkup.addStress(source, targetCaret, targetCaret)

        assertEquals("моло́ко", result.text)
        assertEquals(1, result.text.count { it == GrokEditorMarkup.COMBINING_ACUTE })
    }

    @Test
    fun addStressDoesNotRemoveStressFromAnotherWord() {
        val source = "му́ка молоко"
        val caret = source.length

        val result = GrokEditorMarkup.addStress(source, caret, caret)

        assertEquals("му́ка молоко́", result.text)
        assertEquals(2, result.text.count { it == GrokEditorMarkup.COMBINING_ACUTE })
    }

    @Test
    fun addStressOnAlreadyStressedVowelIsIdempotent() {
        val source = "сло́во"
        val caretAfterAcute = 4

        val result = GrokEditorMarkup.addStress(source, caretAfterAcute, caretAfterAcute)

        assertEquals(source, result.text)
        assertEquals(caretAfterAcute, result.selectionStart)
    }

    @Test
    fun addStressAcceptsSelectedVowel() {
        val result = GrokEditorMarkup.addStress("молоко", 3, 4)
        assertEquals("моло́ко", result.text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addStressRejectsConsonant() {
        GrokEditorMarkup.addStress("слово", 1, 1)
    }

    @Test
    fun wrapSelectionKeepsOriginalSelectionInsideTags() {
        val result = GrokEditorMarkup.wrapSelection("читать медленно", 7, 15, "slow")
        assertEquals("читать <slow>медленно</slow>", result.text)
        assertEquals("медленно", result.text.substring(result.selectionStart, result.selectionEnd))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrapSelectionRejectsUnknownTag() {
        GrokEditorMarkup.wrapSelection("текст", 0, 5, "not-a-grok-tag")
    }

    @Test
    fun insertTokenAddsReadableSpacing() {
        val result = GrokEditorMarkup.insertToken("до после", 2, 2, "[pause]")
        assertEquals("до [pause] после", result.text)
        assertTrue(result.selectionStart == result.selectionEnd)
    }

    @Test
    fun playbackInsideStyleMovesBackToOpeningTag() {
        val source = "до <slow>очень медленно и спокойно</slow> после"
        val cursor = source.indexOf("спокойно")

        val start = GrokEditorMarkup.playbackStartOffset(source, cursor)

        assertEquals(source.indexOf("<slow>"), start)
    }

    @Test
    fun balancedChunkEndExtendsPastPreferredEndToClosingTag() {
        val source = "начало <slow>раз два три четыре пять</slow> конец"
        val preferred = source.indexOf("три")

        val end = GrokEditorMarkup.balancedChunkEnd(
            text = source,
            start = 0,
            preferredEnd = preferred,
            hardEnd = source.length
        )

        assertTrue(end > source.indexOf("</slow>") + "</slow>".length - 1)
        assertFalse(GrokEditorMarkup.hasUnclosedWrappingTag(source.substring(0, end)))
    }

    @Test
    fun nestedTagsRemainBalanced() {
        val source = "<slow>раз <emphasis>два три</emphasis> четыре</slow> конец"
        assertFalse(GrokEditorMarkup.hasUnclosedWrappingTag(source))
        assertTrue(GrokEditorMarkup.hasUnclosedWrappingTag(source.substringBefore("</slow>")))
    }
}
