package com.weeklymeals

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Rings a finished cooking timer like an alarm clock: on the alarm sound channel, so it plays
 * even when the phone is on silent or vibrate (at your alarm volume). Keeps ringing until you
 * tap Stop, stop the timer in the app, or tap the cooking bubble. Gives up after 5 minutes.
 */
class TimerAlarmService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val ringing = linkedMapOf<String, String>() // timer key -> label

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
                ringing[key] = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                showNotification()
                startSound()
                main.removeCallbacks(giveUp)
                main.postDelayed(giveUp, 5 * 60_000L)
            }
            ACTION_STOP -> stopAll()
            else -> if (ringing.isEmpty()) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSound()
        main.removeCallbacks(giveUp)
        instance = null
        super.onDestroy()
    }

    private val giveUp = Runnable { stopAll() }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            // The sound comes from the alarm player, so the notification itself is quiet.
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Timer alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shows while a finished timer is ringing"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TimerAlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(CookOverlay.EXTRA_OPEN_COOK, true)
                .putExtra(EXTRA_STOP_ALARM, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val labels = ringing.values.filter { it.isNotBlank() }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("⏰ Timer done")
            .setContentText(labels.joinToString(" · ").ifBlank { "Your cooking timer is done" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun startSound() {
        if (player == null) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@TimerAlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            }
            runCatching {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400), 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            }
        }
    }

    private fun stopSound() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    /** Stops one timer's ringing (the rest keep going). */
    fun stopKey(key: String) {
        ringing.remove(key)
        if (ringing.isEmpty()) stopAll() else showNotification()
    }

    /** Silences everything and clears the finished timers from the app. */
    fun stopAll() {
        val keys = ringing.keys.toList()
        ringing.clear()
        stopSound()
        if (keys.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { Store(applicationContext).update { s -> s.copy(timers = s.timers - keys.toSet(), timerStarts = s.timerStarts - keys.toSet()) } }
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "timer_alarm"
        private const val NOTIFICATION_ID = 4343
        private const val ACTION_RING = "ring"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_LABEL = "label"
        const val EXTRA_STOP_ALARM = "stop_alarm"

        @Volatile private var instance: TimerAlarmService? = null

        val isRinging: Boolean get() = instance?.ringing?.isNotEmpty() == true

        /** Starts ringing. False if Android didn't allow it (then a normal notification is used). */
        fun ring(ctx: Context, key: String, label: String): Boolean = runCatching {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, TimerAlarmService::class.java).setAction(ACTION_RING)
                    .putExtra(EXTRA_KEY, key).putExtra(EXTRA_LABEL, label),
            )
        }.isSuccess

        fun stop(key: String) { instance?.let { s -> s.main.post { s.stopKey(key) } } }

        fun stopAll() { instance?.let { s -> s.main.post { s.stopAll() } } }
    }
}
