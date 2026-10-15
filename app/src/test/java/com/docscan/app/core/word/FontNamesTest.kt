package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontNamesTest {
    @Test fun cleansPdfFontNames() {
        assertEquals("Bookman Old Style", FontNames.family("ABCDEF+BookmanOldStyle-BoldItalic"))
        assertEquals("Arial", FontNames.family("ArialMT"))
        assertEquals("Times New Roman", FontNames.family("TimesNewRomanPS-BoldMT"))
        assertEquals("Segoe UI", FontNames.family("XYZABC+SegoeUI,Bold"))
        assertEquals("Cooper Black", FontNames.family("Cooper Black"))
        assertNull(FontNames.family("F1"))
        assertNull(FontNames.family("TT12"))
        assertNull(FontNames.family(null))
    }
}
