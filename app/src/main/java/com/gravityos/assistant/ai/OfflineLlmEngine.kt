package com.gravityos.assistant.ai

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class OfflineLlmEngine(private val context: Context) {

    private val llmDir: File
        get() = File(Environment.getExternalStorageDirectory(), "GravityOS/models/llm")

    @Volatile
    private var mediaPipeHandle: Any? = null

    @Volatile
    var statusMessage: String = "Offline Utility Ready"
        private set

    fun isLocalModelLoaded(): Boolean = mediaPipeHandle != null

    @Synchronized
    fun initialize(): Boolean {
        if (mediaPipeHandle != null) return true

        val modelFile = findSupportedLocalModel()
        if (modelFile == null) {
            statusMessage = "Offline Math & Study Ready (Optional .task LLM in /models/llm)"
            return false
        }

        // Dynamic bridge for MediaPipe LlmInference (if tasks-genai AAR is present in app/libs)
        return try {
            val optionsClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions")
            val builderMethod = optionsClass.getMethod("builder")
            val builder = builderMethod.invoke(null)

            val builderClass = builder.javaClass
            builderClass.getMethod("setModelPath", String::class.java).invoke(builder, modelFile.absolutePath)
            try {
                builderClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(builder, 512)
            } catch (_: Throwable) {
            }
            val options = builderClass.getMethod("build").invoke(builder)

            val llmClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
            val createMethod = llmClass.getMethod("createFromOptions", Context::class.java, optionsClass)
            mediaPipeHandle = createMethod.invoke(null, context.applicationContext, options)
            statusMessage = "Local LLM Ready (${modelFile.name})"
            true
        } catch (_: Throwable) {
            statusMessage = "Model mila (${modelFile.name}); MediaPipe AAR add karne par active hoga"
            false
        }
    }

    fun generateReply(prompt: String): AiCallResult {
        val trimmed = prompt.trim()

        // 1. Instant Offline Math, Date/Time & Study Formulas (0 ms latency, 0 RAM overhead)
        tryBuiltInOfflineAnswer(trimmed)?.let { instant ->
            return AiCallResult.Success(instant, "Offline Core")
        }

        // 2. Loaded Local MediaPipe LLM (.task / .bin model from /GravityOS/models/llm)
        if (mediaPipeHandle == null) {
            initialize()
        }
        mediaPipeHandle?.let { llm ->
            try {
                val method = llm.javaClass.getMethod("generateResponse", String::class.java)
                val response = (method.invoke(llm, trimmed) as? String)?.trim()
                if (!response.isNullOrEmpty()) {
                    return AiCallResult.Success(response, "Offline Local LLM")
                }
            } catch (_: Throwable) {
            }
        }

        return AiCallResult.Success(
            reply = "Abhi Offline Mode chal raha hai. Main offline phone commands, calling, apps, voice typing, math calculations aur study formulas kar sakta hoon. Full AI chat ke liye internet ON karein aur Setup me apni free Groq ya Gemini API key save karein.",
            providerName = "Offline Core"
        )
    }

    @Synchronized
    fun release() {
        try {
            mediaPipeHandle?.javaClass?.getMethod("close")?.invoke(mediaPipeHandle)
        } catch (_: Throwable) {
        }
        mediaPipeHandle = null
    }

    private fun findSupportedLocalModel(): File? {
        if (!llmDir.exists()) return null
        return llmDir.listFiles()?.firstOrNull { file ->
            file.isFile && (
                file.name.endsWith(".task", ignoreCase = true) ||
                    file.name.endsWith(".bin", ignoreCase = true)
                )
        }
    }

    private fun tryBuiltInOfflineAnswer(input: String): String? {
        val lower = input.lowercase(Locale.ROOT)

        // Greetings & Identity
        if (lower in listOf("hi", "hello", "hey", "namaste", "hii", "hello gravity", "hey gravity")) {
            return "Namaste! Main GRAVITY OS hoon. Boliye aaj kya help karu?"
        }
        if (lower.contains("who are you") || lower.contains("tum kaun ho") || lower.contains("aap kaun ho") || lower.contains("your name")) {
            return "Main GRAVITY OS hoon — aapka personal Android AI assistant."
        }

        // Time & Date
        if (lower.contains("time") || lower.contains("kitne baje") || lower.contains("samay")) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return "Abhi $timeStr baj rahe hain."
        }
        if (lower.contains("date") || lower.contains("tarikh") || lower.contains("aaj kya din") || lower.contains("today")) {
            val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
            return "Aaj $dateStr hai."
        }

        // Quick Study & Electronics Formulas (Offline)
        if (lower.contains("ohm") && (lower.contains("law") || lower.contains("formula"))) {
            return "Ohm's Law: V = I × R\nYahan V = Voltage (Volts), I = Current (Amperes), aur R = Resistance (Ohms)."
        }
        if (lower.contains("quadratic")) {
            return "Quadratic Formula (ax² + bx + c = 0):\nx = (-b ± √(b² - 4ac)) / 2a"
        }
        if (lower.contains("pythagoras")) {
            return "Pythagoras Theorem:\nHypotenuse² = Base² + Perpendicular²  (h² = b² + p²)"
        }
        if (lower.contains("power formula") || lower.contains("electric power")) {
            return "Electric Power Formulas:\nP = V × I = I² × R = V² / R (Unit: Watt)"
        }
        if (lower.contains("kirchhoff")) {
            return "Kirchhoff's Laws:\n1. KCL (Current Law): Kisi node par aane wale currents ka sum = jaane wale currents ka sum (ΣI = 0).\n2. KVL (Voltage Law): Kisi closed loop me sabhi voltages ka algebraic sum zero hota hai (ΣV = 0)."
        }

        // Offline Math Expression Solver
        val mathCandidate = input
            .replace(Regex("^(calculate|solve|what is|kitna hoga|value of)\\s+", RegexOption.IGNORE_CASE), "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("x", "*", ignoreCase = false)
            .trim()

        if (mathCandidate.matches(Regex("^[0-9+\\-*/().^\\s%]+$")) &&
            mathCandidate.any { it in "+-*/^" }
        ) {
            val value = evaluateMathExpression(mathCandidate)
            if (value != null && !value.isNaN() && !value.isInfinite()) {
                val formatted = if (value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
                }
                return "$mathCandidate = $formatted"
            }
        }

        return null
    }

    private fun evaluateMathExpression(expr: String): Double? {
        return try {
            object {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    ch = if (++pos < expr.length) expr[pos].code else -1
                }

                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < expr.length) throw IllegalArgumentException("Unexpected char")
                    return x
                }

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.code) -> x += parseTerm()
                            eat('-'.code) -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.code) -> x *= parseFactor()
                            eat('/'.code) -> x /= parseFactor()
                            eat('%'.code) -> x %= parseFactor()
                            else -> return x
                        }
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()

                    var x: Double
                    val startPos = pos
                    if (eat('('.code)) {
                        x = parseExpression()
                        if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
                    } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                        while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                        x = expr.substring(startPos, pos).toDouble()
                    } else if (ch in 'a'.code..'z'.code) {
                        while (ch in 'a'.code..'z'.code) nextChar()
                        val func = expr.substring(startPos, pos)
                        x = parseFactor()
                        x = when (func) {
                            "sqrt" -> sqrt(x)
                            "sin" -> sin(Math.toRadians(x))
                            "cos" -> cos(Math.toRadians(x))
                            "tan" -> tan(Math.toRadians(x))
                            else -> throw IllegalArgumentException("Unknown function")
                        }
                    } else {
                        throw IllegalArgumentException("Unexpected token")
                    }

                    if (eat('^'.code)) x = x.pow(parseFactor())
                    return x
                }
            }.parse()
        } catch (_: Throwable) {
            null
        }
    }
}
