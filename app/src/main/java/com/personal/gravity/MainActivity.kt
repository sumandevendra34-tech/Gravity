package com.personal.gravity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var orbView: GravityOrbView
    private lateinit var statusText: TextView
    private lateinit var modeBadge: TextView
    private lateinit var transcriptText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var overlayPermBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        orbView = findViewById(R.id.gravityOrb)
        statusText = findViewById(R.id.statusText)
        modeBadge = findViewById(R.id.modeBadge)
        transcriptText = findViewById(R.id.transcriptText)
        toggleBtn = findViewById(R.id.toggleServiceBtn)
        overlayPermBtn = findViewById(R.id.overlayPermBtn)

        requestRequiredPermissions()

        GravityVoiceService.onAudioLevelUpdate = { rms ->
            runOnUiThread { orbView.updateAudioLevel(rms) }
        }

        GravityVoiceService.onStateUpdate = { orbState, isAwake, message ->
            runOnUiThread {
                orbView.setOrbState(orbState)
                statusText.text = when (orbState) {
                    GravityOrbView.OrbState.AWAKE -> "Gravity Awake"
                    GravityOrbView.OrbState.EXECUTING -> "Executing Action"
                    GravityOrbView.OrbState.PASSIVE -> "Listening Passive"
                }
                modeBadge.text = if (isAwake) "AWAKE" else "ONLINE"
                modeBadge.setTextColor(
                    if (isAwake) Color.parseColor("#00F0FF") else Color.parseColor("#38BDF8")
                )
                transcriptText.text = message
            }
        }

        orbView.setOnClickListener {
            if (GravityVoiceService.isRunning) {
                GravityVoiceService.manualWakeTrigger?.invoke()
            } else {
                startGravityCore()
            }
        }

        overlayPermBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        toggleBtn.setOnClickListener {
            if (!GravityVoiceService.isRunning) {
                startGravityCore()
            } else {
                stopGravityCore()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermBtn.visibility = if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
        syncUiWithServiceState()
    }

    private fun startGravityCore() {
        if (!hasMicrophonePermission()) {
            requestRequiredPermissions()
            Toast.makeText(
                this,
                "Please allow Microphone, Contacts & Phone permissions",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val serviceIntent = Intent(this, GravityVoiceService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        syncUiWithServiceState(forceRunning = true)
        transcriptText.text = "Say “Hello Gravity” or tap the Orb to give any command."
    }

    private fun stopGravityCore() {
        stopService(Intent(this, GravityVoiceService::class.java))
        orbView.setOrbState(GravityOrbView.OrbState.PASSIVE)
        toggleBtn.text = "Activate Gravity Core"
        toggleBtn.setBackgroundColor(Color.parseColor("#00F0FF"))
        toggleBtn.setTextColor(Color.parseColor("#04060A"))
        statusText.text = "Core Standby"
        modeBadge.text = "OFFLINE"
        modeBadge.setTextColor(Color.parseColor("#94A3B8"))
        transcriptText.text = "Gravity Core is offline."
    }

    private fun syncUiWithServiceState(forceRunning: Boolean = false) {
        if (GravityVoiceService.isRunning || forceRunning) {
            toggleBtn.text = "Shutdown Gravity Core"
            toggleBtn.setBackgroundColor(Color.parseColor("#1E293B"))
            toggleBtn.setTextColor(Color.parseColor("#F8FAFC"))
            statusText.text = "Listening Passive"
            modeBadge.text = "ONLINE"
            modeBadge.setTextColor(Color.parseColor("#38BDF8"))
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 701)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && hasMicrophonePermission() && !GravityVoiceService.isRunning) {
            startGravityCore()
        }
    }
}
