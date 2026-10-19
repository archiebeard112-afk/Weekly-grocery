package com.weeklymeals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val vm: MealsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timers.ensureChannel(this)
        // Normal layout: status bar and navigation bar showing, drawn in the app's dark colors.
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { WeeklyTheme { App(vm) } }
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
    }

    override fun onStop() {
        super.onStop()
        CookOverlay.appVisible.value = false
    }

    private fun handle(intent: Intent?) {
        // Tapped the cooking bubble (or its notification): back to your steps.
        if (intent?.getBooleanExtra(CookOverlay.EXTRA_OPEN_COOK, false) == true) {
            vm.openCook.value = true
            intent.removeExtra(CookOverlay.EXTRA_OPEN_COOK)
            return
        }
        // Share → Weekly Meals from a browser: import the recipe link.
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Importer.findUrl(it) }?.let { vm.sharedLink.value = it }
            return
        }
        val data = intent?.data ?: return
        if (data.scheme == "weeklymeals" && data.host == "kroger") vm.handleRedirect(data)
    }
}
