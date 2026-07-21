package com.example.shazamytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorFormatterTest {
    @Test
    fun stripsVerboseDebugNoise() {
        val message = """
            [debug] Command-line config: ['--verbose']
            [debug] Python 3.11.0
            ERROR: [youtube] abc123: HTTP Error 403: Forbidden
        """.trimIndent()

        val formatted = DownloadErrorFormatter.userMessage(message)

        assertFalse(formatted.contains("[debug]"))
        assertEquals(
            "YouTube je zavrnil prenos (HTTP 403). Poskusi znova ali izberi drug YouTube zadetek.",
            formatted
        )
    }

    @Test
    fun showsRateLimitForBotCheckScreenshotError() {
        val message = """
            WARNING: [youtube] JxPj3GAYYZO: Unable to download webpage: HTTP Error 429: Too Many Requests
            ERROR: [youtube] JxPj3GAYYZO: Sign in to confirm you're not a bot.
        """.trimIndent()

        val formatted = DownloadErrorFormatter.userMessage(message)

        assertTrue(formatted.contains("naprava ni bot"))
        assertFalse(formatted.contains("cookies"))
    }

    @Test
    fun keepsUsefulYtdlpErrorText() {
        val formatted = DownloadErrorFormatter.userMessage(
            "ERROR: [youtube] abc123: Requested format is not available"
        )

        assertTrue(formatted.contains("nima primernega zvočnega formata"))
    }
}
