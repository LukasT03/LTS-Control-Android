package com.lts.control

import com.lts.control.ui.screens.about.ChangelogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ChangelogParserTest {

    @Test
    fun testStandardChangelogParsing() {
        val markdown = """
            # LTS Respooler Firmware Changelog
            
            Some intro text here.
            
            ### v1.2.1
            - Fixed servo positioning bug
            * Added high speed mode guard
            • Improved temperature sensor reading
            
            ### V1.2.0
            – Added board variant reporting
            — Added servo calibration endpoints
            
            ### 1.1.0
            - Initial release with basic motor controls
        """.trimIndent()

        val entries = ChangelogParser.parse(markdown)

        assertEquals(3, entries.size)

        // Entry 1: 1.2.1
        assertEquals("1.2.1", entries[0].version)
        assertEquals(3, entries[0].items.size)
        assertEquals("Fixed servo positioning bug", entries[0].items[0])
        assertEquals("Added high speed mode guard", entries[0].items[1])
        assertEquals("Improved temperature sensor reading", entries[0].items[2])

        // Entry 2: 1.2.0 (with – and — dashes)
        assertEquals("1.2.0", entries[1].version)
        assertEquals(2, entries[1].items.size)
        assertEquals("Added board variant reporting", entries[1].items[0])
        assertEquals("Added servo calibration endpoints", entries[1].items[1])

        // Entry 3: 1.1.0
        assertEquals("1.1.0", entries[2].version)
        assertEquals(1, entries[2].items.size)
        assertEquals("Initial release with basic motor controls", entries[2].items[0])
    }

    @Test
    fun testCrlfNormalization() {
        val crlfMarkdown = "### 1.0.1\r\n- Bug fix one\r\n- Bug fix two\r\n"
        val entries = ChangelogParser.parse(crlfMarkdown)

        assertEquals(1, entries.size)
        assertEquals("1.0.1", entries[0].version)
        assertEquals(listOf("Bug fix one", "Bug fix two"), entries[0].items)
    }

    @Test
    fun testEmptyChangelogThrowsIllegalStateException() {
        val emptyMarkdown = "# Only header\n\nNo versions here."
        try {
            ChangelogParser.parse(emptyMarkdown)
            fail("Expected IllegalStateException for empty changelog")
        } catch (_: IllegalStateException) {
            // Expected
        }
    }
}
