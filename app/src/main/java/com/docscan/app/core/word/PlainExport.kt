package com.docscan.app.core.word

/**
 * Saves a [WordDoc] as plain text or RTF. Pure Kotlin, so it is unit-tested on the JVM.
 *
 * Plain text keeps the words, list markers and table rows (tab-separated) but no formatting or pictures.
 * RTF keeps character and paragraph formatting and tables; pictures and headers/footers are left out and the
 * app says so before sharing.
 */
object PlainExport {
    fun toText(doc: WordDoc): String {
        val sb = StringBuilder()
        val counters = IntArray(9)
        for (b in doc.blocks) {
            when (b) {
                is Block.Para -> {
                    val p = b.paragraph
                    val prefix = when (p.list) {
                        ListKind.BULLET -> "  ".repeat(p.level) + "• "
                        ListKind.NUMBER -> {
                            val lv = p.level.coerceIn(0, 8)
                            counters[lv]++
                            for (i in lv + 1..8) counters[i] = 0
                            "  ".repeat(lv) + counters[lv] + ". "
                        }
                        ListKind.NONE -> ""
                    }
                    if (p.list != ListKind.NUMBER) counters.fill(0)
                    sb.append(prefix).append(p.text).append('\n')
                }
                is Block.Table -> {
                    counters.fill(0)
                    for (row in b.table.rows) {
                        sb.append(row.filter { !it.vMergeContinue }.joinToString("\t") { c -> c.paragraphs.joinToString(" ") { it.text } }).append('\n')
                    }
                }
                is Block.Image -> { counters.fill(0); sb.append("[Picture]\n") }
                is Block.Shape -> { counters.fill(0); sb.append("[Shape]\n") }
                Block.PageBreak -> { counters.fill(0); sb.append("\n") }
            }
        }
        return sb.toString().trimEnd('\n') + "\n"
    }

    fun toRtf(doc: WordDoc): String {
        val fonts = LinkedHashMap<String, Int>()
        fonts[StyleDefaults.BODY_FONT] = 0
        val colors = LinkedHashMap<Int, Int>()
        fun fontIdx(name: String) = fonts.getOrPut(name) { fonts.size }
        fun colorIdx(rgb: Int) = colors.getOrPut(rgb and 0xFFFFFF) { colors.size + 1 }

        val body = StringBuilder()
        val counters = IntArray(9)

        fun runs(p: Paragraph, into: StringBuilder) {
            val defaultBold = StyleDefaults.bold(p.style)
            val defaultSize = StyleDefaults.sizePt(p.style)
            for (r in p.runs) {
                if (r.text.isEmpty()) continue
                val s = r.style
                val f = StringBuilder()
                if (s.bold || defaultBold) f.append("\\b")
                if (s.italic) f.append("\\i")
                if (s.underline) f.append("\\ul")
                if (s.strike) f.append("\\strike")
                when (s.vertical) {
                    VerticalAlign.SUPERSCRIPT -> f.append("\\super")
                    VerticalAlign.SUBSCRIPT -> f.append("\\sub")
                    else -> {}
                }
                f.append("\\fs").append(((s.sizePt ?: defaultSize) * 2).toInt().coerceAtLeast(2))
                s.font?.let { f.append("\\f").append(fontIdx(it)) }
                s.color?.let { f.append("\\cf").append(colorIdx(it)) }
                s.highlight?.let { f.append("\\highlight").append(colorIdx(it)) }
                into.append('{').append(f).append(' ').append(rtfEscape(r.text)).append('}')
            }
        }

        fun paraFormat(p: Paragraph, sb: StringBuilder) {
            sb.append(
                when (p.align) {
                    Align.CENTER -> "\\qc"
                    Align.RIGHT -> "\\qr"
                    Align.JUSTIFY -> "\\qj"
                    Align.LEFT -> "\\ql"
                },
            )
            val left = p.indentLeftTwips + StyleDefaults.indentLeftTwips(p.style) + if (p.list != ListKind.NONE) 720 * (p.level + 1) else 0
            val first = if (p.list != ListKind.NONE) -360 else p.indentFirstTwips
            if (left != 0) sb.append("\\li").append(left)
            if (first != 0) sb.append("\\fi").append(first)
            p.spaceBeforePt?.let { sb.append("\\sb").append((it * 20).toInt()) }
            p.spaceAfterPt?.let { sb.append("\\sa").append((it * 20).toInt()) }
            p.lineSpacing?.let { sb.append("\\sl").append((it * 240).toInt()).append("\\slmult1") }
            p.shading?.let { sb.append("\\cbpat").append(colorIdx(it)) }
            if (p.borderColor != null) sb.append("\\brdrt\\brdrs\\brdrw10\\brdrl\\brdrs\\brdrw10\\brdrb\\brdrs\\brdrw10\\brdrr\\brdrs\\brdrw10")
        }

        for (b in doc.blocks) {
            when (b) {
                is Block.Para -> {
                    val p = b.paragraph
                    body.append("\\pard")
                    paraFormat(p, body)
                    body.append(' ')
                    when (p.list) {
                        ListKind.BULLET -> body.append("\\u8226?\\tab ")
                        ListKind.NUMBER -> {
                            val lv = p.level.coerceIn(0, 8)
                            counters[lv]++
                            for (i in lv + 1..8) counters[i] = 0
                            body.append(counters[lv]).append(".\\tab ")
                        }
                        ListKind.NONE -> {}
                    }
                    if (p.list != ListKind.NUMBER) counters.fill(0)
                    runs(p, body)
                    body.append("\\par\n")
                }
                is Block.Table -> {
                    counters.fill(0)
                    val widths = b.table.columnWidthsTwips
                    for (row in b.table.rows) {
                        body.append("\\trowd\\trgaph108")
                        var x = 0
                        var col = 0
                        val cells = row.filter { !it.vMergeContinue }
                        for (c in cells) {
                            x += (col until (col + c.colSpan).coerceAtMost(widths.size)).sumOf { widths[it] }.coerceAtLeast(360)
                            col += c.colSpan
                            if (b.table.borders) body.append("\\clbrdrt\\brdrs\\brdrw10\\clbrdrl\\brdrs\\brdrw10\\clbrdrb\\brdrs\\brdrw10\\clbrdrr\\brdrs\\brdrw10")
                            c.shading?.let { body.append("\\clcbpat").append(colorIdx(it)) }
                            body.append("\\cellx").append(x)
                        }
                        for (c in cells) {
                            body.append("\\pard\\intbl ")
                            c.paragraphs.forEachIndexed { i, p ->
                                if (i > 0) body.append("\\line ")
                                runs(p, body)
                            }
                            body.append("\\cell")
                        }
                        body.append("\\row\n")
                    }
                    body.append("\\pard\\par\n")
                }
                is Block.Image -> { counters.fill(0); body.append("\\pard [Picture]\\par\n") }
                is Block.Shape -> { counters.fill(0); body.append("\\pard [Shape]\\par\n") }
                Block.PageBreak -> { counters.fill(0); body.append("\\page\n") }
            }
        }

        val head = StringBuilder("{\\rtf1\\ansi\\ansicpg1252\\deff0\n{\\fonttbl")
        for ((name, i) in fonts) head.append("{\\f").append(i).append("\\fnil ").append(rtfEscape(name)).append(";}")
        head.append("}\n{\\colortbl;")
        for (rgb in colors.keys) head.append("\\red${(rgb shr 16) and 255}\\green${(rgb shr 8) and 255}\\blue${rgb and 255};")
        head.append("}\n")
        head.append("{\\info{\\title ").append(rtfEscape(doc.title)).append("}{\\author ").append(rtfEscape(doc.author)).append("}}\n")
        head.append("\\paperw${doc.page.sheetWidthTwips}\\paperh${doc.page.sheetHeightTwips}")
        head.append("\\margl${doc.page.marginLeftTwips}\\margr${doc.page.marginRightTwips}")
        head.append("\\margt${doc.page.marginTopTwips}\\margb${doc.page.marginBottomTwips}")
        if (doc.page.landscape) head.append("\\landscape")
        head.append("\n")
        return head.toString() + body + "}"
    }

    /** RTF escaping: backslash and braces, and every non-ASCII UTF-16 unit as a signed \uN? escape. */
    internal fun rtfEscape(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '\\' -> sb.append("\\\\")
                ch == '{' -> sb.append("\\{")
                ch == '}' -> sb.append("\\}")
                ch == '\n' -> sb.append("\\line ")
                ch == '\t' -> sb.append("\\tab ")
                ch.code in 32..126 -> sb.append(ch)
                ch.code < 32 -> {}
                else -> sb.append("\\u").append(ch.code.toShort().toInt()).append('?')
            }
        }
        return sb.toString()
    }
}
