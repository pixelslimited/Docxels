package com.docscan.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMetaTest {
    @Test fun roundTrip() {
        val map = mapOf(
            LibraryMeta.key(DocKind.DOCX, "a") to ItemMeta(favourite = true, folder = "Clients", tags = setOf("urgent", "2026"), trashedAt = 0L),
            LibraryMeta.key(DocKind.SCAN, "b") to ItemMeta(trashedAt = 123456L),
        )
        assertEquals(map, LibraryMeta.parse(LibraryMeta.serialize(map)))
    }

    @Test fun oddInputIsIgnored() {
        assertTrue(LibraryMeta.parse("").isEmpty())
        assertTrue(LibraryMeta.parse("garbage\nmore").isEmpty())
    }

    @Test fun namesAreCleaned() {
        assertEquals("a b", LibraryMeta.cleanName(" a,b "))
        assertEquals(40, LibraryMeta.cleanName("x".repeat(80)).length)
    }

    @Test fun defaultMetaIsNotStored() {
        assertTrue(ItemMeta().isDefault)
        assertEquals(false, ItemMeta(favourite = true).isDefault)
    }
}
