package com.docscan.app.ui.write

import org.junit.Assert.assertEquals
import org.junit.Test

class MyStylesTest {
    @Test fun roundTrip() {
        val st = MyStyles.Style("Chapter title", listOf(CharAttr.Bold, CharAttr.Size(18f), CharAttr.Color(0x1F4E79), CharAttr.Font("Cambria")))
        val back = MyStyles.decode(MyStyles.encode(listOf(st, MyStyles.Style("Note", listOf(CharAttr.Italic)))))
        assertEquals(2, back.size)
        assertEquals("Chapter title", back[0].name)
        assertEquals(st.attrs, back[0].attrs)
        assertEquals(listOf<CharAttr>(CharAttr.Italic), back[1].attrs)
    }

    @Test fun oddNamesAreKeptOnOneLine() {
        val back = MyStyles.decode(MyStyles.encode(listOf(MyStyles.Style("a\tb\nc", listOf(CharAttr.Bold)))))
        assertEquals(1, back.size)
        assertEquals("a b c", back[0].name)
    }

    @Test fun emptyListDecodesToEmpty() {
        assertEquals(0, MyStyles.decode("").size)
    }
}
