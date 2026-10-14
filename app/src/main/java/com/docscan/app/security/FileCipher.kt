package com.docscan.app.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authenticated encryption (AES-256-GCM) for files at rest.
 * Layout: "DXE1" | 12-byte random IV | ciphertext + 16-byte tag. The magic is bound in as associated data,
 * so a changed or truncated file fails to decrypt instead of producing garbage.
 * Data without the magic is treated as an old, unencrypted file and passed through (used for migration).
 */
class FileCipher(private val key: SecretKey, private val random: SecureRandom = SecureRandom()) {
    class TamperedException(message: String, cause: Throwable? = null) : SecurityException(message, cause)

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= HEADER + TAG && data[0] == MAGIC[0] && data[1] == MAGIC[1] && data[2] == MAGIC[2] && data[3] == MAGIC[3]

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, iv))
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(plain)
        return MAGIC + iv + body
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (!isEncrypted(data)) return data
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG * 8, data.copyOfRange(4, HEADER)))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(data, HEADER, data.size - HEADER)
        } catch (e: GeneralSecurityException) {
            throw TamperedException("File could not be decrypted (wrong key or modified data)", e)
        }
    }

    companion object {
        val MAGIC = byteArrayOf('D'.code.toByte(), 'X'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV = 12
        private const val TAG = 16
        private const val HEADER = 4 + IV
    }
}
