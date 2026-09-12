package com.lts.control.core.util

object FirmwareVersionUtils {

    /**
     * Ported from iOS BLEManager.isFirmwareVersionAtMost
     * Strips leading 'v', keeps only digits per segment, missing segments are 0,
     * and comparison is <= on patch.
     */
    fun isFirmwareVersionAtMost(fw: String, major: Int, minor: Int, patch: Int): Boolean {
        val cleaned = fw.trim().replace("v", "", ignoreCase = true)
        val parts = cleaned.split(".").map { part ->
            val digits = part.filter { it.isDigit() }
            digits.toIntOrNull() ?: 0
        }

        val parsedMajor = parts.getOrElse(0) { 0 }
        val parsedMinor = parts.getOrElse(1) { 0 }
        val parsedPatch = parts.getOrElse(2) { 0 }

        if (parsedMajor != major) return parsedMajor < major
        if (parsedMinor != minor) return parsedMinor < minor
        return parsedPatch <= patch
    }

    /**
     * Ported from iOS AboutView.isNewerVersion
     * Compares two dot-separated version strings component-wise after zero-padding to equal length.
     * Returns true if latest is strictly newer than current.
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.trim().toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.trim().toIntOrNull() }

        if (latestParts.isEmpty() || currentParts.isEmpty()) return false

        val maxCount = maxOf(latestParts.size, currentParts.size)
        val paddedLatest = latestParts + List(maxCount - latestParts.size) { 0 }
        val paddedCurrent = currentParts + List(maxCount - currentParts.size) { 0 }

        for (i in 0 until maxCount) {
            val l = paddedLatest[i]
            val c = paddedCurrent[i]
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
