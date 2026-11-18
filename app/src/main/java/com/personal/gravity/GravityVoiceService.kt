package com.personal.gravity

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class GravityVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private lateinit var processor: CommandProcessor
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var floatingOrb: GravityOrbView? = null

    private var isAwakeSession = false
    private var isTtsSpeaking = false
    private var isListening = false
    private var isBeepMuted = false

    private val sleepTimeoutRunnable = Runnable {
        isAwakeSession = false
        broadcastState(
            GravityOrbView.OrbState.PASSIVE,
            false,
            "Passive Standby — Say “Hello Gravity”"
        )
    }

    companion object {
        var isRunning = false
        var onAudioLevelUpdate: ((Float) -> Unit)? = null
        var onStateUpdate: ((GravityOrbView.OrbState, Boolean, String) -> Unit)? = null
        var manualWakeTrigger: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        processor = CommandProcessor(applicationContext) { speaking ->
            isTtsSpeaking = speaking
            if (speaking) {
                setRecognizerBeepMuted(false)
                stopListeningInternal()
            } else {
                scheduleListeningRestart(250)
            }
        }

        manualWakeTrigger = {
            activateWakeSession("Manual Wake — Speak your command now...")
            processor.speak("Haan boliye?")
        }

        startForegroundSafely()
        setupFloatingOverlayIfAllowed()
        setupSpeechRecognizer()
    }

    private fun startForegroundSafely() {
        val channelId = "gravity_os_v3_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "Gravity System Assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gravity Core Online")
            .setContentText("Say 'Hello Gravity' anywhere")
            .setSmallIcon(R.drawable.ic_gravity_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1003,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1003, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun setupFloatingOverlayIfAllowed() {
        if (!Settings.canDrawOverlays(this)) return

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_gravity_bubble, null)
            floatingOrb = overlayView?.findViewById(R.id.floatingOrbView)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 24
                y = 260
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            overlayView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val movedX = abs(event.rawX - initialTouchX)
                        val movedY = abs(event.rawY - initialTouchY)
                        if (movedX < 12 && movedY < 12) {
                            manualWakeTrigger?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {}
    }

    private fun setRecognizerBeepMuted(mute: Boolean) {
        if (isBeepMuted == mute) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, 0)
            isBeepMuted = mute
        } catch (_: Exception) {}
    }

    private fun setupSpeechRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    // hi-IN captures Hindi, Hinglish, and English words accurately in India
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        floatingOrb?.updateAudioLevel(rmsdB)
                        onAudioLevelUpdate?.invoke(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == SpeechRecognizer.ERROR_CLIENT
                        ) {
                            mainHandler.postDelayed({ setupSpeechRecognizer() }, 600)
                        } else {
                            scheduleListeningRestart(450)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            processor.processAllHypotheses(matches, isAwakeSession) { orbState, stayAwake, msg ->
                                if (stayAwake) {
                                    activateWakeSession(msg)
                                } else {
                                    isAwakeSession = false
                                    mainHandler.removeCallbacks(sleepTimeoutRunnable)
                                    broadcastState(orbState, false, msg)
                                }
                            }
                        }
                        if (!isTtsSpeaking) {
                            scheduleListeningRestart(320)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.lowercase() ?: return

                        if (!isAwakeSession && processor.containsWakeWord(partial)) {
                            broadcastState(
                                GravityOrbView.OrbState.AWAKE,
                                true,
                                "Hearing: “$partial”..."
                            )
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                startListeningInternal()
            } catch (_: Exception) {
                scheduleListeningRestart(1000)
            }
        }
    }

    private fun broadcastState(orbState: GravityOrbView.OrbState, isAwake: Boolean, message: String) {
        floatingOrb?.setOrbState(orbState)
        onStateUpdate?.invoke(orbState, isAwake, message)
    }

    private fun activateWakeSession(statusMessage: String) {
        isAwakeSession = true
        mainHandler.removeCallbacks(sleepTimeoutRunnable)
        mainHandler.postDelayed(sleepTimeoutRunnable, 15000)
        broadcastState(GravityOrbView.OrbState.AWAKE, true, statusMessage)
    }

    private fun startListeningInternal() {
        if (isTtsSpeaking || isListening || !isRunning) return
        mainHandler.post {
            try {
                if (!isAwakeSession) setRecognizerBeepMuted(true)
                speechRecognizer?.startListening(recognizerIntent)
            } catch (_: Exception) {
                scheduleListeningRestart(600)
            }
        }
    }

    private fun stopListeningInternal() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                isListening = false
            } catch (_: Exception) {}
        }
    }

    private fun scheduleListeningRestart(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        if (isAwakeSession) {
            mainHandler.postDelayed(sleepTimeoutRunnable, 15000)
        }
        mainHandler.postDelayed({
            if (isRunning && !isTtsSpeaking) {
                startListeningInternal()
            }
        }, delayMs)
    }

    override fun onDestroy() {
        isRunning = false
        manualWakeTrigger = null
        setRecognizerBeepMuted(false)
        mainHandler.removeCallbacksAndMessages(null)
        try {
            overlayView?.let { windowManager?.removeView(it) }
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        processor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
