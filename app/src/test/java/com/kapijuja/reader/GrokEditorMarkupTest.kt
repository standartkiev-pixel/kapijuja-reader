package com.kapijuja.reader

import org.junit.Assert.assertEquals
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

    @Test
    fun insertTokenAddsReadableSpacing() {
        val result = GrokEditorMarkup.insertToken("до после", 2, 2, "[pause]")
        assertEquals("до [pause] после", result.text)
        assertTrue(result.selectionStart == result.selectionEnd)
    }
}
