package com.docscan.app.export

import com.docscan.app.core.word.Block
import com.docscan.app.core.word.ListKind
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.WordDoc
import com.docscan.app.ui.write.newTable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WordPdfTest {
    @Test fun writesSomethingForMixedContent() {
        val blocks = listOf(
            Block.Para(Paragraph(listOf(Run("Title")))),
            Block.Para(Paragraph(listOf(Run("item")), list = ListKind.BULLET)),
            Block.Table(newTable(3, 3, true, 9000)),
            Block.PageBreak,
            Block.Para(Paragraph(listOf(Run("Last page")))),
        )
        val out = ByteArrayOutputStream()
        WordPdf.write(WordDoc("t", blocks), out)
        assertTrue(out.size() > 0)
    }
}
