package com.example.shazamytdl.data

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val sourceUrl: String? = null,
    val localPath: String? = null,
    val artworkPath: String? = null,
    val artworkSource: String? = null,
    val status: TrackStatus = TrackStatus.NEW,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TrackStatus {
    NEW,
    URL_SET,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}
