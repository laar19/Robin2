package com.example.speech.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.TranscriptionItem
import com.example.speech.OnDeviceSpeechRecognizerHelper
import com.example.speech.accessibility.VoiceAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground Service that manages an overlay draggable floating bubble (SYSTEM_ALERT_WINDOW)
 * for rapid offline voice dictation from any active app.
 */
class FloatingVoiceService : Service(), OnDeviceSpeechRecognizerHelper.Listener {

    companion object {
        private const val TAG = "FloatingVoiceService"
        private const val CHANNEL_ID = "robin_floating_voice_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.speech.floating.STOP"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start FloatingVoiceService: Overlay permission not granted")
                return
            }
            val intent = Intent(context, FloatingVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingVoiceService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechHelper: OnDeviceSpeechRecognizerHelper? = null

    private var isListening = false
    private var speechStartTime: Long = 0

    // Views
    private lateinit var bubbleLayout: FrameLayout
    private lateinit var bubbleIcon: ImageView
    private lateinit var cardLayout: LinearLayout
    private lateinit var liveText: TextView
    private lateinit var statusText: TextView

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        speechHelper = OnDeviceSpeechRecognizerHelper(this, this)
        startForegroundNotification()
        setupFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Robin Widget Flotante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra la burbuja flotante de dictado por voz"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Burbuja de Dictado Activa")
            .setContentText("Toca la burbuja en pantalla para transcribir")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar", pStopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 350
        }

        // Build composite UI: Bubble + Expandable Card
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Expanded text preview card (initially hidden)
        cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardDrawable(Color.parseColor("#1E222D"), dpToPx(14))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dpToPx(240), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(8))
            }
        }

        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusText = TextView(this).apply {
            text = "Escuchando..."
            setTextColor(Color.parseColor("#60A5FA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeCardBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#9CA3AF"))
            background = null
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
            setOnClickListener {
                if (isListening) {
                    speechHelper?.stopListening()
                    updateBubbleState(false)
                }
                cardLayout.visibility = View.GONE
            }
        }

        cardHeader.addView(statusText)
        cardHeader.addView(closeCardBtn)
        cardLayout.addView(cardHeader)

        liveText = TextView(this).apply {
            text = "Habla ahora para dictar..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 4
            setPadding(0, dpToPx(4), 0, dpToPx(2))
        }
        cardLayout.addView(liveText)
        rootLayout.addView(cardLayout)

        // Floating draggable circular bubble
        bubbleLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(58), dpToPx(58))
            background = createCircleDrawable(Color.parseColor("#2563EB"), Color.WHITE, dpToPx(2))
            elevation = dpToPx(6).toFloat()
        }

        bubbleIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(32), dpToPx(32), Gravity.CENTER)
        }
        bubbleLayout.addView(bubbleIcon)
        rootLayout.addView(bubbleLayout)

        // Touch listener for dragging and tapping
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isClick = false

        bubbleLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(rootLayout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        vibrateTap()
                        onBubbleClicked()
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = rootLayout
        try {
            windowManager?.addView(rootLayout, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating view", e)
        }
    }

    private fun onBubbleClicked() {
        if (isListening) {
            speechHelper?.stopListening()
            updateBubbleState(false)
        } else {
            speechStartTime = System.currentTimeMillis()
            updateBubbleState(true)
            cardLayout.visibility = View.VISIBLE
            liveText.text = "Escuchando..."
            statusText.text = "Micrófono activo"
            speechHelper?.startListening()
        }
    }

    private fun updateBubbleState(listening: Boolean) {
        isListening = listening
        if (listening) {
            // Glowing red / pulsing state
            bubbleLayout.background = createCircleDrawable(Color.parseColor("#DC2626"), Color.parseColor("#FCA5A5"), dpToPx(3))
        } else {
            // Indigo / blue normal state
            bubbleLayout.background = createCircleDrawable(Color.parseColor("#2563EB"), Color.WHITE, dpToPx(2))
        }
    }

    // OnDeviceSpeechRecognizerHelper.Listener
    override fun onReadyForSpeech() {
        statusText.text = "Listo para escuchar"
        statusText.setTextColor(Color.parseColor("#4ADE80"))
    }

    override fun onBeginningOfSpeech() {
        statusText.text = "Grabando audio..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Can be used for audio reactivity if needed
    }

    override fun onPartialResult(fullText: String, diffText: String) {
        cardLayout.visibility = View.VISIBLE
        liveText.text = fullText
    }

    override fun onFinalResult(finalText: String) {
        updateBubbleState(false)
        if (finalText.isNotBlank()) {
            liveText.text = finalText
            statusText.text = "¡Transcripción completada!"
            statusText.setTextColor(Color.parseColor("#4ADE80"))

            handleCompletedTranscription(finalText)
        }
    }

    override fun onError(errorCode: Int, errorMsg: String, fallbackText: String) {
        updateBubbleState(false)
        statusText.text = errorMsg
        statusText.setTextColor(Color.parseColor("#EF4444"))

        if (fallbackText.isNotBlank()) {
            liveText.text = fallbackText
            handleCompletedTranscription(fallbackText)
        }
    }

    override fun onEndOfSpeech() {
        statusText.text = "Procesando..."
    }

    private fun handleCompletedTranscription(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 1. Copy text to system clipboard
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Robin Dictado", trimmed)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "📋 Copiado: \"$trimmed\"", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
        }

        // 2. Direct injection via AccessibilityService if active
        val injected = VoiceAccessibilityService.injectTextIntoFocusedView(trimmed)
        if (injected) {
            Toast.makeText(this, "✓ Inyectado en campo activo", Toast.LENGTH_SHORT).show()
        }

        // 3. Persist to Room Database with tag "Widget Flotante"
        val durationSecs = ((System.currentTimeMillis() - speechStartTime) / 1000).coerceAtLeast(1)
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.transcriptionDao().insertTranscription(
                    TranscriptionItem(
                        text = trimmed,
                        timestamp = System.currentTimeMillis(),
                        engine = "Widget Flotante",
                        durationSecs = durationSecs,
                        isFavorite = false
                    )
                )
                Log.d(TAG, "Saved floating transcription to Room DB")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving floating transcription to DB", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        speechHelper?.destroy()
        speechHelper = null

        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
        floatingView = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun createCardDrawable(bgColor: Int, cornerRadiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusPx.toFloat()
            setStroke(dpToPx(1), Color.parseColor("#3B4252"))
        }
    }

    private fun createCircleDrawable(bgColor: Int, strokeColor: Int, strokeWidthPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
            setStroke(strokeWidthPx, strokeColor)
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
