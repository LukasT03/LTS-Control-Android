package com.lts.control

import com.lts.control.core.util.FirmwareVersionUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareVersionUtilsTest {

    @Test
    fun testIsFirmwareVersionAtMost() {
        // Equal
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("1.1.2", 1, 1, 2))
        // Leading 'v' stripped
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("v1.1.2", 1, 1, 2))
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("V1.1.0", 1, 1, 2))

        // Strictly lower
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("1.1.1", 1, 1, 2))
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("1.0.9", 1, 1, 2))
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("0.9.9", 1, 1, 2))
        assertTrue(FirmwareVersionUtils.isFirmwareVersionAtMost("1.1", 1, 1, 2)) // 1.1.0 <= 1.1.2

        // Strictly higher
        assertFalse(FirmwareVersionUtils.isFirmwareVersionAtMost("1.1.3", 1, 1, 2))
        assertFalse(FirmwareVersionUtils.isFirmwareVersionAtMost("1.2.0", 1, 1, 2))
        assertFalse(FirmwareVersionUtils.isFirmwareVersionAtMost("2.0.0", 1, 1, 2))
    }

    @Test
    fun testIsNewerVersion() {
        // Higher patch
        assertTrue(FirmwareVersionUtils.isNewerVersion("1.2.1", "1.2.0"))
        // Higher minor
        assertTrue(FirmwareVersionUtils.isNewerVersion("1.3.0", "1.2.9"))
        // Numeric vs alphabetical (1.10.0 > 1.2.0)
        assertTrue(FirmwareVersionUtils.isNewerVersion("1.10.0", "1.2.0"))
        // Higher major
        assertTrue(FirmwareVersionUtils.isNewerVersion("2.0.0", "1.9.9"))

        // Equal versions
        assertFalse(FirmwareVersionUtils.isNewerVersion("1.2.0", "1.2.0"))
        assertFalse(FirmwareVersionUtils.isNewerVersion("1.0", "1.0.0"))

        // Older versions
        assertFalse(FirmwareVersionUtils.isNewerVersion("1.1.9", "1.2.0"))
        assertFalse(FirmwareVersionUtils.isNewerVersion("1.2.0", "1.10.0"))
        assertFalse(FirmwareVersionUtils.isNewerVersion("0.9.9", "1.0.0"))

        // Different segment lengths
        assertTrue(FirmwareVersionUtils.isNewerVersion("1.2", "1.1.9"))
        assertFalse(FirmwareVersionUtils.isNewerVersion("1.1", "1.1.1"))
    }
}
