package com.docscan.app.core.word

/** A ready-made starting document. The placeholders are in square brackets so they are easy to spot and replace. */
class DocTemplate(val id: String, val label: String, val blurb: String, val build: () -> WordDoc)

object Templates {
    private fun p(text: String, style: ParaStyle = ParaStyle.NORMAL, align: Align = Align.LEFT, bold: Boolean = false, size: Float? = null, after: Float? = null, color: Int? = null) =
        Block.Para(Paragraph(listOf(Run(text, RunStyle(bold = bold, sizePt = size, color = color))), style = style, align = align, spaceAfterPt = after))

    private fun blank() = Block.Para(Paragraph())

    private fun cell(text: String, bold: Boolean = false, align: Align = Align.LEFT, shading: Int? = null) =
        TableCell(listOf(Paragraph(listOf(Run(text, RunStyle(bold = bold))), align = align)), shading = shading)

    val letter = DocTemplate("letter", "Letter", "Formal letter with sender, date, recipient and sign-off") {
        WordDoc(
            title = "Letter",
            blocks = listOf(
                p("[Your name]", bold = true, align = Align.RIGHT, after = 0f),
                p("[Your address]", align = Align.RIGHT, after = 0f),
                p("[Phone · Email]", align = Align.RIGHT),
                p("[Date]", after = 12f),
                p("[Recipient name]", after = 0f),
                p("[Recipient title]", after = 0f),
                p("[Organisation]", after = 0f),
                p("[Address]", after = 12f),
                p("Dear [Name],", after = 12f),
                p("Subject: [What this letter is about]", bold = true, after = 12f),
                p("[Opening paragraph: say why you are writing.]", align = Align.JUSTIFY, after = 8f),
                p("[Middle paragraph: give the details, facts or request.]", align = Align.JUSTIFY, after = 8f),
                p("[Closing paragraph: say what you expect next and thank the reader.]", align = Align.JUSTIFY, after = 12f),
                p("Yours faithfully,", after = 36f),
                p("[Your name]", bold = true, after = 0f),
                p("[Your position]"),
            ),
        )
    }

    val report = DocTemplate("report", "Report", "Title, summary, findings and recommendations") {
        WordDoc(
            title = "Report",
            blocks = listOf(
                p("[Report title]", ParaStyle.TITLE),
                p("Prepared by [name] · [date]", color = 0x666666),
                p("Summary", ParaStyle.H1),
                p("[Two or three sentences: what was looked at, what was found, what to do.]", align = Align.JUSTIFY),
                p("Background", ParaStyle.H1),
                p("[Why this report was needed.]", align = Align.JUSTIFY),
                p("Findings", ParaStyle.H1),
                p("[First finding]", ParaStyle.H2),
                p("[Details and evidence.]", align = Align.JUSTIFY),
                p("[Second finding]", ParaStyle.H2),
                p("[Details and evidence.]", align = Align.JUSTIFY),
                p("Recommendations", ParaStyle.H1),
                Block.Para(Paragraph(listOf(Run("[First recommendation]")), list = ListKind.NUMBER)),
                Block.Para(Paragraph(listOf(Run("[Second recommendation]")), list = ListKind.NUMBER)),
                p("Conclusion", ParaStyle.H1),
                p("[Closing remarks.]", align = Align.JUSTIFY),
            ),
        )
    }

    val cv = DocTemplate("cv", "CV / résumé", "One-page CV with profile, experience, education and skills") {
        WordDoc(
            title = "CV",
            page = PageSetup(marginTopTwips = 1000, marginBottomTwips = 1000, marginLeftTwips = 1200, marginRightTwips = 1200),
            blocks = listOf(
                p("[Your full name]", ParaStyle.TITLE, after = 0f),
                p("[Phone] · [Email] · [City, Country]", color = 0x666666),
                p("Profile", ParaStyle.H1),
                p("[Two lines about who you are and what you do best.]", align = Align.JUSTIFY),
                p("Experience", ParaStyle.H1),
                p("[Job title] — [Employer]", bold = true, after = 0f),
                p("[Month year – Month year]", color = 0x666666, after = 2f),
                Block.Para(Paragraph(listOf(Run("[What you achieved, with a number if you can]")), list = ListKind.BULLET)),
                Block.Para(Paragraph(listOf(Run("[Another responsibility or result]")), list = ListKind.BULLET)),
                p("Education", ParaStyle.H1),
                p("[Qualification] — [School]", bold = true, after = 0f),
                p("[Year]", color = 0x666666),
                p("Skills", ParaStyle.H1),
                Block.Para(Paragraph(listOf(Run("[Skill]")), list = ListKind.BULLET)),
                Block.Para(Paragraph(listOf(Run("[Skill]")), list = ListKind.BULLET)),
                p("References", ParaStyle.H1),
                p("Available on request."),
            ),
        )
    }

    val invoice = DocTemplate("invoice", "Invoice", "Invoice with items table and total") {
        val head = 0xDCE6F5
        val rows = buildList {
            add(listOf(cell("Description", true, shading = head), cell("Qty", true, Align.RIGHT, head), cell("Unit price", true, Align.RIGHT, head), cell("Amount", true, Align.RIGHT, head)))
            repeat(3) { add(listOf(cell("[Item]"), cell("[1]", align = Align.RIGHT), cell("[0.00]", align = Align.RIGHT), cell("[0.00]", align = Align.RIGHT))) }
            add(listOf(cell(""), cell(""), cell("Total", true, Align.RIGHT), cell("[0.00]", true, Align.RIGHT)))
        }
        WordDoc(
            title = "Invoice",
            blocks = listOf(
                p("INVOICE", ParaStyle.TITLE, after = 4f),
                p("[Your business name]", bold = true, after = 0f),
                p("[Address · Phone · Email]", after = 12f),
                p("Invoice no: [0001]", after = 0f),
                p("Date: [date]", after = 0f),
                p("Due: [date]", after = 12f),
                p("Bill to", bold = true, after = 0f),
                p("[Customer name]", after = 0f),
                p("[Customer address]", after = 12f),
                Block.Table(TableBlock(rows, listOf(4200, 900, 1963, 1963), headerRow = true)),
                blank(),
                p("Payment details", bold = true, after = 0f),
                p("[Bank · Account name · Account number]", after = 8f),
                p("Thank you for your business."),
            ),
        )
    }

    val memo = DocTemplate("memo", "Memo", "Short internal note: to, from, date, subject") {
        WordDoc(
            title = "Memo",
            blocks = listOf(
                p("MEMO", ParaStyle.TITLE),
                p("To: [Name or team]", after = 0f),
                p("From: [Your name]", after = 0f),
                p("Date: [date]", after = 0f),
                p("Subject: [Topic]", bold = true, after = 12f),
                p("[Main message. Lead with what the reader must know or do.]", align = Align.JUSTIFY, after = 8f),
                p("[Supporting detail.]", align = Align.JUSTIFY),
            ),
        )
    }

    val minutes = DocTemplate("minutes", "Meeting minutes", "Attendees, agenda, decisions and actions") {
        val head = 0xDCE6F5
        val rows = listOf(
            listOf(cell("Action", true, shading = head), cell("Owner", true, shading = head), cell("Due", true, shading = head)),
            listOf(cell("[Action]"), cell("[Name]"), cell("[Date]")),
            listOf(cell("[Action]"), cell("[Name]"), cell("[Date]")),
        )
        WordDoc(
            title = "Meeting minutes",
            blocks = listOf(
                p("Meeting minutes", ParaStyle.TITLE),
                p("[Meeting name] · [date] · [time] · [place]", color = 0x666666),
                p("Present", ParaStyle.H2),
                p("[Names]"),
                p("Apologies", ParaStyle.H2),
                p("[Names]"),
                p("Agenda and discussion", ParaStyle.H2),
                Block.Para(Paragraph(listOf(Run("[Item one: what was said]")), list = ListKind.NUMBER)),
                Block.Para(Paragraph(listOf(Run("[Item two: what was said]")), list = ListKind.NUMBER)),
                p("Decisions", ParaStyle.H2),
                Block.Para(Paragraph(listOf(Run("[Decision]")), list = ListKind.BULLET)),
                p("Actions", ParaStyle.H2),
                Block.Table(TableBlock(rows, listOf(5026, 2000, 2000), headerRow = true)),
                blank(),
                p("Next meeting: [date]"),
            ),
        )
    }

    val all: List<DocTemplate> = listOf(letter, report, cv, invoice, memo, minutes)

    fun byId(id: String): DocTemplate? = all.firstOrNull { it.id == id }
}
