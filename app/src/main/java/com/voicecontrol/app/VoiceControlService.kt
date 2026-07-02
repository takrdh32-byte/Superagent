package com.voicecontrol.app

import android.app.*
import android.content.Intent
import android.os.Bundle
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

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false

    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp"
    )

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channel = NotificationChannel("voice", "Voice Control", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, "voice")
            .setContentTitle("Voice Control")
            .setContentText("Listening…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // एरर को हैंडल करो और दोबारा सुनना शुरू करो
                restartListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.lowercase()?.trim() ?: ""
                if (spoken.isNotEmpty()) {
                    Toast.makeText(this@VoiceControlService, "Heard: $spoken", Toast.LENGTH_SHORT).show()
                    handleCommand(spoken)
                }
                restartListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        startListening()
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun restartListening() {
        isListening = false
        speechRecognizer?.cancel()
        handler.postDelayed({ startListening() }, 500) // थोड़ा delay
    }

    private fun handleCommand(spoken: String) {
        for ((app, packageName) in appPackages) {
            if (spoken.contains(app)) {
                openApp(packageName, app)
                return
            }
        }
        // कुछ आम गलतियों के लिए
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
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}