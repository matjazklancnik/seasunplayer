package com.example.shazamytdl.recognition

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

class SongRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class SongRecognitionResult(
    val title: String,
    val artist: String,
    val youtubeVideoId: String? = null,
    val score: Double? = null
) {
    val searchQuery: String
        get() = "$artist - $title"
}

object SongRecognitionSettings {
    const val DEFAULT_AUDD_API_TOKEN = "test"

    fun endpoint(context: Context): String? = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_ENDPOINT, null)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun auddApiToken(context: Context): String? = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_AUDD_API_TOKEN, null)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun saveEndpoint(context: Context, endpoint: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ENDPOINT, endpoint.trim())
        }
    }

    fun saveAudDApiToken(context: Context, apiToken: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_AUDD_API_TOKEN, apiToken.trim())
        }
    }

    fun normalizedEndpoint(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.trim().orEmpty()
        if (scheme != "https" || host.isBlank()) return null
        return trimmed
    }

    private const val PREFS_NAME = "song_recognition"
    private const val KEY_ENDPOINT = "endpoint_url"
    private const val KEY_AUDD_API_TOKEN = "audd_api_token"
}

object SongRecognitionClient {
    fun recognize(
        sample: File,
        auddApiToken: String = SongRecognitionSettings.DEFAULT_AUDD_API_TOKEN
    ): SongRecognitionResult {
        val token = auddApiToken.trim().ifBlank { SongRecognitionSettings.DEFAULT_AUDD_API_TOKEN }
        val body = postSample(
            endpointUrl = AUDD_ENDPOINT,
            sample = sample,
            fileFieldName = "file",
            textParts = listOf(
                "api_token" to token,
                "return" to "apple_music,spotify"
            )
        )
        return parseResponse(body)
    }

    fun recognize(endpointUrl: String, sample: File): SongRecognitionResult {
        val endpoint = SongRecognitionSettings.normalizedEndpoint(endpointUrl)
            ?: throw SongRecognitionException("Vnesi veljaven HTTPS prepoznavni endpoint.")
        val body = postSample(
            endpointUrl = endpoint,
            sample = sample,
            fileFieldName = "sample",
            textParts = listOf(
                "sample_bytes" to sample.length().toString(),
                "data_type" to "audio"
            )
        )
        return parseResponse(body)
    }

    internal fun parseResponse(body: String): SongRecognitionResult {
        val root = runCatching { JSONObject(body) }.getOrElse { error ->
            throw SongRecognitionException(
                "Prepoznavni streznik ni vrnil veljavnega JSON rezultata.",
                error
            )
        }

        root.optStringOrNull("status")?.lowercase(Locale.US)?.let { status ->
            if (status == "error") {
                val errorBody = root.optJSONObject("error")
                val code = errorBody?.optInt("error_code", -1) ?: -1
                val message = errorBody?.optStringOrNull("error_message")
                    ?: root.optStringOrNull("error")
                    ?: "neznana napaka"
                if (code == 901 || code == 902) {
                    throw SongRecognitionException(
                        "Brezplacna AudD kvota je porabljena. V nastavitvah prepoznave vnesi svoj AudD token."
                    )
                }
                throw SongRecognitionException("Prepoznavanje ni uspelo: $message ($code).")
            }
        }

        root.optJSONObject("status")?.let { status ->
            if (status.has("code")) {
                val code = status.optInt("code", -1)
                if (code != 0) {
                    val message = status.optStringOrNull("msg") ?: "neznana napaka"
                    if (code == 1001) {
                        throw SongRecognitionException("Skladbe ni bilo mogoce prepoznati.")
                    }
                    throw SongRecognitionException("Prepoznavanje ni uspelo: $message ($code).")
                }
            }
        }

        if (root.has("result")) {
            when (val result = root.opt("result")) {
                JSONObject.NULL -> throw SongRecognitionException("Skladbe ni bilo mogoce prepoznati.")
                is JSONObject -> result.toRecognitionResult()?.let { return it }
                is JSONArray -> {
                    if (result.length() == 0) {
                        throw SongRecognitionException("Skladbe ni bilo mogoce prepoznati.")
                    }
                    bestResult(result)?.let { return it }
                }
            }
        }

        root.toRecognitionResult()?.let { return it }

        val metadata = root.optJSONObject("metadata")
        bestResult(metadata?.optJSONArray("music"))?.let { return it }
        bestResult(metadata?.optJSONArray("humming"))?.let { return it }

        throw SongRecognitionException("Prepoznavni rezultat ne vsebuje naslova in izvajalca.")
    }

    private fun postSample(
        endpointUrl: String,
        sample: File,
        fileFieldName: String,
        textParts: List<Pair<String, String>>
    ): String {
        if (!sample.isFile || sample.length() <= 0L) {
            throw SongRecognitionException("Zvocni vzorec ni veljavna datoteka.")
        }

        val boundary = "SunSea-${UUID.randomUUID()}"
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            BufferedOutputStream(connection.outputStream).use { output ->
                textParts.forEach { (name, value) ->
                    output.writeTextPart(boundary, name, value)
                }
                output.writeFilePart(boundary, fileFieldName, sample.name, "audio/mp4", sample)
                output.writeAscii("--$boundary--\r\n")
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (responseCode !in 200..299) {
                throw SongRecognitionException(
                    "Prepoznavni streznik je vrnil HTTP $responseCode: ${body.take(180)}"
                )
            }
            return body
        } catch (error: SongRecognitionException) {
            throw error
        } catch (error: Throwable) {
            throw SongRecognitionException("Prepoznavanje ni uspelo: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun bestResult(array: JSONArray?): SongRecognitionResult? {
        if (array == null || array.length() == 0) return null
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.toRecognitionResult() }
            .maxByOrNull { it.score ?: 0.0 }
    }

    private fun JSONObject.toRecognitionResult(): SongRecognitionResult? {
        val title = optStringOrNull("title")
            ?: optJSONObject("track")?.optStringOrNull("title")
            ?: optJSONObject("track")?.optStringOrNull("name")
        val artist = optStringOrNull("artist")
            ?: optStringOrNull("artist_name")
            ?: firstArtistName(opt("artists"))
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return null

        val youtubeVideoId = optStringOrNull("youtubeVideoId")
            ?: optStringOrNull("youtube_video_id")
            ?: optStringOrNull("videoId")
            ?: optStringOrNull("youtube_vid")
            ?: optJSONObject("external_metadata")
                ?.optJSONObject("youtube")
                ?.optStringOrNull("vid")

        return SongRecognitionResult(
            title = title.trim(),
            artist = artist.trim(),
            youtubeVideoId = youtubeVideoId?.trim()?.takeIf { it.isNotBlank() },
            score = optScore()
        )
    }

    private fun firstArtistName(value: Any?): String? = when (value) {
        is JSONArray -> (0 until value.length())
            .firstNotNullOfOrNull { index ->
                when (val item = value.opt(index)) {
                    is JSONObject -> item.optStringOrNull("name")
                    is String -> item.takeIf { it.isNotBlank() }
                    else -> null
                }
            }
        is JSONObject -> value.optStringOrNull("name")
        is String -> value.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun JSONObject.optScore(): Double? {
        if (!has("score")) return null
        val raw = opt("score") ?: return null
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        optString(name, "").trim().takeIf { it.isNotBlank() }

    private fun BufferedOutputStream.writeTextPart(
        boundary: String,
        name: String,
        value: String
    ) {
        writeAscii("--$boundary\r\n")
        writeAscii("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        write(value.toByteArray(Charsets.UTF_8))
        writeAscii("\r\n")
    }

    private fun BufferedOutputStream.writeFilePart(
        boundary: String,
        name: String,
        filename: String,
        contentType: String,
        file: File
    ) {
        writeAscii("--$boundary\r\n")
        writeAscii(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n"
        )
        writeAscii("Content-Type: $contentType\r\n\r\n")
        file.inputStream().use { input -> input.copyTo(this) }
        writeAscii("\r\n")
    }

    private fun BufferedOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val AUDD_ENDPOINT = "https://api.audd.io/"
}
