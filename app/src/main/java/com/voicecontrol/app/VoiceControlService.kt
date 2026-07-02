package com.voicecontrol.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoiceControlService : Service(), RecognitionListener {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var isDestroyed = false
    private val handler = Handler(Looper.getMainLooper())

    // कमांड मैपिंग – ज़रूरत पड़ने पर और शब्द जोड़ो
    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp"
    )

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        initModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "voice_control_vosk",
                "Voice Control (Vosk)",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, "voice_control_vosk")
            .setContentTitle("Voice Control")
            .setContentText("Listening for commands…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun initModel() {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(this, "model-en-us", "model",
            { mdl ->
                model = mdl
                startVoskListening()
                Toast.makeText(this, "Vosk model ready", Toast.LENGTH_SHORT).show()
            },
            { err ->
                Log.e("Vosk", "Failed to unpack model", err)
                stopSelf()
            }
        )
    }

    private fun startVoskListening() {
        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: IOException) {
            Log.e("Vosk", "Failed to start Vosk recognizer", e)
        }
    }

    // RecognitionListener callbacks
    override fun onPartialResult(hypothesis: String) {
        // Vosk देता है JSON, हम सिर्फ "text" फील्ड निकालेंगे
        val spoken = extractText(hypothesis)
        if (spoken.isNotEmpty()) {
            handleCommand(spoken)
        }
    }

    override fun onResult(hypothesis: String) {
        val spoken = extractText(hypothesis)
        if (spoken.isNotEmpty()) {
            handleCommand(spoken)
        }
    }

    override fun onFinalResult(hypothesis: String) {
        // अगर Vosk रुक जाए, तो दोबारा शुरू करें
        restartVosk()
    }

    override fun onError(exception: Exception) {
        Log.e("Vosk", "Recognition error", exception)
        restartVosk()
    }

    override fun onTimeout() {
        restartVosk()
    }

    private fun restartVosk() {
        speechService?.stop()
        speechService = null
        if (!isDestroyed) {
            handler.postDelayed({ startVoskListening() }, 1000)
        }
    }

    // Vosk JSON से "text" निकालो
    private fun extractText(json: String): String {
        return try {
            val start = json.indexOf("\"text\" : \"") + 10
            val end = json.indexOf("\"", start)
            if (start >= 10 && end > start) {
                json.substring(start, end).lowercase().trim()
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun handleCommand(spoken: String) {
        // डिबग के लिए Toast (बाद में हटा सकते हो)
        Toast.makeText(this, "Heard: $spoken", Toast.LENGTH_SHORT).show()

        for ((app, packageName) in appPackages) {
            if (spoken.contains(app) ||
                (app == "youtube" && (spoken.contains("youtub") || spoken.contains("yt") || spoken.contains("यूट्यूब"))) ||
                (app == "chrome" && (spoken.contains("chrom") || spoken.contains("क्रोम"))) ||
                (app == "instagram" && (spoken.contains("insta") || spoken.contains("इंस्टाग्राम"))) ||
                (app == "whatsapp" && (spoken.contains("whatsap") || spoken.contains("whats app") || spoken.contains("व्हाट्सएप")))
            ) {
                openApp(packageName, app)
                return
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
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model = null
        super.onDestroy()
    }
}