package com.voicecontrol.app

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat

class VoiceControlService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    // ऐप का नाम -> पैकेज नेम
    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "facebook" to "com.facebook.katana",
        "gmail" to "com.google.android.gm",
        "google" to "com.google.android.googlequicksearchbox",
        "maps" to "com.google.android.apps.maps",
        "calculator" to "com.android.calculator2",
        "calendar" to "com.android.calendar"
    )

    // ऐप के हिंदी और आम नाम, जो लोग बोल सकते हैं
    private val appAliases = mapOf(
        "youtube" to listOf("youtube", "yt", "youtub", "यूट्यूब", "ytube"),
        "chrome" to listOf("chrome", "chrom", "क्रोम", "google chrome", "browser"),
        "instagram" to listOf("instagram", "insta", "इंस्टाग्राम", "instagrm"),
        "whatsapp" to listOf("whatsapp", "whatsap", "whats app", "व्हाट्सएप", "whatapp"),
        "facebook" to listOf("facebook", "fb", "फेसबुक", "face book"),
        "gmail" to listOf("gmail", "mail", "email", "जीमेल", "g mail"),
        "google" to listOf("google", "गूगल", "googal", "assistant"),
        "maps" to listOf("maps", "map", "मैप", "gps", "navigation"),
        "calculator" to listOf("calculator", "calc", "कैलकुलेटर", "cal"),
        "calendar" to listOf("calendar", "cal", "कैलेंडर", "calendar")
    )

    // एक्शन वर्ड्स
    private val openWords = listOf("open", "kholo", "khol", "kholna", "खोलो", "खोल", "खोलना", "start", "launch", "play", "chalu", "चालू", "शुरू", "shuru", "chalana", "चलाना", "dikhao", "दिखाओ", "dekhna", "देखना", "jana", "जाना")
    private val closeWords = listOf("close", "band", "bandh", "बंद", "बंद करो", "बंद करना", "ruk", "रुक", "stop", "hatana", "हटाना", "mitana", "मिटाना")

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        setupRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        startListening()
    }

    private fun startListening() {
        if (isDestroyed) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
            scheduleRestart(2000)
        }
    }

    private fun scheduleRestart(delayMs: Long = 2000) {
        if (isDestroyed) return
        handler.postDelayed({
            startListening()
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                scheduleRestart(1500)
            } else {
                scheduleRestart(2500)
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = matches?.firstOrNull()?.lowercase()?.trim()
            if (!spoken.isNullOrEmpty()) {
                Log.d(TAG, "Heard: $spoken")
                handleCommand(spoken)
            }
            scheduleRestart(600)
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    }

    // ========== सुपर-स्मार्ट कमांड हैंडलिंग ==========
    private fun handleCommand(spoken: String) {
        val words = spoken.split(" ")
        var targetApp: String? = null
        var wantsOpen = false
        var wantsClose = false

        // 1. हर शब्द चेक करो
        for (word in words) {
            // क्या यह कोई एक्शन वर्ड है?
            if (openWords.any { it == word }) {
                wantsOpen = true
            }
            if (closeWords.any { it == word }) {
                wantsClose = true
            }

            // क्या यह कोई ऐप है?
            if (targetApp == null) {
                for ((key, aliases) in appAliases) {
                    if (aliases.any { alias -> levenshtein(word, alias) <= 2 || word.contains(alias) || alias.contains(word) }) {
                        targetApp = key
                        break
                    }
                }
            }
        }

        // 2. अगर कोई एक्शन नहीं बोला, तो डिफ़ॉल्ट "open" मानो
        if (!wantsOpen && !wantsClose) {
            wantsOpen = true
        }

        // 3. अगर ऐप मिल गई और हमें खोलना है
        if (targetApp != null && wantsOpen) {
            val packageName = appPackages[targetApp]
            if (packageName != null) {
                openApp(packageName)
            }
        }

        // 4. बंद करने का प्रयास (हो सके तो)
        if (targetApp != null && wantsClose) {
            val packageName = appPackages[targetApp]
            if (packageName != null) {
                closeApp(packageName)
            }
        }
    }

    // फ़ज़ी मैचिंग
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun openApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Log.e(TAG, "App not installed: $packageName")
        }
    }

    private fun closeApp(packageName: String) {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "closeApp failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VoiceControlService"
        private const val CHANNEL_ID = "voice_control_channel"
        private const val NOTIF_ID = 1
    }
}