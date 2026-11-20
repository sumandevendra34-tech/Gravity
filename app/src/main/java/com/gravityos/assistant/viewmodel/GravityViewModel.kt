package com.gravityos.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravityos.assistant.core.ModelFolderStatus
import com.gravityos.assistant.core.ModelStorageManager
import com.gravityos.assistant.core.PendingAction
import com.gravityos.assistant.ui.components.OrbState
import com.gravityos.assistant.voice.ChatMessage
import com.gravityos.assistant.voice.GravityVoiceHub
import com.gravityos.assistant.voice.GravityVoiceService
import com.gravityos.assistant.voice.VoiceEngineDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GravityScreen {
    HOME,
    CHAT,
    SETUP
}

data class GravityUiState(
    val currentScreen: GravityScreen = GravityScreen.HOME,
    val orbState: OrbState = OrbState.IDLE,
    val voiceAmplitude: Float = 0f,
    val isWakeServiceRunning: Boolean = false,
    val inputText: String = "",
    val statusHeadline: String = "How can I help\nyou today?",
    val statusSubtitle: String = "Hello • Offline Core Ready",
    val latestReply: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val pendingAction: PendingAction? = null,
    val voiceSpeed: Float = 1.0f,
    val diagnostics: VoiceEngineDiagnostics = VoiceEngineDiagnostics(),
    val modelFolders: List<ModelFolderStatus> = emptyList(),
    val hasAllFilesAccess: Boolean = false
)

class GravityViewModel(application: Application) : AndroidViewModel(application) {

    private val modelStorageManager = ModelStorageManager()

    private val _uiState = MutableStateFlow(GravityUiState())
    val uiState: StateFlow<GravityUiState> = _uiState.asStateFlow()

    init {
        GravityVoiceHub.ensureInitialized(application)
        refreshStorageStatus()

        viewModelScope.launch {
            GravityVoiceHub.state.collect { voice ->
                _uiState.update { current ->
                    current.copy(
                        orbState = voice.orbState,
                        voiceAmplitude = voice.voiceAmplitude,
                        isWakeServiceRunning = voice.isWakeServiceRunning,
                        statusSubtitle = voice.statusSubtitle,
                        latestReply = voice.latestReply,
                        messages = voice.messages,
                        pendingAction = voice.pendingAction,
                        voiceSpeed = voice.voiceSpeed,
                        diagnostics = voice.diagnostics
                    )
                }
            }
        }
    }

    fun navigateTo(screen: GravityScreen) {
        if (screen == GravityScreen.SETUP) {
            refreshStorageStatus()
            GravityVoiceHub.refreshDiagnosticsOnly()
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onOrbTap() {
        val app = getApplication<Application>()
        if (_uiState.value.orbState == OrbState.SPEAKING) {
            GravityVoiceHub.interruptSpeech()
            return
        }
        if (_uiState.value.isWakeServiceRunning) {
            GravityVoiceService.triggerListenNow(app)
        } else {
            GravityVoiceHub.triggerSingleShotListen(app)
        }
    }

    fun toggleWakeWordService() {
        val app = getApplication<Application>()
        if (_uiState.value.isWakeServiceRunning) {
            GravityVoiceService.stop(app)
        } else {
            GravityVoiceService.start(app)
        }
    }

    fun submitCommand(overrideText: String? = null) {
        val text = (overrideText ?: _uiState.value.inputText).trim()
        if (text.isEmpty()) return

        _uiState.update { it.copy(inputText = "") }
        GravityVoiceHub.launchTypedCommand(getApplication(), text)
    }

    fun cancelPendingAction() {
        GravityVoiceHub.cancelPendingAction()
    }

    fun clearChat() {
        GravityVoiceHub.clearChat()
    }

    fun setVoiceSpeed(speed: Float) {
        GravityVoiceHub.setVoiceSpeed(speed)
    }

    fun reloadOfflineModels() {
        GravityVoiceHub.reloadAllOfflineModels(getApplication())
        refreshStorageStatus()
    }

    fun testNeuralVoice() {
        GravityVoiceHub.testNeuralVoice(getApplication())
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
