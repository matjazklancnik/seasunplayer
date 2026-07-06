package com.example.shazamytdl.download

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.shazamytdl.data.TrackRepository
import com.example.shazamytdl.data.TrackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

sealed interface DownloadQueueEvent {
    val trackId: String

    data class StatusChanged(override val trackId: String) : DownloadQueueEvent
    data class Completed(override val trackId: String, val fileName: String) : DownloadQueueEvent
    data class Failed(override val trackId: String, val message: String) : DownloadQueueEvent
}

object DownloadQueueManager {
    private data class QueueEntry(
        val trackId: String,
        val generation: Long,
        val attempt: Int = 0
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueSignal = Channel<Unit>(Channel.CONFLATED)
    private val queueLock = Any()
    private val regularQueue = ArrayDeque<QueueEntry>()
    private val priorityQueue = ArrayDeque<QueueEntry>()
    private val pendingTrackIds = mutableSetOf<String>()

    @Volatile
    private var activeTrackId: String? = null

    private val _progress = MutableStateFlow<Map<String, String>>(emptyMap())
    val progress = _progress.asStateFlow()

    private val _events = MutableSharedFlow<DownloadQueueEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    @Volatile
    private var initialized = false
    @Volatile
    private var libraryGeneration = 0L
    private lateinit var appContext: Context
    private lateinit var repository: TrackRepository

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        repository = TrackRepository(appContext)
        initialized = true

        scope.launch { consumeQueue() }
        scope.launch {
            repository.list()
                .filter { it.status == TrackStatus.QUEUED || it.status == TrackStatus.DOWNLOADING }
                .forEach { track ->
                    repository.updateStatus(track.id, TrackStatus.QUEUED)
                    enqueueInternal(track.id)
                }
        }
    }

    fun enqueue(trackId: String): Boolean {
        check(initialized) { "DownloadQueueManager is not initialized" }
        val track = repository.getById(trackId) ?: return false
        synchronized(queueLock) {
            if (!pendingTrackIds.add(trackId)) return false
            regularQueue.addLast(QueueEntry(trackId, libraryGeneration))
        }

        ensureDownloadServiceRunning()
        repository.updateStatus(trackId, TrackStatus.QUEUED)
        _progress.update { it + (trackId to "V čakalni vrsti") }
        _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))
        queueSignal.trySend(Unit).getOrThrow()
        return true
    }

    fun promote(trackId: String): Boolean {
        check(initialized) { "DownloadQueueManager is not initialized" }
        val promoted = synchronized(queueLock) {
            if (activeTrackId == trackId) return false
            val entry = priorityQueue.firstOrNull { it.trackId == trackId }
                ?: regularQueue.firstOrNull { it.trackId == trackId }
                ?: return false
            priorityQueue.remove(entry)
            regularQueue.remove(entry)
            priorityQueue.addFirst(entry)
            true
        }
        if (promoted) {
            _progress.update { it + (trackId to "Prednostno: naslednja za prenos") }
            _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))
            queueSignal.trySend(Unit)
        }
        return promoted
    }

    fun removeQueued(trackId: String): Boolean {
        check(initialized) { "DownloadQueueManager is not initialized" }
        val canRemove = synchronized(queueLock) {
            if (activeTrackId == trackId) return false
            val entry = priorityQueue.firstOrNull { it.trackId == trackId }
                ?: regularQueue.firstOrNull { it.trackId == trackId }
            if (entry != null) {
                priorityQueue.remove(entry)
                regularQueue.remove(entry)
            }
            pendingTrackIds.remove(trackId)
            true
        }
        if (canRemove) _progress.update { it - trackId }
        stopDownloadServiceIfIdle()
        return canRemove
    }

    private fun enqueueInternal(trackId: String) {
        synchronized(queueLock) {
            if (!pendingTrackIds.add(trackId)) return
            regularQueue.addLast(QueueEntry(trackId, libraryGeneration))
        }
        ensureDownloadServiceRunning()
        _progress.update { it + (trackId to "V čakalni vrsti") }
        _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))
        queueSignal.trySend(Unit).getOrThrow()
    }

    fun invalidateForLibraryClear() {
        synchronized(queueLock) {
            libraryGeneration++
            regularQueue.clear()
            priorityQueue.clear()
            pendingTrackIds.clear()
        }
        _progress.value = emptyMap()
        stopDownloadServiceIfIdle()
    }

    private suspend fun consumeQueue() {
        for (ignored in queueSignal) {
            while (true) {
                val entry = synchronized(queueLock) {
                    (priorityQueue.pollFirst() ?: regularQueue.pollFirst())
                        ?.also { activeTrackId = it.trackId }
                } ?: break
                processTrack(entry)
                synchronized(queueLock) {
                    if (activeTrackId == entry.trackId) activeTrackId = null
                }
            }
            stopDownloadServiceIfIdle()
        }
    }

    private fun ensureDownloadServiceRunning() {
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, DownloadService::class.java)
            )
        }.onFailure { error ->
            Log.e("DownloadQueue", "Could not start download foreground service", error)
        }
    }

    private fun stopDownloadServiceIfIdle() {
        val isIdle = synchronized(queueLock) {
            activeTrackId == null && regularQueue.isEmpty() &&
                priorityQueue.isEmpty() && pendingTrackIds.isEmpty()
        }
        if (isIdle) {
            appContext.stopService(Intent(appContext, DownloadService::class.java))
        }
    }

    private fun processTrack(entry: QueueEntry) {
        val trackId = entry.trackId
        var outDir: File? = null
        var retryQueued = false
        try {
            if (entry.generation != libraryGeneration) return
            val track = repository.getById(trackId) ?: return
            val originalUrl = track.sourceUrl
            val downloadUrl = if (!originalUrl.isNullOrBlank() &&
                (originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be"))
            ) {
                originalUrl
            } else {
                "ytsearch1:${track.artist} - ${track.title}"
            }

            repository.updateStatus(trackId, TrackStatus.DOWNLOADING)
            _progress.update { it + (trackId to "Priprava prenosa...") }
            _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))

            val trackOutputDir = File(File(appContext.filesDir, "music"), track.id)
            outDir = trackOutputDir
            val downloaded = YoutubeDlBridge.downloadAllowedUrl(
                context = appContext,
                url = downloadUrl,
                outputDir = trackOutputDir,
                subPath = "audio"
            ) { progress, eta ->
                if (entry.generation == libraryGeneration) {
                    _progress.update {
                        it + (trackId to "Prenašanje: ${progress.toInt()}% · ETA ${eta}s")
                    }
                }
            }

            if (entry.generation != libraryGeneration || repository.getById(trackId) == null) {
                trackOutputDir.deleteRecursively()
                return
            }
            repository.updateSourceUrl(trackId, downloaded.sourceUrl)
            repository.updateDownloaded(trackId, downloaded.file.absolutePath)
            trackOutputDir.listFiles()
                ?.filter {
                    it.isFile && it.name.startsWith("audio.") &&
                        it.absolutePath != downloaded.file.absolutePath
                }
                ?.forEach { staleFile ->
                    if (!staleFile.delete()) {
                        Log.w("DownloadQueue", "Could not delete stale file ${staleFile.absolutePath}")
                    }
                }
            _progress.update { it + (trackId to "Pridobivanje naslovnice...") }
            val artwork = ArtworkResolver.resolveAndDownload(
                artist = track.artist,
                title = track.title,
                youtubeThumbnailUrl = downloaded.thumbnailUrl,
                outputDir = trackOutputDir
            )
            if (artwork != null) {
                repository.updateArtwork(
                    id = trackId,
                    artworkPath = artwork.file.absolutePath,
                    artworkSource = artwork.source
                )
            }
            _events.tryEmit(DownloadQueueEvent.Completed(trackId, downloaded.file.name))
        } catch (error: Throwable) {
            if (entry.generation != libraryGeneration) {
                outDir?.deleteRecursively()
                return
            }
            Log.e("DownloadQueue", "Download failed for track $trackId", error)
            val errorMessage = error.message ?: error.toString()
            retryQueued = entry.attempt == 0 && repository.getById(trackId) != null &&
                synchronized(queueLock) {
                    if (entry.generation != libraryGeneration || trackId !in pendingTrackIds) {
                        false
                    } else {
                        regularQueue.addLast(entry.copy(attempt = 1))
                        true
                    }
                }
            if (retryQueued) {
                repository.updateStatus(trackId, TrackStatus.QUEUED)
                _progress.update {
                    it + (trackId to "Prvi poskus ni uspel · ponovitev na koncu vrste")
                }
                _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))
                queueSignal.trySend(Unit)
            } else if (repository.getById(trackId) != null) {
                repository.updateStatus(trackId, TrackStatus.ERROR, errorMessage)
                _events.tryEmit(DownloadQueueEvent.Failed(trackId, errorMessage))
            }
        } finally {
            if (!retryQueued) {
                synchronized(queueLock) { pendingTrackIds.remove(trackId) }
            }
            if (!retryQueued && entry.generation == libraryGeneration) {
                _progress.update { it - trackId }
            }
        }
    }
}
