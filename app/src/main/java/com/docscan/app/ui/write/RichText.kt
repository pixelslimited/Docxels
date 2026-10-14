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
) {
    fun toParagraph(runs: List<Run>) = Paragraph(
        runs, style, align, list, level, indentLeftTwips, indentFirstTwips,
        spaceBeforePt, spaceAfterPt, lineSpacing, keepWithNext,
    )

    companion object {
        fun of(p: Paragraph) = ParaAttrs(
            p.style, p.align, p.list, p.level, p.indentLeftTwips, p.indentFirstTwips,
            p.spaceBeforePt, p.spaceAfterPt, p.lineSpacing, p.keepWithNext,
        )
    }
}

// ---- spans ----------------------------------------------------------------------------------------------

/** Paragraph attributes: indent and list marker, alignment, and spacing. */
class ParaSpan(val attrs: ParaAttrs, private val density: Float) : LeadingMarginSpan, AlignmentSpan, LineHeightSpan {
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

    override fun getAlignment(): Layout.Alignment = when (attrs.align) {
        Align.CENTER -> Layout.Alignment.ALIGN_CENTER
        Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val sp = text as? Spanned ?: return
        val s = sp.getSpanStart(this)
        val e = sp.getSpanEnd(this)
        val mult = attrs.lineSpacing ?: StyleDefaults.LINE_SPACING
        if (mult > 1f) {
            val extra = ((mult - 1f) * (fm.descent - fm.ascent)).roundToInt()
            fm.descent += extra
            fm.bottom += extra
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
        tp.typeface = Typeface.create(familyFor(name), style)
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

enum class CharKind { BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, HIGHLIGHT, SIZE, FONT, VERT }

sealed interface CharAttr {
    val kind: CharKind
    data object Bold : CharAttr { override val kind = CharKind.BOLD }
    data object Italic : CharAttr { override val kind = CharKind.ITALIC }
    data object Underline : CharAttr { override val kind = CharKind.UNDERLINE }
    data object Strike : CharAttr { override val kind = CharKind.STRIKE }
    data class Color(val rgb: Int) : CharAttr { override val kind = CharKind.COLOR }
    data class Highlight(val rgb: Int) : CharAttr { override val kind = CharKind.HIGHLIGHT }
    data class Size(val pt: Float) : CharAttr { override val kind = CharKind.SIZE }
    data class Font(val name: String) : CharAttr { override val kind = CharKind.FONT }
    data class Vert(val up: Boolean) : CharAttr { override val kind = CharKind.VERT }
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
        else -> null
    }

    private fun make(a: CharAttr): Pair<Any, Int> = when (a) {
        CharAttr.Bold -> StyleSpan(Typeface.BOLD) to (CHAR_FLAGS or prio(1))
        CharAttr.Italic -> StyleSpan(Typeface.ITALIC) to (CHAR_FLAGS or prio(1))
        CharAttr.Underline -> UnderlineSpan() to (CHAR_FLAGS or prio(1))
        CharAttr.Strike -> StrikethroughSpan() to (CHAR_FLAGS or prio(1))
        is CharAttr.Color -> ForegroundColorSpan(0xFF000000.toInt() or (a.rgb and 0xFFFFFF)) to (CHAR_FLAGS or prio(1))
        is CharAttr.Highlight -> BackgroundColorSpan(0xFF000000.toInt() or (a.rgb and 0xFFFFFF)) to (CHAR_FLAGS or prio(1))
        is CharAttr.Size -> PtSizeSpan(a.pt) to (CHAR_FLAGS or prio(2))
        is CharAttr.Font -> FontNameSpan(a.name) to (CHAR_FLAGS or prio(2))
        is CharAttr.Vert -> VertSpan(a.up) to (CHAR_FLAGS or prio(0))
    }

    private fun clone(span: Any): Any = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineSpan -> UnderlineSpan()
        is StrikethroughSpan -> StrikethroughSpan()
        is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor)
        is BackgroundColorSpan -> BackgroundColorSpan(span.backgroundColor)
        is PtSizeSpan -> PtSizeSpan(span.pt)
        is FontNameSpan -> FontNameSpan(span.name)
        is VertSpan -> VertSpan(span.up)
        else -> span
    }

    private fun spansOfKind(text: Spanned, from: Int, to: Int, kind: CharKind): List<Any> =
        text.getSpans(from, to, Any::class.java).filter { kindOf(it) == kind }

    private fun flagsOf(kind: CharKind) = make(
        when (kind) {
            CharKind.BOLD -> CharAttr.Bold
            CharKind.ITALIC -> CharAttr.Italic
            CharKind.UNDERLINE -> CharAttr.Underline
            CharKind.STRIKE -> CharAttr.Strike
            CharKind.COLOR -> CharAttr.Color(0)
            CharKind.HIGHLIGHT -> CharAttr.Highlight(0)
            CharKind.SIZE -> CharAttr.Size(11f)
            CharKind.FONT -> CharAttr.Font("")
            CharKind.VERT -> CharAttr.Vert(true)
        },
    ).second

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
            else -> true
        }
    }

    /** Removes every direct character format in the range (the "clear formatting" button). */
    fun clearAll(text: Editable, from: Int, to: Int) {
        CharKind.entries.forEach { clear(text, from, to, it) }
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
            val pair = RichOps::class.java // keep reference stable for readers
            val (span, flags) = makeSpan(a)
            sb.setSpan(span, s, e, flags)
        }
        if (st.bold) put(CharAttr.Bold)
        if (st.italic) put(CharAttr.Italic)
        if (st.underline) put(CharAttr.Underline)
        if (st.strike) put(CharAttr.Strike)
        st.color?.let { put(CharAttr.Color(it)) }
        st.highlight?.let { put(CharAttr.Highlight(it)) }
        st.sizePt?.let { put(CharAttr.Size(it)) }
        st.font?.let { put(CharAttr.Font(it)) }
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
                    is UnderlineSpan -> st.copy(underline = true)
                    is StrikethroughSpan -> st.copy(strike = true)
                    is ForegroundColorSpan -> st.copy(color = s.foregroundColor and 0xFFFFFF)
                    is BackgroundColorSpan -> st.copy(highlight = s.backgroundColor and 0xFFFFFF)
                    is PtSizeSpan -> st.copy(sizePt = s.pt)
                    is FontNameSpan -> st.copy(font = s.name)
                    is VertSpan -> st.copy(vertical = if (s.up) VerticalAlign.SUPERSCRIPT else VerticalAlign.SUBSCRIPT)
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
        s is ForegroundColorSpan || s is BackgroundColorSpan || s is PtSizeSpan || s is FontNameSpan || s is VertSpan
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
}

class BlockChipSpan(
    val index: Int,
    val label: String,
    val block: com.docscan.app.core.word.Block? = null,
) : android.text.style.ReplacementSpan() {
    private var bitmap: android.graphics.Bitmap? = null
    private var decoded = false

    private fun pxPerTwip(paint: Paint): Float = ZOOM * ((paint as? TextPaint)?.density ?: 1f) / 20f

    private fun tableRowTwips(t: com.docscan.app.core.word.TableBlock): List<Int> = t.rows.map { row ->
        var col = 0
        var tallest = 1
        for (cell in row) {
            val span = cell.colSpan.coerceAtLeast(1)
            val width = (0 until span).sumOf { t.columnWidthsTwips.getOrElse(col + it) { 1440 } } - 230
            col += span
            val perLine = (width / 110).coerceAtLeast(4)
            tallest = maxOf(tallest, cell.paragraphs.sumOf { p -> ((p.text.length + perLine - 1) / perLine).coerceAtLeast(1) })
        }
        tallest * 264 + 120
    }

    private fun naturalWidthTwips(): Int? = when (val b = block) {
        is com.docscan.app.core.word.Block.Image -> b.image.widthTwips
        is com.docscan.app.core.word.Block.Table -> b.table.columnWidthsTwips.sum()
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
        is com.docscan.app.core.word.Block.Table -> (tableRowTwips(b.table).sum() * pxPerTwip(paint) * fit(paint)).roundToInt().coerceAtLeast(8)
        else -> null
    }

    private fun widthPx(paint: Paint): Int? = naturalWidthTwips()?.let { (it * pxPerTwip(paint) * fit(paint)).roundToInt().coerceAtLeast(8) }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
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
        when (val b = block) {
            is com.docscan.app.core.word.Block.Image -> drawImage(c, b.image, x, top, bottom, paint)
            is com.docscan.app.core.word.Block.Table -> drawTable(c, b.table, x, top, paint)
            else -> drawLabel(c, x, top, y, bottom, paint)
        }
    }

    private fun drawLabel(c: Canvas, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val saved = paint.color
        val pad = paint.textSize * 0.3f
        val w = paint.measureText(label) + 4 * pad
        paint.color = 0xFFE8EAF0.toInt()
        c.drawRoundRect(x, top.toFloat() + pad, x + w, bottom.toFloat() - pad, pad, pad, paint)
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

    private fun drawTable(c: Canvas, t: com.docscan.app.core.word.TableBlock, x: Float, top: Int, paint: Paint) {
        val k = pxPerTwip(paint) * fit(paint)
        val savedColor = paint.color
        val savedStyle = paint.style
        val savedStroke = paint.strokeWidth
        val savedTypeface = paint.typeface
        val rows = tableRowTwips(t)
        var yTw = 0
        val line = 0xFF9AA0AE.toInt()
        for ((ri, row) in t.rows.withIndex()) {
            var xTw = 0
            var col = 0
            for (cell in row) {
                val span = cell.colSpan.coerceAtLeast(1)
                val wTw = (0 until span).sumOf { t.columnWidthsTwips.getOrElse(col + it) { 1440 } }
                col += span
                val r = android.graphics.RectF(x + xTw * k, top + yTw * k, x + (xTw + wTw) * k, top + (yTw + rows[ri]) * k)
                val fill = cell.shading?.let { 0xFF000000.toInt() or it } ?: if (t.headerRow && ri == 0) 0xFFE8EAF0.toInt() else null
                if (fill != null) {
                    paint.style = Paint.Style.FILL
                    paint.color = fill
                    c.drawRect(r, paint)
                }
                if (t.borders) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    paint.color = line
                    c.drawRect(r, paint)
                }
                paint.style = Paint.Style.FILL
                paint.color = 0xFF1B1B1F.toInt()
                if (t.headerRow && ri == 0) paint.typeface = Typeface.create(savedTypeface ?: Typeface.DEFAULT, Typeface.BOLD)
                val pad = 57f * k
                val maxW = r.width() - 2 * pad
                var ly = r.top + pad + paint.textSize
                val words = cell.paragraphs.joinToString(" ") { it.text }.split(' ').filter { it.isNotEmpty() }
                var cur = ""
                for (w in words) {
                    val trial = if (cur.isEmpty()) w else "$cur $w"
                    if (paint.measureText(trial) > maxW && cur.isNotEmpty()) {
                        if (ly > r.bottom) break
                        c.drawText(cur, r.left + pad, ly, paint)
                        ly += paint.textSize * 1.2f
                        cur = w
                    } else cur = trial
                }
                if (cur.isNotEmpty() && ly <= r.bottom + paint.textSize) c.drawText(cur, r.left + pad, ly, paint)
                paint.typeface = savedTypeface
                xTw += wTw
            }
            yTw += rows[ri]
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
