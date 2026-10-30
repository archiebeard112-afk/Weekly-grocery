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
