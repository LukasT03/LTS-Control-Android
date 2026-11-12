package de.ltsdesign.ltscontrol.data

object FirmwareVersion {
    /**
     * Compares dotted versions numerically, so "1.10" is newer than "1.9".
     * Non-digits (e.g. a leading "v") are ignored and missing parts count as 0.
     */
    fun compare(a: String, b: String): Int {
        val lhs = parts(a)
        val rhs = parts(b)
        for (i in 0 until maxOf(lhs.size, rhs.size)) {
            val l = lhs.getOrElse(i) { 0 }
            val r = rhs.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        version.split('.').map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
