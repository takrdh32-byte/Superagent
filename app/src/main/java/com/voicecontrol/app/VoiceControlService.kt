package com.voicecontrol.app

import android.app.*
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File

class VoiceControlService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp"
    )

    // वही फ़ाइल जो Python स्क्रिप्ट लिखती है
    private val commandFile = File("/sdcard/voice_commands.txt")

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channel = NotificationChannel("voice", "Voice Control", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, "voice")
            .setContentTitle("Voice Control (Python)")
            .setContentText("Waiting for command...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun startWatching() {
        isRunning = true
        Thread {
            var lastCommand = ""
            while (isRunning) {
                try {
                    if (commandFile.exists()) {
                        val text = commandFile.readText().trim().lowercase()
                        if (text.isNotEmpty() && text != lastCommand) {
                            lastCommand = text
                            handler.post { handleCommand(text) }
                        }
                    }
                } catch (_: Exception) {}
                Thread.sleep(800)
            }
        }.start()
    }

    private fun handleCommand(spoken: String) {
        Toast.makeText(this, "Heard: $spoken", Toast.LENGTH_SHORT).show()
        for ((app, packageName) in appPackages) {
            if (spoken.contains(app)) {
                openApp(packageName, app)
                return
            }
        }
        // हिंदी/गलतियों के लिए
        val aliases = mapOf(
            "youtube" to listOf("youtub", "yt", "यूट्यूब"),
            "chrome" to listOf("chrom", "क्रोम", "browser"),
            "instagram" to listOf("insta", "इंस्टाग्राम"),
            "whatsapp" to listOf("whatsap", "whats app", "व्हाट्सएप")
        )
        for ((app, words) in aliases) {
            for (word in words) {
                if (spoken.contains(word)) {
                    openApp(appPackages[app]!!, app)
                    return
                }
            }
        }
    }

    private fun openApp(packageName: String, name: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(this, "Opening $name", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "$name not installed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}