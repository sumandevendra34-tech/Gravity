package com.gravityos.assistant.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings

class PhoneControlManager(private val context: Context) {

    private var torchEnabled: Boolean = false

    fun setTorch(enable: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findFlashCameraId(cameraManager)
                ?: return "Is device me flashlight available nahi mili."
            cameraManager.setTorchMode(cameraId, enable)
            torchEnabled = enable
            if (enable) "Flashlight chalu kar di hai." else "Flashlight band kar di hai."
        } catch (e: Exception) {
            "Flashlight control karne me dikkat aayi: camera abhi busy ho sakta hai."
        }
    }

    fun toggleTorch(): String = setTorch(!torchEnabled)

    private fun findFlashCameraId(cameraManager: CameraManager): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun increaseVolume(): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = ((current.toFloat() / max.coerceAtLeast(1)) * 100).toInt()
        return "Volume badha diya hai ($percent%)."
    }

    fun decreaseVolume(): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = ((current.toFloat() / max.coerceAtLeast(1)) * 100).toInt()
        return "Volume kam kar diya hai ($percent%)."
    }

    fun setVolumeMax(): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        return "Volume full (100%) kar diya hai."
    }

    fun muteVolume(): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        return "Media volume mute kar diya hai."
    }

    fun openWifiSettings(): String {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        return startSafeIntent(intent, Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi settings khol di hain.")
    }

    fun openBluetoothSettings(): String {
        return startSafeIntent(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            "Bluetooth settings khol di hain."
        )
    }

    fun openHotspotSettings(): String {
        val tetherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName("com.android.settings", "com.android.settings.TetherSettings")
        }
        return startSafeIntent(
            tetherIntent,
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            "Hotspot aur Tethering settings khol di hain."
        )
    }

    fun openDisplaySettings(): String {
        return startSafeIntent(
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            "Display settings khol di hain."
        )
    }

    fun openSystemSettings(): String {
        return startSafeIntent(
            Intent(Settings.ACTION_SETTINGS),
            null,
            "Phone Settings khol di hain."
        )
    }

    fun openCamera(): String {
        return startSafeIntent(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            null,
            "Camera khol diya hai."
        )
    }

    private fun startSafeIntent(primary: Intent, fallback: Intent?, successMessage: String): String {
        return try {
            primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(primary)
            successMessage
        } catch (_: Exception) {
            if (fallback != null) {
                try {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                    successMessage
                } catch (_: Exception) {
                    "Ye settings page direct open nahi ho paya."
                }
            } else {
                "Ye option is device par direct open nahi ho paya."
            }
        }
    }
}
