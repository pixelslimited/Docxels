package com.docscan.app.update

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

/**
 * Rebuilds the new APK from the installed one plus a small patch ("DXPATCH1": a bsdiff patch whose three
 * parts are deflate-compressed). No networking here; the result is verified against the published SHA-256 by the
 * caller before anything is installed, so a bad or tampered patch can only cause a fallback to the full download.
 */
object DeltaPatch {
    private val MAGIC = "DXPATCH1".toByteArray(Charsets.US_ASCII)
    private const val HEADER = 64

    class PatchException(message: String) : Exception(message)

    /** Applies [patch] to [oldFile] and writes the result to [out]. Throws [PatchException] for anything inconsistent. */
    fun apply(oldFile: File, patch: ByteArray, out: File) {
        val old = oldFile.readBytes()
        val result = apply(old, patch)
        out.writeBytes(result)
    }

    fun apply(old: ByteArray, patch: ByteArray): ByteArray {
        if (patch.size < HEADER || !patch.copyOfRange(0, 8).contentEquals(MAGIC)) throw PatchException("Not a patch")
        val h = ByteBuffer.wrap(patch, 8, HEADER - 8).order(ByteOrder.LITTLE_ENDIAN)
        val newSize = h.long
        val ctrlRaw = h.long
        val diffRaw = h.long
        val extraRaw = h.long
        val ctrlComp = h.long
        val diffComp = h.long
        val extraComp = h.long
        val maxOut = UpdateInfo.MAX_APK_BYTES
        if (newSize !in 1..maxOut || ctrlRaw !in 0..(64L * 1024 * 1024) || diffRaw !in 0..maxOut || extraRaw !in 0..maxOut) throw PatchException("Bad sizes")
        if (ctrlComp < 0 || diffComp < 0 || extraComp < 0 || HEADER + ctrlComp + diffComp + extraComp != patch.size.toLong()) throw PatchException("Bad lengths")

        fun part(start: Long, len: Long): InputStream =
            InflaterInputStream(ByteArrayInputStream(patch, start.toInt(), len.toInt()))
        val ctrl = DataInputStream(part(HEADER.toLong(), ctrlComp))
        val diff = part(HEADER + ctrlComp, diffComp)
        val extra = part(HEADER + ctrlComp + diffComp, extraComp)

        val new = ByteArray(newSize.toInt())
        var oldPos = 0L
        var newPos = 0L
        var diffUsed = 0L
        var extraUsed = 0L
        val buf = ByteArray(64 * 1024)
        while (newPos < newSize) {
            val add = readInt64(ctrl)
            val copy = readInt64(ctrl)
            val seek = readInt64(ctrl)
            if (add < 0 || copy < 0 || newPos + add + copy > newSize) throw PatchException("Corrupt control data")
            diffUsed += add
            extraUsed += copy
            if (diffUsed > diffRaw || extraUsed > extraRaw) throw PatchException("Corrupt data")
            var left = add
            while (left > 0) {
                val n = diff.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
                if (n < 0) throw PatchException("Patch ended early")
                for (i in 0 until n) {
                    val o = oldPos + i
                    val base = if (o in 0 until old.size) old[o.toInt()].toInt() else 0
                    new[(newPos + i).toInt()] = (buf[i].toInt() + base).toByte()
                }
                newPos += n
                oldPos += n
                left -= n
            }
            left = copy
            while (left > 0) {
                val n = extra.read(new, newPos.toInt(), left.toInt())
                if (n < 0) throw PatchException("Patch ended early")
                newPos += n
                left -= n
            }
            oldPos += seek
        }
        return new
    }

    /** bsdiff's sign-and-magnitude 64-bit little-endian integer. */
    private fun readInt64(s: DataInputStream): Long {
        val b = ByteArray(8)
        s.readFully(b)
        var v = 0L
        for (i in 6 downTo 0) v = (v shl 8) or (b[i].toLong() and 0xFF)
        v = v or ((b[7].toLong() and 0x7F) shl 56)
        return if (b[7].toInt() and 0x80 != 0) -v else v
    }
}
