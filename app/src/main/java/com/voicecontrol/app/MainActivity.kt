package com.voicecontrol.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voicecontrol.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isListening = false

    private val permissionsNeeded = mutableListOf(Manifest.permission.RECORD_AUDIO).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startListeningService()
        } else {
            binding.statusText.text = "Status: Permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggleButton.setOnClickListener {
            if (!isListening) {
                if (hasAllPermissions()) {
                    startListeningService()
                } else {
                    permissionLauncher.launch(permissionsNeeded)
                }
            } else {
                stopListeningService()
            }
        }
    }

    private fun hasAllPermissions(): Boolean =
        permissionsNeeded.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startListeningService() {
        val intent = Intent(this, VoiceControlService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isListening = true
        binding.statusText.text = "Status: Listening…"
        binding.toggleButton.text = "Stop Listening"
    }

    private fun stopListeningService() {
        val intent = Intent(this, VoiceControlService::class.java)
        stopService(intent)
        isListening = false
        binding.statusText.text = "Status: Stopped"
        binding.toggleButton.text = "Start Listening"
    }
}
