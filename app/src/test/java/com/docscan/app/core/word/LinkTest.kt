package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {
    @Test fun safety() {
        assertEquals("https://example.com", LinkSafety.normalize("example.com"))
        assertEquals("mailto:a@b.ng", LinkSafety.normalize("a@b.ng"))
        assertEquals("tel:+2348012345678", LinkSafety.normalize("tel:+2348012345678"))
        assertNull(LinkSafety.normalize("javascript:alert(1)"))
        assertNull(LinkSafety.normalize("file:///sdcard/x"))
        assertNull(LinkSafety.normalize("intent://x#Intent;end"))
        assertNull(LinkSafety.normalize("two words.com"))
        assertNull(LinkSafety.normalize(""))
    }

    @Test fun roundTripKeepsLinks() {
        val para = Paragraph(
            listOf(
                Run("Visit "),
                Run("our site", RunStyle(link = "https://docxels.pixelslimited.ng/")),
                Run(" or "),
                Run("mail us", RunStyle(bold = true, link = "mailto:docxels@pixelslimited.ng")),
            ),
        )
        val bytes = DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(para))))
        val back = (DocxReader.read(bytes).blocks[0] as Block.Para).paragraph
        assertEquals("Visit our site or mail us", back.text)
        val links = back.runs.mapNotNull { it.style.link }
        assertEquals(listOf("https://docxels.pixelslimited.ng/", "mailto:docxels@pixelslimited.ng"), links)
        assertTrue(back.runs.first { it.style.link?.startsWith("mailto") == true }.style.bold)
        assertNull(back.runs.first { it.text.startsWith("Visit") }.style.link)
    }

    @Test fun unsafeLinksAreNotWritten() {
        val para = Paragraph(listOf(Run("click", RunStyle(link = "javascript:alert(1)"))))
        val back = (DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(para))))).blocks[0] as Block.Para).paragraph
        assertNull(back.runs.single().style.link)
    }
}
