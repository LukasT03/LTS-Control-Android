package com.lts.control.ui.screens.about

import kotlinx.serialization.Serializable

@Serializable
data class ChangelogEntry(
    val version: String,
    val items: List<String>
)

object ChangelogParser {
    private val BULLET_MARKERS = charArrayOf('-', '*', '•', '–', '—')

    fun parse(text: String): List<ChangelogEntry> {
        val lines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")

        val entries = mutableListOf<ChangelogEntry>()
        var currentVersion: String? = null
        val currentItems = mutableListOf<String>()

        fun commit() {
            val v = currentVersion
            if (!v.isNullOrBlank() && currentItems.isNotEmpty()) {
                entries.add(ChangelogEntry(v, currentItems.toList()))
            }
            currentVersion = null
            currentItems.clear()
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("### ")) {
                commit()
                var version = line.substring(4).trim()
                if (version.startsWith("v", ignoreCase = true)) {
                    version = version.substring(1).trim()
                }
                currentVersion = version
                continue
            }

            if (currentVersion != null && line.isNotEmpty()) {
                val firstChar = line.first()
                if (BULLET_MARKERS.contains(firstChar)) {
                    val bulletText = line.substring(1).trim()
                    if (bulletText.isNotEmpty()) {
                        currentItems.add(bulletText)
                    }
                }
            }
        }
        commit()

        if (entries.isEmpty()) {
            throw IllegalStateException("No changelog entries found.")
        }

        return entries
    }
}
