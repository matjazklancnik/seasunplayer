package com.example.shazamytdl.importer

import android.content.Context
import android.net.Uri
import com.example.shazamytdl.data.Track
import com.example.shazamytdl.data.TrackStatus
import com.example.shazamytdl.util.stableTrackId
import java.io.BufferedReader
import java.io.InputStreamReader

class ShazamCsvImporter(private val context: Context) {
    fun import(uri: Uri): List<Track> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open selected CSV")

        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            parseCsvTracks(reader)
        }
    }
}

internal fun parseCsvTracks(reader: BufferedReader): List<Track> {
    val allLines = reader.lineSequence().filter { it.isNotBlank() }.toList()
    if (allLines.isEmpty()) return emptyList()

    val headerRowIndex = allLines.indexOfFirst { line ->
        val columns = parseCsvLine(line, detectDelimiter(line)).map {
            it.removePrefix("\uFEFF").trim().lowercase()
        }
        columns.any { column ->
            column in setOf(
                "title", "track title", "song title", "song", "name", "video title",
                "track", "pesem", "skladba", "naslov",
                "artist", "artis", "izvajalec", "subtitle", "performer", "author", "avtor",
                "video id", "video_id", "youtube video id"
            )
        }
    }

    if (headerRowIndex == -1) return parseHeaderlessArtistTitleRows(allLines)

    val delimiter = detectDelimiter(allLines[headerRowIndex])
    val rows = allLines.drop(headerRowIndex).map { parseCsvLine(it, delimiter) }
    val header = rows.first().map { it.removePrefix("\uFEFF").trim().lowercase() }
    val dataRows = rows.drop(1)

    val titleIndex = header.indexByExact(
        "track title", "video title", "song title", "title", "track", "song", "name",
        "pesem", "skladba", "naslov"
    )
    val artistIndex = header.indexByExact(
        "artist", "artis", "izvajalec", "subtitle", "performer", "author", "avtor",
        "channel title", "channel", "uploader"
    )
    val urlIndex = header.indexByAny("url", "link", "shazam url", "track url", "source url", "youtube url")
    val videoIdIndex = header.indexByExact("video id", "video_id", "youtube video id")

    val imported = dataRows.mapNotNull { row ->
        val videoId = row.getOrNull(videoIdIndex)
            ?.trim()
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,}")) }
        val explicitUrl = row.getOrNull(urlIndex)?.trim()?.takeIf { it.startsWith("http") }
        val url = explicitUrl ?: videoId?.let { "https://www.youtube.com/watch?v=$it" }
        val rawTitle = row.getOrNull(titleIndex)?.trim().orEmpty().ifBlank { videoId.orEmpty() }
        val (title, artist) = if (artistIndex == -1) {
            splitCombinedTitle(rawTitle)
        } else {
            rawTitle to row.getOrNull(artistIndex)?.trim().orEmpty().ifBlank {
                if (videoId != null) "YouTube" else ""
            }
        }

        if (title.isBlank() || artist.isBlank()) return@mapNotNull null

        Track(
            id = stableTrackId(title, artist),
            title = title,
            artist = artist,
            sourceUrl = url,
            status = if (url == null) TrackStatus.NEW else TrackStatus.URL_SET
        )
    }

    return dedupeTracks(imported)
}

private fun parseHeaderlessArtistTitleRows(allLines: List<String>): List<Track> {
    if (allLines.none { countDelimiterOutsideQuotes(it, ';') > 0 }) return emptyList()

    val imported = allLines.mapNotNull { line ->
        val row = parseCsvLine(line, ';')
        val artist = row.getOrNull(0)?.trim().orEmpty()
        val title = row.getOrNull(1)?.trim().orEmpty()
        if (artist.isBlank() || title.isBlank()) return@mapNotNull null

        Track(
            id = stableTrackId(title, artist),
            title = title,
            artist = artist,
            status = TrackStatus.NEW
        )
    }

    return dedupeTracks(imported)
}

private fun dedupeTracks(imported: List<Track>): List<Track> = imported
    .groupBy { it.id }
    .values
    .map { duplicates -> duplicates.firstOrNull { it.sourceUrl != null } ?: duplicates.first() }

private fun splitCombinedTitle(value: String): Pair<String, String> {
    val separatorIndex = value.indexOf(" - ")
    if (separatorIndex <= 0 || separatorIndex >= value.length - 3) {
        return value to "YouTube"
    }

    val artist = value.substring(0, separatorIndex).trim()
    val title = value.substring(separatorIndex + 3).trim()
    return title to artist
}

private fun List<String>.indexByAny(vararg names: String): Int {
    names.forEach { wanted ->
        val exact = indexOf(wanted)
        if (exact >= 0) return exact
    }
    names.forEach { wanted ->
        val fuzzy = indexOfFirst { it.contains(wanted) }
        if (fuzzy >= 0) return fuzzy
    }
    return -1
}

private fun List<String>.indexByExact(vararg names: String): Int {
    names.forEach { wanted ->
        val exact = indexOf(wanted)
        if (exact >= 0) return exact
    }
    return -1
}

private fun detectDelimiter(line: String): Char {
    val semicolons = countDelimiterOutsideQuotes(line, ';')
    val commas = countDelimiterOutsideQuotes(line, ',')
    return if (semicolons > commas) ';' else ','
}

private fun countDelimiterOutsideQuotes(line: String, delimiter: Char): Int {
    var count = 0
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val ch = line[i]
        if (ch == '"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (ch == delimiter && !inQuotes) {
            count++
        }
        i++
    }

    return count
}

private fun parseCsvLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val ch = line[i]
        if (ch == '"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                current.append('"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (ch == delimiter && !inQuotes) {
            result.add(current.toString().trim())
            current.setLength(0)
        } else {
            current.append(ch)
        }
        i++
    }
    result.add(current.toString().trim())
    return result
}
