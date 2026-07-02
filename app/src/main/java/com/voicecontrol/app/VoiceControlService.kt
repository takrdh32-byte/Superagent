package com.voicecontrol.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class VoiceControlService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    // तुम्हारी पसंद की ऐप्स
    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp"
    )

    // कौन से शब्द सुनने पर ऐप खोलनी है
    private val keywords = listOf("open", "kholo", "खोलो", "start", "launch", "play", "dikhao", "दिखाओ")

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        setupRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "voice_control",
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, "voice_control")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition NOT available", Toast.LENGTH_LONG).show()
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                scheduleRestart(1500)
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.lowercase()?.trim() ?: ""
                if (spoken.isNotEmpty()) {
                    Toast.makeText(this@VoiceControlService, "Heard: $spoken", Toast.LENGTH_SHORT).show()
                    handleCommand(spoken)
                }
                scheduleRestart(500)
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        startListening()
    }

    private fun startListening() {
        if (isDestroyed || recognizer == null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            scheduleRestart(2000)
        }
    }

    private fun scheduleRestart(delayMs: Long = 2000) {
        if (isDestroyed) return
        handler.postDelayed({ startListening() }, delayMs)
    }

    private fun handleCommand(spoken: String) {
        // पहले चेक करो कि कोई ऐप का नाम तो नहीं बोला गया
        for ((app, packageName) in appPackages) {
            if (spoken.contains(app)) {
                // अगर सिर्फ नाम बोला, या साथ में कोई कीवर्ड भी है
                openApp(packageName, app)
                return
            }
        }
        // अगर कोई हिंदी या मिलता-जुलता नाम है, तो उसे भी पकड़ो
        val aliases = mapOf(
            "youtube" to listOf("youtub", "yt", "यूट्यूब"),
            "chrome" to listOf("chrom", "क्रोम", "browser"),
            "instagram" to listOf("insta", "इंस्टाग्राम"),
            "whatsapp" to listOf("whatsap", "whats app", "व्हाट्सएप")
        )
        for ((app, list) in aliases) {
            for (alias in list) {
                if (spoken.contains(alias)) {
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
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
}