package com.weeklymeals

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFD9582B)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D8),
    onPrimaryContainer = Color(0xFF3B1204),
    secondaryContainer = Color(0xFFFFE3D8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5A1A00),
    primaryContainer = Color(0xFF7D2C0F),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondaryContainer = Color(0xFF5A2A18),
)

@Composable
fun WeeklyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
