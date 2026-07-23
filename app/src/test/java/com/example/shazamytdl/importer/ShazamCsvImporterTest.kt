package com.example.shazamytdl.importer

import com.example.shazamytdl.data.TrackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class ShazamCsvImporterTest {
    @Test
    fun importsSemicolonSeparatedArtisPesemFormat() {
        val tracks = parseCsvTracks(
            """
                artis;pesem
                Massive Attack;Teardrop
                "Queens of the Stone Age";"No One Knows"
            """.trimIndent().reader()
        )

        assertEquals(2, tracks.size)
        assertEquals("Massive Attack", tracks[0].artist)
        assertEquals("Teardrop", tracks[0].title)
        assertEquals(TrackStatus.NEW, tracks[0].status)
        assertNull(tracks[0].sourceUrl)
        assertEquals("Queens of the Stone Age", tracks[1].artist)
        assertEquals("No One Knows", tracks[1].title)
    }

    @Test
    fun importsHeaderlessSemicolonArtistSongRows() {
        val tracks = parseCsvTracks(
            """
                Portishead;Glory Box
                Air;All I Need
            """.trimIndent().reader()
        )

        assertEquals(2, tracks.size)
        assertEquals("Portishead", tracks[0].artist)
        assertEquals("Glory Box", tracks[0].title)
        assertEquals("Air", tracks[1].artist)
        assertEquals("All I Need", tracks[1].title)
    }

    @Test
    fun keepsCommaSeparatedTitleArtistUrlImport() {
        val tracks = parseCsvTracks(
            """
                Track Title,Artist,YouTube URL
                One More Time,Daft Punk,https://www.youtube.com/watch?v=FGBhQbmPwH8
            """.trimIndent().reader()
        )

        assertEquals(1, tracks.size)
        assertEquals("Daft Punk", tracks[0].artist)
        assertEquals("One More Time", tracks[0].title)
        assertEquals("https://www.youtube.com/watch?v=FGBhQbmPwH8", tracks[0].sourceUrl)
        assertEquals(TrackStatus.URL_SET, tracks[0].status)
    }

    private fun String.reader(): BufferedReader = BufferedReader(StringReader(this))
}
