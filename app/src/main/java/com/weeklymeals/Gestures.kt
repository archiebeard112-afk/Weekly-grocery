package com.weeklymeals

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Swipe left-to-right anywhere on the screen to go back.
 * Only a clear sideways swipe counts, so scrolling up and down is unaffected.
 */
fun Modifier.swipeBack(onBack: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    val latest by rememberUpdatedState(onBack)
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total > threshold) latest() },
            onDragCancel = { total = 0f },
        ) { change, dx ->
            total += dx
            change.consume()
        }
    }
}
