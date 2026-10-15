package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    @Test fun everyTemplateSurvivesSaveAndOpen() {
        for (t in Templates.all) {
            val doc = t.build()
            val back = DocxReader.read(DocxWriter.toBytes(doc), t.label)
            val before = doc.blocks.filterIsInstance<Block.Para>().joinToString("\n") { it.paragraph.text }
            val after = back.blocks.filterIsInstance<Block.Para>().joinToString("\n") { it.paragraph.text }
            assertEquals("text of ${t.id}", before, after)
            assertEquals("tables of ${t.id}", doc.blocks.count { it is Block.Table }, back.blocks.count { it is Block.Table })
        }
    }

    @Test fun idsAreUniqueAndFindable() {
        assertEquals(Templates.all.size, Templates.all.map { it.id }.toSet().size)
        Templates.all.forEach { assertTrue(Templates.byId(it.id) === it) }
    }

    @Test fun invoiceTableFitsTheTextWidth() {
        val t = Templates.invoice.build().blocks.filterIsInstance<Block.Table>().single().table
        val width = PageSetup().let { it.widthTwips - it.marginLeftTwips - it.marginRightTwips }
        assertTrue(t.columnWidthsTwips.sum() <= width)
    }
}
