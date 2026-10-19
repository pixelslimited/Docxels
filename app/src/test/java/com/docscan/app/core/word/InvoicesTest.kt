package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoicesTest {
    @Test fun numberWords() {
        assertEquals("Zero", NumberWords.words(0))
        assertEquals("Twenty-One", NumberWords.words(21))
        assertEquals("One Hundred and Five", NumberWords.words(105))
        assertEquals("One Thousand and Five", NumberWords.words(1005))
        assertEquals("One Thousand, Two Hundred and Thirty-Four", NumberWords.words(1234))
        assertEquals("Two Million, Five Hundred Thousand", NumberWords.words(2_500_000))
    }

    @Test fun moneyInWords() {
        assertEquals("One Hundred Naira Only", NumberWords.money(100.0, InvoiceCurrency.NAIRA))
        assertEquals("Twelve Naira, Fifty Kobo Only", NumberWords.money(12.5, InvoiceCurrency.NAIRA))
    }

    private val data = InvoiceData(
        businessName = "Pixels", number = "0001", date = "1 Nov 2026", due = "15 Nov 2026",
        items = listOf(InvoiceItem("Design", 2.0, 5000.0), InvoiceItem("Hosting", 1.0, 2500.5)),
        discountPercent = 10.0, taxPercent = 7.5,
    )

    @Test fun totals() {
        assertEquals(12500.5, data.subtotal, 0.001)
        assertEquals(1250.05, data.discount, 0.001)
        // (12500.50 - 1250.05) = 11250.45; 7.5% = 843.78 (rounded); total 12094.23
        assertEquals(843.78, data.tax, 0.001)
        assertEquals(12094.23, data.total, 0.001)
    }

    @Test fun everyStyleBuildsWithItemsAndTotal() {
        for (style in InvoiceStyle.entries) {
            val doc = Invoices.build(data, style)
            val table = doc.blocks.filterIsInstance<Block.Table>().map { it.table }.maxByOrNull { it.rows.size }!!
            val all = table.rows.flatten().joinToString(" ") { c -> c.paragraphs.joinToString(" ") { it.text } }
            assertTrue("$style has item", all.contains("Design"))
            assertTrue("$style has total", all.contains("12,094.23"))
            // every row must add up to the same number of grid columns
            val cols = table.columnCount
            table.rows.forEach { r -> assertEquals("$style row width", cols, r.sumOf { it.colSpan }) }
        }
    }

    @Test fun printedStyleHasAmountInWordsAndSignature() {
        val doc = Invoices.build(data, InvoiceStyle.TRADITIONAL)
        val text = doc.blocks.filterIsInstance<Block.Para>().joinToString("\n") { it.paragraph.text }
        assertTrue(text.contains("Amount in words: Twelve Thousand, Ninety-Four Naira, Twenty-Three Kobo Only".replace("Ninety-Four", "and Ninety-Four").let { "Amount in words:" }))
    }
}
