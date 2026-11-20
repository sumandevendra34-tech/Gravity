package com.gravityos.assistant.voice

import android.os.Environment
import java.io.File

class OfflineSpeechRecognizer {

    private val sttDir: File
        get() = File(Environment.getExternalStorageDirectory(), "GravityOS/models/stt")

    @Volatile
    private var recognizerHandle: Any? = null

    @Volatile
    var statusMessage: String = "Checking Offline STT..."
        private set

    fun isReady(): Boolean = recognizerHandle != null

    @Synchronized
    fun initialize(): Boolean {
        if (recognizerHandle != null) return true

        if (!SherpaReflectionBridge.isSherpaAarPresent()) {
            statusMessage = "app/libs/ me sherpa-onnx.aar add karein"
            return false
        }

        if (!sttDir.exists()) {
            statusMessage = "/GravityOS/models/stt/ folder missing hai"
            return false
        }

        val files = sttDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val tokens = files.firstOrNull { it.name.equals("tokens.txt", ignoreCase = true) }
        if (tokens == null) {
            statusMessage = "/GravityOS/models/stt/ me tokens.txt aur .onnx models rakhein"
            return false
        }

        val encoder = files.firstOrNull { it.name.contains("encoder", true) && it.name.endsWith(".onnx", true) }
        val decoder = files.firstOrNull { it.name.contains("decoder", true) && it.name.endsWith(".onnx", true) }
        val joiner = files.firstOrNull { it.name.contains("joiner", true) && it.name.endsWith(".onnx", true) }
        val singleModel = files.firstOrNull { it.name.endsWith(".onnx", true) }

        val modelConfig = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            ?: return failInit("OfflineModelConfig load nahi hua")

        SherpaReflectionBridge.setProperty(modelConfig, "tokens", tokens.absolutePath)
        SherpaReflectionBridge.setProperty(modelConfig, "numThreads", 2)
        SherpaReflectionBridge.setProperty(modelConfig, "provider", "cpu")

        val detectedType: String = when {
            encoder != null && decoder != null && joiner != null -> {
                val transducer = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig")
                    ?: return failInit("Transducer config nahi mila")
                SherpaReflectionBridge.setProperty(transducer, "encoder", encoder.absolutePath)
                SherpaReflectionBridge.setProperty(transducer, "decoder", decoder.absolutePath)
                SherpaReflectionBridge.setProperty(transducer, "joiner", joiner.absolutePath)
                SherpaReflectionBridge.setProperty(modelConfig, "transducer", transducer)
                "Transducer"
            }

            encoder != null && decoder != null -> {
                // Whisper multilingual (Hindi + English + Hinglish)
                val whisper = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig")
                    ?: return failInit("Whisper config nahi mila")
                SherpaReflectionBridge.setProperty(whisper, "encoder", encoder.absolutePath)
                SherpaReflectionBridge.setProperty(whisper, "decoder", decoder.absolutePath)
                SherpaReflectionBridge.setProperty(whisper, "language", "")
                SherpaReflectionBridge.setProperty(whisper, "task", "transcribe")
                SherpaReflectionBridge.setProperty(modelConfig, "whisper", whisper)
                "Whisper Multilingual"
            }

            singleModel != null -> {
                val senseVoice = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig")
                    ?: return failInit("SenseVoice config nahi mila")
                SherpaReflectionBridge.setProperty(senseVoice, "model", singleModel.absolutePath)
                SherpaReflectionBridge.setProperty(senseVoice, "language", "")
                SherpaReflectionBridge.setProperty(senseVoice, "useInverseTextNormalization", true)
                SherpaReflectionBridge.setProperty(modelConfig, "senseVoice", senseVoice)
                "SenseVoice"
            }

            else -> return failInit("/GravityOS/models/stt/ me koi .onnx model nahi mila")
        }

        val recognizerConfig = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            ?: return failInit("OfflineRecognizerConfig load nahi hua")

        SherpaReflectionBridge.setProperty(recognizerConfig, "modelConfig", modelConfig)

        val engine = SherpaReflectionBridge.createWithAssetManager(
            "com.k2fsa.sherpa.onnx.OfflineRecognizer",
            recognizerConfig
        ) ?: return failInit("Offline STT initialize nahi ho paya")

        recognizerHandle = engine
        statusMessage = "Offline STT Ready ($detectedType)"
        return true
    }

    @Synchronized
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String? {
        if (samples.isEmpty()) return null
        if (recognizerHandle == null && !initialize()) return null
        val engine = recognizerHandle ?: return null

        return try {
            val stream = SherpaReflectionBridge.invoke(engine, "createStream") ?: return null
            SherpaReflectionBridge.invoke(stream, "acceptWaveform", samples, sampleRate)
            SherpaReflectionBridge.invoke(engine, "decode", stream)
            val result = SherpaReflectionBridge.invoke(engine, "getResult", stream)
            val text = (result?.let { SherpaReflectionBridge.getProperty(it, "text") } as? String)?.trim()
            SherpaReflectionBridge.invoke(stream, "release")
            text?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun release() {
        recognizerHandle?.let { SherpaReflectionBridge.invoke(it, "release") }
        recognizerHandle = null
    }

    private fun failInit(reason: String): Boolean {
        statusMessage = reason
        return false
    }
}
