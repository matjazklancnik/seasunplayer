package com.example.shazamytdl.download

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioLoudnessReport(
    val rmsDb: Double,
    val activeRmsDb: Double,
    val peakDb: Double,
    val sampleCount: Long,
    val activeSampleCount: Long
)

object AudioQualityAnalyzer {
    fun analyze(file: File, cancelToken: DownloadCancelToken? = null): AudioLoudnessReport? {
        if (!file.isFile || file.length() <= 0L) return null
        cancelToken?.throwIfCancelled()

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = findAudioTrack(extractor) ?: return null
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)
            seekToAnalysisWindow(extractor, inputFormat)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            decodePcm(decoder, extractor, inputFormat, cancelToken)
        } catch (error: DownloadCancelledException) {
            throw error
        } catch (error: Throwable) {
            Log.w(TAG, "Could not analyze audio loudness for ${file.name}", error)
            null
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    fun isTooQuiet(report: AudioLoudnessReport): Boolean {
        if (report.sampleCount < MIN_ANALYZED_SAMPLES) return false
        return (report.peakDb < QUIET_PEAK_DB && report.activeRmsDb < QUIET_ACTIVE_RMS_DB) ||
            (report.rmsDb < QUIET_RMS_DB && report.peakDb < QUIET_SOFT_PEAK_DB)
    }

    fun shouldTryNextCandidate(report: AudioLoudnessReport): Boolean {
        if (report.sampleCount < MIN_ANALYZED_SAMPLES) return false
        return isTooQuiet(report) ||
            report.activeRmsDb < SUSPECT_ACTIVE_RMS_DB ||
            report.rmsDb < SUSPECT_RMS_DB ||
            report.peakDb < SUSPECT_PEAK_DB
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return null
    }

    private fun seekToAnalysisWindow(extractor: MediaExtractor, format: MediaFormat) {
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            -1L
        }
        val startUs = when {
            durationUs > 120_000_000L -> 30_000_000L
            durationUs > 45_000_000L -> 15_000_000L
            else -> 0L
        }
        if (startUs > 0L) {
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }
    }

    private fun decodePcm(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        cancelToken: DownloadCancelToken?
    ): AudioLoudnessReport? {
        val stats = AudioStats()
        val bufferInfo = MediaCodec.BufferInfo()
        var outputFormat = inputFormat
        var sawInputEnd = false
        var sawOutputEnd = false

        while (!sawOutputEnd && stats.sampleCount < MAX_ANALYZED_SAMPLES) {
            cancelToken?.throwIfCancelled()

            if (!sawInputEnd) {
                val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { buffer ->
                        buffer.clear()
                        extractor.readSampleData(buffer, 0)
                    } ?: -1

                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEnd = true
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0L),
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (outputIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEnd = true
                    }
                    if (bufferInfo.size > 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            stats.add(outputBuffer.slice(), outputFormat)
                        }
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }

        return stats.report()
    }

    private class AudioStats {
        var sampleCount = 0L
            private set
        private var activeSampleCount = 0L
        private var squareSum = 0.0
        private var activeSquareSum = 0.0
        private var peak = 0.0

        fun add(buffer: ByteBuffer, format: MediaFormat) {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            when (pcmEncoding(format)) {
                AudioFormat.ENCODING_PCM_FLOAT -> addFloat(buffer)
                AudioFormat.ENCODING_PCM_8BIT -> addPcm8(buffer)
                else -> addPcm16(buffer)
            }
        }

        fun report(): AudioLoudnessReport? {
            if (sampleCount == 0L) return null
            val rms = sqrt(squareSum / sampleCount)
            val activeRms = if (activeSampleCount > 0L) {
                sqrt(activeSquareSum / activeSampleCount)
            } else {
                0.0
            }
            return AudioLoudnessReport(
                rmsDb = db(rms),
                activeRmsDb = db(activeRms),
                peakDb = db(peak),
                sampleCount = sampleCount,
                activeSampleCount = activeSampleCount
            )
        }

        private fun addFloat(buffer: ByteBuffer) {
            while (buffer.remaining() >= Float.SIZE_BYTES && sampleCount < MAX_ANALYZED_SAMPLES) {
                addSample(buffer.float.toDouble().coerceIn(-1.0, 1.0))
            }
        }

        private fun addPcm8(buffer: ByteBuffer) {
            while (buffer.hasRemaining() && sampleCount < MAX_ANALYZED_SAMPLES) {
                val unsigned = buffer.get().toInt() and 0xFF
                addSample((unsigned - 128) / 128.0)
            }
        }

        private fun addPcm16(buffer: ByteBuffer) {
            while (buffer.remaining() >= Short.SIZE_BYTES && sampleCount < MAX_ANALYZED_SAMPLES) {
                addSample(buffer.short / Short.MAX_VALUE.toDouble())
            }
        }

        private fun addSample(sample: Double) {
            val absolute = kotlin.math.abs(sample)
            sampleCount++
            squareSum += sample * sample
            if (absolute > peak) peak = absolute
            if (absolute >= ACTIVE_SAMPLE_THRESHOLD) {
                activeSampleCount++
                activeSquareSum += sample * sample
            }
        }
    }

    private fun pcmEncoding(format: MediaFormat): Int = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        AudioFormat.ENCODING_PCM_16BIT
    }

    private fun db(value: Double): Double = if (value <= 0.0) {
        Double.NEGATIVE_INFINITY
    } else {
        20.0 * log10(value)
    }

    private const val TAG = "AudioQualityAnalyzer"
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val ACTIVE_SAMPLE_THRESHOLD = 0.01
    private const val MAX_ANALYZED_SAMPLES = 5_000_000L
    private const val MIN_ANALYZED_SAMPLES = 44_100L
    private const val QUIET_PEAK_DB = -18.0
    private const val QUIET_ACTIVE_RMS_DB = -30.0
    private const val QUIET_RMS_DB = -40.0
    private const val QUIET_SOFT_PEAK_DB = -12.0
    private const val SUSPECT_ACTIVE_RMS_DB = -26.0
    private const val SUSPECT_RMS_DB = -34.0
    private const val SUSPECT_PEAK_DB = -12.0
}
