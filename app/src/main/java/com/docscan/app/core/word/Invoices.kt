package com.docscan.app.core.word

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** The three looks an invoice can have. */
enum class InvoiceStyle(val label: String, val blurb: String) {
    TRADITIONAL("Printed", "The familiar printed invoice: boxed, ruled table, amount in words, signature lines"),
    ONLINE("Online", "Clean modern invoice with a colour header, striped rows and a highlighted amount due"),
    MINIMAL("Minimal", "Plain and compact: light header, no ruled lines"),
}

class InvoiceItem(val description: String, val qty: Double, val unitPrice: Double) {
    val amount: Double get() = qty * unitPrice
}

class InvoiceCurrency(val symbol: String, val major: String, val minor: String) {
    companion object {
        val NAIRA = InvoiceCurrency("₦", "Naira", "Kobo")
        val DOLLAR = InvoiceCurrency("$", "Dollars", "Cents")
        val POUND = InvoiceCurrency("£", "Pounds", "Pence")
        val EURO = InvoiceCurrency("€", "Euros", "Cents")
        val all = listOf(NAIRA, DOLLAR, POUND, EURO)
    }
}

class InvoiceData(
    val businessName: String = "",
    val businessContact: String = "",
    val customerName: String = "",
    val customerAddress: String = "",
    val number: String = "",
    val date: String = "",
    val due: String = "",
    val currency: InvoiceCurrency = InvoiceCurrency.NAIRA,
    val items: List<InvoiceItem> = emptyList(),
    val discountPercent: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxName: String = "VAT",
    val paymentDetails: String = "",
    val notes: String = "",
) {
    val subtotal: Double get() = items.sumOf { it.amount }
    val discount: Double get() = round2(subtotal * discountPercent / 100.0)
    val taxable: Double get() = subtotal - discount
    val tax: Double get() = round2(taxable * taxPercent / 100.0)
    val total: Double get() = round2(taxable + tax)
}

internal fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0

object NumberWords {
    private val ones = arrayOf(
        "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
        "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
    )
    private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun below1000(n: Int): String {
        val parts = ArrayList<String>()
        val h = n / 100
        val r = n % 100
        if (h > 0) parts += ones[h] + " Hundred"
        if (r > 0) {
            val t = if (r < 20) ones[r] else tens[r / 10] + (if (r % 10 > 0) "-" + ones[r % 10] else "")
            parts += if (h > 0) "and $t" else t
        }
        return parts.joinToString(" ")
    }

    /** A whole number in English words ("One Thousand, Two Hundred and Five"). Zero for 0. */
    fun words(n: Long): String {
        if (n == 0L) return "Zero"
        var rest = n
        val scales = listOf(1_000_000_000_000L to "Trillion", 1_000_000_000L to "Billion", 1_000_000L to "Million", 1_000L to "Thousand")
        val parts = ArrayList<String>()
        for ((size, name) in scales) {
            if (rest >= size) {
                parts += below1000((rest / size).toInt()) + " " + name
                rest %= size
            }
        }
        if (rest > 0) {
            val last = below1000(rest.toInt())
            // "One Thousand and Five" reads better than "One Thousand, Five".
            if (parts.isNotEmpty() && rest < 100 && !last.startsWith("and ")) parts += "and $last" else parts += last
        }
        return parts.joinToString(", ").replace(", and ", " and ")
    }

    /** "One Hundred Naira, Fifty Kobo Only". */
    fun money(amount: Double, c: InvoiceCurrency): String {
        val cents = (abs(amount) * 100.0).roundToLong()
        val major = cents / 100
        val minor = (cents % 100).toInt()
        val sb = StringBuilder(words(major) + " " + c.major)
        if (minor > 0) sb.append(", ").append(words(minor.toLong())).append(' ').append(c.minor)
        return sb.append(" Only").toString()
    }
}

object Invoices {
    private const val WIDTH = 9906 // A4 with 1000-twip side margins
    private const val ACCENT = 0x1687E8
    private val GREY = 0x666666

    fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

    private fun qtyText(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else q.toString().trimEnd('0').trimEnd('.')

    private fun p(text: String, bold: Boolean = false, size: Float? = null, color: Int? = null, align: Align = Align.LEFT, after: Float? = 0f, before: Float? = null, italic: Boolean = false) =
        Paragraph(listOf(Run(text, RunStyle(bold = bold, italic = italic, sizePt = size, color = color))), align = align, spaceAfterPt = after, spaceBeforePt = before)

    private fun lines(text: String, size: Float? = null, color: Int? = null, align: Align = Align.LEFT, bold: Boolean = false): List<Paragraph> =
        text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { p(it, size = size, color = color, align = align, bold = bold) }

    private fun cell(paras: List<Paragraph>, shading: Int? = null, span: Int = 1) = TableCell(paras.ifEmpty { listOf(Paragraph()) }, shading = shading, colSpan = span)
    private fun cell(text: String, bold: Boolean = false, align: Align = Align.LEFT, shading: Int? = null, color: Int? = null, size: Float? = null, span: Int = 1) =
        TableCell(listOf(p(text, bold = bold, align = align, color = color, size = size, after = 0f)), shading = shading, colSpan = span)

    /** Rows for the items table: (S/N), description, quantity, price, amount. */
    private fun itemRows(d: InvoiceData, serial: Boolean, stripe: Int?): List<List<TableCell>> {
        val items = d.items.ifEmpty { listOf(InvoiceItem("", 0.0, 0.0)) }
        return items.mapIndexed { i, it ->
            val fill = if (stripe != null && i % 2 == 1) stripe else null
            buildList {
                if (serial) add(cell("${i + 1}", align = Align.CENTER, shading = fill))
                add(cell(it.description, shading = fill))
                add(cell(qtyText(it.qty), align = Align.RIGHT, shading = fill))
                add(cell(money(it.unitPrice), align = Align.RIGHT, shading = fill))
                add(cell(money(it.amount), align = Align.RIGHT, shading = fill))
            }
        }
    }

    /** Subtotal / discount / tax / total rows; the label runs across all but the last column. */
    private fun totalRows(d: InvoiceData, cols: Int, shadeTotal: Int?, totalColor: Int?): List<List<TableCell>> {
        val sym = d.currency.symbol
        val rows = ArrayList<List<TableCell>>()
        fun add(label: String, value: String, strong: Boolean = false) {
            val shade = if (strong) shadeTotal else null
            val color = if (strong) totalColor else null
            rows += listOf(cell(label, bold = strong, align = Align.RIGHT, span = cols - 1, shading = shade, color = color),
                cell(value, bold = strong, align = Align.RIGHT, shading = shade, color = color))
        }
        add("Subtotal", sym + money(d.subtotal))
        if (d.discountPercent > 0) add("Discount (${qtyText(d.discountPercent)}%)", "-" + sym + money(d.discount))
        if (d.taxPercent > 0) add("${d.taxName} (${qtyText(d.taxPercent)}%)", sym + money(d.tax))
        add(if (shadeTotal != null) "Amount due" else "Total", sym + money(d.total), strong = true)
        return rows
    }

    private fun extras(d: InvoiceData, style: InvoiceStyle): List<Block> = buildList {
        if (d.paymentDetails.isNotBlank()) {
            add(Block.Para(p("Payment details", bold = true, before = 14f, color = if (style == InvoiceStyle.ONLINE) ACCENT else null)))
            lines(d.paymentDetails).forEach { add(Block.Para(it)) }
        }
        if (d.notes.isNotBlank()) {
            add(Block.Para(p("Notes", bold = true, before = 10f, color = if (style == InvoiceStyle.ONLINE) ACCENT else null)))
            lines(d.notes).forEach { add(Block.Para(it)) }
        }
    }

    fun build(d: InvoiceData, style: InvoiceStyle): WordDoc {
        val page = PageSetup(marginTopTwips = 1000, marginBottomTwips = 1000, marginLeftTwips = 1000, marginRightTwips = 1000)
        val blocks = when (style) {
            InvoiceStyle.TRADITIONAL -> traditional(d)
            InvoiceStyle.ONLINE -> online(d)
            InvoiceStyle.MINIMAL -> minimal(d)
        }
        return WordDoc(title = "Invoice " + d.number.ifBlank { d.customerName }.trim(), page = page, blocks = blocks)
    }

    private fun traditional(d: InvoiceData): List<Block> {
        val head = 0xE7E6E6
        val widths = listOf(600, 4506, 1100, 1700, 2000)
        val rows = ArrayList<List<TableCell>>()
        rows += listOf(cell("S/N", true, Align.CENTER, head), cell("Description", true, shading = head), cell("Qty", true, Align.RIGHT, head),
            cell("Rate (${d.currency.symbol})", true, Align.RIGHT, head), cell("Amount (${d.currency.symbol})", true, Align.RIGHT, head))
        rows += itemRows(d, serial = true, stripe = null)
        rows += totalRows(d, cols = 5, shadeTotal = head, totalColor = null)
        val info = TableBlock(
            listOf(listOf(
                cell(listOf(p("BILL TO", bold = true, size = 9f, color = GREY)) + lines(d.customerName, bold = true) + lines(d.customerAddress)),
                cell(listOf(p("Invoice No: ${d.number}", bold = true), p("Date: ${d.date}"), p("Due date: ${d.due}"))),
            )),
            listOf(WIDTH / 2, WIDTH - WIDTH / 2), borders = true, borderColor = 0x000000, borderWidthPt = 0.75f,
        )
        val sign = TableBlock(
            listOf(listOf(cell(listOf(p("Received by:", before = 26f), p("Name / Sign / Date: ______________________", before = 10f))),
                cell(listOf(p("Authorised signature:", before = 26f, align = Align.RIGHT), p("______________________", before = 10f, align = Align.RIGHT))))),
            listOf(WIDTH / 2, WIDTH - WIDTH / 2), borders = false,
        )
        return buildList {
            add(Block.Para(p("INVOICE", bold = true, size = 22f, align = Align.CENTER, after = 2f)))
            add(Block.Para(p(d.businessName, bold = true, size = 15f, align = Align.CENTER, after = 0f)))
            lines(d.businessContact, size = 10f, align = Align.CENTER).forEach { add(Block.Para(it)) }
            add(Block.Para(p("", after = 6f)))
            add(Block.Table(info))
            add(Block.Para(p("", after = 6f)))
            add(Block.Table(TableBlock(rows, widths, headerRow = true, borders = true, borderColor = 0x000000, borderWidthPt = 0.75f)))
            add(Block.Para(p("Amount in words: " + NumberWords.money(d.total, d.currency), bold = true, before = 8f)))
            addAll(extras(d, InvoiceStyle.TRADITIONAL))
            add(Block.Table(sign))
        }
    }

    private fun online(d: InvoiceData): List<Block> {
        val widths = listOf(4906, 1300, 1700, 2000)
        val rows = ArrayList<List<TableCell>>()
        rows += listOf(cell("DESCRIPTION", true, shading = ACCENT, color = 0xFFFFFF, size = 9f), cell("QTY", true, Align.RIGHT, ACCENT, 0xFFFFFF, 9f),
            cell("PRICE", true, Align.RIGHT, ACCENT, 0xFFFFFF, 9f), cell("AMOUNT", true, Align.RIGHT, ACCENT, 0xFFFFFF, 9f))
        rows += itemRows(d, serial = false, stripe = 0xF3F7FC)
        rows += totalRows(d, cols = 4, shadeTotal = ACCENT, totalColor = 0xFFFFFF)
        val top = TableBlock(
            listOf(listOf(
                cell(listOf(p(d.businessName, bold = true, size = 13f)) + lines(d.businessContact, size = 10f, color = GREY)),
                cell(listOf(p("Invoice no", size = 9f, color = GREY, align = Align.RIGHT), p(d.number, bold = true, align = Align.RIGHT, after = 6f),
                    p("Issued ${d.date}", size = 10f, align = Align.RIGHT), p("Due ${d.due}", size = 10f, bold = true, align = Align.RIGHT))),
            )),
            listOf(5906, 4000), borders = false,
        )
        return buildList {
            add(Block.Para(p("Invoice", bold = true, size = 30f, color = ACCENT, after = 10f)))
            add(Block.Table(top))
            add(Block.Para(p("BILLED TO", bold = true, size = 9f, color = GREY, before = 14f)))
            lines(d.customerName, bold = true, size = 12f).forEach { add(Block.Para(it)) }
            lines(d.customerAddress, color = GREY).forEach { add(Block.Para(it)) }
            add(Block.Para(p("", after = 8f)))
            add(Block.Table(TableBlock(rows, widths, headerRow = true, borders = false)))
            addAll(extras(d, InvoiceStyle.ONLINE))
            add(Block.Para(p("Thank you for your business.", color = GREY, before = 18f, italic = true)))
        }
    }

    private fun minimal(d: InvoiceData): List<Block> {
        val widths = listOf(5106, 1200, 1700, 1900)
        val head = 0xEFEFEF
        val rows = ArrayList<List<TableCell>>()
        rows += listOf(cell("Item", true, shading = head), cell("Qty", true, Align.RIGHT, head), cell("Price", true, Align.RIGHT, head), cell("Amount", true, Align.RIGHT, head))
        rows += itemRows(d, serial = false, stripe = null)
        rows += totalRows(d, cols = 4, shadeTotal = null, totalColor = null)
        return buildList {
            add(Block.Para(p("Invoice ${d.number}", bold = true, size = 20f, after = 2f)))
            add(Block.Para(p("${d.date}   ·   Due ${d.due}", color = GREY, after = 12f)))
            add(Block.Para(p(d.businessName, bold = true)))
            lines(d.businessContact, size = 10f, color = GREY).forEach { add(Block.Para(it)) }
            add(Block.Para(p("To", color = GREY, size = 9f, before = 12f)))
            lines(d.customerName, bold = true).forEach { add(Block.Para(it)) }
            lines(d.customerAddress).forEach { add(Block.Para(it)) }
            add(Block.Para(p("", after = 8f)))
            add(Block.Table(TableBlock(rows, widths, headerRow = true, borders = false)))
            addAll(extras(d, InvoiceStyle.MINIMAL))
        }
    }
}
