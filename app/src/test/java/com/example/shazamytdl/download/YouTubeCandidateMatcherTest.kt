package com.example.shazamytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCandidateMatcherTest {
    @Test
    fun selectedUrlFallbackSearchesByVideoTitleOnly() {
        assertEquals(
            "Tanja Ribič Za vsako rano",
            YouTubeCandidateMatcher.fallbackSearchQuery(
                trackTitle = "Tanja Ribič Za vsako rano",
                trackArtist = "Nakljucni YouTube kanal",
                hasDirectSource = true
            )
        )
    }

    @Test
    fun acceptsSameSongTitleFromFallbackResults() {
        assertTrue(
            YouTubeCandidateMatcher.matchesTrack(
                trackTitle = "Tanja Ribič Za vsako rano",
                trackArtist = "Nakljucni YouTube kanal",
                candidateTitle = "Tanja Ribič - Za vsako rano",
                candidateChannel = "Slovenska glasba"
            )
        )
    }

    @Test
    fun rejectsDifferentSongBySameArtist() {
        assertFalse(
            YouTubeCandidateMatcher.matchesTrack(
                trackTitle = "Tanja Ribič Za vsako rano",
                trackArtist = "Nakljucni YouTube kanal",
                candidateTitle = "Tanja Ribič - Petelinček je na goro šel",
                candidateChannel = "Otroške pesmi"
            )
        )
    }

    @Test
    fun acceptsSongTitleWhenArtistIsOnlyInChannel() {
        assertTrue(
            YouTubeCandidateMatcher.matchesTrack(
                trackTitle = "Za vsako rano",
                trackArtist = "Tanja Ribič",
                candidateTitle = "Za vsako rano",
                candidateChannel = "Tanja Ribic"
            )
        )
    }
}
