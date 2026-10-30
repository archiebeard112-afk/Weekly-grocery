package com.weeklymeals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: MealsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        Timers.ensureChannel(this)
        // Normal layout: status bar and navigation bar showing, with light icons on the app's dark background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        // Opening animation only when the app starts fresh from the launcher.
        // Skipped when the phone's "Remove animations" setting is on.
        val noMotion = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val fromLauncher = savedInstanceState == null && intent?.action == Intent.ACTION_MAIN && !noMotion
        setContent {
            WeeklyTheme {
                var intro by rememberSaveable { mutableStateOf(fromLauncher) }
                Box(Modifier.fillMaxSize()) {
                    App(vm)
                    if (intro) Intro { intro = false }
                }
            }
        }
    }

    // singleTask: Kroger sends you back here after sign-in.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    // The floating cooking bubble shows only while the app is off screen.
    override fun onStart() {
        super.onStart()
        CookOverlay.appVisible.value = true
        vm.autoCheck()
    }

    override fun onStop() {
        super.onStop()
        CookOverlay.appVisible.value = false
    }

    private fun handle(intent: Intent?) {
        // Tapped the cooking bubble (or its notification): back to your steps.
        if (intent?.getBooleanExtra(TimerAlarmService.EXTRA_STOP_ALARM, false) == true) {
            TimerAlarmService.stopAll()
            intent.removeExtra(TimerAlarmService.EXTRA_STOP_ALARM)
        }
        if (intent?.getBooleanExtra(CookOverlay.EXTRA_OPEN_COOK, false) == true) {
            vm.openCook.value = true
            intent.removeExtra(CookOverlay.EXTRA_OPEN_COOK)
            return
        }
        // Share → Thyme Table from a browser: import the recipe link.
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Importer.findUrl(it) }?.let { vm.sharedLink.value = it }
            return
        }
        val data = intent?.data ?: return
        if (data.scheme == "weeklymeals" && data.host == "kroger") vm.handleRedirect(data)
    }
}
