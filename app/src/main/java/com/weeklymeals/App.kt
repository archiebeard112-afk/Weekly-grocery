package com.weeklymeals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.unit.dp

private enum class Overlay { None, Settings, AddRecipe }

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

    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }

    BackHandler(enabled = review != null) { vm.closeReview() }
    BackHandler(enabled = review == null && overlay != Overlay.None) { overlay = Overlay.None }

    val toggleFav: (Recipe) -> Unit = { r ->
        if (Logic.isFav(r, st)) confirmRemove = r else vm.addFav(r.id)
    }

    if (review != null) {
        CartReviewScreen(vm, review!!)
    } else if (overlay == Overlay.Settings) {
        SettingsScreen(vm, st) { overlay = Overlay.None }
    } else if (overlay == Overlay.AddRecipe) {
        AddRecipeScreen(vm) { overlay = Overlay.None }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Text("Weekly Meals") },
                    actions = {
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
                    0 -> BrowseScreen(vm, st, toggleFav)
                    1 -> WeekScreen(vm, st, toggleFav)
                    2 -> FavoritesScreen(vm, st, toggleFav) { overlay = Overlay.AddRecipe }
                    else -> ShopScreen(vm, st) { overlay = Overlay.Settings }
                }
            }
        }
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
private fun BrowseScreen(vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit) {
    val ids by vm.browse.collectAsState()
    val all = vm.recipes(st)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("10 ideas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { vm.refreshBrowse() }) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("10 more")
                }
            }
        }
        items(ids, key = { it }) { id ->
            val r = all[id] ?: return@items
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
                        val inWeek = id in st.week
                        Button(onClick = { vm.addToWeek(id) }, enabled = !inWeek) {
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

// ---- My Week -----------------------------------------------------------------------

@Composable
private fun WeekScreen(vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit) {
    val all = vm.recipes(st)
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
                        androidx.compose.foundation.layout.Box {
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
