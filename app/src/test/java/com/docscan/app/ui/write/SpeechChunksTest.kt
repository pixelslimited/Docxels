package com.docscan.app.ui.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test fun shortTextIsOnePiece() = assertEquals(listOf("Hello there."), speechChunks("  Hello   there. ", 4000))

    @Test fun longTextIsSplitAtSentencesAndKeepsEveryWord() {
        val text = (1..200).joinToString(" ") { "Sentence number $it." }
        val parts = speechChunks(text, 300)
        assertTrue(parts.size > 5)
        assertTrue(parts.all { it.length <= 300 })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test fun aWordLongerThanTheLimitStillMakesProgress() {
        val parts = speechChunks("x".repeat(1000), 200)
        assertEquals(1000, parts.sumOf { it.length })
    }
}
