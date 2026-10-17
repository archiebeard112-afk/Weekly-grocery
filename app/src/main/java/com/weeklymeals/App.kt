package com.weeklymeals

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
import androidx.compose.ui.unit.dp

private enum class Overlay { None, Settings, AddRecipe, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MealsViewModel) {
    val st by vm.state.collectAsState()
    val review by vm.review.collectAsState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var confirmRemove by remember { mutableStateOf<Recipe?>(null) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }

    BackHandler(enabled = review != null) { vm.closeReview() }
    BackHandler(enabled = review == null && overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = review == null && overlay == Overlay.None && viewing != null) { viewing = null }

    val toggleFav: (Recipe) -> Unit = { r ->
        if (Logic.isFav(r, st)) confirmRemove = r else vm.addFav(r.id)
    }

    Box(Modifier.fillMaxSize()) {
    if (review != null) {
        CartReviewScreen(vm, review!!)
    } else if (overlay == Overlay.None && viewing != null) {
        RecipeScreen(vm, st, viewing!!, toggleFav) { viewing = null }
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
                        icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Browse") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.DateRange, null) },
                        label = { Text("My Week (${st.week.size})") },
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Favorite, null) }, label = { Text("Favorites") },
                    )
                    NavigationBarItem(
                        selected = tab == 3, onClick = { tab = 3 },
                        icon = { Icon(Icons.Filled.ShoppingCart, null) }, label = { Text("Shop") },
                    )
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> BrowseScreen(vm, st, toggleFav) { viewing = it }
                    1 -> WeekScreen(vm, st, toggleFav)
                    2 -> FavoritesScreen(vm, st, toggleFav) { overlay = Overlay.AddRecipe }
                    else -> ShopScreen(vm, st) { overlay = Overlay.Settings }
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

// ---- Shared pieces ----------------------------------------------------------------

@Composable
private fun Steps(steps: List<String>) {
    steps.forEachIndexed { n, s ->
        Text("${n + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun RecipeDetails(r: Recipe, serv: Int, side: Side?) {
    Heading("Ingredients (for $serv)")
    r.ingredients.forEach { Text("• " + Logic.ingText(it, serv, r.base), style = MaterialTheme.typography.bodyMedium) }
    Heading("Steps")
    Steps(r.steps)
    if (side != null) {
        Heading("Side: ${side.label}")
        side.ingredients.forEach { Text("• " + Logic.ingText(it, serv, 4), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.padding(2.dp))
        Steps(side.steps)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("10 ideas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { vm.refreshBrowse() }) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("10 more")
            }
        }
        Spacer(Modifier.height(6.dp))
        shown.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pair.forEach { r ->
                    RecipeTile(r, st, toggleFav, onAdd = { vm.addToWeek(r.id) }, onOpen = { onOpen(r.id) }, Modifier.weight(1f).fillMaxHeight())
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (inWeek) "In your week" else "",
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

/** One recipe on its own page: buttons at the top, then ingredients and steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeScreen(vm: MealsViewModel, st: UserState, id: Int, toggleFav: (Recipe) -> Unit, onBack: () -> Unit) {
    val r = vm.recipes(st)[id]
    if (r == null) { onBack(); return }
    val side = st.sides[id]?.let { vm.sideMap[it] }
    val inWeek = id in st.week
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = { FavButton(r, st, toggleFav) },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.emoji, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(12.dp))
                Button(onClick = { vm.addToWeek(id) }, enabled = !inWeek) {
                    Text(if (inWeek) "In your week" else "Add to week")
                }
            }
            RecipeDetails(r, st.servings, if (inWeek) side else null)
        }
    }
}

// ---- My Week -----------------------------------------------------------------------

@Composable
private fun WeekScreen(vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit) {
    val all = vm.recipes(st)
    var confirmFinish by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Cooking for", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.setServings(st.servings - 1) }) { Text("−") }
                Text("${st.servings}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp))
                OutlinedButton(onClick = { vm.setServings(st.servings + 1) }) { Text("+") }
            }
        }
        if (st.week.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Add recipes from Browse or Favorites.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(st.week, key = { it }) { id ->
            val r = all[id] ?: return@items
            var open by remember { mutableStateOf(false) }
            var menu by remember { mutableStateOf(false) }
            val sideKey = st.sides[id]
            val side = sideKey?.let { vm.sideMap[it] }
            val index = st.week.indexOf(id)
            Card(colors = cardColors, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.move(id, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                        }
                        IconButton(onClick = { vm.move(id, 1) }, enabled = index < st.week.size - 1) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Side: ", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            OutlinedButton(onClick = { menu = true }) { Text(side?.label ?: "None") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("None") }, onClick = { vm.setSide(id, null); menu = false })
                                r.sides.mapNotNull { vm.sideMap[it] }.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        onClick = { vm.setSide(id, s.key); menu = false },
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { open = !open }) { Text(if (open) "Hide steps" else "Show steps") }
                        Spacer(Modifier.weight(1f))
                        FavButton(r, st, toggleFav)
                        IconButton(onClick = { vm.removeFromWeek(id) }) {
                            Icon(Icons.Filled.Delete, "Remove from week")
                        }
                    }
                    if (open) RecipeDetails(r, st.servings, side)
                }
            }
        }
        if (st.week.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { confirmFinish = true }) { Text("Finish week") }
                }
            }
        }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this week?") },
            text = { Text("Your ${st.week.size} meals will be saved to History, and My Week and the shopping list will be cleared.") },
            confirmButton = {
                TextButton(onClick = { vm.finishWeek(); confirmFinish = false }) { Text("Finish week") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Cancel") } },
        )
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
