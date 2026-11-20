package com.gravityos.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gravityos.assistant.core.GravityAccessibilityService
import com.gravityos.assistant.ui.screens.GravityRootHost
import com.gravityos.assistant.ui.screens.PermissionSnapshot
import com.gravityos.assistant.ui.theme.GravityOSTheme
import com.gravityos.assistant.viewmodel.GravityViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            GravityOSTheme {
                val vm: GravityViewModel = viewModel()
                val uiState by vm.uiState.collectAsState()
                var refreshTick by remember { mutableIntStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshTick++
                            vm.refreshStorageStatus()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    refreshTick++
                }

                val permissions = remember(refreshTick) {
                    buildPermissionSnapshot()
                }

                val requestCorePermissions = {
                    val needed = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.CAMERA
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        needed.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(needed.toTypedArray())
                }

                GravityRootHost(
                    uiState = uiState,
                    permissions = permissions,
                    onNavigate = vm::navigateTo,
                    onInputChange = vm::onInputChange,
                    onSubmit = vm::submitCommand,
                    onOrbTap = {
                        if (isGranted(Manifest.permission.RECORD_AUDIO)) {
                            vm.onOrbTap()
                        } else {
                            requestCorePermissions()
                        }
                    },
                    onToggleWakeWord = {
                        if (isGranted(Manifest.permission.RECORD_AUDIO)) {
                            vm.toggleWakeWordService()
                        } else {
                            requestCorePermissions()
                        }
                    },
                    onCancelPending = vm::cancelPendingAction,
                    onClearChat = vm::clearChat,
                    onSetVoiceSpeed = vm::setVoiceSpeed,
                    onReloadOfflineModels = vm::reloadOfflineModels,
                    onTestNeuralVoice = vm::testNeuralVoice,
                    onRequestRuntimePermissions = requestCorePermissions,
                    onRequestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    },
                    onRequestBatteryOptimization = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    onRequestAccessibilityPermission = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRequestStoragePermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } catch (_: Exception) {
                                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    },
                    onCreateModelFolders = {
                        vm.createModelFolders()
                        refreshTick++
                    }
                )
            }
        }
    }

    private fun buildPermissionSnapshot(): PermissionSnapshot {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val storageOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        return PermissionSnapshot(
            micGranted = isGranted(Manifest.permission.RECORD_AUDIO),
            contactsGranted = isGranted(Manifest.permission.READ_CONTACTS),
            callGranted = isGranted(Manifest.permission.CALL_PHONE),
            cameraGranted = isGranted(Manifest.permission.CAMERA),
            overlayGranted = Settings.canDrawOverlays(this),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(packageName),
            accessibilityGranted = isAccessibilityEnabled(),
            storageGranted = storageOk
        )
