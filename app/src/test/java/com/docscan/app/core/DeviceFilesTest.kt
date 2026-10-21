package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceFilesTest {
    private fun f(name: String, t: Long) = DeviceFile(File("/sdcard/Download/$name"), t, 10)

    @Test fun everyWordMustMatchAnyOrder() {
        val all = listOf(f("Imam List 2026.pdf", 1), f("Invoice.docx", 2), f("list of imams.txt", 3))
        assertEquals(setOf("Imam List 2026.pdf", "list of imams.txt"), DeviceFiles.filter(all, "imam list").map { it.name }.toSet())
    }

    @Test fun startsWithRanksFirstThenNewest() {
        val all = listOf(f("old report.pdf", 5), f("my report.pdf", 9), f("report final.pdf", 1))
        assertEquals(listOf("report final.pdf", "my report.pdf", "old report.pdf"), DeviceFiles.filter(all, "REPORT").map { it.name })
    }

    @Test fun blankQueryAndNoMatchGiveNothing() {
        val all = listOf(f("a.pdf", 1))
        assertTrue(DeviceFiles.filter(all, "  ").isEmpty())
        assertTrue(DeviceFiles.filter(all, "zzz").isEmpty())
    }

    @Test fun readUpToStopsAtLimit() {
        assertEquals(6, ByteArray(100).inputStream().readUpTo(5).size)
        assertEquals(3, ByteArray(3).inputStream().readUpTo(5).size)
    }

    @Test fun scanSkipsHiddenAndAndroidFoldersAndOtherTypes() {
        val root = kotlin.io.path.createTempDirectory("dev").toFile()
        try {
            File(root, "Documents").mkdirs(); File(root, "Android/data").mkdirs(); File(root, ".trash").mkdirs()
            File(root, "Documents/a.pdf").writeText("x"); File(root, "Documents/b.jpg").writeText("x")
            File(root, "Android/data/c.pdf").writeText("x"); File(root, ".trash/d.pdf").writeText("x")
            assertEquals(listOf("a.pdf"), DeviceFiles.scan(root).map { it.name })
        } finally { root.deleteRecursively() }
    }
}
