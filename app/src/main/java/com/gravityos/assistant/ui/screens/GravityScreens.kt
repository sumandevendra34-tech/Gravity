package com.gravityos.assistant.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravityos.assistant.core.PendingAction
import com.gravityos.assistant.ui.components.GravityOrb
import com.gravityos.assistant.ui.components.OrbState
import com.gravityos.assistant.ui.theme.GravityColors
import com.gravityos.assistant.viewmodel.GravityScreen
import com.gravityos.assistant.viewmodel.GravityUiState
import com.gravityos.assistant.voice.ChatMessage
import java.util.Locale

data class PermissionSnapshot(
    val micGranted: Boolean,
    val contactsGranted: Boolean,
    val callGranted: Boolean,
    val cameraGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val accessibilityGranted: Boolean,
    val storageGranted: Boolean
)

@Composable
fun GravityRootHost(
    uiState: GravityUiState,
    permissions: PermissionSnapshot,
    onNavigate: (GravityScreen) -> Unit,
    onInputChange: (String) -> Unit,
    onSubmit: (String?) -> Unit,
    onOrbTap: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onCancelPending: () -> Unit,
    onClearChat: () -> Unit,
    onSetVoiceSpeed: (Float) -> Unit,
    onReloadOfflineModels: () -> Unit,
    onTestNeuralVoice: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onCreateModelFolders: () -> Unit
) {
    AnimatedContent(
        targetState = uiState.currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "gravity_screen_switch"
    ) { screen ->
        when (screen) {
            GravityScreen.HOME -> GravityHomeScreen(
                uiState = uiState,
                permissions = permissions,
                onOpenChat = { onNavigate(GravityScreen.CHAT) },
                onOpenSetup = { onNavigate(GravityScreen.SETUP) },
                onInputChange = onInputChange,
                onSubmit = onSubmit,
                onOrbTap = onOrbTap,
                onToggleWakeWord = onToggleWakeWord,
                onCancelPending = onCancelPending
            )

            GravityScreen.CHAT -> GravityChatScreen(
                uiState = uiState,
                onBack = { onNavigate(GravityScreen.HOME) },
                onClearChat = onClearChat,
                onInputChange = onInputChange,
                onSubmit = onSubmit,
                onOrbTap = onOrbTap,
                onCancelPending = onCancelPending
            )

            GravityScreen.SETUP -> GravitySetupScreen(
                uiState = uiState,
                permissions = permissions,
                onBack = { onNavigate(GravityScreen.HOME) },
                onToggleWakeWord = onToggleWakeWord,
                onSetVoiceSpeed = onSetVoiceSpeed,
                onReloadOfflineModels = onReloadOfflineModels,
                onTestNeuralVoice = onTestNeuralVoice,
                onRequestRuntimePermissions = onRequestRuntimePermissions,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onRequestAccessibilityPermission = onRequestAccessibilityPermission,
                onRequestStoragePermission = onRequestStoragePermission,
                onCreateModelFolders = onCreateModelFolders
            )
        }
    }
}

@Composable
private fun GravityHomeScreen(
    uiState: GravityUiState,
    permissions: PermissionSnapshot,
    onOpenChat: () -> Unit,
    onOpenSetup: () -> Unit,
    onInputChange: (String) -> Unit,
    onSubmit: (String?) -> Unit,
    onOrbTap: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onCancelPending: () -> Unit
) {
    val allCoreReady = permissions.micGranted && permissions.contactsGranted && permissions.callGranted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GravityColors.ObsidianBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x381D4ED8),
                            0.45f to Color(0x160C2A5E),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(540f, 560f),
                        radius = 780f
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to Color(0x331D4ED8),
                            0.78f to Color(0x8838BDF8),
                            1.0f to Color(0x550F2B63)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = if (allCoreReady) onToggleWakeWord else onOpenSetup,
                    shape = RoundedCornerShape(50),
                    color = if (uiState.isWakeServiceRunning) Color(0xAA0E2C5E) else GravityColors.GlassSurface,
                    border = BorderStroke(
                        1.dp,
                        when {
                            !allCoreReady -> GravityColors.WarningAmber.copy(alpha = 0.6f)
                            uiState.isWakeServiceRunning -> GravityColors.ElectricCyan
                            else -> GravityColors.GlassBorder
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !allCoreReady -> GravityColors.WarningAmber
                                        uiState.isWakeServiceRunning -> GravityColors.SuccessGreen
                                        else -> GravityColors.TextSecondary
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !allCoreReady -> "Setup Permissions"
                                uiState.isWakeServiceRunning -> "Hey Gravity • ON"
                                else -> "Wake Word • OFF"
                            },
                            color = GravityColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassCircleButton(
                        icon = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Chat",
                        onClick = onOpenChat
                    )
                    GlassCircleButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Setup",
                        onClick = onOpenSetup
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.9f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clickable(onClick = onOrbTap)
            ) {
                GravityOrb(
                    state = uiState.orbState,
                    size = 240.dp,
                    voiceAmplitude = uiState.voiceAmplitude
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = uiState.statusSubtitle,
                color = GravityColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.latestReply ?: uiState.statusHeadline,
                color = GravityColors.TextPrimary,
                fontSize = if (uiState.latestReply == null) 30.sp else 17.sp,
                lineHeight = if (uiState.latestReply == null) 36.sp else 24.sp,
                fontWeight = if (uiState.latestReply == null) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = uiState.pendingAction != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut()
            ) {
                uiState.pendingAction?.let { pending ->
                    PendingConfirmationCard(
                        pendingAction = pending,
                        onSelect = { selection -> onSubmit(selection) },
                        onCancel = onCancelPending
                    )
                }
            }

            if (uiState.pendingAction == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickCommands = listOf(
                        "YouTube kholo",
                        "Torch chalu karo",
                        "Volume badhao",
                        "Wi-Fi settings",
                        "Bluetooth settings"
                    )
                    quickCommands.forEach { cmd ->
                        Surface(
                            onClick = { onSubmit(cmd) },
                            shape = RoundedCornerShape(50),
                            color = GravityColors.GlassCard,
                            border = BorderStroke(1.dp, GravityColors.GlassBorder)
                        ) {
                            Text(
                                text = cmd,
                                color = GravityColors.IceCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            GlowingAskBar(
                value = uiState.inputText,
                onValueChange = onInputChange,
                onSend = { onSubmit(null) },
                onMicTap = onOrbTap,
                isListening = uiState.orbState == OrbState.LISTENING
            )

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun GravityChatScreen(
    uiState: GravityUiState,
    onBack: () -> Unit,
    onClearChat: () -> Unit,
    onInputChange: (String) -> Unit,
    onSubmit: (String?) -> Unit,
    onOrbTap: () -> Unit,
    onCancelPending: () -> Unit
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GravityColors.DeepNavyBg,
                        GravityColors.ObsidianBg,
                        Color(0xFF050E22)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassCircleButton(
                    icon = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "Back",
                    onClick = onBack
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Chat with Gravity",
                        color = GravityColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (uiState.isWakeServiceRunning) "Voice & Wake Word Active" else "Offline Commands Ready",
                        color = GravityColors.ElectricCyan,
                        fontSize = 11.sp
                    )
                }

                GlassCircleButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "Clear Chat",
                    onClick = onClearChat
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubbleRow(
                        message = msg,
                        onCopy = { clipboard.setText(AnnotatedString(msg.text)) }
                    )
                }
            }

            uiState.pendingAction?.let { pending ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PendingConfirmationCard(
                        pendingAction = pending,
                        onSelect = { onSubmit(it) },
                        onCancel = onCancelPending
                    )
                }
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                GlowingAskBar(
                    value = uiState.inputText,
                    onValueChange = onInputChange,
                    onSend = { onSubmit(null) },
                    onMicTap = onOrbTap,
                    isListening = uiState.orbState == OrbState.LISTENING
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            GravityOrb(
                state = OrbState.IDLE,
                size = 34.dp,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isUser) 20.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            ),
            color = if (message.isUser) Color(0xCC1E3A8A) else GravityColors.GlassCard,
            border = BorderStroke(
                1.dp,
                if (message.isUser) GravityColors.ElectricCyan.copy(alpha = 0.45f) else GravityColors.GlassBorder
            ),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    color = GravityColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (!message.isUser) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            tint = GravityColors.TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onCopy)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GravitySetupScreen(
    uiState: GravityUiState,
    permissions: PermissionSnapshot,
    onBack: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onSetVoiceSpeed: (Float) -> Unit,
    onReloadOfflineModels: () -> Unit,
    onTestNeuralVoice: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onCreateModelFolders: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GravityColors.ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleButton(
                icon = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "GRAVITY OS Setup",
                    color = GravityColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Voice Engines, Typing & Manual Storage",
                    color = GravityColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Section 1: Offline Voice, Wake Word & Neural TTS Control
        SectionCard(title = "1. Offline Voice & Wake Word Engines (No System TTS)") {
            StatusRow("Sherpa-ONNX Engine (app/libs)", uiState.diagnostics.sherpaAarLoaded)
            DiagnosticStatusRow("Wake Word (\"Gravity\")", uiState.diagnostics.wakeWordReady, uiState.diagnostics.wakeWordStatus)
            DiagnosticStatusRow("Offline Speech Recognition (STT)", uiState.diagnostics.sttReady, uiState.diagnostics.sttStatus)
            DiagnosticStatusRow("Offline Neural Voice (TTS)", uiState.diagnostics.ttsReady, uiState.diagnostics.ttsStatus)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Neural Voice Speed", color = GravityColors.TextPrimary, fontSize = 13.sp)
                Text(
                    text = String.format(Locale.US, "%.2fx", uiState.voiceSpeed),
                    color = GravityColors.ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = uiState.voiceSpeed,
                onValueChange = onSetVoiceSpeed,
                valueRange = 0.7f..1.4f,
                colors = SliderDefaults.colors(
                    thumbColor = GravityColors.ElectricCyan,
                    activeTrackColor = GravityColors.PlasmaBlue
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onToggleWakeWord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isWakeServiceRunning) Color(0xFFB91C1C) else GravityColors.ElectricCyan
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Hearing,
                    contentDescription = null,
                    tint = if (uiState.isWakeServiceRunning) Color.White else GravityColors.ObsidianBg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isWakeServiceRunning) "Stop Background \"Gravity\" Listener" else "Start Background \"Gravity\" Listener",
                    color = if (uiState.isWakeServiceRunning) Color.White else GravityColors.ObsidianBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onReloadOfflineModels,
                    colors = ButtonDefaults.buttonColors(containerColor = GravityColors.DeepCobalt),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reload Models", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = onTestNeuralVoice,
                    colors = ButtonDefaults.buttonColors(containerColor = GravityColors.NebulaViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Voice", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Section 2: Core Permissions
        SectionCard(title = "2. Core Permissions (Voice, Calls, Contacts, Camera)") {
            StatusRow("Microphone (Wake Word & Voice)", permissions.micGranted)
            StatusRow("Contacts (Naam se Call)", permissions.contactsGranted)
            StatusRow("Direct Phone Call", permissions.callGranted)
            StatusRow("Camera & Flashlight", permissions.cameraGranted)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRequestRuntimePermissions,
                colors = ButtonDefaults.buttonColors(containerColor = GravityColors.PlasmaBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Core Permissions", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        // Section 3: Android 15 Background, Floating Orb & Offline Voice Typing
        SectionCard(title = "3. Background, Floating Orb & Voice Typing (Android 15)") {
            StatusRow("Display Over Other Apps (Floating Orb + Background App Launch)", permissions.overlayGranted)
            StatusRow("Battery Unrestricted (Far-Field Wake Word)", permissions.batteryUnrestricted)
            StatusRow("Voice Typing Anywhere (Accessibility Service)", permissions.accessibilityGranted)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRequestOverlayPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = GravityColors.DeepCobalt),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Overlay", color = Color.White, fontSize = 13.sp)
                }
                Button(
                    onClick = onRequestBatteryOptimization,
                    colors = ButtonDefaults.buttonColors(containerColor = GravityColors.NebulaViolet),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Battery", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestAccessibilityPermission,
                colors = ButtonDefaults.buttonColors(containerColor = GravityColors.ElectricCyan),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = GravityColors.ObsidianBg, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (permissions.accessibilityGranted) "Voice Typing Accessibility • ON" else "Enable Offline Voice Typing (Accessibility)",
                    color = GravityColors.ObsidianBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Section 4: Manual Offline Models Storage
        SectionCard(title = "4. Manual Offline Models (Phone Storage)") {
            StatusRow("All Files Access (/GravityOS/models/)", permissions.storageGranted)
            Spacer(modifier = Modifier.height(6.dp))
            uiState.modelFolders.forEach { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.title, color = GravityColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(folder.relativePath, color = GravityColors.TextSecondary, fontSize = 11.sp)
                    }
                    Text(
                        text = if (folder.fileCount > 0) "${folder.fileCount} files" else if (folder.exists) "Empty" else "Missing",
                        color = if (folder.fileCount > 0) GravityColors.SuccessGreen else GravityColors.WarningAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRequestStoragePermission,
                    colors = ButtonDefaults.buttonColors(containerColor = GravityColors.DeepCobalt),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Storage Access", color = Color.White, fontSize = 13.sp)
                }
                Button(
                    onClick = onCreateModelFolders,
                    colors = ButtonDefaults.buttonColors(containerColor = GravityColors.ElectricCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = GravityColors.ObsidianBg, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Folders", color = GravityColors.ObsidianBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusRow(title: String, ready: Boolean, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = GravityColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = GravityColors.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ready) GravityColors.SuccessGreen else GravityColors.WarningAmber,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PendingConfirmationCard(
    pendingAction: PendingAction,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = GravityColors.GlassSurface,
        border = BorderStroke(1.dp, GravityColors.ElectricCyan.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirm Karein",
                    color = GravityColors.ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancel",
                    tint = GravityColors.TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onCancel)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (pendingAction) {
                is PendingAction.CallOptions -> {
                    pendingAction.candidates.forEachIndexed { idx, contact ->
                        Surface(
                            onClick = { onSelect((idx + 1).toString()) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x661D4ED8),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, tint = GravityColors.SuccessGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(contact.name, color = GravityColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(contact.number, color = GravityColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                is PendingAction.AppOptions -> {
                    pendingAction.candidates.forEachIndexed { idx, app ->
                        Surface(
                            onClick = { onSelect((idx + 1).toString()) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x661D4ED8),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Apps, contentDescription = null, tint = GravityColors.ElectricCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(app.label, color = GravityColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowingAskBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
    isListening: Boolean
) {
    val barGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0x801D4ED8),
            Color(0x9938BDF8),
            Color(0x802563EB)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xB30B1D42))
            .border(width = 1.4.dp, brush = barGradient, shape = RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Ask anything...",
                    color = GravityColors.IceCyan.copy(alpha = 0.65f),
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = GravityColors.TextPrimary,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(GravityColors.ElectricCyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (value.isNotBlank()) {
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GravityColors.ElectricCyan)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Send",
                    tint = GravityColors.ObsidianBg,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            IconButton(
                onClick = onMicTap,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isListening) GravityColors.ElectricCyan else Color(0x4438BDF8))
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Voice",
                    tint = if (isListening) GravityColors.ObsidianBg else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = GravityColors.GlassSurface,
        border = BorderStroke(1.dp, GravityColors.GlassBorder),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = GravityColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GravityColors.GlassCard,
        border = BorderStroke(1.dp, GravityColors.GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = GravityColors.ElectricCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = GravityColors.TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (granted) GravityColors.SuccessGreen else GravityColors.WarningAmber,
            modifier = Modifier.size(18.dp)
        )
    }
}
