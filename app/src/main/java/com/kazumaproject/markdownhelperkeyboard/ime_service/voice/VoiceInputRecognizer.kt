package com.kazumaproject.markdownhelperkeyboard.ime_service.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import timber.log.Timber

/**
 * Speech recognizer used by the IME.
 *
 * Android's dedicated on-device recognizer is preferred on Android 12+. If the selected language
 * is not installed/supported by that implementation, the request is retried once with the system
 * recognizer. The IME therefore remains usable on devices whose on-device language catalog varies.
 */
class VoiceInputRecognizer(
    context: Context,
    private val callback: Callback,
) {
    interface Callback {
        fun onReady()
        fun onEndOfSpeech()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(error: Int)
        fun onBackendChanged(onDevice: Boolean)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var backend = Backend.NONE
    private var currentIntent: Intent? = null
    private var fallbackAttempted = false

    val isListening: Boolean
        get() = listening

    val isUsingOnDeviceRecognition: Boolean
        get() = backend == Backend.ON_DEVICE

    private var listening = false

    fun start(languageTag: String): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (listening) return true

        fallbackAttempted = false
        val intent = recognitionIntent(languageTag)
        currentIntent = intent
        if (!ensureRecognizer(preferOnDevice = true)) return false
        return startCurrentRecognizer(intent)
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!listening) return
        runCatching { recognizer?.stopListening() }
        listening = false
    }

    fun destroy() {
        check(Looper.myLooper() == Looper.getMainLooper())
        listening = false
        currentIntent = null
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        backend = Backend.NONE
    }

    private fun recognitionIntent(languageTag: String) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

    private fun ensureRecognizer(preferOnDevice: Boolean): Boolean {
        val desiredBackend = if (
            preferOnDevice &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            Backend.ON_DEVICE
        } else {
            Backend.SYSTEM
        }
        if (recognizer != null && backend == desiredBackend) return true

        runCatching { recognizer?.destroy() }
        recognizer = null
        backend = Backend.NONE

        val created = runCatching {
            when (desiredBackend) {
                Backend.ON_DEVICE -> SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                Backend.SYSTEM -> {
                    if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return false
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                }
                Backend.NONE -> return false
            }
        }.onFailure {
            Timber.w(it, "Could not create %s speech recognizer", desiredBackend)
        }.getOrNull() ?: return if (desiredBackend == Backend.ON_DEVICE) {
            ensureRecognizer(preferOnDevice = false)
        } else {
            false
        }

        backend = desiredBackend
        recognizer = created.apply { setRecognitionListener(recognitionListener) }
        callback.onBackendChanged(desiredBackend == Backend.ON_DEVICE)
        return true
    }

    private fun startCurrentRecognizer(intent: Intent): Boolean = try {
        recognizer?.startListening(intent)
        listening = true
        true
    } catch (error: RuntimeException) {
        Timber.w(error, "Could not start speech recognizer")
        listening = false
        if (backend == Backend.ON_DEVICE && !fallbackAttempted) {
            retryWithSystemRecognizer()
        } else {
            false
        }
    }

    private fun retryWithSystemRecognizer(): Boolean {
        val intent = currentIntent ?: return false
        fallbackAttempted = true
        listening = false
        return ensureRecognizer(preferOnDevice = false) && startCurrentRecognizer(intent)
    }

    private fun shouldFallback(error: Int): Boolean = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> true
        else -> false
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = callback.onReady()
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = callback.onEndOfSpeech()

        override fun onError(error: Int) {
            listening = false
            if (backend == Backend.ON_DEVICE && !fallbackAttempted && shouldFallback(error)) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                ) {
                    currentIntent?.let { intent ->
                        runCatching { recognizer?.triggerModelDownload(intent) }
                    }
                }
                mainHandler.post {
                    if (!retryWithSystemRecognizer()) callback.onError(error)
                }
                return
            }
            callback.onError(error)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            results.firstRecognition()?.let(callback::onFinalResult)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.firstRecognition()?.let(callback::onPartialResult)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.firstRecognition(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private enum class Backend {
        NONE,
        ON_DEVICE,
        SYSTEM,
    }
}
