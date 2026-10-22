package de.ltsdesign.ltscontrol.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwareChangelogParserTest {
    @Test
    fun parsesVersionHeadingsAndSupportedBullets() {
        val markdown = """
            # Changelog
            - Ignored outside a version

            ### v1.2.1
            - First change
            * Second change

            ### 1.2.0
            • Third change
            – Fourth change
            — Fifth change
        """.trimIndent()

        assertEquals(
            listOf(
                ChangelogEntry("1.2.1", listOf("First change", "Second change")),
                ChangelogEntry("1.2.0", listOf("Third change", "Fourth change", "Fifth change")),
            ),
            FirmwareChangelogParser.parse(markdown),
        )
    }

    @Test
    fun ignoresVersionsWithoutItems() {
        val markdown = """
            ### 2.0.0
            No bullet points here.
            ### 1.0.0
            - Initial release
        """.trimIndent()

        assertEquals(
            listOf(ChangelogEntry("1.0.0", listOf("Initial release"))),
            FirmwareChangelogParser.parse(markdown),
        )
    }
}
