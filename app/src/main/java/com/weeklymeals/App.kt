package com.weeklymeals

import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp

private enum class Overlay { None, Settings, AddRecipe, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MealsViewModel) {
    val st by vm.state.collectAsState()
    val picking by vm.picking.collectAsState()
    // Kept here so the Shop list keeps its scroll position while a product list is open.
    val shopListState = rememberLazyListState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var confirmRemove by remember { mutableStateOf<Recipe?>(null) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    var cooking by rememberSaveable { mutableStateOf<Int?>(null) }
    val ctx = LocalContext.current
    val openCart by vm.openCart.collectAsState()
    LaunchedEffect(openCart) {
        if (openCart) {
            vm.openCart.value = false
            openKingSoopersCart(ctx)
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) vm.message.value = "Allow notifications so timers can ring"
    }
    val timerCtl: (Recipe) -> TimerCtl = { r ->
        TimerCtl(
            prefix = "r${r.id}", title = r.name, running = st.timers,
            start = { key, label, minutes ->
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.startTimer(key, label, minutes)
            },
            stop = { vm.stopTimer(it) },
        )
    }

    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }

    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && viewing != null && cooking == null) { viewing = null }

    val toggleFav: (Recipe) -> Unit = { r ->
        if (Logic.isFav(r, st)) confirmRemove = r else vm.addFav(r.id)
    }

    Box(Modifier.fillMaxSize()) {
    if (picking != null) {
        ProductPicker(vm, picking!!) { vm.picking.value = null }
    } else if (overlay == Overlay.None && cooking != null) {
        CookScreen(vm, st, cooking!!, timerCtl) { cooking = null }
    } else if (overlay == Overlay.None && viewing != null) {
        RecipeScreen(vm, st, viewing!!, toggleFav, timerCtl, onCook = { cooking = viewing }) { viewing = null }
    } else if (overlay == Overlay.Settings) {
        SettingsScreen(vm, st) { overlay = Overlay.None }
    } else if (overlay == Overlay.History) {
        HistoryScreen(vm, st) { overlay = Overlay.None }
    } else if (overlay == Overlay.AddRecipe) {
        AddRecipeScreen(vm) { overlay = Overlay.None }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Weekly Meals") },
                    actions = {
                        IconButton(onClick = { overlay = Overlay.History }) {
                            Icon(Icons.Filled.List, contentDescription = "History")
                        }
                        IconButton(onClick = { overlay = Overlay.Settings }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Search, null) }, label = { TabLabel("Browse") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { CountIcon(st.week.size) { Icon(Icons.Filled.DateRange, null) } },
                        label = { TabLabel("Week") },
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Favorite, null) }, label = { TabLabel("Favorites") },
                    )
                    NavigationBarItem(
                        selected = tab == 3, onClick = { tab = 3 },
                        icon = { Icon(Icons.Filled.ShoppingCart, null) }, label = { TabLabel("Shop") },
                    )
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> BrowseScreen(vm, st, toggleFav) { viewing = it }
                    1 -> WeekScreen(vm, st, onOpen = { viewing = it }, onBrowse = { tab = 0 })
                    2 -> FavoritesScreen(vm, st, toggleFav) { overlay = Overlay.AddRecipe }
                    else -> ShopScreen(vm, st, shopListState) { overlay = Overlay.Settings }
                }
            }
        }
    }
    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    confirmRemove?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from favorites?") },
            text = { Text("Are you sure you want to remove ${r.name} from your favorites?") },
            confirmButton = {
                TextButton(onClick = { vm.removeFav(r.id); confirmRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Keep") } },
        )
    }
}

/** Bottom-bar label, always centered on one line. */
@Composable
private fun TabLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
}

/** Tab icon with a small number bubble when count > 0. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountIcon(count: Int, icon: @Composable () -> Unit) {
    if (count <= 0) icon() else BadgedBox(badge = { androidx.compose.material3.Badge { Text("$count") } }) { icon() }
}

// ---- Shared pieces ----------------------------------------------------------------

/** Lets steps show timer buttons (only for recipes in your week). */
data class TimerCtl(
    val prefix: String,
    val title: String,
    val running: Map<String, Long>,
    val start: (key: String, label: String, minutes: Int) -> Unit,
    val stop: (key: String) -> Unit,
)

@Composable
private fun Steps(steps: List<String>, ctl: TimerCtl? = null) {
    steps.forEachIndexed { n, s ->
        Text("${n + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        if (ctl != null) {
            val times = Logic.stepTimes(s)
            if (times.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    times.forEachIndexed { k, t ->
                        TimerButton("${ctl.prefix}:$n:$k", "${ctl.title}: step ${n + 1}", t, ctl)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimerButton(key: String, label: String, t: Logic.StepTime, ctl: TimerCtl) {
    val end = ctl.running[key]
    val small = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    if (end == null) {
        OutlinedButton(
            onClick = { ctl.start(key, label, t.minutes) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { Text("⏱ " + Logic.fmtMinutes(t.minutes) + if (t.perSide) " per side" else "") }
        return
    }
    var now by remember(key) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key, end) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= end) break
            delay(500)
        }
    }
    val left = ((end - now) / 1000).coerceAtLeast(0)
    if (left > 0) {
        FilledTonalButton(
            onClick = { ctl.stop(key) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { Text("⏱ %d:%02d  ·  Stop".format(left / 60, left % 60)) }
    } else {
        Button(
            onClick = { ctl.stop(key) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { Text("⏰ Done! Tap to stop") }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun RecipeDetails(r: Recipe, serv: Int, side: Side?, ctl: TimerCtl? = null) {
    Heading("Ingredients (for $serv)")
    r.ingredients.forEach { Text("• " + Logic.ingText(it, serv, r.base), style = MaterialTheme.typography.bodyMedium) }
    Heading("Steps")
    Steps(r.steps, ctl)
    if (side != null) {
        Heading("Side: ${side.label}")
        side.ingredients.forEach { Text("• " + Logic.ingText(it, serv, 4), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.padding(2.dp))
        Steps(side.steps, ctl?.copy(prefix = ctl.prefix + "side", title = side.label.substringAfter(" ")))
    }
}

@Composable
private fun FavButton(r: Recipe, st: UserState, onToggle: (Recipe) -> Unit) {
    val fav = Logic.isFav(r, st)
    IconButton(onClick = { onToggle(r) }) {
        Icon(
            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private val cardColors @Composable get() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
)

// ---- Browse ------------------------------------------------------------------------

@Composable
private fun BrowseScreen(vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit, onOpen: (Int) -> Unit) {
    val ids by vm.browse.collectAsState()
    val all = vm.recipes(st)
    val shown = ids.mapNotNull { all[it] }
    // Fits on one screen: a header plus a 2 x 5 grid that stretches to fill the space.
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        val saleOnly by vm.saleOnly.collectAsState()
        val progress by vm.salesProgress.collectAsState()
        val cache = vm.salesCache(st)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (saleOnly) "On sale" else "10 ideas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("🏷️ Sale", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = saleOnly,
                onCheckedChange = { vm.setSaleOnly(it) },
                enabled = progress == null,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp),
            )
            FilledTonalButton(onClick = { vm.refreshBrowse() }, enabled = progress == null) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(4.dp))
                Text("More")
            }
        }
        Spacer(Modifier.height(6.dp))
        progress?.let { (done, total) ->
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Checking King Soopers sale prices…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Text("$done of $total ingredients", style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        if (saleOnly && shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No meals save \$3 or more at your store today.", textAlign = TextAlign.Center)
            }
            return@Column
        }
        shown.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pair.forEach { r ->
                    RecipeTile(
                        r, st, toggleFav, onAdd = { vm.addToWeek(r.id) }, onOpen = { onOpen(r.id) },
                        Modifier.weight(1f).fillMaxHeight(),
                        saleSave = Sales.savings(r, st.servings, cache),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        repeat(5 - (shown.size + 1) / 2) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun RecipeTile(
    r: Recipe, st: UserState, toggleFav: (Recipe) -> Unit,
    onAdd: () -> Unit, onOpen: () -> Unit, modifier: Modifier,
    saleSave: Double = 0.0,
) {
    val inWeek = r.id in st.week
    Card(
        colors = if (inWeek) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else cardColors,
        modifier = modifier.clickable(onClick = onOpen),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 6.dp, end = 2.dp, bottom = 2.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(r.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    r.name, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            val badges = (
                (if (saleSave >= Sales.MIN_SAVINGS) listOf("🏷️ " + Sales.money(saleSave).substringBefore(".")) else emptyList()) +
                    Logic.badges(r)
                ).take(2)
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    badges.forEach { Badge(it, small = true) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⏱ " + Logic.fmtShort(Logic.cookMinutes(r)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                FavButton(r, st, toggleFav)
                IconButton(onClick = onAdd, enabled = !inWeek) {
                    Icon(
                        if (inWeek) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = if (inWeek) "In your week" else "Add to week",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** "On sale this week" for one recipe's main ingredients. */
@Composable
private fun SaleSection(vm: MealsViewModel, st: UserState, r: Recipe) {
    if (st.kroger.store.id.isEmpty()) return
    val progress by vm.salesProgress.collectAsState()
    val cache = vm.salesCache(st)
    Heading("🏷️ On sale this week")
    when {
        !Sales.isChecked(r, st.servings, cache) -> {
            if (progress != null) {
                Text("Checking prices…", style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedButton(onClick = { vm.checkSales(r) }) { Text("Check sales") }
            }
        }
        else -> {
            val lines = Sales.lines(r, st.servings, cache)
            if (lines.isEmpty()) {
                Text("No big savings on this one this week.", style = MaterialTheme.typography.bodyMedium)
            } else {
                lines.forEach { l ->
                    Text(
                        "• ${l.term.replaceFirstChar { it.uppercase() }}: ${Sales.money(l.sale.promo)}, " +
                            "was ${Sales.money(l.sale.regular)}" +
                            (if (l.sale.size.isNotBlank()) " (${l.sale.size})" else "") +
                            " · save ~${Sales.money(l.savings)}" + (if (l.packages > 1) " for ${l.packages}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Save about ${Sales.money(lines.sumOf { it.savings })} on this meal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A small rounded label like "🍲 Crock-Pot" or "💲 Budget". */
@Composable
private fun Badge(text: String, small: Boolean) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 1.dp else 4.dp),
    )
}

/** On a planned recipe: which day, the side dish, and Remove from week. */
@Composable
private fun WeekControls(vm: MealsViewModel, st: UserState, r: Recipe, side: Side?) {
    var menu by remember { mutableStateOf(false) }
    val day = Logic.dayPlan(st.week, st.days).dayOf(r.id)
    Heading(if (day != null) "📅 Planned for ${Logic.DAY_LONG[day]}" else "📅 In your week (extra meal)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Side: ", style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { menu = true }) { Text(side?.label ?: "None") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { vm.setSide(r.id, null); menu = false })
                r.sides.mapNotNull { vm.sideMap[it] }.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setSide(r.id, s.key); menu = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { vm.removeFromWeek(r.id) }) { Text("Remove from week") }
    }
}

/** One recipe on its own page: buttons at the top, then ingredients and steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeScreen(
    vm: MealsViewModel, st: UserState, id: Int, toggleFav: (Recipe) -> Unit,
    timerCtl: (Recipe) -> TimerCtl, onCook: () -> Unit, onBack: () -> Unit,
) {
    val r = vm.recipes(st)[id]
    if (r == null) { onBack(); return }
    val side = st.sides[id]?.let { vm.sideMap[it] }
    val inWeek = id in st.week
    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = { FavButton(r, st, toggleFav) },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.emoji, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(12.dp))
                Text(r.name, style = MaterialTheme.typography.headlineSmall)
            }
            val badges = Logic.badges(r)
            if (badges.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) { badges.forEach { Badge(it, small = false) } }
            }
            Text(
                "⏱ About ${Logic.fmtMinutes(Logic.cookMinutes(r))} total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCook) { Text("▶  Start cooking") }
                if (!inWeek) OutlinedButton(onClick = { vm.addToWeek(id) }) { Text("Add to week") }
            }
            if (inWeek) WeekControls(vm, st, r, side)
            SaleSection(vm, st, r)
            RecipeDetails(r, st.servings, if (inWeek) side else null)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---- Favorites ---------------------------------------------------------------------

@Composable
private fun FavoritesScreen(vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit, onAdd: () -> Unit) {
    val favs = vm.recipes(st).values.filter { Logic.isFav(it, st) }.sortedBy { it.name }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Favorites", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add recipe")
                }
            }
        }
        if (favs.isEmpty()) {
            item { Text("No favorites yet. Tap the heart on any recipe.", modifier = Modifier.padding(top = 24.dp)) }
        }
        items(favs, key = { it.id }) { r ->
            var open by remember { mutableStateOf(false) }
            Card(colors = cardColors, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FavButton(r, st, toggleFav)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val inWeek = r.id in st.week
                        Button(onClick = { vm.addToWeek(r.id) }, enabled = !inWeek) {
                            Text(if (inWeek) "In your week" else "Add to week")
                        }
                        TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Details") }
                    }
                    if (open) RecipeDetails(r, st.servings, null)
                }
            }
        }
    }
}

// ---- History -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: MealsViewModel, st: UserState, onBack: () -> Unit) {
    val all = vm.recipes(st)
    val fmt = remember { java.text.SimpleDateFormat("EEE, MMM d, yyyy", java.util.Locale.getDefault()) }
    var confirmDelete by remember { mutableStateOf<PastWeek?>(null) }
    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (st.history.isEmpty()) {
                item {
                    Text(
                        "No past weeks yet. When you're done with a week, tap \"Finish week\" at the bottom of My Week to save it here.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(st.history, key = { it.date }) { w ->
                var open by remember { mutableStateOf<Int?>(null) }
                Card(colors = cardColors, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Week of ${fmt.format(java.util.Date(w.date))}", style = MaterialTheme.typography.titleMedium)
                        Text("${w.recipes.size} meals · cooking for ${w.servings}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.padding(4.dp))
                        w.recipes.forEach { id ->
                            val r = all[id]
                            if (r == null) {
                                Text("• (recipe no longer available)", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                val side = w.sides[id]?.let { vm.sideMap[it] }
                                TextButton(onClick = { open = if (open == id) null else id }) {
                                    Text(
                                        "${r.emoji}  ${r.name}" + (side?.let { " + ${it.label}" } ?: ""),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (open == id) {
                                    Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                                        RecipeDetails(r, w.servings, side)
                                        TextButton(onClick = { vm.addToWeek(id) }, enabled = id !in st.week) {
                                            Text(if (id in st.week) "In this week" else "Add to this week")
                                        }
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                vm.repeatWeek(w)
                                vm.message.value = "Added to My Week"
                            }) { Text("Repeat this week") }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { confirmDelete = w }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this week?") },
            text = { Text("This removes it from History. Your recipes and favorites are not affected.") },
            confirmButton = { TextButton(onClick = { vm.deletePastWeek(w); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

const val KING_SOOPERS_CART = "https://www.kingsoopers.com/cart"

/** Opens the King Soopers cart: in their app if it handles the link, otherwise the browser. */
fun openKingSoopersCart(ctx: android.content.Context) {
    try {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(KING_SOOPERS_CART))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: android.content.ActivityNotFoundException) {
    }
}
