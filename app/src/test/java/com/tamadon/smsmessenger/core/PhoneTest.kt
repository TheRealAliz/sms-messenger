package com.tamadon.smsmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTest {

    @Test
    fun `groups local and international formats`() {
        val expected = "9123456789"
        assertEquals(expected, Phone.threadKey("0912 345 6789"))
        assertEquals(expected, Phone.threadKey("+98 912 345 6789"))
        assertEquals(expected, Phone.threadKey("00989123456789"))
    }

    @Test
    fun `groups Persian digits`() {
        assertEquals("9123456789", Phone.threadKey("۰۹۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `keeps alphanumeric sender ids lowercase`() {
        assertEquals("bankmelli", Phone.threadKey("BankMelli"))
    }

    @Test
    fun `keeps short codes as-is`() {
        assertEquals("110", Phone.threadKey("110"))
    }

    @Test
    fun `alphanumeric senders cannot be replied to`() {
        assertFalse(Phone.canReply("BankMelli"))
        assertTrue(Phone.canReply("09123456789"))
    }

    @Test
    fun `numbers with fewer than three digits cannot be called`() {
        assertFalse(Phone.canCall("12"))
        assertTrue(Phone.canCall("110"))
        assertTrue(Phone.canCall("09123456789"))
    }

    @Test
    fun `converts Persian and Arabic digits to ASCII`() {
        assertEquals("0912", Phone.asciiDigits("۰۹۱۲"))
        assertEquals("0912", Phone.asciiDigits("٠٩١٢"))
    }
}
