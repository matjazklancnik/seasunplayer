package com.example.shazamytdl.youtube

import com.example.shazamytdl.data.Track
import com.example.shazamytdl.data.TrackStatus
import com.example.shazamytdl.util.stableTrackId
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.Locale

data class YouTubePlaylist(
    val id: String,
    val title: String,
    val itemCount: Int,
    val privacyStatus: String
)

class YouTubeApiClient {
    fun listMyPlaylists(accessToken: String): List<YouTubePlaylist> {
        val playlists = mutableListOf<YouTubePlaylist>()
        var pageToken: String? = null

        do {
            val response = get(
                path = "playlists",
                accessToken = accessToken,
                parameters = buildMap {
                    put("part", "snippet,contentDetails,status")
                    put("mine", "true")
                    put("maxResults", "50")
                    pageToken?.let { put("pageToken", it) }
                }
            )

            val items = response.optJSONArray("items")
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val id = item.optString("id")
                    val title = item.optJSONObject("snippet")?.optString("title").orEmpty()
                    if (id.isBlank() || title.isBlank()) continue

                    playlists += YouTubePlaylist(
                        id = id,
                        title = title,
                        itemCount = item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0,
                        privacyStatus = item.optJSONObject("status")
                            ?.optString("privacyStatus")
                            .orEmpty()
                    )
                }
            }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        val slovenianCollator = Collator.getInstance(Locale.forLanguageTag("sl-SI")).apply {
            strength = Collator.PRIMARY
        }
        return playlists.sortedWith(compareBy(slovenianCollator, YouTubePlaylist::title))
    }

    fun listPlaylistTracks(accessToken: String, playlistId: String): List<Track> {
        val tracks = mutableListOf<Track>()
        var pageToken: String? = null

        do {
            val response = get(
                path = "playlistItems",
                accessToken = accessToken,
                parameters = buildMap {
                    put("part", "snippet,contentDetails")
                    put("playlistId", playlistId)
                    put("maxResults", "50")
                    pageToken?.let { put("pageToken", it) }
                }
            )

            val items = response.optJSONArray("items")
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val videoId = snippet.optJSONObject("resourceId")
                        ?.optString("videoId")
                        ?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("contentDetails")
                            ?.optString("videoId")
                            ?.takeIf { it.isNotBlank() }
                        ?: continue
                    val title = snippet.optString("title").takeIf { it.isNotBlank() }
                        ?: videoId
                    val artist = snippet.optString("videoOwnerChannelTitle")
                        .takeIf { it.isNotBlank() }
                        ?: "YouTube"

                    tracks += Track(
                        id = stableTrackId(title, artist),
                        title = title,
                        artist = artist,
                        sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                        status = TrackStatus.URL_SET
                    )
                }
            }
            pageToken = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return tracks
    }

    private fun get(
        path: String,
        accessToken: String,
        parameters: Map<String, String>
    ): JSONObject {
        val query = parameters.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        val connection = URL("$BASE_URL/$path?$query").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()

            if (status !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(body)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull()
                error(apiMessage?.takeIf { it.isNotBlank() } ?: "YouTube API napaka HTTP $status")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val READ_ONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
    }
}
