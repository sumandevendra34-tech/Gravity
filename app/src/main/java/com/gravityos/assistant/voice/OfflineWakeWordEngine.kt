package com.gravityos.assistant.voice

import android.os.Environment
import java.io.File

class OfflineWakeWordEngine {

    private val wakeDir: File
        get() = File(Environment.getExternalStorageDirectory(), "GravityOS/models/wakeword")

    @Volatile
    private var spotterHandle: Any? = null

    @Volatile
    private var streamHandle: Any? = null

    @Volatile
    var statusMessage: String = "Checking Wake Word..."
        private set

    fun isReady(): Boolean = spotterHandle != null && streamHandle != null

    @Synchronized
    fun initialize(): Boolean {
        if (isReady()) return true

        if (!SherpaReflectionBridge.isSherpaAarPresent()) {
            statusMessage = "app/libs/ me sherpa-onnx.aar add karein"
            return false
        }

        if (!wakeDir.exists()) {
            statusMessage = "/GravityOS/models/wakeword/ folder missing hai"
            return false
        }

        val files = wakeDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val encoder = files.firstOrNull { it.name.contains("encoder", true) && it.name.endsWith(".onnx", true) }
        val decoder = files.firstOrNull { it.name.contains("decoder", true) && it.name.endsWith(".onnx", true) }
        val joiner = files.firstOrNull { it.name.contains("joiner", true) && it.name.endsWith(".onnx", true) }
        val tokens = files.firstOrNull { it.name.equals("tokens.txt", ignoreCase = true) }
        val keywords = files.firstOrNull { it.name.equals("keywords.txt", ignoreCase = true) }

        if (encoder == null || decoder == null || joiner == null || tokens == null || keywords == null) {
            statusMessage = "/GravityOS/models/wakeword/ me encoder, decoder, joiner, tokens.txt aur keywords.txt rakhein"
            return false
        }

        val transducer = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig")
            ?: return failInit("OnlineTransducerModelConfig load nahi hua")

        SherpaReflectionBridge.setProperty(transducer, "encoder", encoder.absolutePath)
        SherpaReflectionBridge.setProperty(transducer, "decoder", decoder.absolutePath)
        SherpaReflectionBridge.setProperty(transducer, "joiner", joiner.absolutePath)

        val onlineModel = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.OnlineModelConfig")
            ?: return failInit("OnlineModelConfig load nahi hua")

        SherpaReflectionBridge.setProperty(onlineModel, "transducer", transducer)
        SherpaReflectionBridge.setProperty(onlineModel, "tokens", tokens.absolutePath)
        SherpaReflectionBridge.setProperty(onlineModel, "numThreads", 2)
        SherpaReflectionBridge.setProperty(onlineModel, "provider", "cpu")

        val kwsConfig = SherpaReflectionBridge.newInstance("com.k2fsa.sherpa.onnx.KeywordSpotterConfig")
            ?: return failInit("KeywordSpotterConfig load nahi hua")

        SherpaReflectionBridge.setProperty(kwsConfig, "modelConfig", onlineModel)
        SherpaReflectionBridge.setProperty(kwsConfig, "keywordsFile", keywords.absolutePath)
        SherpaReflectionBridge.setProperty(kwsConfig, "keywordsScore", 1.5f)
        SherpaReflectionBridge.setProperty(kwsConfig, "keywordsThreshold", 0.22f)

        val spotter = SherpaReflectionBridge.createWithAssetManager(
            "com.k2fsa.sherpa.onnx.KeywordSpotter",
            kwsConfig
        ) ?: return failInit("Wake Word Engine start nahi hua (keywords.txt check karein)")

        val stream = SherpaReflectionBridge.invoke(spotter, "createStream", "")
            ?: SherpaReflectionBridge.invoke(spotter, "createStream")
            ?: return failInit("Wake Word stream create nahi hui")

        spotterHandle = spotter
        streamHandle = stream
        statusMessage = "Wake Word Active (\"Gravity\")"
        return true
    }

    @Synchronized
    fun processChunk(samples: FloatArray, sampleRate: Int = 16000): String? {
        val spotter = spotterHandle ?: return null
        val stream = streamHandle ?: return null

        return try {
            SherpaReflectionBridge.invoke(stream, "acceptWaveform", samples, sampleRate)
            while (SherpaReflectionBridge.invoke(spotter, "isReady", stream) == true) {
                SherpaReflectionBridge.invoke(spotter, "decode", stream)
            }
            val result = SherpaReflectionBridge.invoke(spotter, "getResult", stream)
            val keyword = (result?.let { SherpaReflectionBridge.getProperty(it, "keyword") } as? String)?.trim()
            if (!keyword.isNullOrEmpty()) {
                SherpaReflectionBridge.invoke(spotter, "reset", stream)
                keyword
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun release() {
        streamHandle?.let { SherpaReflectionBridge.invoke(it, "release") }
        spotterHandle?.let { SherpaReflectionBridge.invoke(it, "release") }
        streamHandle = null
        spotterHandle = null
    }

    private fun failInit(reason: String): Boolean {
        statusMessage = reason
        return false
    }
}
