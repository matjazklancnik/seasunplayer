package com.example.shazamytdl.download

import java.text.Normalizer
import kotlin.math.ceil

internal object YouTubeCandidateMatcher {
    fun fallbackSearchQuery(trackTitle: String, trackArtist: String, hasDirectSource: Boolean): String =
        if (hasDirectSource) {
            trackTitle.trim()
        } else {
            listOf(trackArtist, trackTitle)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" - ")
        }

    fun matchesTrack(
        trackTitle: String,
        trackArtist: String,
        candidateTitle: String,
        candidateChannel: String
    ): Boolean {
        val candidateWords = words("$candidateTitle $candidateChannel").toSet()
        if (candidateWords.isEmpty()) return false

        val titlePhrases = titlePhraseOptions(trackTitle)
        if (titlePhrases.any { phraseMatches(it, candidateWords) }) return true

        val titleWords = words(trackTitle).filter(::isMeaningfulToken)
        val artistWords = words(trackArtist).filter(::isMeaningfulToken)
        if (titleWords.size == 1 && artistWords.isNotEmpty()) {
            return candidateWords.contains(titleWords.single()) &&
                artistWords.any(candidateWords::contains)
        }

        return false
    }

    private fun titlePhraseOptions(title: String): List<List<String>> {
        val fullWords = words(title)
        if (fullWords.isEmpty()) return emptyList()

        val splitParts = title
            .split(" - ", " – ", " — ", "|", ":")
            .map(::words)
            .filter { it.isNotEmpty() }

        return buildList {
            splitParts.lastOrNull()?.let(::add)
            if (fullWords.size <= 3) {
                add(fullWords)
            } else {
                add(fullWords.takeLast(3))
                add(fullWords.takeLast(4))
                add(fullWords)
            }
        }.distinct()
    }

    private fun phraseMatches(phrase: List<String>, candidateWords: Set<String>): Boolean {
        val meaningful = phrase.filter(::isMeaningfulToken)
        if (meaningful.isEmpty()) return false
        val matched = meaningful.count(candidateWords::contains)
        if (meaningful.size <= 2) return matched == meaningful.size
        return matched >= ceil(meaningful.size * 0.8).toInt()
    }

    private fun words(value: String): List<String> = Normalizer
        .normalize(value, Normalizer.Form.NFKD)
        .lowercase()
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in DECORATION_WORDS }

    private fun isMeaningfulToken(value: String): Boolean = value.length >= 3

    private val DECORATION_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "hd", "hq", "live",
        "remastered", "karaoke", "cover", "original", "full", "version"
    )
}
