package com.docscan.app.core.word

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a .docx (from Word, LibreOffice, Google Docs, or [DocxWriter]) into a [WordDoc].
 * It keeps what the editor can show: text with character formatting, paragraph styles and alignment, lists,
 * tables (spans, shading, header row), inline pictures, page setup, header/footer text. Everything else
 * (comments, tracked changes, text boxes, fields) degrades to plain text instead of failing.
 */
object DocxReader {
    class DocxException(message: String) : Exception(message)

    // Limits that keep a malicious or corrupt file (zip bomb, millions of entries) from exhausting memory.
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    const val MAX_ENTRIES = 3000

    fun read(input: InputStream, fallbackTitle: String = "Untitled"): WordDoc {
        val parts = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw DocxException("This file has too many parts to be a normal document")
                if (entry.isDirectory) continue
                val bytes = readBounded(zip, MAX_ENTRY_BYTES)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) throw DocxException("File is too large to open")
                parts[entry.name.trimStart('/')] = bytes
            }
        }
        if (parts.isEmpty()) throw DocxException("Not a Word document")
        return Reader(parts, fallbackTitle).run()
    }

    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw DocxException("A part of this file is unreasonably large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray, fallbackTitle: String = "Untitled"): WordDoc =
        read(ByteArrayInputStream(bytes), fallbackTitle)

    // ---------------------------------------------------------------------------------------------------------

    private class StyleInfo(val id: String, val name: String, val basedOn: String?, val isDefault: Boolean, val pPr: XNode?, val rPr: XNode?)

    private class Reader(val parts: Map<String, ByteArray>, val fallbackTitle: String) {
        var rels: Map<String, String> = emptyMap()
        var links: Map<String, String> = emptyMap()
        var styles: Map<String, StyleInfo> = emptyMap()
        var defaultStyleId: String? = null
        var numKinds: Map<String, Map<Int, ListKind>> = emptyMap() // numId -> level -> kind
        var page = PageSetup()

        fun xml(path: String): XNode? {
            val bytes = parts[path] ?: return null
            return try {
                MiniXml.parse(String(bytes, Charsets.UTF_8))
            } catch (e: MiniXml.XmlException) {
                null
            }
        }

        fun run(): WordDoc {
            val mainPath = findMainPart() ?: throw DocxException("This file has no document text")
            val dir = mainPath.substringBeforeLast('/', "")
            val relsPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + mainPath.substringAfterLast('/') + ".rels"
            rels = readRels(relsPath, dir)
            links = readLinks(relsPath)
            readStyles(dir)
            readNumbering(dir)
            val root = xml(mainPath) ?: throw DocxException("The document text could not be read")
            val body = root.child("body") ?: root
            body.child("sectPr")?.let { page = readSection(it) }

            val blocks = ArrayList<Block>()
            readBlocks(body, blocks)
            if (blocks.isEmpty()) blocks += Block.Para(Paragraph())
            val title = xml("docProps/core.xml")?.find("title")?.text?.toString()?.trim().orEmpty()
            return WordDoc(title.ifEmpty { fallbackTitle }, blocks, page)
        }

        fun findMainPart(): String? {
            xml("_rels/.rels")?.findAll("Relationship")?.forEach { r ->
                if (r.attr("Type")?.endsWith("/officeDocument") == true) {
                    r.attr("Target")?.trimStart('/')?.let { if (parts.containsKey(it)) return it }
                }
            }
            return if (parts.containsKey("word/document.xml")) "word/document.xml" else null
        }

        /** External hyperlink targets by relationship id, with the address untouched (readRels turns targets into paths). */
        fun readLinks(path: String): Map<String, String> {
            val node = xml(path) ?: return emptyMap()
            val map = HashMap<String, String>()
            for (r in node.findAll("Relationship")) {
                val id = r.attr("Id") ?: continue
                val target = r.attr("Target") ?: continue
                if (r.attr("TargetMode") == "External" || r.attr("Type")?.endsWith("/hyperlink") == true) {
                    LinkSafety.normalize(target)?.let { map[id] = it }
                }
            }
            return map
        }

        fun readRels(path: String, baseDir: String): Map<String, String> {
            val node = xml(path) ?: return emptyMap()
            val map = HashMap<String, String>()
            for (r in node.findAll("Relationship")) {
                val id = r.attr("Id") ?: continue
                val target = r.attr("Target") ?: continue
                map[id] = resolve(baseDir, target)
            }
            return map
        }

        fun resolve(baseDir: String, target: String): String {
            if (target.startsWith("/")) return target.trimStart('/')
            val segments = ArrayList<String>()
            if (baseDir.isNotEmpty()) segments += baseDir.split('/')
            for (s in target.split('/')) {
                when (s) {
                    "", "." -> {}
                    ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                    else -> segments += s
                }
            }
            return segments.joinToString("/")
        }

        // ---- styles -----------------------------------------------------------------------------------------

        fun readStyles(dir: String) {
            val path = rels.values.firstOrNull { it.endsWith("styles.xml") } ?: (if (dir.isEmpty()) "styles.xml" else "$dir/styles.xml")
            val root = xml(path) ?: return
            val map = HashMap<String, StyleInfo>()
            for (s in root.childrenNamed("style")) {
                if (s.attr("type") != "paragraph" && s.attr("type") != "character") continue
                val id = s.attr("styleId") ?: continue
                val isDefault = s.attr("default") == "1" && s.attr("type") == "paragraph"
                if (isDefault) defaultStyleId = id
                map[id] = StyleInfo(
                    id = id,
                    name = s.child("name")?.attr("val").orEmpty(),
                    basedOn = s.child("basedOn")?.attr("val"),
                    isDefault = isDefault,
                    pPr = s.child("pPr"),
                    rPr = s.child("rPr"),
                )
            }
            styles = map
        }

        fun builtin(info: StyleInfo): ParaStyle? {
            val n = info.name.lowercase().replace(" ", "")
            val i = info.id.lowercase()
            return when {
                n == "title" || i == "title" -> ParaStyle.TITLE
                n == "heading1" || i == "heading1" -> ParaStyle.H1
                n == "heading2" || i == "heading2" -> ParaStyle.H2
                n == "heading3" || i == "heading3" -> ParaStyle.H3
                n == "quote" || n == "intensequote" || i == "quote" || i == "intensequote" -> ParaStyle.QUOTE
                else -> null
            }
        }

        /** Walks a style and its ancestors up to (excluding) the first built-in or default style. */
        class Chain(val custom: List<StyleInfo>, val paraStyle: ParaStyle, val name: String)

        fun chainFor(styleId: String?): Chain {
            val custom = ArrayList<StyleInfo>()
            var para = ParaStyle.NORMAL
            var cur = styleId?.let { styles[it] }
            val first = cur
            var guard = 0
            while (cur != null && guard++ < 20) {
                val b = builtin(cur)
                if (b != null) {
                    para = b
                    break
                }
                if (cur.isDefault || cur.id == defaultStyleId) break
                custom += cur
                cur = cur.basedOn?.let { styles[it] }
            }
            return Chain(custom, para, first?.name.orEmpty())
        }

        fun charStyleRun(styleId: String?): RunStyle {
            var result = RunStyle()
            var cur = styleId?.let { styles[it] }
            var guard = 0
            while (cur != null && guard++ < 20) {
                if (cur.isDefault || cur.id == defaultStyleId) break
                cur.rPr?.let { result = readRPr(it).over(result) }
                cur = cur.basedOn?.let { styles[it] }
            }
            return result
        }

        // ---- numbering --------------------------------------------------------------------------------------

        fun readNumbering(dir: String) {
            val path = rels.values.firstOrNull { it.endsWith("numbering.xml") } ?: return
            val root = xml(path) ?: return
            val abstractKinds = HashMap<String, Map<Int, ListKind>>()
            for (a in root.childrenNamed("abstractNum")) {
                val id = a.attr("abstractNumId") ?: continue
                val levels = HashMap<Int, ListKind>()
                for (lvl in a.childrenNamed("lvl")) {
                    val ilvl = lvl.attr("ilvl")?.toIntOrNull() ?: continue
                    val fmt = lvl.child("numFmt")?.attr("val") ?: "decimal"
                    levels[ilvl] = if (fmt == "bullet" || fmt == "none") ListKind.BULLET else ListKind.NUMBER
                }
                abstractKinds[id] = levels
            }
            val map = HashMap<String, Map<Int, ListKind>>()
            for (n in root.childrenNamed("num")) {
                val id = n.attr("numId") ?: continue
                val abs = n.child("abstractNumId")?.attr("val") ?: continue
                abstractKinds[abs]?.let { map[id] = it }
            }
            numKinds = map
        }

        // ---- section ----------------------------------------------------------------------------------------

        fun readSection(sect: XNode): PageSetup {
            var setup = PageSetup()
            sect.child("pgSz")?.let { sz ->
                val w = sz.attr("w")?.toIntOrNull()
                val h = sz.attr("h")?.toIntOrNull()
                if (w != null && h != null && w > 0 && h > 0) {
                    setup = setup.copy(
                        widthTwips = minOf(w, h),
                        heightTwips = maxOf(w, h),
                        landscape = sz.attr("orient") == "landscape" || w > h,
                    )
                }
            }
            sect.child("pgMar")?.let { m ->
                setup = setup.copy(
                    marginTopTwips = m.attr("top")?.toIntOrNull()?.coerceAtLeast(0) ?: setup.marginTopTwips,
                    marginBottomTwips = m.attr("bottom")?.toIntOrNull()?.coerceAtLeast(0) ?: setup.marginBottomTwips,
                    marginLeftTwips = m.attr("left")?.toIntOrNull()?.coerceAtLeast(0) ?: setup.marginLeftTwips,
                    marginRightTwips = m.attr("right")?.toIntOrNull()?.coerceAtLeast(0) ?: setup.marginRightTwips,
                )
            }
            val headerRef = sect.childrenNamed("headerReference").let { l -> l.firstOrNull { it.attr("type") == "default" } ?: l.firstOrNull() }
            val footerRef = sect.childrenNamed("footerReference").let { l -> l.firstOrNull { it.attr("type") == "default" } ?: l.firstOrNull() }
            headerRef?.attr("id")?.let { id ->
                rels[id]?.let { path -> xml(path)?.let { setup = setup.copy(headerText = partText(it).first) } }
            }
            footerRef?.attr("id")?.let { id ->
                rels[id]?.let { path ->
                    xml(path)?.let {
                        val (text, hasPage) = partText(it)
                        setup = setup.copy(footerText = text, pageNumbers = hasPage)
                    }
                }
            }
            return setup
        }

        /** Text of a header/footer, and whether it has a page-number field. Paragraphs with a page field contribute no text. */
        fun partText(root: XNode): Pair<String, Boolean> {
            val lines = ArrayList<String>()
            var hasPage = false
            for (p in root.findAll("p")) {
                val instr = p.findAll("instrText").joinToString(" ") { it.text.toString() } + " " +
                    p.findAll("fldSimple").joinToString(" ") { it.attr("instr").orEmpty() }
                if (instr.contains("PAGE")) {
                    hasPage = true
                    continue
                }
                val text = p.findAll("t").joinToString("") { it.text.toString() }
                if (text.isNotBlank()) lines += text.trim()
            }
            return lines.joinToString("\n") to hasPage
        }

        // ---- blocks -----------------------------------------------------------------------------------------

        fun readBlocks(container: XNode, out: MutableList<Block>) {
            for (child in container.children) {
                when (child.local) {
                    "p" -> readParagraph(child, out)
                    "tbl" -> readTable(child)?.let { out += Block.Table(it) }
                    "sdt" -> child.child("sdtContent")?.let { readBlocks(it, out) }
                    "customXml", "ins", "smartTag" -> readBlocks(child, out)
                }
            }
        }

        fun truthy(node: XNode?): Boolean {
            if (node == null) return false
            val v = node.attr("val") ?: return true
            return v != "0" && v != "false" && v != "off" && v != "none"
        }

        fun readRPr(rPr: XNode): RunStyle {
            var s = RunStyle()
            if (truthy(rPr.child("b"))) s = s.copy(bold = true)
            if (truthy(rPr.child("i"))) s = s.copy(italic = true)
            if (truthy(rPr.child("strike")) || truthy(rPr.child("dstrike"))) s = s.copy(strike = true)
            rPr.child("u")?.let { if (truthy(it)) s = s.copy(underline = true) }
            rPr.child("color")?.attr("val")?.let { v -> parseHex(v)?.let { s = s.copy(color = it) } }
            rPr.child("sz")?.attr("val")?.toIntOrNull()?.let { if (it > 0) s = s.copy(sizePt = it / 2f) }
            rPr.child("rFonts")?.let { f ->
                val name = f.attr("ascii") ?: f.attr("hAnsi") ?: f.attr("cs") ?: f.attr("eastAsia")
                if (!name.isNullOrBlank()) s = s.copy(font = name)
            }
            rPr.child("highlight")?.attr("val")?.let { v -> namedHighlight(v)?.let { s = s.copy(highlight = it) } }
            rPr.child("shd")?.attr("fill")?.let { v -> parseHex(v)?.let { s = s.copy(highlight = it) } }
            when (rPr.child("vertAlign")?.attr("val")) {
                "superscript" -> s = s.copy(vertical = VerticalAlign.SUPERSCRIPT)
                "subscript" -> s = s.copy(vertical = VerticalAlign.SUBSCRIPT)
            }
            return s
        }

        fun parseHex(v: String): Int? {
            if (v.length != 6) return null
            return v.toIntOrNull(16)
        }

        fun namedHighlight(name: String): Int? = when (name) {
            "yellow" -> 0xFFFF00
            "green" -> 0x00FF00
            "cyan" -> 0x00FFFF
            "magenta" -> 0xFF00FF
            "blue" -> 0x0000FF
            "red" -> 0xFF0000
            "darkBlue" -> 0x000080
            "darkCyan" -> 0x008080
            "darkGreen" -> 0x008000
            "darkMagenta" -> 0x800080
            "darkRed" -> 0x800000
            "darkYellow" -> 0x808000
            "darkGray" -> 0x808080
            "lightGray" -> 0xC0C0C0
            "black" -> 0x000000
            "white" -> 0xFFFFFF
            else -> null
        }

        /** Paragraph reading accumulates runs; images and page breaks split it into separate blocks. */
        inner class ParaReader(val p: XNode, val out: MutableList<Block>, val allowImages: Boolean) {
            val pPr = p.child("pPr")
            val chain = chainFor(pPr?.child("pStyle")?.attr("val"))
            val styleRun: RunStyle = chain.custom.asReversed().fold(RunStyle()) { acc, st -> st.rPr?.let { readRPr(it).over(acc) } ?: acc }
            val runs = ArrayList<Run>()
            var emitted = false
            val base: Paragraph

            init {
                fun prop(local: String): XNode? = pPr?.child(local) ?: chain.custom.firstNotNullOfOrNull { it.pPr?.child(local) }
                var align = Align.LEFT
                when (prop("jc")?.attr("val")) {
                    "center" -> align = Align.CENTER
                    "right", "end" -> align = Align.RIGHT
                    "both", "distribute", "justify" -> align = Align.JUSTIFY
                }
                var list = ListKind.NONE
                var level = 0
                val numPr = prop("numPr")
                val numId = numPr?.child("numId")?.attr("val")
                val ilvl = numPr?.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0
                if (numId != null && numId != "0") {
                    list = numKinds[numId]?.get(ilvl) ?: ListKind.BULLET
                    level = ilvl.coerceIn(0, 8)
                } else if (numId == null) {
                    val n = chain.name.lowercase()
                    if (n.startsWith("list bullet")) list = ListKind.BULLET else if (n.startsWith("list number")) list = ListKind.NUMBER
                }
                val sp = prop("spacing")
                val spaceBefore = sp?.attr("before")?.toIntOrNull()?.let { it / 20f }
                val spaceAfter = sp?.attr("after")?.toIntOrNull()?.let { it / 20f }
                val line = sp?.attr("line")?.toIntOrNull()
                val lineRule = sp?.attr("lineRule") ?: "auto"
                val lineSpacing = if (line != null && lineRule == "auto" && line > 0) line / 240f else null
                val ind = prop("ind")
                val left = (ind?.attr("left") ?: ind?.attr("start"))?.toIntOrNull() ?: 0
                val hanging = ind?.attr("hanging")?.toIntOrNull()
                val firstLine = ind?.attr("firstLine")?.toIntOrNull()
                val first = when {
                    hanging != null -> -hanging
                    firstLine != null -> firstLine
                    else -> 0
                }
                // List indents come from the numbering definition, so only keep explicit non-list indents.
                val keepIndent = list == ListKind.NONE
                base = Paragraph(
                    style = chain.paraStyle,
                    align = align,
                    list = list,
                    level = level,
                    indentLeftTwips = if (keepIndent) left.coerceAtLeast(0) else 0,
                    indentFirstTwips = if (keepIndent) first else 0,
                    spaceBeforePt = spaceBefore,
                    spaceAfterPt = spaceAfter,
                    lineSpacing = lineSpacing,
                    keepWithNext = truthy(prop("keepNext")),
                    shading = prop("shd")?.attr("fill")?.let { parseHex(it) },
                    borderColor = prop("pBdr")?.children?.firstOrNull { it.local == "top" || it.local == "left" }
                        ?.takeIf { (it.attr("val") ?: "nil") !in setOf("nil", "none") }
                        ?.let { parseHex(it.attr("color") ?: "auto") ?: 0x000000 },
                )
            }

            fun addText(text: String, style: RunStyle) {
                if (text.isEmpty()) return
                val last = runs.lastOrNull()
                if (last != null && last.style == style) runs[runs.size - 1] = Run(last.text + text, style) else runs += Run(text, style)
            }

            fun flush(force: Boolean) {
                if (runs.isNotEmpty() || force) {
                    out += Block.Para(base.copy(runs = runs.toList()))
                    runs.clear()
                    emitted = true
                }
            }

            fun walk(node: XNode, inherited: RunStyle) {
                for (c in node.children) {
                    when (c.local) {
                        "r" -> readRun(c, inherited)
                        "hyperlink" -> {
                            val url = c.attr("id")?.let { links[it] }
                            walk(c, if (url != null) inherited.copy(link = url) else inherited)
                        }
                        "ins", "smartTag", "sdtContent", "customXml", "fldSimple", "bookmarkStart" -> walk(c, inherited)
                        "sdt" -> c.child("sdtContent")?.let { walk(it, inherited) }
                    }
                }
            }

            fun readRun(r: XNode, inherited: RunStyle) {
                val rPr = r.child("rPr")
                val direct = rPr?.let { readRPr(it) } ?: RunStyle()
                val style = direct.over(charStyleOf(rPr)).over(styleRun).over(inherited)
                for (c in r.children) {
                    when (c.local) {
                        "t" -> addText(c.text.toString(), style)
                        "tab" -> addText("\t", style)
                        "br" -> if (c.attr("type") == "page") {
                            flush(false)
                            out += Block.PageBreak
                            emitted = true
                        } else addText("\n", style)
                        "cr" -> addText("\n", style)
                        "noBreakHyphen" -> addText("‑", style)
                        "drawing", "pict" -> if (allowImages) readImage(c)
                    }
                }
            }

            fun charStyleOf(rPr: XNode?): RunStyle = charStyleRun(rPr?.child("rStyle")?.attr("val"))

            fun readImage(drawing: XNode) {
                val blip = drawing.find("blip")
                val embed = blip?.attr("embed") ?: drawing.find("imagedata")?.attr("id")
                val path = embed?.let { rels[it] } ?: return
                val bytes = parts[path] ?: return
                val ext = path.substringAfterLast('.', "png").lowercase().let { if (it == "jpeg") "jpg" else it }
                val extent = drawing.find("extent")
                val cx = extent?.attr("cx")?.toLongOrNull()
                val cy = extent?.attr("cy")?.toLongOrNull()
                val w = if (cx != null && cx > 0) (cx / Twips.EMU_PER_TWIP).toInt() else 4320
                val h = if (cy != null && cy > 0) (cy / Twips.EMU_PER_TWIP).toInt() else 3240
                flush(false)
                out += Block.Image(ImageBlock(bytes, ext, w.coerceAtLeast(120), h.coerceAtLeast(120), base.align))
                emitted = true
            }

            fun run() {
                if (pPr?.child("pageBreakBefore") != null && truthy(pPr.child("pageBreakBefore"))) out += Block.PageBreak
                walk(p, RunStyle())
                flush(!emitted)
            }
        }

        fun readParagraph(p: XNode, out: MutableList<Block>) = ParaReader(p, out, allowImages = true).run()

        fun readTable(tbl: XNode): TableBlock? {
            val grid = tbl.child("tblGrid")?.childrenNamed("gridCol")?.mapNotNull { it.attr("w")?.toIntOrNull() }?.toMutableList() ?: mutableListOf()
            val tblPr = tbl.child("tblPr")
            val rowNodes = tbl.childrenNamed("tr")
            if (rowNodes.isEmpty()) return null
            val rows = ArrayList<List<TableCell>>()
            var header = false
            rowNodes.forEachIndexed { index, tr ->
                if (index == 0 && tr.child("trPr")?.child("tblHeader") != null) header = true
                val cells = ArrayList<TableCell>()
                for (tc in tr.children) {
                    val cellNode = when (tc.local) {
                        "tc" -> tc
                        "sdt" -> tc.child("sdtContent")?.child("tc")
                        else -> null
                    } ?: continue
                    val tcPr = cellNode.child("tcPr")
                    val span = tcPr?.child("gridSpan")?.attr("val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val vm = tcPr?.child("vMerge")
                    val fill = tcPr?.child("shd")?.attr("fill")?.let { parseHex(it) }
                    val paragraphs = ArrayList<Paragraph>()
                    for (c in cellNode.children) {
                        if (c.local == "p") {
                            val tmp = ArrayList<Block>()
                            ParaReader(c, tmp, allowImages = false).run()
                            tmp.filterIsInstance<Block.Para>().forEach { paragraphs += it.paragraph }
                        }
                    }
                    cells += TableCell(
                        paragraphs = paragraphs.ifEmpty { listOf(Paragraph()) },
                        shading = fill,
                        colSpan = span,
                        vMergeStart = vm != null && vm.attr("val") == "restart",
                        vMergeContinue = vm != null && vm.attr("val") != "restart",
                    )
                }
                rows += cells
            }
            val columns = maxOf(grid.size, rows.maxOf { r -> r.sumOf { it.colSpan } }).coerceAtLeast(1)
            if (grid.size < columns) {
                val known = grid.sum()
                val fill = if (grid.isEmpty()) page.contentWidthTwips / columns
                else ((page.contentWidthTwips - known) / (columns - grid.size)).coerceAtLeast(360)
                while (grid.size < columns) grid += fill
            }
            val padded = rows.map { row ->
                val used = row.sumOf { it.colSpan }
                if (used < columns) row + List(columns - used) { TableCell() } else row
            }
            val borderNode = tblPr?.child("tblBorders")
            val hasBorders = borderNode?.children?.any { it.attr("val").let { v -> v != null && v != "nil" && v != "none" } } == true ||
                (borderNode == null && tblPr?.child("tblStyle")?.attr("val")?.contains("Grid", ignoreCase = true) == true)
            return TableBlock(padded, grid, header, hasBorders)
        }
    }
}
