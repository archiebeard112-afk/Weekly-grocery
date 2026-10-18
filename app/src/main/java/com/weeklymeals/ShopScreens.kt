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

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Same margins as Browse and Favorites.
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Shopping list", style = MaterialTheme.typography.titleLarge)
                    val meals = st.week.size
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
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.MoreVert, "More") }
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
                    Modifier.padding(top = 6.dp),
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

            Logic.AISLES.forEach { aisle ->
                val section = toBuy.filter { it.aisle == aisle }
                if (section.isNotEmpty()) {
                    item(key = "h_$aisle") {
                        Text(
                            aisle.uppercase(), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                        )
                    }
                    items(section, key = { "g_" + it.term }) { g ->
                        val inFridge = st.pantry.any { it.term == g.term && it.shelf == "fridge" }
                        val status = vm.pantryStatus(st, g)
                        val pantryNote = when (status) {
                            MealsViewModel.PantryStatus.EXPIRED -> {
                                val item = vm.pantryItem(st, g.term)
                                val place = if (inFridge) "fridge" else "pantry"
                                val date = if (item?.dateKind == "sell") "sell-by" else "best-by"
                                if (item != null && Pantry.isSpice(item)) "⚠ Pantry jar is past its $date and may be weak · get more"
                                else "⚠ The one in your $place is past its $date · get more"
                            }
                            MealsViewModel.PantryStatus.LOW -> if (inFridge) "🥛 Low in fridge" else "🫙 Low in pantry"
                            MealsViewModel.PantryStatus.COVERED -> if (inFridge) "🥛 In fridge · buying anyway" else "🫙 In pantry · buying anyway"
                            null -> null
                        }
                        // Some in the freezer but not enough: say how much more to buy.
                        val frozen = vm.freezerFor(st, g.term)
                        val freezerNote = frozen?.let { f ->
                            val need = vm.meatNeed(st, g.term, g.items)
                            val more = need - f.amount()
                            if (more <= 0) null
                            else if (f.byCount) "🧊 ${f.count} in freezer · need ${Logic.fmt(more)} more"
                            else "🧊 ${Logic.fmt(f.lb)} lb in freezer · need ${Logic.fmt(Math.round(more * 4) / 4.0)} lb more"
                        }
                        ShopRow(
                            vm, g, products[g.term], hasStore, pantryNote, vm.shopPack(st, g), freezerNote,
                            usePantry = if (status == MealsViewModel.PantryStatus.EXPIRED) ({ vm.setUseAnyway(g.term, true) }) else null,
                        )
                    }
                }
            }

            if (have.isNotEmpty()) {
                item(key = "have_header") {
                    Row(
                        Modifier.fillMaxWidth().clickable { showHave = !showHave }.padding(top = 20.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "✓ ALREADY HAVE (${have.size})", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                        Text(if (showHave) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showHave) {
                    items(have, key = { "have_" + it.term }) { g ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val fromPantry = g.term !in st.haveTerms
                            val fromFreezer = fromPantry && vm.freezerCovers(st, g)
                            val fromFridge = fromPantry && st.pantry.any { it.term == g.term && it.shelf == "fridge" }
                            Text(
                                g.term.replaceFirstChar { it.uppercase() } +
                                    if (fromFreezer) "  · 🧊 in freezer" else if (fromFridge) "  · 🥛 in fridge" else if (fromPantry) "  · 🫙 in pantry" else "",
                                textDecoration = if (fromPantry) null else TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1f),
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

        // Always-visible bottom bar: count, total, Add to cart.
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${toBuy.size} item${if (toBuy.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                    if (cart.isNotEmpty()) {
                        val highlight = MaterialTheme.colorScheme.primary
                        Text(
                            buildAnnotatedString {
                                append(Sales.money(total))
                                if (saved >= 0.01) {
                                    append(" · ")
                                    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
                                        append("You save " + Sales.money(saved))
                                    }
                                }
                                if (cart.size < toBuy.size) append(" · ${toBuy.size - cart.size} without a product")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = { if (!vm.signedIn) openSettings() else vm.confirmAddToCart() },
                    enabled = !busy && loading == null && (cart.isNotEmpty() || !vm.signedIn),
                ) { Text(if (vm.signedIn) "Add to cart" else "Sign in") }
            }
        }
    }
}

/** Your most-bought extras as chips. Tap to add or remove; long-press to stop suggesting it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAddChips(vm: MealsViewModel, st: UserState) {
    val chips = vm.quickAdd(st)
    if (chips.isEmpty()) return
    // Same size and spacing as the filter chips on Browse.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (term, b) ->
            val on = vm.onList(st, b.name)
            val emoji = SnackCatalog.find(b.name).emoji.takeIf { it != "⭐" } ?: stapleEmoji(b.name)
            // 32dp tall like a filter chip; long names end in "…".
            Box(
                Modifier
                    .widthIn(max = 200.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { vm.toggleQuickAdd(b.name) },
                        onLongClick = { vm.forgetQuickAdd(term, b.name) },
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    (if (on) "✓ " else "") + "$emoji ${b.name}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ShopRow(
    vm: MealsViewModel, g: ShopGroup, line: CartLine?, hasStore: Boolean, pantryNote: String? = null,
    meat: MealsViewModel.MeatPack? = null,
    freezerNote: String? = null,
    usePantry: (() -> Unit)? = null,
) {
    var editPack by remember { mutableStateOf(false) }
    val highlight = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g.term.replaceFirstChar { it.uppercase() }) }
                    withStyle(SpanStyle(color = highlight)) { append("  ·  need ${g.need}") }
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { vm.toggleHave(g.term) }) { Text("Have it") }
        }
        pantryNote?.let {
            if (usePantry != null) {
                // Past its date: buy more, or say you're fine using what you have.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = usePantry) { Text("Use pantry") }
                }
            } else {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFBA7517))
            }
        }
        // Meat sold by the pound: which pack, what's left over. Tap to set the real pack weight.
        freezerNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF85B7EB))
        }
        meat?.let { m ->
            val leftover = when {
                m.cut != null && m.leftoverPieces > 0 -> " · ${m.leftoverPieces} left over → freezer"
                m.cut == null && m.leftoverLb > 1.0 -> " · ${Logic.fmt(Math.round(m.leftoverLb * 4) / 4.0)} lb left over → freezer"
                m.cut == null && m.leftoverLb >= 0.1 -> " · ${Logic.fmt(Math.round(m.leftoverLb * 4) / 4.0)} lb left over"
                else -> ""
            }
            val yours = if (m.cut != null) m.perPackYours else m.pack.source == Packs.Source.YOU
            Text(
                "🥩 " + (if (m.packs > 1) "${m.packs} × " else "") + Packs.label(m.pack) +
                    (m.cut?.let { " · ${if (m.perPackYours) "" else "about "}${m.perPack} ${Packs.pieceWord(it, m.perPack)}" } ?: "") +
                    leftover + " · " + (if (yours) "your size" else "tap to set"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { editPack = true }.padding(vertical = 2.dp),
            )
            if (editPack) {
                if (m.cut != null) {
                    PackCountDialog(g.term, m, onSet = { vm.setPackCount(m.key, it); editPack = false }) { editPack = false }
                } else {
                    PackWeightDialog(g.term, m, onSet = { vm.setPackWeight(m.key, it); editPack = false }) { editPack = false }
                }
            }
        }
        if (g.sources.isNotEmpty()) {
            Text(
                g.sources.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        line?.note?.let {
            Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (hasStore) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val p = line?.product
                Text(
                    when {
                        line == null -> "Looking up…"
                        p == null -> "Nothing available at your store. Tap to search ›"
                        else -> productLine(p) + "  ›"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (p == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable(enabled = line != null) { vm.picking.value = g.term }
                        .padding(vertical = 6.dp),
                )
                if (line != null && p != null) {
                    OutlinedButton(
                        onClick = {
                            if (line.quantity <= 1 && g.aisle == EXTRAS) vm.removeExtra(g)
                            else vm.setQuantity(g.term, line.quantity - 1)
                        },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp),
                    ) { Text("−") }
                    Text("${line.quantity}", modifier = Modifier.padding(horizontal = 10.dp))
                    OutlinedButton(
                        onClick = { vm.setQuantity(g.term, line.quantity + 1) },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp),
                    ) { Text("+") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

/** "Kroger Yellow Onions · 3 lb · $2.99 (was $3.49) · ⚠️ Low stock" */
private fun productLine(p: Product): String = buildString {
    append(p.title)
    if (p.size.isNotBlank()) append(" · ").append(p.size)
    val now = p.promo ?: p.price
    if (now != null) append(" · ").append(Sales.money(now))
    if (p.promo != null && p.price != null) append(" (was ").append(Sales.money(p.price)).append(")")
    if (p.lowStock) append(" · ⚠️ Low stock")
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
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
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
