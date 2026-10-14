package com.docscan.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIntegrityTest {
    @Test fun cleanPhone() {
        assertTrue(DeviceIntegrity.assess("release-keys", false, false, false).isEmpty())
    }

    @Test fun flagsEachSign() {
        assertEquals(4, DeviceIntegrity.assess("test-keys", true, true, true).size)
        assertEquals(1, DeviceIntegrity.assess(null, true, false, false).size)
    }
}
