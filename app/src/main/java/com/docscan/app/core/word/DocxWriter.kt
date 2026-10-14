package com.docscan.app.core.word

import com.docscan.app.core.Xml
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a [WordDoc] as a standards-compliant .docx (WordprocessingML) that opens in Word, LibreOffice and Google Docs.
 * Element order inside w:pPr / w:rPr / w:tcPr follows the schema, which Word is strict about.
 */
object DocxWriter {
    private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
        "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
        "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""
    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun write(doc: WordDoc, out: OutputStream) {
        val ctx = Ctx()
        val body = StringBuilder()
        writeBlocks(doc.blocks, body, ctx)
        // Word requires a paragraph after a table that ends the body.
        if (doc.blocks.lastOrNull() is Block.Table) body.append("<w:p/>")

        val page = doc.page
        val hasHeader = page.headerText.isNotBlank()
        val hasFooter = page.footerText.isNotBlank() || page.pageNumbers
        val first = page.differentFirstPage
        val hasFirstHeader = first && page.firstHeaderText.isNotBlank()
        val hasFirstFooter = first && (page.firstFooterText.isNotBlank() || page.firstPageNumbers)
        val rels = StringBuilder()
        rels.append("<Relationship Id=\"rIdStyles\" Type=\"$REL/styles\" Target=\"styles.xml\"/>")
        rels.append("<Relationship Id=\"rIdNumbering\" Type=\"$REL/numbering\" Target=\"numbering.xml\"/>")
        if (hasHeader) rels.append("<Relationship Id=\"rIdHeader\" Type=\"$REL/header\" Target=\"header1.xml\"/>")
        if (hasFooter) rels.append("<Relationship Id=\"rIdFooter\" Type=\"$REL/footer\" Target=\"footer1.xml\"/>")
        if (hasFirstHeader) rels.append("<Relationship Id=\"rIdHeaderFirst\" Type=\"$REL/header\" Target=\"header2.xml\"/>")
        if (hasFirstFooter) rels.append("<Relationship Id=\"rIdFooterFirst\" Type=\"$REL/footer\" Target=\"footer2.xml\"/>")
        ctx.images.forEachIndexed { i, img ->
            rels.append("<Relationship Id=\"rIdImg${i + 1}\" Type=\"$REL/image\" Target=\"media/image${i + 1}.${img.extension}\"/>")
        }

        ctx.links.forEach { (url, id) ->
            rels.append("<Relationship Id=\"$id\" Type=\"$REL/hyperlink\" Target=\"${Xml.escape(url)}\" TargetMode=\"External\"/>")
        }

        body.append("<w:sectPr>")
        if (hasHeader) body.append("<w:headerReference w:type=\"default\" r:id=\"rIdHeader\"/>")
        if (hasFooter) body.append("<w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/>")
        if (hasFirstHeader) body.append("<w:headerReference w:type=\"first\" r:id=\"rIdHeaderFirst\"/>")
        if (hasFirstFooter) body.append("<w:footerReference w:type=\"first\" r:id=\"rIdFooterFirst\"/>")
        body.append("<w:pgSz w:w=\"${page.sheetWidthTwips}\" w:h=\"${page.sheetHeightTwips}\"")
        if (page.landscape) body.append(" w:orient=\"landscape\"")
        body.append("/>")
        body.append(
            "<w:pgMar w:top=\"${page.marginTopTwips}\" w:right=\"${page.marginRightTwips}\" " +
                "w:bottom=\"${page.marginBottomTwips}\" w:left=\"${page.marginLeftTwips}\" " +
                "w:header=\"709\" w:footer=\"709\" w:gutter=\"0\"/>",
        )
        if (first) body.append("<w:titlePg/>")
        body.append("</w:sectPr>")

        val zip = ZipOutputStream(out)
        fun putText(name: String, text: String) = put(zip, name, text.toByteArray(Charsets.UTF_8))

        putText("[Content_Types].xml", contentTypes(ctx, hasHeader, hasFooter, hasFirstHeader, hasFirstFooter))
        putText(
            "_rels/.rels",
            HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"word/document.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                "</Relationships>",
        )
        putText(
            "docProps/core.xml",
            HEAD + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${Xml.escape(doc.title)}</dc:title>" +
                "<dc:creator>Kelvin Codes</dc:creator><cp:lastModifiedBy>Kelvin Codes</cp:lastModifiedBy></cp:coreProperties>",
        )
        putText("word/document.xml", HEAD + "<w:document $NS><w:body>$body</w:body></w:document>")
        putText(
            "word/_rels/document.xml.rels",
            HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$rels</Relationships>",
        )
        putText("word/styles.xml", stylesXml())
        putText("word/numbering.xml", numberingXml(ctx))
        if (hasHeader) putText("word/header1.xml", HEAD + headerXml(page.headerText))
        if (hasFooter) putText("word/footer1.xml", HEAD + footerXml(page.footerText, page.pageNumbers))
        if (hasFirstHeader) putText("word/header2.xml", HEAD + headerXml(page.firstHeaderText))
        if (hasFirstFooter) putText("word/footer2.xml", HEAD + footerXml(page.firstFooterText, page.firstPageNumbers))
        ctx.images.forEachIndexed { i, img -> put(zip, "word/media/image${i + 1}.${img.extension}", img.bytes) }
        zip.finish()
        zip.flush()
    }

    private fun headerXml(text: String) =
        "<w:hdr $NS><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t xml:space=\"preserve\">${Xml.escape(text)}</w:t></w:r></w:p></w:hdr>"

    private fun footerXml(text: String, numbers: Boolean): String {
        val sb = StringBuilder()
        if (text.isNotBlank()) {
            sb.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t xml:space=\"preserve\">${Xml.escape(text)}</w:t></w:r></w:p>")
        }
        if (numbers) {
            sb.append(
                "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>" +
                    "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>" +
                    "<w:r><w:instrText xml:space=\"preserve\"> PAGE </w:instrText></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r>" +
                    "<w:r><w:t>1</w:t></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>",
            )
        }
        return "<w:ftr $NS>$sb</w:ftr>"
    }

    fun toBytes(doc: WordDoc): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        write(doc, bos)
        return bos.toByteArray()
    }

    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private class Ctx {
        val images = ArrayList<ImageBlock>()
        var nextNumId = 2 // 1 is the shared bullet list
        val numberedLists = ArrayList<Int>()
        var currentNumId = 0
        var drawingId = 1
        val links = LinkedHashMap<String, String>() // address -> relationship id
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun contentTypes(ctx: Ctx, header: Boolean, footer: Boolean, firstHeader: Boolean, firstFooter: Boolean): String {
        val sb = StringBuilder(HEAD)
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        for (ext in ctx.images.map { it.extension }.distinct()) {
            sb.append("<Default Extension=\"$ext\" ContentType=\"${imageMime(ext)}\"/>")
        }
        val w = "application/vnd.openxmlformats-officedocument.wordprocessingml"
        sb.append("<Override PartName=\"/word/document.xml\" ContentType=\"$w.document.main+xml\"/>")
        sb.append("<Override PartName=\"/word/styles.xml\" ContentType=\"$w.styles+xml\"/>")
        sb.append("<Override PartName=\"/word/numbering.xml\" ContentType=\"$w.numbering+xml\"/>")
        if (header) sb.append("<Override PartName=\"/word/header1.xml\" ContentType=\"$w.header+xml\"/>")
        if (footer) sb.append("<Override PartName=\"/word/footer1.xml\" ContentType=\"$w.footer+xml\"/>")
        if (firstHeader) sb.append("<Override PartName=\"/word/header2.xml\" ContentType=\"$w.header+xml\"/>")
        if (firstFooter) sb.append("<Override PartName=\"/word/footer2.xml\" ContentType=\"$w.footer+xml\"/>")
        sb.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        sb.append("</Types>")
        return sb.toString()
    }

    private fun imageMime(ext: String) = when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "application/octet-stream"
    }

    // ---- body -----------------------------------------------------------------------------------------------

    private fun writeBlocks(blocks: List<Block>, sb: StringBuilder, ctx: Ctx) {
        ctx.currentNumId = 0
        for (b in blocks) {
            when (b) {
                is Block.Para -> writeParagraph(b.paragraph, sb, ctx)
                is Block.Table -> {
                    ctx.currentNumId = 0
                    writeTable(b.table, sb, ctx)
                }
                is Block.Image -> {
                    ctx.currentNumId = 0
                    writeImage(b.image, sb, ctx)
                }
                Block.PageBreak -> {
                    ctx.currentNumId = 0
                    sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
            }
        }
    }

    private fun styleId(s: ParaStyle): String? = when (s) {
        ParaStyle.NORMAL -> null
        ParaStyle.TITLE -> "Title"
        ParaStyle.H1 -> "Heading1"
        ParaStyle.H2 -> "Heading2"
        ParaStyle.H3 -> "Heading3"
        ParaStyle.QUOTE -> "Quote"
    }

    private fun writeParagraph(p: Paragraph, sb: StringBuilder, ctx: Ctx) {
        sb.append("<w:p>")
        val pPr = StringBuilder()
        styleId(p.style)?.let { pPr.append("<w:pStyle w:val=\"$it\"/>") }
        if (p.keepWithNext) pPr.append("<w:keepNext/>")
        when (p.list) {
            ListKind.NONE -> ctx.currentNumId = 0
            ListKind.BULLET -> {
                ctx.currentNumId = 0
                pPr.append("<w:numPr><w:ilvl w:val=\"${p.level.coerceIn(0, 8)}\"/><w:numId w:val=\"1\"/></w:numPr>")
            }
            ListKind.NUMBER -> {
                if (ctx.currentNumId == 0) {
                    ctx.currentNumId = ctx.nextNumId++
                    ctx.numberedLists += ctx.currentNumId
                }
                pPr.append("<w:numPr><w:ilvl w:val=\"${p.level.coerceIn(0, 8)}\"/><w:numId w:val=\"${ctx.currentNumId}\"/></w:numPr>")
            }
        }
        p.borderColor?.let { c ->
            val col = String.format("%06X", c and 0xFFFFFF)
            pPr.append("<w:pBdr>")
            for (side in listOf("top", "left", "bottom", "right")) {
                pPr.append("<w:$side w:val=\"single\" w:sz=\"6\" w:space=\"4\" w:color=\"$col\"/>")
            }
            pPr.append("</w:pBdr>")
        }
        p.shading?.let { pPr.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${String.format("%06X", it and 0xFFFFFF)}\"/>") }
        if (p.spaceBeforePt != null || p.spaceAfterPt != null || p.lineSpacing != null) {
            pPr.append("<w:spacing")
            p.spaceBeforePt?.let { pPr.append(" w:before=\"${(it * 20).toInt()}\"") }
            p.spaceAfterPt?.let { pPr.append(" w:after=\"${(it * 20).toInt()}\"") }
            p.lineSpacing?.let { pPr.append(" w:line=\"${(it * 240).toInt()}\" w:lineRule=\"auto\"") }
            pPr.append("/>")
        }
        if (p.indentLeftTwips != 0 || p.indentFirstTwips != 0) {
            pPr.append("<w:ind w:left=\"${p.indentLeftTwips}\"")
            if (p.indentFirstTwips < 0) pPr.append(" w:hanging=\"${-p.indentFirstTwips}\"")
            else if (p.indentFirstTwips > 0) pPr.append(" w:firstLine=\"${p.indentFirstTwips}\"")
            pPr.append("/>")
        }
        if (p.list != ListKind.NONE) pPr.append("<w:contextualSpacing/>")
        jc(p.align)?.let { pPr.append("<w:jc w:val=\"$it\"/>") }
        if (pPr.isNotEmpty()) sb.append("<w:pPr>").append(pPr).append("</w:pPr>")
        var open: String? = null
        for (run in p.runs) {
            val url = run.style.link?.let { LinkSafety.normalize(it) }
            if (url != open) {
                if (open != null) sb.append("</w:hyperlink>")
                if (url != null) {
                    val id = ctx.links.getOrPut(url) { "rIdLink${ctx.links.size + 1}" }
                    sb.append("<w:hyperlink r:id=\"$id\" w:history=\"1\">")
                }
                open = url
            }
            writeRun(if (url != null) run.copy(style = run.style.copy(color = run.style.color ?: 0x0563C1, underline = true)) else run, sb)
        }
        if (open != null) sb.append("</w:hyperlink>")
        sb.append("</w:p>")
    }

    private fun jc(a: Align): String? = when (a) {
        Align.LEFT -> null
        Align.CENTER -> "center"
        Align.RIGHT -> "right"
        Align.JUSTIFY -> "both"
    }

    private fun writeRun(run: Run, sb: StringBuilder) {
        if (run.text.isEmpty()) return
        sb.append("<w:r>")
        val rPr = rPrXml(run.style)
        if (rPr.isNotEmpty()) sb.append("<w:rPr>").append(rPr).append("</w:rPr>")
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                sb.append("<w:t xml:space=\"preserve\">").append(Xml.escape(text.toString())).append("</w:t>")
                text.setLength(0)
            }
        }
        for (ch in run.text) {
            when (ch) {
                '\t' -> { flush(); sb.append("<w:tab/>") }
                '\n' -> { flush(); sb.append("<w:br/>") }
                '\r' -> {}
                else -> text.append(ch)
            }
        }
        flush()
        sb.append("</w:r>")
    }

    private fun rPrXml(s: RunStyle): String {
        val sb = StringBuilder()
        if (s.font != null) sb.append("<w:rFonts w:ascii=\"${Xml.escape(s.font)}\" w:hAnsi=\"${Xml.escape(s.font)}\" w:cs=\"${Xml.escape(s.font)}\"/>")
        if (s.bold) sb.append("<w:b/><w:bCs/>")
        if (s.italic) sb.append("<w:i/><w:iCs/>")
        if (s.strike) sb.append("<w:strike/>")
        s.color?.let { sb.append("<w:color w:val=\"${hex(it)}\"/>") }
        s.sizePt?.let {
            val half = (it * 2).toInt().coerceAtLeast(2)
            sb.append("<w:sz w:val=\"$half\"/><w:szCs w:val=\"$half\"/>")
        }
        if (s.underline) sb.append("<w:u w:val=\"${s.underlineStyle?.takeIf { it in DocxReader.UNDERLINE_WORDS } ?: "single"}\"/>")
        s.highlight?.let { sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${hex(it)}\"/>") }
        when (s.vertical) {
            VerticalAlign.SUPERSCRIPT -> sb.append("<w:vertAlign w:val=\"superscript\"/>")
            VerticalAlign.SUBSCRIPT -> sb.append("<w:vertAlign w:val=\"subscript\"/>")
            VerticalAlign.BASELINE -> {}
        }
        return sb.toString()
    }

    private fun hex(rgb: Int) = String.format("%06X", rgb and 0xFFFFFF)

    // ---- tables ---------------------------------------------------------------------------------------------

    private fun writeTable(t: TableBlock, sb: StringBuilder, ctx: Ctx) {
        val total = t.columnWidthsTwips.sum()
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"$total\" w:type=\"dxa\"/>")
        if (t.borders) {
            sb.append("<w:tblBorders>")
            for (side in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
                sb.append("<w:$side w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"808080\"/>")
            }
            sb.append("</w:tblBorders>")
        }
        sb.append("<w:tblLayout w:type=\"fixed\"/>")
        sb.append("<w:tblCellMar><w:left w:w=\"100\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/></w:tblCellMar>")
        sb.append("</w:tblPr><w:tblGrid>")
        for (w in t.columnWidthsTwips) sb.append("<w:gridCol w:w=\"$w\"/>")
        sb.append("</w:tblGrid>")
        t.rows.forEachIndexed { rowIndex, row ->
            sb.append("<w:tr>")
            if (rowIndex == 0 && t.headerRow) sb.append("<w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>")
            var col = 0
            for (cell in row) {
                val span = cell.colSpan.coerceAtLeast(1)
                var width = 0
                for (k in col until minOf(col + span, t.columnWidthsTwips.size)) width += t.columnWidthsTwips[k]
                col += span
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"dxa\"/>")
                if (span > 1) sb.append("<w:gridSpan w:val=\"$span\"/>")
                if (cell.vMergeStart) sb.append("<w:vMerge w:val=\"restart\"/>")
                else if (cell.vMergeContinue) sb.append("<w:vMerge/>")
                cell.shading?.let { sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${hex(it)}\"/>") }
                sb.append("</w:tcPr>")
                val before = ctx.currentNumId
                ctx.currentNumId = 0
                val paragraphs = cell.paragraphs.ifEmpty { listOf(Paragraph()) }
                for (p in paragraphs) writeParagraph(p, sb, ctx)
                ctx.currentNumId = before
                sb.append("</w:tc>")
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
    }

    // ---- images ---------------------------------------------------------------------------------------------

    private fun writeImage(img: ImageBlock, sb: StringBuilder, ctx: Ctx) {
        ctx.images += img
        val index = ctx.images.size
        val cx = img.widthTwips.toLong() * Twips.EMU_PER_TWIP
        val cy = img.heightTwips.toLong() * Twips.EMU_PER_TWIP
        val id = ctx.drawingId++
        sb.append("<w:p>")
        jc(img.align)?.let { sb.append("<w:pPr><w:jc w:val=\"$it\"/></w:pPr>") }
        sb.append(
            "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$id\" name=\"Picture $id\"/>" +
                "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
                "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"image$index.${img.extension}\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                "<pic:blipFill><a:blip r:embed=\"rIdImg$index\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
                "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
                "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>",
        )
    }

    // ---- styles & numbering ---------------------------------------------------------------------------------

    private fun pt2(v: Float) = (v * 2).toInt()

    private fun stylesXml(): String {
        val sb = StringBuilder(HEAD)
        sb.append("<w:styles $NS>")
        sb.append(
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"${StyleDefaults.BODY_FONT}\" w:hAnsi=\"${StyleDefaults.BODY_FONT}\" " +
                "w:eastAsia=\"${StyleDefaults.BODY_FONT}\" w:cs=\"${StyleDefaults.BODY_FONT}\"/>" +
                "<w:sz w:val=\"${pt2(StyleDefaults.BODY_SIZE_PT)}\"/><w:szCs w:val=\"${pt2(StyleDefaults.BODY_SIZE_PT)}\"/>" +
                "<w:lang w:val=\"en-US\"/></w:rPr></w:rPrDefault>" +
                "<w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>",
        )
        sb.append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>")
        for ((id, name, style) in listOf(
            Triple("Title", "Title", ParaStyle.TITLE),
            Triple("Heading1", "heading 1", ParaStyle.H1),
            Triple("Heading2", "heading 2", ParaStyle.H2),
            Triple("Heading3", "heading 3", ParaStyle.H3),
            Triple("Quote", "Quote", ParaStyle.QUOTE),
        )) {
            sb.append("<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$name\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/><w:pPr>")
            if (style == ParaStyle.H1 || style == ParaStyle.H2 || style == ParaStyle.H3) sb.append("<w:keepNext/>")
            sb.append(
                "<w:spacing w:before=\"${(StyleDefaults.spaceBeforePt(style) * 20).toInt()}\" " +
                    "w:after=\"${(StyleDefaults.spaceAfterPt(style) * 20).toInt()}\"/>",
            )
            val ind = StyleDefaults.indentLeftTwips(style)
            if (ind != 0) sb.append("<w:ind w:left=\"$ind\" w:right=\"$ind\"/>")
            when (style) {
                ParaStyle.H1 -> sb.append("<w:outlineLvl w:val=\"0\"/>")
                ParaStyle.H2 -> sb.append("<w:outlineLvl w:val=\"1\"/>")
                ParaStyle.H3 -> sb.append("<w:outlineLvl w:val=\"2\"/>")
                else -> {}
            }
            sb.append("</w:pPr><w:rPr>")
            if (StyleDefaults.bold(style)) sb.append("<w:b/><w:bCs/>")
            if (StyleDefaults.italic(style)) sb.append("<w:i/><w:iCs/>")
            StyleDefaults.color(style)?.let { sb.append("<w:color w:val=\"${hex(it)}\"/>") }
            sb.append("<w:sz w:val=\"${pt2(StyleDefaults.sizePt(style))}\"/><w:szCs w:val=\"${pt2(StyleDefaults.sizePt(style))}\"/>")
            sb.append("</w:rPr></w:style>")
        }
        sb.append("</w:styles>")
        return sb.toString()
    }

    private fun numberingXml(ctx: Ctx): String {
        val sb = StringBuilder(HEAD)
        sb.append("<w:numbering $NS>")
        // abstractNum 0 = bullets, 1 = numbers (decimal / letter / roman by depth)
        val bullets = listOf("•", "o", "▪")
        sb.append("<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/>")
        for (lvl in 0..8) {
            val left = 720 * (lvl + 1)
            sb.append(
                "<w:lvl w:ilvl=\"$lvl\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"${bullets[lvl % 3]}\"/>" +
                    "<w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"$left\" w:hanging=\"360\"/></w:pPr></w:lvl>",
            )
        }
        sb.append("</w:abstractNum>")
        val formats = listOf("decimal", "lowerLetter", "lowerRoman")
        sb.append("<w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"hybridMultilevel\"/>")
        for (lvl in 0..8) {
            val left = 720 * (lvl + 1)
            sb.append(
                "<w:lvl w:ilvl=\"$lvl\"><w:start w:val=\"1\"/><w:numFmt w:val=\"${formats[lvl % 3]}\"/><w:lvlText w:val=\"%${lvl + 1}.\"/>" +
                    "<w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"$left\" w:hanging=\"360\"/></w:pPr></w:lvl>",
            )
        }
        sb.append("</w:abstractNum>")
        sb.append("<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>")
        for (id in ctx.numberedLists) {
            sb.append("<w:num w:numId=\"$id\"><w:abstractNumId w:val=\"1\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>")
        }
        sb.append("</w:numbering>")
        return sb.toString()
    }
}
