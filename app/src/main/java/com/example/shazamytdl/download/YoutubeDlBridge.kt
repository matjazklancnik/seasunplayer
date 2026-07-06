package com.example.shazamytdl.download

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        try {
            init(context)
            Log.d("YoutubeDlBridge", "Checking for yt-dlp updates...")
            val result = synchronized(operationLock) {
                YoutubeDL.getInstance().updateYoutubeDL(context)
            }
            Log.d("YoutubeDlBridge", "yt-dlp update result: ${result?.name}")
            return@withContext result?.name
        } catch (e: Exception) {
            Log.e("YoutubeDlBridge", "Failed to update YoutubeDL", e)
            return@withContext null
        }
    }

    fun downloadAllowedUrl(
        context: Context,
        url: String,
        outputDir: File,
        subPath: String? = null,
        onProgress: (progressPercent: Float, etaSeconds: Long) -> Unit
    ): DownloadedMedia {
        init(context)

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
            YoutubeDL.getInstance().getInfo(
                YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("-f", "bestaudio/best")
                    addNetworkOptions()
                }
            )
        }
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
            addOption("--verbose")
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
        try {
            synchronized(operationLock) {
                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
                    lastActivityAt.set(System.currentTimeMillis())
                    onProgress(progress, etaInSeconds)
                }
            }
        } catch (error: Throwable) {
            executionError = error
        } finally {
            finished.set(true)
            watchdog.interrupt()
        }
        if (timedOut.get()) {
            error("Prenos je bil brez napredka več kot 3 minute in je bil prekinjen")
        }
        executionError?.let { throw it }

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

    private const val WATCHDOG_INTERVAL_MS = 5_000L
    private const val INACTIVITY_TIMEOUT_MS = 3 * 60_000L
}
