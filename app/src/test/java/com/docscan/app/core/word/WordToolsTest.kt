package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Test

class WordToolsTest {
    @Test fun cases() {
        val s = "hello wORLD. this is it! ok"
        assertEquals("hello world. this is it! ok", TextCase.apply(s, CaseMode.LOWER))
        assertEquals("HELLO WORLD. THIS IS IT! OK", TextCase.apply(s, CaseMode.UPPER))
        assertEquals("Hello World. This Is It! Ok", TextCase.apply(s, CaseMode.CAPITALIZE))
        assertEquals("Hello world. This is it! Ok", TextCase.apply(s, CaseMode.SENTENCE))
        assertEquals("HELLO World. THIS IS IT! OK", TextCase.apply(s, CaseMode.TOGGLE))
    }

    @Test fun capitalizeKeepsApostrophes() {
        assertEquals("Don't Stop", TextCase.apply("don't stop", CaseMode.CAPITALIZE))
    }

    @Test fun lengthNeverChanges() {
        val s = "Straße"
        assertEquals(s.length, TextCase.apply(s, CaseMode.UPPER).length)
    }

    @Test fun stats() {
        val st = WordStats.of("One two\nthree\n\nfour five six", 4)
        assertEquals(6, st.words)
        assertEquals(3, st.paragraphs)
        assertEquals(22, st.charsNoSpaces)
        assertEquals(25, st.charsWithSpaces)
    }

    @Test fun outline() {
        val o = Outline.build(
            listOf("My Title", "intro", "Chapter 1", "text", "Sub", "  "),
            listOf(ParaStyle.TITLE, ParaStyle.NORMAL, ParaStyle.H1, ParaStyle.NORMAL, ParaStyle.H2, ParaStyle.H2),
        )
        assertEquals(listOf(0, 1, 2), o.map { it.level })
        assertEquals(listOf(0, 15, 30), o.map { it.offset })
    }
}
