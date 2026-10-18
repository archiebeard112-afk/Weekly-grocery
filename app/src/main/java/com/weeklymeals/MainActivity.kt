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

    private fun handle(intent: Intent?) {
        // Share → Weekly Meals from a browser: import the recipe link.
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Importer.findUrl(it) }?.let { vm.sharedLink.value = it }
            return
        }
        val data = intent?.data ?: return
        if (data.scheme == "weeklymeals" && data.host == "kroger") vm.handleRedirect(data)
    }
}
