package com.docscan.app.core.word

/**
 * The document model behind the word processor. Pure Kotlin so it can be tested without a phone.
 * Colours are 0xRRGGBB. Sizes: font sizes in points, spacing in points, indents and page sizes in twips
 * (1/20 point, the unit Word files use).
 */
enum class VerticalAlign { BASELINE, SUPERSCRIPT, SUBSCRIPT }

data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val color: Int? = null,
    val highlight: Int? = null,
    val sizePt: Float? = null,
    val font: String? = null,
    val vertical: VerticalAlign = VerticalAlign.BASELINE,
    /** Web address, e-mail (mailto:) or phone (tel:) this text links to. */
    val link: String? = null,
) {
    /** Properties set on [this] win; anything unset falls back to [base]. */
    fun over(base: RunStyle): RunStyle = RunStyle(
        bold = bold || base.bold,
        italic = italic || base.italic,
        underline = underline || base.underline,
        strike = strike || base.strike,
        color = color ?: base.color,
        highlight = highlight ?: base.highlight,
        sizePt = sizePt ?: base.sizePt,
        font = font ?: base.font,
        vertical = if (vertical != VerticalAlign.BASELINE) vertical else base.vertical,
        link = link ?: base.link,
    )
}

/** Text with one set of character formatting. '\t' is a tab and '\n' a line break inside the paragraph. */
data class Run(val text: String, val style: RunStyle = RunStyle())

enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }

enum class ParaStyle(val label: String) {
    NORMAL("Normal"),
    TITLE("Title"),
    H1("Heading 1"),
    H2("Heading 2"),
    H3("Heading 3"),
    QUOTE("Quote"),
}

enum class ListKind { NONE, BULLET, NUMBER }

data class Paragraph(
    val runs: List<Run> = emptyList(),
    val style: ParaStyle = ParaStyle.NORMAL,
    val align: Align = Align.LEFT,
    val list: ListKind = ListKind.NONE,
    /** Nesting depth of a list item, 0..8. */
    val level: Int = 0,
    val indentLeftTwips: Int = 0,
    /** First-line indent; negative = hanging. */
    val indentFirstTwips: Int = 0,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    /** Line spacing as a multiple of single spacing (1.0, 1.15, 1.5, 2.0). */
    val lineSpacing: Float? = null,
    val keepWithNext: Boolean = false,
) {
    val text: String get() = runs.joinToString("") { it.text }
    val isEmpty: Boolean get() = runs.all { it.text.isEmpty() }
}

data class TableCell(
    val paragraphs: List<Paragraph> = listOf(Paragraph()),
    val shading: Int? = null,
    val colSpan: Int = 1,
    /** True for the cells that a vertical merge continues into; they carry no content of their own. */
    val vMergeContinue: Boolean = false,
    val vMergeStart: Boolean = false,
)

data class TableBlock(
    val rows: List<List<TableCell>>,
    /** Column widths in twips (one per grid column). */
    val columnWidthsTwips: List<Int>,
    val headerRow: Boolean = false,
    val borders: Boolean = true,
) {
    val columnCount: Int get() = columnWidthsTwips.size
}

data class ImageBlock(
    val bytes: ByteArray,
    /** "png" or "jpg". */
    val extension: String,
    val widthTwips: Int,
    val heightTwips: Int,
    val align: Align = Align.LEFT,
) {
    override fun equals(other: Any?): Boolean =
        other is ImageBlock && extension == other.extension && widthTwips == other.widthTwips &&
            heightTwips == other.heightTwips && align == other.align && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode() * 31 + widthTwips
}

sealed interface Block {
    data class Para(val paragraph: Paragraph) : Block
    data class Table(val table: TableBlock) : Block
    data class Image(val image: ImageBlock) : Block
    data object PageBreak : Block
}

enum class PaperSize(val label: String, val widthTwips: Int, val heightTwips: Int) {
    A4("A4", 11906, 16838),
    A5("A5", 8392, 11906),
    LETTER("Letter", 12240, 15840),
    LEGAL("Legal", 12240, 20160),
}

data class PageSetup(
    val widthTwips: Int = PaperSize.A4.widthTwips,
    val heightTwips: Int = PaperSize.A4.heightTwips,
    val landscape: Boolean = false,
    val marginTopTwips: Int = 1440,
    val marginBottomTwips: Int = 1440,
    val marginLeftTwips: Int = 1440,
    val marginRightTwips: Int = 1440,
    val headerText: String = "",
    val footerText: String = "",
    val pageNumbers: Boolean = false,
) {
    /** Page size as laid out on the sheet, with orientation applied. */
    val sheetWidthTwips: Int get() = if (landscape) maxOf(widthTwips, heightTwips) else minOf(widthTwips, heightTwips)
    val sheetHeightTwips: Int get() = if (landscape) minOf(widthTwips, heightTwips) else maxOf(widthTwips, heightTwips)
    val contentWidthTwips: Int get() = (sheetWidthTwips - marginLeftTwips - marginRightTwips).coerceAtLeast(720)
    val contentHeightTwips: Int get() = (sheetHeightTwips - marginTopTwips - marginBottomTwips).coerceAtLeast(720)
}

data class WordDoc(
    val title: String = "Untitled",
    val blocks: List<Block> = listOf(Block.Para(Paragraph())),
    val page: PageSetup = PageSetup(),
)

object Twips {
    const val PER_POINT = 20
    const val PER_INCH = 1440
    const val EMU_PER_TWIP = 635
    fun fromCm(cm: Float): Int = (cm / 2.54f * PER_INCH).toInt()
    fun toCm(twips: Int): Float = twips / PER_INCH.toFloat() * 2.54f
}

/** Default look of the built-in paragraph styles. Shared by the editor, the PDF export and the .docx writer. */
object StyleDefaults {
    const val BODY_FONT = "Calibri"
    const val BODY_SIZE_PT = 11f

    /** Default line spacing used by the styles written to .docx (259/240 = Word's "1.08"). */
    const val LINE_SPACING = 259f / 240f

    fun sizePt(style: ParaStyle): Float = when (style) {
        ParaStyle.NORMAL, ParaStyle.QUOTE -> BODY_SIZE_PT
        ParaStyle.TITLE -> 26f
        ParaStyle.H1 -> 20f
        ParaStyle.H2 -> 16f
        ParaStyle.H3 -> 13f
    }

    fun bold(style: ParaStyle): Boolean = style == ParaStyle.H1 || style == ParaStyle.H2 || style == ParaStyle.H3 || style == ParaStyle.TITLE
    fun italic(style: ParaStyle): Boolean = style == ParaStyle.QUOTE
    fun color(style: ParaStyle): Int? = when (style) {
        ParaStyle.H1, ParaStyle.H2, ParaStyle.H3 -> 0x1F3864
        ParaStyle.QUOTE -> 0x595959
        else -> null
    }

    fun spaceBeforePt(style: ParaStyle): Float = when (style) {
        ParaStyle.H1 -> 18f
        ParaStyle.H2 -> 14f
        ParaStyle.H3 -> 10f
        else -> 0f
    }

    fun spaceAfterPt(style: ParaStyle): Float = when (style) {
        ParaStyle.TITLE -> 12f
        ParaStyle.H1, ParaStyle.H2, ParaStyle.H3 -> 6f
        else -> 8f
    }

    fun indentLeftTwips(style: ParaStyle): Int = if (style == ParaStyle.QUOTE) 720 else 0
}

/** Link addresses the app is willing to open or write. Anything else (javascript:, file:, intent:) is refused. */
object LinkSafety {
    private val ALLOWED = setOf("http", "https", "mailto", "tel")

    /** Returns a cleaned address, adding https:// to bare web addresses, or null if it is not safe to link to. */
    fun normalize(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 2000 || t.any { it.isISOControl() || it == ' ' }) return null
        val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(t)?.groupValues?.get(1)?.lowercase()
        return when {
            scheme != null -> if (scheme in ALLOWED) t else null
            t.contains('@') && !t.contains('/') -> "mailto:$t"
            t.contains('.') -> "https://$t"
            else -> null
        }
    }
}
