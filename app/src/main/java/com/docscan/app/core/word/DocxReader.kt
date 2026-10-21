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
    /** Underline styles kept as-is when a document is opened and saved again (single is the default and is not listed). */
    val UNDERLINE_WORDS = setOf(
        "double", "thick", "dotted", "dottedHeavy", "dash", "dashedHeavy", "dashLong", "dashLongHeavy",
        "dotDash", "dashDotHeavy", "dotDotDash", "dashDotDotHeavy", "wave", "wavyHeavy", "wavyDouble", "words",
    )

    class DocxException(message: String) : Exception(message)

    // Limits that keep a malicious or corrupt file (zip bomb, millions of entries) from exhausting memory.
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    const val MAX_ENTRIES = 3000

    /** What to do with tracked changes (Word's insertions and deletions) when opening a file. */
    enum class Tracked { ACCEPT, REJECT, MARKUP }

    private val trackedMode = ThreadLocal.withInitial { Tracked.ACCEPT }

    /** How many tracked insertions and deletions the file holds (0 when it has none or cannot be read). */
    fun trackedCount(bytes: ByteArray): Int {
        var doc = ""
        runCatching {
            ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
                var entries = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) break
                    if (e.name.trimStart('/') == "word/document.xml") { doc = String(readBounded(zip, MAX_ENTRY_BYTES), Charsets.UTF_8); break }
                }
            }
        }
        return Regex("<w:(ins|del) ").findAll(doc).count()
    }

    fun read(input: InputStream, fallbackTitle: String = "Untitled", tracked: Tracked = Tracked.ACCEPT): WordDoc {
        trackedMode.set(tracked)
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

    /**
     * What a file contains that Docxels cannot keep yet. Shown to the user after opening, so nothing is lost without
     * them knowing. Returns an empty list for a plain document.
     */
    fun compatNotes(bytes: ByteArray): List<String> {
        val names = HashSet<String>()
        var doc = ""
        var footnotes = ""
        var comments = ""
        runCatching {
            ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
                var entries = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) break
                    if (e.isDirectory) continue
                    val n = e.name.trimStart('/')
                    names += n
                    when (n) {
                        "word/document.xml" -> doc = String(readBounded(zip, MAX_ENTRY_BYTES), Charsets.UTF_8)
                        "word/footnotes.xml" -> footnotes = String(readBounded(zip, MAX_ENTRY_BYTES), Charsets.UTF_8)
                        "word/comments.xml" -> comments = String(readBounded(zip, MAX_ENTRY_BYTES), Charsets.UTF_8)
                    }
                }
            }
        }
        fun count(text: String, token: String): Int {
            var i = 0
            var c = 0
            while (true) {
                i = text.indexOf(token, i)
                if (i < 0) return c
                c++
                i += token.length
            }
        }
        val notes = ArrayList<String>()
        val fn = count(footnotes, "<w:footnote ") - count(footnotes, "w:type=\"separator\"") - count(footnotes, "w:type=\"continuationSeparator\"")
        if (fn > 0) notes += "footnotes ($fn) moved to a Notes list at the end"
        if ("word/endnotes.xml" in names) {
            val en = count(doc, "<w:endnoteReference")
            if (en > 0) notes += "endnotes ($en)"
        }
        if (names.any { it.startsWith("word/charts/") }) notes += "charts"
        if (names.any { it.startsWith("word/diagrams/") }) notes += "SmartArt diagrams"
        if (names.any { it.startsWith("word/embeddings/") }) notes += "embedded objects"
        if ("word/vbaProject.bin" in names) notes += "macros (never run, not kept)"
        val eq = count(doc, "<m:oMath")
        if (eq > 0) notes += "equations ($eq)"
        val boxes = count(doc, "<w:txbxContent") - count(doc, "wordprocessingShape")
        if (boxes > 0) notes += "text boxes"
        val sections = count(doc, "<w:sectPr")
        if (sections > 1) notes += "several sections (only the last page setup is kept)"
        if (Regex("<w:cols [^>]*w:num=\"([2-9]|\\d\\d)\"").containsMatchIn(doc)) notes += "multiple columns (shown as one column; the column setting is kept when you save)"
        if (count(doc, "<w:sdt>") + count(doc, "<w:sdt ") > 0) notes += "content controls (their text is kept)"
        return notes
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

    /** Undoes Word's font scrambling: the first 32 bytes are XORed with the (reversed) GUID in the font key. Null if the result is not a font. */
    fun deobfuscateFont(data: ByteArray, fontKey: String): ByteArray? {
        val hex = fontKey.filter { it.isLetterOrDigit() }
        if (hex.length != 32 || data.size < 64 || data.size > 8_000_000) return null
        val raw = ByteArray(16)
        for (i in 0 until 16) raw[i] = (hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null).toByte()
        val key = raw.reversedArray()
        val out = data.copyOf()
        for (i in 0 until 32) out[i] = (out[i].toInt() xor key[i % 16].toInt()).toByte()
        val tag = String(out, 0, 4, Charsets.ISO_8859_1)
        val ok = (out[0].toInt() == 0 && out[1].toInt() == 1 && out[2].toInt() == 0 && out[3].toInt() == 0) || tag == "true" || tag == "OTTO" || tag == "ttcf"
        return if (ok) out else null
    }

    fun read(bytes: ByteArray, fallbackTitle: String = "Untitled", tracked: Tracked = Tracked.ACCEPT): WordDoc =
        read(ByteArrayInputStream(bytes), fallbackTitle, tracked)

    // ---------------------------------------------------------------------------------------------------------

    private class StyleInfo(val id: String, val name: String, val basedOn: String?, val isDefault: Boolean, val pPr: XNode?, val rPr: XNode?)

    /** A table style: the look every cell gets (font, colour, shading), plus the extra look of first row, banded rows and so on. */
    private class TableStyleInfo(val id: String, val basedOn: String?, val rPr: XNode?, val fill: Int?, val conds: Map<String, Pair<XNode?, Int?>>)

    /** What a table style gives one cell: text style and shading. */
    private class CellLook(val run: RunStyle, val fill: Int?)

    private class Reader(val parts: Map<String, ByteArray>, val fallbackTitle: String) {
        var rels: Map<String, String> = emptyMap()
        var links: Map<String, String> = emptyMap()
        var styles: Map<String, StyleInfo> = emptyMap()
        var tableStyles: Map<String, TableStyleInfo> = emptyMap()
        var defaultStyleId: String? = null
        /** The document's own body font when it is not Calibri (from docDefaults / the Normal style / the theme). */
        var defaultFont: String? = null
        var comments: Map<Int, DocComment> = emptyMap()
        /** The comment whose range we are inside while reading (ranges can span paragraphs). */
        var activeComment: Int? = null
        var themeMajor: String? = null
        var themeMinor: String? = null
        var numKinds: Map<String, Map<Int, ListKind>> = emptyMap() // numId -> level -> kind
        var page = PageSetup()
        /** Footnotes and endnotes in the order they are cited; their text is added at the end so nothing is lost. */
        val cited = ArrayList<Pair<String, String>>() // kind ("footnote"/"endnote") to id

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
            readTheme()
            readComments(dir)
            readStyles(dir)
            readNumbering(dir)
            val root = xml(mainPath) ?: throw DocxException("The document text could not be read")
            val body = root.child("body") ?: root
            body.child("sectPr")?.let { page = readSection(it) }

            val blocks = ArrayList<Block>()
            readBlocks(body, blocks)
            appendNotes(dir, blocks)
            if (blocks.isEmpty()) blocks += Block.Para(Paragraph())
            val title = xml("docProps/core.xml")?.find("title")?.text?.toString()?.trim().orEmpty()
            val used = HashSet<Int>()
            fun scan(bs: List<Block>) {
                for (b in bs) if (b is Block.Para) b.paragraph.runs.forEach { r -> r.style.comment?.let { used += it } }
            }
            scan(blocks)
            val kept = comments.values.filter { it.id in used || comments.size <= 200 }.sortedBy { it.id }
            return WordDoc(title.ifEmpty { fallbackTitle }, blocks, page, readEmbeddedFonts(dir), kept)
        }

        /** Footnote and endnote text, added under a "Notes" heading with the [n] numbers cited in the text. */
        fun appendNotes(dir: String, blocks: MutableList<Block>) {
            if (cited.isEmpty()) return
            val texts = HashMap<String, String>()
            for (kind in listOf("footnote", "endnote")) {
                val root = xml((if (dir.isEmpty()) "" else "$dir/") + kind + "s.xml") ?: continue
                for (n in root.findAll(kind)) {
                    val id = n.attr("id") ?: continue
                    texts["$kind:$id"] = n.findAll("p").joinToString(" ") { p -> p.findAll("t").joinToString("") { it.text.toString() } }.trim()
                }
            }
            blocks += Block.Para(Paragraph(listOf(Run("Notes", RunStyle(bold = true)))))
            cited.forEachIndexed { i, (kind, id) ->
                val t = texts["$kind:$id"].orEmpty()
                blocks += Block.Para(Paragraph(listOf(Run("[${i + 1}] $t"))))
            }
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
            val tmap = HashMap<String, TableStyleInfo>()
            for (s in root.childrenNamed("style")) {
                if (s.attr("type") != "table") continue
                val id = s.attr("styleId") ?: continue
                fun fillOf(tcPr: XNode?): Int? = tcPr?.child("shd")?.attr("fill")?.let { parseHex(it) }
                val conds = HashMap<String, Pair<XNode?, Int?>>()
                for (c in s.childrenNamed("tblStylePr")) {
                    val type = c.attr("type") ?: continue
                    conds[type] = c.child("rPr") to fillOf(c.child("tcPr"))
                }
                tmap[id] = TableStyleInfo(id, s.child("basedOn")?.attr("val"), s.child("rPr"), fillOf(s.child("tcPr")), conds)
            }
            tableStyles = tmap
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
            fun fontOf(rPr: XNode?): String? = rPr?.child("rFonts")?.let { fontName(it) }
            val fromDefaults = fontOf(root.child("docDefaults")?.child("rPrDefault")?.child("rPr"))
            val fromNormal = fontOf(defaultStyleId?.let { map[it]?.rPr })
            defaultFont = (fromNormal ?: fromDefaults)?.takeIf { !it.equals(StyleDefaults.BODY_FONT, ignoreCase = true) }
        }

        /** Fonts the file carries (the .odttf files in word/fonts, scrambled with the font key); unreadable ones are skipped. */
        fun readEmbeddedFonts(dir: String): List<EmbeddedFont> {
            val base = if (dir.isEmpty()) "" else "$dir/"
            val table = xml(base + "fontTable.xml") ?: return emptyList()
            val frels = readRels(base + "_rels/fontTable.xml.rels", dir)
            if (frels.isEmpty()) return emptyList()
            val out = ArrayList<EmbeddedFont>()
            for (f in table.childrenNamed("font")) {
                val name = f.attr("name") ?: continue
                for ((tag, style) in listOf("embedRegular" to 0, "embedBold" to 1, "embedItalic" to 2, "embedBoldItalic" to 3)) {
                    val e = f.child(tag) ?: continue
                    val data = frels[e.attr("id") ?: continue]?.let { parts[it] } ?: continue
                    val key = e.attr("fontKey") ?: continue
                    val plain = deobfuscateFont(data, key) ?: continue
                    out += EmbeddedFont(name, style, plain)
                }
            }
            return out
        }

        fun readComments(dir: String) {
            val path = rels.values.firstOrNull { it.endsWith("comments.xml") } ?: (if (dir.isEmpty()) "comments.xml" else "$dir/comments.xml")
            val root = xml(path) ?: return
            val map = LinkedHashMap<Int, DocComment>()
            for (c in root.childrenNamed("comment")) {
                val id = c.attr("id")?.toIntOrNull() ?: continue
                val text = c.findAll("p").joinToString("\n") { p -> p.findAll("t").joinToString("") { it.text.toString() } }.trim()
                map[id] = DocComment(id, c.attr("author")?.takeIf { it.isNotBlank() } ?: "Someone", text)
                if (map.size >= 500) break
            }
            comments = map
        }

        fun commentMarker(n: XNode) {
            val id = n.attr("id")?.toIntOrNull()
            when (n.local) {
                "commentRangeStart" -> if (id != null && id in comments) activeComment = id
                "commentRangeEnd" -> if (id == activeComment) activeComment = null
            }
        }

        fun readTheme() {
            val path = rels.values.firstOrNull { it.contains("theme/") && it.endsWith(".xml") } ?: "word/theme/theme1.xml"
            val root = xml(path) ?: return
            themeMajor = root.find("majorFont")?.child("latin")?.attr("typeface")?.takeIf { it.isNotBlank() }
            themeMinor = root.find("minorFont")?.child("latin")?.attr("typeface")?.takeIf { it.isNotBlank() }
        }

        /** The font a w:rFonts element names; theme fonts (minorHAnsi, majorHAnsi…) are looked up in the theme. */
        fun fontName(f: XNode): String? {
            val theme = f.attr("asciiTheme") ?: f.attr("hAnsiTheme")
            val fromTheme = when {
                theme == null -> null
                theme.startsWith("minor") -> themeMinor
                theme.startsWith("major") -> themeMajor
                else -> null
            }
            return (fromTheme ?: f.attr("ascii") ?: f.attr("hAnsi") ?: f.attr("cs") ?: f.attr("eastAsia"))?.takeIf { it.isNotBlank() }
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
        class Chain(val custom: List<StyleInfo>, val paraStyle: ParaStyle, val name: String, val builtinFont: String? = null)

        fun chainFor(styleId: String?): Chain {
            val custom = ArrayList<StyleInfo>()
            var para = ParaStyle.NORMAL
            var cur = styleId?.let { styles[it] }
            val first = cur
            var guard = 0
            var bFont: String? = null
            while (cur != null && guard++ < 20) {
                val b = builtin(cur)
                if (b != null) {
                    para = b
                    bFont = cur.rPr?.child("rFonts")?.let { fontName(it) }
                    break
                }
                if (cur.isDefault || cur.id == defaultStyleId) break
                custom += cur
                cur = cur.basedOn?.let { styles[it] }
            }
            return Chain(custom, para, first?.name.orEmpty(), bFont)
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
                    headerDistTwips = m.attr("header")?.toIntOrNull()?.coerceAtLeast(0) ?: setup.headerDistTwips,
                    footerDistTwips = m.attr("footer")?.toIntOrNull()?.coerceAtLeast(0) ?: setup.footerDistTwips,
                )
            }
            sect.child("cols")?.attr("num")?.toIntOrNull()?.let { n -> if (n in 2..6) setup = setup.copy(columns = n) }
            val headerRef = sect.childrenNamed("headerReference").let { l -> l.firstOrNull { it.attr("type") == "default" } ?: l.firstOrNull { it.attr("type") == null } }
            val footerRef = sect.childrenNamed("footerReference").let { l -> l.firstOrNull { it.attr("type") == "default" } ?: l.firstOrNull { it.attr("type") == null } }
            val firstHeaderRef = sect.childrenNamed("headerReference").firstOrNull { it.attr("type") == "first" }
            val firstFooterRef = sect.childrenNamed("footerReference").firstOrNull { it.attr("type") == "first" }
            if (sect.child("titlePg") != null) {
                setup = setup.copy(differentFirstPage = true)
                firstHeaderRef?.attr("id")?.let { id ->
                    rels[id]?.let { path -> xml(path)?.let { setup = setup.copy(firstHeaderText = partText(it).first, firstHeaderImages = partImages(path, it, false, setup)) } }
                }
                firstFooterRef?.attr("id")?.let { id ->
                    rels[id]?.let { path ->
                        xml(path)?.let {
                            val (text, hasPage) = partText(it)
                            setup = setup.copy(firstFooterText = text, firstPageNumbers = hasPage, firstFooterImages = partImages(path, it, true, setup))
                        }
                    }
                }
            }
            val evenOdd = (rels.values.firstOrNull { it.endsWith("settings.xml") } ?: "word/settings.xml").let { xml(it)?.child("evenAndOddHeaders") != null }
            if (evenOdd) {
                setup = setup.copy(differentOddEven = true)
                sect.childrenNamed("headerReference").firstOrNull { it.attr("type") == "even" }?.attr("id")?.let { id ->
                    rels[id]?.let { path -> xml(path)?.let { setup = setup.copy(evenHeaderText = partText(it).first) } }
                }
                sect.childrenNamed("footerReference").firstOrNull { it.attr("type") == "even" }?.attr("id")?.let { id ->
                    rels[id]?.let { path -> xml(path)?.let { setup = setup.copy(evenFooterText = partText(it).first) } }
                }
            }
            headerRef?.attr("id")?.let { id ->
                rels[id]?.let { path -> xml(path)?.let { val info = partText(it); setup = setup.copy(headerText = info.first, headerAlign = info.align, headerImages = partImages(path, it, false, setup)) } }
            }
            footerRef?.attr("id")?.let { id ->
                rels[id]?.let { path ->
                    xml(path)?.let {
                        val (text, hasPage) = partText(it)
                        setup = setup.copy(footerText = text, pageNumbers = hasPage, footerAlign = partText(it).align, footerImages = partImages(path, it, true, setup))
                    }
                }
            }
            return pushMargins(setup)
        }

        /** Letterhead pictures of one header/footer part, placed absolutely on the sheet. */
        fun partImages(path: String, root: XNode, footer: Boolean, setup: PageSetup): List<PageImage> {
            val dir = path.substringBeforeLast('/', "")
            val partRels = readRels((if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels", dir)
            val out = ArrayList<PageImage>()
            val sheetW = setup.sheetWidthTwips
            val sheetH = setup.sheetHeightTwips
            fun emu(n: XNode?): Int? = n?.text?.toString()?.trim()?.toLongOrNull()?.let { (it / Twips.EMU_PER_TWIP).toInt() }
            for (p in root.findAll("p")) {
                val jc = p.child("pPr")?.child("jc")?.attr("val")
                for (d in p.findAll("drawing")) {
                    val blip = d.find("blip") ?: continue
                    val rid = blip.attr("embed") ?: continue
                    val target = partRels[rid] ?: continue
                    val bytes = parts[target] ?: continue
                    val ext = target.substringAfterLast('.', "png").lowercase().let { if (it == "jpeg") "jpg" else it }
                    if (ext != "png" && ext != "jpg" && ext != "gif" && ext != "bmp") continue
                    val extent = d.find("extent")
                    val w = ((extent?.attr("cx")?.toLongOrNull() ?: 0L) / Twips.EMU_PER_TWIP).toInt()
                    val h = ((extent?.attr("cy")?.toLongOrNull() ?: 0L) / Twips.EMU_PER_TWIP).toInt()
                    if (w <= 0 || h <= 0) continue
                    val anchor = d.find("anchor")
                    var x: Int
                    var y: Int
                    var behind = false
                    if (anchor == null) {
                        val area = sheetW - setup.marginLeftTwips - setup.marginRightTwips
                        x = setup.marginLeftTwips + when (jc) { "center" -> (area - w) / 2; "right" -> area - w; else -> 0 }
                        y = if (footer) sheetH - setup.footerDistTwips - h else setup.headerDistTwips
                    } else {
                        behind = anchor.attr("behindDoc").let { v -> v == "1" || v == "true" }
                        val ph = anchor.child("positionH")
                        val pv = anchor.child("positionV")
                        val hRel = ph?.attr("relativeFrom") ?: "column"
                        val vRel = pv?.attr("relativeFrom") ?: "paragraph"
                        val hOrigin = if (hRel == "page") 0 else setup.marginLeftTwips
                        val hArea = if (hRel == "page") sheetW else sheetW - setup.marginLeftTwips - setup.marginRightTwips
                        x = hOrigin + (emu(ph?.child("posOffset")) ?: when (ph?.child("align")?.text?.toString()?.trim()) {
                            "center" -> (hArea - w) / 2
                            "right" -> hArea - w
                            else -> 0
                        })
                        val vOrigin = when (vRel) {
                            "page" -> 0
                            "margin", "topMargin", "insideMargin", "outsideMargin" -> if (footer) sheetH - setup.marginBottomTwips else setup.marginTopTwips
                            else -> if (footer) sheetH - setup.footerDistTwips - 300 else setup.headerDistTwips
                        }
                        val vArea = if (vRel == "page") sheetH else sheetH - setup.marginTopTwips - setup.marginBottomTwips
                        y = vOrigin + (emu(pv?.child("posOffset")) ?: when (pv?.child("align")?.text?.toString()?.trim()) {
                            "center" -> (vArea - h) / 2
                            "bottom" -> vArea - h
                            else -> 0
                        })
                    }
                    out += PageImage(bytes, ext, x, y, w, h, behind)
                }
                // Older Word files draw header pictures with VML.
                for (pict in p.findAll("pict")) {
                    val data = pict.find("imagedata") ?: continue
                    val rid = data.attr("id") ?: continue
                    val target = partRels[rid] ?: continue
                    val bytes = parts[target] ?: continue
                    val ext = target.substringAfterLast('.', "png").lowercase().let { if (it == "jpeg") "jpg" else it }
                    if (ext != "png" && ext != "jpg" && ext != "gif" && ext != "bmp") continue
                    val style = (pict.find("shape") ?: pict.find("rect"))?.attr("style").orEmpty()
                    val css = style.split(';').mapNotNull { kv -> kv.indexOf(':').takeIf { it > 0 }?.let { kv.substring(0, it).trim() to kv.substring(it + 1).trim() } }.toMap()
                    fun len(v: String?): Int? {
                        val m = Regex("(-?[0-9.]+)\\s*(pt|in|cm|mm|px)?").matchEntire(v ?: return null) ?: return null
                        val n = m.groupValues[1].toFloatOrNull() ?: return null
                        val pt = when (m.groupValues[2]) { "in" -> n * 72f; "cm" -> n * 28.3465f; "mm" -> n * 2.83465f; "px" -> n * 0.75f; else -> n }
                        return (pt * 20f).toInt()
                    }
                    val w = len(css["width"]) ?: continue
                    val h = len(css["height"]) ?: continue
                    if (w <= 0 || h <= 0) continue
                    val absolute = css["position"] == "absolute"
                    val hRel = css["mso-position-horizontal-relative"] ?: "margin"
                    val vRel = css["mso-position-vertical-relative"] ?: "margin"
                    val mx = len(css["margin-left"]) ?: 0
                    val my = len(css["margin-top"]) ?: 0
                    val x = (if (hRel == "page") 0 else setup.marginLeftTwips) + if (absolute) mx else 0
                    val y = if (absolute) {
                        (if (vRel == "page") 0 else if (footer) sheetH - setup.marginBottomTwips else setup.marginTopTwips) + my
                    } else if (footer) sheetH - setup.footerDistTwips - h else setup.headerDistTwips
                    val behind = (css["z-index"]?.toIntOrNull() ?: 0) < 0
                    out += PageImage(bytes, ext, x, y, w, h, behind)
                }
            }
            return out
        }

        /** Word moves the body out of the way of header/footer pictures that sit in the flow; do the same. */
        fun pushMargins(setup: PageSetup): PageSetup {
            fun pushesTop(list: List<PageImage>) = list.filter { !it.behind && it.yTwips < setup.sheetHeightTwips / 2 }.maxOfOrNull { it.yTwips + it.heightTwips }
            fun pushesBottom(list: List<PageImage>) = list.filter { !it.behind && it.yTwips >= setup.sheetHeightTwips / 2 }.minOfOrNull { it.yTwips }
            val top = listOfNotNull(pushesTop(setup.headerImages), pushesTop(setup.firstHeaderImages)).maxOrNull()
            val bottom = listOfNotNull(pushesBottom(setup.footerImages), pushesBottom(setup.firstFooterImages)).minOrNull()
            var out = setup
            if (top != null && top + 60 > out.marginTopTwips) out = out.copy(marginTopTwips = (top + 60).coerceAtMost(out.sheetHeightTwips / 3))
            if (bottom != null && out.sheetHeightTwips - bottom + 60 > out.marginBottomTwips) {
                out = out.copy(marginBottomTwips = (out.sheetHeightTwips - bottom + 60).coerceAtMost(out.sheetHeightTwips / 3))
            }
            return out
        }

        /** What a header/footer part holds: its text (placeholders for page fields), whether it has a lone page number, and alignment. */
        data class PartInfo(val first: String, val second: Boolean, val align: Int)

        /** Text of one paragraph in reading order; PAGE and NUMPAGES fields become {page} and {pages}. */
        fun paragraphWithFields(p: XNode): String {
            val sb = StringBuilder()
            var state = 0 // 0 plain text, 1 inside a field's code, 2 inside a field's shown result
            val code = StringBuilder()
            fun token(instr: String): String? = when {
                instr.contains("NUMPAGES") -> HeaderFooter.PAGES
                instr.contains("PAGE") -> HeaderFooter.PAGE
                else -> null
            }
            fun walk(n: XNode) {
                for (c in n.children) {
                    when (c.local) {
                        "pPr", "rPr" -> {}
                        "t" -> if (state != 2) sb.append(c.text)
                        "instrText" -> if (state == 1) code.append(c.text)
                        "fldChar" -> when (c.attr("fldCharType")) {
                            "begin" -> { state = 1; code.setLength(0) }
                            "separate" -> { token(code.toString())?.let { sb.append(it) }; state = 2 }
                            "end" -> { if (state == 1) token(code.toString())?.let { sb.append(it) }; state = 0 }
                        }
                        "fldSimple" -> token(c.attr("instr").orEmpty())?.let { sb.append(it) } ?: walk(c)
                        else -> walk(c)
                    }
                }
            }
            walk(p)
            return sb.toString()
        }

        fun partText(root: XNode): PartInfo {
            val lines = ArrayList<String>()
            var hasPage = false
            var align: Int? = null
            for (p in root.findAll("p")) {
                val text = paragraphWithFields(p).trim()
                if (text == HeaderFooter.PAGE) { hasPage = true; continue }
                if (text.isEmpty()) continue
                if (align == null) align = when (p.child("pPr")?.child("jc")?.attr("val")) { "center" -> 1; "right", "end" -> 2; else -> 0 }
                lines += text
            }
            return PartInfo(lines.joinToString("\n"), hasPage, align ?: 1)
        }

        // ---- blocks -----------------------------------------------------------------------------------------

        fun readBlocks(container: XNode, out: MutableList<Block>) {
            for (child in container.children) {
                when (child.local) {
                    "p" -> readParagraph(child, out)
                    "commentRangeStart", "commentRangeEnd" -> commentMarker(child)
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
            rPr.child("u")?.let {
                if (truthy(it)) {
                    val v = it.attr("val")
                    s = s.copy(underline = true, underlineStyle = v?.takeIf { w -> w in UNDERLINE_WORDS })
                }
            }
            rPr.child("color")?.attr("val")?.let { v -> parseHex(v)?.let { s = s.copy(color = it) } }
            rPr.child("sz")?.attr("val")?.toIntOrNull()?.let { if (it > 0) s = s.copy(sizePt = it / 2f) }
            rPr.child("rFonts")?.let { f ->
                val name = fontName(f)
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
        inner class ParaReader(val p: XNode, val out: MutableList<Block>, val allowImages: Boolean, val tableRun: RunStyle = RunStyle()) {
            val pPr = p.child("pPr")
            val chain = chainFor(pPr?.child("pStyle")?.attr("val"))
            val styleRun: RunStyle = chain.custom.asReversed().fold(RunStyle(font = chain.builtinFont ?: tableRun.font ?: defaultFont)) { acc, st -> st.rPr?.let { readRPr(it).over(acc) } ?: acc }
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
                    rtl = truthy(prop("bidi")),
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
                        "commentRangeStart", "commentRangeEnd" -> commentMarker(c)
                        "hyperlink" -> {
                            val url = c.attr("id")?.let { links[it] }
                            walk(c, if (url != null) inherited.copy(link = url) else inherited)
                        }
                        "ins" -> when (trackedMode.get()) {
                            Tracked.REJECT -> Unit
                            Tracked.MARKUP -> walk(c, inherited.copy(underline = true, color = 0x1F5FBF))
                            else -> walk(c, inherited)
                        }
                        "del" -> when (trackedMode.get()) {
                            Tracked.ACCEPT -> Unit
                            Tracked.REJECT -> walk(c, inherited)
                            Tracked.MARKUP -> walk(c, inherited.copy(strike = true, color = 0xC00000))
                        }
                        "smartTag", "sdtContent", "customXml", "fldSimple", "bookmarkStart" -> walk(c, inherited)
                        "sdt" -> c.child("sdtContent")?.let { walk(it, inherited) }
                    }
                }
            }

            fun readRun(r: XNode, inherited: RunStyle) {
                val rPr = r.child("rPr")
                val direct = rPr?.let { readRPr(it) } ?: RunStyle()
                val style = direct.over(charStyleOf(rPr)).over(styleRun).over(inherited).let { st -> activeComment?.let { st.copy(comment = it) } ?: st }
                for (c in r.children) {
                    when (c.local) {
                        "t", "delText" -> addText(c.text.toString(), style)
                        "tab" -> addText("\t", style)
                        "br" -> if (c.attr("type") == "page") {
                            flush(false)
                            out += Block.PageBreak
                            emitted = true
                        } else addText("\n", style)
                        "cr" -> addText("\n", style)
                        "noBreakHyphen" -> addText("‑", style)
                        "footnoteReference", "endnoteReference" -> {
                            val id = c.attr("id")
                            if (id != null && allowImages) {
                                cited += c.local.removeSuffix("Reference") to id
                                addText("[${cited.size}]", style.copy(vertical = VerticalAlign.SUPERSCRIPT))
                            }
                        }
                        "drawing", "pict" -> if (allowImages) readImage(c)
                        "AlternateContent" -> if (allowImages) {
                            c.childrenNamed("Choice").firstNotNullOfOrNull { it.child("drawing") }?.let { readImage(it) }
                        }
                    }
                }
            }

            fun charStyleOf(rPr: XNode?): RunStyle = charStyleRun(rPr?.child("rStyle")?.attr("val"))

            fun wrapOf(drawing: XNode): ImageWrap {
                val anchor = drawing.find("anchor") ?: return ImageWrap.IN_LINE
                return when {
                    anchor.child("wrapSquare") != null -> ImageWrap.SQUARE
                    anchor.child("wrapTight") != null -> ImageWrap.TIGHT
                    anchor.child("wrapThrough") != null -> ImageWrap.THROUGH
                    anchor.child("wrapTopAndBottom") != null -> ImageWrap.TOP_BOTTOM
                    anchor.attr("behindDoc").let { v -> v == "1" || v == "true" } -> ImageWrap.BEHIND
                    else -> ImageWrap.IN_FRONT
                }
            }

            /** Offsets of a floating object from the left margin and the anchor paragraph's top, in twips. */
            fun offsetsOf(drawing: XNode, wTwips: Int): Pair<Int, Int> {
                val anchor = drawing.find("anchor") ?: return 0 to 0
                fun emu(n: XNode?): Int? = n?.text?.toString()?.trim()?.toLongOrNull()?.let { (it / Twips.EMU_PER_TWIP).toInt() }
                val ph = anchor.child("positionH")
                val pv = anchor.child("positionV")
                val content = page.contentWidthTwips
                val x = emu(ph?.child("posOffset"))?.let { off -> if (ph?.attr("relativeFrom") == "page") off - page.marginLeftTwips else off }
                    ?: when (ph?.child("align")?.text?.toString()?.trim()) {
                        "center" -> (content - wTwips) / 2
                        "right" -> content - wTwips
                        else -> 0
                    }
                val y = emu(pv?.child("posOffset"))?.let { off -> if (pv?.attr("relativeFrom") == "page") off - page.marginTopTwips else off } ?: 0
                return x to y
            }

            /** Offsets from the sheet's corner when a picture behind the text is positioned relative to the page on both axes. */
            fun pageAbsolute(drawing: XNode): Pair<Int, Int>? {
                val anchor = drawing.find("anchor") ?: return null
                val ph = anchor.child("positionH")
                val pv = anchor.child("positionV")
                if (ph?.attr("relativeFrom") != "page" || pv?.attr("relativeFrom") != "page") return null
                fun emu(n: XNode?): Int? = n?.text?.toString()?.trim()?.toLongOrNull()?.let { (it / Twips.EMU_PER_TWIP).toInt() }
                val x = emu(ph.child("posOffset")) ?: return null
                val y = emu(pv.child("posOffset")) ?: return null
                return x to y
            }

            fun readShape(drawing: XNode, wsp: XNode) {
                val extent = drawing.find("extent")
                val cx = extent?.attr("cx")?.toLongOrNull() ?: 0L
                val cy = extent?.attr("cy")?.toLongOrNull() ?: 0L
                val spPr = wsp.child("spPr")
                val isTextBox = wsp.child("cNvSpPr")?.attr("txBox") == "1"
                val preset = if (isTextBox) "textbox" else (spPr?.child("prstGeom")?.attr("prst") ?: "rect")
                val styled = wsp.child("style") != null
                fun colorOf(holder: XNode?, fallback: Int?): Int? {
                    val sf = holder?.child("solidFill") ?: return fallback
                    sf.child("srgbClr")?.attr("val")?.let { v -> parseHex(v)?.let { return it } }
                    return fallback ?: 0x000000
                }
                val fill = if (spPr?.child("noFill") != null) null else colorOf(spPr, if (styled) 0x4472C4 else null)
                val ln = spPr?.child("ln")
                val lineColor = colorOf(ln, if (styled) 0x2F528F else 0x000000) ?: 0x000000
                val lineW = if (ln?.child("noFill") != null) 0f else (ln?.attr("w")?.toFloatOrNull()?.div(12700f) ?: if (styled) 1f else 0.75f)
                flush(false)
                // Plain, single-style text goes inside the shape; anything richer stays as ordinary paragraphs after it.
                val box = wsp.find("txbxContent")
                val paras = box?.childrenNamed("p").orEmpty()
                val runs = paras.flatMap { it.childrenNamed("r") }
                val styleKeys = runs.map { r ->
                    val rp = r.child("rPr")
                    listOf(rp?.child("b") != null, rp?.child("sz")?.attr("val"), rp?.child("color")?.attr("val"))
                }.distinct()
                val simple = box != null && paras.size in 1..12 && styleKeys.size <= 1 && box.find("tbl") == null && box.find("drawing") == null && !ShapePresets.isLine(preset)
                val inner = if (simple) paras.joinToString("\n") { p -> p.childrenNamed("r").joinToString("") { r -> r.childrenNamed("t").joinToString("") { it.text.toString() } } } else ""
                val first = runs.firstOrNull()?.child("rPr")
                flush(false)
                out += Block.Shape(
                    ShapeBlock(
                        text = inner,
                        textBold = first?.child("b") != null,
                        textSizePt = first?.child("sz")?.attr("val")?.toFloatOrNull()?.div(2f)?.coerceIn(4f, 96f) ?: 11f,
                        textColor = first?.child("color")?.attr("val")?.let { parseHex(it) } ?: 0x000000,
                        preset = preset,
                        widthTwips = (cx / Twips.EMU_PER_TWIP).toInt().coerceAtLeast(120),
                        heightTwips = (cy / Twips.EMU_PER_TWIP).toInt().coerceAtLeast(0),
                        fill = fill, lineColor = lineColor, lineWidthPt = lineW, align = base.align, wrap = wrapOf(drawing),
                        offsetXTwips = offsetsOf(drawing, (cx / Twips.EMU_PER_TWIP).toInt()).first,
                        offsetYTwips = offsetsOf(drawing, (cx / Twips.EMU_PER_TWIP).toInt()).second,
                    ),
                )
                emitted = true
                // Text typed inside the shape is kept as ordinary paragraphs after it, so it is never lost.
                if (!simple) wsp.find("txbxContent")?.childrenNamed("p")?.forEach { readParagraph(it, out) }
            }

            fun readImage(drawing: XNode) {
                drawing.find("wsp")?.let { readShape(drawing, it); return }
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
                val wrap = wrapOf(drawing)
                flush(false)
                val abs = if (wrap == ImageWrap.BEHIND) pageAbsolute(drawing) else null
                val off = abs ?: if (wrap.floating) offsetsOf(drawing, w) else (0 to 0)
                out += Block.Image(ImageBlock(bytes, ext, w.coerceAtLeast(120), h.coerceAtLeast(120), base.align, wrap, off.first, off.second, pageRelative = abs != null))
                emitted = true
            }

            fun run() {
                if (pPr?.child("pageBreakBefore") != null && truthy(pPr.child("pageBreakBefore"))) out += Block.PageBreak
                walk(p, tableRun)
                flush(!emitted)
            }
        }

        fun readParagraph(p: XNode, out: MutableList<Block>) = ParaReader(p, out, allowImages = true).run()

        /** The table style's look for the cell at [row]/[col], following the style's own base styles and the table's look flags. */
        fun tableLook(styleId: String?, tblPr: XNode?, row: Int, col: Int, rows: Int, cols: Int): CellLook {
            val chain = ArrayList<TableStyleInfo>()
            var cur = styleId?.let { tableStyles[it] }
            var guard = 0
            while (cur != null && guard++ < 10) { chain += cur; cur = cur.basedOn?.let { tableStyles[it] } }
            if (chain.isEmpty()) return CellLook(RunStyle(), null)
            val look = tblPr?.child("tblLook")
            val bits = look?.attr("val")?.toIntOrNull(16) ?: 0x04A0
            fun flag(name: String, bit: Int): Boolean = look?.attr(name)?.let { it == "1" || it == "true" } ?: ((bits and bit) != 0)
            val firstRow = flag("firstRow", 0x0020)
            val lastRow = flag("lastRow", 0x0040)
            val firstCol = flag("firstColumn", 0x0080)
            val lastCol = flag("lastColumn", 0x0100)
            val noHBand = flag("noHBand", 0x0200)
            val noVBand = flag("noVBand", 0x0400)
            val types = ArrayList<String>()
            if (!noHBand) {
                val bandRow = if (firstRow) row - 1 else row
                if (!(firstRow && row == 0) && !(lastRow && row == rows - 1)) types += if (bandRow % 2 == 0) "band1Horz" else "band2Horz"
            }
            if (!noVBand) {
                val bandCol = if (firstCol) col - 1 else col
                if (!(firstCol && col == 0) && !(lastCol && col == cols - 1)) types += if (bandCol % 2 == 0) "band1Vert" else "band2Vert"
            }
            if (firstCol && col == 0) types += "firstCol"
            if (lastCol && col == cols - 1) types += "lastCol"
            if (firstRow && row == 0) types += "firstRow"
            if (lastRow && row == rows - 1) types += "lastRow"
            if (firstRow && row == 0 && firstCol && col == 0) types += "nwCell"
            var run = RunStyle()
            var fill: Int? = null
            // Base styles first, then each style's own whole-table look, then its conditional looks in order.
            for (st in chain.asReversed()) {
                st.rPr?.let { run = readRPr(it).over(run) }
                if (st.fill != null) fill = st.fill
                for (t in types) {
                    val c = st.conds[t] ?: continue
                    c.first?.let { run = readRPr(it).over(run) }
                    if (c.second != null) fill = c.second
                }
            }
            return CellLook(run, fill)
        }

        fun readTable(tbl: XNode): TableBlock? {
            val grid = tbl.child("tblGrid")?.childrenNamed("gridCol")?.mapNotNull { it.attr("w")?.toIntOrNull() }?.toMutableList() ?: mutableListOf()
            val tblPr = tbl.child("tblPr")
            val rowNodes = tbl.childrenNamed("tr")
            if (rowNodes.isEmpty()) return null
            val rows = ArrayList<List<TableCell>>()
            var header = false
            val styleId = tblPr?.child("tblStyle")?.attr("val")
            val colCount = maxOf(grid.size, rowNodes.maxOf { r -> r.children.count { it.local == "tc" } })
            rowNodes.forEachIndexed { index, tr ->
                if (index == 0 && tr.child("trPr")?.child("tblHeader") != null) header = true
                var colIdx = 0
                val rowHeight = tr.child("trPr")?.child("trHeight")?.attr("val")?.toIntOrNull()?.coerceIn(0, 20000) ?: 0
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
                    val look = tableLook(styleId, tblPr, index, colIdx, rowNodes.size, colCount)
                    colIdx += span
                    val shdNode = tcPr?.child("shd")
                    val direct = shdNode?.attr("fill")?.let { parseHex(it) }
                    // "auto" fill means no colour of its own, so the table style's shading shows through.
                    val fill = direct ?: look.fill
                    val paragraphs = ArrayList<Paragraph>()
                    for (c in cellNode.children) {
                        if (c.local == "p") {
                            val tmp = ArrayList<Block>()
                            ParaReader(c, tmp, allowImages = false, tableRun = look.run).run()
                            tmp.filterIsInstance<Block.Para>().forEach { paragraphs += it.paragraph }
                        }
                    }
                    fun side(vararg names: String): CellBorder? {
                        val n = names.firstNotNullOfOrNull { tcPr?.child("tcBorders")?.child(it) } ?: return null
                        val v = n.attr("val")
                        if (v == null || v == "nil" || v == "none") return CellBorder(visible = false)
                        return CellBorder(true, n.attr("color")?.let { parseHex(it) } ?: 0x000000, n.attr("sz")?.toFloatOrNull()?.div(8f)?.coerceIn(0.25f, 12f) ?: 0.5f)
                    }
                    val own = if (tcPr?.child("tcBorders") != null) CellBorders(side("top"), side("left", "start"), side("bottom"), side("right", "end")) else null
                    cells += TableCell(
                        paragraphs = paragraphs.ifEmpty { listOf(Paragraph()) },
                        shading = fill,
                        borders = own,
                        rowHeightTwips = rowHeight,
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
            val indent = tblPr?.child("tblInd")?.attr("w")?.toIntOrNull() ?: 0
            val firstSide = borderNode?.children?.firstOrNull { it.attr("val").let { v -> v != null && v != "nil" && v != "none" } }
            val bColor = firstSide?.attr("color")?.let { parseHex(it) } ?: 0x808080
            val bWidth = firstSide?.attr("sz")?.toFloatOrNull()?.div(8f)?.coerceIn(0.25f, 12f) ?: 0.5f
            return TableBlock(padded, grid, header, hasBorders, indent.coerceAtLeast(0), bColor, bWidth)
        }
    }
}
