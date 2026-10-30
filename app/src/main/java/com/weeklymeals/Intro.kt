package com.weeklymeals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val INTRO_MS = 1950

/** Seconds into the animation → 0..1 progress of a step that starts at [start] and lasts [dur]. */
private fun step(t: Float, start: Float, dur: Float) = ((t - start) / dur).coerceIn(0f, 1f)
private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)
/** Overshoots a little past 1 before settling, for the "pop" of the days. */
private fun pop(p: Float): Float { val c = 1.70158f; val q = p - 1f; return 1f + (c + 1f) * q * q * q + c * q * q }

/**
 * Opening animation on a cold start (about 2 seconds): the calendar draws itself, the days
 * pop in, tonight lights up lime with its thyme leaf, and the name slides in. Tap to skip.
 * Same drawing as the launcher icon (ic_fg.xml), in its 100 x 100 box.
 */
@Composable
fun Intro(onDone: () -> Unit) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(INTRO_MS / 1000f, tween(INTRO_MS, easing = LinearEasing))
        onDone()
    }
    val t = time.value
    val fade = 1f - step(t, 1.7f, 0.25f)
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(Look.Ink)
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(120.dp)) {
            val u = size.width / 100f
            val sw = 5f * u
            // Calendar outline, drawn around like a pen stroke.
            val frame = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(20f * u, 26f * u, 80f * u, 80f * u, CornerRadius(12f * u)))
            }
            val len = PathMeasure().apply { setPath(frame, false) }.length
            val f = easeOut(step(t, 0f, 0.45f))
            if (f > 0f) drawPath(frame, Look.Lime, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(len * f, len), 0f)))
            // The two binder tabs grow up out of the top edge.
            for ((x, start) in listOf(36f to 0.30f, 64f to 0.36f)) {
                val p = easeOut(step(t, start, 0.25f))
                if (p > 0f) drawLine(Look.Lime, Offset(x * u, 30f * u), Offset(x * u, (30f - 12f * p) * u), sw, StrokeCap.Round)
            }
            // The days, one by one.
            var k = 0
            for (y in listOf(44f, 56f, 68f)) for (x in listOf(32f, 44f, 56f, 68f)) {
                if ((x == 56f && y == 56f) || (x == 68f && y == 68f)) continue
                val p = pop(step(t, 0.45f + k++ * 0.045f, 0.22f))
                if (p > 0f) drawCircle(Look.DimDay, 3f * u * p, Offset(x * u, y * u))
            }
            // Tonight: a lime chip, then the leaf cut into it.
            val c = pop(step(t, 0.9f, 0.3f))
            if (c > 0f) {
                val half = 7f * u * c
                drawRoundRect(Look.Lime, Offset(56f * u - half, 56f * u - half), Size(half * 2, half * 2), CornerRadius(4.5f * u * c))
            }
            val l = easeOut(step(t, 1.05f, 0.3f))
            if (l > 0f) {
                val leaf = Path().apply {
                    moveTo(0f, 0f); quadraticTo(5.625f * u, -4.6f * u, 12.5f * u, 0f); quadraticTo(5.625f * u, 4.6f * u, 0f, 0f); close()
                }
                translate(51f * u, 59.8f * u) {
                    rotate(-37f - 20f * (1f - l), Offset.Zero) { scale(l, l, Offset.Zero) { drawPath(leaf, Look.Ink) } }
                }
            }
        }
        val n = easeOut(step(t, 1.1f, 0.35f))
        Text(
            buildAnnotatedString {
                append("Thyme ")
                withStyle(SpanStyle(color = Look.Lime)) { append("Table") }
            },
            color = Look.Paper, fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp,
            modifier = Modifier.graphicsLayer { alpha = n; translationY = (1f - n) * 8.dp.toPx() },
        )
    }
}
