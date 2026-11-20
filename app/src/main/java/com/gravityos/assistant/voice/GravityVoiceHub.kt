package com.gravityos.assistant.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.gravityos.assistant.core.AppLauncherManager
import com.gravityos.assistant.core.CommandRouter
import com.gravityos.assistant.core.ContactCallManager
import com.gravityos.assistant.core.PendingAction
import com.gravityos.assistant.core.PhoneControlManager
import com.gravityos.assistant.ui.components.OrbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class VoiceEngineDiagnostics(
    val sherpaAarLoaded: Boolean = false,
    val wakeWordReady: Boolean = false,
    val wakeWordStatus: String = "Not initialized",
    val sttReady: Boolean = false,
    val sttStatus: String = "Not initialized",
    val ttsReady: Boolean = false,
    val ttsStatus: String = "Not initialized"
)

data class VoiceRuntimeState(
    val orbState: OrbState = OrbState.IDLE,
    val voiceAmplitude: Float = 0f,
    val isWakeServiceRunning: Boolean = false,
    val statusSubtitle: String = "Hello • Offline Core Ready",
    val latestReply: String? = null,
    val pendingAction: PendingAction? = null,
    val voiceSpeed: Float = 1.0f,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "Main GRAVITY OS hoon. Mujhse koi bhi app khulwao, kisi bhi contact ko direct call lagwao, ya Torch, Volume aur Settings control karo.",
            isUser = false
        )
    ),
    val diagnostics: VoiceEngineDiagnostics = VoiceEngineDiagnostics()
)

object GravityVoiceHub {

    const val SAMPLE_RATE = 16000
    private const val FAR_FIELD_GAIN = 1.45f
    private const val SPEECH_START_RMS = 0.016f
    private const val SILENCE_RMS = 0.010f
    private const val MAX_WAIT_FOR_SPEECH_MS = 4000L
    private const val SILENCE_STOP_MS = 1250L
    private const val MAX_RECORD_MS = 8500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(VoiceRuntimeState())
    val state: StateFlow<VoiceRuntimeState> = _state.asStateFlow()

    val wakeWordEngine = OfflineWakeWordEngine()
    val sttEngine = OfflineSpeechRecognizer()
    val ttsEngine = OfflineNeuralTtsEngine()

    @Volatile
    private var commandRouter: CommandRouter? = null

    private val singleShotBusy = AtomicBoolean(false)
    private val manualListenPending = AtomicBoolean(false)
    private val cancelCaptureRequested = AtomicBoolean(false)

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (commandRouter == null) {
            val appCtx = context.applicationContext
            commandRouter = CommandRouter(
                contactCallManager = ContactCallManager(appCtx),
                appLauncherManager = AppLauncherManager(appCtx),
                phoneControlManager = PhoneControlManager(appCtx)
            )
            refreshDiagnosticsOnly()
        }
    }

    fun reloadAllOfflineModels(context: Context) {
        ensureInitialized(context)
        scope.launch {
            _state.update { it.copy(statusSubtitle = "Loading offline models...") }
            wakeWordEngine.release()
            sttEngine.release()
            ttsEngine.release()

            wakeWordEngine.initialize()
            sttEngine.initialize()
            ttsEngine.initialize()

            refreshDiagnosticsOnly()
            _state.update {
                it.copy(
                    statusSubtitle = if (it.isWakeServiceRunning) {
                        "Hey Gravity • Active in Background"
                    } else {
                        "Offline Core Ready"
                    }
                )
            }
        }
    }

    fun refreshDiagnosticsOnly() {
        _state.update { current ->
            current.copy(
                diagnostics = VoiceEngineDiagnostics(
                    sherpaAarLoaded = SherpaReflectionBridge.isSherpaAarPresent(),
                    wakeWordReady = wakeWordEngine.isReady(),
                    wakeWordStatus = wakeWordEngine.statusMessage,
                    sttReady = sttEngine.isReady(),
                    sttStatus = sttEngine.statusMessage,
                    ttsReady = ttsEngine.isReady(),
                    ttsStatus = ttsEngine.statusMessage
                )
            )
        }
    }

    fun setWakeServiceRunning(running: Boolean) {
        _state.update {
            it.copy(
                isWakeServiceRunning = running,
                statusSubtitle = if (running) {
                    "Listening for \"Gravity\" in background"
                } else {
                    "Hello • Offline Core Ready"
                },
                orbState = if (!running && it.orbState == OrbState.LISTENING) OrbState.IDLE else it.orbState,
                voiceAmplitude = if (!running) 0f else it.voiceAmplitude
            )
        }
    }

    fun setVoiceSpeed(speed: Float) {
        _state.update { it.copy(voiceSpeed = speed.coerceIn(0.7f, 1.4f)) }
    }

    fun interruptSpeech() {
        ttsEngine.stop()
        cancelCaptureRequested.set(true)
        _state.update {
            it.copy(
                orbState = OrbState.IDLE,
                voiceAmplitude = 0f
            )
        }
    }

    fun requestManualListenFromService() {
        ttsEngine.stop()
        manualListenPending.set(true)
    }

    fun consumeManualListenRequest(): Boolean {
        return manualListenPending.getAndSet(false)
    }

    fun clearCancelCapture() {
        cancelCaptureRequested.set(false)
    }

    fun isCancelCaptureRequested(): Boolean = cancelCaptureRequested.get()

    fun clearChat() {
        ttsEngine.stop()
        _state.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        text = "Nayi conversation shuru ho gayi hai. Boliye kya karna hai?",
                        isUser = false
                    )
                ),
                latestReply = null,
                pendingAction = null,
                orbState = OrbState.IDLE,
                voiceAmplitude = 0f
            )
        }
    }

    fun cancelPendingAction() {
        ttsEngine.stop()
        _state.update {
            it.copy(
                pendingAction = null,
                latestReply = "Cancel kar diya gaya.",
                orbState = OrbState.IDLE
            )
        }
    }

    fun testNeuralVoice(context: Context) {
        ensureInitialized(context)
        scope.launch {
            if (!ttsEngine.isReady()) {
                ttsEngine.initialize()
                refreshDiagnosticsOnly()
            }
            if (!ttsEngine.isReady()) {
                _state.update {
                    it.copy(
                        latestReply = "Neural Voice ready nahi hai: ${ttsEngine.statusMessage}",
                        statusSubtitle = ttsEngine.statusMessage
                    )
                }
                return@launch
            }
            speakReplyBlocking("Namaste! Main Gravity hoon. Mera offline neural voice system bilkul ready hai.")
        }
    }

    fun processCommandText(
        context: Context,
        rawText: String,
        speakResponse: Boolean = true,
        allowHandsFreeFollowUp: Boolean = false
    ): Boolean {
        ensureInitialized(context)
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return false

        val router = commandRouter ?: return false
        val activePending = _state.value.pendingAction

        _state.update {
            it.copy(
                orbState = OrbState.THINKING,
                voiceAmplitude = 0f,
                statusSubtitle = "Processing...",
                messages = it.messages + ChatMessage(text = trimmed, isUser = true)
            )
        }

        val result = router.processCommand(trimmed, activePending)

        _state.update {
            it.copy(
                latestReply = result.reply,
                pendingAction = result.pendingAction,
                messages = it.messages + ChatMessage(text = result.reply, isUser = false)
            )
        }

        if (speakResponse) {
            speakReplyBlocking(result.reply)
        } else {
            _state.update {
                it.copy(
                    orbState = OrbState.IDLE,
                    voiceAmplitude = 0f,
                    statusSubtitle = if (it.isWakeServiceRunning) {
                        "Listening for \"Gravity\""
                    } else {
                        "GRAVITY OS"
                    }
                )
            }
        }

        // Return true if Gravity asked a confirmation question and needs a hands-free reply
        return allowHandsFreeFollowUp && result.pendingAction != null
    }

    fun speakReplyBlocking(text: String) {
        if (!ttsEngine.isReady()) {
            ttsEngine.initialize()
            refreshDiagnosticsOnly()
        }

        _state.update {
            it.copy(
                orbState = OrbState.SPEAKING,
                statusSubtitle = "Gravity Speaking..."
            )
        }

        val speed = _state.value.voiceSpeed
        ttsEngine.speak(
            text = text,
            speed = speed,
            onAmplitude = { amp ->
                _state.update { it.copy(voiceAmplitude = amp) }
            },
            onFinished = {
                _state.update {
                    it.copy(
                        orbState = OrbState.IDLE,
                        voiceAmplitude = 0f,
                        statusSubtitle = if (it.isWakeServiceRunning) {
                            "Listening for \"Gravity\""
                        } else {
                            "GRAVITY OS"
                        }
                    )
                }
            }
        )
    }

    /**
     * Used when the user taps the Orb while the background service is OFF.
     */
    fun triggerSingleShotListen(context: Context) {
        ensureInitialized(context)

        if (ttsEngine.isSpeaking()) {
            interruptSpeech()
            return
        }

        if (_state.value.isWakeServiceRunning) {
            requestManualListenFromService()
            return
        }

        if (!singleShotBusy.compareAndSet(false, true)) {
            cancelCaptureRequested.set(true)
            return
        }

        scope.launch {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    _state.update {
                        it.copy(
                            latestReply = "Microphone permission chahiye. Setup me jakar allow karein.",
                            orbState = OrbState.IDLE
                        )
                    }
                    return@launch
                }

                if (!sttEngine.isReady()) {
                    sttEngine.initialize()
                    refreshDiagnosticsOnly()
                }
                if (!sttEngine.isReady()) {
                    _state.update {
                        it.copy(
                            latestReply = "Offline Voice model ready nahi hai: ${sttEngine.statusMessage}",
                            statusSubtitle = sttEngine.statusMessage,
                            orbState = OrbState.IDLE
                        )
                    }
                    SoundCuePlayer.playSoftErrorCue()
                    return@launch
                }

                runSingleShotCaptureSession(context)
            } finally {
                singleShotBusy.set(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun runSingleShotCaptureSession(context: Context) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4
            )
        } catch (_: Throwable) {
            null
        } ?: return

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        try {
            recorder.startRecording()
            var followUpNeeded = captureAndHandleCommand(context, recorder, allowFollowUp = true)
            if (followUpNeeded) {
                captureAndHandleCommand(context, recorder, allowFollowUp = false)
            }
        } catch (_: Throwable) {
        } finally {
            try {
                recorder.stop()
            } catch (_: Throwable) {
            }
            recorder.release()
            _state.update { it.copy(orbState = OrbState.IDLE, voiceAmplitude = 0f) }
        }
    }

    /**
     * Captures one spoken command using an already-running AudioRecord instance
     * (shared by both GravityVoiceService and single-shot mode).
     */
    fun captureAndHandleCommand(
        context: Context,
        recorder: AudioRecord,
        allowFollowUp: Boolean
    ): Boolean {
        clearCancelCapture()
        ttsEngine.stop()

        _state.update {
            it.copy(
                orbState = OrbState.LISTENING,
                voiceAmplitude = 0.25f,
                statusSubtitle = "Listening..."
            )
        }

        SoundCuePlayer.playWakeChime()
        // Brief pause so the wake chime does not bleed into the speech recording
        try {
            Thread.sleep(270)
        } catch (_: InterruptedException) {
            return false
        }

        val frameSamples = 1600 // 100ms at 16kHz
        val shortBuf = ShortArray(frameSamples)
        // Drain stale audio buffered during chime
        recorder.read(shortBuf, 0, frameSamples)

        val collected = ArrayList<Float>(SAMPLE_RATE * 5)
        var speechDetected = false
        var silenceAccumMs = 0L
        var totalElapsedMs = 0L

        while (totalElapsedMs < MAX_RECORD_MS && !isCancelCaptureRequested()) {
            val read = recorder.read(shortBuf, 0, frameSamples)
            if (read <= 0) continue

            val frameMs = (read * 1000L) / SAMPLE_RATE
            totalElapsedMs += frameMs

            var sumSq = 0.0
            for (i in 0 until read) {
                val normalized = ((shortBuf[i] / 32768.0f) * FAR_FIELD_GAIN).coerceIn(-1f, 1f)
                collected.add(normalized)
                sumSq += (normalized * normalized).toDouble()
            }

            val rms = sqrt(sumSq / read).toFloat()
            val visualAmp = (rms * 7.5f).coerceIn(0.08f, 1f)
            _state.update { it.copy(voiceAmplitude = visualAmp) }

            if (rms >= SPEECH_START_RMS) {
                speechDetected = true
                silenceAccumMs = 0L
            } else if (speechDetected && rms < SILENCE_RMS) {
                silenceAccumMs += frameMs
                if (silenceAccumMs >= SILENCE_STOP_MS) {
                    break
                }
            } else if (!speechDetected && totalElapsedMs >= MAX_WAIT_FOR_SPEECH_MS) {
                break
            }
        }

        if (!speechDetected || collected.size < SAMPLE_RATE / 3 || isCancelCaptureRequested()) {
            _state.update {
                it.copy(
                    orbState = OrbState.IDLE,
                    voiceAmplitude = 0f,
                    statusSubtitle = if (it.isWakeServiceRunning) {
                        "Listening for \"Gravity\""
                    } else {
                        "Kuch sunai nahi diya — dobara try karein"
                    }
                )
            }
            return false
        }

        _state.update {
            it.copy(
                orbState = OrbState.THINKING,
                voiceAmplitude = 0f,
                statusSubtitle = "Recognizing offline..."
            )
        }

        if (!sttEngine.isReady()) {
            sttEngine.initialize()
            refreshDiagnosticsOnly()
        }

        val pcmArray = FloatArray(collected.size) { collected[it] }
        val transcript = sttEngine.transcribe(pcmArray, SAMPLE_RATE)

        if (transcript.isNullOrBlank()) {
            val msg = if (!sttEngine.isReady()) {
                "Offline STT model nahi mila: ${sttEngine.statusMessage}"
            } else {
                "Maaf kijiye, awaaz clear samajh nahi aayi. Dobara boliye."
            }
            _state.update {
                it.copy(
                    latestReply = msg,
                    orbState = OrbState.IDLE,
                    statusSubtitle = if (it.isWakeServiceRunning) "Listening for \"Gravity\"" else "GRAVITY OS"
                )
            }
            if (ttsEngine.isReady()) {
                speakReplyBlocking(msg)
            } else {
                SoundCuePlayer.playSoftErrorCue()
            }
            return false
        }

        return processCommandText(
            context = context,
            rawText = transcript,
            speakResponse = true,
            allowHandsFreeFollowUp = allowFollowUp
        )
    }

    fun launchTypedCommand(context: Context, text: String) {
        scope.launch {
            processCommandText(
                context = context,
                rawText = text,
                speakResponse = true,
                allowHandsFreeFollowUp = false
            )
        }
    }

    fun scheduleIdleReset() {
        scope.launch {
            delay(400)
            if (!ttsEngine.isSpeaking()) {
                _state.update { it.copy(orbState = OrbState.IDLE, voiceAmplitude = 0f) }
            }
        }
    }
}
