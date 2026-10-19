package com.weeklymeals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Where the Browse search looks. One at a time. */
object BrowseScope {
    const val APP = "app"
    const val WEB = "web"
    const val STORE = "store"
    val ALL = listOf(APP to "In app", WEB to "Online", STORE to "King Soopers")
}

/** The three "where to search" chips under the Browse search box. */
@Composable
internal fun ScopeChips(scope: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrowseScope.ALL.forEach { (key, label) ->
            FilterChip(selected = scope == key, onClick = { onPick(key) }, label = { Text(label) })
        }
    }
}

/** "$6.99" or, on sale, "$5.99 (was $6.99)". */
private fun priceText(p: Product): String = when {
    p.promo != null && p.price != null && p.promo < p.price -> Sales.money(p.promo) + " (was " + Sales.money(p.price) + ")"
    else -> p.price?.let { Sales.money(it) } ?: ""
}

/**
 * Browse with "King Soopers" selected: with nothing typed, the store meals you added before;
 * with a search, store products. "Add to day" puts one on a day as a meal (never saved as a recipe).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StoreSearch(vm: MealsViewModel, st: UserState, query: String, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val results by vm.storeResults.collectAsState()
    val loading by vm.storeSearching.collectAsState()
    var pickProduct by remember { mutableStateOf<Product?>(null) }
    var pickRecent by remember { mutableStateOf<StoreMeal?>(null) }
    var removing by remember { mutableStateOf<StoreMeal?>(null) }
    val ready = st.kroger.store.id.isNotEmpty() && st.kroger.clientId.isNotBlank()

    if (!ready) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Choose your King Soopers store in Settings to search its products.",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
            TextButton(onClick = onSettings) { Text("Open Settings") }
        }
        return
    }

    val typed = query.trim().length >= 2
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        if (!typed) {
            item(key = "recent_header") {
                Text(
                    if (st.recentStoreMeals.isEmpty()) "Search King Soopers for a frozen pizza, nuggets, anything. Add it to a day without saving a recipe."
                    else "Recent store meals",
                    style = if (st.recentStoreMeals.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            items(st.recentStoreMeals, key = { "recent_" + it.upc }) { m ->
                StoreRow(
                    "🛒 " + m.title, listOfNotNull(m.size.ifBlank { null }, m.price?.let { Sales.money(it) }).joinToString(" · "),
                    onAdd = { pickRecent = m }, onHold = { removing = m },
                )
            }
            if (st.recentStoreMeals.isNotEmpty()) {
                item(key = "recent_hint") {
                    Text(
                        "Hold one to remove it from this list.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        } else if (loading && results.isEmpty()) {
            item(key = "searching") {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Searching King Soopers…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (results.isEmpty()) {
            item(key = "none") {
                Text(
                    "Nothing found at your store.", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        } else {
            item(key = "results_header") {
                Text(
                    "King Soopers · ${results.size}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            items(results, key = { "p_" + it.upc }) { p ->
                StoreRow(p.title, listOfNotNull(p.size.ifBlank { null }, priceText(p).ifBlank { null }).joinToString(" · "), onAdd = { pickProduct = p })
            }
        }
    }

    pickProduct?.let { p ->
        DayPickerDialog(st, p.title, onPick = { d -> vm.addStoreMeal(d, p); pickProduct = null }, onDismiss = { pickProduct = null })
    }
    pickRecent?.let { m ->
        DayPickerDialog(st, m.title, onPick = { d -> vm.addRecentStoreMeal(d, m); pickRecent = null }, onDismiss = { pickRecent = null })
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from recent?") },
            text = { Text(m.title) },
            confirmButton = { TextButton(onClick = { vm.forgetRecentStoreMeal(m.upc); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreRow(title: String, detail: String, onAdd: () -> Unit, onHold: (() -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onAdd, onLongClick = onHold)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onAdd) { Text("Add to day") }
        }
        HorizontalDivider()
    }
}

/** "Which day?": Monday to Sunday, with how many store items each already has. */
@Composable
internal fun DayPickerDialog(st: UserState, title: String, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val today = Logic.todayIndex()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which day?") },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 6.dp)) {
                    Column {
                        Logic.DAY_LONG.forEachIndexed { d, name ->
                            val n = st.storeMeals.count { it.day == d }
                            TextButton(onClick = { onPick(d) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    name + (if (d == today) " (today)" else "") + (if (n > 0) "  ·  $n store item${if (n == 1) "" else "s"}" else ""),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
