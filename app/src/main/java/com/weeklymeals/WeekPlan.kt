package com.weeklymeals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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

/** One store-bought meal under a day: 🛒 name, price, and ✕ to take it off. Tap for details. */
@Composable
private fun StoreLine(m: StoreMeal, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🛒", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        m.price?.let {
            Text(Sales.money(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
        Text(
            "✕", color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The Week tab: Monday-Sunday, one dinner per day. Hold and drag a meal to move it. */
@Composable
fun WeekScreen(
    vm: MealsViewModel, st: UserState, onOpen: (Int) -> Unit, onBrowse: () -> Unit,
    flashId: Int? = null, onLeftovers: () -> Unit = {}, onAddStore: () -> Unit = {}, onFlashed: () -> Unit = {},
) {
    val all = vm.recipes(st)
    val plan = Logic.dayPlan(st.week, st.days, st.weekStart)
    val dayAt = Logic.dayOrder(st.weekStart)
    val today = Logic.todayIndex()
    val density = LocalDensity.current
    val gapPx = with(density) { ROW_GAP.toPx() }
    var rowPx by remember { mutableFloatStateOf(with(density) { ROW_HEIGHT.toPx() }) }
    // Each tile's real height (tiles with store items are taller), so dragging lands on the right day.
    val heights = remember { mutableStateMapOf<Int, Float>() }
    fun topOf(i: Int): Float {
        var y = 0f
        for (j in 0 until i) y += (heights[j] ?: rowPx) + gapPx
        return y
    }
    fun targetFor(from: Int, dy: Float): Int {
        val center = topOf(from) + (heights[from] ?: rowPx) / 2f + dy
        var t = 0
        for (i in 0..6) if (topOf(i) <= center) t = i
        return t
    }
    var openMeal by remember { mutableStateOf<StoreMeal?>(null) }
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
        if (id in Logic.dayPlan(st.week, st.days, st.weekStart).extras) scroll.animateScrollTo(scroll.maxValue)
        flash.snapTo(1f)
        flash.animateTo(0f, androidx.compose.animation.core.tween(1400))
        onFlashed()
    }
    val target = if (dragId != null) targetFor(dragFrom, dragY) else -1

    // Rows: the 7 days, then any extra meals.
    // Days in your week's order (Settings → Week starts on): row i is weekday dayAt[i].
    val rows: List<Pair<String, Int?>> =
        dayAt.map { d -> Logic.DAY_SHORT[d] to plan.slots[d] } + plan.extras.map { "Extra" to it }

    // Roughly how tall each day's tile needs to be (dp): a day with store items is taller.
    fun estDp(index: Int, hasRecipe: Boolean): Float {
        val n = if (index < 7) st.storeMeals.count { it.day == dayAt[index] } else 0
        return if (n == 0) 46f else 16f + (if (hasRecipe) 46f else 0f) + 32f * n + 4f
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // The week stays on one screen (days with store items take more room, the rest squeeze a little).
    // It only scrolls if there are extra meals or it truly can't fit.
    val needDp = 12f + 44f + 40f + rows.withIndex().sumOf { (i, row) -> estDp(i, row.second != null).toDouble() }.toFloat() + 6f * (rows.size + 2)
    val fits = plan.extras.isEmpty() && (st.storeMeals.isEmpty() || maxHeight.value >= needDp)
    Column(
        Modifier.fillMaxSize()
            .then(if (fits) Modifier else Modifier.verticalScroll(scroll))
            // Same margins as Browse and Favorites.
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // ♻️ Fresh things you'll have extra of: tap for the list and meals that use them up.
            val left = vm.weekLeftovers(st)
            var showLeft by remember { mutableStateOf(false) }
            if (left.isNotEmpty()) {
                Text(
                    "♻️ ${left.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .clickable { showLeft = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (showLeft) {
                AlertDialog(
                    onDismissRequest = { showLeft = false },
                    title = { Text("Left over this week") },
                    text = {
                        Column {
                            Text(
                                "Fresh things you'll only use part of. Most valuable first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            left.forEach { l ->
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${l.label} ${l.term}", style = MaterialTheme.typography.bodyMedium)
                                        Text("keeps ${l.keeps}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (l.value >= 0.05) Text(Sales.money(l.value), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showLeft = false; onLeftovers() }) { Text("See meals that use them") } },
                    dismissButton = { TextButton(onClick = { showLeft = false }) { Text("Close") } },
                )
            }
        }

        rows.forEachIndexed { index, (label, id) ->
            val r = id?.let { all[it] }
            val dragged = id != null && id == dragId
            val isTarget = dragId != null && index == target && !dragged
            val isToday = index < 7 && dayAt[index] == today
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
                .then(if (fits) Modifier.weight(estDp(index, id != null)) else Modifier.heightIn(min = ROW_HEIGHT))
                .onSizeChanged {
                    heights[index] = it.height.toFloat()
                    if (index == 0) rowPx = it.height.toFloat()
                }
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
                                val to = targetFor(dragFrom, dragY)
                                vm.moveToDay(id, dayAt[to])
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
            // Store-bought meals on this day (frozen pizza): the tile grows by a line for each.
            val meals = if (index < 7) st.storeMeals.filter { it.day == dayAt[index] } else emptyList()
            Row(
                rowMod.padding(horizontal = 12.dp, vertical = if (meals.isEmpty()) 0.dp else 8.dp),
                verticalAlignment = if (meals.isEmpty()) Alignment.CenterVertically else Alignment.Top,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(46.dp).padding(top = if (meals.isEmpty()) 0.dp else 6.dp),
                )
                Column(Modifier.weight(1f)) {
                    if (r == null && meals.isEmpty()) {
                        Text("+ Add a meal", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (r != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    meals.forEach { m ->
                        StoreLine(m, onClick = { openMeal = m }, onRemove = { vm.removeStoreMeal(m.id) })
                    }
                }
            }
        }

        if (st.week.isNotEmpty() || st.storeMeals.isNotEmpty()) {
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
    }

    openMeal?.let { m ->
        AlertDialog(
            onDismissRequest = { openMeal = null },
            title = { Text("🛒 " + m.title) },
            text = {
                Column {
                    val detail = listOfNotNull(m.size.ifBlank { null }, m.price?.let { Sales.money(it) }).joinToString(" · ")
                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Logic.DAY_LONG.getOrElse(m.day) { "" }} · from King Soopers. Nothing to cook: it's on your Shop list.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.removeStoreMeal(m.id); openMeal = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { openMeal = null }) { Text("Close") } },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this week?") },
            text = {
                Text(
                    "Your ${st.week.size + st.storeMeals.size} meals will be saved to History, and the week and shopping list will be cleared." +
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
