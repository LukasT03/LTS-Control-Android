package de.ltsdesign.ltscontrol.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Latest board firmware published on GitHub Pages, cached for offline use. */
class FirmwareUpdateRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("firmware_update", Context.MODE_PRIVATE)

    var latestVersion by mutableStateOf(preferences.getString(CACHE_KEY, null))
        private set

    fun isUpdateAvailable(current: String?): Boolean {
        val latest = latestVersion ?: return false
        current ?: return false
        return FirmwareVersion.compare(latest, current) > 0
    }

    suspend fun refresh() {
        val version = withContext(Dispatchers.IO) {
            runCatching {
                // GitHub Pages caches for several minutes, the query parameter bypasses that.
                val url = URL("$SOURCE_URL?t=${System.currentTimeMillis() / 1000}")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    if (connection.responseCode != 200) return@runCatching null
                    connection.inputStream.bufferedReader().use { it.readText() }.trim().ifEmpty { null }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        } ?: return
        latestVersion = version
        preferences.edit { putString(CACHE_KEY, version) }
    }

    private companion object {
        const val SOURCE_URL = "https://respooler.lts-design.com/Firmware/latest_board_firmware.txt"
        const val CACHE_KEY = "latestBoardFirmwareVersion"
    }
}
