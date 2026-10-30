package com.example.core.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise FinTech Security Manager providing AES-256-GCM encryption,
 * Android KeyStore key lifecycle management, and PII masking.
 */
object FintechSecurityManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SmartBudgetFintechKey"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // In-memory fallback key for testing environments (Robolectric/JVM unit tests)
    private var testSecretKey: SecretKey? = null

    init {
        initOrGetSecretKey()
    }

    private fun initOrGetSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                keyStore.getKey(KEY_ALIAS, null) as SecretKey
            }
        } catch (e: Throwable) {
            // Testing environment fallback (Standard 256-bit AES)
            if (testSecretKey == null) {
                val secureRandom = SecureRandom()
                val keyBytes = ByteArray(32)
                secureRandom.nextBytes(keyBytes)
                testSecretKey = SecretKeySpec(keyBytes, "AES")
            }
            testSecretKey!!
        }
    }

    /**
     * Encrypts plaintext string using AES-256-GCM.
     * Returns Base64-encoded IV + Ciphertext.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val key = initOrGetSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        // Prepend IV to ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return base64Encode(combined)
    }

    /**
     * Decrypts Base64-encoded IV + Ciphertext back to plaintext.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val combined = base64Decode(encryptedBase64)
            if (combined.size < GCM_IV_LENGTH) return encryptedBase64

            val iv = ByteArray(GCM_IV_LENGTH)
            val ciphertext = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val key = initOrGetSecretKey()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Return raw string if not encrypted
            encryptedBase64
        }
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

    /**
     * Creates a deterministic SHA-256 hash for audit logs and verification.
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Masks an email address for compliance (e.g. j***n@example.com).
     */
    fun maskEmail(email: String): String {
        val atIdx = email.indexOf('@')
        if (atIdx <= 1) return email
        val user = email.substring(0, atIdx)
        val domain = email.substring(atIdx)
        val masked = if (user.length > 2) {
            "${user.first()}${"*".repeat(user.length - 2)}${user.last()}"
        } else {
            "${user.first()}*"
        }
        return "$masked$domain"
    }

    /**
     * Masks account numbers or card numbers showing only the last 4 digits.
     */
    fun maskAccountNumber(accountNum: String): String {
        if (accountNum.length <= 4) return accountNum
        return "•••• " + accountNum.takeLast(4)
    }
}
