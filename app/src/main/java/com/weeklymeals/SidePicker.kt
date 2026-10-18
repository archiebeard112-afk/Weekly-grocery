package com.weeklymeals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * "Pick a side": recommended sides first, More sides for everything else, and one search
 * that finds your sides, online side recipes and King Soopers products.
 */
@Composable
internal fun SidePicker(vm: MealsViewModel, st: UserState, r: Recipe, onOnlineSide: (String) -> Unit) {
    val id = r.id
    var query by remember(id) { mutableStateOf("") }
    var showMore by remember(id) { mutableStateOf(false) }
    val products by vm.suggestions.collectAsState()
    val web by vm.sideWeb.collectAsState()
    LaunchedEffect(query) {
        delay(450)
        vm.searchSuggestions(query)
        vm.searchSideWeb(query)
    }
    val close = { vm.sidePicker.value = null; vm.suggestions.value = emptyList() }
    val pick: (String?) -> Unit = { key -> vm.chooseSide(id, key); close() }
    val q = query.trim().lowercase()

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Pick a side") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("${r.emoji} ${r.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompactSearch(query, { query = it }, Modifier.padding(top = 8.dp), placeholder = "Find a side", icon = Icons.Filled.Search)

                if (q.length >= 2) {
                    // 1) Your sides
                    val mine = vm.allSides(st).filter { it.label.lowercase().contains(q) } +
                        vm.sideOptions(st, r).filter { isStoreSide(it) && it.label.lowercase().contains(q) }
                    GroupLabel("Your sides")
                    if (mine.isEmpty()) Note("None match")
                    mine.distinctBy { it.key }.forEach { SideRow(it.label) { pick(it.key) } }

                    // 2) Online side recipes
                    val source = RecipeSources.effective(st)
                    if (source != RecipeSources.OFF && q.length >= 3) {
                        GroupLabel("Online · " + RecipeSources.label(source))
                        when {
                            source == RecipeSources.SPOONACULAR && st.spoonKey.isBlank() -> Note("Add your Spoonacular key in Settings")
                            web.loading || web.query != q || web.source != source -> Row(
                                Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Searching…", style = MaterialTheme.typography.bodySmall)
                            }
                            web.error != null -> Note(web.error ?: "")
                            web.results.isEmpty() -> Note("No online recipes found")
                            else -> RecipeSources.sort(web.results, st.webSort).forEach { w ->
                                Column(
                                    Modifier.fillMaxWidth().clickable { close(); onOnlineSide(w.id) }.padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        "${Importer.emojiFor(w.title)} ${w.title}",
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    w.rating?.let { RatingLine(it, w.ratingCount, w.countLabel, 12.dp) }
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    // 3) King Soopers products
                    GroupLabel("King Soopers")
                    when {
                        st.kroger.store.id.isEmpty() -> Note("Pick your store in Settings to see products")
                        products.isEmpty() -> Note("No products yet")
                        else -> products.forEach { p ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.addToWeekWithProduct(id, p); close() }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text("🛒 ${p.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                } else {
                    // Recommended for this dish, then More sides.
                    Spacer(Modifier.height(6.dp))
                    val recommended = vm.sideOptions(st, r)
                    if (recommended.isNotEmpty()) GroupLabel("Recommended")
                    recommended.forEach { SideRow(it.label) { pick(it.key) } }
                    SideRow("No side", muted = true) { pick(null) }
                    if (!showMore) {
                        TextButton(onClick = { showMore = true }) { Text("More sides") }
                    } else {
                        GroupLabel("More sides")
                        val shownKeys = recommended.map { it.key }.toSet()
                        vm.allSides(st).filter { it.key !in shownKeys }.forEach { SideRow(it.label) { pick(it.key) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@Composable
private fun SideRow(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
    )
    HorizontalDivider()
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
