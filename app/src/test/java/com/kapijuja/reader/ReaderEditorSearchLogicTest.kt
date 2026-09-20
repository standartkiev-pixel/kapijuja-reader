package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderEditorSearchLogicTest {
    @Test
    fun nextFindsForwardAndWraps() {
        val text = "Alpha beta alpha"
        assertEquals(
            11,
            ReaderEditorSearchLogic.next(
                text = text,
                query = "alpha",
                from = 1
            )
        )
        assertEquals(
            0,
            ReaderEditorSearchLogic.next(
                text = text,
                query = "alpha",
                from = 16
            )
        )
    }

    @Test
    fun previousFindsBackwardAndWraps() {
        val text = "Alpha beta alpha"
        assertEquals(
            0,
            ReaderEditorSearchLogic.previous(
                text = text,
                query = "alpha",
                before = 11
            )
        )
        assertEquals(
            11,
            ReaderEditorSearchLogic.previous(
                text = text,
                query = "alpha",
                before = 0
            )
        )
    }

    @Test
    fun searchIsCaseInsensitiveAndBlankQueryIsIgnored() {
        assertEquals(
            4,
            ReaderEditorSearchLogic.next(
                text = "abc ТЕСТ def",
                query = "тест",
                from = 0
            )
        )
        assertNull(
            ReaderEditorSearchLogic.next(
                text = "abc",
                query = "   ",
                from = 0
            )
        )
    }
}
