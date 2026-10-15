package com.docscan.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpdateInfoTest {
    private val sha = "a".repeat(64)
    private fun json(url: String = "https://github.com/x/y/releases/latest/download/Docxels.apk", sha256: String = sha, code: Int = 20) =
        """{"versionCode":$code,"versionName":"0.2.$code","sha256":"$sha256","size":1000,"apkUrl":"$url","notes":"Fixes"}"""

    @Test fun parsesValid() {
        val i = UpdateInfo.parse(json())!!
        assertEquals(20L, i.versionCode)
        assertTrue(i.isNewerThan(19))
        assertFalse(i.isNewerThan(20))
    }

    @Test fun whatsNewListsOnlyNewerLines() {
        val i = UpdateInfo.parse(json().replace("\"Fixes\"", "\"5|old thing\\n6|new thing\\n7|newer thing\\nplain\""))!!
        assertEquals(listOf("new thing", "newer thing", "plain"), i.whatsNew(5))
        assertEquals(listOf("plain"), i.whatsNew(7))
    }

    @Test fun refusesUnsafe() {
        assertNull(UpdateInfo.parse(json(url = "http://github.com/a.apk")))
        assertNull(UpdateInfo.parse(json(url = "https://evil.example.com/a.apk")))
        assertNull(UpdateInfo.parse(json(url = "https://github.com.evil.com/a.apk")))
        assertNull(UpdateInfo.parse(json(sha256 = "xyz")))
        assertNull(UpdateInfo.parse("not json"))
        assertNull(UpdateInfo.parse("{}"))
    }

    @Test fun hosts() {
        assertTrue(UpdateInfo.hostAllowed("objects.githubusercontent.com"))
        assertTrue(UpdateInfo.hostAllowed("docxels.pixelslimited.ng"))
        assertFalse(UpdateInfo.hostAllowed("githubusercontent.com.evil.io"))
        assertFalse(UpdateInfo.hostAllowed(null))
        assertNotNull(UpdateInfo.parse(json("https://docxels.pixelslimited.ng/Docxels.apk")))
    }
}
