package com.docscan.app.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAllDataTest {
    @Test fun acceptsTheWordInAnyCaseWithSpaces() {
        assertTrue(DeleteAllData.confirmed("DELETE"))
        assertTrue(DeleteAllData.confirmed("  delete "))
    }

    @Test fun rejectsAnythingElse() {
        assertFalse(DeleteAllData.confirmed(""))
        assertFalse(DeleteAllData.confirmed("del"))
        assertFalse(DeleteAllData.confirmed("delete all"))
    }
}
