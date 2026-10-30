package com.example.core.security

import org.junit.Assert.*
import org.junit.Test

class FintechSecurityManagerTest {

    @Test
    fun `encrypt and decrypt roundtrip preserves original plaintext`() {
        val sensitiveData = "User bank account: 123456789012, PAN: ABCDE1234F"

        val encrypted = FintechSecurityManager.encrypt(sensitiveData)
        assertNotEquals(sensitiveData, encrypted)
        assertTrue(encrypted.isNotEmpty())

        val decrypted = FintechSecurityManager.decrypt(encrypted)
        assertEquals(sensitiveData, decrypted)
    }

    @Test
    fun `encrypt handles empty string gracefully`() {
        val result = FintechSecurityManager.encrypt("")
        assertEquals("", result)
    }

    @Test
    fun `decrypt handles unencrypted string gracefully without crash`() {
        val nonEncrypted = "Just normal plain text"
        val result = FintechSecurityManager.decrypt(nonEncrypted)
        assertEquals(nonEncrypted, result)
    }

    @Test
    fun `sha256 produces consistent deterministic hashes`() {
        val input = "AuditLog: Transaction#991 created by user@test.com"
        val hash1 = FintechSecurityManager.sha256(input)
        val hash2 = FintechSecurityManager.sha256(input)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // 256 bits = 64 hex characters
    }

    @Test
    fun `maskEmail masks username preserving first and last char and domain`() {
        val email = "alexander@fintech.com"
        val masked = FintechSecurityManager.maskEmail(email)

        assertEquals("a*******r@fintech.com", masked)
    }

    @Test
    fun `maskAccountNumber masks card displaying only last four digits`() {
        val cardNumber = "4532789012345678"
        val masked = FintechSecurityManager.maskAccountNumber(cardNumber)

        assertEquals("•••• 5678", masked)
    }
}
