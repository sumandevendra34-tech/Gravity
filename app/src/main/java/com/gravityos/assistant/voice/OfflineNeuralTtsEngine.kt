package com.gravityos.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class OfflineNeuralTtsEngine {

    private val ttsDir: File
        get() = File(Environment.getExternalStorageDirectory(), "GravityOS/models/tts")

    @Volatile
    private var ttsHandle: Any? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    private val cancelled = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)

    @Volatile
    var statusMessage: String = "Checking Neural Voice..."
        private set

    fun isReady(): Boolean = ttsHandle != null

    fun isSpeaking(): Boolean = speaking.get()

    @Synchronized
    fun initialize(): Boolean {
        if (ttsHandle != null) return true

        if (!SherpaReflectionBridge.isSherpaAarPresent()) {
            statusMessage = "app/libs/ me sherpa-onnx.aar add karein"
            return false
        }

        if (!ttsDir.exists()) {
            statusMessage = "/GravityOS/models/tts/ folder missing hai"
            return false
        }

        val onnxFile = ttsDir.listFiles()?.firstOrNull {
            it.isFile && it.name.endsWith(".onnx", ignoreCase = true)
        }
        val tokensFile = File(ttsDir, "tokens.txt")

        if (onnxFile == null || !tokensFile.exists()) {
            statusMessage = "/GravityOS/models/tts/ me .onnx + tokens.txt rakhein"
            return false
        }

        val espeakDir = File(ttsDir, "espeak-ng-data")
        val lexiconFile = File(ttsDir, "lexicon.txt")

        val vitsConfig = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig")
            ?: return failInit("VITS config load nahi hua")

        SherpaReflectionBridge.setProperty(vitsConfig, "model", onnxFile.absolutePath)
        SherpaReflectionBridge.setProperty(vitsConfig, "tokens", tokensFile.absolutePath)
        if (espeakDir.exists() && espeakDir.isDirectory) {
            SherpaReflectionBridge.setProperty(vitsConfig, "dataDir", espeakDir.absolutePath)
        }
        if (lexiconFile.exists()) {
            SherpaReflectionBridge.setProperty(vitsConfig, "lexicon", lexiconFile.absolutePath)
        }

        val modelConfig = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineTtsModelConfig")
            ?: return failInit("TTS model config load nahi hua")

        SherpaReflectionBridge.setProperty(modelConfig, "vits", vitsConfig)
        SherpaReflectionBridge.setProperty(modelConfig, "numThreads", 2)
        SherpaReflectionBridge.setProperty(modelConfig, "provider", "cpu")

        val ttsConfig = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            ?: return failInit("TTS root config load nahi hua")

        SherpaReflectionBridge.setProperty(ttsConfig, "model", modelConfig)

        val engine = SherpaReflectionBridge.createWithAssetManager(
            "com.k2fsa.sherpa.onnx.OfflineTts",
            ttsConfig
        ) ?: return failInit("Neural Voice initialize nahi ho paya (model check karein)")

        ttsHandle = engine
        statusMessage = "Neural Voice Ready (${onnxFile.nameWithoutExtension})"
        return true
    }

    fun speak(
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = 0,
        onAmplitude: (Float) -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        stop()

        val cleanText = text
            .replace(Regex("[*#_`~]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanText.isEmpty()) {
            onFinished()
            return
        }

        if (ttsHandle == null && !initialize()) {
            // Strict rule: Never fall back to Android default system TTS
            SoundCuePlayer.playCommandDoneCue()
            onFinished()
            return
        }

        val engine = ttsHandle ?: run {
            onFinished()
            return
        }

        cancelled.set(false)
        speaking.set(true)

        try {
            val sentences = splitIntoSentences(cleanText)
            val clampedSpeed = speed.coerceIn(0.6f, 1.6f)

            for (sentence in sentences) {
                if (cancelled.get()) break
                val generated = SherpaReflectionBridge.invoke(
                    engine,
                    "generate",
                    sentence,
                    speakerId,
                    clampedSpeed
                ) ?: continue

                val samples = SherpaReflectionBridge.getProperty(generated, "samples") as? FloatArray
                    ?: continue
                val sampleRate = (SherpaReflectionBridge.getProperty(generated, "sampleRate") as? Int)
                    ?: 22050

                if (samples.isNotEmpty() && !cancelled.get()) {
                    playFloatPcm(samples, sampleRate, onAmplitude)
                }
            }
        } catch (_: Throwable) {
        } finally {
            speaking.set(false)
            onAmplitude(0f)
            onFinished()
        }
    }

    fun stop() {
        cancelled.set(true)
        speaking.set(false)
        try {
            activeTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.stop()
                track.release()
            }
        } catch (_: Throwable) {
        } finally {
            activeTrack = null
        }
    }

    fun release() {
        stop()
        ttsHandle?.let { SherpaReflectionBridge.invoke(it, "release") }
        ttsHandle = null
    }

    private fun playFloatPcm(
        samples: FloatArray,
        sampleRate: Int,
        onAmplitude: (Float) -> Unit
    ) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        track.play()

        val chunkSize = 1024
        var idx = 0
        while (idx < samples.size && !cancelled.get()) {
            val len = minOf(chunkSize, samples.size - idx)
            var sumSq = 0.0
            for (i in idx until (idx + len)) {
                val s = samples[i].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / len).toFloat()
            onAmplitude((rms * 4.2f).coerceIn(0f, 1f))

            track.write(samples, idx, len, AudioTrack.WRITE_BLOCKING)
            idx += len
        }

        try {
            track.stop()
            track.release()
        } catch (_: Throwable) {
        }
        if (activeTrack === track) {
            activeTrack = null
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val parts = text.split(Regex("(?<=[.!?।\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parts.isEmpty()) listOf(text) else parts
    }

    private fun failInit(reason: String): Boolean {
        statusMessage = reason
        return false
    }
}
