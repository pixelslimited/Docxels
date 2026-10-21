package com.docscan.app.core.word

/**
 * Mail merge: a letter with fields such as {{Name}} and a list of recipients become one document with a page per
 * recipient. Fields are plain text, so they work in paragraphs and table cells and survive saving as .docx.
 */
object MailMerge {
    private val token = Regex("""\{\{\s*([^{}\n]+?)\s*\}\}""")

    /** [columns] keep the names as the person wrote them; each row is keyed by the lower-case column name. */
    class Recipients(val columns: List<String>, val rows: List<Map<String, String>>)

    fun fieldsIn(text: String): List<String> = token.findAll(text).map { it.groupValues[1].trim() }.toList()

    /** Every distinct field used in the body and in table cells, in order of first use. */
    fun fieldNames(doc: WordDoc): List<String> {
        val out = LinkedHashMap<String, String>()
        fun see(t: String) = fieldsIn(t).forEach { out.putIfAbsent(it.lowercase(), it) }
        for (b in doc.blocks) when (b) {
            is Block.Para -> see(b.paragraph.text)
            is Block.Table -> b.table.rows.forEach { r -> r.forEach { c -> c.paragraphs.forEach { see(it.text) } } }
            else -> {}
        }
        return out.values.toList()
    }

    /** Fields the letter uses that the list has no column for. */
    fun missingColumns(doc: WordDoc, rec: Recipients): List<String> {
        val have = rec.columns.map { it.trim().lowercase() }.toSet()
        return fieldNames(doc).filter { it.lowercase() !in have }
    }

    private fun lookup(row: Map<String, String>, name: String): String? = row[name.trim().lowercase()]

    fun fillText(text: String, row: Map<String, String>): String = token.replace(text) { m -> lookup(row, m.groupValues[1]) ?: m.value }

    /** Replaces fields in a paragraph's runs. A field split over several runs takes the formatting of its first character. */
    fun fillRuns(runs: List<Run>, row: Map<String, String>): List<Run> {
        val full = runs.joinToString("") { it.text }
        val matches = token.findAll(full).toList()
        if (matches.isEmpty()) return runs
        val owner = IntArray(full.length)
        var p = 0
        runs.forEachIndexed { i, r -> repeat(r.text.length) { owner[p++] = i } }
        val out = ArrayList<Run>()
        var lastOwner = -1
        fun emit(runIdx: Int, s: String) {
            if (s.isEmpty()) return
            if (out.isNotEmpty() && lastOwner == runIdx) out[out.lastIndex] = out.last().copy(text = out.last().text + s)
            else out += Run(s, runs[runIdx].style)
            lastOwner = runIdx
        }
        fun plain(from: Int, to: Int) {
            var i = from
            while (i < to) {
                var j = i
                while (j < to && owner[j] == owner[i]) j++
                emit(owner[i], full.substring(i, j))
                i = j
            }
        }
        var pos = 0
        for (m in matches) {
            plain(pos, m.range.first)
            emit(owner[m.range.first], lookup(row, m.groupValues[1]) ?: m.value)
            pos = m.range.last + 1
        }
        plain(pos, full.length)
        return out
    }

    private fun fillPara(p: Paragraph, row: Map<String, String>) = p.copy(runs = fillRuns(p.runs, row))

    fun fillBlock(b: Block, row: Map<String, String>): Block = when (b) {
        is Block.Para -> Block.Para(fillPara(b.paragraph, row))
        is Block.Table -> Block.Table(
            b.table.copy(rows = b.table.rows.map { r -> r.map { c -> c.copy(paragraphs = c.paragraphs.map { fillPara(it, row) }) } }),
        )
        else -> b
    }

    /** One copy of the letter per recipient, each on its own page. [skipBlock] leaves out the block that holds the list itself. */
    fun merge(doc: WordDoc, rec: Recipients, title: String, skipBlock: Int = -1): WordDoc {
        val template = doc.blocks.filterIndexed { i, _ -> i != skipBlock }
        val blocks = ArrayList<Block>()
        rec.rows.forEachIndexed { n, row ->
            if (n > 0) blocks += Block.PageBreak
            template.forEach { blocks += fillBlock(it, row) }
        }
        return doc.copy(title = title, blocks = blocks)
    }

    /** A list from rows of text: the first non-empty row names the columns. */
    fun fromGrid(grid: List<List<String>>): Recipients? {
        val header = grid.indexOfFirst { r -> r.any { it.isNotBlank() } }
        if (header < 0) return null
        val used = HashSet<String>()
        val columns = grid[header].mapIndexed { i, raw ->
            var name = raw.trim().ifEmpty { "Column ${i + 1}" }
            while (!used.add(name.lowercase())) name += " ${i + 1}"
            name
        }
        val rows = grid.drop(header + 1).filter { r -> r.any { it.isNotBlank() } }.map { r ->
            columns.indices.associate { i -> columns[i].lowercase() to r.getOrElse(i) { "" }.trim() }
        }
        return if (rows.isEmpty()) null else Recipients(columns, rows)
    }

    /** A table in the document as a list: its first row is the column names. */
    fun fromTable(t: TableBlock): Recipients? =
        fromGrid(t.rows.map { r -> r.map { c -> c.paragraphs.joinToString(" ") { it.text } } })
}
