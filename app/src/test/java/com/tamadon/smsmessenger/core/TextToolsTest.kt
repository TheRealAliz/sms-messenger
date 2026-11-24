package com.tamadon.smsmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextToolsTest {

    @Test
    fun `finds a code after a Persian keyword`() {
        assertEquals("482913", TextTools.otp("کد ورود شما: ۴۸۲۹۱۳"))
    }

    @Test
    fun `finds a code after an English keyword`() {
        assertEquals("7310", TextTools.otp("Your login code is 7310. Do not share it."))
    }

    @Test
    fun `keyword code wins over an earlier number`() {
        assertEquals("654321", TextTools.otp("سفارش 12345 آماده است. کد تحویل: 654321"))
    }

    @Test
    fun `accepts a standalone short code`() {
        assertEquals("55221", TextTools.otp("55221"))
    }

    @Test
    fun `ignores dates`() {
        assertNull(TextTools.otp("تاریخ 1405/08/12"))
        assertNull(TextTools.otp("2026-11-24"))
    }

    @Test
    fun `ignores USSD and MMI tokens`() {
        assertNull(TextTools.otp("*1234*"))
        assertNull(TextTools.otp("#1234#"))
    }

    @Test
    fun `returns null when there is no number`() {
        assertNull(TextTools.otp("سلام، حال شما چطور است؟"))
    }

    @Test
    fun `extracts links and strips trailing punctuation`() {
        assertEquals(
            listOf("https://example.com/a?b=1"),
            TextTools.links("See https://example.com/a?b=1.")
        )
    }

    @Test
    fun `adds https to www links`() {
        assertEquals(listOf("https://www.example.org"), TextTools.links("www.example.org"))
    }

    @Test
    fun `strips Persian punctuation`() {
        assertEquals(listOf("https://example.com/x"), TextTools.links("لینک: https://example.com/x،"))
    }

    @Test
    fun `does not treat ordinary words as links`() {
        assertEquals(emptyList<String>(), TextTools.links("example.com is not a link here"))
    }

    @Test
    fun `deduplicates repeated links`() {
        assertEquals(
            listOf("https://example.com"),
            TextTools.links("https://example.com and https://example.com")
        )
    }
}
