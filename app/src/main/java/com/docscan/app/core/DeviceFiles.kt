package com.docscan.app.core

import java.io.File
import java.io.InputStream

/** Reads at most [max] bytes (returns max+1 bytes if the stream is longer, so callers can detect "too big"). Works on every Android version. */
fun InputStream.readUpTo(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (total <= max) {
        val n = read(buf, 0, minOf(buf.size, max + 1 - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

/** A document found on the phone's storage. */
class DeviceFile(val file: File, val modified: Long, val size: Long) {
    val name: String get() = file.name
    val folder: String get() = file.parentFile?.name ?: ""
}

/**
 * Finds documents (PDF, Word, text) anywhere on the phone's storage. The walk runs once and is kept in memory;
 * typing then only filters that list, so every keystroke is instant. Pure Kotlin so the filtering is unit-tested.
 */
object DeviceFiles {
    private val EXT = setOf("pdf", "docx", "txt")
    private const val MAX_FILES = 20_000
    private const val MAX_DEPTH = 12
    const val MAX_RESULTS = 25

    /** Folders that only hold app internals, caches or the bin: never worth searching. */
    private fun skip(dir: File): Boolean {
        val n = dir.name
        return n.startsWith(".") || n == "Android" || n == "cache" || n == "thumbnails" || n == "node_modules"
    }

    fun scan(root: File): List<DeviceFile> {
        val out = ArrayList<DeviceFile>()
        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
            val kids = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (f in kids) {
                if (out.size >= MAX_FILES) return
                if (f.isDirectory) { if (!skip(f)) walk(f, depth + 1) }
                else if (f.extension.lowercase() in EXT && f.length() > 0) out += DeviceFile(f, f.lastModified(), f.length())
            }
        }
        walk(root, 0)
        return out
    }

    /**
     * Every typed word must appear in the file name (any order, any case). Names that start with the typing rank first,
     * then the most recently changed.
     */
    fun filter(all: List<DeviceFile>, query: String, limit: Int = MAX_RESULTS): List<DeviceFile> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return all.asSequence()
            .filter { f -> val n = f.name.lowercase(); words.all { it in n } }
            .sortedWith(compareByDescending<DeviceFile> { f -> f.name.lowercase().startsWith(words[0]) }.thenByDescending { it.modified })
            .take(limit).toList()
    }
}
