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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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

    // ---- Amounts inside steps: "Sauté garlic [2 cloves]" ----------------------------

    private val skipWords = setOf(
        "fresh", "large", "small", "medium", "boneless", "skinless", "low-sodium", "extra-virgin",
        "shredded", "grated", "dried", "frozen", "plain", "whole", "baby", "mild", "hot", "sweet",
        "red", "green", "white", "black", "yellow", "dry", "light", "dark", "italian", "mexican",
        "cooked", "crushed", "diced", "sliced", "chopped", "minced", "bulk", "unsweetened", "oil-packed",
    )
    private val pluralUnits = mapOf(
        "clove" to "cloves", "can" to "cans", "cup" to "cups", "bag" to "bags", "jar" to "jars",
        "pkg" to "pkgs", "box" to "boxes", "bunch" to "bunches",
    )
    private val amountBefore = Regex(
        "\\d[\\d/.½¼¾\\s-]*(cups?|tbsp|tsp|lbs?|oz|cloves?|slices|cans?|pinch)?\\s*(of\\s+)?$",
        RegexOption.IGNORE_CASE,
    )

    private fun amountText(i: Ingredient): String {
        val q = Logic.fmt(i.q)
        val u = if (i.q > 1.0) pluralUnits[i.u] ?: i.u else i.u
        return listOf(q, u).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Words to look for in the step for an ingredient: full name, then last word, then first word. */
    private fun phrases(name: String): List<List<String>> {
        val words = name.lowercase().replace(Regex("\\(.*?\\)"), " ").split(Regex("[^a-z'-]+"))
            .filter { it.isNotBlank() && it !in skipWords }
        if (words.isEmpty()) return emptyList()
        val full = words.dropLast(1).plus(stem(words.last())).joinToString(" ")
        return listOf(
            listOf(full),
            if (words.size > 1) listOf(stem(words.last())) else emptyList(),
            if (words.size > 1) listOf(stem(words.first())) else emptyList(),
        )
    }

    /** "carrots" -> "carrot", "tomatoes" -> "tomato", so singular and plural both match. */
    private fun stem(w: String): String = when {
        w.endsWith("oes") -> w.dropLast(2)
        w.endsWith("s") && !w.endsWith("ss") && w.length > 3 -> w.dropLast(1)
        else -> w
    }

    /** A step split into plain text and highlighted amounts. */
    data class Piece(val text: String, val isAmount: Boolean)

    /**
     * Adds amounts after the first mention of each ingredient across the steps.
     * Skips mentions that already have an amount right before them ("3 tbsp butter").
     */
    fun withAmounts(steps: List<String>, ings: List<Ingredient>): List<List<Piece>> {
        val usable = ings.filter { it.q > 0 }
        val done = mutableSetOf<Int>() // ingredient indexes already shown
        // Inserts per step: position -> amount text
        val inserts = steps.map { mutableListOf<Pair<Int, String>>() }
        val claimed = steps.map { mutableListOf<IntRange>() }
        for (tier in 0..2) {
            for ((idx, ing) in usable.withIndex()) {
                if (idx in done) continue
                val phrase = phrases(ing.name).getOrNull(tier)?.firstOrNull() ?: continue
                val rx = Regex("\\b" + Regex.escape(phrase) + "(e?s)?\\b", RegexOption.IGNORE_CASE)
                for ((si, step) in steps.withIndex()) {
                    val m = rx.findAll(step).firstOrNull { mm -> claimed[si].none { it.first <= mm.range.last && mm.range.first <= it.last } }
                        ?: continue
                    claimed[si] += m.range
                    done += idx
                    if (!amountBefore.containsMatchIn(step.substring(0, m.range.first))) {
                        inserts[si] += (m.range.last + 1) to amountText(ing)
                    }
                    break
                }
            }
        }
        return steps.mapIndexed { si, step ->
            val pieces = mutableListOf<Piece>()
            var at = 0
            inserts[si].sortedBy { it.first }.forEach { (pos, amt) ->
                pieces += Piece(step.substring(at, pos), false)
                pieces += Piece(" [$amt]", true)
                at = pos
            }
            pieces += Piece(step.substring(at), false)
            pieces
        }
    }

    /** True if the side's first step comes before the main dish's first step. */
    fun sideFirst(steps: List<CookStep>): Boolean = steps.firstOrNull()?.isSide == true
}

/** Full-screen cooking view: ingredients, then every step on one scrolling page. The screen stays on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookScreen(vm: MealsViewModel, st: UserState, id: Int, timerCtl: (Recipe) -> TimerCtl, onClose: () -> Unit) {
    val r = vm.recipes(st)[id]
    if (r == null) { onClose(); return }
    val side = if (id in st.week) st.sides[id]?.let { vm.sideMap[it] } else null
    val sideName = side?.label?.substringAfter(" ")
    val serv = st.servings
    val steps = remember(r, side) { Cook.order(r, side) }
    // Amounts scaled to "Cooking for", added after each ingredient's first mention.
    val mainPieces = remember(r, serv) { Cook.withAmounts(r.steps, r.ingredients.map { Logic.scale(it, serv, r.base) }) }
    val sidePieces = remember(side, serv) {
        side?.let { sd -> Cook.withAmounts(sd.steps, sd.ingredients.map { Logic.scale(it, serv, 4) }) } ?: emptyList()
    }
    var finished by remember(id) { mutableStateOf(setOf<Int>()) }
    val mainCtl = timerCtl(r)
    val sideCtl = mainCtl.copy(prefix = mainCtl.prefix + "side", title = sideName ?: "Side")

    // Keep the screen awake while cooking.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Exit cooking mode") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            RunningTimers(st, steps, mainCtl, sideCtl)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                IngredientsPage(r, side, sideName, serv, steps)
                Spacer(Modifier.height(22.dp))
                Text("Steps", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap a step when it's done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                steps.forEachIndexed { n, step ->
                    val done = n in finished
                    val pieces = (if (step.isSide) sidePieces else mainPieces).getOrNull(step.stepIndex)
                        ?: listOf(Cook.Piece(step.text, false))
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { finished = if (done) finished - n else finished + n }
                            .alpha(if (done) 0.45f else 1f)
                            .padding(vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (done) "✓" else "${n + 1}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(34.dp),
                            )
                            StepTag(step, r, side)
                        }
                        val highlight = MaterialTheme.colorScheme.primary
                        Text(
                            buildAnnotatedString {
                                if (step.startsSide) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Start the side: ") }
                                pieces.forEach { p ->
                                    if (p.isAmount) withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) { append(p.text) }
                                    else append(p.text)
                                }
                            },
                            fontSize = 20.sp, lineHeight = 28.sp,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(start = 34.dp, top = 4.dp),
                        )
                        if (step.times.isNotEmpty() && !done) {
                            val ctl = if (step.isSide) sideCtl else mainCtl
                            Row(
                                Modifier.padding(start = 34.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                step.times.forEachIndexed { k, t ->
                                    TimerButton("${ctl.prefix}:${step.stepIndex}:$k", "${ctl.title}: step ${step.stepIndex + 1}", t, ctl)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (steps.isNotEmpty() && finished.size == steps.size) {
                    Text("That's it. Time to eat! 🎉", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(40.dp))
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

/** Small strip of every timer running for this meal, so you can see them while scrolling. */
@Composable
private fun RunningTimers(st: UserState, steps: List<CookStep>, mainCtl: TimerCtl, sideCtl: TimerCtl) {
    val running = st.timers.filterKeys { k ->
        k.startsWith("${mainCtl.prefix}:") || k.startsWith("${sideCtl.prefix}:")
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
            val idx = key.split(":").getOrNull(1)?.toIntOrNull()
            val stepNo = steps.indexOfFirst { it.isSide == isSide && it.stepIndex == idx } + 1
            val left = ((end - now) / 1000).coerceAtLeast(0)
            val label = "Step $stepNo · " + if (left > 0) "%d:%02d".format(left / 60, left % 60) else "Done!"
            FilledTonalButton(onClick = { (if (isSide) sideCtl else mainCtl).stop(key) }) {
                Text("⏱ $label  ✕")
            }
        }
    }
}
