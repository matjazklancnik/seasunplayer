package com.example.shazamytdl.download

object DownloadErrorFormatter {
    fun userMessage(error: Throwable): String {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?: error.toString()
        return userMessage(raw)
    }

    fun userMessage(message: String): String {
        val selected = message.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isDebugNoise)
            .lastOrNull(::isUsefulLine)
            ?: message.trim()

        return friendlyMessage(normalizeLine(selected)).limitLength()
    }

    private fun isUsefulLine(line: String): Boolean {
        val lower = line.lowercase()
        return !lower.startsWith("traceback ") &&
            !lower.startsWith("file \"") &&
            !lower.startsWith("raise ") &&
            !lower.startsWith("yt_dlp.") &&
            !lower.startsWith("python ")
    }

    private fun isDebugNoise(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("[debug]") ||
            lower.startsWith("debug:") ||
            lower.contains("command-line config") ||
            (lower.contains("loaded ") && lower.contains("extractors"))
    }

    private fun normalizeLine(line: String): String {
        val errorIndex = line.indexOf("ERROR:", ignoreCase = true)
        val withoutPrefix = if (errorIndex >= 0) {
            line.substring(errorIndex + "ERROR:".length)
        } else {
            line
        }
        return withoutPrefix
            .replace(Regex("^\\[[^]]+]\\s*"), "")
            .replace(Regex("^ERROR:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Prenos ni uspel." }
    }

    private fun friendlyMessage(message: String): String {
        val lower = message.lowercase()
        return when {
            "http error 429" in lower || "too many requests" in lower ->
                "YouTube je začasno omejil prenose za to napravo ali omrežje (HTTP 429). Počakaj nekaj minut ali poskusi prek druge povezave."
            "sign in to confirm" in lower || "not a bot" in lower ->
                "YouTube zahteva preverjanje, da naprava ni bot. Počakaj nekaj minut, poskusi drugo povezavo ali izberi drug zadetek."
            "http error 403" in lower || "forbidden" in lower ->
                "YouTube je zavrnil prenos (HTTP 403). Poskusi znova ali izberi drug YouTube zadetek."
            "requested format is not available" in lower ->
                "Izbrani YouTube zadetek nima primernega zvočnega formata. Poskusi drug zadetek."
            "unable to download webpage" in lower || "timed out" in lower || "timeout" in lower ->
                "Povezava do YouTuba je potekla ali trenutno ni dosegljiva. Poskusi znova."
            else -> message
        }
    }

    private fun String.limitLength(maxLength: Int = 320): String =
        if (length <= maxLength) this else take(maxLength - 1).trimEnd() + "..."
}
