package com.gravityos.assistant.ai

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AiHistoryTurn(
    val text: String,
    val isUser: Boolean
)

sealed class AiCallResult {
    data class Success(val reply: String, val providerName: String) : AiCallResult()
    data class Error(val message: String) : AiCallResult()
}

class OnlineAiEngine {

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "llama-3.3-70b-versatile"
        private const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        private const val SYSTEM_PROMPT =
            "You are GRAVITY OS, a smart, fast personal Android AI assistant for an Indian user. " +
                "Reply naturally in the same language the user uses (Hindi, English, or Hinglish). " +
                "Keep voice-friendly answers clear and concise unless the user asks for detailed study notes, code, or step-by-step math/science solutions."
    }

    fun askGroq(
        apiKey: String,
        prompt: String,
        history: List<AiHistoryTurn>
    ): AiCallResult {
        if (apiKey.isBlank()) return AiCallResult.Error("Groq API key set nahi hai.")

        return try {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                history.takeLast(8).forEach { turn ->
                    put(
                        JSONObject()
                            .put("role", if (turn.isUser) "user" else "assistant")
                            .put("content", turn.text)
                    )
                }
                put(JSONObject().put("role", "user").put("content", prompt))
            }

            val payload = JSONObject()
                .put("model", GROQ_MODEL)
                .put("messages", messages)
                .put("temperature", 0.6)
                .put("max_tokens", 1024)

            val responseText = postJson(
                urlString = GROQ_URL,
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                ),
                body = payload.toString()
            )

            val root = JSONObject(responseText)
            val content = root
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()

            if (!content.isNullOrEmpty()) {
                AiCallResult.Success(content, "Groq Llama 3.3")
            } else {
                AiCallResult.Error("Groq se khali response mila.")
            }
        } catch (e: Exception) {
            AiCallResult.Error("Groq error: ${e.message ?: "Connection failed"}")
        }
    }

    fun askGemini(
        apiKey: String,
        prompt: String,
        history: List<AiHistoryTurn>,
        imageBitmap: Bitmap? = null
    ): AiCallResult {
        if (apiKey.isBlank()) return AiCallResult.Error("Gemini API key set nahi hai.")

        return try {
            val contents = JSONArray()

            history.takeLast(8).forEach { turn ->
                val parts = JSONArray().put(JSONObject().put("text", turn.text))
                contents.put(
                    JSONObject()
                        .put("role", if (turn.isUser) "user" else "model")
                        .put("parts", parts)
                )
            }

            val currentParts = JSONArray()
            if (imageBitmap != null) {
                val base64Jpeg = encodeBitmapForVision(imageBitmap)
                currentParts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Jpeg)
                    )
                )
            }
            currentParts.put(JSONObject().put("text", prompt))

            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", currentParts)
            )

            val payload = JSONObject()
                .put(
                    "system_instruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
                    )
                )
                .put("contents", contents)

            val responseText = postJson(
                urlString = GEMINI_URL,
                headers = mapOf(
                    "x-goog-api-key" to apiKey,
                    "Content-Type" to "application/json"
                ),
                body = payload.toString()
            )

            val root = JSONObject(responseText)
            val partsArray = root
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")

            val replyBuilder = StringBuilder()
            if (partsArray != null) {
                for (i in 0 until partsArray.length()) {
                    val partText = partsArray.optJSONObject(i)?.optString("text").orEmpty()
                    if (partText.isNotEmpty()) {
                        if (replyBuilder.isNotEmpty()) replyBuilder.append("\n")
                        replyBuilder.append(partText)
                    }
                }
            }

            val finalReply = replyBuilder.toString().trim()
            if (finalReply.isNotEmpty()) {
                AiCallResult.Success(
                    finalReply,
                    if (imageBitmap != null) "Gemini Vision" else "Gemini 2.5 Flash"
                )
            } else {
                AiCallResult.Error("Gemini se khali response mila.")
            }
        } catch (e: Exception) {
            AiCallResult.Error("Gemini error: ${e.message ?: "Connection failed"}")
        }
    }

    private fun encodeBitmapForVision(source: Bitmap): String {
        val maxSide = 1024
        val width = source.width
        val height = source.height
        val scaled = if (width > maxSide || height > maxSide) {
            val ratio = maxSide.toFloat() / maxOf(width, height).toFloat()
            Bitmap.createScaledBitmap(
                source,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== source) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun postJson(
        urlString: String,
        headers: Map<String, String>,
        body: String
    ): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 28000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        return try {
            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val errMsg = try {
                    val obj = JSONObject(text)
                    obj.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                } catch (_: Throwable) {
                    "HTTP $code"
                }
                throw IllegalStateException(errMsg)
            }
            text
        } finally {
            conn.disconnect()
        }
    }
}
