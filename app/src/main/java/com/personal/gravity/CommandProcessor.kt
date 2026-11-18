package com.personal.gravity

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class CommandProcessor(
    private val context: Context,
    private val onSpeechStateChange: (Boolean) -> Unit
) {

    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var safetyReleaseRunnable: Runnable? = null

    private val appAliases = mapOf(
        "व्हाट्सएप" to "whatsapp", "वाट्सएप" to "whatsapp", "vatsap" to "whatsapp",
        "यूट्यूब" to "youtube", "युटुब" to "youtube", "utube" to "youtube",
        "इंस्टाग्राम" to "instagram", "इंस्टा" to "instagram", "insta" to "instagram",
        "क्रोम" to "chrome", "ब्राउज़र" to "chrome",
        "कैमरा" to "camera", "फोटो" to "photos", "गैलरी" to "gallery",
        "सेटिंग" to "settings", "सेटिंग्स" to "settings", "setting" to "settings",
        "कैलकुलेटर" to "calculator", "कैलेंडर" to "calendar",
        "मैप्स" to "maps", "गूगल मैप" to "maps", "जीमेल" to "gmail",
        "टेलीग्राम" to "telegram", "फोनपे" to "phonepe", "पेटीएम" to "paytm",
        "गूगल पे" to "gpay", "स्पॉटिफाई" to "spotify", "फाइल" to "files",
        "प्ले स्टोर" to "play store", "playstore" to "play store"
    )

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("hi", "IN"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeechStateChange(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        releaseSpeechLock()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        releaseSpeechLock()
                    }
                })
            }
        }
    }

    private fun releaseSpeechLock() {
        safetyReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
        mainHandler.postDelayed({ onSpeechStateChange(false) }, 220)
    }

    fun speak(message: String) {
        onSpeechStateChange(true)
        safetyReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyReleaseRunnable = Runnable { onSpeechStateChange(false) }
        mainHandler.postDelayed(safetyReleaseRunnable!!, 3500)
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "GRAVITY_TTS_ID")
    }

    fun processAllHypotheses(
        hypotheses: List<String>,
        isAwakeSession: Boolean,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        if (hypotheses.isEmpty()) return

        // Find the hypothesis containing wake word if not already awake
        val matchedRaw = if (isAwakeSession) {
            hypotheses.first()
        } else {
            hypotheses.firstOrNull { containsWakeWord(it.lowercase(Locale.getDefault())) } ?: return
        }

        val text = matchedRaw.lowercase(Locale.getDefault()).trim()
        val command = stripWakeWords(text)

        if (command.isEmpty()) {
            speak("Haan boliye, main sun raha hu")
            onResult(GravityOrbView.OrbState.AWAKE, true, "Awake — Speak your command...")
            return
        }

        when {
            isTorchCommand(command) -> {
                val turnOn = !command.contains("off") && !command.contains("band") && !command.contains("बंद")
                toggleFlashlight(turnOn, onResult)
            }
            isBatteryCommand(command) -> {
                reportBatteryStatus(onResult)
            }
            isTimeCommand(command) -> {
                reportTimeAndDate(onResult)
            }
            isVolumeCommand(command) -> {
                adjustSystemVolume(command, onResult)
            }
            isWhatsAppMsgCommand(command) -> {
                handleWhatsAppMessage(command, onResult)
            }
            isCallCommand(command) -> {
                val target = extractCallTarget(command)
                handlePhoneCall(target, onResult)
            }
            isOpenAppCommand(command) -> {
                val appQuery = extractAppTarget(command)
                handleAppLaunch(appQuery, onResult)
            }
            else -> {
                if (tryLaunchAppSilently(command, onResult)) return
                speak("Command samajh nahi aaya, dobara boliye")
                onResult(GravityOrbView.OrbState.AWAKE, true, "Heard: “$matchedRaw”\nSay Call [Name] or Open [App]")
            }
        }
    }

    fun containsWakeWord(text: String): Boolean {
        return text.contains("gravity") ||
                text.contains("graviti") ||
                text.contains("gravitational") ||
                text.contains("ग्रेविटी") ||
                text.contains("ग्रैविटी") ||
                text.contains("ग्रेवटी")
    }

    private fun stripWakeWords(text: String): String {
        return text
            .replace("hello gravity", "")
            .replace("hey gravity", "")
            .replace("hi gravity", "")
            .replace("ok gravity", "")
            .replace("gravity", "")
            .replace("graviti", "")
            .replace("हेलो ग्रेविटी", "")
            .replace("हे ग्रेविटी", "")
            .replace("ग्रेविटी", "")
            .replace("ग्रैविटी", "")
            .replace("ग्रेवटी", "")
            .trim()
    }

    private fun isCallCommand(cmd: String): Boolean {
        return cmd.contains("call") || cmd.contains("कॉल") ||
                cmd.contains("phone lagao") || cmd.contains("फोन लगाओ") ||
                cmd.contains("baat karao") || cmd.contains("बात कराओ") ||
                cmd.contains("dial") || cmd.contains("number lagao") ||
                cmd.contains("lagao") || cmd.contains("लगाओ")
    }

    private fun isOpenAppCommand(cmd: String): Boolean {
        return cmd.contains("open") || cmd.contains("launch") ||
                cmd.contains("kholo") || cmd.contains("khol do") ||
                cmd.contains("chalu karo") || cmd.contains("start karo") ||
                cmd.contains("खोलो") || cmd.contains("खोल दो") || cmd.contains("चालू करो")
    }

    private fun isTorchCommand(cmd: String): Boolean {
        return cmd.contains("torch") || cmd.contains("flashlight") ||
                cmd.contains("flash light") || cmd.contains("टॉर्च") || cmd.contains("फ्लैशलाइट")
    }

    private fun isBatteryCommand(cmd: String): Boolean {
        return cmd.contains("battery") || cmd.contains("बैटरी") || cmd.contains("charge kitna")
    }

    private fun isTimeCommand(cmd: String): Boolean {
        return cmd.contains("time kya") || cmd.contains("what time") ||
                cmd.contains("kitne baje") || cmd.contains("टाइम क्या") ||
                cmd.contains("समय") || cmd.contains("date kya")
    }

    private fun isVolumeCommand(cmd: String): Boolean {
        return cmd.contains("volume") || cmd.contains("आवाज") ||
                cmd.contains("aawaz") || cmd.contains("silent") || cmd.contains("साइलेंट")
    }

    private fun isWhatsAppMsgCommand(cmd: String): Boolean {
        return (cmd.contains("whatsapp") || cmd.contains("व्हाट्सएप")) &&
                (cmd.contains("message") || cmd.contains("msg") || cmd.contains("मैसेज") || cmd.contains("bhejo"))
    }

    private fun extractCallTarget(cmd: String): String {
        val stopWords = listOf(
            "please", "zara", "ek baar", "call", "कॉल", "phone", "फोन",
            "lagao", "लगाओ", "laga do", "लगा दो", "karo", "करो", "kar do", "कर दो",
            "baat karao", "बात कराओ", "ko", "को", "to", "par", "पर", "number", "dial"
        )
        var cleaned = cmd
        for (word in stopWords) {
            cleaned = cleaned.replace(word, " ")
        }
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractAppTarget(cmd: String): String {
        val stopWords = listOf(
            "please", "zara", "open", "launch", "start", "app", "application",
            "kholo", "khol do", "kholiye", "chalu karo", "chalu kar do",
            "खोलो", "खोल दो", "चालू करो", "करो", "को", "ko", "karo", "kar do", "the"
        )
        var cleaned = cmd
        for (word in stopWords) {
            cleaned = cleaned.replace(word, " ")
        }
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        return appAliases[cleaned] ?: cleaned
    }

    private fun handlePhoneCall(
        targetQuery: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        if (targetQuery.isEmpty()) {
            speak("Kisko call lagana hai? Naam boliye.")
            onResult(GravityOrbView.OrbState.AWAKE, true, "Waiting for contact name...")
            return
        }

        val digitsOnly = targetQuery.filter { it.isDigit() }
        if (digitsOnly.length >= 10) {
            placePhoneCall(digitsOnly, digitsOnly, onResult)
            return
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            speak("Contacts permission allow kijiye")
            onResult(GravityOrbView.OrbState.PASSIVE, false, "Missing Permission: READ_CONTACTS")
            return
        }

        val contactMatch = findBestContactMatch(targetQuery)
        if (contactMatch != null) {
            placePhoneCall(contactMatch.first, contactMatch.second, onResult)
        } else {
            speak("$targetQuery naam ka contact nahi mila")
            onResult(GravityOrbView.OrbState.PASSIVE, false, "Contact not found: “$targetQuery”")
        }
    }

    private fun findBestContactMatch(query: String): Pair<String, String>? {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null

        var bestName: String? = null
        var bestNumber: String? = null
        var bestScore = 0.0
        val normalizedQuery = query.lowercase(Locale.getDefault()).trim()

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val rawName = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numIdx) ?: continue
                val cName = rawName.lowercase(Locale.getDefault()).trim()
                val score = calculateMatchScore(normalizedQuery, cName)
                if (score > bestScore) {
                    bestScore = score
                    bestName = rawName
                    bestNumber = rawNumber
                }
            }
        }

        return if (bestScore >= 0.54 && bestName != null && bestNumber != null) {
            Pair(bestName!!, bestNumber!!)
        } else null
    }

    private fun placePhoneCall(
        displayName: String,
        phoneNumber: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        speak("$displayName ko call lagaya ja raha hai")
        onResult(GravityOrbView.OrbState.EXECUTING, false, "Calling $displayName ($phoneNumber)...")

        val hasCallPerm = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val callIntent = Intent(action).apply {
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(callIntent)
        } catch (e: Exception) {
            onResult(GravityOrbView.OrbState.PASSIVE, false, "Call failed: ${e.localizedMessage}")
        }
    }

    private fun handleAppLaunch(
        appQuery: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        if (appQuery.isEmpty()) {
            speak("Kaunsa app open karna hai?")
            onResult(GravityOrbView.OrbState.AWAKE, true, "Waiting for app name...")
            return
        }

        when {
            appQuery.contains("wifi") || appQuery.contains("wi-fi") || appQuery.contains("वाईफाई") -> {
                openSystemSetting(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
                    else Settings.ACTION_WIFI_SETTINGS,
                    "Wi-Fi Settings",
                    onResult
                )
                return
            }
            appQuery.contains("bluetooth") || appQuery.contains("ब्लूटूथ") -> {
                openSystemSetting(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings", onResult)
                return
            }
            appQuery == "settings" || appQuery == "setting" || appQuery == "सेटिंग्स" -> {
                openSystemSetting(Settings.ACTION_SETTINGS, "System Settings", onResult)
                return
            }
        }

        if (!tryLaunchAppSilently(appQuery, onResult)) {
            speak("$appQuery app phone me nahi mila")
            onResult(GravityOrbView.OrbState.PASSIVE, false, "App not found: “$appQuery”")
        }
    }

    private fun tryLaunchAppSilently(
        rawQuery: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ): Boolean {
        val query = appAliases[rawQuery] ?: rawQuery
        val pm = context.packageManager

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        var bestPackage: String? = null
        var bestLabel: String? = null
        var bestScore = 0.0

        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            val cleanLabel = label.lowercase(Locale.getDefault()).trim()

            val labelScore = calculateMatchScore(query, cleanLabel)
            val pkgScore = if (pkg.lowercase().contains(query)) 0.78 else 0.0
            val score = maxOf(labelScore, pkgScore)

            if (score > bestScore) {
                bestScore = score
                bestPackage = pkg
                bestLabel = label
            }
        }

        if (bestScore >= 0.56 && bestPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(bestPackage!!)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                speak("$bestLabel open ho raha hai")
                onResult(GravityOrbView.OrbState.EXECUTING, false, "Opening $bestLabel...")
                context.startActivity(launchIntent)
                return true
            }
        }
        return false
    }

    private fun openSystemSetting(
        action: String,
        label: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        try {
            val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            speak("$label open ho raha hai")
            onResult(GravityOrbView.OrbState.EXECUTING, false, "Opening $label...")
            context.startActivity(intent)
        } catch (_: Exception) {
            onResult(GravityOrbView.OrbState.PASSIVE, false, "Unable to open $label")
        }
    }

    private fun toggleFlashlight(
        turnOn: Boolean,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camManager.cameraIdList.firstOrNull() ?: return
            camManager.setTorchMode(cameraId, turnOn)
            val stateWord = if (turnOn) "ON" else "OFF"
            speak("Flashlight $stateWord kar diya hai")
            onResult(GravityOrbView.OrbState.EXECUTING, false, "Flashlight $stateWord")
        } catch (_: Exception) {
            speak("Flashlight control error")
            onResult(GravityOrbView.OrbState.PASSIVE, false, "Flashlight unavailable")
        }
    }

    private fun reportBatteryStatus(
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speak("Phone ki battery $level percent hai")
        onResult(GravityOrbView.OrbState.EXECUTING, false, "Battery Level: $level%")
    }

    private fun reportTimeAndDate(
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        val now = Date()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now)
        speak("Abhi samay $timeStr hai")
        onResult(GravityOrbView.OrbState.EXECUTING, false, "$timeStr • $dateStr")
    }

    private fun adjustSystemVolume(
        cmd: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        when {
            cmd.contains("full") || cmd.contains("max") || cmd.contains("badhao") || cmd.contains("बढ़ाओ") -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
                speak("Volume full kar diya hai")
                onResult(GravityOrbView.OrbState.EXECUTING, false, "Volume: 100%")
            }
            cmd.contains("silent") || cmd.contains("mute") || cmd.contains("कम") || cmd.contains("kam") -> {
                val targetVol = (maxVol * 0.2).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                speak("Volume kam kar diya hai")
                onResult(GravityOrbView.OrbState.EXECUTING, false, "Volume: 20%")
            }
            else -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol / 2, AudioManager.FLAG_SHOW_UI)
                speak("Volume 50 percent set hai")
                onResult(GravityOrbView.OrbState.EXECUTING, false, "Volume: 50%")
            }
        }
    }

    private fun handleWhatsAppMessage(
        cmd: String,
        onResult: (GravityOrbView.OrbState, Boolean, String) -> Unit
    ) {
        val cleaned = cmd.replace("whatsapp", "")
            .replace("व्हाट्सएप", "")
            .replace("par", "")
            .replace("message", "")
            .replace("bhejo", "")
            .trim()

        val contact = findBestContactMatch(cleaned)
        if (contact != null) {
            val formattedNum = contact.second.filter { it.isDigit() || it == '+' }
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNum")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            speak("${contact.first} ka WhatsApp open ho raha hai")
            onResult(GravityOrbView.OrbState.EXECUTING, false, "WhatsApp Chat: ${contact.first}")
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                onResult(GravityOrbView.OrbState.PASSIVE, false, "WhatsApp not installed")
            }
        } else {
            handleAppLaunch("whatsapp", onResult)
        }
    }

    private fun calculateMatchScore(query: String, candidate: String): Double {
        if (query == candidate) return 1.0
        if (candidate.contains(query) || query.contains(candidate)) return 0.90

        val queryTokens = query.split(" ").filter { it.isNotBlank() }
        val candTokens = candidate.split(" ").filter { it.isNotBlank() }

        var bestTokenScore = 0.0
        for (qToken in queryTokens) {
            for (cToken in candTokens) {
                val dist = levenshtein(qToken, cToken)
                val maxLen = maxOf(qToken.length, cToken.length).coerceAtLeast(1)
                val similarity = 1.0 - (dist.toDouble() / maxLen.toDouble())
                if (similarity > bestTokenScore) {
                    bestTokenScore = similarity
                }
            }
        }
        return bestTokenScore
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    fun shutdown() {
        safetyReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
        tts?.stop()
        tts?.shutdown()
    }
}
