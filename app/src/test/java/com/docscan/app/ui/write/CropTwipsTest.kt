package com.docscan.app.ui.write

import org.junit.Assert.assertEquals
import org.junit.Test

class CropTwipsTest {
    @Test fun cuttingTenPercentOffEachSideLeavesEightyPercent() {
        assertEquals(8000 to 4000, cropTwips(10000, 5000, 0.1f, 0.1f, 0.1f, 0.1f))
    }

    @Test fun noCutKeepsTheSize() {
        assertEquals(3000 to 2000, cropTwips(3000, 2000, 0f, 0f, 0f, 0f))
    }

    @Test fun neverCollapsesToNothing() {
        assertEquals(120 to 120, cropTwips(200, 200, 0.45f, 0.45f, 0.45f, 0.45f))
    }
}
