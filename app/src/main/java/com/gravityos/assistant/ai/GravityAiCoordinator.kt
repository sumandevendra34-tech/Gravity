package com.gravityos.assistant.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gravityos.assistant.security.AiMode
import com.gravityos.assistant.security.OnlineProviderPreference
import com.gravityos.assistant.security.SecureVault

class GravityAiCoordinator(private val context: Context) {

    val vault = SecureVault(context.applicationContext)
    val onlineEngine = OnlineAiEngine()
    val offlineEngine = OfflineLlmEngine(context.applicationContext)
    val ocrEngine = OfflineOcrEngine()

    fun isInternetAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    fun askText(prompt: String, history: List<AiHistoryTurn>): AiCallResult {
        val mode = vault.getAiMode()
        val hasNet = isInternetAvailable()

        if (mode == AiMode.OFFLINE || (mode == AiMode.AUTO && !hasNet)) {
            return offlineEngine.generateReply(prompt)
        }

        if (mode == AiMode.ONLINE && !hasNet) {
            return AiCallResult.Error("Internet band hai. Online mode ke liye data/Wi-Fi on karein ya Auto/Offline mode chunein.")
        }

        val groqKey = vault.getGroqApiKey()
        val geminiKey = vault.getGeminiApiKey()
        val pref = vault.getOnlineProvider()

        val onlineAttempt: AiCallResult? = when (pref) {
            OnlineProviderPreference.GROQ_FIRST -> {
                if (groqKey.isNotBlank()) {
                    val res = onlineEngine.askGroq(groqKey, prompt, history)
                    if (res is AiCallResult.Success || geminiKey.isBlank()) res
                    else onlineEngine.askGemini(geminiKey, prompt, history)
                } else if (geminiKey.isNotBlank()) {
                    onlineEngine.askGemini(geminiKey, prompt, history)
                } else null
            }

            OnlineProviderPreference.GEMINI_FIRST -> {
                if (geminiKey.isNotBlank()) {
                    val res = onlineEngine.askGemini(geminiKey, prompt, history)
                    if (res is AiCallResult.Success || groqKey.isBlank()) res
                    else onlineEngine.askGroq(groqKey, prompt, history)
                } else if (groqKey.isNotBlank()) {
                    onlineEngine.askGroq(groqKey, prompt, history)
                } else null
            }
        }

        return when {
            onlineAttempt is AiCallResult.Success -> onlineAttempt
            mode == AiMode.ONLINE -> onlineAttempt
                ?: AiCallResult.Error("Setup me apni free Groq ya Gemini API key save karein.")
            else -> {
                // AUTO mode graceful fallback to Offline Core
                val offlineRes = offlineEngine.generateReply(prompt)
                if (onlineAttempt is AiCallResult.Error && offlineRes is AiCallResult.Success) {
                    AiCallResult.Success(
                        reply = "${offlineRes.reply}\n\n(Online note: ${onlineAttempt.message})",
                        providerName = offlineRes.providerName
                    )
                } else {
                    offlineRes
                }
            }
        }
    }

    suspend fun askWithImage(
        bitmap: Bitmap,
        userPrompt: String,
        history: List<AiHistoryTurn>
    ): AiCallResult {
        val cleanPrompt = userPrompt.trim().ifEmpty {
            "Is image ko samjhao. Agar koi study question, equation ya text hai to step-by-step solve karo."
        }

        val mode = vault.getAiMode()
        val hasNet = isInternetAvailable()
        val geminiKey = vault.getGeminiApiKey()
        val groqKey = vault.getGroqApiKey()

        // 1. Best experience: Online Gemini 2.5 Flash Vision (handwritten math, diagrams, tables, OCR)
        if (mode != AiMode.OFFLINE && hasNet && geminiKey.isNotBlank()) {
            val visionResult = onlineEngine.askGemini(geminiKey, cleanPrompt, history, bitmap)
            if (visionResult is AiCallResult.Success) return visionResult
        }

        // 2. Always-available Offline OCR (Hindi + English)
        val extractedText = ocrEngine.extractTextOffline(bitmap)

        // 3. If Groq key is set and internet is available, send OCR text to Groq Llama 3.3
        if (mode != AiMode.OFFLINE && hasNet && groqKey.isNotBlank() && extractedText.isNotBlank()) {
            val combinedPrompt = "Image se mila text:\n\"$extractedText\"\n\nUser ka sawal: $cleanPrompt"
            val groqRes = onlineEngine.askGroq(groqKey, combinedPrompt, history)
            if (groqRes is AiCallResult.Success) {
                return AiCallResult.Success(groqRes.reply, "Offline OCR + Groq")
            }
        }

        // 4. Pure Offline OCR result
        return if (extractedText.isNotBlank()) {
            AiCallResult.Success(
                reply = "Offline OCR se mila text:\n\n$extractedText",
                providerName = "Offline OCR"
            )
        } else {
            AiCallResult.Error(
                "Offline OCR me koi clear text nahi mila. Diagrams ya handwritten math ke liye Setup me free Gemini API key add karein."
            )
        }
    }
}
