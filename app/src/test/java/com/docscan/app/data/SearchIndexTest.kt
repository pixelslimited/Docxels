package com.docscan.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchIndexTest {
    @Test fun roundTrip() {
        val m = mapOf("DOCX:a" to SearchIndex.Entry(5L, "hello   world\nsecond line"), "SCAN:b" to SearchIndex.Entry(9L, "x"))
        val back = SearchIndex.parse(SearchIndex.serialize(m))
        assertEquals("hello world second line", back["DOCX:a"]!!.text)
        assertEquals(5L, back["DOCX:a"]!!.stamp)
        assertEquals(2, back.size)
    }

    @Test fun junkIsIgnored() = assertEquals(0, SearchIndex.parse("nope\n\nstill nope").size)
}
