package com.docscan.app.ui.write

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.ListKind
import com.docscan.app.core.word.ParaStyle
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.RunStyle
import com.docscan.app.core.word.StyleDefaults
import com.docscan.app.core.word.VerticalAlign
import kotlin.math.roundToInt

/**
 * Rich text for the word processor, built on Android's own Spannable so typing, selection, IME and
 * accessibility behave like any text field. One Editable holds several paragraphs separated by '\n'.
 *
 * - Character formatting (bold, size, colour, ...) = ordinary spans, which grow as you type at their end.
 * - Paragraph formatting (style, alignment, lists, spacing, indents) = exactly one [ParaSpan] per paragraph.
 * - Heading looks are drawn by [LookSpan] and never exported as direct formatting.
 */

/** Screen units (dp) per typographic point. 11 pt body text becomes about 15 dp, comfortable on a phone. */
const val ZOOM = 1.35f

/** Span priority: higher numbers are applied first, so later ones (direct formatting) win. */
private const val PRIORITY_SHIFT = Spanned.SPAN_PRIORITY_SHIFT
private fun prio(p: Int) = p shl PRIORITY_SHIFT
private val CHAR_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE
private val PARA_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

data class ParaAttrs(
    val style: ParaStyle = ParaStyle.NORMAL,
    val align: Align = Align.LEFT,
    val list: ListKind = ListKind.NONE,
    val level: Int = 0,
    val indentLeftTwips: Int = 0,
    val indentFirstTwips: Int = 0,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val lineSpacing: Float? = null,
    val keepWithNext: Boolean = false,
    val shading: Int? = null,
    val borderColor: Int? = null,
    val rtl: Boolean = false,
) {
    fun toParagraph(runs: List<Run>) = Paragraph(
        runs, style, align, list, level, indentLeftTwips, indentFirstTwips,
        spaceBeforePt, spaceAfterPt, lineSpacing, keepWithNext, shading, borderColor, rtl,
    )

    companion object {
        fun of(p: Paragraph) = ParaAttrs(
            p.style, p.align, p.list, p.level, p.indentLeftTwips, p.indentFirstTwips,
            p.spaceBeforePt, p.spaceAfterPt, p.lineSpacing, p.keepWithNext, p.shading, p.borderColor, p.rtl,
        )
    }
}

// ---- spans ----------------------------------------------------------------------------------------------

/** Paragraph attributes: indent and list marker, alignment, and spacing. */
class ParaSpan(val attrs: ParaAttrs, private val density: Float) : LeadingMarginSpan, AlignmentSpan, LineHeightSpan, android.text.style.LineBackgroundSpan {
    private fun px(twips: Int) = twips / 20f * ZOOM * density
    private fun pxPt(pt: Float) = pt * ZOOM * density

    private val leftTwips: Int
        get() = attrs.indentLeftTwips + StyleDefaults.indentLeftTwips(attrs.style) +
            (if (attrs.list != ListKind.NONE) 720 * (attrs.level + 1) else 0)

    override fun getLeadingMargin(first: Boolean): Int {
        val first1 = if (attrs.list == ListKind.NONE && first) attrs.indentFirstTwips else 0
        return px((leftTwips + first1).coerceAtLeast(0)).roundToInt()
    }

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) {
        if (!first || attrs.list == ListKind.NONE) return
        val marker = if (attrs.list == ListKind.BULLET) bullet(attrs.level) else numberLabel(text, start)
        val at = x + dir * px((leftTwips - 360).coerceAtLeast(0))
        val w = p.measureText(marker)
        c.drawText(marker, if (dir > 0) at else at - w, baseline.toFloat(), p)
    }

    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        if (attrs.shading == null && attrs.borderColor == null) return
        val sp = text as? Spanned
        val s = sp?.getSpanStart(this) ?: start
        val e = sp?.getSpanEnd(this) ?: end
        val l = left + px(leftTwips.coerceAtLeast(0)).toInt().toFloat()
        val r = right.toFloat()
        val oldColor = p.color
        val oldStyle = p.style
        val oldWidth = p.strokeWidth
        attrs.shading?.let {
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt() or it
            c.drawRect(l, top.toFloat(), r, bottom.toFloat(), p)
        }
        attrs.borderColor?.let {
            p.style = Paint.Style.STROKE
            p.strokeWidth = ZOOM * density
            p.color = 0xFF000000.toInt() or it
            val half = p.strokeWidth / 2
            c.drawLine(l + half, top.toFloat(), l + half, bottom.toFloat(), p)
            c.drawLine(r - half, top.toFloat(), r - half, bottom.toFloat(), p)
            if (start <= s) c.drawLine(l, top + half, r, top + half, p)
            if (end >= e) c.drawLine(l, bottom - half, r, bottom - half, p)
        }
        p.color = oldColor
        p.style = oldStyle
        p.strokeWidth = oldWidth
    }

    override fun getAlignment(): Layout.Alignment = when (attrs.align) {
        Align.CENTER -> Layout.Alignment.ALIGN_CENTER
        Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    // What this span left in the font metrics after the previous line, and what it added. The layout can hand the next
    // line the metrics we already enlarged; adding again would make every wrapped line taller than the one before.
    private var leftMetrics: IntArray? = null
    private var addedMetrics = IntArray(4)

    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val sp = text as? Spanned ?: return
        val s = sp.getSpanStart(this)
        val e = sp.getSpanEnd(this)
        leftMetrics?.let { left ->
            if (fm.ascent == left[0] && fm.top == left[1] && fm.descent == left[2] && fm.bottom == left[3]) {
                fm.ascent -= addedMetrics[0]; fm.top -= addedMetrics[1]; fm.descent -= addedMetrics[2]; fm.bottom -= addedMetrics[3]
            }
        }
        val ascent0 = fm.ascent; val top0 = fm.top; val descent0 = fm.descent; val bottom0 = fm.bottom
        val mult = attrs.lineSpacing ?: StyleDefaults.LINE_SPACING
        if (mult > 1f) {
            val extra = ((mult - 1f) * (fm.descent - fm.ascent)).roundToInt()
            fm.descent += extra
            fm.bottom += extra
        }
        // The blank room between sheets goes on the first line of each page after the first.
        sp.getSpans(start, end, PageGapSpan::class.java).firstOrNull()?.let { g ->
            if (g.gapPx > 0 && start in g.starts) { val gap = g.gapPx + (g.starts[start] ?: 0); fm.ascent -= gap; fm.top -= gap }
        }
        if (start <= s) {
            val before = pxPt(attrs.spaceBeforePt ?: StyleDefaults.spaceBeforePt(attrs.style)).roundToInt()
            fm.ascent -= before
            fm.top -= before
        }
        if (end >= e) {
            val after = pxPt(attrs.spaceAfterPt ?: defaultAfter()).roundToInt()
            fm.descent += after
            fm.bottom += after
        }
        addedMetrics = intArrayOf(fm.ascent - ascent0, fm.top - top0, fm.descent - descent0, fm.bottom - bottom0)
        leftMetrics = intArrayOf(fm.ascent, fm.top, fm.descent, fm.bottom)
    }

    private fun defaultAfter() = if (attrs.list != ListKind.NONE) 0f else StyleDefaults.spaceAfterPt(attrs.style)

    private fun numberLabel(text: CharSequence, start: Int): String {
        val sp = text as? Spanned ?: return "1."
        var n = 1
        val before = sp.getSpans(0, start, ParaSpan::class.java).filter { sp.getSpanStart(it) < start }
            .sortedByDescending { sp.getSpanStart(it) }
        for (other in before) {
            val a = other.attrs
            if (a.list != ListKind.NUMBER || a.level < attrs.level) break
            if (a.level == attrs.level) n++
        }
        return when (attrs.level % 3) {
            0 -> "$n."
            1 -> letters(n) + "."
            else -> roman(n) + "."
        }
    }

    companion object {
        fun bullet(level: Int) = when (level % 3) {
            0 -> "•"
            1 -> "◦"
            else -> "▪"
        }

        fun letters(n: Int): String {
            var v = n
            val sb = StringBuilder()
            while (v > 0) {
                v--
                sb.insert(0, 'a' + v % 26)
                v /= 26
            }
            return sb.toString()
        }

        fun roman(n: Int): String {
            val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
            val names = arrayOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")
            var v = n
            val sb = StringBuilder()
            for (i in values.indices) while (v >= values[i]) {
                sb.append(names[i])
                v -= values[i]
            }
            return sb.toString()
        }
    }
}

/** The look of a built-in paragraph style (size, weight, colour). Direct formatting applied later wins over it. */
class LookSpan(val style: ParaStyle) : MetricAffectingSpan() {
    override fun updateMeasureState(tp: TextPaint) = apply(tp, false)
    override fun updateDrawState(tp: TextPaint) = apply(tp, true)

    private fun apply(tp: TextPaint, draw: Boolean) {
        tp.textSize = StyleDefaults.sizePt(style) * ZOOM * tp.density
        val bold = StyleDefaults.bold(style)
        val italic = StyleDefaults.italic(style)
        if (bold || italic) {
            val flags = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
            tp.typeface = Typeface.create(tp.typeface ?: Typeface.DEFAULT, flags)
        }
        if (draw) StyleDefaults.color(style)?.let { tp.color = 0xFF000000.toInt() or it }
    }
}

/**
 * Extra width for every space in one wrapped line of a justified paragraph, so the line ends exactly at the right
 * margin. It is a layout aid only: it is never saved, and the editor recomputes it after every layout.
 */
class GapSpan(val extra: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(tp: TextPaint) { tp.wordSpacing = extra }
    override fun updateDrawState(tp: TextPaint) { tp.wordSpacing = extra }
}

class PtSizeSpan(val pt: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(tp: TextPaint) { tp.textSize = pt * ZOOM * tp.density }
    override fun updateDrawState(tp: TextPaint) { tp.textSize = pt * ZOOM * tp.density }
}

/** Keeps the Word font name for export while showing the closest font Android has. */
class FontNameSpan(val name: String) : MetricAffectingSpan() {
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    private fun apply(tp: TextPaint) {
        val style = tp.typeface?.style ?: 0
        tp.typeface = WordFonts.typeface(name, style) ?: Typeface.create(familyFor(name), style)
    }

    companion object {
        fun familyFor(name: String): String {
            val n = name.lowercase()
            return when {
                "courier" in n || "mono" in n || "consolas" in n -> "monospace"
                "times" in n || "georgia" in n || "garamond" in n || "cambria" in n || "palatino" in n || n.endsWith(" serif") && "sans" !in n -> "serif"
                else -> "sans-serif"
            }
        }
    }
}

/** A comment's anchor: the text it is about is tinted, and the id links it to the comment's text. */
class CommentSpan(val id: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        if (tp.bgColor == 0) tp.bgColor = 0x66FFD54F
    }
}

/** Underline that remembers its Word style (double, dotted, wavy…) so it survives editing and saving. */
class UnderlineStyleSpan(val word: String) : UnderlineSpan()

/** A hyperlink: blue and underlined. The address travels with the text so it is saved to .docx and PDF. */
class LinkSpan(val url: String) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = 0xFF0563C1.toInt()
        tp.isUnderlineText = true
    }
}

/** Superscript / subscript. Applied last so it shrinks whatever size the text already has. */
class VertSpan(val up: Boolean) : MetricAffectingSpan() {
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    private fun apply(tp: TextPaint) {
        val ascent = tp.ascent()
        tp.baselineShift += if (up) (ascent * 0.4f).toInt() else -(ascent * 0.15f).toInt()
        tp.textSize = tp.textSize * 0.65f
    }
}

// ---- character formatting operations --------------------------------------------------------------------

enum class CharKind { BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, HIGHLIGHT, SIZE, FONT, VERT, LINK }

sealed interface CharAttr {
    val kind: CharKind
    data object Bold : CharAttr { override val kind = CharKind.BOLD }
    data object Italic : CharAttr { override val kind = CharKind.ITALIC }
    data object Underline : CharAttr { override val kind = CharKind.UNDERLINE }
    /** An underline other than single: "double", "dotted", "dash", "wave", "thick" … (shown as a plain underline on screen, kept in .docx). */
    data class UnderlineAs(val word: String) : CharAttr { override val kind = CharKind.UNDERLINE }
    data object Strike : CharAttr { override val kind = CharKind.STRIKE }
    data class Color(val rgb: Int) : CharAttr { override val kind = CharKind.COLOR }
    data class Highlight(val rgb: Int) : CharAttr { override val kind = CharKind.HIGHLIGHT }
    data class Size(val pt: Float) : CharAttr { override val kind = CharKind.SIZE }
    data class Font(val name: String) : CharAttr { override val kind = CharKind.FONT }
    data class Vert(val up: Boolean) : CharAttr { override val kind = CharKind.VERT }
    data class Link(val url: String) : CharAttr { override val kind = CharKind.LINK }
}

object RichOps {
    // -- span <-> attribute mapping --

    private fun kindOf(span: Any): CharKind? = when (span) {
        is StyleSpan -> when {
            span.style and Typeface.BOLD != 0 -> CharKind.BOLD
            span.style and Typeface.ITALIC != 0 -> CharKind.ITALIC
            else -> null
        }
        is UnderlineSpan -> CharKind.UNDERLINE
        is StrikethroughSpan -> CharKind.STRIKE
        is ForegroundColorSpan -> CharKind.COLOR
        is BackgroundColorSpan -> CharKind.HIGHLIGHT
        is PtSizeSpan -> CharKind.SIZE
        is FontNameSpan -> CharKind.FONT
        is VertSpan -> CharKind.VERT
        is LinkSpan -> CharKind.LINK
        else -> null
    }

    private fun make(a: CharAttr): Pair<Any, Int> = when (a) {
        CharAttr.Bold -> StyleSpan(Typeface.BOLD) to (CHAR_FLAGS or prio(1))
        CharAttr.Italic -> StyleSpan(Typeface.ITALIC) to (CHAR_FLAGS or prio(1))
        CharAttr.Underline -> UnderlineSpan() to (CHAR_FLAGS or prio(1))
        is CharAttr.UnderlineAs -> UnderlineStyleSpan(a.word) to (CHAR_FLAGS or prio(1))
        CharAttr.Strike -> StrikethroughSpan() to (CHAR_FLAGS or prio(1))
        is CharAttr.Color -> ForegroundColorSpan(0xFF000000.toInt() or (a.rgb and 0xFFFFFF)) to (CHAR_FLAGS or prio(1))
        is CharAttr.Highlight -> BackgroundColorSpan(0xFF000000.toInt() or (a.rgb and 0xFFFFFF)) to (CHAR_FLAGS or prio(1))
        is CharAttr.Size -> PtSizeSpan(a.pt) to (CHAR_FLAGS or prio(2))
        is CharAttr.Font -> FontNameSpan(a.name) to (CHAR_FLAGS or prio(2))
        is CharAttr.Vert -> VertSpan(a.up) to (CHAR_FLAGS or prio(0))
        is CharAttr.Link -> LinkSpan(a.url) to (Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or prio(1))
    }

    private fun clone(span: Any): Any = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineStyleSpan -> UnderlineStyleSpan(span.word)
        is UnderlineSpan -> UnderlineSpan()
        is StrikethroughSpan -> StrikethroughSpan()
        is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor)
        is BackgroundColorSpan -> BackgroundColorSpan(span.backgroundColor)
        is PtSizeSpan -> PtSizeSpan(span.pt)
        is FontNameSpan -> FontNameSpan(span.name)
        is VertSpan -> VertSpan(span.up)
        is LinkSpan -> LinkSpan(span.url)
        is CommentSpan -> CommentSpan(span.id)
        else -> span
    }

    private fun spansOfKind(text: Spanned, from: Int, to: Int, kind: CharKind): List<Any> =
        text.getSpans(from, to, Any::class.java).filter { kindOf(it) == kind }

    private const val MASK = 0x33
    private fun endInclusive(flags: Int): Boolean {
        val f = flags and MASK
        return f == Spanned.SPAN_EXCLUSIVE_INCLUSIVE || f == Spanned.SPAN_INCLUSIVE_INCLUSIVE
    }

    /**
     * Removes [kind] formatting from [from, to), keeping it on the parts of existing spans outside the range.
     * With a cursor (from == to) the formatting is switched off for text typed next: a span is split at the cursor
     * so the text typed there falls into the gap.
     */
    fun clear(text: Editable, from: Int, to: Int, kind: CharKind) {
        for (span in spansOfKind(text, from, to, kind)) {
            val a = text.getSpanStart(span)
            val b = text.getSpanEnd(span)
            val flags = text.getSpanFlags(span)
            if (from == to) {
                if (a == b) {
                    if (a == from) text.removeSpan(span)
                } else if (a <= from && b >= from) {
                    text.removeSpan(span)
                    if (a < from) text.setSpan(clone(span), a, from, (flags and MASK.inv()) or Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (b > from) text.setSpan(clone(span), from, b, flags)
                }
                continue
            }
            if (b <= from || a >= to) continue // only touching the range
            text.removeSpan(span)
            if (a < from) text.setSpan(clone(span), a, from, flags)
            if (b > to) text.setSpan(clone(span), to, b, flags)
        }
    }

    /**
     * Applies [attr] to [from, to). With a collapsed cursor ([from] == [to]) an empty span is placed, which
     * text typed next picks up.
     */
    fun set(text: Editable, from: Int, to: Int, attr: CharAttr) {
        clear(text, from, to, attr.kind)
        val (span, base) = make(attr)
        // An empty span must be inclusive at both ends or the text typed at the cursor falls outside it.
        val flags = if (from == to) (base and MASK.inv()) or Spanned.SPAN_INCLUSIVE_INCLUSIVE else base
        text.setSpan(span, from, to, flags)
    }

    /** True when every character in the selection has [kind]; for a cursor, when the text at the cursor has it. */
    fun covers(text: Spanned, from: Int, to: Int, kind: CharKind): Boolean {
        if (from == to) {
            return spansOfKind(text, from, to, kind).any {
                val a = text.getSpanStart(it)
                val b = text.getSpanEnd(it)
                (a < from && b > from) || (a == b && a == from) || (b == from && a < b && endInclusive(text.getSpanFlags(it)))
            }
        }
        var pos = from
        val spans = spansOfKind(text, from, to, kind).filter { text.getSpanStart(it) < text.getSpanEnd(it) }
            .sortedBy { text.getSpanStart(it) }
        for (s in spans) {
            val a = text.getSpanStart(s)
            val b = text.getSpanEnd(s)
            if (a > pos) return false
            if (b > pos) pos = b
            if (pos >= to) return true
        }
        return pos >= to
    }

    /** The value of a valued attribute at the cursor / start of the selection, or null. */
    fun valueAt(text: Spanned, from: Int, kind: CharKind): Any? {
        val probe = if (from > 0) from else 0
        val end = minOf(text.length, probe + 1)
        val spans = text.getSpans(maxOf(0, probe - 1), end, Any::class.java).filter {
            kindOf(it) == kind && text.getSpanStart(it) <= probe && text.getSpanEnd(it) >= probe
        }
        val span = spans.lastOrNull() ?: return null
        return when (span) {
            is PtSizeSpan -> span.pt
            is FontNameSpan -> span.name
            is ForegroundColorSpan -> span.foregroundColor and 0xFFFFFF
            is BackgroundColorSpan -> span.backgroundColor and 0xFFFFFF
            is VertSpan -> span.up
            is LinkSpan -> span.url
            else -> true
        }
    }

    /** Removes every direct character format in the range (the "clear formatting" button). */
    fun clearAll(text: Editable, from: Int, to: Int) {
        // Clear Formatting keeps hyperlinks, as Word does.
        CharKind.entries.filter { it != CharKind.LINK }.forEach { clear(text, from, to, it) }
    }

    // -- paragraphs --

    data class Range(val start: Int, val end: Int, val hasNewline: Boolean) {
        /** End of the paragraph including its newline. */
        val full: Int get() = if (hasNewline) end + 1 else end
    }

    fun paragraphRanges(text: CharSequence): List<Range> {
        val out = ArrayList<Range>()
        var s = 0
        while (true) {
            val nl = text.indexOf('\n', s)
            if (nl < 0) {
                out += Range(s, text.length, false)
                break
            }
            out += Range(s, nl, true)
            s = nl + 1
        }
        return out
    }

    /** Paragraph range that contains [offset]. */
    fun paragraphAt(text: CharSequence, offset: Int): Range {
        val o = offset.coerceIn(0, text.length)
        return paragraphRanges(text).lastOrNull { it.start <= o } ?: Range(0, text.length, false)
    }

    fun attrsOf(text: Spanned, r: Range): ParaAttrs {
        // Query the whole text: getSpans can skip zero-length spans (an empty last paragraph).
        val spans = text.getSpans(0, text.length, ParaSpan::class.java)
        val chosen = spans.firstOrNull { text.getSpanStart(it) == r.start }
        return chosen?.attrs ?: ParaAttrs()
    }

    private fun place(text: Editable, r: Range, attrs: ParaAttrs, density: Float) {
        text.getSpans(r.start, r.full, ParaSpan::class.java).forEach { text.removeSpan(it) }
        text.getSpans(r.start, r.full, LookSpan::class.java).forEach { text.removeSpan(it) }
        text.setSpan(ParaSpan(attrs, density), r.start, r.full, PARA_FLAGS)
        if (attrs.style != ParaStyle.NORMAL) text.setSpan(LookSpan(attrs.style), r.start, r.full, CHAR_FLAGS or prio(3))
    }

    /** Changes the attributes of every paragraph touched by [from, to]. */
    fun updateParagraphs(text: Editable, from: Int, to: Int, density: Float, change: (ParaAttrs) -> ParaAttrs) {
        for (r in paragraphRanges(text)) {
            val hit = if (from == to) r.start <= from && from <= r.end else r.start < to && r.full > from
            if (hit) place(text, r, change(attrsOf(text, r)), density)
        }
    }

    /**
     * Keeps exactly one correct [ParaSpan] (and [LookSpan]) on every paragraph after edits: a paragraph created
     * by pressing Enter inherits the formatting of the one it was split from.
     */
    fun normalize(text: Editable, density: Float) {
        val ranges = paragraphRanges(text)
        var previous = ParaAttrs()
        val used = HashSet<ParaSpan>()
        for (r in ranges) {
            val spans = text.getSpans(r.start, r.full, ParaSpan::class.java).filter {
                val a = text.getSpanStart(it)
                val b = text.getSpanEnd(it)
                (a < r.full && b > r.start) || (a == r.start && b == r.start)
            }
            val chosen = spans.firstOrNull { text.getSpanStart(it) == r.start } ?: spans.firstOrNull()
            val attrs = chosen?.attrs ?: previous
            val ok = chosen != null && chosen !in used && text.getSpanStart(chosen) == r.start &&
                text.getSpanEnd(chosen) == r.full && spans.size == 1 && lookMatches(text, r, attrs)
            if (!ok) {
                place(text, r, attrs, density)
                text.getSpans(r.start, r.full, ParaSpan::class.java).firstOrNull { text.getSpanStart(it) == r.start }?.let { used += it }
            } else {
                used += chosen!!
            }
            previous = attrs
        }
        // Remove paragraph spans left over from paragraphs that no longer exist.
        text.getSpans(0, text.length, ParaSpan::class.java).filter { it !in used }.forEach { text.removeSpan(it) }
    }

    private fun lookMatches(text: Spanned, r: Range, attrs: ParaAttrs): Boolean {
        val looks = text.getSpans(r.start, r.full, LookSpan::class.java)
        if (attrs.style == ParaStyle.NORMAL) return looks.isEmpty()
        return looks.size == 1 && looks[0].style == attrs.style &&
            text.getSpanStart(looks[0]) == r.start && text.getSpanEnd(looks[0]) == r.full
    }
}

// ---- model <-> editable text ----------------------------------------------------------------------------

object SpanBridge {
    /** Soft line breaks inside a paragraph become separate paragraphs, so '\n' only ever separates paragraphs. */
    fun splitSoftBreaks(paragraphs: List<Paragraph>): List<Paragraph> {
        val out = ArrayList<Paragraph>()
        for (p in paragraphs) {
            if (p.runs.none { '\n' in it.text }) {
                out += p
                continue
            }
            var current = ArrayList<Run>()
            for (run in p.runs) {
                val parts = run.text.split('\n')
                parts.forEachIndexed { i, part ->
                    if (i > 0) {
                        out += p.copy(runs = current)
                        current = ArrayList()
                    }
                    if (part.isNotEmpty()) current += Run(part, run.style)
                }
            }
            out += p.copy(runs = current)
        }
        return out
    }

    fun toEditable(paragraphs: List<Paragraph>, density: Float): SpannableStringBuilder {
        val source = splitSoftBreaks(paragraphs).ifEmpty { listOf(Paragraph()) }
        val sb = SpannableStringBuilder()
        val ranges = ArrayList<RichOps.Range>()
        // Spans are applied after all text exists: spans that grow at their end would swallow text appended later.
        val pending = ArrayList<Triple<Int, Int, RunStyle>>()
        source.forEachIndexed { i, p ->
            val ps = sb.length
            for (run in p.runs) {
                if (run.text.isEmpty()) continue
                val s = sb.length
                sb.append(run.text.replace("\r", ""))
                pending += Triple(s, sb.length, run.style)
            }
            val pe = sb.length
            val last = i == source.lastIndex
            if (!last) sb.append('\n')
            ranges += RichOps.Range(ps, pe, !last)
        }
        for ((a, b, st) in pending) applyRun(sb, a, b, st)
        source.forEachIndexed { i, p ->
            val r = ranges[i]
            sb.setSpan(ParaSpan(ParaAttrs.of(p), density), r.start, r.full, PARA_FLAGS)
            if (p.style != ParaStyle.NORMAL) sb.setSpan(LookSpan(p.style), r.start, r.full, CHAR_FLAGS or prio(3))
        }
        return sb
    }

    private fun applyRun(sb: Spannable, s: Int, e: Int, st: RunStyle) {
        fun put(a: CharAttr) {
            val (span, flags) = makeSpan(a)
            sb.setSpan(span, s, e, flags)
        }
        if (st.bold) put(CharAttr.Bold)
        if (st.italic) put(CharAttr.Italic)
        if (st.underline) put(st.underlineStyle?.let { CharAttr.UnderlineAs(it) } ?: CharAttr.Underline)
        if (st.strike) put(CharAttr.Strike)
        st.color?.let { put(CharAttr.Color(it)) }
        st.highlight?.let { put(CharAttr.Highlight(it)) }
        st.sizePt?.let { put(CharAttr.Size(it)) }
        st.font?.let { put(CharAttr.Font(it)) }
        st.link?.let { put(CharAttr.Link(it)) }
        st.comment?.let { sb.setSpan(CommentSpan(it), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        when (st.vertical) {
            VerticalAlign.SUPERSCRIPT -> put(CharAttr.Vert(true))
            VerticalAlign.SUBSCRIPT -> put(CharAttr.Vert(false))
            VerticalAlign.BASELINE -> {}
        }
    }

    private fun makeSpan(a: CharAttr): Pair<Any, Int> {
        val tmp = SpannableStringBuilder("x")
        RichOps.set(tmp, 0, 1, a)
        val span = tmp.getSpans(0, 1, Any::class.java).first { it !is ParaSpan }
        return span to tmp.getSpanFlags(span)
    }

    fun toParagraphs(text: Spanned): List<Paragraph> =
        RichOps.paragraphRanges(text).map { r ->
            RichOps.attrsOf(text, r).toParagraph(runsOf(text, r.start, r.end))
        }

    fun runsIn(text: Spanned, start: Int, end: Int): List<Run> = runsOf(text, start, end)

    private fun runsOf(text: Spanned, start: Int, end: Int): List<Run> {
        if (start >= end) return emptyList()
        val spans = text.getSpans(start, end, Any::class.java).filter { s ->
            val a = text.getSpanStart(s)
            val b = text.getSpanEnd(s)
            a < b && isCharSpan(s)
        }
        val cuts = sortedSetOf(start, end)
        for (s in spans) {
            cuts += text.getSpanStart(s).coerceIn(start, end)
            cuts += text.getSpanEnd(s).coerceIn(start, end)
        }
        val points = cuts.toList()
        val out = ArrayList<Run>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (a == b) continue
            var st = RunStyle()
            for (s in spans) {
                if (text.getSpanStart(s) > a || text.getSpanEnd(s) < b) continue
                st = when (s) {
                    is StyleSpan -> st.copy(
                        bold = st.bold || (s.style and Typeface.BOLD != 0),
                        italic = st.italic || (s.style and Typeface.ITALIC != 0),
                    )
                    is UnderlineStyleSpan -> st.copy(underline = true, underlineStyle = s.word)
                    is UnderlineSpan -> st.copy(underline = true)
                    is StrikethroughSpan -> st.copy(strike = true)
                    is ForegroundColorSpan -> st.copy(color = s.foregroundColor and 0xFFFFFF)
                    is BackgroundColorSpan -> st.copy(highlight = s.backgroundColor and 0xFFFFFF)
                    is PtSizeSpan -> st.copy(sizePt = s.pt)
                    is FontNameSpan -> st.copy(font = s.name)
                    is VertSpan -> st.copy(vertical = if (s.up) VerticalAlign.SUPERSCRIPT else VerticalAlign.SUBSCRIPT)
                    is LinkSpan -> st.copy(link = s.url)
                    is CommentSpan -> st.copy(comment = s.id)
                    else -> st
                }
            }
            val piece = text.subSequence(a, b).toString()
            val last = out.lastOrNull()
            if (last != null && last.style == st) out[out.size - 1] = Run(last.text + piece, st) else out += Run(piece, st)
        }
        return out
    }

    private fun isCharSpan(s: Any) = s is StyleSpan || s is UnderlineSpan || s is StrikethroughSpan ||
        s is ForegroundColorSpan || s is BackgroundColorSpan || s is PtSizeSpan || s is FontNameSpan || s is VertSpan || s is LinkSpan || s is CommentSpan
}

// ---- blocks the editor cannot edit yet (tables, images, page breaks) ------------------------------------

/** Placeholder character standing in for a table / image / page break so it is kept, in place, on save. */
const val CHIP_CHAR = '\uFFFC'

/**
 * Stands in for a table, picture or page break. Pictures and tables are drawn as a preview at their real
 * size so the page view shows where they sit and how much room they take; the page break is a small label.
 */
/** Widest a picture or table may be drawn, in pixels; set by the editor in the mobile view. */
object ChipLimits {
    @Volatile var maxWidthPx: Float = Float.MAX_VALUE
    /** True while the editor's view is drawn into a PDF/preview: editor-only marks (the grey "Page break" pill) are left out. */
    @Volatile var exporting: Boolean = false
    /**
     * Dark paper only: while the page is drawn through the colour filter, pictures and shapes are not drawn; each one is
     * kept here and drawn afterwards, so a logo or photo keeps its real colours.
     */
    @Volatile var deferred: MutableList<(android.graphics.Canvas) -> Unit>? = null
}

class BlockChipSpan(
    val index: Int,
    val label: String,
    /** Mutable so a floating picture or shape can be dragged without rebuilding the text. */
    var block: com.docscan.app.core.word.Block? = null,
) : android.text.style.ReplacementSpan() {
    /** True for pictures and shapes set to "in front of" / "behind" the text: they float freely and take no room. */
    val floating: Boolean get() = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> b.image.wrap.floating
        is com.docscan.app.core.word.Block.Shape -> b.shape.wrap.floating
        else -> false
    }

    val behindText: Boolean get() = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> b.image.wrap == com.docscan.app.core.word.ImageWrap.BEHIND
        is com.docscan.app.core.word.Block.Shape -> b.shape.wrap == com.docscan.app.core.word.ImageWrap.BEHIND
        else -> false
    }

    /** Offset from the left margin / anchor line, in twips. */
    fun offsetTwips(): Pair<Int, Int> = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> b.image.offsetXTwips to b.image.offsetYTwips
        is com.docscan.app.core.word.Block.Shape -> b.shape.offsetXTwips to b.shape.offsetYTwips
        else -> 0 to 0
    }

    fun withOffset(x: Int, y: Int): com.docscan.app.core.word.Block? = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> com.docscan.app.core.word.Block.Image(b.image.copy(offsetXTwips = x, offsetYTwips = y))
        is com.docscan.app.core.word.Block.Shape -> com.docscan.app.core.word.Block.Shape(b.shape.copy(offsetXTwips = x, offsetYTwips = y))
        else -> null
    }

    /** Size in twips of a picture or shape (null for anything else). */
    fun sizeTwips(): Pair<Int, Int>? = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> b.image.widthTwips to b.image.heightTwips
        is com.docscan.app.core.word.Block.Shape -> b.shape.widthTwips to (if (com.docscan.app.core.word.ShapePresets.isLine(b.shape.preset)) 0 else b.shape.heightTwips)
        else -> null
    }

    fun withSize(w: Int, h: Int): com.docscan.app.core.word.Block? = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> com.docscan.app.core.word.Block.Image(b.image.copy(widthTwips = w.coerceAtLeast(240), heightTwips = h.coerceAtLeast(120)))
        is com.docscan.app.core.word.Block.Shape -> com.docscan.app.core.word.Block.Shape(b.shape.copy(widthTwips = w.coerceAtLeast(240), heightTwips = if (com.docscan.app.core.word.ShapePresets.isLine(b.shape.preset)) b.shape.heightTwips else h.coerceAtLeast(120)))
        else -> null
    }

    fun floatSizePx(paint: Paint): Pair<Int, Int>? {
        val w = widthPx(paint) ?: return null
        val h = heightPx(paint) ?: return null
        return w to h
    }

    fun drawFloating(c: Canvas, left: Float, top: Float, paint: Paint) {
        val (w, h) = floatSizePx(paint) ?: return
        when (val b = block) {
            is com.docscan.app.core.word.Block.Image -> drawImage(c, b.image, left, top.toInt(), (top + h).toInt(), paint)
            is com.docscan.app.core.word.Block.Shape ->
                com.docscan.app.export.ShapeDraw.draw(c, b.shape, android.graphics.RectF(left, top, left + w, top + h), pxPerTwip(paint) * 20f)
            else -> {}
        }
    }

    private var bitmap: android.graphics.Bitmap? = null
    private var decoded = false

    private fun pxPerTwip(paint: Paint): Float = ZOOM * ((paint as? TextPaint)?.density ?: 1f) / 20f

    /** One laid-out table: every cell's text laid out with its own fonts, colours and sizes, as the page preview does. */
    private class TableLayoutCache(val table: com.docscan.app.core.word.TableBlock, val k: Float, val density: Float) {
        class Placed(val xPx: Float, val wPx: Float, val cell: com.docscan.app.core.word.TableCell, val layout: android.text.StaticLayout)
        val rows = ArrayList<List<Placed>>()
        val heights = ArrayList<Float>()
        val total: Float get() = heights.sum()
    }
    private var tableCache: TableLayoutCache? = null

    /** True for a table chip. */
    val isTable: Boolean get() = block is com.docscan.app.core.word.Block.Table

    /**
     * Page-view paging, set by the editor: how far down its page the table starts, the text height of a page and the
     * gap between pages. While [startY] is negative (mobile view) the table is one unbroken block.
     */
    var startY = -1f
    var pageH = 0f
    var pageGap = 0f

    private var planKey: List<Any?>? = null
    private var planCache: com.docscan.app.core.word.TablePaging.Plan? = null

    /** The table's page breaks, or null when it is not paged. */
    fun paging(paint: Paint): com.docscan.app.core.word.TablePaging.Plan? {
        val b = block as? com.docscan.app.core.word.Block.Table ?: return null
        if (startY < 0f || pageH <= 0f) return null
        val lay = tableLayout(b.table, paint)
        val key = listOf(lay, startY, pageH, pageGap)
        if (key == planKey) return planCache
        planKey = key
        planCache = com.docscan.app.core.word.TablePaging.plan(lay.heights, b.table.headerRow, startY, pageH, pageGap)
        return planCache
    }

    private fun tableLayout(t: com.docscan.app.core.word.TableBlock, paint: Paint): TableLayoutCache {
        val density = (paint as? TextPaint)?.density ?: 1f
        val k = pxPerTwip(paint) * fit(paint)
        tableCache?.let { if (it.table === t && it.k == k && it.density == density) return it }
        val out = TableLayoutCache(t, k, density)
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.density = density
            textSize = StyleDefaults.BODY_SIZE_PT * ZOOM * density
            typeface = com.docscan.app.ui.write.WordFonts.body()
            color = 0xFF1B1B1F.toInt()
        }
        val pad = 57f * k
        for (row in t.rows) {
            var col = 0
            var x = 0f
            var h = 0f
            val placed = ArrayList<TableLayoutCache.Placed>()
            for (cell in row) {
                val span = cell.colSpan.coerceAtLeast(1)
                val wTw = (0 until span).sumOf { t.columnWidthsTwips.getOrElse(col + it) { 1440 } }
                val w = wTw * k
                col += span
                val paras = if (cell.vMergeContinue) listOf(Paragraph()) else cell.paragraphs
                val text = SpanBridge.toEditable(paras, density)
                val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, body, (w - 2 * pad).toInt().coerceAtLeast(1))
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()
                h = maxOf(h, layout.height + 2 * pad)
                placed += TableLayoutCache.Placed(x, w, cell, layout)
                x += w
            }
            out.rows += placed
            out.heights += maxOf(h, com.docscan.app.core.word.CellEdges.rowHeight(row) * k)
        }
        tableCache = out
        return out
    }

    private fun naturalWidthTwips(): Int? = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> b.image.widthTwips
        is com.docscan.app.core.word.Block.Shape -> b.shape.widthTwips
        is com.docscan.app.core.word.Block.Table -> b.table.columnWidthsTwips.sum() + b.table.indentTwips
        else -> null
    }

    /** Shrinks to fit when the screen (mobile view) is narrower than the object; the page view never shrinks. */
    private fun fit(paint: Paint): Float {
        val w = naturalWidthTwips() ?: return 1f
        val natural = w * pxPerTwip(paint)
        return if (natural > ChipLimits.maxWidthPx && natural > 0f) ChipLimits.maxWidthPx / natural else 1f
    }

    private fun heightPx(paint: Paint): Int? = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> (b.image.heightTwips * pxPerTwip(paint) * fit(paint)).roundToInt().coerceAtLeast(8)
        is com.docscan.app.core.word.Block.Shape -> {
            val t = if (com.docscan.app.core.word.ShapePresets.isLine(b.shape.preset)) 120 else b.shape.heightTwips
            (t * pxPerTwip(paint) * fit(paint)).roundToInt().coerceAtLeast(8)
        }
        is com.docscan.app.core.word.Block.Table -> (paging(paint)?.height ?: tableLayout(b.table, paint).total).roundToInt().coerceAtLeast(8)
        else -> null
    }

    private fun widthPx(paint: Paint): Int? = naturalWidthTwips()?.let { (it * pxPerTwip(paint) * fit(paint)).roundToInt().coerceAtLeast(8) }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (floating) {
            // The anchor of a floating object is a hairline: it takes no visible room in the text.
            if (fm != null) { fm.ascent = -1; fm.top = -1; fm.descent = 0; fm.bottom = 0 }
            return 1
        }
        val h = heightPx(paint)
        if (h != null && fm != null) {
            fm.ascent = -h
            fm.top = -h
            fm.descent = 0
            fm.bottom = 0
        }
        return widthPx(paint) ?: (paint.measureText(label) + 2 * paint.textSize * 0.6f).toInt()
    }

    override fun draw(c: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        if (floating) return
        // The line also carries the blank room between pages and paragraph spacing; the object itself ends at the baseline.
        val h = heightPx(paint)
        val objTop = if (h != null) y - h else top
        val objBottom = if (h != null) y else bottom
        when (val b = block) {
            is com.docscan.app.core.word.Block.Image -> {
                val later = ChipLimits.deferred
                if (later != null) { val p = Paint(paint); later.add { cv -> drawImage(cv, b.image, x, objTop, objBottom, p) } }
                else drawImage(c, b.image, x, objTop, objBottom, paint)
            }
            is com.docscan.app.core.word.Block.Shape -> {
                val w = (widthPx(paint) ?: 0).toFloat()
                val box = android.graphics.RectF(x, objTop.toFloat(), x + w, objBottom.toFloat())
                val scale = pxPerTwip(paint) * 20f
                val later = ChipLimits.deferred
                if (later != null) later.add { cv -> com.docscan.app.export.ShapeDraw.draw(cv, b.shape, box, scale) }
                else com.docscan.app.export.ShapeDraw.draw(c, b.shape, box, scale)
            }
            is com.docscan.app.core.word.Block.Table -> drawTable(c, b.table, x, objTop, paint)
            else -> drawLabel(c, x, top, y, bottom, paint)
        }
    }

    private fun drawLabel(c: Canvas, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        if (ChipLimits.exporting) return
        val saved = paint.color
        val pad = paint.textSize * 0.3f
        val w = paint.measureText(label) + 4 * pad
        // The line also carries the blank room between sheets; the label is only as tall as its text, on the baseline.
        val labelTop = maxOf(top.toFloat(), y - paint.textSize * 1.2f)
        val labelBottom = minOf(bottom.toFloat(), y + paint.textSize * 0.4f)
        paint.color = 0xFFE8EAF0.toInt()
        c.drawRoundRect(x, labelTop, x + w, labelBottom, pad, pad, paint)
        paint.color = 0xFF444B5A.toInt()
        c.drawText(label, x + 2 * pad, y.toFloat(), paint)
        paint.color = saved
    }

    private fun drawImage(c: Canvas, img: com.docscan.app.core.word.ImageBlock, x: Float, top: Int, bottom: Int, paint: Paint) {
        if (!decoded) {
            decoded = true
            bitmap = runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2
                android.graphics.BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
        val dst = android.graphics.RectF(x, top.toFloat(), x + (widthPx(paint) ?: 0), bottom.toFloat())
        val bmp = bitmap
        if (bmp != null) c.drawBitmap(bmp, null, dst, null) else {
            val saved = paint.color
            paint.color = 0xFFE8EAF0.toInt()
            c.drawRect(dst, paint)
            paint.color = saved
        }
    }

    /** Where the table was last drawn (text-layout coordinates), so a tap can be matched to a cell. */
    private var lastX = 0f
    private var lastTop = 0f

    class CellHit(val row: Int, val col: Int, val rect: android.graphics.RectF, val pad: Float)

    /** The cell under a point given in text-layout coordinates, or null (outside the table, or a merged-down cell). */
    fun cellAt(px: Float, py: Float, paint: Paint): CellHit? {
        val b = block as? com.docscan.app.core.word.Block.Table ?: return null
        val lay = tableLayout(b.table, paint)
        val plan = paging(paint)
        val x = lastX + b.table.indentTwips * lay.k
        var acc = 0f
        for (ri in lay.rows.indices) {
            val y = lastTop + (plan?.rowTops?.get(ri) ?: acc)
            val h = plan?.rowHeights?.get(ri) ?: lay.heights[ri]
            acc += lay.heights[ri]
            if (py < y || py > y + h) continue
            for ((ci, cell) in lay.rows[ri].withIndex()) {
                val r = android.graphics.RectF(x + cell.xPx, y, x + cell.xPx + cell.wPx, y + h)
                if (r.contains(px, py)) return if (cell.cell.vMergeContinue) null else CellHit(ri, ci, r, 57f * lay.k)
            }
        }
        return null
    }

    private fun drawTable(c: Canvas, t: com.docscan.app.core.word.TableBlock, x0: Float, top: Int, paint: Paint) {
        lastX = x0
        lastTop = top.toFloat()
        val lay = tableLayout(t, paint)
        val k = lay.k
        val savedColor = paint.color
        val savedStyle = paint.style
        val savedStroke = paint.strokeWidth
        val x = x0 + t.indentTwips * k
        val pad = 57f * k
        val plan = paging(paint)
        fun drawRow(ri: Int, y: Float, h: Float) {
            for (cell in lay.rows[ri]) {
                val r = android.graphics.RectF(x + cell.xPx, y, x + cell.xPx + cell.wPx, y + h)
                cell.cell.shading?.let {
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFF000000.toInt() or it
                    c.drawRect(r, paint)
                }
                com.docscan.app.export.CellBorderDraw.draw(c, r, cell.cell, t, 20f * k)
                c.save()
                c.clipRect(r)
                c.translate(r.left + pad, r.top + pad)
                cell.layout.draw(c)
                c.restore()
            }
        }
        var y = top.toFloat()
        for (ri in lay.rows.indices) {
            if (plan == null) {
                drawRow(ri, y, lay.heights[ri])
                y += lay.heights[ri]
            } else {
                plan.breaks.firstOrNull { it.row == ri }?.headerAt?.let { drawRow(0, top + it, plan.rowHeights[0]) }
                drawRow(ri, top + plan.rowTops[ri], plan.rowHeights[ri])
            }
        }
        paint.color = savedColor
        paint.style = savedStyle
        paint.strokeWidth = savedStroke
    }
}

object EditorModel {
    fun label(b: com.docscan.app.core.word.Block): String = when (b) {
        is com.docscan.app.core.word.Block.Table ->
            "Table ${b.table.rows.size}×${b.table.columnCount}"
        is com.docscan.app.core.word.Block.Image -> "Image"
        is com.docscan.app.core.word.Block.Shape -> com.docscan.app.core.word.ShapePresets.label(b.shape.preset)
        com.docscan.app.core.word.Block.PageBreak -> "Page break"
        is com.docscan.app.core.word.Block.Para -> ""
    }

    /** Builds the editable text for a document. Non-paragraph blocks become one-character chips, kept in order. */
    fun toEditable(blocks: List<com.docscan.app.core.word.Block>, density: Float): Pair<SpannableStringBuilder, List<com.docscan.app.core.word.Block>> {
        val originals = ArrayList<com.docscan.app.core.word.Block>()
        val paras = ArrayList<Paragraph>()
        val chipIndex = HashMap<Int, Int>()
        for (b in blocks.ifEmpty { listOf(com.docscan.app.core.word.Block.Para(Paragraph())) }) {
            if (b is com.docscan.app.core.word.Block.Para) {
                paras += SpanBridge.splitSoftBreaks(listOf(b.paragraph))
            } else {
                chipIndex[paras.size] = originals.size
                originals += b
                paras += Paragraph(listOf(Run(CHIP_CHAR.toString())))
            }
        }
        val e = SpanBridge.toEditable(paras, density)
        val ranges = RichOps.paragraphRanges(e)
        for ((pi, bi) in chipIndex) {
            val r = ranges[pi]
            e.setSpan(BlockChipSpan(bi, label(originals[bi]), originals[bi]), r.start, r.start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return e to originals
    }

    fun toBlocks(text: Spanned, originals: List<com.docscan.app.core.word.Block>): List<com.docscan.app.core.word.Block> {
        val out = ArrayList<com.docscan.app.core.word.Block>()
        for (r in RichOps.paragraphRanges(text)) {
            val chip = if (r.end - r.start == 1 && text[r.start] == CHIP_CHAR)
                text.getSpans(r.start, r.end, BlockChipSpan::class.java).firstOrNull() else null
            if (chip != null && chip.index in originals.indices) {
                out += originals[chip.index]
            } else {
                val p = RichOps.attrsOf(text, r).toParagraph(SpanBridge.runsIn(text, r.start, r.end))
                out += com.docscan.app.core.word.Block.Para(p)
            }
        }
        return out
    }
}
