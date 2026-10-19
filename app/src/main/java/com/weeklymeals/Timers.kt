package com.weeklymeals

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Cooking timers. Uses the system alarm clock so they ring even when the app is closed
 * or the screen is off. The alarm keeps sounding until you tap the notification or press Stop.
 */
object Timers {
    private const val CHANNEL = "cooking_timers"
    private const val EXTRA_KEY = "key"
    private const val EXTRA_LABEL = "label"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Cooking timers", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when a recipe step timer is done"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
        }
        nm.createNotificationChannel(ch)
    }

    private fun pending(ctx: Context, key: String, label: String): PendingIntent {
        val i = Intent(ctx, TimerReceiver::class.java)
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(
            ctx, key.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(ctx: Context, key: String, label: String, endAt: Long) {
        ensureChannel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, key, label)
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactOk) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        }
    }

    fun cancel(ctx: Context, key: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, key, ""))
        NotificationManagerCompat.from(ctx).cancel(key.hashCode())
        TimerAlarmService.stop(key)
    }

    internal fun ring(ctx: Context, key: String, label: String) {
        // Ring like an alarm clock (plays on silent). Falls back to a loud notification.
        if (TimerAlarmService.ring(ctx, key, label)) return
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle("⏰ Timer done")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until dismissed
        try {
            NotificationManagerCompat.from(ctx).notify(key.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    fun keyFromIntent(i: Intent) = i.getStringExtra(EXTRA_KEY).orEmpty()
    fun labelFromIntent(i: Intent) = i.getStringExtra(EXTRA_LABEL).orEmpty()
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timers.ring(context, Timers.keyFromIntent(intent), Timers.labelFromIntent(intent))
    }
}
