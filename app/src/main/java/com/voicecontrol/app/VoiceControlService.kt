package com.voicecontrol.app

import android.app.*
import android.content.Intent
import android.os.AsyncTask
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import edu.cmu.pocketsphinx.*
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference

class VoiceControlService : Service(), RecognitionListener {

    private var recognizer: SpeechRecognizer? = null

    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp"
    )

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        SetupTask(this).execute()
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

    private class SetupTask(service: VoiceControlService) : AsyncTask<Void, Void, Exception>() {
        private val reference = WeakReference(service)

        override fun doInBackground(vararg params: Void?): Exception? {
            val svc = reference.get() ?: return null
            try {
                val assets = Assets(svc)
                val assetDir = assets.syncAssets()
                svc.setupRecognizer(assetDir)
            } catch (e: IOException) {
                return e
            }
            return null
        }

        override fun onPostExecute(result: Exception?) {
            val svc = reference.get() ?: return
            if (result != null) {
                Toast.makeText(svc, "Init failed: ${result.message}", Toast.LENGTH_SHORT).show()
                svc.stopSelf()
            } else {
                svc.recognizer?.startListening("youtube")
                Toast.makeText(svc, "Ready – Speak a command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecognizer(assetsDir: File) {
        recognizer = SpeechRecognizerSetup.defaultSetup()
            .setAcousticModel(File(assetsDir, "en-us-ptm"))
            .setDictionary(File(assetsDir, "cmudict-en-us.dict"))
            .recognizer

        recognizer?.addKeyphraseSearch("youtube", "open youtube")
        recognizer?.addKeyphraseSearch("chrome", "open chrome")
        recognizer?.addKeyphraseSearch("instagram", "open instagram")
        recognizer?.addKeyphraseSearch("whatsapp", "open whatsapp")
        recognizer?.addListener(this)
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        val phrase = hypothesis?.hypstr?.lowercase()?.trim() ?: return
        Toast.makeText(this, "Heard: $phrase", Toast.LENGTH_SHORT).show()

        for ((app, packageName) in appPackages) {
            if (phrase.contains(app)) {
                openApp(packageName, app)
                recognizer?.stop()
                recognizer?.startListening("youtube")
                return
            }
        }
    }

    override fun onResult(hypothesis: Hypothesis?) {}
    override fun onBeginningOfSpeech() {}
    override fun onEndOfSpeech() {}

    override fun onError(e: Exception?) {
        recognizer?.stop()
        recognizer?.startListening("youtube")
    }

    override fun onTimeout() {
        recognizer?.stop()
        recognizer?.startListening("youtube")
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
        recognizer?.cancel()
        recognizer?.shutdown()
        super.onDestroy()
    }
}