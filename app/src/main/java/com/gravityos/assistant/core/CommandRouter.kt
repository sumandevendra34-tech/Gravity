package com.gravityos.assistant.core

sealed class PendingAction {
    data class CallOptions(val candidates: List<ContactMatch>) : PendingAction()
    data class AppOptions(val candidates: List<InstalledApp>) : PendingAction()
}

data class CommandResult(
    val reply: String,
    val handledLocally: Boolean = true,
    val pendingAction: PendingAction? = null
)

class CommandRouter(
    private val contactCallManager: ContactCallManager,
    private val appLauncherManager: AppLauncherManager,
    private val phoneControlManager: PhoneControlManager
) {

    fun processCommand(rawInput: String, pendingAction: PendingAction?): CommandResult {
        val cleaned = rawInput
            .trim()
            .replace(Regex("^(hey\\s+gravity|gravity|ओके\\s+ग्रैविटी|हे\\s+ग्रैविटी|ग्रैविटी)[,\\s]*", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleaned.isEmpty()) {
            return CommandResult("Boliye, main sun raha hoon.")
        }

        // 1. Handle active confirmation first ("haan", "yes", "1", "2", "cancel", or contact name)
        if (pendingAction != null) {
            val confirmationResult = handlePendingConfirmation(cleaned, pendingAction)
            if (confirmationResult != null) return confirmationResult
        }

        val lower = cleaned.lowercase()

        // 2. Torch / Flashlight commands
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("flash light") || lower.contains("टॉर्च")) {
            return when {
                lower.contains("band") || lower.contains("off") || lower.contains("close") || lower.contains("बंद") ->
                    CommandResult(phoneControlManager.setTorch(false))
                lower.contains("chalu") || lower.contains("on") || lower.contains("start") || lower.contains("jalao") || lower.contains("चालू") ->
                    CommandResult(phoneControlManager.setTorch(true))
                else -> CommandResult(phoneControlManager.toggleTorch())
            }
        }

        // 3. Volume commands
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("aawaz") || lower.contains("sound") || lower.contains("आवाज") || lower.contains("वॉल्यूम")) {
            return when {
                lower.contains("mute") || lower.contains("band") || lower.contains("silent") || lower.contains("म्यूट") ->
                    CommandResult(phoneControlManager.muteVolume())
                lower.contains("full") || lower.contains("max") || lower.contains("100") ->
                    CommandResult(phoneControlManager.setVolumeMax())
                lower.contains("kam") || lower.contains("down") || lower.contains("low") || lower.contains("ghata") || lower.contains("कम") ->
                    CommandResult(phoneControlManager.decreaseVolume())
                else -> CommandResult(phoneControlManager.increaseVolume())
            }
        }

        // 4. System Settings shortcuts (Wi-Fi, Bluetooth, Hotspot, Display)
        if (lower.contains("hotspot") || lower.contains("tether") || lower.contains("हॉटस्पॉट")) {
            return CommandResult(phoneControlManager.openHotspotSettings())
        }
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई") || lower.contains("internet setting")) {
            return CommandResult(phoneControlManager.openWifiSettings())
        }
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ")) {
            return CommandResult(phoneControlManager.openBluetoothSettings())
        }
        if (lower.contains("display setting") || lower.contains("brightness") || lower.contains("screen setting")) {
            return CommandResult(phoneControlManager.openDisplaySettings())
        }

        // 5. Camera command
        if (lower == "camera" || lower.contains("camera kholo") || lower.contains("open camera") || lower.contains("camera open") || lower.contains("कैमरा")) {
            return CommandResult(phoneControlManager.openCamera())
        }

        // 6. Direct Call commands (Hindi + English + Hinglish)
        val callTarget = extractCallTarget(cleaned)
        if (callTarget != null) {
            return when (val res = contactCallManager.resolveAndCall(callTarget)) {
                is CallResolution.DirectCalled ->
                    CommandResult("${res.contact.name} ko call laga raha hoon...")
                is CallResolution.AskConfirmation ->
                    CommandResult(res.message, pendingAction = PendingAction.CallOptions(res.candidates))
                is CallResolution.PermissionRequired ->
                    CommandResult(res.message)
                is CallResolution.NotFound ->
                    CommandResult("\"${res.query}\" naam ka koi contact phone me nahi mila.")
            }
        }

        // 7. App Opening commands (All System + Play Store apps)
        val appTarget = extractAppTarget(cleaned)
        if (appTarget != null) {
            if (appTarget.equals("settings", ignoreCase = true) || appTarget.equals("setting", ignoreCase = true)) {
                return CommandResult(phoneControlManager.openSystemSettings())
            }
            return when (val res = appLauncherManager.resolveAndLaunch(appTarget)) {
                is AppLaunchResolution.Launched ->
                    CommandResult("${res.app.label} khol diya hai.")
                is AppLaunchResolution.AskConfirmation ->
                    CommandResult(res.message, pendingAction = PendingAction.AppOptions(res.candidates))
                is AppLaunchResolution.NotFound ->
                    CommandResult("\"${res.query}\" naam ka koi app phone me install nahi mila.")
            }
        }

        // 8. Non-command conversational query (Phase 4 me Groq/Gemini + Offline LLM yahan connect hoga)
        return CommandResult(
            reply = "Mainne aapka message padha: \"$cleaned\".\nAbhi Phase 1-2 me Phone Commands (Call, Koi bhi App kholna, Torch, Volume, Settings) active hain. Step 3 me Offline Voice + Wake Word aur Step 4 me Groq/Gemini Online AI + Offline LLM jud jayega.",
            handledLocally = false
        )
    }

    private fun handlePendingConfirmation(input: String, pending: PendingAction): CommandResult? {
        val lower = input.lowercase().trim()

        // Cancel keywords
        if (lower in listOf("nahi", "no", "cancel", "rehne do", "mat karo", "stop", "band karo", "नहीं")) {
            return CommandResult("Theek hai, cancel kar diya.")
        }

        val chosenIndex: Int? = when {
            lower in listOf("haan", "ha", "han", "yes", "ok", "okay", "ji", "karo", "laga do", "khol do", "1", "pehla", "first", "ek", "हाँ") -> 0
            lower in listOf("2", "dusra", "doosra", "second", "do") -> 1
            lower in listOf("3", "teesra", "third", "teen") -> 2
            lower in listOf("4", "chautha", "fourth", "char") -> 3
            else -> null
        }

        return when (pending) {
            is PendingAction.CallOptions -> {
                val chosen = if (chosenIndex != null && chosenIndex < pending.candidates.size) {
                    pending.candidates[chosenIndex]
                } else {
                    pending.candidates.maxByOrNull { StringSimilarity.score(lower, it.name) }
                        ?.takeIf { StringSimilarity.score(lower, it.name) >= 0.65 }
                }
                if (chosen != null) {
                    when (val res = contactCallManager.placeCall(chosen)) {
                        is CallResolution.DirectCalled -> CommandResult("${res.contact.name} ko call laga raha hoon...")
                        is CallResolution.PermissionRequired -> CommandResult(res.message)
                        else -> CommandResult("Call lagane me dikkat aayi.")
                    }
                } else null
            }

            is PendingAction.AppOptions -> {
                val chosen = if (chosenIndex != null && chosenIndex < pending.candidates.size) {
                    pending.candidates[chosenIndex]
                } else {
                    pending.candidates.maxByOrNull { StringSimilarity.score(lower, it.label) }
                        ?.takeIf { StringSimilarity.score(lower, it.label) >= 0.65 }
                }
                if (chosen != null) {
                    when (val res = appLauncherManager.launchPackage(chosen)) {
                        is AppLaunchResolution.Launched -> CommandResult("${res.app.label} khol diya hai.")
                        else -> CommandResult("${chosen.label} open nahi ho paya.")
                    }
                } else null
            }
        }
    }

    private fun extractCallTarget(input: String): String? {
        val patterns = listOf(
            Regex("^call\\s+(?:to\\s+)?(.+)$", RegexOption.IGNORE_CASE),
            Regex("^dial\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^phone\\s+(?:lagao|karo)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s+(?:ko|pe|par)\\s+(?:call|phone|फोने|कॉल)(?:\\s+(?:karo|lagao|laga\\s+do|kar\\s+do|करो|लगाओ))?$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s+(?:call|कॉल)\\s+(?:karo|lagao|laga\\s+do|kar\\s+do|करो|लगाओ)$", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(input)
            if (match != null) {
                val target = match.groupValues[1].trim()
                if (target.isNotEmpty()) return target
            }
        }
        return null
    }

    private fun extractAppTarget(input: String): String? {
        val patterns = listOf(
            Regex("^(?:open|launch|start|run)\\s+(.+?)(?:\\s+app)?$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s+(?:app\\s+)?(?:kholo|khol\\s+do|open\\s+karo|chalao|chalu\\s+karo|launch\\s+karo|खोलो|चलाओ)$", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val match = regex.find(input)
            if (match != null) {
                val target = match.groupValues[1].trim()
                if (target.isNotEmpty()) return target
            }
        }
        return null
    }
}
