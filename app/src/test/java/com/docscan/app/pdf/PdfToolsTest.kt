package com.docscan.app.pdf

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PdfToolsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tools by lazy { PdfTools(ApplicationProvider.getApplicationContext()) }

    /** Blank pages whose widths (100, 101, …) identify them. */
    private fun makePdf(name: String, pages: Int, firstWidth: Float = 100f): File {
        val f = File(tmp.root, name)
        PDDocument().use { d ->
            for (i in 0 until pages) d.addPage(PDPage(PDRectangle(firstWidth + i, 200f)))
            d.save(f)
        }
        return f
    }

    private fun widths(f: File, password: String = ""): List<Int> =
        PDDocument.load(f, password).use { d -> (0 until d.numberOfPages).map { d.getPage(it).mediaBox.width.toInt() } }

    @Test fun rangesParse() {
        assertEquals(listOf(0, 1, 2, 4), PageRanges.parse("1-3, 5", 6))
        assertEquals(listOf(4, 5), PageRanges.parse("5-", 6))
        assertEquals(listOf(0, 1), PageRanges.parse("-2", 6))
        assertEquals(listOf(2, 0), PageRanges.parse("3,1", 6))
        assertNull(PageRanges.parse("7", 6))
        assertNull(PageRanges.parse("3-1", 6))
        assertNull(PageRanges.parse("a", 6))
        assertNull(PageRanges.parse("", 6))
    }

    @Test fun mergeKeepsOrder() {
        val a = makePdf("a.pdf", 2, 100f)
        val b = makePdf("b.pdf", 3, 300f)
        val out = File(tmp.root, "m.pdf")
        tools.merge(listOf(a, b), out)
        assertEquals(listOf(100, 101, 300, 301, 302), widths(out))
    }

    @Test fun pickReorders() {
        val a = makePdf("a.pdf", 4)
        val out = File(tmp.root, "p.pdf")
        tools.pick(a, listOf(3, 0, 1), out)
        assertEquals(listOf(103, 100, 101), widths(out))
    }

    @Test fun splitEveryChunks() {
        val a = makePdf("a.pdf", 5)
        val parts = tools.splitEvery(a, 2, tmp.newFolder("o"), "x")
        assertEquals(3, parts.size)
        assertEquals(listOf(104), widths(parts[2]))
    }

    @Test fun rotateAccumulates() {
        val a = makePdf("a.pdf", 2)
        val out = File(tmp.root, "r.pdf")
        tools.rotate(a, listOf(1), 90, out)
        val out2 = File(tmp.root, "r2.pdf")
        tools.rotate(out, listOf(1), -180, out2)
        PDDocument.load(out2).use { d ->
            assertEquals(0, d.getPage(0).rotation)
            assertEquals(270, d.getPage(1).rotation)
        }
    }

    @Test fun protectRoundTrip() {
        val a = makePdf("a.pdf", 2)
        val locked = File(tmp.root, "l.pdf")
        tools.protect(a, "secret1", locked)
        assertTrue(tools.isEncrypted(locked))
        assertThrows(PdfPasswordException::class.java) { tools.pageCount(locked, "wrong") }
        assertEquals(2, tools.pageCount(locked, "secret1"))
        val open = File(tmp.root, "u.pdf")
        tools.unprotect(locked, "secret1", open)
        assertEquals(listOf(100, 101), widths(open))
    }
}
