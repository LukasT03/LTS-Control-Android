package de.ltsdesign.ltscontrol.data

import android.content.Context
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChangelogEntry(
    val version: String,
    val items: List<String>,
)

object FirmwareChangelogParser {
    fun parse(text: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var version: String? = null
        var items = mutableListOf<String>()

        fun commit() {
            val currentVersion = version
            if (!currentVersion.isNullOrEmpty() && items.isNotEmpty()) {
                entries += ChangelogEntry(currentVersion, items.toList())
            }
            version = null
            items = mutableListOf()
        }

        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .forEach { raw ->
                val line = raw.trim()
                if (line.startsWith("### ")) {
                    commit()
                    version = line.removePrefix("### ")
                        .trim()
                        .removePrefix("v")
                        .removePrefix("V")
                        .trim()
                    return@forEach
                }

                extractBullet(line)?.let { bullet ->
                    if (version != null) items += bullet
                }
            }

        commit()
        return entries
    }

    private fun extractBullet(line: String): String? {
        val marker = line.firstOrNull() ?: return null
        if (marker !in setOf('-', '*', '\u2022', '\u2013', '\u2014')) return null
        return line.drop(1).trim().ifEmpty { null }
    }
}

class FirmwareChangelogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "firmware_changelog",
        Context.MODE_PRIVATE,
    )

    fun loadCached(): List<ChangelogEntry> {
        val cachedMarkdown = preferences.getString(CACHE_KEY, null) ?: return emptyList()
        return FirmwareChangelogParser.parse(cachedMarkdown)
    }

    suspend fun refresh(): Result<List<ChangelogEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(SOURCE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "text/plain, text/markdown, */*")
            }

            try {
                val status = connection.responseCode
                check(status in 200..299) { "Server returned HTTP $status." }
                val markdown = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val entries = FirmwareChangelogParser.parse(markdown)
                check(entries.isNotEmpty()) { "No data found." }
                preferences.edit { putString(CACHE_KEY, markdown) }
                entries
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val SOURCE_URL = "https://respooler.lts-design.com/Firmware/README.md"
        const val CACHE_KEY = "FirmwareChangelog.cache.v1"
    }
}
