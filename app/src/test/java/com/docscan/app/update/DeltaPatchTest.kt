package com.docscan.app.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DeflaterOutputStream

class DeltaPatchTest {
    private fun deflate(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        DeflaterOutputStream(o).use { it.write(b) }
        return o.toByteArray()
    }

    private fun off(v: Long): ByteArray {
        val b = ByteArray(8)
        var x = Math.abs(v)
        for (i in 0 until 8) { b[i] = (x and 0xFF).toByte(); x = x shr 8 }
        if (v < 0) b[7] = (b[7].toInt() or 0x80).toByte()
        return b
    }

    /** Builds a patch from explicit control triples, the same layout the CI script produces. */
    private fun patch(newSize: Long, ctrl: List<Triple<Long, Long, Long>>, diff: ByteArray, extra: ByteArray): ByteArray {
        val c = ByteArrayOutputStream().also { o -> ctrl.forEach { o.write(off(it.first)); o.write(off(it.second)); o.write(off(it.third)) } }.toByteArray()
        val cc = deflate(c); val dc = deflate(diff); val ec = deflate(extra)
        val h = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        h.put("DXPATCH1".toByteArray()).putLong(newSize).putLong(c.size.toLong()).putLong(diff.size.toLong()).putLong(extra.size.toLong())
            .putLong(cc.size.toLong()).putLong(dc.size.toLong()).putLong(ec.size.toLong())
        return h.array() + cc + dc + ec
    }

    @Test fun rebuildsNewFileFromOldPlusPatch() {
        val old = ByteArray(1000) { (it % 251).toByte() }
        val new = old.copyOf(1300)
        new[10] = 99
        new[500] = 7
        for (i in 1000 until 1300) new[i] = (i * 3).toByte()
        val diff = ByteArray(1000) { (new[it] - old[it]).toByte() }
        val extra = new.copyOfRange(1000, 1300)
        val p = patch(1300, listOf(Triple(1000L, 300L, 0L)), diff, extra)
        assertArrayEquals(new, DeltaPatch.apply(old, p))
    }

    @Test fun handlesSeeksBackwardsAndForwards() {
        val old = ByteArray(300) { it.toByte() }
        // new = old[100..200) + "XYZ" + old[0..50)
        val part1 = old.copyOfRange(100, 200)
        val part2 = old.copyOfRange(0, 50)
        val new = part1 + "XYZ".toByteArray() + part2
        val diff = ByteArray(100) + ByteArray(50)
        val extra = "XYZ".toByteArray()
        // first: oldPos starts at 0, so seek +100 first by an empty-add control
        val ctrl = listOf(Triple(0L, 0L, 100L), Triple(100L, 3L, -200L), Triple(50L, 0L, 0L))
        val p = patch(153, ctrl, diff, extra)
        assertArrayEquals(new, DeltaPatch.apply(old, p))
    }

    @Test fun rejectsGarbageAndTruncation() {
        val old = ByteArray(10)
        assertTrue(runCatching { DeltaPatch.apply(old, ByteArray(10)) }.isFailure)
        val good = patch(10, listOf(Triple(10L, 0L, 0L)), ByteArray(10), ByteArray(0))
        assertTrue(runCatching { DeltaPatch.apply(old, good.copyOf(good.size - 3)) }.isFailure)
        val lying = patch(5, listOf(Triple(10L, 0L, 0L)), ByteArray(10), ByteArray(0))
        assertTrue(runCatching { DeltaPatch.apply(old, lying) }.isFailure)
    }
}
