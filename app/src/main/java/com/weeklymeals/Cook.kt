package com.weeklymeals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
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

    fun amountText(i: Ingredient): String {
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

    /** Amounts already written in a step: "3 tbsp", "2 tablespoons of", "1 1/2 cups", "½ pound". */
    private val writtenAmount = Regex(
        "(\\d+\\s+\\d/\\d|\\d+[½¼¾]|\\d/\\d|\\d+(?:\\.\\d+)?|[½¼¾])\\s*" +
            "(tablespoons?|tbsps?\\.?|teaspoons?|tsps?\\.?|cups?|pounds?|lbs?\\.?|ounces?|oz\\.?|cloves?)\\b(\\s+of)?",
        RegexOption.IGNORE_CASE,
    )

    private fun parseNum(s: String): Double? {
        val t = s.trim()
        val frac = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75)
        Regex("^(\\d+)\\s+(\\d)/(\\d)$").find(t)?.let {
            return it.groupValues[1].toDouble() + it.groupValues[2].toDouble() / it.groupValues[3].toDouble()
        }
        Regex("^(\\d)/(\\d)$").find(t)?.let { return it.groupValues[1].toDouble() / it.groupValues[2].toDouble() }
        val last = t.lastOrNull() ?: return null
        frac[last]?.let { f ->
            val whole = t.dropLast(1)
            return (if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null) + f
        }
        return t.toDoubleOrNull()
    }

    private val singularUnit = mapOf(
        "tablespoons" to "tablespoon", "teaspoons" to "teaspoon", "cups" to "cup",
        "pounds" to "pound", "ounces" to "ounce", "cloves" to "clove",
    )
    private val pluralUnit = singularUnit.entries.associate { (k, v) -> v to k }

    /** "tablespoons" -> "tablespoon" when the scaled amount is 1 or less, and back. */
    private fun unitFor(u: String, q: Double): String {
        val w = u.lowercase()
        return when {
            q <= 1.0 && w in singularUnit -> singularUnit.getValue(w)
            q > 1.0 && w in pluralUnit -> pluralUnit.getValue(w)
            else -> u
        }
    }

    private data class Edit(val range: IntRange, val text: String)

    /**
     * Highlights amounts in the steps, scaled by [factor] (servings ÷ recipe servings):
     *  - amounts already written ("3 tbsp butter") are scaled and highlighted with the ingredient;
     *  - otherwise the amount is added in front of the first mention ("2 cloves garlic").
     * [ings] are already scaled to servings.
     */
    fun withAmounts(steps: List<String>, ings: List<Ingredient>, factor: Double): List<List<Piece>> {
        val usable = ings.filter { it.q > 0 }
        val done = mutableSetOf<Int>() // ingredient indexes already shown
        val edits = steps.map { mutableListOf<Edit>() }
        val claimed = steps.map { mutableListOf<IntRange>() }
        fun overlaps(si: Int, r: IntRange) = claimed[si].any { it.first <= r.last && r.first <= it.last }

        // 1) Amounts already written in the step.
        for ((si, step) in steps.withIndex()) {
            for (m in writtenAmount.findAll(step)) {
                if (overlaps(si, m.range)) continue
                val n = parseNum(m.groupValues[1]) ?: continue
                val q = n * factor
                var end = m.range.last
                // Include the ingredient right after it ("3 tbsp [unsalted] butter"), if it's one of ours.
                val after = step.substring(end + 1)
                found@ for (tier in 0..2) {
                    for ((idx, ing) in usable.withIndex()) {
                        val phrase = phrases(ing.name).getOrNull(tier)?.firstOrNull() ?: continue
                        val rx = Regex("^\\s+(?:[A-Za-z-]+\\s+){0,2}?" + Regex.escape(phrase) + "(e?s)?\\b", RegexOption.IGNORE_CASE)
                        val mm = rx.find(after) ?: continue
                        end += mm.value.length
                        done += idx
                        break@found
                    }
                }
                val text = Logic.fmt(q) + " " + unitFor(m.groupValues[2], q) + m.groupValues[3] +
                    step.substring(m.range.last + 1, end + 1)
                val range = m.range.first..end
                edits[si] += Edit(range, text)
                claimed[si] += range
            }
        }

        // 2) Add amounts in front of the first plain mention of each remaining ingredient.
        for (tier in 0..2) {
            for ((idx, ing) in usable.withIndex()) {
                if (idx in done) continue
                val phrase = phrases(ing.name).getOrNull(tier)?.firstOrNull() ?: continue
                val rx = Regex("\\b" + Regex.escape(phrase) + "(e?s)?\\b", RegexOption.IGNORE_CASE)
                for ((si, step) in steps.withIndex()) {
                    val m = rx.findAll(step).firstOrNull { !overlaps(si, it.range) } ?: continue
                    claimed[si] += m.range
                    done += idx
                    if (!amountBefore.containsMatchIn(step.substring(0, m.range.first))) {
                        edits[si] += Edit(m.range, amountText(ing) + " " + m.value)
                    }
                    break
                }
            }
        }

        return steps.mapIndexed { si, step ->
            val pieces = mutableListOf<Piece>()
            var at = 0
            edits[si].sortedBy { it.range.first }.forEach { e ->
                if (e.range.first < at) return@forEach
                pieces += Piece(step.substring(at, e.range.first), false)
                pieces += Piece(e.text, true)
                at = e.range.last + 1
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
fun CookScreen(
    vm: MealsViewModel, st: UserState, id: Int, timerCtl: (Recipe) -> TimerCtl,
    onDone: () -> Unit = {}, onClose: () -> Unit,
) {
    val r = vm.recipes(st)[id]
    if (r == null) { onClose(); return }
    val side = if (id in st.week) st.sides[id]?.let { vm.sideMap[it] } else null
    val sideName = side?.label?.substringAfter(" ")
    val serv = st.servings
    val mainServ = Logic.servFor(r, serv)
    val steps = remember(r, side) { Cook.order(r, side) }
    // Amounts scaled to "Cooking for" (recipes you added keep their own), added after each ingredient's first mention.
    val mainPieces = remember(r, mainServ) { Cook.withAmounts(r.steps, r.ingredients.map { Logic.scale(it, mainServ, r.base) }, mainServ.toDouble() / r.base) }
    val sidePieces = remember(side, serv) {
        side?.let { sd -> Cook.withAmounts(sd.steps, sd.ingredients.map { Logic.scale(it, serv, 4) }, serv / 4.0) } ?: emptyList()
    }
    // Picks up where you left off (you can leave and come back with the cooking bubble).
    val resume = st.cookingId == id
    var finished by remember(id) { mutableStateOf(if (resume) st.cookDone else setOf()) }
    val scroll = rememberScrollState(if (resume) st.cookScroll else 0)
    DisposableEffect(id) {
        onDispose { vm.saveCookPlace(id, scroll.value, finished) }
    }
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
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
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
                                    if (p.isAmount) withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append(p.text) }
                                    else append(p.text)
                                }
                            },
                            fontSize = 16.sp, lineHeight = 23.sp,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(start = 34.dp, top = 4.dp),
                        )
                        // This step uses something split with another meal: how much to use now.
                        if (!done) shareForStep(vm, st, id, step.text)?.let { note ->
                            Text(
                                note, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 34.dp, top = 4.dp),
                            )
                        }
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
                Spacer(Modifier.height(16.dp))
                // Ends cooking mode (and the bubble). The first time you make a meal, asks how it was.
                var askRating by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { if (id !in st.ratings) askRating = true else { vm.stopCooking(); onDone() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("✓  Done cooking")
                }
                if (askRating) {
                    fun finish(v: Int) { askRating = false; vm.rate(id, v); vm.stopCooking(); onDone() }
                    AlertDialog(
                        onDismissRequest = { askRating = false },
                        title = { Text("How was ${r.name}?") },
                        text = {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                RatingChoices(null, ::finish)
                                // Centered under the choices.
                                TextButton(onClick = { finish(0) }, modifier = Modifier.padding(top = 12.dp)) { Text("Skip") }
                            }
                        },
                        confirmButton = {},
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun StepTag(step: CookStep, r: Recipe, side: Side?) {
    val text = if (step.isSide) "${side?.label ?: "Side"} · Side" else "Main"
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
    Text("Ingredients", style = MaterialTheme.typography.titleLarge)
    if (side != null && isStoreSide(side)) {
        // Store-bought: nothing to cook, just a reminder.
        Text(
            "Side: ${storeSideTitle(side)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    } else if (side != null && sideName != null) {
        Text(
            if (Cook.sideFirst(steps)) "Start with the $sideName. You'll start ${r.name} partway through."
            else "Start with ${r.name}. You'll start the $sideName partway through.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    val mainServ = Logic.servFor(r, serv)
    IngredientColumns("${r.name} (for $mainServ)", r.ingredients.map { Logic.scale(it, mainServ, r.base) })
    if (side != null && !isStoreSide(side)) IngredientColumns("${side.label} (side)", side.ingredients.map { Logic.scale(it, serv, 4) })
}

/** Compact ingredient list in two columns: bold amount, then the name. */
@Composable
private fun IngredientColumns(title: String, items: List<Ingredient>) {
    val highlight = MaterialTheme.colorScheme.primary
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    val half = (items.size + 1) / 2
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(items.take(half), items.drop(half)).forEach { col ->
            Column(Modifier.weight(1f)) {
                col.forEach { i ->
                    val amt = if (i.q > 0) Cook.amountText(i) else ""
                    Text(
                        buildAnnotatedString {
                            if (amt.isNotEmpty()) {
                                withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append(amt) }
                                append(" ")
                            }
                            append(i.name.replaceFirstChar { it.lowercase() })
                        },
                        fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
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

/**
 * The small cooking window that opens from the floating bubble (outside the app):
 * running timers, then every step with amounts highlighted. Tap a step to tick it off.
 */
@Composable
internal fun MiniCook(vm: MealsViewModel, st: UserState, id: Int, onOpenApp: () -> Unit) {
    val r = vm.recipes(st)[id] ?: return
    val side = if (id in st.week) st.sides[id]?.let { vm.sideMap[it] } else null
    val sideName = side?.label?.substringAfter(" ")
    val serv = st.servings
    val mainServ = Logic.servFor(r, serv)
    val steps = remember(r, side) { Cook.order(r, side) }
    val mainPieces = remember(r, mainServ) { Cook.withAmounts(r.steps, r.ingredients.map { Logic.scale(it, mainServ, r.base) }, mainServ.toDouble() / r.base) }
    val sidePieces = remember(side, serv) {
        side?.let { sd -> Cook.withAmounts(sd.steps, sd.ingredients.map { Logic.scale(it, serv, 4) }, serv / 4.0) } ?: emptyList()
    }
    val mainCtl = TimerCtl(
        prefix = "r${r.id}", title = r.name, running = st.timers,
        start = { key, label, minutes -> vm.startTimer(key, label, minutes) },
        stop = { vm.stopTimer(it) },
    )
    val sideCtl = mainCtl.copy(prefix = mainCtl.prefix + "side", title = sideName ?: "Side")
    val done = st.cookDone
    val highlight = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenApp) { Text("Open app") }
            }
            RunningTimers(st, steps, mainCtl, sideCtl)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                steps.forEachIndexed { n, step ->
                    val isDone = n in done
                    val pieces = (if (step.isSide) sidePieces else mainPieces).getOrNull(step.stepIndex)
                        ?: listOf(Cook.Piece(step.text, false))
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { vm.saveCookPlace(id, st.cookScroll, if (isDone) done - n else done + n) }
                            .alpha(if (isDone) 0.45f else 1f)
                            .padding(vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                if (isDone) "✓" else "${n + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = highlight, modifier = Modifier.width(26.dp),
                            )
                            Text(
                                buildAnnotatedString {
                                    if (step.isSide) withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append("Side · ") }
                                    pieces.forEach { p ->
                                        if (p.isAmount) withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append(p.text) }
                                        else append(p.text)
                                    }
                                },
                                fontSize = 15.sp, lineHeight = 21.sp,
                                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            )
                        }
                        if (step.times.isNotEmpty() && !isDone) {
                            val ctl = if (step.isSide) sideCtl else mainCtl
                            Row(Modifier.padding(start = 26.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                step.times.forEachIndexed { k, t ->
                                    TimerButton("${ctl.prefix}:${step.stepIndex}:$k", "${ctl.title}: step ${step.stepIndex + 1}", t, ctl)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}



/** "🔁 Use ½ lb · save the rest for Thu" (or "the ½ lb saved from Mon") if this step uses a shared ingredient. */
private fun shareForStep(vm: MealsViewModel, st: UserState, id: Int, text: String): String? {
    if (id !in st.week) return null
    val t = text.lowercase()
    val sh = vm.weekShares(st).values.firstOrNull { s ->
        id in s.meals && (t.contains(s.term) || t.contains(s.term.substringAfterLast(' ').trimEnd('s')))
    } ?: return null
    val all = vm.recipes(st)
    val amount = sh.amounts[id] ?: return null
    return if (sh.meals.first() == id) {
        val later = sh.meals.drop(1).joinToString(", ") { Shares.mealLabel(st, it, all[it]?.name ?: "").substringBefore(" ·") }
        "🔁 Use $amount ${sh.term} · save the rest for $later"
    } else {
        val from = sh.meals.first().let { Shares.mealLabel(st, it, all[it]?.name ?: "").substringBefore(" ·") }
        "🔁 Use the $amount ${sh.term} saved from $from"
    }
}

