package com.weeklymeals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** What the editor hands back: name, emoji, serves, ingredient lines and step lines. */
internal data class RecipeDraft(val name: String, val emoji: String, val serves: Int, val ingredients: String, val steps: String)

/**
 * Full-screen editor for a meal or a side: name, emoji, serves (meals only), ingredients and steps,
 * one per line. [onSave] returns false when something's missing. [onReset] (built-in recipes you've
 * changed) puts the original back.
 */
@Composable
internal fun RecipeEditor(
    heading: String,
    start: RecipeDraft,
    showServes: Boolean,
    note: String? = null,
    onReset: (() -> Unit)? = null,
    onSave: (RecipeDraft) -> Boolean,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(start.name) }
    var emoji by remember { mutableStateOf(start.emoji) }
    var serves by remember { mutableStateOf(start.serves.toString()) }
    var ings by remember { mutableStateOf(start.ingredients) }
    var steps by remember { mutableStateOf(start.steps) }
    var error by remember { mutableStateOf<String?>(null) }
    var askReset by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close without saving") }
                    Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Button(onClick = {
                        val draft = RecipeDraft(name, emoji, serves.toIntOrNull() ?: start.serves, ings, steps)
                        if (draft == start) onClose()
                        else if (onSave(draft)) onClose()
                        else error = "Needs a name, at least one ingredient and one step"
                    }) { Text("Save") }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EmojiPicker(emoji, { emoji = it }, Modifier.weight(1f))
                        if (showServes) OutlinedTextField(
                            serves, { serves = it.filter(Char::isDigit) }, label = { Text("Serves") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        ings, { ings = it }, label = { Text("Ingredients (one per line)") },
                        minLines = 6, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        steps, { steps = it }, label = { Text("Steps (one per line)") },
                        minLines = 6, modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (onReset != null) {
                        OutlinedButton(onClick = { askReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset to original") }
                    }
                }
            }
        }
        if (askReset && onReset != null) {
            AppDialog(
                onDismiss = { askReset = false },
                title = "Reset to original?",
                actions = listOf(
                    DialogAction("Reset", ActionKind.Danger) { askReset = false; onReset(); onClose() },
                    DialogAction("Cancel") { askReset = false },
                ),
            ) { Text("Your changes to ${plainLabel(start.name)} will be lost.") }
        }
    }
}

/** A meal's lines for the editor. */
internal fun draftOf(r: Recipe) = RecipeDraft(
    r.name, r.emoji, r.base,
    r.ingredients.joinToString("\n") { Logic.ingLine(it) },
    r.steps.joinToString("\n"),
)

/** A side's lines for the editor (sides are written for 4). */
internal fun draftOf(sd: Side): RecipeDraft {
    val hasEmoji = sd.label.firstOrNull()?.isLetterOrDigit() == false && ' ' in sd.label
    return RecipeDraft(
        if (hasEmoji) sd.label.substringAfter(" ") else sd.label,
        if (hasEmoji) sd.label.substringBefore(" ") else "🍽️",
        4,
        sd.ingredients.joinToString("\n") { Logic.ingLine(it) },
        sd.steps.joinToString("\n"),
    )
}

/**
 * "Buy at King Soopers instead": search for the product you buy in place of making [sideName]
 * (frozen garlic bread). Tapping one saves it on the side.
 */
@Composable
internal fun BuyInsteadPicker(vm: MealsViewModel, st: UserState, sideKey: String, sideName: String, onClose: () -> Unit) {
    var query by remember(sideKey) { mutableStateOf(sideName) }
    val products by vm.suggestions.collectAsState()
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        searching = true
        delay(400)
        vm.searchSuggestions(query)
        delay(1200)
        searching = false
    }
    val close = { vm.suggestions.value = emptyList(); onClose() }
    AppDialog(
        onDismiss = close,
        eyebrow = "Buy instead of making",
        title = sideName,
        centered = true,
        actions = listOf(DialogAction("Cancel", onClick = close)),
    ) {
        Column {
            Text(
                "Pick what you buy at King Soopers. The recipe stays saved, and your list gets this product instead of the ingredients.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            CompactSearch(query, { query = it }, placeholder = "Search King Soopers", icon = Icons.Filled.Search)
            Spacer(Modifier.size(6.dp))
            when {
                st.kroger.store.id.isEmpty() -> Text("Pick your store in Settings to see products", style = MaterialTheme.typography.bodyMedium)
                products.isEmpty() && searching -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodySmall)
                }
                products.isEmpty() -> Text("No products found. Try other words, like \"frozen $sideName\".", style = MaterialTheme.typography.bodyMedium)
                else -> products.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth().clickable { vm.setSideBuy(sideKey, p); close() }.padding(vertical = 8.dp),
                    ) {
                        Text("🛒 ${p.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                p.size.ifBlank { null }, (p.promo ?: p.price)?.let { Sales.money(it) },
                                if (p.promo != null) "on sale" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Under a side's name: "🛒 You buy … at King Soopers" with a button to make it again. */
@Composable
internal fun BoughtNote(title: String, onMake: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer, shape = Radius.Card,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🛒 You buy this", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            TextButton(onClick = onMake) { Text("Make it instead") }
        }
    }
}
