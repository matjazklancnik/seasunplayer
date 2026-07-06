package com.example.shazamytdl.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test
    fun stableTrackId_ignoresCaseAndRepeatedWhitespace() {
        assertEquals(
            stableTrackId("  My   Song ", "The ARTIST"),
            stableTrackId("my song", "the artist")
        )
    }

    @Test
    fun stableTrackId_normalizesUnicodeCompatibilityCharacters() {
        assertEquals(
            stableTrackId("Ｓｏｎｇ", "Artist"),
            stableTrackId("Song", "Artist")
        )
    }

    @Test
    fun stableTrackId_keepsArtistAndTitleRolesDistinct() {
        assertNotEquals(
            stableTrackId("Song", "Artist"),
            stableTrackId("Artist", "Song")
        )
    }

    @Test
    fun stableTrackId_isStableHexIdentifier() {
        val id = stableTrackId("Song", "Artist")
        assertEquals(id, stableTrackId("Song", "Artist"))
        assertEquals(32, id.length)
        assertTrue(id.matches(Regex("[0-9a-f]{32}")))
    }
}
