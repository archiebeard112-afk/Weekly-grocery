package com.weeklymeals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: MealsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val data = intent?.data ?: return
        if (data.scheme == "weeklymeals" && data.host == "kroger") vm.handleRedirect(data)
    }
}
