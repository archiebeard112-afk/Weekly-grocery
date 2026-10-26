package com.weeklymeals

import androidx.compose.material.icons.filled.Check

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    // A side you held down on: choose to remove it from this dish, hide it, or delete it.
    var held by remember(id) { mutableStateOf<Side?>(null) }
    // Edit mode: each side shows a ✕ to remove, hide or delete it.
    var editing by remember(id) { mutableStateOf(false) }
    // A side opened for a closer look before adding it.
    var preview by remember(id) { mutableStateOf<Side?>(null) }
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

    // One row per side: tap to preview, ✓ to add right away; in Edit mode, ✕ to remove.
    @Composable
    fun item(sd: Side) = SideRow(
        sd.label, editing = editing,
        onHold = { held = sd },
        onQuick = { pick(sd.key) },
        onEdit = { held = sd },
    ) { if (editing) held = sd else preview = sd }

    preview?.let { sd ->
        val fav = vm.isFavSide(st, sd.key)
        SidePreview(
            st, sd, onUse = { size -> vm.chooseSide(id, sd.key, size); close() }, onBack = { preview = null },
            // Sides you added are always in Favorites; built-in ones get a ♥.
            fav = if (vm.isBuiltInSide(sd.key)) fav else null,
            onFav = { if (fav) vm.removeFavSide(sd.key) else vm.addFavSide(sd.key) },
        )
        return
    }

    AlertDialog(
        onDismissRequest = close,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pick a side", modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit") }
            }
        },
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
                    mine.distinctBy { it.key }.forEach { item(it) }

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
                    Note(if (editing) "Tap ✕ to remove, hide or delete a side" else "Tap a side to see it first, or ✓ to add it")
                    recommended.forEach { item(it) }
                    if (!editing) SideRow("No side", muted = true) { pick(null) }
                    if (!showMore) {
                        TextButton(onClick = { showMore = true }) { Text("More sides") }
                    } else {
                        GroupLabel("More sides")
                        val shownKeys = recommended.map { it.key }.toSet()
                        vm.allSides(st).filter { it.key !in shownKeys }.forEach { item(it) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )

    held?.let { sd ->
        val name = sd.label.substringAfter(" ")
        val recommended = vm.sideOptions(st, r).any { it.key == sd.key }
        val builtIn = vm.isBuiltInSide(sd.key)
        AlertDialog(
            onDismissRequest = { held = null },
            title = { Text(sd.label) },
            text = {
                Column {
                    if (recommended) {
                        TextButton(onClick = { vm.removeSideFromDish(id, sd.key); held = null }) {
                            Text("Remove from ${r.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Note("Takes it off this dish's Recommended list. It stays under More sides.")
                    }
                    if (builtIn) {
                        TextButton(onClick = { vm.hideSide(sd.key); held = null }) {
                            Text("Hide everywhere", color = MaterialTheme.colorScheme.error)
                        }
                        Note("Hides $name for every dish. Bring it back in Settings → Hidden recipes & sides.")
                    } else {
                        TextButton(onClick = { vm.deleteSide(sd.key); held = null }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                        Note("Removes $name for good.")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { held = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideRow(
    label: String, muted: Boolean = false, editing: Boolean = false,
    onHold: (() -> Unit)? = null, onQuick: (() -> Unit)? = null, onEdit: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onHold).padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 11.dp),
        )
        when {
            editing && onEdit != null -> IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = MaterialTheme.colorScheme.error)
            }
            onQuick != null -> IconButton(onClick = onQuick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Check, "Add this side", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    HorizontalDivider()
}

/** Half / Normal / Double: how much of a side to make (Logic.SIDE_HALF / NORMAL / DOUBLE). */
@Composable
internal fun SideSizeChoice(size: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Logic.SIDE_HALF to "Half", Logic.SIDE_NORMAL to "Normal", Logic.SIDE_DOUBLE to "Double").forEach { (key, label) ->
            FilterChip(selected = size == key, onClick = { onPick(key) }, label = { Text(label) })
        }
    }
}

/** How many a side at [size] serves, e.g. "1.5" for half with 3 servings. */
internal fun sideServesText(serv: Int, size: Int?): String = Logic.fmt(Logic.sideServ(serv, size) / 2.0)

/**
 * A side's ingredients (for your servings), steps, calories and reviews before you add it.
 * [onUse] null = just looking (from Favorites); otherwise it gets the Half / Normal / Double you picked.
 * [fav] non-null shows a ♥ to keep it in Favorites.
 */
@Composable
internal fun SidePreview(
    st: UserState, sd: Side, onUse: ((Int) -> Unit)?, onBack: () -> Unit,
    fav: Boolean? = null, onFav: () -> Unit = {},
) {
    var size by remember(sd.key) { mutableIntStateOf(Logic.SIDE_NORMAL) }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sd.label, modifier = Modifier.weight(1f))
                if (fav != null) {
                    IconButton(onClick = onFav) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (fav) "Remove from favorites" else "Add to favorites",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Nutrition.side(sd)?.let {
                    Text(
                        "≈ %,d cal per serving".format(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sd.rating?.let { RatingLine(it, sd.ratingCount, sd.countLabel, 14.dp) }
                if (isStoreSide(sd)) {
                    Note("Store-bought from King Soopers. Nothing to cook.")
                } else {
                    if (onUse != null) SideSizeChoice(size, { size = it }, Modifier.padding(top = 8.dp))
                    GroupLabel("Ingredients (for ${sideServesText(st.servings, size)})")
                    sd.ingredients.forEach {
                        Text("• " + Logic.ingText(it, Logic.sideServ(st.servings, size), Logic.SIDE_BASE), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (sd.steps.isNotEmpty()) {
                        GroupLabel("Steps")
                        sd.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    ReviewsSection(sd.key, sd.rating, sd.ratingCount, sd.countLabel, sd.reviews)
                }
            }
        },
        confirmButton = { if (onUse != null) TextButton(onClick = { onUse(size) }) { Text("Use as side") } },
        dismissButton = { TextButton(onClick = onBack) { Text(if (onUse != null) "Back" else "Close") } },
    )
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
