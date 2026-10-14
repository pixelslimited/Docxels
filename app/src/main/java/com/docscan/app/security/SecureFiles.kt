package com.docscan.app.security

import java.io.File

/**
 * Single gate for reading and writing page files. When a [FileCipher] is installed every write is encrypted
 * and every read decrypted; without one (unit tests) files stay plain. Writes go to a temp file first and are
 * renamed into place, so a crash can never leave a half-written page.
 */
object SecureFiles {
    @Volatile
    var cipher: FileCipher? = null

    fun write(file: File, bytes: ByteArray) {
        val data = cipher?.encrypt(bytes) ?: bytes
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                tmp.delete()
                error("Could not save ${file.name}")
            }
        }
    }

    fun read(file: File): ByteArray? {
        if (!file.exists()) return null
        val raw = file.readBytes()
        return cipher?.decrypt(raw) ?: raw
    }

    /** Overwrites the file's bytes with zeros before deleting it. Best effort: flash storage may keep old blocks. */
    fun wipe(file: File) {
        if (!file.exists()) return
        runCatching {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                val zeros = ByteArray(64 * 1024)
                var left = raf.length()
                raf.seek(0)
                while (left > 0) {
                    val n = minOf(left, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, n)
                    left -= n
                }
                raf.fd.sync()
            }
        }
        file.delete()
    }

    fun wipeDirectory(dir: File) {
        dir.listFiles()?.forEach { if (it.isDirectory) wipeDirectory(it) else wipe(it) }
        dir.delete()
    }

    /** Encrypts any page files saved by older versions. Safe to run repeatedly. Returns how many files were converted. */
    fun migrate(root: File): Int {
        val c = cipher ?: return 0
        var converted = 0
        root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
            runCatching {
                val raw = f.readBytes()
                if (!c.isEncrypted(raw)) {
                    write(f, raw)
                    converted++
                }
            }
        }
        return converted
    }
}
