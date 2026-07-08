package com.example.shazamytdl

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

private const val SLOVENIAN = "sl-SI"
private const val ENGLISH = "en-US"

class VoiceSearchController(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onErrorMessage: (String) -> Unit
) : RecognitionListener {
    private var cancellationRequested = false
    private val appContext = context.applicationContext
    private val recognizer = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
        SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(this)
        }
    } else {
        null
    }

    fun prepareLanguageModels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || recognizer == null) return

        val preferences = appContext.getSharedPreferences("voice_search", Context.MODE_PRIVATE)
        if (preferences.getBoolean("models_requested", false)) return

        listOf(SLOVENIAN, ENGLISH).forEach { language ->
            val intent = recognitionIntent(language, enableLanguageSwitch = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                recognizer.triggerModelDownload(
                    intent,
                    appContext.mainExecutor,
                    object : ModelDownloadListener {
                        override fun onProgress(completedPercent: Int) = Unit
                        override fun onSuccess() = Unit
                        override fun onScheduled() = Unit
                        override fun onError(error: Int) = Unit
                    }
                )
            } else {
                recognizer.triggerModelDownload(intent)
            }
        }
        preferences.edit().putBoolean("models_requested", true).apply()
    }

    fun startListening() {
        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            onErrorMessage("V napravi ni razpoložljive storitve za prepoznavanje govora.")
            return
        }

        cancellationRequested = false
        onListeningChanged(true)
        speechRecognizer.startListening(
            recognitionIntent(
                language = defaultLanguage(),
                enableLanguageSwitch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            )
        )
    }

    fun cancel() {
        cancellationRequested = true
        recognizer?.cancel()
        onListeningChanged(false)
    }

    fun destroy() {
        recognizer?.destroy()
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onResults(results: Bundle?) {
        onListeningChanged(false)
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(onResult)
    }

    override fun onError(error: Int) {
        onListeningChanged(false)
        if (cancellationRequested) {
            cancellationRequested = false
            return
        }
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Govora ni bilo mogoče prepoznati. Poskusi znova."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ni bilo zaznanega govora."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Za glasovno iskanje dovoli uporabo mikrofona."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Slovenski ali angleški govorni model ni na voljo."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Prepoznavanje govora potrebuje omrežno povezavo."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Prepoznavalnik govora je trenutno zaseden."
            else -> "Glasovno iskanje ni uspelo."
        }
        onErrorMessage(message)
    }

    private fun recognitionIntent(language: String, enableLanguageSwitch: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (enableLanguageSwitch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val languages = arrayListOf(SLOVENIAN, ENGLISH)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    languages
                )
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    languages
                )
            }
        }

    private fun defaultLanguage(): String =
        if (appContext.resources.configuration.locales[0].language == "sl") SLOVENIAN else ENGLISH
}
