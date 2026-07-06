package com.example.shazamytdl.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.Collator
import java.util.Locale

class TrackDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tracks (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                source_url TEXT,
                local_path TEXT,
                artwork_path TEXT,
                artwork_source TEXT,
                status TEXT NOT NULL,
                last_error TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tracks_artist_title ON tracks(artist, title)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN artwork_path TEXT")
            db.execSQL("ALTER TABLE tracks ADD COLUMN artwork_source TEXT")
        }
    }

    companion object {
        private const val DB_NAME = "tracks.db"
        private const val DB_VERSION = 2
    }
}

class TrackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val helper = TrackDbHelper(appContext)
    private val musicDir = File(appContext.filesDir, "music")

    fun list(): List<Track> {
        val db = helper.readableDatabase
        val cursor = db.query(
            "tracks",
            null,
            null,
            null,
            null,
            null,
            "artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC"
        )
        val tracks = cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(c.toTrack())
            }
        }
        val slovenianCollator = Collator.getInstance(Locale.forLanguageTag("sl-SI")).apply {
            strength = Collator.PRIMARY
        }
        return tracks
            .map(::repairDownloadedPath)
            .sortedWith(
                compareBy(slovenianCollator, Track::title)
                    .thenBy(slovenianCollator, Track::artist)
            )
    }

    fun upsert(track: Track) {
        val db = helper.writableDatabase
        val existing = getById(track.id)
        val merged = if (existing == null) {
            track
        } else {
            existing.copy(
                title = track.title,
                artist = track.artist,
                sourceUrl = track.sourceUrl ?: existing.sourceUrl,
                artworkPath = track.artworkPath ?: existing.artworkPath,
                artworkSource = track.artworkSource ?: existing.artworkSource,
                updatedAt = System.currentTimeMillis()
            )
        }
        db.insertWithOnConflict("tracks", null, merged.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertAll(tracks: List<Track>): ImportResult {
        val uniqueTracks = tracks.distinctBy { it.id }
        val db = helper.writableDatabase
        var added = 0
        var existing = 0

        db.beginTransaction()
        try {
            uniqueTracks.forEach { track ->
                if (getById(track.id) == null) added++ else existing++
                upsert(track)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return ImportResult(
            added = added,
            existing = existing,
            duplicatesInFile = tracks.size - uniqueTracks.size
        )
    }

    fun clearAll() {
        helper.writableDatabase.delete("tracks", null, null)
    }

    fun delete(id: String) {
        helper.writableDatabase.delete("tracks", "id=?", arrayOf(id))
    }

    fun getById(id: String): Track? {
        val db = helper.readableDatabase
        val cursor = db.query("tracks", null, "id=?", arrayOf(id), null, null, null)
        return cursor.use { if (it.moveToFirst()) it.toTrack() else null }
            ?.let(::repairDownloadedPath)
    }

    fun updateSourceUrl(id: String, url: String?) {
        val existing = getById(id)
        val status = if (existing?.localPath?.let(::File)?.isFile == true) {
            TrackStatus.DOWNLOADED
        } else if (url.isNullOrBlank()) {
            TrackStatus.NEW
        } else {
            TrackStatus.URL_SET
        }
        updateFields(
            id,
            ContentValues().apply {
                put("source_url", url?.trim())
                put("status", status.name)
                putNull("last_error")
                put("updated_at", System.currentTimeMillis())
            }
        )
    }

    fun updateStatus(id: String, status: TrackStatus, error: String? = null) {
        updateFields(
            id,
            ContentValues().apply {
                put("status", status.name)
                if (error == null) putNull("last_error") else put("last_error", error)
                put("updated_at", System.currentTimeMillis())
            }
        )
    }

    fun updateDownloaded(id: String, localPath: String) {
        updateFields(
            id,
            ContentValues().apply {
                put("local_path", localPath)
                put("status", TrackStatus.DOWNLOADED.name)
                putNull("last_error")
                put("updated_at", System.currentTimeMillis())
            }
        )
    }

    fun updateArtwork(id: String, artworkPath: String?, artworkSource: String?) {
        updateFields(
            id,
            ContentValues().apply {
                if (artworkPath == null) putNull("artwork_path") else put("artwork_path", artworkPath)
                if (artworkSource == null) putNull("artwork_source") else put("artwork_source", artworkSource)
                put("updated_at", System.currentTimeMillis())
            }
        )
    }

    private fun updateFields(id: String, values: ContentValues) {
        helper.writableDatabase.update("tracks", values, "id=?", arrayOf(id))
    }

    private fun repairDownloadedPath(track: Track): Track {
        val storedFile = track.localPath?.let(::File)
        if (storedFile?.isFile == true) return track

        val actualFile = File(musicDir, track.id)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.maxByOrNull { it.lastModified() }

        if (actualFile != null) {
            updateDownloaded(track.id, actualFile.absolutePath)
            return track.copy(
                localPath = actualFile.absolutePath,
                status = TrackStatus.DOWNLOADED,
                lastError = null,
                updatedAt = System.currentTimeMillis()
            )
        }

        if (storedFile != null || track.status == TrackStatus.DOWNLOADED) {
            val message = "Prenesena zvočna datoteka ni bila najdena."
            updateFields(
                track.id,
                ContentValues().apply {
                    putNull("local_path")
                    put("status", TrackStatus.ERROR.name)
                    put("last_error", message)
                    put("updated_at", System.currentTimeMillis())
                }
            )
            return track.copy(
                localPath = null,
                status = TrackStatus.ERROR,
                lastError = message,
                updatedAt = System.currentTimeMillis()
            )
        }

        return track
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf(
            "aac", "flac", "m4a", "mp3", "ogg", "opus", "vorbis", "wav", "webm"
        )
    }
}

data class ImportResult(
    val added: Int,
    val existing: Int,
    val duplicatesInFile: Int
)

private fun Track.toValues() = ContentValues().apply {
    put("id", id)
    put("title", title)
    put("artist", artist)
    put("source_url", sourceUrl)
    put("local_path", localPath)
    put("artwork_path", artworkPath)
    put("artwork_source", artworkSource)
    put("status", status.name)
    put("last_error", lastError)
    put("updated_at", updatedAt)
}

private fun Cursor.toTrack(): Track {
    fun str(name: String): String? {
        val idx = getColumnIndex(name)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }
    fun nonNullStr(name: String): String = str(name).orEmpty()
    fun long(name: String): Long {
        val idx = getColumnIndex(name)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else 0L
    }

    return Track(
        id = nonNullStr("id"),
        title = nonNullStr("title"),
        artist = nonNullStr("artist"),
        sourceUrl = str("source_url"),
        localPath = str("local_path"),
        artworkPath = str("artwork_path"),
        artworkSource = str("artwork_source"),
        status = runCatching { TrackStatus.valueOf(nonNullStr("status")) }.getOrDefault(TrackStatus.NEW),
        lastError = str("last_error"),
        updatedAt = long("updated_at")
    )
}
