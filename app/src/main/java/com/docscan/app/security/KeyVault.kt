package com.docscan.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Holds the key that encrypts page files. A random 256-bit data key is generated once and stored only in
 * wrapped form: it is encrypted by a non-exportable AES key that lives in the Android Keystore (hardware-backed
 * where the phone has it). The data key never touches disk in the clear.
 */
class KeyVault(context: Context) {
    private val blob = File(context.filesDir, "vault.bin")

    @Volatile
    private var cached: SecretKey? = null

    @Synchronized
    fun dataKey(): SecretKey {
        cached?.let { return it }
        val master = masterKey()
        val key = if (blob.exists()) unwrap(master, blob.readBytes()) else createAndStore(master)
        cached = key
        return key
    }

    private fun createAndStore(master: SecretKey): SecretKey {
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, master)
        val wrapped = cipher.iv + cipher.doFinal(raw)
        val tmp = File(blob.parentFile, "vault.tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(blob)) {
            tmp.delete()
            error("Could not store the encryption key")
        }
        return SecretKeySpec(raw, "AES")
    }

    private fun unwrap(master: SecretKey, data: ByteArray): SecretKey {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, data, 0, 12))
        return SecretKeySpec(cipher.doFinal(data, 12, data.size - 12), "AES")
    }

    private fun masterKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try {
            generator.init(spec(strongBox = true))
            generator.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            generator.init(spec(strongBox = false))
            generator.generateKey()
        }
    }

    private companion object {
        const val ALIAS = "docxels_master_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
