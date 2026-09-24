package com.example.speech.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.AppDatabase
import com.example.data.TranscriptionItem
import com.example.speech.OnDeviceSpeechRecognizerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom InputMethodService (IME) that brings offline on-device voice dictation
 * to any app across the Android system.
 */
class VoiceInputMethodService : InputMethodService(), OnDeviceSpeechRecognizerHelper.Listener {

    private val TAG = "VoiceIME"
    private var speechHelper: OnDeviceSpeechRecognizerHelper? = null
    private var isListening = false
    private var speechStartTime: Long = 0

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var previewText: TextView
    private lateinit var micButton: FrameLayout
    private lateinit var micIcon: ImageView
    private lateinit var waveContainer: LinearLayout
    private val waveBars = mutableListOf<View>()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        speechHelper = OnDeviceSpeechRecognizerHelper(this, this)
    }

    override fun onCreateInputView(): View {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(270)
            )
            setBackgroundColor(Color.parseColor("#14171F"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }

        // Top bar: IME Title + Switch Keyboard button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val appLabel = TextView(this).apply {
            text = "Robin Voz Offline"
            setTextColor(Color.parseColor("#9ECAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switchKeyboardBtn = ImageButton(this).apply {
            contentDescription = "Cambiar de teclado"
            layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(38))
            background = createPillDrawable(Color.parseColor("#262E3D"), dpToPx(19))
            setImageResource(android.R.drawable.ic_menu_agenda)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                vibrateTap()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        }

        topBar.addView(appLabel)
        topBar.addView(switchKeyboardBtn)
        rootLayout.addView(topBar)

        // Status TextView
        statusText = TextView(this).apply {
            text = "Toca el micrófono para dictar"
            setTextColor(Color.parseColor("#8E919A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
        rootLayout.addView(statusText)

        // Preview Box for live recognized text
        val previewCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(8))
            }
            background = createCardDrawable(Color.parseColor("#1F2430"), dpToPx(12))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        previewText = TextView(this).apply {
            text = "El texto reconocido aparecerá aquí..."
            setTextColor(Color.parseColor("#C3C7D2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        previewCard.addView(previewText)
        rootLayout.addView(previewCard)

        // Center Area: Audio wave visualizer + Big Mic FAB
        val centerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        // Sound wave visualizer bars
        waveContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(36))
            gravity = Gravity.CENTER
        }
        waveBars.clear()
        for (i in 0 until 5) {
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(4), dpToPx(10)).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                background = createPillDrawable(Color.parseColor("#3880FF"), dpToPx(2))
            }
            waveBars.add(bar)
            waveContainer.addView(bar)
        }
        centerRow.addView(waveContainer)

        // Big Mic FAB Button
        micButton = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(66), dpToPx(66)).apply {
                setMargins(dpToPx(16), 0, dpToPx(16), 0)
            }
            background = createCircleDrawable(Color.parseColor("#2563EB"))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                vibrateTap()
                toggleSpeechRecognition()
            }
        }

        micIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(34), dpToPx(34), Gravity.CENTER)
        }
        micButton.addView(micIcon)
        centerRow.addView(micButton)

        // Symmetrical spacer/wave on right
        val rightWave = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(80), dpToPx(36))
            gravity = Gravity.CENTER
        }
        for (i in 0 until 5) {
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(4), dpToPx(10)).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                background = createPillDrawable(Color.parseColor("#3880FF"), dpToPx(2))
            }
            waveBars.add(bar)
            rightWave.addView(bar)
        }
        centerRow.addView(rightWave)
        rootLayout.addView(centerRow)

        // Bottom Row: Quick keys: Space, Backspace, Enter, Clear
        val bottomKeysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(10)
            }
            gravity = Gravity.CENTER
        }

        // Backspace button
        val backspaceBtn = ImageButton(this).apply {
            contentDescription = "Borrar"
            layoutParams = LinearLayout.LayoutParams(dpToPx(52), dpToPx(44)).apply {
                setMargins(0, 0, dpToPx(8), 0)
            }
            background = createPillDrawable(Color.parseColor("#262E3D"), dpToPx(10))
            setImageResource(android.R.drawable.ic_input_delete)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                vibrateTap()
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }

        // Space button
        val spaceBtn = TextView(this).apply {
            text = "ESPACIO"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f)
            background = createPillDrawable(Color.parseColor("#2B3345"), dpToPx(10))
            setOnClickListener {
                vibrateTap()
                currentInputConnection?.commitText(" ", 1)
            }
        }

        // Enter / Line Break button
        val enterBtn = TextView(this).apply {
            text = "↵"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(44)).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }
            background = createPillDrawable(Color.parseColor("#2563EB"), dpToPx(10))
            setOnClickListener {
                vibrateTap()
                val ic = currentInputConnection
                if (ic != null) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
        }

        bottomKeysRow.addView(backspaceBtn)
        bottomKeysRow.addView(spaceBtn)
        bottomKeysRow.addView(enterBtn)
        rootLayout.addView(bottomKeysRow)

        return rootLayout
    }

    private fun toggleSpeechRecognition() {
        if (isListening) {
            speechHelper?.stopListening()
            updateUiListening(false)
        } else {
            speechStartTime = System.currentTimeMillis()
            updateUiListening(true)
            statusText.text = "Iniciando escucha..."
            speechHelper?.startListening()
        }
    }

    private fun updateUiListening(listening: Boolean) {
        isListening = listening
        if (listening) {
            micButton.background = createCircleDrawable(Color.parseColor("#EF4444")) // Red pulsing
            statusText.text = "Escuchando voz..."
            statusText.setTextColor(Color.parseColor("#F87171"))
        } else {
            micButton.background = createCircleDrawable(Color.parseColor("#2563EB")) // Blue normal
            statusText.text = "Dictado en pausa"
            statusText.setTextColor(Color.parseColor("#8E919A"))
            resetWaveBars()
        }
    }

    private fun resetWaveBars() {
        for (bar in waveBars) {
            bar.layoutParams.height = dpToPx(10)
            bar.requestLayout()
        }
    }

    // OnDeviceSpeechRecognizerHelper.Listener implementations
    override fun onReadyForSpeech() {
        statusText.text = "¡Habla ahora!"
        statusText.setTextColor(Color.parseColor("#4ADE80"))
    }

    override fun onBeginningOfSpeech() {
        statusText.text = "Detectando audio..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
        for (i in waveBars.indices) {
            val factor = if (i % 2 == 0) normalized else (normalized * 0.7f)
            val heightDp = (10 + factor * 22).toInt()
            waveBars[i].layoutParams.height = dpToPx(heightDp)
            waveBars[i].requestLayout()
        }
    }

    override fun onPartialResult(fullText: String, diffText: String) {
        previewText.text = fullText
        // Use composing text to stream directly into the active field without repeating words
        currentInputConnection?.setComposingText(fullText, 1)
    }

    override fun onFinalResult(finalText: String) {
        updateUiListening(false)
        if (finalText.isNotBlank()) {
            previewText.text = finalText
            currentInputConnection?.finishComposingText()
            currentInputConnection?.commitText(" ", 1)

            val durationSecs = ((System.currentTimeMillis() - speechStartTime) / 1000).coerceAtLeast(1)
            saveTranscriptionToRoom(finalText, durationSecs)
        }
    }

    override fun onError(errorCode: Int, errorMsg: String, fallbackText: String) {
        updateUiListening(false)
        statusText.text = errorMsg
        statusText.setTextColor(Color.parseColor("#EF4444"))

        if (fallbackText.isNotBlank()) {
            previewText.text = fallbackText
            currentInputConnection?.finishComposingText()
            currentInputConnection?.commitText(" ", 1)
            val durationSecs = ((System.currentTimeMillis() - speechStartTime) / 1000).coerceAtLeast(1)
            saveTranscriptionToRoom(fallbackText, durationSecs)
        }
    }

    override fun onEndOfSpeech() {
        statusText.text = "Procesando transcripción..."
    }

    private fun saveTranscriptionToRoom(text: String, durationSecs: Long) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.transcriptionDao().insertTranscription(
                    TranscriptionItem(
                        text = text.trim(),
                        timestamp = System.currentTimeMillis(),
                        engine = "Teclado IME",
                        durationSecs = durationSecs,
                        isFavorite = false
                    )
                )
                Log.d(TAG, "Transcription saved successfully from IME Keyboard")
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving transcription to Room", e)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (isListening) {
            speechHelper?.stopListening()
            updateUiListening(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechHelper?.destroy()
        speechHelper = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun createPillDrawable(color: Int, cornerRadiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = cornerRadiusPx.toFloat()
        }
    }

    private fun createCardDrawable(color: Int, cornerRadiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = cornerRadiusPx.toFloat()
            setStroke(dpToPx(1), Color.parseColor("#333A4A"))
        }
    }

    private fun createCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun vibrateTap() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (_: Exception) {}
    }
}
