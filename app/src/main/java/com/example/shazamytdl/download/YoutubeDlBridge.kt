package com.example.shazamytdl.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

data class DownloadedMedia(
    val file: File,
    val sourceUrl: String,
    val thumbnailUrl: String?
)

data class YouTubeSearchResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val url: String,
    val durationSeconds: Long?
)

class DownloadCancelledException : RuntimeException("Prenos je bil preklican.")

class DownloadCancelToken {
    private val cancelled = AtomicBoolean(false)
    private val callbackLock = Any()
    private var cancelAction: (() -> Unit)? = null

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        val action = synchronized(callbackLock) {
            if (cancelled.compareAndSet(false, true)) cancelAction else null
        }
        action?.invoke()
    }

    fun throwIfCancelled() {
        if (isCancelled) throw DownloadCancelledException()
    }

    internal fun setCancelAction(action: () -> Unit) {
        val runNow = synchronized(callbackLock) {
            if (isCancelled) {
                true
            } else {
                cancelAction = action
                false
            }
        }
        if (runNow) action()
    }

    internal fun clearCancelAction() {
        synchronized(callbackLock) { cancelAction = null }
    }
}

object YoutubeDlBridge {
    private val operationLock = Any()
    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        try {
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            initialized = true
        } catch (e: Exception) {
            Log.e("YoutubeDlBridge", "Failed to initialize YoutubeDL", e)
        }
    }

    suspend fun updateYoutubeDL(context: Context): String? = withContext(Dispatchers.IO) {
        updateYoutubeDLNow(context)
    }

    fun updateYoutubeDLNow(context: Context): String? {
        try {
            init(context)
            Log.d("YoutubeDlBridge", "Checking for yt-dlp updates...")
            val result = synchronized(operationLock) {
                YoutubeDL.getInstance().updateYoutubeDL(context)
            }
            Log.d("YoutubeDlBridge", "yt-dlp update result: ${result?.name}")
            return result?.name
        } catch (e: Exception) {
            Log.e("YoutubeDlBridge", "Failed to update YoutubeDL", e)
            return null
        }
    }

    fun searchYouTube(
        context: Context,
        query: String,
        maxResults: Int = 5
    ): List<YouTubeSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        init(context)

        val request = YoutubeDLRequest("ytsearch${maxResults.coerceIn(1, 10)}:$normalizedQuery").apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--ignore-errors")
            addOption("--no-warnings")
            addNetworkOptions()
        }
        val response = synchronized(operationLock) {
            YoutubeDL.getInstance().execute(request)
        }
        val entries = JSONObject(response.out).optJSONArray("entries") ?: return emptyList()

        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val videoId = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = entry.optString("title").takeIf { it.isNotBlank() } ?: continue
                val channel = sequenceOf("channel", "uploader", "channel_id", "uploader_id")
                    .map(entry::optString)
                    .firstOrNull { it.isNotBlank() }
                    ?: "YouTube"
                val duration = entry.optLong("duration", -1L).takeIf { it >= 0L }
                add(
                    YouTubeSearchResult(
                        videoId = videoId,
                        title = title,
                        channel = channel,
                        url = "https://www.youtube.com/watch?v=$videoId",
                        durationSeconds = duration
                    )
                )
            }
        }
    }

    fun downloadAllowedUrl(
        context: Context,
        url: String,
        outputDir: File,
        subPath: String? = null,
        cancelToken: DownloadCancelToken? = null,
        onProgress: (progressPercent: Float, etaSeconds: Long) -> Unit
    ): DownloadedMedia {
        init(context)
        cancelToken?.throwIfCancelled()

        check(outputDir.exists() || outputDir.mkdirs()) {
            "Could not create output directory: ${outputDir.absolutePath}"
        }
        val before = outputDir.listFiles()
            ?.filter { it.isFile }
            ?.associate { it.absolutePath to (it.lastModified() to it.length()) }
            .orEmpty()
        val fileNameTemplate = (subPath ?: "%(title).180s")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outputTemplate = File(outputDir, "$fileNameTemplate.%(ext)s").absolutePath

        val resolvedInfo = synchronized(operationLock) {
            cancelToken?.throwIfCancelled()
            YoutubeDL.getInstance().getInfo(
                YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("-f", "bestaudio/best")
                    addNetworkOptions()
                }
            )
        }
        cancelToken?.throwIfCancelled()
        val resolvedUrl = resolvedInfo.webpageUrl
            ?.takeIf { it.startsWith("https://www.youtube.com/") || it.startsWith("https://youtu.be/") }
            ?: error("yt-dlp ni vrnil veljavnega YouTube URL-ja")

        Log.d(
            "YoutubeDlBridge",
            "Resolved $url to $resolvedUrl (format=${resolvedInfo.formatId}, ext=${resolvedInfo.ext})"
        )

        val request = YoutubeDLRequest(resolvedUrl).apply {
            addOption("--no-playlist")
            addOption("-f", "bestaudio/best")
            addOption("--restrict-filenames")
            addOption("-x")
            // Preserve Opus/AAC instead of transcoding one lossy codec into another.
            addOption("--audio-format", "best")
            addOption("--add-metadata")
            addOption("-o", outputTemplate)
            addNetworkOptions()
        }

        val processId = "download-${UUID.randomUUID()}"
        Log.d("YoutubeDlBridge", "Starting download: $url")
        Log.d("YoutubeDlBridge", "Process ID: $processId")
        val lastActivityAt = AtomicLong(System.currentTimeMillis())
        val finished = AtomicBoolean(false)
        val timedOut = AtomicBoolean(false)
        val watchdog = thread(
            start = true,
            isDaemon = true,
            name = "yt-dlp-watchdog"
        ) {
            try {
                while (!finished.get()) {
                    Thread.sleep(WATCHDOG_INTERVAL_MS)
                    if (System.currentTimeMillis() - lastActivityAt.get() >= INACTIVITY_TIMEOUT_MS) {
                        timedOut.set(true)
                        Log.e("YoutubeDlBridge", "Download timed out: $resolvedUrl")
                        YoutubeDL.getInstance().destroyProcessById(processId)
                        return@thread
                    }
                }
            } catch (_: InterruptedException) {
                // Normal completion interrupts the watchdog.
            }
        }
        var executionError: Throwable? = null
        val cancelAction = {
            Log.d("YoutubeDlBridge", "Cancelling download process: $processId")
            YoutubeDL.getInstance().destroyProcessById(processId)
            Unit
        }
        cancelToken?.setCancelAction(cancelAction)
        try {
            synchronized(operationLock) {
                cancelToken?.throwIfCancelled()
                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
                    cancelToken?.throwIfCancelled()
                    lastActivityAt.set(System.currentTimeMillis())
                    onProgress(progress, etaInSeconds)
                }
            }
        } catch (error: Throwable) {
            executionError = if (cancelToken?.isCancelled == true) {
                DownloadCancelledException()
            } else {
                error
            }
        } finally {
            cancelToken?.clearCancelAction()
            finished.set(true)
            watchdog.interrupt()
        }
        cancelToken?.throwIfCancelled()
        if (timedOut.get()) {
            error("Prenos je bil brez napredka več kot 3 minute in je bil prekinjen")
        }
        executionError?.let { throw it }

        cancelToken?.throwIfCancelled()
        val candidates = outputDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .orEmpty()
        val changedCandidates = candidates.filter { file ->
            before[file.absolutePath] != (file.lastModified() to file.length())
        }

        // This directory belongs to one track only. Prefer output changed by this
        // execution, but accept an existing complete file reused by yt-dlp.
        val file = (changedCandidates.ifEmpty { candidates })
            .maxByOrNull { it.lastModified() }
            ?: error("Prenos se je končal, vendar zvočna datoteka ni bila najdena")

        return DownloadedMedia(
            file = file,
            sourceUrl = resolvedUrl,
            thumbnailUrl = resolvedInfo.thumbnail
        )
    }

    fun resolvePreviewVideoStreamUrl(
        context: Context,
        url: String,
        cancelToken: DownloadCancelToken? = null
    ): String {
        init(context)
        cancelToken?.throwIfCancelled()

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-f", PREVIEW_STREAM_FORMAT)
            addOption("--get-url")
            addOption("--no-warnings")
            addNetworkOptions()
        }
        val response = synchronized(operationLock) {
            cancelToken?.throwIfCancelled()
            YoutubeDL.getInstance().execute(request)
        }
        cancelToken?.throwIfCancelled()
        return response.out
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
            ?: error("yt-dlp ni vrnil pretočnega video URL-ja.")
    }

    fun downloadPreviewVideo(
        context: Context,
        url: String,
        outputDir: File,
        cancelToken: DownloadCancelToken? = null,
        onProgress: (progressPercent: Float, etaSeconds: Long) -> Unit = { _, _ -> }
    ): File {
        init(context)
        cancelToken?.throwIfCancelled()

        if (outputDir.exists()) {
            check(outputDir.deleteRecursively()) {
                "Prejšnjega video previewja ni bilo mogoče počistiti."
            }
        }
        check(outputDir.mkdirs() || outputDir.isDirectory) {
            "Could not create preview directory: ${outputDir.absolutePath}"
        }

        val outputTemplate = File(outputDir, "preview.%(ext)s").absolutePath
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-f", PREVIEW_VIDEO_FORMAT)
            addOption("--restrict-filenames")
            addOption("--force-overwrites")
            addOption("--max-filesize", "80M")
            addOption("--merge-output-format", "mp4")
            addOption("-o", outputTemplate)
            addNetworkOptions()
        }

        val processId = "preview-${UUID.randomUUID()}"
        Log.d("YoutubeDlBridge", "Starting video preview download: $url")
        Log.d("YoutubeDlBridge", "Process ID: $processId")
        val lastActivityAt = AtomicLong(System.currentTimeMillis())
        val finished = AtomicBoolean(false)
        val timedOut = AtomicBoolean(false)
        val watchdog = thread(
            start = true,
            isDaemon = true,
            name = "yt-dlp-preview-watchdog"
        ) {
            try {
                while (!finished.get()) {
                    Thread.sleep(WATCHDOG_INTERVAL_MS)
                    if (System.currentTimeMillis() - lastActivityAt.get() >= INACTIVITY_TIMEOUT_MS) {
                        timedOut.set(true)
                        Log.e("YoutubeDlBridge", "Video preview timed out: $url")
                        YoutubeDL.getInstance().destroyProcessById(processId)
                        return@thread
                    }
                }
            } catch (_: InterruptedException) {
                // Normal completion interrupts the watchdog.
            }
        }
        var executionError: Throwable? = null
        val cancelAction = {
            Log.d("YoutubeDlBridge", "Cancelling video preview process: $processId")
            YoutubeDL.getInstance().destroyProcessById(processId)
            Unit
        }
        cancelToken?.setCancelAction(cancelAction)
        try {
            synchronized(operationLock) {
                cancelToken?.throwIfCancelled()
                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
                    cancelToken?.throwIfCancelled()
                    lastActivityAt.set(System.currentTimeMillis())
                    onProgress(progress, etaInSeconds)
                }
            }
        } catch (error: Throwable) {
            executionError = if (cancelToken?.isCancelled == true) {
                DownloadCancelledException()
            } else {
                error
            }
        } finally {
            cancelToken?.clearCancelAction()
            finished.set(true)
            watchdog.interrupt()
        }
        cancelToken?.throwIfCancelled()
        if (timedOut.get()) {
            error("Video preview je bil brez napredka več kot 3 minute in je bil prekinjen")
        }
        executionError?.let { throw it }

        val previewFile = outputDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension.lowercase() in VIDEO_EXTENSIONS }
            ?.maxByOrNull { it.lastModified() }
            ?: error("Video preview ni bil prenesen.")
        check(previewFile.hasVideoTrack()) {
            "Preneseni preview ne vsebuje video slike. Poskušam drug YouTube format."
        }
        return previewFile
    }

    private fun File.hasVideoTrack(): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 0
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 0
            width > 0 && height > 0
        } catch (error: Throwable) {
            Log.w("YoutubeDlBridge", "Could not inspect preview video track for $absolutePath", error)
            false
        } finally {
            retriever.release()
        }
    }

    private fun YoutubeDLRequest.addNetworkOptions() {
        addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        addOption("--referer", "https://www.google.com/")
        addOption("--socket-timeout", "30")
        addOption("--retries", "3")
        addOption("--fragment-retries", "3")
    }

    private val AUDIO_EXTENSIONS = setOf(
        "aac", "flac", "m4a", "mp3", "ogg", "opus", "vorbis", "wav", "webm"
    )

    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v")
    private const val PREVIEW_STREAM_FORMAT =
        "18/22/best[height<=360][ext=mp4][vcodec!=none][acodec!=none]/best[height<=480][ext=mp4][vcodec!=none][acodec!=none]/best[height<=360][vcodec!=none][acodec!=none]"
    private const val PREVIEW_VIDEO_FORMAT =
        "18/22/bestvideo[vcodec^=avc1][height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[vcodec^=avc1][height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=360][ext=mp4][vcodec!=none][acodec!=none]"

    private const val WATCHDOG_INTERVAL_MS = 5_000L
    private const val INACTIVITY_TIMEOUT_MS = 3 * 60_000L
}
