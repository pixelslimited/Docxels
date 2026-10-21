package com.docscan.app.data

import android.content.Context
import com.docscan.app.security.SecureFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** What the person has added to a library item on top of the item itself. */
data class ItemMeta(
    val favourite: Boolean = false,
    val folder: String = "",
    val tags: Set<String> = emptySet(),
    /** Time it was moved to Trash, or 0 when it is not in Trash. */
    val trashedAt: Long = 0L,
    /** Where the person stopped: page (1-based), pages in all, and the caret position in a Word document. 0 = unknown. */
    val lastPage: Int = 0,
    val pageTotal: Int = 0,
    val lastOffset: Int = 0,
) {
    val isDefault: Boolean get() = !favourite && folder.isEmpty() && tags.isEmpty() && trashedAt == 0L && lastPage == 0 && lastOffset == 0
}

/**
 * Favourites, folders, tags and Trash for scans and Word documents together. Kept in one small encrypted index
 * so no database migration is needed. Keys look like "DOCX:<id>" or "SCAN:<id>".
 */
class LibraryMeta(context: Context) {
    private val file = File(File(context.filesDir, "library").apply { mkdirs() }, "meta.tsv")
    private val lock = Any()
    private val _all = MutableStateFlow(runCatching { parse(SecureFiles.read(file)?.toString(Charsets.UTF_8).orEmpty()) }.getOrDefault(emptyMap()))
    val all: StateFlow<Map<String, ItemMeta>> = _all

    fun get(kind: DocKind, id: String): ItemMeta = _all.value[key(kind, id)] ?: ItemMeta()

    fun update(kind: DocKind, id: String, change: (ItemMeta) -> ItemMeta) = synchronized(lock) {
        val k = key(kind, id)
        val next = change(_all.value[k] ?: ItemMeta())
        val map = _all.value.toMutableMap()
        if (next.isDefault) map.remove(k) else map[k] = next
        save(map)
    }


    private fun save(map: Map<String, ItemMeta>) {
        SecureFiles.write(file, serialize(map).toByteArray(Charsets.UTF_8))
        _all.value = map
    }

    companion object {
        fun key(kind: DocKind, id: String) = "${kind.name}:$id"

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace(',', ' ').trim()

        fun cleanName(s: String) = clean(s).take(40)

        fun serialize(map: Map<String, ItemMeta>): String = map.entries.joinToString("\n") { (k, m) ->
            listOf(k, if (m.favourite) "1" else "0", clean(m.folder), m.tags.joinToString(",") { clean(it) }, m.trashedAt.toString(), m.lastPage.toString(), m.pageTotal.toString(), m.lastOffset.toString()).joinToString("\t")
        }

        fun parse(text: String): Map<String, ItemMeta> = text.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5 || p[0].isEmpty()) null
            else p[0] to ItemMeta(
                favourite = p[1] == "1",
                folder = p[2],
                tags = p[3].split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                trashedAt = p[4].toLongOrNull() ?: 0L,
                lastPage = p.getOrNull(5)?.toIntOrNull() ?: 0,
                pageTotal = p.getOrNull(6)?.toIntOrNull() ?: 0,
                lastOffset = p.getOrNull(7)?.toIntOrNull() ?: 0,
            )
        }.toMap()
    }
}
