package com.example.shazamytdl.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object AudioSampleRecorder {
    @SuppressLint("MissingPermission")
    suspend fun record(
        context: Context,
        durationMs: Long = DEFAULT_DURATION_MS
    ): File = withContext(Dispatchers.IO) {
        val output = File(context.cacheDir, "song-recognition-${System.currentTimeMillis()}.m4a")
        val recorder = createRecorder(context)
        var started = false
        var stopped = false

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(SAMPLE_RATE)
            recorder.setAudioEncodingBitRate(BIT_RATE)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            started = true

            delay(durationMs)

            recorder.stop()
            stopped = true
            check(output.isFile && output.length() > 0L) {
                "Zvocni vzorec ni bil posnet."
            }
            output
        } catch (error: CancellationException) {
            if (started && !stopped) runCatching { recorder.stop() }
            output.delete()
            throw error
        } catch (error: Throwable) {
            if (started && !stopped) runCatching { recorder.stop() }
            output.delete()
            throw IllegalStateException(
                "Snemanje zvocnega vzorca ni uspelo: ${error.message}",
                error
            )
        } finally {
            runCatching { recorder.reset() }
            recorder.release()
        }
    }

    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private const val DEFAULT_DURATION_MS = 10_000L
    private const val SAMPLE_RATE = 44_100
    private const val BIT_RATE = 128_000
}
