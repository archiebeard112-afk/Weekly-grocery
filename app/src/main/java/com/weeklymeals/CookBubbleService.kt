package com.weeklymeals

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The cooking bubble outside the app. While you're cooking, a small service keeps a red pot
 * bubble floating over your home screen and other apps (needs "Display over other apps").
 * Tap it to jump back to your steps, drag it to move it, hold it to hide it until next time.
 */
object CookOverlay {
    /** True while the app is on screen (the in-app bubble shows then instead). */
    val appVisible = MutableStateFlow(true)

    /** What's cooking, or null when you're not. */
    data class Info(val id: Int, val name: String, val ends: List<Long>)
    val info = MutableStateFlow<Info?>(null)

    /** Recipe the floating bubble was hidden for (by holding it). */
    @Volatile var hiddenFor: Int? = null

    fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** The Android settings page to allow the floating bubble. */
    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts or stops the service to match what's cooking. Call while the app is on screen. */
    fun sync(ctx: Context) {
        val i = Intent(ctx, CookBubbleService::class.java)
        if (info.value != null && canDraw(ctx)) {
            runCatching { ContextCompat.startForegroundService(ctx, i) }
        } else {
            ctx.stopService(i)
        }
    }

    const val EXTRA_OPEN_COOK = "open_cook"

    /** A view model for the mini cook window, kept for as long as the app's process runs. */
    @Volatile private var sharedVm: MealsViewModel? = null
    fun vm(app: android.app.Application): MealsViewModel =
        sharedVm ?: synchronized(this) { sharedVm ?: MealsViewModel(app).also { sharedVm = it } }
}

/** Lets a Compose view live in a window owned by a service (no activity). */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }
    fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
}

class CookBubbleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var pill: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    // The mini cook window that opens from the bubble.
    private var panel: View? = null
    private var panelOwner: OverlayOwner? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        startAsForeground(CookOverlay.info.value?.name)
        scope.launch {
            combine(CookOverlay.appVisible, CookOverlay.info) { visible, info -> visible to info }.collect { (visible, info) ->
                if (info == null) { stopSelf(); return@collect }
                startAsForeground(info.name)
                val show = !visible && CookOverlay.canDraw(this@CookBubbleService) && CookOverlay.hiddenFor != info.id
                if (show) showBubble() else hideBubble()
            }
        }
        // Timer countdown on the bubble.
        scope.launch {
            var blink = false
            while (true) {
                blink = !blink
                updatePill(blink)
                delay(500)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        hideBubble()
        scope.cancel()
        super.onDestroy()
    }

    /** The service has to show a notification; it doubles as a way back to your recipe. */
    private fun startAsForeground(name: String?) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Cooking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while you're cooking a recipe"
                    setShowBadge(false)
                },
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_fg_mono)
            .setContentTitle("Cooking" + (name?.let { " $it" } ?: ""))
            .setContentText("Tap to go back to your steps")
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 7, openIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openIntent() = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(CookOverlay.EXTRA_OPEN_COOK, true)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubble != null) return
        val label = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFF1EFE8.toInt())
            setPadding(dp(8), dp(2), dp(8), dp(2))
            gravity = Gravity.CENTER
            // Same-width digits so the pill doesn't jiggle as it counts down.
            fontFeatureSettings = "tnum"
            visibility = View.GONE
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_cook_bubble)
            elevation = dp(6).toFloat()
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Pill sized to its text and centered over the pot (not stretched across the bubble).
            addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)).apply { topMargin = dp(4) })
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val metrics = resources.displayMetrics
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX ?: (metrics.widthPixels - dp(76))
            y = savedY ?: (metrics.heightPixels * 0.62f).toInt()
        }

        // Tap: back to the app. Drag: move. Hold: hide until you cook again.
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val hold = Runnable {
            if (!moved) {
                CookOverlay.hiddenFor = CookOverlay.info.value?.id
                hideBubble()
                Toast.makeText(this, "Bubble hidden. It's still in the app.", Toast.LENGTH_SHORT).show()
            }
        }
        box.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false
                    main.postDelayed(hold, 650)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) { moved = true; main.removeCallbacks(hold); closePanel() }
                    if (moved && bubble != null) {
                        p.x = (startX + dx).toInt().coerceIn(0, metrics.widthPixels - dp(64))
                        p.y = (startY + dy).toInt().coerceIn(0, metrics.heightPixels - dp(100))
                        runCatching { wm.updateViewLayout(box, p) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(hold)
                    if (moved) { savedX = p.x; savedY = p.y }
                    else if (e.action == MotionEvent.ACTION_UP && bubble != null) {
                        // Tap: silence a ringing timer, and open or close the mini cook window.
                        if (TimerAlarmService.isRinging) TimerAlarmService.stopAll()
                        if (panel == null) openPanel() else closePanel()
                    }
                }
            }
            true
        }
        runCatching { wm.addView(box, p) }.onSuccess {
            bubble = box; pill = label; params = p
            updatePill(false)
        }
    }

    /** The mini cook window: above the bubble (or below it when the bubble is near the top). */
    private fun openPanel() {
        val bp = params ?: return
        val m = resources.displayMetrics
        val w = (m.widthPixels * 0.88f).toInt()
        val h = (m.heightPixels * 0.5f).toInt()
        val bubbleH = bubble?.height?.takeIf { it > 0 } ?: dp(90)
        val above = bp.y + bubbleH / 2 > m.heightPixels / 2
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (m.widthPixels - w) / 2
            y = if (above) (bp.y - h - dp(6)).coerceAtLeast(dp(24)) else (bp.y + bubbleH + dp(6)).coerceAtMost(m.heightPixels - h)
        }
        val owner = OverlayOwner().also { it.start() }
        val vm = CookOverlay.vm(application)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                WeeklyTheme {
                    val st by vm.state.collectAsState()
                    val id = st.cookingId
                    if (id != null) MiniCook(vm, st, id) { closePanel(); runCatching { startActivity(openIntent()) } }
                }
            }
        }
        runCatching { wm.addView(view, p) }.onSuccess {
            panel = view; panelOwner = owner
            // Keep the bubble on top of the window.
            bubble?.let { b -> runCatching { wm.removeView(b); wm.addView(b, bp) } }
        }.onFailure { owner.stop() }
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panelOwner?.stop()
        panel = null; panelOwner = null
    }

    private fun hideBubble() {
        closePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null; pill = null; params = null
    }

    /** Next timer for this recipe: "4:32", or a flashing red "Done!". */
    private fun updatePill(blink: Boolean) {
        val label = pill ?: return
        val now = System.currentTimeMillis()
        val next = CookOverlay.info.value?.ends?.minOrNull()
        if (next == null) { label.visibility = View.GONE; return }
        label.visibility = View.VISIBLE
        val bg = GradientDrawable().apply { cornerRadius = dp(10).toFloat() }
        if (next <= now) {
            label.text = "Done!"
            bg.setColor(0xFFE24B4A.toInt())
            label.setTextColor(0xFFFFFFFF.toInt())
            label.alpha = if (blink) 1f else 0.4f
        } else {
            val sec = (next - now + 999) / 1000
            val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
            label.text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
            bg.setColor(0xFF444441.toInt())
            label.setTextColor(0xFFF1EFE8.toInt())
            label.alpha = 1f
        }
        label.background = bg
    }

    companion object {
        private const val CHANNEL = "cooking_now"
        private const val NOTIFICATION_ID = 4242
        private var savedX: Int? = null
        private var savedY: Int? = null
    }
}
