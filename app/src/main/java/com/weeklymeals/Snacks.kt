package com.weeklymeals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.unit.dp

data class Snack(val name: String, val emoji: String, val qty: Double = 1.0)

object SnackCatalog {
    val groups: List<Pair<String, List<Snack>>> = listOf(
        "🥔 Chips & crackers" to listOf(
            Snack("Potato chips", "🥔"), Snack("Tortilla chips", "🌽"), Snack("Cheez-It crackers", "🧀"),
            Snack("Goldfish crackers", "🐟"), Snack("Ritz crackers", "🍘"), Snack("Pringles", "🥫"),
            Snack("Pretzels", "🥨"), Snack("Microwave popcorn", "🍿"), Snack("Doritos", "🔺"),
            Snack("Cheetos", "🧡"), Snack("Graham crackers", "🍪"), Snack("Animal crackers", "🦁"),
        ),
        "🍫 Sweet" to listOf(
            Snack("Oreo cookies", "🍪"), Snack("Granola bars", "🥣"), Snack("Fruit snacks", "🍬"),
            Snack("Chocolate bars", "🍫"), Snack("Chocolate chip cookies", "🍪"), Snack("Pop-Tarts", "🥮"),
            Snack("Rice Krispies Treats", "🟨"), Snack("Pudding cups", "🍮"), Snack("Ice cream", "🍨"),
            Snack("Popsicles", "🍭"),
        ),
        "🍎 Fresh" to listOf(
            Snack("Apples", "🍎", 6.0), Snack("Bananas", "🍌", 6.0), Snack("Grapes", "🍇"),
            Snack("Baby carrots", "🥕"), Snack("Strawberries", "🍓"), Snack("Clementines", "🍊"),
            Snack("String cheese", "🧀"), Snack("Yogurt cups", "🥛"), Snack("Hummus", "🫘"),
            Snack("Celery sticks", "🥬"),
        ),
        "🥜 Other" to listOf(
            Snack("Trail mix", "🥜"), Snack("Peanuts", "🥜"), Snack("Beef jerky", "🥩"),
            Snack("Applesauce pouches", "🍏"), Snack("Peanut butter crackers", "🥜"), Snack("Almonds", "🌰"),
            Snack("Cheese and cracker packs", "🧀"), Snack("Raisins", "🍇"),
        ),
        "🥤 Drinks" to listOf(
            Snack("Juice boxes", "🧃"), Snack("Soda", "🥤"), Snack("Sparkling water", "🫧"),
            Snack("Gatorade", "🏃"), Snack("Bottled water", "💧"), Snack("Lemonade", "🍋"),
            Snack("Chocolate milk", "🥛"), Snack("Iced tea", "🧊"),
        ),
    )

    private val byName = groups.flatMap { it.second }.associateBy { it.name }

    fun find(name: String): Snack = byName[name] ?: Snack(name, "⭐")
}

object Staples {
    val DEFAULT = listOf(
        "Milk", "Eggs", "Bread", "Butter", "Bananas", "Coffee", "Cereal", "Shredded cheese",
        "Lunch meat", "Sliced cheese", "Orange juice", "Toilet paper", "Paper towels",
        "Dish soap", "Trash bags",
    )
}

/** Emoji for common usual items. */
fun stapleEmoji(name: String): String {
    val n = name.lowercase()
    return listOf(
        "milk" to "🥛", "egg" to "🥚", "bread" to "🍞", "butter" to "🧈", "banana" to "🍌", "coffee" to "☕",
        "cereal" to "🥣", "cheese" to "🧀", "lunch meat" to "🥪", "juice" to "🧃", "toilet" to "🧻",
        "paper towel" to "🧻", "soap" to "🧼", "trash" to "🗑️", "yogurt" to "🥛", "water" to "💧",
    ).firstOrNull { n.contains(it.first) }?.second ?: "🛒"
}

/**
 * "Quick add" (opened from More… on the Shop tab): usual items and snacks to tap onto the list.
 * Everything picked here goes in the Shop tab's Extras section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(vm: MealsViewModel, st: UserState, onBack: () -> Unit) {
    var addingSnack by remember { mutableStateOf(false) }
    var addingUsual by remember { mutableStateOf(false) }
    var editUsual by remember { mutableStateOf(false) }
    val reAdd = st.lastSnacks - st.snacks
    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { Text("Quick add") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(pad),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tap to add to this week's list. Everything here goes in Extras on the Shop tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Usual items
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { GroupHeader("🛒 Usual items") }
                    TextButton(onClick = { editUsual = !editUsual }) { Text(if (editUsual) "Done" else "Edit") }
                    TextButton(onClick = { addingUsual = true }) { Text("+ Add") }
                }
            }
            items(st.staples, key = { "u_$it" }) { name ->
                SnackTile(
                    Snack(name, stapleEmoji(name)), name in st.pickedStaples,
                    onToggle = { vm.toggleStaple(name) },
                    onRemove = if (editUsual) ({ vm.removeStaple(name) }) else null,
                )
            }
            // Snacks
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { GroupHeader("🍿 Snacks") }
                    TextButton(onClick = { addingSnack = true }) { Text("+ Add your own") }
                }
            }
            if (reAdd.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { vm.addSnacks(reAdd) }) { Text("↺ Add last week's snacks (${reAdd.size})") }
                }
            }
            if (st.customSnacks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("⭐ Mine") }
                items(st.customSnacks, key = { "c_$it" }) { name ->
                    SnackTile(SnackCatalog.find(name), name in st.snacks, onToggle = { vm.toggleSnack(name) }, onRemove = { vm.removeCustomSnack(name) })
                }
            }
            SnackCatalog.groups.forEach { (title, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_$title") { GroupHeader(title) }
                items(list, key = { it.name }) { sn ->
                    SnackTile(sn, sn.name in st.snacks, onToggle = { vm.toggleSnack(sn.name) }, onRemove = null)
                }
            }
        }
    }
    if (addingSnack || addingUsual) {
        var text by remember { mutableStateOf("") }
        val done = {
            if (addingSnack) vm.addCustomSnack(text) else {
                vm.addStaple(text)
                val n = text.trim().replaceFirstChar { it.uppercase() }
                if (n.isNotEmpty() && n !in st.pickedStaples) vm.toggleStaple(n)
            }
            addingSnack = false; addingUsual = false
        }
        AlertDialog(
            onDismissRequest = { addingSnack = false; addingUsual = false },
            title = { Text(if (addingSnack) "Add a snack" else "Add a usual item") },
            text = {
                OutlinedTextField(
                    text, { text = it }, singleLine = true,
                    label = { Text(if (addingSnack) "e.g. Takis" else "e.g. Sparkling water") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done() }),
                )
            },
            confirmButton = { TextButton(onClick = { done() }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addingSnack = false; addingUsual = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SnackTile(s: Snack, picked: Boolean, onToggle: () -> Unit, onRemove: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (picked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth().height(92.dp).clickable(onClick = onToggle),
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(6.dp).align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.emoji, style = MaterialTheme.typography.headlineSmall)
                Text(
                    s.name, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (picked) {
                Text("✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopStart).size(28.dp)) {
                    Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
