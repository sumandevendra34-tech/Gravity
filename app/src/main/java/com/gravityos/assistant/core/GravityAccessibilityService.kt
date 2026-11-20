package com.gravityos.assistant.core

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed class TypingResult {
    data class Success(val typedText: String, val submitted: Boolean = false) : TypingResult()
    data object ServiceNotEnabled : TypingResult()
    data object NoInputFieldFound : TypingResult()
    data class Failed(val reason: String) : TypingResult()
}

class GravityAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: GravityAccessibilityService? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        fun isRunning(): Boolean = instance != null

        fun typeText(textToType: String, submitAfter: Boolean = false): TypingResult {
            val service = instance ?: return TypingResult.ServiceNotEnabled
            val cleanText = textToType.trim()
            if (cleanText.isEmpty()) return TypingResult.Failed("Type karne ke liye koi text nahi mila.")

            return runOnMainSync(TypingResult.Failed("Typing timeout ho gaya.")) {
                service.performTypeOnActiveField(cleanText, submitAfter)
            }
        }

        fun clearActiveText(): TypingResult {
            val service = instance ?: return TypingResult.ServiceNotEnabled
            return runOnMainSync(TypingResult.Failed("Clear timeout ho gaya.")) {
                service.performClearActiveField()
            }
        }

        fun pressEnterOrSend(): TypingResult {
            val service = instance ?: return TypingResult.ServiceNotEnabled
            return runOnMainSync(TypingResult.Failed("Action timeout ho gaya.")) {
                val node = service.findTargetEditableNode()
                val submitted = service.triggerSubmitAction(node)
                if (submitted) {
                    TypingResult.Success("", submitted = true)
                } else {
                    TypingResult.Failed("Screen par Search ya Send button nahi mila.")
                }
            }
        }

        private fun <T> runOnMainSync(fallback: T, block: () -> T): T {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return try {
                    block()
                } catch (_: Throwable) {
                    fallback
                }
            }
            val resultRef = AtomicReference(fallback)
            val latch = CountDownLatch(1)
            mainHandler.post {
                try {
                    resultRef.set(block())
                } catch (_: Throwable) {
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(1500, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
            return resultRef.get()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener — active window is queried on demand when user speaks a typing command
    }

    override fun onInterrupt() {
        // Nothing to clean up
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun performTypeOnActiveField(textToType: String, submitAfter: Boolean): TypingResult {
        val node = findTargetEditableNode() ?: return TypingResult.NoInputFieldFound

        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        val existingText = extractRealExistingText(node)
        val combinedText = if (existingText.isBlank()) {
            textToType
        } else {
            val sep = if (existingText.endsWith(" ") || existingText.endsWith("\n")) "" else " "
            "$existingText$sep$textToType"
        }

        // Primary method: Direct offline Accessibility text injection (fastest, works in Google, WhatsApp, YouTube, etc.)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combinedText)
        }
        var typed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

        if (typed) {
            val cursorArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, combinedText.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, combinedText.length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
        } else {
            // Fallback for WebViews / custom editors where ACTION_SET_TEXT is unsupported
            typed = pasteViaClipboard(node, if (existingText.isBlank()) textToType else " $textToType")
        }

        if (!typed) {
            return TypingResult.Failed("Is text box me auto-typing block ho rahi hai.")
        }

        val didSubmit = if (submitAfter) {
            triggerSubmitAction(node)
        } else {
            false
        }

        return TypingResult.Success(typedText = textToType, submitted = didSubmit)
    }

    private fun performClearActiveField(): TypingResult {
        val node = findTargetEditableNode() ?: return TypingResult.NoInputFieldFound
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val cleared = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        return if (cleared) {
            TypingResult.Success("")
        } else {
            TypingResult.Failed("Text clear nahi ho paya.")
        }
    }

    private fun pasteViaClipboard(node: AccessibilityNodeInfo, chunk: String): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val previousClip = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("GravityVoiceType", chunk))
            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            // Restore previous clipboard softly after a short delay if paste succeeded
            if (pasted && previousClip != null) {
                mainHandler.postDelayed({
                    try {
                        clipboard.setPrimaryClip(previousClip)
                    } catch (_: Throwable) {
                    }
                }, 400)
            }
            pasted
        } catch (_: Throwable) {
            false
        }
    }

    private fun triggerSubmitAction(focusedNode: AccessibilityNodeInfo?): Boolean {
        // 1. Android 11+ (API 30+) native IME Enter / Search action
        if (focusedNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (focusedNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                return true
            }
        }

        // 2. Fallback: find visible Send / Search / Go button in the active window
        val root = rootInActiveWindow ?: return false
        val submitButton = findSubmitButton(root)
        return submitButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun extractRealExistingText(node: AccessibilityNodeInfo): String {
        val raw = node.text?.toString().orEmpty()
        if (raw.isEmpty()) return ""
        if (node.isShowingHintText) return ""
        val hint = node.hintText?.toString().orEmpty()
        if (hint.isNotEmpty() && raw == hint) return ""
        return raw
    }

    private fun findTargetEditableNode(): AccessibilityNodeInfo? {
        // Priority 1: Directly focused input box
        val directFocus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (directFocus != null && isEditableNode(directFocus)) {
            return directFocus
        }

        // Priority 2: Search active window tree for focused editable box, then any visible editable box
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            findEditableRecursive(activeRoot, requireFocused = true)?.let { return it }
            findEditableRecursive(activeRoot, requireFocused = false)?.let { return it }
        }

        // Priority 3: Check other interactive windows (useful when overlay / split screen is active)
        val allWindows = try {
            windows
        } catch (_: Throwable) {
            emptyList()
        }
        for (window in allWindows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() == packageName) continue
            findEditableRecursive(root, requireFocused = true)?.let { return it }
        }
        for (window in allWindows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() == packageName) continue
            findEditableRecursive(root, requireFocused = false)?.let { return it }
        }

        return null
    }

    private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        val cls = node.className?.toString().orEmpty()
        return cls.contains("EditText", ignoreCase = true) ||
            cls.contains("AutoCompleteTextView", ignoreCase = true)
    }

    private fun findEditableRecursive(
        node: AccessibilityNodeInfo,
        requireFocused: Boolean
    ): AccessibilityNodeInfo? {
        if (isEditableNode(node) && node.isVisibleToUser && node.isEnabled) {
            if (!requireFocused || node.isFocused) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findEditableRecursive(child, requireFocused)
            if (match != null) return match
        }
        return null
    }

    private fun findSubmitButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isVisibleToUser && node.isEnabled) {
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val combined = "$desc $text"
            val keywords = listOf("send", "search", "submit", "go", "भेजें", "खोजें")
            if (keywords.any { combined.contains(it) } && !isEditableNode(node)) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSubmitButton(child)
            if (found != null) return found
        }
        return null
    }
}
