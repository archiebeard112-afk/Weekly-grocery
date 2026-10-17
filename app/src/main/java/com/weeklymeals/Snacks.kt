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

/** The Snacks tab: tap snacks to put them on this week's list. */
@Composable
fun SnacksScreen(vm: MealsViewModel, st: UserState) {
    var adding by remember { mutableStateOf(false) }
    val reAdd = st.lastSnacks - st.snacks
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Snacks", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (st.snacks.isEmpty()) "Tap to add to this week's list"
                        else "${st.snacks.size} on this week's list",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add your own")
                }
            }
        }
        if (reAdd.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = { vm.addSnacks(reAdd) }) {
                    Text("↺ Add last week's snacks (${reAdd.size})")
                }
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
            items(list, key = { it.name }) { s ->
                SnackTile(s, s.name in st.snacks, onToggle = { vm.toggleSnack(s.name) }, onRemove = null)
            }
        }
    }
    if (adding) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add a snack") },
            text = {
                OutlinedTextField(
                    text, { text = it }, singleLine = true, label = { Text("e.g. Takis") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addCustomSnack(text); adding = false }),
                )
            },
            confirmButton = { TextButton(onClick = { vm.addCustomSnack(text); adding = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
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

/**
 * Pop-up before the King Soopers review: "Anything else this week?"
 * Shows your usual items (last week's picks pre-checked) and lets you add or remove items.
 */
@Composable
fun StaplesDialog(vm: MealsViewModel, st: UserState, onDismiss: () -> Unit, onContinue: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(st.lastStaples.intersect(st.staples.toSet())) }
    var newItem by remember { mutableStateOf("") }
    val addNew = {
        val n = newItem.trim()
        if (n.isNotEmpty()) {
            vm.addStaple(n)
            picked = picked + n
            newItem = ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anything else this week?") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Your usual items. Tap ✕ to take one off the list for good.", style = MaterialTheme.typography.bodySmall)
                st.staples.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (item in picked) picked - item else picked + item },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item in picked, onCheckedChange = { c -> picked = if (c) picked + item else picked - item })
                        Text(item, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeStaple(item); picked = picked - item }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Remove $item", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(
                        newItem, { newItem = it }, singleLine = true,
                        label = { Text("Add another item") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addNew() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { addNew() }) { Icon(Icons.Filled.Add, "Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val list = st.staples.filter { it in picked } + picked.filter { it !in st.staples }
                onContinue(list)
            }) { Text(if (picked.isEmpty()) "Skip" else "Continue (${picked.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
