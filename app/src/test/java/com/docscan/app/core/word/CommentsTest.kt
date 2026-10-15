package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentsTest {
    private fun doc() = WordDoc(
        "T",
        listOf(
            Block.Para(Paragraph(listOf(Run("Before "), Run("the claim", RunStyle(bold = true, comment = 3)), Run(" after")))),
            Block.Para(Paragraph(listOf(Run("Second", RunStyle(comment = 7))))),
        ),
        comments = listOf(DocComment(3, "Aisha Bello", "Please add a source.\nThanks"), DocComment(7, "Kelvin Codes", "Check <this> & that")),
    )

    @Test fun commentsSurviveSaveAndOpen() {
        val back = DocxReader.read(DocxWriter.toBytes(doc()))
        assertEquals(listOf("Aisha Bello" to "Please add a source.\nThanks", "Kelvin Codes" to "Check <this> & that"), back.comments.map { it.author to it.text })
        val p1 = (back.blocks[0] as Block.Para).paragraph
        val tagged = p1.runs.filter { it.style.comment != null }
        assertEquals("the claim", tagged.joinToString("") { it.text })
        assertEquals(true, tagged.all { it.style.bold })
        assertNull(p1.runs.first { it.text.startsWith("Before") }.style.comment)
        assertNull(p1.runs.first { it.text.startsWith(" after") }.style.comment)
        val id1 = tagged.first().style.comment
        val p2 = (back.blocks[1] as Block.Para).paragraph
        assertEquals(true, p2.runs.single().style.comment != null && p2.runs.single().style.comment != id1)
    }

    @Test fun commentWithoutTextIsDropped() {
        val d = doc().copy(comments = listOf(DocComment(3, "A", "x")))
        val back = DocxReader.read(DocxWriter.toBytes(d))
        assertEquals(1, back.comments.size)
        assertNull((back.blocks[1] as Block.Para).paragraph.runs.single().style.comment)
    }
}
