package com.example.core.security

import android.content.Context
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Enterprise FinTech Crypto Manager.
 * Manages Master Keystore keys and SQLite encryption passphrases.
 */
class CryptoManager(private val context: Context? = null) {

    /**
     * Retrieves or generates a 256-bit database passphrase backed by Android KeyStore.
     */
    fun databasePassphrase(): ByteArray {
        val prefs = context?.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        val existingEncoded = prefs?.getString(KEY_DB_PASSPHRASE, null)

        if (existingEncoded != null) {
            val decrypted = FintechSecurityManager.decrypt(existingEncoded)
            return base64Decode(decrypted)
        }

        val newPassphrase = ByteArray(DB_KEY_BYTES).also { bytes ->
            SecureRandom().nextBytes(bytes)
        }

        if (prefs != null) {
            val base64Raw = base64Encode(newPassphrase)
            val encrypted = FintechSecurityManager.encrypt(base64Raw)
            prefs.edit().putString(KEY_DB_PASSPHRASE, encrypted).apply()
        }

        return newPassphrase
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (e: Throwable) {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        }
    }

    companion object {
        private const val SECURE_PREFS_NAME = "secure_budget_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
        private const val DB_KEY_BYTES = 32
    }
}
