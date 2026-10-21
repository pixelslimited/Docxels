package com.docscan.app.core

import com.docscan.app.core.word.MiniXml
import com.docscan.app.core.word.XNode
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream

/** Reads a recipient list from CSV text or the first sheet of an .xlsx file, into rows of text. Sizes are capped. */
object RecipientFiles {
    private const val MAX_ROWS = 5000
    private const val MAX_COLS = 60
    private const val MAX_ENTRY = 20L * 1024 * 1024

    /** Comma, semicolon or tab, whichever the first line uses most. */
    fun parseCsv(text: String): List<List<String>> {
        val src = text.removePrefix("﻿")
        val firstLine = src.lineSequence().firstOrNull().orEmpty()
        val delim = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }?.takeIf { d -> firstLine.contains(d) } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); if (row.size > MAX_COLS) row = ArrayList(row.take(MAX_COLS)); rows.add(row); row = ArrayList() }
        while (i < src.length && rows.size < MAX_ROWS) {
            val ch = src[i]
            when {
                quoted -> if (ch == '"') { if (i + 1 < src.length && src[i + 1] == '"') { cell.append('"'); i++ } else quoted = false } else cell.append(ch)
                ch == '"' && cell.isEmpty() -> quoted = true
                ch == delim -> endCell()
                ch == '\n' -> endRow()
                ch == '\r' -> {}
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** First worksheet of an .xlsx as rows of text, or null if it is not a readable workbook. */
    fun parseXlsx(bytes: ByteArray): List<List<String>>? {
        val files = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || !(e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet"))) continue
                    val buf = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val n = zip.read(chunk); if (n < 0) break
                        buf.write(chunk, 0, n)
                        if (buf.size() > MAX_ENTRY) return null
                    }
                    files[e.name] = buf.toByteArray()
                }
            }
        }.onFailure { return null }
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.sorted().let { l -> l.firstOrNull { it == "xl/worksheets/sheet1.xml" } ?: l.firstOrNull() } ?: return null
        return runCatching {
            val strings = files["xl/sharedStrings.xml"]?.let { b ->
                MiniXml.parse(String(b, Charsets.UTF_8)).findAll("si").map { si -> si.findAll("t").joinToString("") { it.text.toString() } }
            }.orEmpty()
            val sheet = MiniXml.parse(String(files.getValue(sheetName), Charsets.UTF_8))
            val out = ArrayList<List<String>>()
            for (r in sheet.findAll("row")) {
                if (out.size >= MAX_ROWS) break
                val cells = ArrayList<String>()
                for (c in r.childrenNamed("c")) {
                    val col = columnIndex(c.attr("r").orEmpty()).takeIf { it >= 0 } ?: cells.size
                    if (col >= MAX_COLS) continue
                    while (cells.size < col) cells += ""
                    val v = c.child("v")?.text?.toString().orEmpty()
                    val value = when (c.attr("t")) {
                        "s" -> strings.getOrNull(v.trim().toIntOrNull() ?: -1).orEmpty()
                        "inlineStr" -> c.findAll("t").joinToString("") { it.text.toString() }
                        else -> number(v)
                    }
                    if (cells.size == col) cells += value else cells[col] = value
                }
                out += cells
            }
            out
        }.getOrNull()
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    /** Numbers Excel stores in scientific form are written out in full. */
    private fun number(v: String): String {
        val t = v.trim()
        if (t.contains('E') || t.contains('e')) return runCatching { BigDecimal(t).toPlainString().removeSuffix(".0") }.getOrDefault(t)
        return t.removeSuffix(".0")
    }
}
