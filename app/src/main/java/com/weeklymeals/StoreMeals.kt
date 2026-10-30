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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
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
    const val DESSERT = "dessert"
    const val SIDE = "side"
    val ALL = listOf(APP to "Meals", SIDE to "Sides", DESSERT to "Desserts", WEB to "Online recipes", STORE to "King Soopers")
    fun label(key: String) = ALL.firstOrNull { it.first == key }?.second ?: "Meals"
    /** The shorter name shown on the button in the search bar. */
    fun short(key: String) = if (key == WEB) "Online" else label(key)
    fun hint(key: String) = when (key) {
        WEB -> "Search online recipes"
        STORE -> "Search King Soopers"
        DESSERT -> "Search desserts"
        SIDE -> "Search sides"
        else -> "Search meals"
    }
}

/** The "where to search" button at the right end of the Browse search bar, with a short menu. */
@Composable
internal fun ScopeMenu(scope: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { open = true }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                BrowseScope.short(scope), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, maxLines = 1,
            )
            Icon(Icons.Filled.ArrowDropDown, "Where to search", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BrowseScope.ALL.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text((if (key == scope) "✓  " else "     ") + label) },
                    onClick = { open = false; onPick(key) },
                )
            }
        }
    }
}

/** A small product photo from King Soopers (a soft square while it loads, or if there's none). */
@Composable
fun ProductPhoto(url: String, size: androidx.compose.ui.unit.Dp = 48.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.size(size).clip(shape).background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            coil.compose.AsyncImage(
                model = url, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        } else {
            Text("🛒", style = MaterialTheme.typography.titleMedium)
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
                    m.title, listOfNotNull(m.size.ifBlank { null }, m.price?.let { Sales.money(it) }).joinToString(" · "),
                    image = m.image, onAdd = { pickRecent = m }, onHold = { removing = m },
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
                StoreRow(p.title, listOfNotNull(p.size.ifBlank { null }, priceText(p).ifBlank { null }).joinToString(" · "), image = p.image, onAdd = { pickProduct = p })
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
        AppDialog(
            onDismiss = { removing = null },
            title = "Remove from recent?",
            actions = listOf(
                DialogAction("Remove", ActionKind.Danger) { vm.forgetRecentStoreMeal(m.upc); removing = null },
                DialogAction("Keep it") { removing = null },
            ),
        ) {
            Text(m.title)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreRow(title: String, detail: String, image: String = "", onAdd: () -> Unit, onHold: (() -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onAdd, onLongClick = onHold)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductPhoto(image, 52.dp)
            Spacer(Modifier.width(10.dp))
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
internal fun DayPickerDialog(
    st: UserState, title: String, onPick: (Int) -> Unit, onDismiss: () -> Unit,
    /** What's on each day already (e.g. that night's dinner); null shows the store item count. */
    detail: ((Int) -> String?)? = null,
    /** Days you can't pick show greyed out. */
    enabled: (Int) -> Boolean = { true },
) {
    val today = Logic.todayIndex()
    val plan = Logic.dayPlan(st)
    AppDialog(
        onDismiss = onDismiss,
        eyebrow = "Add $title",
        title = "Which day?",
        actions = listOf(DialogAction("Cancel", onClick = onDismiss)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Logic.dayOrder(st.weekStart).forEach { d ->
                val n = st.storeMeals.count { it.day == d }
                val sub = when {
                    detail != null -> detail(d)
                    n > 0 -> "$n store item${if (n == 1) "" else "s"}"
                    else -> null
                } ?: "Nothing planned"
                val on = enabled(d)
                val dinner = plan.slots[d]
                Row(
                    Modifier.fillMaxWidth().clip(Radius.Card).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(enabled = on) { onPick(d) }
                        .alpha(if (on) 1f else 0.4f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The day in a dot: that night's dinner color, an outline if nothing's planned.
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .then(
                                if (dinner != null) Modifier.background(Look.color(dinner))
                                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Logic.DAY_SHORT[d].uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Look.Display), fontWeight = FontWeight.ExtraBold,
                            color = if (dinner != null) Look.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Logic.DAY_LONG[d] + if (d == today) " · today" else "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
