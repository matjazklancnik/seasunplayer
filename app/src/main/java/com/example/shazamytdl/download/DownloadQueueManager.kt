package com.example.shazamytdl.download

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.shazamytdl.data.Track
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
    private data class QueueRemoval(
        val removed: Boolean,
        val wasActive: Boolean
    )

    private data class QueueEntry(
        val trackId: String,
        val generation: Long,
        val attempt: Int = 0
    )

    private data class DownloadCandidate(
        val url: String,
        val label: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueSignal = Channel<Unit>(Channel.CONFLATED)
    private val queueLock = Any()
    private val regularQueue = ArrayDeque<QueueEntry>()
    private val priorityQueue = ArrayDeque<QueueEntry>()
    private val pendingTrackIds = mutableSetOf<String>()

    @Volatile
    private var activeTrackId: String? = null
    @Volatile
    private var activeCancelToken: DownloadCancelToken? = null

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
        removeFromQueue(trackId)
        return true
    }

    fun cancel(trackId: String): Boolean {
        check(initialized) { "DownloadQueueManager is not initialized" }
        val removal = removeFromQueue(trackId)
        if (!removal.removed) return false

        repository.resetDownloadState(trackId)
        if (!removal.wasActive) {
            File(File(appContext.filesDir, "music"), trackId).deleteRecursively()
        }
        _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))
        return true
    }

    private fun removeFromQueue(trackId: String): QueueRemoval {
        var cancelToken: DownloadCancelToken? = null
        var wasActive = false
        val removed = synchronized(queueLock) {
            var removedInQueue = false
            if (activeTrackId == trackId) {
                wasActive = true
                cancelToken = activeCancelToken
                removedInQueue = true
            }

            val entry = priorityQueue.firstOrNull { it.trackId == trackId }
                ?: regularQueue.firstOrNull { it.trackId == trackId }
            if (entry != null) {
                priorityQueue.remove(entry)
                regularQueue.remove(entry)
                removedInQueue = true
            }
            if (pendingTrackIds.remove(trackId)) removedInQueue = true
            removedInQueue
        }

        cancelToken?.cancel()
        if (removed) {
            _progress.update { it - trackId }
            _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))
        }
        stopDownloadServiceIfIdle()
        return QueueRemoval(removed = removed, wasActive = wasActive)
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
                val cancelToken = DownloadCancelToken()
                val entry = synchronized(queueLock) {
                    (priorityQueue.pollFirst() ?: regularQueue.pollFirst())
                        ?.also {
                            activeTrackId = it.trackId
                            activeCancelToken = cancelToken
                        }
                } ?: break
                processTrack(entry, cancelToken)
                synchronized(queueLock) {
                    if (activeTrackId == entry.trackId) {
                        activeTrackId = null
                        activeCancelToken = null
                    }
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

    private fun processTrack(entry: QueueEntry, cancelToken: DownloadCancelToken) {
        val trackId = entry.trackId
        var outDir: File? = null
        var retryQueued = false
        try {
            if (entry.generation != libraryGeneration) return
            cancelToken.throwIfCancelled()
            val track = repository.getById(trackId) ?: return
            repository.updateStatus(trackId, TrackStatus.DOWNLOADING)
            _progress.update { it + (trackId to "Priprava prenosa...") }
            _events.tryEmit(DownloadQueueEvent.StatusChanged(trackId))

            val trackOutputDir = File(File(appContext.filesDir, "music"), track.id)
            outDir = trackOutputDir
            val downloaded = downloadWithQualityFallback(
                track = track,
                outputDir = trackOutputDir,
                entry = entry,
                cancelToken = cancelToken
            )

            cancelToken.throwIfCancelled()
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
            cancelToken.throwIfCancelled()
            _progress.update { it + (trackId to "Pridobivanje naslovnice...") }
            val artwork = ArtworkResolver.resolveAndDownload(
                artist = track.artist,
                title = track.title,
                youtubeThumbnailUrl = downloaded.thumbnailUrl,
                outputDir = trackOutputDir,
                cancelToken = cancelToken
            )
            cancelToken.throwIfCancelled()
            if (entry.generation != libraryGeneration || repository.getById(trackId) == null) {
                trackOutputDir.deleteRecursively()
                return
            }
            if (artwork != null) {
                repository.updateArtwork(
                    id = trackId,
                    artworkPath = artwork.file.absolutePath,
                    artworkSource = artwork.source
                )
            }
            cancelToken.throwIfCancelled()
            _events.tryEmit(DownloadQueueEvent.Completed(trackId, downloaded.file.name))
        } catch (error: Throwable) {
            if (error is DownloadCancelledException ||
                cancelToken.isCancelled ||
                entry.generation != libraryGeneration ||
                repository.getById(trackId) == null
            ) {
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

    private fun downloadWithQualityFallback(
        track: Track,
        outputDir: File,
        entry: QueueEntry,
        cancelToken: DownloadCancelToken
    ): DownloadedMedia {
        val candidates = downloadCandidates(track)
        var lastQuietReport: AudioLoudnessReport? = null

        candidates.forEachIndexed { index, candidate ->
            cancelToken.throwIfCancelled()
            if (outputDir.exists()) {
                check(outputDir.deleteRecursively()) {
                    "Prejšnjega poskusa prenosa ni bilo mogoče počistiti."
                }
            }
            check(outputDir.mkdirs() || outputDir.isDirectory) {
                "Could not create output directory: ${outputDir.absolutePath}"
            }

            _progress.update {
                it + (track.id to "Prenašanje zadetka ${candidate.label}...")
            }
            val downloaded = YoutubeDlBridge.downloadAllowedUrl(
                context = appContext,
                url = candidate.url,
                outputDir = outputDir,
                subPath = "audio",
                cancelToken = cancelToken
            ) { progress, eta ->
                if (entry.generation == libraryGeneration && !cancelToken.isCancelled) {
                    _progress.update {
                        it + (track.id to "Zadetek ${candidate.label}: ${progress.toInt()}% · ETA ${eta}s")
                    }
                }
            }

            cancelToken.throwIfCancelled()
            val loudness = AudioQualityAnalyzer.analyze(downloaded.file, cancelToken)
            if (loudness != null) {
                Log.d(
                    "DownloadQueue",
                    "Audio loudness for ${downloaded.sourceUrl}: " +
                        "rms=${loudness.rmsDb}, active=${loudness.activeRmsDb}, peak=${loudness.peakDb}"
                )
            }
            if (loudness != null &&
                AudioQualityAnalyzer.isTooQuiet(loudness) &&
                index < candidates.lastIndex
            ) {
                lastQuietReport = loudness
                _progress.update {
                    it + (track.id to "Zadetek ${candidate.label} je pretih · poskušam naslednjega")
                }
                outputDir.deleteRecursively()
                return@forEachIndexed
            }

            if (lastQuietReport != null) {
                _progress.update { it + (track.id to "Najden glasnejši zadetek.") }
            }
            return downloaded
        }

        error("Prenos se je končal brez veljavnega zvočnega kandidata.")
    }

    private fun downloadCandidates(track: Track): List<DownloadCandidate> {
        val originalUrl = track.sourceUrl?.trim()
        if (!originalUrl.isNullOrBlank() && originalUrl.isYouTubeUrl()) {
            return listOf(DownloadCandidate(originalUrl, "1/1"))
        }

        val query = "${track.artist} - ${track.title}"
        val results = runCatching {
            YoutubeDlBridge.searchYouTube(appContext, query, AUTO_SEARCH_CANDIDATE_COUNT)
        }.onFailure { error ->
            Log.w("DownloadQueue", "Could not prefetch YouTube candidates for $query", error)
        }.getOrDefault(emptyList())

        val urls = results
            .map { it.url }
            .filter { it.isYouTubeUrl() }
            .distinct()
            .take(AUTO_SEARCH_CANDIDATE_COUNT)

        val fallbackUrls = urls.ifEmpty { listOf("ytsearch1:$query") }
        return fallbackUrls.mapIndexed { index, url ->
            DownloadCandidate(url = url, label = "${index + 1}/${fallbackUrls.size}")
        }
    }

    private fun String.isYouTubeUrl(): Boolean = contains("youtube.com") || contains("youtu.be")

    private const val AUTO_SEARCH_CANDIDATE_COUNT = 3
}
