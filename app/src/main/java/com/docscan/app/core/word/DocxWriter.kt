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
        "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\" " +
        "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" " +
        "xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\""
    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun write(doc: WordDoc, out: OutputStream) {
        val ctx = Ctx()
        ctx.comments = doc.comments.associateBy { it.id }
        val body = StringBuilder()
        writeBlocks(doc.blocks, body, ctx)
        // Word requires a paragraph after a table that ends the body.
        if (doc.blocks.lastOrNull() is Block.Table) body.append("<w:p/>")

        val page = doc.page
        val hasHeader = page.headerText.isNotBlank() || page.headerImages.isNotEmpty()
        val hasFooter = page.footerText.isNotBlank() || page.pageNumbers || page.footerImages.isNotEmpty()
        val first = page.differentFirstPage
        val hasFirstHeader = first && (page.firstHeaderText.isNotBlank() || page.firstHeaderImages.isNotEmpty())
        val hasFirstFooter = first && (page.firstFooterText.isNotBlank() || page.firstPageNumbers || page.firstFooterImages.isNotEmpty())
        val even = page.differentOddEven
        val rels = StringBuilder()
        if (even) rels.append("<Relationship Id=\"rIdSettings\" Type=\"$REL/settings\" Target=\"settings.xml\"/>")
        rels.append("<Relationship Id=\"rIdStyles\" Type=\"$REL/styles\" Target=\"styles.xml\"/>")
        rels.append("<Relationship Id=\"rIdNumbering\" Type=\"$REL/numbering\" Target=\"numbering.xml\"/>")
        if (hasHeader) rels.append("<Relationship Id=\"rIdHeader\" Type=\"$REL/header\" Target=\"header1.xml\"/>")
        if (hasFooter) rels.append("<Relationship Id=\"rIdFooter\" Type=\"$REL/footer\" Target=\"footer1.xml\"/>")
        if (hasFirstHeader) rels.append("<Relationship Id=\"rIdHeaderFirst\" Type=\"$REL/header\" Target=\"header2.xml\"/>")
        if (hasFirstFooter) rels.append("<Relationship Id=\"rIdFooterFirst\" Type=\"$REL/footer\" Target=\"footer2.xml\"/>")
        if (even) {
            rels.append("<Relationship Id=\"rIdHeaderEven\" Type=\"$REL/header\" Target=\"header3.xml\"/>")
            rels.append("<Relationship Id=\"rIdFooterEven\" Type=\"$REL/footer\" Target=\"footer3.xml\"/>")
        }
        ctx.images.forEachIndexed { i, img ->
            rels.append("<Relationship Id=\"rIdImg${i + 1}\" Type=\"$REL/image\" Target=\"media/image${i + 1}.${img.extension}\"/>")
        }

        if (ctx.usedComments.isNotEmpty()) rels.append("<Relationship Id=\"rIdComments\" Type=\"$REL/comments\" Target=\"comments.xml\"/>")
        ctx.links.forEach { (url, id) ->
            rels.append("<Relationship Id=\"$id\" Type=\"$REL/hyperlink\" Target=\"${Xml.escape(url)}\" TargetMode=\"External\"/>")
        }

        body.append("<w:sectPr>")
        if (hasHeader) body.append("<w:headerReference w:type=\"default\" r:id=\"rIdHeader\"/>")
        if (hasFooter) body.append("<w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/>")
        if (hasFirstHeader) body.append("<w:headerReference w:type=\"first\" r:id=\"rIdHeaderFirst\"/>")
        if (hasFirstFooter) body.append("<w:footerReference w:type=\"first\" r:id=\"rIdFooterFirst\"/>")
        if (even) body.append("<w:headerReference w:type=\"even\" r:id=\"rIdHeaderEven\"/><w:footerReference w:type=\"even\" r:id=\"rIdFooterEven\"/>")
        body.append("<w:pgSz w:w=\"${page.sheetWidthTwips}\" w:h=\"${page.sheetHeightTwips}\"")
        if (page.landscape) body.append(" w:orient=\"landscape\"")
        body.append("/>")
        body.append(
            "<w:pgMar w:top=\"${page.marginTopTwips}\" w:right=\"${page.marginRightTwips}\" " +
                "w:bottom=\"${page.marginBottomTwips}\" w:left=\"${page.marginLeftTwips}\" " +
                "w:header=\"${page.headerDistTwips}\" w:footer=\"${page.footerDistTwips}\" w:gutter=\"0\"/>",
        )
        if (first) body.append("<w:titlePg/>")
        body.append("</w:sectPr>")

        val zip = ZipOutputStream(out)
        fun putText(name: String, text: String) = put(zip, name, text.toByteArray(Charsets.UTF_8))

        putText("[Content_Types].xml", contentTypes(ctx, hasHeader, hasFooter, hasFirstHeader, hasFirstFooter, (page.headerImages + page.footerImages + page.firstHeaderImages + page.firstFooterImages).map { it.extension }, even))
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
        if (ctx.usedComments.isNotEmpty()) putText("word/comments.xml", commentsXml(ctx))
        var mediaNo = 0
        val hfExts = LinkedHashSet<String>()
        /** Writes a header/footer part, plus its relationships and pictures when it has any. */
        fun part(name: String, xml: (List<String>) -> String, images: List<PageImage>) {
            val ids = images.mapIndexed { i, _ -> "rIdP${i + 1}" }
            putText("word/$name", HEAD + xml(ids))
            if (images.isNotEmpty()) {
                val sb = StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                images.forEachIndexed { i, img ->
                    mediaNo++
                    hfExts += img.extension
                    sb.append("<Relationship Id=\"${ids[i]}\" Type=\"$REL/image\" Target=\"media/letterhead$mediaNo.${img.extension}\"/>")
                    put(zip, "word/media/letterhead$mediaNo.${img.extension}", img.bytes)
                }
                sb.append("</Relationships>")
                putText("word/_rels/$name.rels", HEAD + sb)
            }
        }
        if (hasHeader) part("header1.xml", { ids -> headerXml(page.headerText, page.headerImages, ids, 1001) }, page.headerImages)
        if (hasFooter) part("footer1.xml", { ids -> footerXml(page.footerText, page.pageNumbers, page.footerImages, ids, 1101) }, page.footerImages)
        if (hasFirstHeader) part("header2.xml", { ids -> headerXml(page.firstHeaderText, page.firstHeaderImages, ids, 1201) }, page.firstHeaderImages)
        if (even) {
            putText("word/settings.xml", HEAD + "<w:settings $NS><w:evenAndOddHeaders/></w:settings>")
            part("header3.xml", { ids -> headerXml(page.evenHeaderText, emptyList(), ids, 1401) }, emptyList())
            part("footer3.xml", { ids -> footerXml(page.evenFooterText, page.pageNumbers, emptyList(), ids, 1501) }, emptyList())
        }
        if (hasFirstFooter) part("footer2.xml", { ids -> footerXml(page.firstFooterText, page.firstPageNumbers, page.firstFooterImages, ids, 1301) }, page.firstFooterImages)
        ctx.images.forEachIndexed { i, img -> put(zip, "word/media/image${i + 1}.${img.extension}", img.bytes) }
        zip.finish()
        zip.flush()
    }

    /** One header/footer picture, placed by absolute position on the sheet so it repeats exactly on every page. */
    private fun pageImageXml(img: PageImage, rId: String, id: Int): String {
        val cx = img.widthTwips.toLong() * Twips.EMU_PER_TWIP
        val cy = img.heightTwips.toLong() * Twips.EMU_PER_TWIP
        val x = img.xTwips.toLong() * Twips.EMU_PER_TWIP
        val y = img.yTwips.toLong() * Twips.EMU_PER_TWIP
        return "<w:r><w:drawing><wp:anchor distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" relativeHeight=\"${251658240 + id}\" " +
            "behindDoc=\"${if (img.behind) 1 else 0}\" locked=\"0\" layoutInCell=\"1\" allowOverlap=\"1\"><wp:simplePos x=\"0\" y=\"0\"/>" +
            "<wp:positionH relativeFrom=\"page\"><wp:posOffset>$x</wp:posOffset></wp:positionH>" +
            "<wp:positionV relativeFrom=\"page\"><wp:posOffset>$y</wp:posOffset></wp:positionV>" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/><wp:wrapNone/>" +
            "<wp:docPr id=\"$id\" name=\"Letterhead $id\"/><wp:cNvGraphicFramePr/>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>" +
            "<pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"Letterhead $id\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$rId\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>"
    }

    private fun pictures(images: List<PageImage>, ids: List<String>, firstId: Int): String =
        images.indices.joinToString("") { pageImageXml(images[it], ids[it], firstId + it) }

    private fun headerXml(text: String, images: List<PageImage>, ids: List<String>, firstId: Int): String {
        val textRun = if (text.isBlank()) "" else "<w:r><w:t xml:space=\"preserve\">${Xml.escape(text)}</w:t></w:r>"
        return "<w:hdr $NS><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>$textRun${pictures(images, ids, firstId)}</w:p></w:hdr>"
    }

    private fun footerXml(text: String, numbers: Boolean, images: List<PageImage>, ids: List<String>, firstId: Int): String {
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
        if (images.isNotEmpty()) sb.append("<w:p>${pictures(images, ids, firstId)}</w:p>")
        if (sb.isEmpty()) sb.append("<w:p/>")
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
        var comments: Map<Int, DocComment> = emptyMap()
        val usedComments = LinkedHashSet<Int>()
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun contentTypes(ctx: Ctx, header: Boolean, footer: Boolean, firstHeader: Boolean, firstFooter: Boolean, extra: List<String>, evenOdd: Boolean = false): String {
        val sb = StringBuilder(HEAD)
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        for (ext in (ctx.images.map { it.extension } + extra).distinct()) {
            sb.append("<Default Extension=\"$ext\" ContentType=\"${imageMime(ext)}\"/>")
        }
        val w = "application/vnd.openxmlformats-officedocument.wordprocessingml"
        sb.append("<Override PartName=\"/word/document.xml\" ContentType=\"$w.document.main+xml\"/>")
        sb.append("<Override PartName=\"/word/styles.xml\" ContentType=\"$w.styles+xml\"/>")
        sb.append("<Override PartName=\"/word/numbering.xml\" ContentType=\"$w.numbering+xml\"/>")
        if (evenOdd) sb.append("<Override PartName=\"/word/settings.xml\" ContentType=\"$w.settings+xml\"/><Override PartName=\"/word/header3.xml\" ContentType=\"$w.header+xml\"/><Override PartName=\"/word/footer3.xml\" ContentType=\"$w.footer+xml\"/>")
        if (ctx.usedComments.isNotEmpty()) sb.append("<Override PartName=\"/word/comments.xml\" ContentType=\"$w.comments+xml\"/>")
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
                is Block.Shape -> {
                    ctx.currentNumId = 0
                    writeShape(b.shape, sb, ctx)
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
        if (p.rtl) pPr.append("<w:bidi/>")
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
        var openComment: Int? = null
        fun closeComment() {
            val id = openComment ?: return
            sb.append("<w:commentRangeEnd w:id=\"$id\"/><w:r><w:commentReference w:id=\"$id\"/></w:r>")
            openComment = null
        }
        for (run in p.runs) {
            val cid = run.style.comment?.takeIf { it in ctx.comments && (it == openComment || it !in ctx.usedComments) }
            if (cid != openComment) {
                closeComment()
                if (cid != null) {
                    sb.append("<w:commentRangeStart w:id=\"$cid\"/>")
                    ctx.usedComments += cid
                    openComment = cid
                }
            }
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
        closeComment()
        sb.append("</w:p>")
    }

    private fun commentsXml(ctx: Ctx): String {
        val sb = StringBuilder(HEAD).append("<w:comments $NS>")
        for (id in ctx.usedComments) {
            val c = ctx.comments[id] ?: continue
            val initials = c.author.split(' ').filter { it.isNotBlank() }.joinToString("") { it.take(1).uppercase() }.take(4)
            sb.append("<w:comment w:id=\"${c.id}\" w:author=\"${Xml.escape(c.author)}\" w:initials=\"${Xml.escape(initials)}\">")
            for (line in c.text.split('\n').ifEmpty { listOf("") }) {
                sb.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(Xml.escape(line)).append("</w:t></w:r></w:p>")
            }
            sb.append("</w:comment>")
        }
        return sb.append("</w:comments>").toString()
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
        if (t.indentTwips != 0) sb.append("<w:tblInd w:w=\"${t.indentTwips}\" w:type=\"dxa\"/>")
        if (t.borders) {
            sb.append("<w:tblBorders>")
            val sz = (t.borderWidthPt * 8f).toInt().coerceIn(2, 96)
            for (side in listOf("top", "left", "bottom", "right", "insideH", "insideV")) {
                sb.append("<w:$side w:val=\"single\" w:sz=\"$sz\" w:space=\"0\" w:color=\"${hex(t.borderColor)}\"/>")
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
            val rowH = CellEdges.rowHeight(row)
            if ((rowIndex == 0 && t.headerRow) || rowH > 0) {
                sb.append("<w:trPr>")
                if (rowIndex == 0 && t.headerRow) sb.append("<w:cantSplit/>")
                if (rowH > 0) sb.append("<w:trHeight w:val=\"$rowH\" w:hRule=\"atLeast\"/>")
                if (rowIndex == 0 && t.headerRow) sb.append("<w:tblHeader/>")
                sb.append("</w:trPr>")
            }
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
                cell.borders?.let { bs ->
                    sb.append("<w:tcBorders>")
                    for ((i, name) in listOf("top", "left", "bottom", "right").withIndex()) {
                        val s = bs.side(i) ?: continue
                        if (!s.visible) sb.append("<w:$name w:val=\"nil\"/>")
                        else sb.append("<w:$name w:val=\"single\" w:sz=\"${(s.widthPt * 8f).toInt().coerceIn(2, 96)}\" w:space=\"0\" w:color=\"${hex(s.color)}\"/>")
                    }
                    sb.append("</w:tcBorders>")
                }
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
        val frame = "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"image$index.${img.extension}\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdImg$index\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
            "</a:graphicData></a:graphic>"
        sb.append("<w:r>").append(drawingXml(id, cx, cy, img.wrap, img.align, frame, img.offsetXTwips, img.offsetYTwips, img.pageRelative)).append("</w:r></w:p>")
    }

    /** `<w:drawing>` holding [frame] either inline or anchored with the chosen text wrap. */
    private fun drawingXml(id: Int, cx: Long, cy: Long, wrap: ImageWrap, align: Align, frame: String, offX: Int = 0, offY: Int = 0, pageRel: Boolean = false): String {
        if (wrap == ImageWrap.IN_LINE) {
            return "<w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$id\" name=\"Picture $id\"/>" + frame + "</wp:inline></w:drawing>"
        }
        val behind = if (wrap == ImageWrap.BEHIND) "1" else "0"
        val hAlign = when (align) { Align.CENTER -> "center"; Align.RIGHT -> "right"; else -> "left" }
        val wrapEl = when (wrap) {
            ImageWrap.SQUARE -> "<wp:wrapSquare wrapText=\"bothSides\"/>"
            ImageWrap.TIGHT -> "<wp:wrapTight wrapText=\"bothSides\"><wp:wrapPolygon edited=\"0\"><wp:start x=\"0\" y=\"0\"/>" +
                "<wp:lineTo x=\"0\" y=\"21600\"/><wp:lineTo x=\"21600\" y=\"21600\"/><wp:lineTo x=\"21600\" y=\"0\"/><wp:lineTo x=\"0\" y=\"0\"/></wp:wrapPolygon></wp:wrapTight>"
            ImageWrap.THROUGH -> "<wp:wrapThrough wrapText=\"bothSides\"><wp:wrapPolygon edited=\"0\"><wp:start x=\"0\" y=\"0\"/>" +
                "<wp:lineTo x=\"0\" y=\"21600\"/><wp:lineTo x=\"21600\" y=\"21600\"/><wp:lineTo x=\"21600\" y=\"0\"/><wp:lineTo x=\"0\" y=\"0\"/></wp:wrapPolygon></wp:wrapThrough>"
            ImageWrap.TOP_BOTTOM -> "<wp:wrapTopAndBottom/>"
            else -> "<wp:wrapNone/>"
        }
        return "<w:drawing><wp:anchor distT=\"0\" distB=\"0\" distL=\"114300\" distR=\"114300\" simplePos=\"0\" relativeHeight=\"${251658240 + id}\" " +
            "behindDoc=\"$behind\" locked=\"0\" layoutInCell=\"1\" allowOverlap=\"1\"><wp:simplePos x=\"0\" y=\"0\"/>" +
            (if (wrap.floating && pageRel) "<wp:positionH relativeFrom=\"page\"><wp:posOffset>${offX.toLong() * Twips.EMU_PER_TWIP}</wp:posOffset></wp:positionH>"
            else if (wrap.floating) "<wp:positionH relativeFrom=\"column\"><wp:posOffset>${offX.toLong() * Twips.EMU_PER_TWIP}</wp:posOffset></wp:positionH>"
            else "<wp:positionH relativeFrom=\"column\"><wp:align>$hAlign</wp:align></wp:positionH>") +
            "<wp:positionV relativeFrom=\"${if (wrap.floating && pageRel) "page" else "paragraph"}\"><wp:posOffset>${if (wrap.floating) offY.toLong() * Twips.EMU_PER_TWIP else 0L}</wp:posOffset></wp:positionV>" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>" +
            wrapEl + "<wp:docPr id=\"$id\" name=\"Picture $id\"/>" + frame + "</wp:anchor></w:drawing>"
    }

    /** The text inside a shape or text box, as Word keeps it. */
    private fun shapeText(sh: ShapeBlock): String {
        if (sh.text.isEmpty()) return ""
        val rPr = "<w:rPr>" + (if (sh.textBold) "<w:b/>" else "") + "<w:color w:val=\"${hex(sh.textColor)}\"/><w:sz w:val=\"${pt2(sh.textSizePt)}\"/><w:szCs w:val=\"${pt2(sh.textSizePt)}\"/></w:rPr>"
        val paras = sh.text.split("\n").joinToString("") { line ->
            "<w:p><w:pPr><w:spacing w:after=\"0\"/></w:pPr><w:r>$rPr<w:t xml:space=\"preserve\">${Xml.escape(line)}</w:t></w:r></w:p>"
        }
        return "<wps:txbx><w:txbxContent>$paras</w:txbxContent></wps:txbx>"
    }

    private fun writeShape(sh: ShapeBlock, sb: StringBuilder, ctx: Ctx) {
        val cx = sh.widthTwips.toLong() * Twips.EMU_PER_TWIP
        val cy = if (ShapePresets.isLine(sh.preset)) 0L else sh.heightTwips.toLong() * Twips.EMU_PER_TWIP
        val id = ctx.drawingId++
        val fill = sh.fill?.let { "<a:solidFill><a:srgbClr val=\"${hex(it)}\"/></a:solidFill>" } ?: "<a:noFill/>"
        val lineW = (sh.lineWidthPt * 12700).toInt().coerceIn(0, 12700 * 40)
        val frame = "<a:graphic><a:graphicData uri=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\">" +
            "<wps:wsp><wps:cNvSpPr${if (ShapePresets.isTextBox(sh.preset)) " txBox=\"1\"" else ""}/><wps:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"${Xml.escape(if (ShapePresets.isTextBox(sh.preset)) "rect" else sh.preset)}\"><a:avLst/></a:prstGeom>$fill" +
            (if (sh.lineWidthPt <= 0f) "<a:ln><a:noFill/></a:ln>" else "<a:ln w=\"$lineW\"><a:solidFill><a:srgbClr val=\"${hex(sh.lineColor)}\"/></a:solidFill></a:ln>") + "</wps:spPr>" + shapeText(sh) + "<wps:bodyPr/></wps:wsp>" +
            "</a:graphicData></a:graphic>"
        sb.append("<w:p>")
        jc(sh.align)?.let { sb.append("<w:pPr><w:jc w:val=\"$it\"/></w:pPr>") }
        sb.append("<w:r><mc:AlternateContent><mc:Choice Requires=\"wps\">")
            .append(drawingXml(id, cx, cy, sh.wrap, sh.align, frame, sh.offsetXTwips, sh.offsetYTwips).replace("name=\"Picture $id\"", "name=\"Shape $id\""))
            .append("</mc:Choice></mc:AlternateContent></w:r></w:p>")
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
