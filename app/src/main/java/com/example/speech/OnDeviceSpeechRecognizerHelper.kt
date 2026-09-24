package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Dedicated helper for On-Device and Native SpeechRecognizer.
 * Enforces strict Main Looper thread execution for all SpeechRecognizer interactions.
 */
class OnDeviceSpeechRecognizerHelper(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onReadyForSpeech() {}
        fun onBeginningOfSpeech() {}
        fun onRmsChanged(rmsdB: Float) {}
        fun onPartialResult(fullText: String, diffText: String) {}
        fun onFinalResult(finalText: String) {}
        fun onError(errorCode: Int, errorMsg: String, fallbackText: String) {}
        fun onEndOfSpeech() {}
    }

    companion object {
        private const val TAG = "OnDeviceSpeechHelper"

        /**
         * Checks whether hardware-accelerated on-device speech recognition is available.
         */
        fun isOnDeviceAvailable(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking isOnDeviceRecognitionAvailable", e)
                    false
                }
            } else {
                false
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private var lastPartialText: String = ""

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun ensureRecognizerInitialized() {
        if (speechRecognizer != null) return

        try {
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                Log.d(TAG, "Instantiating On-Device SpeechRecognizer")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.d(TAG, "Instantiating standard SpeechRecognizer (On-device flag will be preferred)")
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                    listener.onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                    listener.onBeginningOfSpeech()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    listener.onRmsChanged(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                    isListening = false
                    listener.onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMsg = mapErrorCode(error)
                    Log.e(TAG, "Speech error code $error: $errorMsg, fallback='$lastPartialText'")
                    listener.onError(error, errorMsg, lastPartialText)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val resultText = matches?.firstOrNull() ?: lastPartialText
                    Log.d(TAG, "onResults: $resultText")
                    listener.onFinalResult(resultText)
                    lastPartialText = ""
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val currentText = matches?.firstOrNull() ?: return
                    
                    val diff = if (currentText.startsWith(lastPartialText)) {
                        currentText.substring(lastPartialText.length)
                    } else {
                        currentText
                    }
                    lastPartialText = currentText
                    Log.d(TAG, "onPartialResult: $currentText (diff: $diff)")
                    listener.onPartialResult(currentText, diff)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
        }
    }

    fun startListening(languageCode: String = Locale.getDefault().toLanguageTag()) {
        runOnMainThread {
            try {
                ensureRecognizerInitialized()
                lastPartialText = ""

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "Started listening with language: $languageCode")
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting listening", e)
                isListening = false
                listener.onError(-1, e.message ?: "Error iniciando micrófono", "")
            }
        }
    }

    fun stopListening() {
        runOnMainThread {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping listening", e)
            }
        }
    }

    fun cancel() {
        runOnMainThread {
            try {
                speechRecognizer?.cancel()
                isListening = false
                lastPartialText = ""
            } catch (e: Exception) {
                Log.e(TAG, "Exception canceling speech recognition", e)
            }
        }
    }

    fun destroy() {
        runOnMainThread {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                isListening = false
            } catch (e: Exception) {
                Log.e(TAG, "Exception destroying SpeechRecognizer", e)
            }
        }
    }

    private fun mapErrorCode(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Error de grabación de audio"
            SpeechRecognizer.ERROR_CLIENT -> "Error del cliente de voz"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono no concedido"
            SpeechRecognizer.ERROR_NETWORK -> "Error de red"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
            SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna palabra"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
            SpeechRecognizer.ERROR_SERVER -> "Error en el servidor de voz"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silencio prolongado detectado"
            else -> "Error de reconocimiento ($error)"
        }
    }
}
