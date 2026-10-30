package com.weeklymeals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private val ROW_HEIGHT = 52.dp
private val ROW_GAP = 6.dp
private val STORE_PURPLE = Look.Store
private val LEFT_BG = androidx.compose.ui.graphics.Color(0xFF26301A)

/** Meals planned this week: each recipe, plus one for each day that has store items. */
fun mealCount(st: UserState): Int = st.mains.size + st.storeMeals.map { it.day }.distinct().size

/** A side's name without its emoji ("🥚 Egg rolls" → "Egg rolls"). */
internal fun plainLabel(s: String): String = if (s.firstOrNull()?.isLetterOrDigit() != false) s else s.substringAfter(" ")

/** A small capital-letters tag like STORE or LEFTOVERS. */
@Composable
private fun Tag(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = fg,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** One of the three small tiles under Tonight: a big number and what it counts. */
@Composable
internal fun StatTile(
    modifier: Modifier, value: String, suffix: String?, label: String,
    lime: Boolean = false, onClick: (() -> Unit)? = null,
) {
    // Lime is for "do this now", so a highlighted tile just gets a lime number.
    val bg = MaterialTheme.colorScheme.surfaceContainer
    val fg = if (lime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(value)
                if (suffix != null) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp))
                    append(suffix)
                    pop()
                }
            },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = fg, maxLines = 1,
        )
        Text(
            label, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** No dinner planned tonight: a dark card with a button to pick one. */
@Composable
private fun NothingTonight(day: Int, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Radius.Hero).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "TONIGHT · " + Logic.DAY_LONG[day].uppercase(), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Nothing planned tonight", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.padding(start = 8.dp).height(42.dp)) {
            Text("Add a meal", fontWeight = FontWeight.Bold)
        }
    }
}

/** One store-bought meal under a day: photo, name, price and a STORE tag. Tap for details and Remove. */
@Composable
private fun StoreLine(m: StoreMeal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m.image.isNotBlank()) ProductPhoto(m.image, 26.dp)
        else androidx.compose.material3.Icon(Icons.Filled.ShoppingCart, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(m.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        m.price?.let {
            Text(Sales.money(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
        Tag("STORE", Look.StoreBg, androidx.compose.ui.graphics.Color(0xFFD9C2FF))
    }
}

/** A dessert under a day: a DESSERT tag, its name and ✕ to take it off. Tap to open the recipe. */
@Composable
private fun DessertLine(r: Recipe, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "DESSERT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onPrimary, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        androidx.compose.material3.IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, "Remove dessert", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tonight's dinner, big at the top of the Week page, with Start cooking right on it. */
@Composable
private fun TonightCard(r: Recipe, day: Int, meta: String, onOpen: () -> Unit, onCook: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(Radius.Hero).background(Look.color(r.id)).clickable(onClick = onOpen),
    ) {
        ColorBlob(r.id, 140.dp, 36.dp, (-36).dp, Modifier.align(Alignment.TopEnd))
        OnColor {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(
                    "TONIGHT · " + Logic.DAY_LONG[day].uppercase(),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                )
                Text(
                    r.name, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 30.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, end = 40.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Button(
                        onClick = onCook,
                        colors = ButtonDefaults.buttonColors(containerColor = Look.Ink, contentColor = Look.Paper),
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        modifier = Modifier.height(42.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start cooking", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** The Week tab: Monday-Sunday, one dinner per day. Hold and drag a meal to move it. */
@Composable
fun WeekScreen(
    vm: MealsViewModel, st: UserState, onOpen: (Int) -> Unit, onBrowse: () -> Unit,
    flashId: Int? = null, onLeftovers: () -> Unit = {}, onAddStore: () -> Unit = {}, onCook: (Int) -> Unit = {},
    onFlashed: () -> Unit = {},
) {
    val all = vm.recipes(st)
    val plan = Logic.dayPlan(st)
    val dayAt = Logic.dayOrder(st.weekStart)
    val today = Logic.todayIndex()
    val density = LocalDensity.current
    // Rows sit right under each other in one panel (only a thin line between them).
    val gapPx = 0f
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
        if (id in Logic.dayPlan(st).extras) scroll.animateScrollTo(scroll.maxValue)
        flash.snapTo(1f)
        flash.animateTo(0f, androidx.compose.animation.core.tween(1400))
        onFlashed()
    }
    val target = if (dragId != null) targetFor(dragFrom, dragY) else -1

    // Rows: the 7 days, then any extra meals.
    // Days in your week's order (Settings → Week starts on): row i is weekday dayAt[i].
    val rows: List<Pair<String, Int?>> =
        dayAt.map { d -> Logic.DAY_SHORT[d] to plan.slots[d] } + plan.extras.map { "Extra" to it }

    // Fresh things you'll have extra of: tap the leftovers tile for the list and meals that use them up.
    val left = vm.weekLeftovers(st)
    var showLeft by remember { mutableStateOf(false) }
    // Store items on one day are one meal (pizza and garlic bread on Wednesday = 1).
    val mealCount = mealCount(st)
    // Later meals that use what an earlier meal opened (the rest of the ground beef).
    val shares = vm.weekShares(st)
    fun usesLeftovers(id: Int) = shares.values.any { it.meals.indexOf(id) > 0 }

    // Scrolls whenever the days don't fit, so every day is always reachable.
    val tonightR = plan.slots[today]?.let { all[it] }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(scroll)
            // Same margins as Browse and Favorites.
            .padding(horizontal = Space.Page, vertical = Space.S),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        // WEEK 43 over the dates of your week (Settings → Week starts on).
        val first = java.time.LocalDate.now().minusDays(((today - st.weekStart + 7) % 7).toLong())
        val last = first.plusDays(6)
        val mon = java.time.format.DateTimeFormatter.ofPattern("MMM d")
        val range = mon.format(first) + "–" + (if (last.month == first.month) last.dayOfMonth.toString() else mon.format(last))
        PageHeader("This week", range)

        if (tonightR != null) {
            val side = st.sides[tonightR.id]?.let { vm.sideMap[it] }
            val meta = listOfNotNull(
                Logic.fmtShort(Logic.cookMinutes(tonightR)),
                side?.label?.let(::plainLabel),
            ).joinToString(" · ")
            TonightCard(tonightR, today, meta, onOpen = { onOpen(tonightR.id) }, onCook = { onCook(tonightR.id) })
        } else {
            NothingTonight(today, onBrowse)
        }

        // Meals planned, what the week costs, and leftovers.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val groceries = st.week.mapNotNull { id -> all[id]?.let { vm.mealCost(st, it)?.total } }.sum() +
                st.storeMeals.sumOf { it.price ?: 0.0 }
            StatTile(Modifier.weight(1f), "$mealCount", "/7", "meals planned")
            StatTile(Modifier.weight(1f), "\$" + groceries.roundToInt(), null, "groceries")
            StatTile(
                Modifier.weight(1f), "${left.size}", null, if (left.size == 1) "leftover" else "leftovers",
                lime = left.isNotEmpty(), onClick = if (left.isNotEmpty()) ({ showLeft = true }) else null,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = Space.XS), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your week", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Look.Display),
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (st.week.isNotEmpty() || st.storeMeals.isNotEmpty()) {
                TextButton(
                    onClick = { confirmFinish = true },
                    contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(36.dp),
                ) { Text("Finish week", fontWeight = FontWeight.Bold) }
            }
        }

        // All the days in one rounded panel, a thin line between them.
        Column(
            Modifier.fillMaxWidth()
                .clip(Radius.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
        rows.forEachIndexed { index, (label, id) ->
            val r = id?.let { all[it] }
            val dragged = id != null && id == dragId
            val isTarget = dragId != null && index == target && !dragged
            val isToday = index < 7 && dayAt[index] == today
            // Days already gone this week fade back.
            val isPast = index < 7 && index < dayAt.indexOf(today)
            val flashing = id != null && id == flashId && flash.value > 0f
            val bg = when {
                flashing -> androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), flash.value,
                )
                isTarget -> MaterialTheme.colorScheme.primaryContainer
                dragged -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            var rowMod = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .onSizeChanged {
                    heights[index] = it.height.toFloat()
                    if (index == 0) rowPx = it.height.toFloat()
                }
                .zIndex(if (dragged) 1f else 0f)
                .graphicsLayer { translationY = if (dragged) dragY else 0f }
                .then(if (dragged) Modifier.shadow(8.dp, Radius.Card) else Modifier)
                .background(bg)
                .alpha(if (isPast && !dragged) 0.45f else 1f)
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
            // Store-bought meals on this day (frozen pizza): the row grows by a line for each.
            val meals = if (index < 7) st.storeMeals.filter { it.day == dayAt[index] } else emptyList()
            // Desserts on this day: a line each under the dinner.
            val sweets = if (index < 7) st.dessertDays.filterValues { it == dayAt[index] }.keys.mapNotNull { all[it] } else emptyList()
            val oneLine = meals.isEmpty() && sweets.isEmpty()
            Row(
                rowMod.padding(horizontal = 14.dp, vertical = if (oneLine) 0.dp else 8.dp),
                verticalAlignment = if (oneLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                // The day in a round dot: the meal's color, an outline when empty; today's has a lime ring.
                val dot = when {
                    r != null -> Look.color(r.id)
                    meals.isNotEmpty() -> STORE_PURPLE
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
                Box(
                    Modifier.padding(top = if (oneLine) 0.dp else 2.dp).size(36.dp).clip(CircleShape).background(dot)
                        .then(
                            when {
                                isToday -> Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                r == null && meals.isEmpty() -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), CircleShape)
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (label == "Extra") "+" else label.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = Look.Display),
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            r != null || meals.isNotEmpty() -> Look.Ink
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (r == null && meals.isEmpty()) {
                        Text("+ Add a meal", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (r != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                val side = st.sides[r.id]?.let { vm.sideMap[it] }
                                Text(
                                    listOfNotNull(
                                        side?.label?.let(::plainLabel)?.plus(
                                            when (st.sideSize[r.id]) { Logic.SIDE_HALF -> " (half)"; Logic.SIDE_DOUBLE -> " (double)"; else -> "" },
                                        ),
                                        Logic.fmtShort(Logic.cookMinutes(r)),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isToday) Tag("TONIGHT", Look.Ink, MaterialTheme.colorScheme.primary)
                            else if (usesLeftovers(r.id)) Tag("LEFTOVERS", LEFT_BG, MaterialTheme.colorScheme.primary)
                        }
                    }
                    meals.forEach { m -> StoreLine(m) { openMeal = m } }
                    sweets.forEach { d -> DessertLine(d, onClick = { onOpen(d.id) }, onRemove = { vm.removeFromWeek(d.id) }) }
                }
            }
        }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLeft) {
        AppDialog(
            onDismiss = { showLeft = false },
            eyebrow = "${left.size} " + if (left.size == 1) "leftover" else "leftovers",
            title = "Left over this week",
            actions = listOf(
                DialogAction("See meals that use them", ActionKind.Primary) { showLeft = false; onLeftovers() },
                DialogAction("Close") { showLeft = false },
            ),
        ) {
            Text(
                "Fresh things you'll only use part of. Most valuable first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            left.forEach { l ->
                HorizontalDivider(Modifier.padding(vertical = Space.S), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${l.label} ${l.term}", style = MaterialTheme.typography.bodyLarge)
                        Text("Keeps ${l.keeps}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (l.value >= 0.05) Text(Sales.money(l.value), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Look.Display, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    openMeal?.let { m ->
        AppDialog(
            onDismiss = { openMeal = null },
            eyebrow = "Store meal · " + Logic.DAY_LONG.getOrElse(m.day) { "" },
            title = m.title,
            actions = listOf(
                DialogAction("Close", ActionKind.Primary) { openMeal = null },
                DialogAction("Remove from week", ActionKind.Danger) { vm.removeStoreMeal(m.id); openMeal = null },
            ),
        ) {
            val detail = listOfNotNull(m.size.ifBlank { null }, m.price?.let { Sales.money(it) }).joinToString(" · ")
            if (detail.isNotBlank()) Text(detail)
            DialogNote("From King Soopers. Nothing to cook: it's on your Shop list.")
        }
    }

    if (confirmFinish) {
        AppDialog(
            onDismiss = { confirmFinish = false },
            title = "Finish this week?",
            actions = listOf(
                DialogAction("Finish week", ActionKind.Primary) { vm.finishWeek(); confirmFinish = false },
                DialogAction("Keep planning") { confirmFinish = false },
            ),
        ) {
            Text(
                "Your ${mealCount(st)} meals go to History, and the week and shopping list are cleared." +
                    if (st.pantry.isNotEmpty()) " The Kitchen is updated for what these meals used." else "",
            )
        }
    }
}
