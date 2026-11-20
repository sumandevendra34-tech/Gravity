package com.gravityos.assistant.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gravityos.assistant.MainActivity
import com.gravityos.assistant.R
import java.util.concurrent.atomic.AtomicBoolean

class GravityVoiceService : Service() {

    companion object {
        const val ACTION_START_WAKE_WORD = "com.gravityos.assistant.action.START_WAKE_WORD"
        const val ACTION_LISTEN_NOW = "com.gravityos.assistant.action.LISTEN_NOW"
        const val ACTION_STOP_SERVICE = "com.gravityos.assistant.action.STOP_SERVICE"

        private const val CHANNEL_ID = "gravity_voice_foreground"
        private const val NOTIFICATION_ID = 1001
        private const val FAR_FIELD_GAIN = 1.45f

        fun start(context: Context) {
            val intent = Intent(context, GravityVoiceService::class.java).apply {
                action = ACTION_START_WAKE_WORD
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun triggerListenNow(context: Context) {
            val intent = Intent(context, GravityVoiceService::class.java).apply {
                action = ACTION_LISTEN_NOW
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GravityVoiceService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GravityVoiceHub.ensureInitialized(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopListeningLoop()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_LISTEN_NOW -> {
                if (!hasMicPermission()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                promoteToForeground("GRAVITY OS • Listening...")
                GravityVoiceHub.requestManualListenFromService()
                if (!running.get()) {
                    startListeningLoop()
                }
                return START_STICKY
            }

            else -> {
                if (!hasMicPermission()) {
                    GravityVoiceHub.setWakeServiceRunning(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                promoteToForeground("Hey Gravity • Active in background")
                if (!running.get()) {
                    startListeningLoop()
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopListeningLoop()
        GravityVoiceHub.setWakeServiceRunning(false)
        super.onDestroy()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun promoteToForeground(statusText: String) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(statusText),
            fgsType
        )
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GravityOS::WakeWordCpuLock"
                )
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startListeningLoop() {
        if (!running.compareAndSet(false, true)) return
        acquireWakeLock()
        GravityVoiceHub.setWakeServiceRunning(true)

        workerThread = Thread({
            // Initialize offline engines on background thread
            val wakeReady = GravityVoiceHub.wakeWordEngine.initialize()
            GravityVoiceHub.sttEngine.initialize()
            GravityVoiceHub.ttsEngine.initialize()
            GravityVoiceHub.refreshDiagnosticsOnly()

            if (wakeReady) {
                updateNotification("Say \"Gravity\" anytime (Offline)")
            } else {
                updateNotification("Background active • Place wakeword models in /GravityOS/models/wakeword")
            }

            val minBuf = AudioRecord.getMinBufferSize(
                GravityVoiceHub.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    GravityVoiceHub.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 4
                )
            } catch (_: Throwable) {
                null
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                running.set(false)
                GravityVoiceHub.setWakeServiceRunning(false)
                releaseWakeLock()
                stopSelf()
                return@Thread
            }

            val chunkSamples = 1600 // 100ms
            val shortBuffer = ShortArray(chunkSamples)
            val floatBuffer = FloatArray(chunkSamples)

            try {
                recorder.startRecording()

                while (running.get()) {
                    // Priority 1: Manual tap on Gravity Orb inside app
                    if (GravityVoiceHub.consumeManualListenRequest()) {
                        handleActiveCommandCycle(recorder)
                        continue
                    }

                    val read = recorder.read(shortBuffer, 0, chunkSamples)
                    if (read <= 0) continue

                    // Skip wake-word feeding while Neural Voice is currently speaking
                    if (GravityVoiceHub.ttsEngine.isSpeaking()) continue

                    if (GravityVoiceHub.wakeWordEngine.isReady()) {
                        for (i in 0 until read) {
                            floatBuffer[i] = ((shortBuffer[i] / 32768.0f) * FAR_FIELD_GAIN).coerceIn(-1f, 1f)
                        }
                        val chunk = if (read == chunkSamples) floatBuffer else floatBuffer.copyOf(read)
                        val detectedKeyword = GravityVoiceHub.wakeWordEngine.processChunk(
                            chunk,
                            GravityVoiceHub.SAMPLE_RATE
                        )
                        if (!detectedKeyword.isNullOrBlank()) {
                            handleActiveCommandCycle(recorder)
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    recorder.stop()
                } catch (_: Throwable) {
                }
                recorder.release()
                running.set(false)
                releaseWakeLock()
                GravityVoiceHub.setWakeServiceRunning(false)
            }
        }, "GravityWakeWordThread").apply {
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    private fun handleActiveCommandCycle(recorder: AudioRecord) {
        updateNotification("Gravity is listening...")
        val needFollowUp = GravityVoiceHub.captureAndHandleCommand(
            context = applicationContext,
            recorder = recorder,
            allowFollowUp = true
        )
        // Hands-free follow-up if Gravity asked to confirm a contact or app option
        if (needFollowUp && running.get()) {
            updateNotification("Waiting for your confirmation...")
            GravityVoiceHub.captureAndHandleCommand(
                context = applicationContext,
                recorder = recorder,
                allowFollowUp = false
            )
        }
        updateNotification(
            if (GravityVoiceHub.wakeWordEngine.isReady()) {
                "Say \"Gravity\" anytime (Offline)"
            } else {
                "Background active • Tap Orb to speak"
            }
        )
    }

    private fun stopListeningLoop() {
        running.set(false)
        GravityVoiceHub.interruptSpeech()
        try {
            workerThread?.join(600)
        } catch (_: InterruptedException) {
        }
        workerThread = null
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GRAVITY OS Voice Listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active microphone indicator for offline \"Gravity\" wake word detection"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Throwable) {
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GravityVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GRAVITY OS")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "Stop Listening", stopPending)
            .build()
    }
}
