package com.weeklymeals

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long the bubble sticks around after you last opened cooking mode. */
private const val BUBBLE_HOURS = 6L

/** True when the cooking bubble should show (you started cooking and haven't finished). */
fun cookBubbleOn(st: UserState): Boolean =
    st.cookingId != null && System.currentTimeMillis() - st.cookingSince < BUBBLE_HOURS * 3_600_000L

/**
 * A small round 🍳 bubble in the bottom-right corner while you're cooking. Tap to go back to
 * the cook screen, drag to move it, hold to stop cooking. Shows the next timer counting down.
 */
@Composable
fun CookBubble(vm: MealsViewModel, st: UserState, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val id = st.cookingId ?: return
    val r = vm.recipes(st)[id]
    if (r == null) { LaunchedEffect(id) { vm.stopCooking() }; return }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    // This recipe's timers (main dish and side): show the one ending soonest.
    val ends = st.timers.filterKeys { it.startsWith("r$id:") || it.startsWith("r${id}side:") }.values
    val next = ends.minOrNull()
    val rang = next != null && next <= now
    val pill = when {
        next == null -> null
        rang -> "Done!"
        else -> {
            val sec = (next - now + 999) / 1000
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
    val pulse by rememberInfiniteTransition(label = "bubble").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
    )

    // Dragging: kept on screen, and remembered for next time.
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val maxLeft = with(density) { (conf.screenWidthDp.dp - 88.dp).toPx() }
    val maxUp = with(density) { (conf.screenHeightDp.dp - 220.dp).toPx() }
    var dx by remember { mutableFloatStateOf(st.bubbleX) }
    var dy by remember { mutableFloatStateOf(st.bubbleY) }
    var confirm by remember { mutableStateOf(false) }

    Column(
        modifier
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { vm.setBubblePos(dx, dy) }) { change, drag ->
                    change.consume()
                    dx = (dx + drag.x).coerceIn(-maxLeft, 0f)
                    dy = (dy + drag.y).coerceIn(-maxUp, 0f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onOpen() }, onLongPress = { confirm = true })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pill != null) {
            Text(
                pill, fontSize = 12.sp,
                color = if (rang) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .alpha(if (rang) pulse else 1f)
                    .background(
                        if (rang) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // Red pot with steam.
        Image(
            painterResource(R.drawable.ic_cook_bubble), "Back to cooking ${r.name}",
            modifier = Modifier.padding(top = 4.dp).size(56.dp).shadow(6.dp, CircleShape),
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Stop cooking ${r.name}?") },
            text = { Text("The bubble goes away. Any running timers keep going.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.stopCooking() }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
}
