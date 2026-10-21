package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormTokensTest {
    @Test fun checkBoxIsFoundOnOrJustAfterTheTap() {
        val t = "Agree ☐ yes"
        val a = FormTokens.at(t, 6)!!
        assertEquals(FormKind.CHECK, a.kind); assertEquals(6, a.start); assertEquals(7, a.end)
        assertEquals(6, FormTokens.at(t, 7)!!.start)
        assertEquals(true, FormTokens.at("☑", 0)!!.checked)
        assertNull(FormTokens.at(t, 0))
    }

    @Test fun textDateAndChoiceFields() {
        val t = "Name: ${FormTokens.TEXT}\nDate: ${FormTokens.DATE}\nStatus: ${FormTokens.choice(listOf("Open", "Closed"))}"
        val text = FormTokens.at(t, t.indexOf("Type"))!!
        assertEquals(FormKind.TEXT, text.kind)
        assertEquals(FormTokens.TEXT, t.substring(text.start, text.end))
        assertEquals(FormKind.DATE, FormTokens.at(t, t.indexOf("Pick"))!!.kind)
        val c = FormTokens.at(t, t.indexOf("Open"))!!
        assertEquals(FormKind.CHOICE, c.kind)
        assertEquals(listOf("Open", "Closed"), c.options)
    }

    @Test fun ordinaryBracketsAreIgnored() {
        assertNull(FormTokens.at("see [1] and [note] here", 6))
        assertNull(FormTokens.at("plain text", 3))
    }

    @Test fun optionsAreCleaned() {
        assertEquals(listOf("Yes", "No", "Maybe"), FormTokens.parseOptions(" Yes, No;[Maybe], ,Yes"))
    }
}
