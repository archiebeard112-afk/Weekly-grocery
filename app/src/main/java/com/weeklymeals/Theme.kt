package com.weeklymeals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The bold look: near-black background, lime accent, a bright color block for each recipe. */
object Look {
    val Ink = Color(0xFF0F110F)
    val Card = Color(0xFF1A1D1A)
    val Paper = Color(0xFFF4F3EE)
    val Muted = Color(0xFFA3A89F)
    val Lime = Color(0xFFD4F25A)
    /** The other days in the app icon and opening animation. */
    val DimDay = Color(0xFF55612F)
    val Raise = Color(0xFF222622)

    // What each extra color means. Lime is only for "do this now", today, and the open tab.
    /** Good news: under budget, you have it, savings. */
    val Good = Color(0xFF5DCAA5)
    /** Needs a look soon: low, past sell-by, use soon. */
    val Warn = Color(0xFFF0B44C)
    val WarnBg = Color(0xFF3A2C14)
    /** Removing something, past best-by. */
    val Danger = Color(0xFFFF6B4A)
    val DangerBg = Color(0xFF4A1F14)
    val DangerText = Color(0xFFFFC2B3)
    /** Notes about this week (shared ingredients, tips). */
    val Info = Color(0xFF6CB8FF)
    /** King Soopers store meals. */
    val Store = Color(0xFFC79BFF)
    val StoreBg = Color(0xFF2A2540)

    /** Chunky headline font (Bricolage Grotesque). Body text stays the phone's normal font. */
    val Display = FontFamily(
        Font(R.font.bricolage_600, FontWeight.SemiBold),
        Font(R.font.bricolage_700, FontWeight.Bold),
        Font(R.font.bricolage_800, FontWeight.ExtraBold),
    )

    // Saffron, ocean, basil, tomato, plum, strawberry: each with a deeper shade for the round shape on top.
    private val swatches = listOf(
        Color(0xFFFFB830) to Color(0xFFFF9F1C),
        Color(0xFF6CB8FF) to Color(0xFF4A9CF0),
        Color(0xFF5BD68A) to Color(0xFF3FC274),
        Color(0xFFFF6B4A) to Color(0xFFF2502C),
        Color(0xFFC79BFF) to Color(0xFFB47FF5),
        Color(0xFFFF8FB8) to Color(0xFFF772A3),
    )

    /** A recipe's color: always the same one for the same recipe. */
    fun color(id: Int): Color = swatches[Math.floorMod(id, swatches.size)].first
    fun shade(id: Int): Color = swatches[Math.floorMod(id, swatches.size)].second
}

/** Spacing used everywhere: 16 at the page edges, gaps in steps of 4. */
object Space {
    val Page = 16.dp
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
}

/** Four corner sizes: small chips, cards, big hero cards, and fully round pills. */
object Radius {
    val Chip = RoundedCornerShape(10.dp)
    val Card = RoundedCornerShape(18.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(50)
}

/** Content drawn on a recipe's color block: dark text and icons instead of light ones. */
@Composable
fun OnColor(content: @Composable () -> Unit) {
    val ink = Look.Ink
    val cs = MaterialTheme.colorScheme.copy(
        primary = ink, onPrimary = Look.Paper,
        onSurface = ink, onSurfaceVariant = ink.copy(alpha = 0.72f),
        secondaryContainer = ink, onSecondaryContainer = Look.Paper,
        surfaceVariant = ink.copy(alpha = 0.12f),
        outline = ink.copy(alpha = 0.55f), outlineVariant = ink.copy(alpha = 0.3f),
    )
    MaterialTheme(colorScheme = cs, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides ink) { content() }
    }
}

/** The round shape peeking out of a color block's corner (put it first in a Box). */
@Composable
fun ColorBlob(id: Int, size: Dp, x: Dp, y: Dp, modifier: Modifier = Modifier) {
    Box(modifier.offset(x, y).size(size).clip(CircleShape).background(Look.shade(id)))
}

private val Dark = darkColorScheme(
    primary = Look.Lime,
    onPrimary = Look.Ink,
    primaryContainer = Color(0xFF3A4714),
    onPrimaryContainer = Color(0xFFE6FA94),
    secondary = Color(0xFFFFB830),
    onSecondary = Color(0xFF1A1206),
    secondaryContainer = Color(0xFF2A2E2A),
    onSecondaryContainer = Look.Paper,
    tertiary = Color(0xFF6CB8FF),
    onTertiary = Look.Ink,
    tertiaryContainer = Color(0xFF183048),
    onTertiaryContainer = Color(0xFFCFE6FF),
    error = Color(0xFFFF6B4A),
    onError = Look.Ink,
    errorContainer = Color(0xFF4A1F14),
    onErrorContainer = Color(0xFFFFC2B3),
    background = Look.Ink,
    onBackground = Look.Paper,
    surface = Look.Ink,
    onSurface = Look.Paper,
    surfaceVariant = Color(0xFF2A2E2A),
    onSurfaceVariant = Look.Muted,
    surfaceTint = Look.Card,
    surfaceDim = Look.Ink,
    surfaceBright = Color(0xFF2A2E2A),
    surfaceContainerLowest = Color(0xFF0B0D0B),
    surfaceContainerLow = Color(0xFF151815),
    surfaceContainer = Look.Card,
    surfaceContainerHigh = Color(0xFF222622),
    surfaceContainerHighest = Color(0xFF2A2E2A),
    inverseSurface = Look.Paper,
    inverseOnSurface = Look.Ink,
    inversePrimary = Color(0xFF4A5A10),
    outline = Color(0xFF5A605A),
    outlineVariant = Color(0xFF2E322E),
    scrim = Color.Black,
)

private val base = Typography()
private val Type = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Look.Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
)

private val Rounded = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun WeeklyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = Type, shapes = Rounded, content = content)
}
