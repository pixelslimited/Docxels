package com.docscan.app.core.word

/**
 * A small, dependency-free XML reader for Office files (pure Kotlin, so it also runs in JVM unit tests).
 * Namespace prefixes are ignored on purpose: Word, LibreOffice and Google Docs all use different prefixes
 * for the same elements, so lookups use local names ("p", "r", "val").
 */
class XNode(val name: String, val attrs: Map<String, String>) {
    val children = ArrayList<XNode>()

    /** Character data found directly inside this element (not inside its children). */
    val text = StringBuilder()

    /** Name without its namespace prefix, e.g. "w:p" -> "p". */
    val local: String get() = name.substringAfter(':')

    /** Attribute by local name, e.g. "val" matches "w:val". */
    fun attr(local: String): String? {
        attrs[local]?.let { return it }
        for ((key, value) in attrs) if (key.substringAfter(':') == local) return value
        return null
    }

    fun child(local: String): XNode? = children.firstOrNull { it.local == local }
    fun childrenNamed(local: String): List<XNode> = children.filter { it.local == local }

    /** First descendant (depth first) with this local name. */
    fun find(local: String): XNode? {
        for (c in children) {
            if (c.local == local) return c
            c.find(local)?.let { return it }
        }
        return null
    }

    fun findAll(local: String, into: MutableList<XNode> = ArrayList()): List<XNode> {
        for (c in children) {
            if (c.local == local) into += c
            c.findAll(local, into)
        }
        return into
    }
}

object MiniXml {
    class XmlException(message: String) : Exception(message)

    fun parse(source: String): XNode {
        val root = XNode("#document", emptyMap())
        val stack = ArrayList<XNode>()
        stack += root
        var i = 0
        val n = source.length
        if (source.startsWith("﻿")) i = 1

        while (i < n) {
            val lt = source.indexOf('<', i)
            if (lt < 0) {
                appendText(stack.last(), source.substring(i))
                break
            }
            if (lt > i) appendText(stack.last(), source.substring(i, lt))
            i = lt
            when {
                source.startsWith("<!--", i) -> {
                    val end = source.indexOf("-->", i + 4)
                    if (end < 0) throw XmlException("Unterminated comment")
                    i = end + 3
                }
                source.startsWith("<![CDATA[", i) -> {
                    val end = source.indexOf("]]>", i + 9)
                    if (end < 0) throw XmlException("Unterminated CDATA")
                    stack.last().text.append(source, i + 9, end)
                    i = end + 3
                }
                source.startsWith("<?", i) -> {
                    val end = source.indexOf("?>", i + 2)
                    if (end < 0) throw XmlException("Unterminated processing instruction")
                    i = end + 2
                }
                source.startsWith("<!", i) -> {
                    // DOCTYPE and similar: skip to the matching '>' (internal subsets are not used in Office files).
                    var depth = 0
                    var j = i
                    while (j < n) {
                        val c = source[j]
                        if (c == '<') depth++
                        if (c == '>') {
                            depth--
                            if (depth == 0) break
                        }
                        j++
                    }
                    i = j + 1
                }
                source.startsWith("</", i) -> {
                    val end = source.indexOf('>', i)
                    if (end < 0) throw XmlException("Unterminated closing tag")
                    if (stack.size > 1) stack.removeAt(stack.size - 1)
                    i = end + 1
                }
                else -> i = readOpeningTag(source, i, stack)
            }
        }
        return root.children.firstOrNull() ?: throw XmlException("No root element")
    }

    private fun appendText(node: XNode, raw: String) {
        if (raw.isEmpty()) return
        node.text.append(decode(raw))
    }

    private fun readOpeningTag(s: String, start: Int, stack: ArrayList<XNode>): Int {
        var i = start + 1
        val n = s.length
        val nameStart = i
        while (i < n && !s[i].isWhitespace() && s[i] != '>' && s[i] != '/') i++
        val name = s.substring(nameStart, i)
        val attrs = LinkedHashMap<String, String>()
        var selfClosing = false
        while (i < n) {
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n) throw XmlException("Unterminated tag <$name")
            val c = s[i]
            if (c == '>') {
                i++
                break
            }
            if (c == '/') {
                selfClosing = true
                i++
                continue
            }
            val keyStart = i
            while (i < n && s[i] != '=' && !s[i].isWhitespace() && s[i] != '>' && s[i] != '/') i++
            val key = s.substring(keyStart, i)
            while (i < n && s[i].isWhitespace()) i++
            if (i < n && s[i] == '=') {
                i++
                while (i < n && s[i].isWhitespace()) i++
                if (i >= n) throw XmlException("Bad attribute in <$name")
                val quote = s[i]
                if (quote != '"' && quote != '\'') throw XmlException("Unquoted attribute in <$name")
                val end = s.indexOf(quote, i + 1)
                if (end < 0) throw XmlException("Unterminated attribute in <$name")
                attrs[key] = decode(s.substring(i + 1, end))
                i = end + 1
            } else if (key.isNotEmpty()) {
                attrs[key] = ""
            }
        }
        val node = XNode(name, attrs)
        stack.last().children += node
        if (!selfClosing) stack += node
        return i
    }

    /** Decodes the five predefined entities and numeric character references. */
    fun decode(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val semi = s.indexOf(';', i)
            if (semi < 0 || semi - i > 10) {
                sb.append(c)
                i++
                continue
            }
            val entity = s.substring(i + 1, semi)
            val replacement: String? = when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.let { codePoint(it) }
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let { codePoint(it) }
                else -> null
            }
            if (replacement == null) {
                sb.append(c)
                i++
            } else {
                sb.append(replacement)
                i = semi + 1
            }
        }
        return sb.toString()
    }

    private fun codePoint(value: Int): String? =
        if (value in 0..0x10FFFF) String(Character.toChars(value)) else null
}
