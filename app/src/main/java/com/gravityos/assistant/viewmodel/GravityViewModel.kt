package com.gravityos.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravityos.assistant.core.AppLauncherManager
import com.gravityos.assistant.core.CommandRouter
import com.gravityos.assistant.core.ContactCallManager
import com.gravityos.assistant.core.ModelFolderStatus
import com.gravityos.assistant.core.ModelStorageManager
import com.gravityos.assistant.core.PendingAction
import com.gravityos.assistant.core.PhoneControlManager
import com.gravityos.assistant.ui.components.OrbState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class GravityScreen {
    HOME,
    CHAT,
    SETUP
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class GravityUiState(
    val currentScreen: GravityScreen = GravityScreen.HOME,
    val orbState: OrbState = OrbState.IDLE,
    val inputText: String = "",
    val statusHeadline: String = "How can I help\nyou today?",
    val statusSubtitle: String = "Hello • Offline Core Ready",
    val latestReply: String? = null,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            text = "Main GRAVITY OS hoon. Mujhse koi bhi app khulwao, kisi bhi contact ko direct call lagwao, ya Torch/Volume/Settings control karo.",
            isUser = false
        )
    ),
    val pendingAction: PendingAction? = null,
    val modelFolders: List<ModelFolderStatus> = emptyList(),
    val hasAllFilesAccess: Boolean = false
)

class GravityViewModel(application: Application) : AndroidViewModel(application) {

    private val contactCallManager = ContactCallManager(application)
    private val appLauncherManager = AppLauncherManager(application)
    private val phoneControlManager = PhoneControlManager(application)
    private val modelStorageManager = ModelStorageManager()
    private val commandRouter = CommandRouter(
        contactCallManager = contactCallManager,
        appLauncherManager = appLauncherManager,
        phoneControlManager = phoneControlManager
    )

    private val _uiState = MutableStateFlow(GravityUiState())
    val uiState: StateFlow<GravityUiState> = _uiState.asStateFlow()

    init {
        refreshStorageStatus()
    }

    fun navigateTo(screen: GravityScreen) {
        if (screen == GravityScreen.SETUP) refreshStorageStatus()
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleListeningPreview() {
        val current = _uiState.value.orbState
        if (current == OrbState.LISTENING) {
            _uiState.update {
                it.copy(
                    orbState = OrbState.IDLE,
                    statusSubtitle = "Hello • Offline Core Ready"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    orbState = OrbState.LISTENING,
                    statusSubtitle = "Listening • Step 3 me Offline Voice active hogi"
                )
            }
        }
    }

    fun submitCommand(overrideText: String? = null) {
        val text = (overrideText ?: _uiState.value.inputText).trim()
        if (text.isEmpty()) return

        val activePending = _uiState.value.pendingAction
        _uiState.update {
            it.copy(
                inputText = "",
                orbState = OrbState.THINKING,
                statusSubtitle = "Processing command...",
                messages = it.messages + ChatMessage(text = text, isUser = true)
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                commandRouter.processCommand(text, activePending)
            }

            _uiState.update {
                it.copy(
                    orbState = OrbState.SPEAKING,
                    statusSubtitle = "GRAVITY OS",
                    latestReply = result.reply,
                    pendingAction = result.pendingAction,
                    messages = it.messages + ChatMessage(text = result.reply, isUser = false)
                )
            }

            // Step 3 me yahan Sherpa-ONNX Local Neural Voice play hogi (No Android default TTS)
            delay(1600)
            _uiState.update { state ->
                if (state.orbState == OrbState.SPEAKING) {
                    state.copy(orbState = OrbState.IDLE)
                } else state
            }
        }
    }

    fun cancelPendingAction() {
        _uiState.update {
            it.copy(
                pendingAction = null,
                latestReply = "Cancel kar diya gaya.",
                orbState = OrbState.IDLE
            )
        }
    }

    fun clearChat() {
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        text = "Nayi conversation shuru ho gayi hai. Boliye kya karna hai?",
                        isUser = false
                    )
                ),
                latestReply = null,
                pendingAction = null,
                orbState = OrbState.IDLE
            )
        }
    }

    fun createModelFolders() {
        modelStorageManager.ensureModelDirectories()
        refreshStorageStatus()
    }

    fun refreshStorageStatus() {
        _uiState.update {
            it.copy(
                hasAllFilesAccess = modelStorageManager.hasAllFilesAccess(),
                modelFolders = modelStorageManager.inspectModelFolders()
            )
        }
    }
}
