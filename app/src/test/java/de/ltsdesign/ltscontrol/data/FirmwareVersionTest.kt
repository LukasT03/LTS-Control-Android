package de.ltsdesign.ltscontrol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareVersionTest {
    @Test
    fun comparesNumerically() {
        assertTrue(FirmwareVersion.compare("1.10", "1.9") > 0)
        assertTrue(FirmwareVersion.compare("1.3.2", "1.3.3") < 0)
    }

    @Test
    fun ignoresPrefixAndMissingParts() {
        assertEquals(0, FirmwareVersion.compare("v1.3", "1.3.0"))
    }
}
