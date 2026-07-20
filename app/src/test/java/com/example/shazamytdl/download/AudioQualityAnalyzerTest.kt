package com.example.shazamytdl.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityAnalyzerTest {
    @Test
    fun marksObviousQuietRecordingAsTooQuiet() {
        val report = AudioLoudnessReport(
            rmsDb = -42.0,
            activeRmsDb = -34.0,
            peakDb = -22.0,
            sampleCount = 100_000,
            activeSampleCount = 70_000
        )

        assertTrue(AudioQualityAnalyzer.isTooQuiet(report))
    }

    @Test
    fun keepsTrackWithHealthyPeak() {
        val report = AudioLoudnessReport(
            rmsDb = -34.0,
            activeRmsDb = -31.0,
            peakDb = -6.0,
            sampleCount = 100_000,
            activeSampleCount = 50_000
        )

        assertFalse(AudioQualityAnalyzer.isTooQuiet(report))
    }

    @Test
    fun retriesSuspiciouslyQuietCandidate() {
        val report = AudioLoudnessReport(
            rmsDb = -35.0,
            activeRmsDb = -27.0,
            peakDb = -8.0,
            sampleCount = 100_000,
            activeSampleCount = 55_000
        )

        assertFalse(AudioQualityAnalyzer.isTooQuiet(report))
        assertTrue(AudioQualityAnalyzer.shouldTryNextCandidate(report))
    }

    @Test
    fun keepsClearlyLoudCandidate() {
        val report = AudioLoudnessReport(
            rmsDb = -22.0,
            activeRmsDb = -18.0,
            peakDb = -2.0,
            sampleCount = 100_000,
            activeSampleCount = 90_000
        )

        assertFalse(AudioQualityAnalyzer.shouldTryNextCandidate(report))
    }

    @Test
    fun ignoresVeryShortAnalysis() {
        val report = AudioLoudnessReport(
            rmsDb = -50.0,
            activeRmsDb = -45.0,
            peakDb = -30.0,
            sampleCount = 1_000,
            activeSampleCount = 500
        )

        assertFalse(AudioQualityAnalyzer.isTooQuiet(report))
        assertFalse(AudioQualityAnalyzer.shouldTryNextCandidate(report))
    }
}
