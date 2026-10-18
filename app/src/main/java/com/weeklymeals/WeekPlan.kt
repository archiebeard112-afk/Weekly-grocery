package com.weeklymeals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private val ROW_HEIGHT = 64.dp
private val ROW_GAP = 6.dp

/** The Week tab: Monday-Sunday, one dinner per day. Hold and drag a meal to move it. */
@Composable
fun WeekScreen(
    vm: MealsViewModel, st: UserState, onOpen: (Int) -> Unit, onBrowse: () -> Unit,
    flashId: Int? = null, onFlashed: () -> Unit = {},
) {
    val all = vm.recipes(st)
    val plan = Logic.dayPlan(st.week, st.days)
    val today = Logic.todayIndex()
    val density = LocalDensity.current
    // A standard week (no extras) fits on one screen; extras make the page scroll.
    val fits = plan.extras.isEmpty()
    val gapPx = with(density) { ROW_GAP.toPx() }
    var rowPx by remember { mutableFloatStateOf(with(density) { ROW_HEIGHT.toPx() }) }
    val stepPx = rowPx + gapPx
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableIntStateOf(0) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var confirmFinish by remember { mutableStateOf(false) }
    // A just-added meal flashes once; if it landed in the extras, scroll down to it.
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    val scroll = rememberScrollState()
    LaunchedEffect(flashId, st.week) {
        val id = flashId ?: return@LaunchedEffect
        if (id !in st.week) return@LaunchedEffect
        if (id in Logic.dayPlan(st.week, st.days).extras) scroll.animateScrollTo(scroll.maxValue)
        flash.snapTo(1f)
        flash.animateTo(0f, androidx.compose.animation.core.tween(1400))
        onFlashed()
    }
    val target = if (dragId != null) (dragFrom + (dragY / stepPx).roundToInt()).coerceIn(0, 6) else -1

    // Rows: the 7 days, then any extra meals.
    val rows: List<Pair<String, Int?>> =
        plan.slots.mapIndexed { d, id -> Logic.DAY_SHORT[d] to id } + plan.extras.map { "Extra" to it }

    Column(
        Modifier.fillMaxSize()
            .then(if (fits) Modifier else Modifier.verticalScroll(scroll))
            // Same margins as Browse and Favorites.
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "Cooking for",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            // Same height and style as Browse's More button.
            FilledTonalButton(
                onClick = { vm.setServings(st.servings - 1) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(34.dp).width(40.dp),
            ) { Text("−") }
            Text(
                "${st.servings}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            FilledTonalButton(
                onClick = { vm.setServings(st.servings + 1) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(34.dp).width(40.dp),
            ) { Text("+") }
        }

        rows.forEachIndexed { index, (label, id) ->
            val r = id?.let { all[it] }
            val dragged = id != null && id == dragId
            val isTarget = dragId != null && index == target && !dragged
            val isToday = index == today
            val flashing = id != null && id == flashId && flash.value > 0f
            val bg = when {
                flashing -> androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), flash.value,
                )
                isTarget -> MaterialTheme.colorScheme.primaryContainer
                dragged -> MaterialTheme.colorScheme.secondaryContainer
                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
            var rowMod = Modifier
                .fillMaxWidth()
                .then(if (fits) Modifier.weight(1f) else Modifier.height(ROW_HEIGHT))
                .then(if (index == 0) Modifier.onSizeChanged { rowPx = it.height.toFloat() } else Modifier)
                .zIndex(if (dragged) 1f else 0f)
                .graphicsLayer { translationY = if (dragged) dragY else 0f }
                .then(if (dragged) Modifier.shadow(8.dp, RoundedCornerShape(14.dp)) else Modifier)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
            if (id != null && r != null) {
                rowMod = rowMod
                    .pointerInput(id, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragId = id; dragFrom = index; dragY = 0f },
                            onDragEnd = {
                                val to = (dragFrom + (dragY / stepPx).roundToInt()).coerceIn(0, 6)
                                vm.moveToDay(id, to)
                                dragId = null; dragY = 0f
                            },
                            onDragCancel = { dragId = null; dragY = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragY += amount.y
                        }
                    }
                    .clickable { onOpen(id) }
            } else {
                rowMod = rowMod.clickable { onBrowse() }
            }
            Row(rowMod.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(46.dp),
                )
                if (r == null) {
                    Text("+ Add a meal", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(r.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val side = st.sides[r.id]?.let { vm.sideMap[it] }
                        Text(
                            listOfNotNull(side?.label, "⏱ " + Logic.fmtShort(Logic.cookMinutes(r))).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("⋮⋮", color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        if (st.week.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = { confirmFinish = true },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) { Text("Finish week") }
            }
        }
        if (!fits) Spacer(Modifier.height(12.dp))
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this week?") },
            text = {
                Text(
                    "Your ${st.week.size} meals will be saved to History, and the week and shopping list will be cleared." +
                        if (st.pantry.isNotEmpty()) " Your pantry will be updated for what these meals used." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.finishWeek(); confirmFinish = false }) { Text("Finish week") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Cancel") } },
        )
    }
}
