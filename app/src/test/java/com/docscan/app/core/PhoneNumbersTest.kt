package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    @Test fun plainAndSpaced() {
        assertEquals("08031234567", PhoneNumbers.first("Imam Musa 08031234567"))
        assertEquals("08031234567", PhoneNumbers.first("Tel: 0803 123 4567"))
        assertEquals("+2348031234567", PhoneNumbers.first("call +234 803 123 4567 now"))
        assertEquals("08031234567", PhoneNumbers.first("(0803) 123-4567"))
    }

    @Test fun tapPicksTheNumberUnderTheFinger() {
        val t = "A 08031234567 B 07011112222"
        assertEquals("08031234567", PhoneNumbers.at(t, 4))
        assertEquals("07011112222", PhoneNumbers.at(t, t.length - 2))
        assertNull(PhoneNumbers.at(t, 0))
    }

    @Test fun neighbouringNumbersStaySeparate() {
        val all = PhoneNumbers.all("08031234567 07011112222")
        assertEquals(listOf("08031234567", "07011112222"), all.map { it.number })
    }

    @Test fun shortNumbersAndDatesAreIgnored() {
        assertNull(PhoneNumbers.first("Page 12 of 40, year 2026"))
        assertNull(PhoneNumbers.first("12/10/2026"))
    }
}
