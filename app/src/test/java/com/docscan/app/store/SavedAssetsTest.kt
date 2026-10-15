package com.docscan.app.store

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedAssetsTest {
    @Test fun namesKeepLettersAndDropSymbols() {
        assertEquals("Kelvin Codes", SavedAssets.cleanName("  Kelvin Codes "))
        assertEquals("Ofis dinmu", SavedAssets.cleanName("Ofis/ dinmu*"))
        assertEquals("Untitled", SavedAssets.cleanName("///"))
        assertEquals(40, SavedAssets.cleanName("a".repeat(80)).length)
    }
}
