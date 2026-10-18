package com.weeklymeals

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
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

/**
 * Keeps this at the tallest height it has had (for the current width), so when the
 * keyboard opens and the space shrinks, the content keeps its size and the keyboard
 * simply covers the bottom instead of squishing everything.
 */
fun Modifier.keepFullHeight(): Modifier = composed {
    val tallest = remember { IntArray(2) } // [width, height]
    this.clipToBounds().layout { measurable, constraints ->
        if (!constraints.hasBoundedHeight) {
            val p = measurable.measure(constraints)
            return@layout layout(p.width, p.height) { p.place(0, 0) }
        }
        if (tallest[0] != constraints.maxWidth) { tallest[0] = constraints.maxWidth; tallest[1] = 0 }
        tallest[1] = maxOf(tallest[1], constraints.maxHeight)
        val h = tallest[1]
        val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(p.width, constraints.maxHeight) { p.place(0, 0) }
    }
}
