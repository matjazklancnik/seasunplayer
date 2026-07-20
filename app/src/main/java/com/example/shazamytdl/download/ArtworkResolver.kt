package com.example.shazamytdl.download

import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer

data class ResolvedArtwork(
    val file: File,
    val source: String
)

object ArtworkResolver {
    fun resolveAndDownload(
        artist: String,
        title: String,
        youtubeThumbnailUrl: String?,
        outputDir: File,
        cancelToken: DownloadCancelToken? = null
    ): ResolvedArtwork? {
        cancelToken?.throwIfCancelled()
        val musicBrainzUrl = runCatching { findMusicBrainzArtwork(artist, title, cancelToken) }
            .onFailure { Log.w(TAG, "MusicBrainz artwork lookup failed", it) }
            .getOrNull()

        val candidates = buildList {
            musicBrainzUrl?.let { add("musicbrainz" to it) }
            youtubeThumbnailUrl?.takeIf { it.startsWith("https://") }
                ?.let { add("youtube" to it) }
        }

        for ((source, url) in candidates) {
            cancelToken?.throwIfCancelled()
            val file = runCatching { downloadImage(url, outputDir, cancelToken) }
                .onFailure { Log.w(TAG, "Could not download $source artwork", it) }
                .getOrNull()
            if (file != null) return ResolvedArtwork(file, source)
        }
        return null
    }

    private fun findMusicBrainzArtwork(
        artist: String,
        title: String,
        cancelToken: DownloadCancelToken?
    ): String? {
        val query = "recording:\"$title\" AND artist:\"$artist\""
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val response = getJson(
            "https://musicbrainz.org/ws/2/recording/?query=$encoded&fmt=json&limit=5",
            cancelToken
        )
        val recordings = response.optJSONArray("recordings") ?: return null
        val wantedTitle = normalized(title)
        val wantedArtist = normalized(artist)

        for (index in 0 until recordings.length()) {
            cancelToken?.throwIfCancelled()
            val recording = recordings.optJSONObject(index) ?: continue
            if (recording.optInt("score", 0) < 95) continue
            if (normalized(recording.optString("title")) != wantedTitle) continue

            val credits = recording.optJSONArray("artist-credit")
            val creditedArtist = buildString {
                if (credits != null) {
                    for (creditIndex in 0 until credits.length()) {
                        val credit = credits.optJSONObject(creditIndex) ?: continue
                        append(credit.optString("name"))
                    }
                }
            }
            if (normalized(creditedArtist) != wantedArtist) continue

            val releases = recording.optJSONArray("releases") ?: continue
            var fallbackGroupId: String? = null
            for (releaseIndex in 0 until releases.length()) {
                val release = releases.optJSONObject(releaseIndex) ?: continue
                val group = release.optJSONObject("release-group") ?: continue
                val groupId = group.optString("id").takeIf { it.isNotBlank() } ?: continue
                if (fallbackGroupId == null) fallbackGroupId = groupId

                val primaryType = group.optString("primary-type")
                val secondaryTypes = group.optJSONArray("secondary-types")
                val isCompilation = secondaryTypes?.let { types ->
                    (0 until types.length()).any {
                        types.optString(it).equals("Compilation", ignoreCase = true)
                    }
                } == true
                if (release.optString("status") == "Official" &&
                    primaryType in setOf("Album", "Single", "EP") &&
                    !isCompilation
                ) {
                    findCoverArtUrl(groupId, cancelToken)?.let { return it }
                }
            }
            fallbackGroupId?.let { findCoverArtUrl(it, cancelToken) }?.let { return it }
        }
        return null
    }

    private fun findCoverArtUrl(
        releaseGroupId: String,
        cancelToken: DownloadCancelToken?
    ): String? {
        cancelToken?.throwIfCancelled()
        val response = runCatching {
            getJson("https://coverartarchive.org/release-group/$releaseGroupId", cancelToken)
        }.getOrNull() ?: return null
        val images = response.optJSONArray("images") ?: return null
        val image = (0 until images.length())
            .mapNotNull(images::optJSONObject)
            .firstOrNull { it.optBoolean("front") && it.optBoolean("approved", true) }
            ?: images.optJSONObject(0)
            ?: return null
        val thumbnails = image.optJSONObject("thumbnails")
        return thumbnails?.optString("500")?.takeIf { it.isNotBlank() }
            ?: thumbnails?.optString("250")?.takeIf { it.isNotBlank() }
            ?: image.optString("image").takeIf { it.isNotBlank() }
    }

    private fun getJson(url: String, cancelToken: DownloadCancelToken?): JSONObject {
        cancelToken?.throwIfCancelled()
        val connection = openConnection(url)
        return try {
            val status = connection.responseCode
            cancelToken?.throwIfCancelled()
            if (status !in 200..299) error("HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            cancelToken?.throwIfCancelled()
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadImage(
        url: String,
        outputDir: File,
        cancelToken: DownloadCancelToken?
    ): File {
        cancelToken?.throwIfCancelled()
        check(outputDir.exists() || outputDir.mkdirs())
        val connection = openConnection(url)
        val temporary = File(outputDir, "cover.part")
        val target = File(outputDir, "cover.img")
        try {
            val status = connection.responseCode
            cancelToken?.throwIfCancelled()
            if (status !in 200..299) error("HTTP $status")
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_IMAGE_BYTES) error("Naslovnica je prevelika")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        cancelToken?.throwIfCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            cancelToken?.throwIfCancelled()
            if (temporary.length() == 0L || temporary.length() > MAX_IMAGE_BYTES) {
                error("Neveljavna velikost naslovnice")
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Datoteka ni veljavna slika")
            cancelToken?.throwIfCancelled()

            if (target.exists() && !target.delete()) error("Stare naslovnice ni mogoče zamenjati")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json,image/*;q=0.9,*/*;q=0.1")
        }

    private fun normalized(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKD)
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private const val TAG = "ArtworkResolver"
    private const val USER_AGENT = "SunSeaPlayer/1.0 (Android music library)"
    private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
}
