package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetsTest {
    @Test fun roundTripKeepsLinesAndSymbols() {
        val list = listOf(Snippet("Bank", "Pixels Limited\nAccount: 0123\nBank: Example"), Snippet("Sign-off", "Yours faithfully,"))
        assertEquals(list, Snippets.decode(Snippets.encode(list)))
    }

    @Test fun blankTextIsRefusedAndNameFallsBackToFirstLine() {
        assertNull(Snippets.clean("x", "   "))
        assertEquals("Dear Sir", Snippets.clean("", "Dear Sir\nthanks")!!.name)
    }

    @Test fun controlCharactersCannotBreakTheList() {
        val s = Snippets.clean("a\u001Eb", "t\u001Fx")!!
        assertEquals(listOf(s), Snippets.decode(Snippets.encode(listOf(s))))
    }

    @Test fun sameNameReplacesAndGoesFirst() {
        val l = Snippets.add(listOf(Snippet("A", "1"), Snippet("B", "2")), Snippet("a", "3"))
        assertEquals(listOf(Snippet("a", "3"), Snippet("B", "2")), l)
    }

    @Test fun garbageDecodesToNothing() {
        assertEquals(emptyList<Snippet>(), Snippets.decode("nonsense"))
        assertEquals(emptyList<Snippet>(), Snippets.decode(null))
    }
}
