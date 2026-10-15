package com.docscan.app.core

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/** Minimal OOXML writers (no Apache POI) so the app stays small and fully offline. */
object Xml {
    /** Escapes text for XML and drops characters that are illegal in XML 1.0. */
    fun escape(s: String): String = buildString(s.length) {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\'' -> append("&apos;")
                ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r' -> append(ch)
            }
        }
    }
}

/** Writes a single-sheet .xlsx workbook. */
object XlsxBuilder {
    private val numberRegex = Regex("""^-?(0|[1-9]\d*)(\.\d+)?$""")

    /** 0 -> A, 25 -> Z, 26 -> AA ... */
    fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A'.code + n % 26).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun sanitizeSheetName(name: String): String =
        name.replace(Regex("""[\[\]:*?/\\]"""), " ").trim().ifEmpty { "Sheet1" }.take(31)

    fun build(rows: List<List<String>>, sheetName: String, out: OutputStream) = buildSheets(listOf(sheetName to rows), out)

    /** Writes a workbook with one sheet per entry (names are made unique and valid). */
    fun buildSheets(sheets: List<Pair<String, List<List<String>>>>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val used = HashSet<String>()
        val names = sheets.map { (n, _) ->
            var base = sanitizeSheetName(n)
            var candidate = base
            var k = 2
            while (!used.add(candidate.lowercase())) { candidate = base.take(28) + " " + k++ }
            candidate
        }
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val overrides = sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" }
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$overrides</Types>""",
            )
            entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            val sheetTags = names.indices.joinToString("") { """<sheet name="${Xml.escape(names[it])}" sheetId="${it + 1}" r:id="rId${it + 1}"/>""" }
            entry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetTags</sheets></workbook>""",
            )
            val rels = sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" }
            entry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""",
            )
            sheets.forEachIndexed { i, (_, rows) -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(rows)) }
        }
    }

    internal fun sheetXml(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, raw ->
                val text = raw.trim()
                if (text.isEmpty()) return@forEachIndexed
                val ref = columnName(c) + (r + 1)
                if (numberRegex.matches(text)) {
                    append("""<c r="$ref"><v>$text</v></c>""")
                } else {
                    append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${Xml.escape(raw)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }
}

/** Builds a .docx made of image pages, paragraphs and tables. */
class DocxBuilder {
    private val body = StringBuilder()
    private val media = mutableListOf<Pair<String, ByteArray>>()
    private var firstBlock = true

    /** Adds a full-page picture. [extension] is "jpg" or "png". */
    fun addImagePage(bytes: ByteArray, widthPx: Int, heightPx: Int, extension: String) {
        require(widthPx > 0 && heightPx > 0) { "Image has no size" }
        val index = media.size + 1
        val name = "image$index.$extension"
        media.add(name to bytes)
        val scale = min(MAX_WIDTH_EMU.toDouble() / widthPx, MAX_HEIGHT_EMU.toDouble() / heightPx)
        val cx = (widthPx * scale).toLong()
        val cy = (heightPx * scale).toLong()
        val pageBreak = if (firstBlock) "" else "<w:pPr><w:pageBreakBefore/></w:pPr>"
        firstBlock = false
        body.append(
            """<w:p>$pageBreak<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$index" name="Picture $index"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$index" name="$name"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImg$index"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""",
        )
    }

    fun addParagraph(text: String, bold: Boolean = false, pageBreakBefore: Boolean = false) {
        val props = buildString {
            if (pageBreakBefore && !firstBlock) append("<w:pPr><w:pageBreakBefore/></w:pPr>")
        }
        firstBlock = false
        val runProps = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
        body.append("""<w:p>$props<w:r>$runProps<w:t xml:space="preserve">${Xml.escape(text)}</w:t></w:r></w:p>""")
    }

    fun addTable(rows: List<List<String>>) {
        if (rows.isEmpty()) return
        firstBlock = false
        val columns = rows.maxOf { it.size }.coerceAtLeast(1)
        val colWidth = PAGE_TEXT_TWIPS / columns
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
        for (side in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
            body.append("""<w:$side w:val="single" w:sz="4" w:space="0" w:color="000000"/>""")
        }
        body.append("</w:tblBorders></w:tblPr><w:tblGrid>")
        repeat(columns) { body.append("""<w:gridCol w:w="$colWidth"/>""") }
        body.append("</w:tblGrid>")
        for (row in rows) {
            body.append("<w:tr>")
            for (c in 0 until columns) {
                val cell = row.getOrElse(c) { "" }
                body.append(
                    """<w:tc><w:tcPr><w:tcW w:w="$colWidth" w:type="dxa"/></w:tcPr><w:p><w:r><w:t xml:space="preserve">${Xml.escape(cell)}</w:t></w:r></w:p></w:tc>""",
                )
            }
            body.append("</w:tr>")
        }
        // Word requires a paragraph after a table that ends the body.
        body.append("</w:tbl><w:p/>")
    }

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            fun text(name: String, content: String) = entry(name, content.toByteArray(Charsets.UTF_8))

            text(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            )
            text(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            )
            val rels = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                media.forEachIndexed { i, (name, _) ->
                    append("""<Relationship Id="rIdImg${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/$name"/>""")
                }
                append("</Relationships>")
            }
            text("word/_rels/document.xml.rels", rels)
            text(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            )
            media.forEach { (name, bytes) -> entry("word/media/$name", bytes) }
        }
    }

    private companion object {
        // A4 with 0.5" margins: 7.27" x 10.19" printable. Height leaves a small safety gap.
        const val MAX_WIDTH_EMU = 6_645_000L
        const val MAX_HEIGHT_EMU = 9_300_000L
        const val PAGE_TEXT_TWIPS = 10_466
    }
}
