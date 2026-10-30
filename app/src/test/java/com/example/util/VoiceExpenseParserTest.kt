package com.example.util

import org.junit.Assert.*
import org.junit.Test

class VoiceExpenseParserTest {

    @Test
    fun `parse extracts food expense correctly`() {
        val input = "Spent 250 rs on lunch with colleagues"
        val parsed = VoiceExpenseParser.parse(input)

        assertNotNull(parsed)
        assertEquals(250.0, parsed!!.amount, 0.001)
        assertEquals("Food", parsed.category)
        assertEquals(input, parsed.description)
    }

    @Test
    fun `parse extracts transportation expense with rupees keyword`() {
        val input = "Uber cab to airport 850 rupees"
        val parsed = VoiceExpenseParser.parse(input)

        assertNotNull(parsed)
        assertEquals(850.0, parsed!!.amount, 0.001)
        assertEquals("Transportation", parsed.category)
    }

    @Test
    fun `parse extracts entertainment expense with rupee symbol`() {
        val input = "Movie tickets ₹450"
        val parsed = VoiceExpenseParser.parse(input)

        assertNotNull(parsed)
        assertEquals(450.0, parsed!!.amount, 0.001)
        assertEquals("Entertainment", parsed.category)
    }

    @Test
    fun `parse returns null when no numeric amount is present`() {
        val input = "Had lunch at a restaurant"
        val parsed = VoiceExpenseParser.parse(input)

        assertNull(parsed)
    }

    @Test
    fun `parse returns null on blank or empty input`() {
        assertNull(VoiceExpenseParser.parse(""))
        assertNull(VoiceExpenseParser.parse("   "))
    }
}
