package com.docscan.app.security

import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encryptThenDecryptReturnsTheOriginal() {
        val c = FileCipher(newKey())
        val data = ByteArray(5000) { (it * 31).toByte() }
        val enc = c.encrypt(data)
        assertTrue(c.isEncrypted(enc))
        assertFalse(enc.copyOfRange(16, 80).contentEquals(data.copyOfRange(0, 64)))
        assertArrayEquals(data, c.decrypt(enc))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val c = FileCipher(newKey())
        val data = "same text".toByteArray()
        assertNotEquals(c.encrypt(data).toList(), c.encrypt(data).toList())
    }

    @Test(expected = FileCipher.TamperedException::class)
    fun modifiedFileIsDetected() {
        val c = FileCipher(newKey())
        val enc = c.encrypt("secret".toByteArray())
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        c.decrypt(enc)
    }

    @Test(expected = FileCipher.TamperedException::class)
    fun wrongKeyCannotDecrypt() {
        val enc = FileCipher(newKey()).encrypt("secret".toByteArray())
        FileCipher(newKey()).decrypt(enc)
    }

    @Test
    fun oldPlainFilesPassThrough() {
        val c = FileCipher(newKey())
        val jpegLike = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        assertFalse(c.isEncrypted(jpegLike))
        assertArrayEquals(jpegLike, c.decrypt(jpegLike))
    }

    @Test
    fun secureFilesWriteReadWipeAndMigrate() {
        val dir = java.nio.file.Files.createTempDirectory("docxels").toFile()
        try {
            val previous = SecureFiles.cipher
            SecureFiles.cipher = null
            val legacy = File(dir, "old.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)) }
            SecureFiles.cipher = FileCipher(newKey())
            assertEquals(1, SecureFiles.migrate(dir))
            assertEquals(0, SecureFiles.migrate(dir)) // second run changes nothing
            assertTrue(SecureFiles.cipher!!.isEncrypted(legacy.readBytes()))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21), SecureFiles.read(legacy))

            val f = File(dir, "new.jpg")
            SecureFiles.write(f, "page".toByteArray())
            assertFalse(File(dir, "new.jpg.tmp").exists())
            assertEquals("page", String(SecureFiles.read(f)!!))
            assertNull(SecureFiles.read(File(dir, "missing.jpg")))

            SecureFiles.wipe(f)
            assertFalse(f.exists())
            SecureFiles.cipher = previous
        } finally {
            SecureFiles.cipher = null
            dir.deleteRecursively()
        }
    }

    @Test
    fun lockPolicyFollowsTheTimeout() {
        assertFalse(LockPolicy.shouldLock(false, 0, null, 1000))
        assertTrue(LockPolicy.shouldLock(true, 60_000, null, 1000)) // cold start
        assertFalse(LockPolicy.shouldLock(true, 60_000, 1_000, 30_000))
        assertTrue(LockPolicy.shouldLock(true, 60_000, 1_000, 61_001))
        assertTrue(LockPolicy.shouldLock(true, 0, 5_000, 5_000))
        assertTrue(LockPolicy.shouldLock(true, 60_000, 9_000, 1_000)) // clock went backwards (reboot)
    }
}
