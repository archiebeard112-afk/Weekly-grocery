package com.weeklymeals

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.ref.WeakReference

/**
 * The pop-up for a thaw reminder. It shows over the lock screen (turning the
 * screen on) and over other apps, and only goes away when you tap one of its buttons: the back
 * button and taps outside the card do nothing.
 */
class AlertActivity : ComponentActivity() {
    private var alert by mutableStateOf<Alert?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setFinishOnTouchOutside(false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {} // only a button closes it
        })
        alert = Alert.from(intent)
        if (alert == null) {
            finish()
            return
        }
        current = WeakReference(this)
        setContent { WeeklyTheme { alert?.let { Card(it) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Alert.from(intent)?.let { alert = it }
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun Card(a: Alert) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = Radius.Hero, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp)) {
                    Text(
                        "THAW REMINDER",
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        a.title, modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp),
                    )
                    Column(
                        Modifier.weight(1f, fill = false).padding(top = 12.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        a.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(DialogAction("Moved it", ActionKind.Primary) { Thaw.onDone(this@AlertActivity, a.dates); finish() }, Modifier.height(56.dp))
                        ActionButton(DialogAction("Snooze ${a.snooze} min") { Thaw.onSnooze(this@AlertActivity, a.dates); finish() })
                    }
                }
            }
        }
    }

    /** What the card shows. [dates] are the thaw days its buttons apply to (empty for a test). */
    data class Alert(val kind: String, val title: String, val lines: List<String>, val dates: List<String>, val snooze: Int) {
        companion object {
            fun from(i: Intent?): Alert? {
                val kind = i?.getStringExtra(EXTRA_KIND) ?: return null
                return Alert(
                    kind,
                    i.getStringExtra(EXTRA_TITLE).orEmpty(),
                    i.getStringArrayExtra(EXTRA_LINES)?.toList().orEmpty(),
                    i.getStringArrayExtra(EXTRA_DATES)?.toList().orEmpty(),
                    i.getIntExtra(EXTRA_SNOOZE, 10),
                )
            }
        }
    }

    companion object {
        const val KIND_THAW = "thaw"
        private const val EXTRA_KIND = "alert_kind"
        private const val EXTRA_TITLE = "alert_title"
        private const val EXTRA_LINES = "alert_lines"
        private const val EXTRA_DATES = "alert_dates"
        private const val EXTRA_SNOOZE = "alert_snooze"

        @Volatile private var current: WeakReference<AlertActivity>? = null

        fun intent(ctx: Context, kind: String, title: String, lines: List<String>, dates: List<String> = emptyList(), snooze: Int = 10): Intent =
            Intent(ctx, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_LINES, lines.toTypedArray())
                .putExtra(EXTRA_DATES, dates.toTypedArray())
                .putExtra(EXTRA_SNOOZE, snooze)

        fun pending(ctx: Context, code: Int, i: Intent): PendingIntent =
            PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        /**
         * Opens the card now. Android allows this from the background only with "Display over
         * other apps" (or while the app is on screen) and quietly ignores it otherwise; the
         * notification's full-screen intent still opens the card on the lock screen.
         */
        fun popUp(ctx: Context, i: Intent) {
            runCatching { ctx.startActivity(i) }
        }

        /** Closes the card if it's showing this kind of alert (a button on the notification was used). */
        fun close(kind: String) {
            val a = current?.get() ?: return
            a.runOnUiThread { if (a.alert?.kind == kind) a.finish() }
        }

        /** Whether the card can pop up over other apps. */
        fun canPopUp(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        /** Whether the card can open on the lock screen (Android 14+ lets you turn this off per app). */
        fun canUseLockScreen(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        fun lockScreenSettings(ctx: Context): Intent =
            (if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName))
            else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
