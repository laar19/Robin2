package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SpeechManager(private val context: Context) {
    private val TAG = "SpeechManager"
    private var speechRecognizer: SpeechRecognizer? = null

    private val _textState = MutableStateFlow("")
    val textState: StateFlow<String> = _textState

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    init {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _textState.value = "Listo para hablar..."
                            _isListening.value = true
                            _errorState.value = null
                        }

                        override fun onBeginningOfSpeech() {
                            _textState.value = "Escuchando..."
                        }

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _isListening.value = false
                        }

                        override fun onError(error: Int) {
                            val errorMessage = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono insuficiente"
                                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No se detectó voz. Intenta otra vez."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Servicio ocupado. Reintentando..."
                                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silencio detectado."
                                else -> "Error desconocido al transcribir"
                            }
                            Log.e(TAG, "SpeechRecognizer Error: $errorMessage ($error)")
                            _errorState.value = errorMessage
                            _isListening.value = false
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                _textState.value = matches[0]
                            } else {
                                _textState.value = ""
                            }
                            _isListening.value = false
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                _textState.value = matches[0] + "..."
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } else {
                Log.e(TAG, "Speech recognition not available on this device.")
                _errorState.value = "Reconocimiento de voz nativo no disponible en este dispositivo."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Init SpeechRecognizer failed", e)
            _errorState.value = "No se pudo inicializar SpeechRecognizer nativo."
        }
    }

    fun setError(message: String?) {
        _errorState.value = message
    }

    fun startListening(languageCode: String = "es") {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            _errorState.value = "Motor STT Nativo no disponible"
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            _errorState.value = null
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed startListening", e)
            _errorState.value = e.message ?: "Error al iniciar micrófono"
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopListening", e)
        }
        _isListening.value = false
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed destroy SpeechRecognizer", e)
        }
    }
}
