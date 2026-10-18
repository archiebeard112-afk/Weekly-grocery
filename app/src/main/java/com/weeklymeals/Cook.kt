package com.weeklymeals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

/** One step in cooking mode, from either the main dish or the side. */
data class CookStep(
    val text: String,
    val isSide: Boolean,
    /** Position in its own recipe's step list (keeps timers in sync with the recipe page). */
    val stepIndex: Int,
    val times: List<Logic.StepTime>,
    /** True on the first step of the side dish. */
    val startsSide: Boolean = false,
)

object Cook {
    /** Rough length of a step, only used to decide the order. */
    private fun minutes(step: String): Int =
        Logic.stepTimes(step).let { t -> if (t.isEmpty()) 3 else t.sumOf { if (it.perSide) it.minutes * 2 else it.minutes } }

    /**
     * Main and side steps in one order, arranged so both dishes finish together:
     * the longer dish starts first and the shorter one is slotted in where it needs to start.
     */
    fun order(r: Recipe, side: Side?): List<CookStep> {
        val main = r.steps.mapIndexed { i, s -> CookStep(s, false, i, Logic.stepTimes(s)) }
        if (side == null || side.steps.isEmpty()) return main
        val sideSteps = side.steps.mapIndexed { i, s -> CookStep(s, true, i, Logic.stepTimes(s)) }
        val mainTotal = r.steps.sumOf { minutes(it) }
        val sideTotal = side.steps.sumOf { minutes(it) }
        data class Slot(val start: Int, val rank: Int, val step: CookStep)
        val slots = mutableListOf<Slot>()
        var t = max(0, sideTotal - mainTotal)
        main.forEach { slots += Slot(t, 0, it); t += minutes(it.text) }
        t = max(0, mainTotal - sideTotal)
        sideSteps.forEach { slots += Slot(t, 1, it); t += minutes(it.text) }
        val ordered = slots.sortedWith(compareBy<Slot>({ it.start }, { it.rank })).map { it.step }
        val firstSide = ordered.indexOfFirst { it.isSide }
        return ordered.mapIndexed { i, s -> if (i == firstSide) s.copy(startsSide = true) else s }
    }

    /** True if the side's first step comes before the main dish's first step. */
    fun sideFirst(steps: List<CookStep>): Boolean = steps.firstOrNull()?.isSide == true
}

/** Full-screen, one step at a time. Swipe or use Next/Back; ✕ to leave. The screen stays on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookScreen(vm: MealsViewModel, st: UserState, id: Int, timerCtl: (Recipe) -> TimerCtl, onClose: () -> Unit) {
    val r = vm.recipes(st)[id]
    if (r == null) { onClose(); return }
    val side = if (id in st.week) st.sides[id]?.let { vm.sideMap[it] } else null
    val sideName = side?.label?.substringAfter(" ")
    val steps = remember(r, side) { Cook.order(r, side) }
    val pages = steps.size + 1 // page 0 = ingredients
    var page by rememberSaveable(id) { mutableIntStateOf(0) }
    val mainCtl = timerCtl(r)
    val sideCtl = mainCtl.copy(prefix = mainCtl.prefix + "side", title = sideName ?: "Side")

    // Keep the screen awake while cooking.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { onClose() }

    val swipePx = with(LocalDensity.current) { 80.dp.toPx() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Exit cooking mode") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { page = (page - 1).coerceAtLeast(0) },
                    enabled = page > 0,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Back", fontSize = 18.sp) }
                Button(
                    onClick = { if (page < pages - 1) page++ else onClose() },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(if (page == 0) "Start" else if (page < pages - 1) "Next" else "Done", fontSize = 18.sp) }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .pointerInput(pages) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -swipePx && page < pages - 1) page++
                            else if (total > swipePx && page > 0) page--
                        },
                    ) { change, dx -> total += dx; change.consume() }
                },
        ) {
            LinearProgressIndicator(progress = { (page + 1f) / pages }, modifier = Modifier.fillMaxWidth())
            RunningTimers(st, id, steps, page, mainCtl, sideCtl)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                if (page == 0) {
                    IngredientsPage(r, side, sideName, st.servings, steps)
                } else {
                    val step = steps[page - 1]
                    StepTag(step, r, side)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        (if (step.startsSide) "Start the side: " else "") + step.text,
                        fontSize = 28.sp, lineHeight = 36.sp,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (step.times.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        val ctl = if (step.isSide) sideCtl else mainCtl
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            step.times.forEachIndexed { k, t ->
                                TimerButton("${ctl.prefix}:${step.stepIndex}:$k", "${ctl.title}: step ${step.stepIndex + 1}", t, ctl)
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    val next = steps.getOrNull(page)
                    Text("Up next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    if (next == null) {
                        Text("That's it. Time to eat! 🎉", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(
                            "${if (next.isSide) (side?.label?.substringBefore(" ") ?: "🥗") else r.emoji}  " +
                                (if (next.startsSide) "Start the side: " else "") + next.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Step $page of ${steps.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTag(step: CookStep, r: Recipe, side: Side?) {
    val text = if (step.isSide) "${side?.label ?: "Side"} · Side" else "${r.emoji} Main"
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (step.isSide) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(
                if (step.isSide) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun IngredientsPage(r: Recipe, side: Side?, sideName: String?, serv: Int, steps: List<CookStep>) {
    var got by remember(r.id) { mutableStateOf(setOf<String>()) }
    Text("Get everything out", style = MaterialTheme.typography.headlineSmall)
    if (side != null && sideName != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            if (Cook.sideFirst(steps)) "Start with the $sideName. You'll start ${r.name} partway through."
            else "Start with ${r.name}. You'll start the $sideName partway through.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    @Composable
    fun group(title: String, lines: List<String>) {
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().clickable { got = if (line in got) got - line else got + line },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = line in got, onCheckedChange = { c -> got = if (c) got + line else got - line })
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    group("${r.emoji} ${r.name} (for $serv)", r.ingredients.map { Logic.ingText(it, serv, r.base) })
    if (side != null) group("${side.label} (side)", side.ingredients.map { Logic.ingText(it, serv, 4) })
}

/** Small strip of any timers running for this meal that aren't on the current step. */
@Composable
private fun RunningTimers(st: UserState, id: Int, steps: List<CookStep>, page: Int, mainCtl: TimerCtl, sideCtl: TimerCtl) {
    val current = steps.getOrNull(page - 1)
    val currentPrefix = current?.let { "${if (it.isSide) sideCtl.prefix else mainCtl.prefix}:${it.stepIndex}:" }
    val running = st.timers.filterKeys { k ->
        (k.startsWith("${mainCtl.prefix}:") || k.startsWith("${sideCtl.prefix}:")) &&
            (currentPrefix == null || !k.startsWith(currentPrefix))
    }
    if (running.isEmpty()) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running.keys) {
        while (true) { now = System.currentTimeMillis(); delay(500) }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        running.entries.sortedBy { it.value }.forEach { (key, end) ->
            val isSide = key.startsWith("${sideCtl.prefix}:")
            val stepNo = key.split(":").getOrNull(1)?.toIntOrNull()?.plus(1) ?: 0
            val left = ((end - now) / 1000).coerceAtLeast(0)
            val label = (if (isSide) "Side" else "Main") + " step $stepNo · " +
                if (left > 0) "%d:%02d".format(left / 60, left % 60) else "Done!"
            FilledTonalButton(onClick = { (if (isSide) sideCtl else mainCtl).stop(key) }) {
                Text("⏱ $label  ✕")
            }
        }
    }
}
