package com.weeklymeals

import androidx.compose.material3.AlertDialog

import androidx.compose.ui.graphics.Color

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.unit.sp

/**
 * The Shop tab: the shopping list and the King Soopers product for each item on one page.
 * Tap "Have it" to leave an item out, tap the product to change it, then Add to cart.
 */
@Composable
fun ShopScreen(vm: MealsViewModel, st: UserState, listState: LazyListState, openSettings: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val products by vm.products.collectAsState()
    val loading by vm.productsLoading.collectAsState()
    val busy by vm.busy.collectAsState()
    val groups = vm.shopGroups(st)
    // The pantry can cover items too: they go to "Already have" unless you chose to buy them anyway.
    val toBuy = groups.filter { !vm.isHave(st, it) }
    val have = groups.filter { vm.isHave(st, it) }
    val hasStore = st.kroger.store.id.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    var showHave by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }
    val suggestions by vm.suggestions.collectAsState()
    // "Add these to your Kitchen too?" after tapping Add to cart (the items you untick stay out of the Kitchen).
    var kitchenAsk by remember { mutableStateOf<List<CartLine>?>(null) }
    var kitchenOff by remember { mutableStateOf(setOf<String>()) }
    // Items you skipped last time start unticked (and are marked so).
    var kitchenMem by remember { mutableStateOf(setOf<String>()) }
    kitchenAsk?.let { lines ->
        AlertDialog(
            onDismissRequest = { kitchenAsk = null; kitchenOff = emptySet(); kitchenMem = emptySet() },
            title = { Text("Add these to your Kitchen too?") },
            text = {
              Column {
                // Only the list scrolls; the two buttons below stay put.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    lines.forEach { l ->
                        val on = l.term !in kitchenOff
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                kitchenOff = if (on) kitchenOff + l.term else kitchenOff - l.term
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = {
                                kitchenOff = if (on) kitchenOff + l.term else kitchenOff - l.term
                            })
                            Text(l.term.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                            if (l.term in kitchenMem) {
                                Text(
                                    "  skipped last time",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        "Untick anything you won't keep past this week's meals.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                    // Cart only, or cart plus the Kitchen (same icon as the Kitchen button at the top).
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KitchenAskButton("Cart only", false, Modifier.weight(1f), onClick = {
                            kitchenAsk = null; kitchenOff = emptySet(); kitchenMem = emptySet()
                            vm.confirmAddToCart()
                        }) { Text("🛒", style = MaterialTheme.typography.headlineSmall) }
                        KitchenAskButton("Cart + Kitchen", true, Modifier.weight(1f), onClick = {
                            val terms = lines.map { it.term }.filter { it !in kitchenOff }.toSet()
                            vm.rememberKitchenChoices(lines, kitchenOff)
                            kitchenAsk = null; kitchenOff = emptySet(); kitchenMem = emptySet()
                            vm.confirmAddToCart(terms)
                        }) {
                            Text("🛒", style = MaterialTheme.typography.headlineSmall)
                            Text("+", style = MaterialTheme.typography.titleMedium)
                            Icon(PantryIcon, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { kitchenAsk = null; kitchenOff = emptySet(); kitchenMem = emptySet() }) { Text("Cancel") }
            },
        )
    }

    // Search King Soopers as you type (short pause so it doesn't search on every letter).
    LaunchedEffect(newItem) {
        delay(350)
        vm.searchSuggestions(newItem)
    }

    // Look up King Soopers products for anything new on the list.
    LaunchedEffect(groups.map { it.term }.toSet(), hasStore) { vm.loadProducts() }


    val cart = toBuy.mapNotNull { g -> products[g.term]?.takeIf { it.product != null } }
    val total = cart.sumOf { l -> l.product!!.let { (it.promo ?: it.price ?: 0.0) * l.quantity } }
    val saved = cart.sumOf { l ->
        l.product!!.let { p -> if (p.promo != null && p.price != null) (p.price - p.promo) * l.quantity else 0.0 }
    }

    val sections = Logic.AISLES.map { aisle -> aisle to toBuy.filter { it.aisle == aisle } }.filter { it.second.isNotEmpty() }
    fun lineCost(term: String): Double =
        products[term]?.product?.let { (it.promo ?: it.price ?: 0.0) * (products[term]?.quantity ?: 1) } ?: 0.0

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Same margins as Browse and Favorites.
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp),
        ) {
            item(key = "header") {
                // SHOPPING LIST over "24 items", like WEEK 43 over the dates on the Week tab.
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SHOPPING LIST",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${toBuy.size} item${if (toBuy.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 38.sp),
                        )
                        val meals = mealCount(st)
                        val parts = listOfNotNull(
                            if (meals > 0) "$meals meal${if (meals == 1) "" else "s"}" else null,
                            if (st.snacks.isNotEmpty()) "${st.snacks.size} snack${if (st.snacks.size == 1) "" else "s"}" else null,
                            if (st.pickedStaples.isNotEmpty()) "${st.pickedStaples.size} usual" else null,
                            if (st.extraItems.isNotEmpty()) "${st.extraItems.size} added" else null,
                        )
                        Text(
                            if (parts.isEmpty()) "Nothing yet" else parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        ) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Bring back all \"Already have\" items") },
                                onClick = { vm.clearHave(); menu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Open King Soopers cart") },
                                onClick = { openKingSoopersCart(ctx); menu = false },
                            )
                        }
                    }
                }
                val add = { vm.addExtraItem(newItem); newItem = ""; focus.clearFocus() }
                // Slim rounded bar, same as the Search bar on Browse and Favorites.
                CompactSearch(
                    newItem, { newItem = it },
                    Modifier.padding(top = 12.dp),
                    placeholder = "Add an item",
                    icon = Icons.Filled.Add,
                    onDone = { add() },
                )
                // Dropdown of matches while typing.
                if (newItem.trim().length >= 2) {
                    Surface(
                        tonalElevation = 4.dp, shadowElevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Column {
                            Text(
                                "Add \"${newItem.trim()}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().clickable { add() }.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                            suggestions.forEach { p ->
                                HorizontalDivider()
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clickable { vm.addExtraProduct(p); newItem = ""; focus.clearFocus() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(p.size.ifBlank { null }, (p.promo ?: p.price)?.let { Sales.money(it) },
                                            if (p.promo != null) "on sale" else null).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!hasStore) {
                                Text(
                                    "Pick your store in Settings to see King Soopers matches.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                QuickAddChips(vm, st)
                if (!hasStore) {
                    TextButton(onClick = openSettings) {
                        Text("Pick your King Soopers store in Settings to see products and prices")
                    }
                }
                loading?.let { (done, n) ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text("Finding King Soopers products… $done of $n", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { if (n == 0) 0f else done.toFloat() / n },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
                if (groups.isEmpty()) {
                    Text(
                        "Your list is empty. Add meals to your week, pick snacks, or add an item above.",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }

            sections.forEach { (aisle, section) ->
                item(key = "h_$aisle") {
                    // "Produce  3 ............ $9.47"
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            aisle.substringAfter(' '),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Look.Display), fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "  ${section.size}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                        val sub = section.sumOf { lineCost(it.term) }
                        if (sub > 0) {
                            Text(Sales.money(sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(section, key = { "g_" + it.term }) { g ->
                    val status = vm.pantryStatus(st, g)
                    val zone = vm.pantryItem(st, g.term)?.let { zoneOf(it.shelf) }
                    val place = zoneName(zone)
                    val pantryNote = when (status) {
                        MealsViewModel.PantryStatus.EXPIRED -> {
                            val item = vm.pantryItem(st, g.term)
                            if (item != null && Pantry.isSpice(item)) "$place · may be weak" else "$place · check date"
                        }
                        MealsViewModel.PantryStatus.LOW -> "Low in ${place.lowercase()}"
                        MealsViewModel.PantryStatus.COVERED -> "In ${place.lowercase()} · buying anyway"
                        null -> null
                    }
                    // Some in the freezer but not enough: say how much more to buy.
                    val frozen = vm.freezerFor(st, g.term)
                    val freezerNote = frozen?.let { f ->
                        val need = vm.meatNeed(st, g.term, g.items)
                        val more = need - f.amount()
                        if (more <= 0) null
                        else if (f.byCount) "${f.count} in freezer · need ${Logic.fmt(more)} more"
                        else "${Logic.fmt(f.lb)} lb in freezer · need ${Logic.fmt(Math.round(more * 4) / 4.0)} lb more"
                    }
                    ShopRow(
                        vm, g, products[g.term], hasStore, pantryNote, vm.shopPack(st, g), freezerNote,
                        usePantry = if (status == MealsViewModel.PantryStatus.EXPIRED) ({ vm.setUseAnyway(g.term, true) }) else null,
                        showHave = status == null,
                        first = g == section.first(), last = g == section.last(),
                    )
                }
            }

            if (have.isNotEmpty()) {
                item(key = "have_header") {
                    // One card: the header row, then (when open) the items.
                    val shape = if (showHave) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RoundedCornerShape(18.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp).clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { showHave = !showHave }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Already have",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Look.Display), fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "  ${have.size}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showHave) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showHave) {
                    items(have, key = { "have_" + it.term }) { g ->
                        val shape = if (g == have.last()) RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp) else RoundedCornerShape(0.dp)
                        Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val fromPantry = g.term !in st.haveTerms
                                val fromFreezer = fromPantry && vm.freezerCovers(st, g)
                                val zone = if (!fromPantry) null else if (fromFreezer) "freezer" else vm.haveZone(st, g.term)
                                // Same icon as its Kitchen zone, then where it is.
                                if (fromPantry) {
                                    HaveIcon(zone ?: "pantry")
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    g.term.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (fromPantry) null else TextDecoration.LineThrough,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (fromPantry) zoneName(zone) else "You said",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (fromPantry) {
                                    TextButton(onClick = { vm.togglePantryBuy(g.term) }) { Text("Buy anyway") }
                                } else {
                                    TextButton(onClick = { vm.toggleHave(g.term) }) { Text("Need it") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Always-visible bottom bar: the total, then Add to cart.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val count = "${toBuy.size} item${if (toBuy.size == 1) "" else "s"}"
                if (cart.isNotEmpty()) {
                    Text(Sales.money(total), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                }
                val highlight = MaterialTheme.colorScheme.primary
                val warn = MaterialTheme.colorScheme.error
                Text(
                    buildAnnotatedString {
                        append(count)
                        if (saved >= 0.01) {
                            append(" · ")
                            withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append("you save " + Sales.money(saved)) }
                        }
                    },
                    style = if (cart.isEmpty()) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Items with no product, on their own line.
                val missing = toBuy.count { products[it.term]?.let { l -> l.product == null } == true }
                if (missing > 0) {
                    Text(
                        "$missing need${if (missing == 1) "s" else ""} a product",
                        style = MaterialTheme.typography.bodySmall, color = warn,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    if (!vm.signedIn) openSettings()
                    else {
                        val c = vm.kitchenCandidates()
                        val skipped = vm.kitchenSkipped(st, c)
                        // Nothing to ask about, or you skipped every one of these before: straight to the cart.
                        if (c.isEmpty() || skipped.size == c.size) vm.confirmAddToCart()
                        else { kitchenOff = skipped; kitchenMem = skipped; kitchenAsk = c }
                    }
                },
                enabled = !busy && loading == null && (cart.isNotEmpty() || !vm.signedIn),
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
            ) {
                Icon(Icons.Filled.ShoppingCart, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (vm.signedIn) "Add to cart" else "Sign in", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Your most-bought extras as chips. Tap to add one more (its own row); long-press to stop suggesting it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAddChips(vm: MealsViewModel, st: UserState) {
    val chips = vm.quickAdd(st)
    if (chips.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (term, b) ->
            val on = vm.onList(st, b.name)
            val shape = RoundedCornerShape(16.dp)
            // Round pills, lime once it's on the list; long names end in "…".
            Box(
                Modifier
                    .widthIn(max = 200.dp)
                    .height(32.dp)
                    .clip(shape)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                    .combinedClickable(
                        onClick = { vm.toggleQuickAdd(b.name) },
                        onLongClick = { vm.forgetQuickAdd(term, b.name) },
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    // Each tap adds another row; the chip shows how many are on the list.
                    (if (on) (vm.quickCount(st, b.name).let { if (it > 1) "✓ $it× " else "✓ " }) else "+ ") + b.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Colors for the small labels on a Shop item (NEED 2, SALE, LOW IN PANTRY...). */
private enum class Tag { SALE, PANTRY, FREEZER }

/** A small all-caps label, like the STORE / LEFTOVERS tags on the Week tab. */
@Composable
private fun ShopTag(text: String, kind: Tag) {
    val shape = RoundedCornerShape(7.dp)
    val (bg, fg) = when (kind) {
        Tag.SALE -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        Tag.PANTRY -> Color(0xFF3A2C14) to Color(0xFFF0B44C)
        Tag.FREEZER -> Color(0xFF15283A) to Color(0xFF8FC3F2)
    }
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        color = fg, maxLines = 2,
        modifier = Modifier.clip(shape).background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** The − 1 + pill. */
@Composable
private fun Stepper(qty: Int, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clickable(onClick = onLess), contentAlignment = Alignment.Center) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text("$qty", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.size(34.dp).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * One item on its aisle's card: photo, name, product and price on top;
 * labels, Have it and the − 1 + pill underneath. Tap the photo or name to change the product.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShopRow(
    vm: MealsViewModel, g: ShopGroup, line: CartLine?, hasStore: Boolean, pantryNote: String? = null,
    meat: MealsViewModel.MeatPack? = null,
    freezerNote: String? = null,
    usePantry: (() -> Unit)? = null,
    /** False when the Kitchen already has this item: "Have it" would only repeat what the Kitchen says. */
    showHave: Boolean = true,
    /** First and last items of an aisle round the card's corners. */
    first: Boolean = true, last: Boolean = true,
) {
    var editPack by remember { mutableStateOf(false) }
    val p = line?.product
    val pick = { vm.picking.value = g.term }
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    // Store meals say "store meal · wed" after the name instead of being listed as a source.
    val storeSources = g.sources.filter { it.startsWith("Store meal") }
    val sources = g.sources - storeSources.toSet()
    // Labels line up under the name when there's a photo.
    val indent = if (hasStore) 60.dp else 0.dp
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (!first) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (hasStore) {
                    if (line != null && p == null) {
                        // Nothing at your store: an empty dashed-looking slot.
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                                .clickable(onClick = pick),
                        )
                    } else {
                        ProductPhoto(p?.image ?: "", 48.dp, Modifier.clickable(enabled = line != null, onClick = pick))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f).clickable(enabled = line != null, onClick = pick)) {
                    // "Chicken broth  ·  need 1 cup, 5 cans" (wraps rather than cutting off); store meals say which day.
                    val accent = if (storeSources.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g.term.replaceFirstChar { it.uppercase() }) }
                            withStyle(SpanStyle(color = accent)) {
                                append("  ·  ")
                                append(if (storeSources.isEmpty()) "need ${g.need}" else storeSources.joinToString(", ").lowercase())
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (hasStore) {
                        Text(
                            when {
                                line == null -> "Looking up…"
                                p == null -> "Not found at your store"
                                else -> listOf(p.title, p.size).filter { it.isNotBlank() }.joinToString(" · ")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (line != null && p == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Each meal that needs it on its own line.
                    sources.forEach { src ->
                        Text(
                            src,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (line != null && p != null) {
                    // Price for the whole line (packages × quantity), with the regular price struck through on sale.
                    Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                        val now = p.promo ?: p.price
                        if (now != null) {
                            Text(Sales.money(now * line.quantity), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        if (p.promo != null && p.price != null) {
                            Text(
                                Sales.money(p.price * line.quantity),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                    }
                } else if (line != null && hasStore) {
                    OutlinedButton(
                        onClick = pick,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text("Pick one", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                }
            }
            // Meat sold by the pound: which pack, what's left over. Tap to set the real pack weight.
            meat?.let { m ->
                val leftover = when {
                    m.cut != null && m.leftoverPieces > 0 -> "${m.leftoverPieces} left over go to the freezer"
                    m.cut == null && m.leftoverLb > 1.0 -> "${Logic.fmt(Math.round(m.leftoverLb * 4) / 4.0)} lb left over goes to the freezer"
                    m.cut == null && m.leftoverLb >= 0.1 -> "${Logic.fmt(Math.round(m.leftoverLb * 4) / 4.0)} lb left over"
                    else -> null
                }
                val yours = if (m.cut != null) m.perPackYours else m.pack.source == Packs.Source.YOU
                Row(
                    Modifier.padding(start = indent).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { editPack = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (m.packs > 1) "${m.packs} × " else "1 pack · ") + Packs.label(m.pack) +
                                (m.cut?.let { " · ${if (m.perPackYours) "" else "about "}${m.perPack} ${Packs.pieceWord(it, m.perPack)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            listOfNotNull(leftover, if (yours) "your size" else null).joinToString(" · ").ifEmpty { "Estimated size" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Set size", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (editPack) {
                    if (m.cut != null) {
                        PackCountDialog(g.term, m, onSet = { vm.setPackCount(m.key, it); editPack = false }) { editPack = false }
                    } else {
                        PackWeightDialog(g.term, m, onSet = { vm.setPackWeight(m.key, it); editPack = false }) { editPack = false }
                    }
                }
            }
            line?.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = indent))
            }
            // Labels on the left; Have it / Use mine and the − 1 + pill on the right.
            Row(Modifier.padding(start = indent).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (p?.promo != null) ShopTag("Sale", Tag.SALE)
                    if (p?.lowStock == true) ShopTag("Low stock", Tag.PANTRY)
                    pantryNote?.let { ShopTag(it, Tag.PANTRY) }
                    freezerNote?.let { ShopTag(it, Tag.FREEZER) }
                }
                // Past its date: buy more, or say you're fine using what you have.
                if (usePantry != null) {
                    TextButton(onClick = usePantry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Use mine", color = Color(0xFFF0B44C), fontWeight = FontWeight.Bold)
                    }
                }
                if (showHave) {
                    TextButton(onClick = { vm.toggleHave(g.term) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Have it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (line != null && p != null) {
                    Stepper(
                        line.quantity,
                        onLess = {
                            if (line.quantity <= 1 && g.aisle == EXTRAS) vm.removeExtra(g)
                            else vm.setQuantity(g.term, line.quantity - 1)
                        },
                        onMore = { vm.setQuantity(g.term, line.quantity + 1) },
                    )
                }
            }
        }
    }
}

/** Full list of King Soopers products for one item, with a search box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPicker(vm: MealsViewModel, term: String, onClose: () -> Unit) {
    val products by vm.products.collectAsState()
    val line = products[term]
    if (line == null) { onClose(); return }
    var query by remember(term) { mutableStateOf(line.search) }
    BackHandler { onClose() }
    Scaffold(
        modifier = Modifier.swipeBack(onClose),
        topBar = {
            TopAppBar(
                title = { Text("Choose ${line.term}") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    query, { query = it },
                    label = { Text("Search King Soopers") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.researchLine(term, query) }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.researchLine(term, query) }) { Icon(Icons.Filled.Search, "Search") }
            }
            if (line.searching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (line.options.isEmpty()) {
                Text(
                    "Nothing found. Try different words, like \"white rice\" or \"jasmine rice\".",
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(line.options.size) { n ->
                        val p = line.options[n]
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    vm.selectProduct(term, n)
                                    onClose()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = n == line.selected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            ProductPhoto(p.image, 56.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyLarge)
                                val sub = listOfNotNull(
                                    p.size.ifBlank { null },
                                    (p.promo ?: p.price)?.let { String.format(Locale.US, "$%.2f", it) },
                                    if (p.promo != null && p.price != null) "was " + Sales.money(p.price) else null,
                                    if (p.lowStock) "⚠️ Low stock" else null,
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 122.dp))
                    }
                }
            }
        }
    }
}

/** One of the two big buttons on the "Add to your Kitchen too?" pop-up: icons on top, short label under. */
@Composable
private fun KitchenAskButton(
    label: String, highlight: Boolean, modifier: Modifier, onClick: () -> Unit, icons: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { icons() }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Set how much a pack of this meat really weighs (remembered for that product). */
@Composable
private fun PackWeightDialog(term: String, m: MealsViewModel.MeatPack, onSet: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(Logic.fmt(m.pack.lb)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${term.replaceFirstChar { it.uppercase() }} pack size") },
        text = {
            Column {
                Text(
                    "How much does one pack weigh? Check the label after you buy it. The app remembers it for this product.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0).forEach { v ->
                        FilterChip(
                            selected = text.toDoubleOrNull() == v,
                            onClick = { text = Logic.fmt(v) },
                            label = { Text("${Logic.fmt(v)} lb") },
                        )
                    }
                }
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Pounds per pack") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSet(text.toDoubleOrNull()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (m.pack.source == Packs.Source.YOU) TextButton(onClick = { onSet(null) }) { Text("Use estimate") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** For cuts cooked whole: how many pieces come in one pack (remembered for that product). */
@Composable
private fun PackCountDialog(term: String, m: MealsViewModel.MeatPack, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
    val cut = m.cut ?: term
    var n by remember { mutableStateOf(m.perPack) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${term.replaceFirstChar { it.uppercase() }} pack") },
        text = {
            Column {
                Text(
                    "How many ${Packs.pieceWord(cut, 2)} come in one pack? The app remembers it for this product.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..10).forEach { v ->
                        FilterChip(selected = n == v, onClick = { n = v }, label = { Text("$v") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(n) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (m.perPackYours) TextButton(onClick = { onSet(null) }) { Text("Use estimate") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}


/** "Pantry", "Fridge", "Freezer" or "Produce bin". */
internal fun zoneName(zone: String?): String = when (zone) {
    "fridge" -> "Fridge"; "freezer" -> "Freezer"; "produce" -> "Produce bin"; else -> "Pantry"
}
