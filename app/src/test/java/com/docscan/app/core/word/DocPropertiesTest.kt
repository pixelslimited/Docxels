package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Test

class DocPropertiesTest {
    private fun roundTrip(d: WordDoc) = DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(d)))

    @Test fun propertiesSurviveSaving() {
        val back = roundTrip(WordDoc(title = "Proposal", author = "Kelvin Codes", subject = "Solar & CCTV", keywords = "solar, drc"))
        assertEquals("Proposal", back.title)
        assertEquals("Kelvin Codes", back.author)
        assertEquals("Solar & CCTV", back.subject)
        assertEquals("solar, drc", back.keywords)
    }

    @Test fun removedPersonalDataStaysRemoved() {
        val back = roundTrip(WordDoc(title = "T", author = ""))
        assertEquals("", back.author)
    }
}
