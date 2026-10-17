package com.weeklymeals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun ShopScreen(vm: MealsViewModel, st: UserState, openSettings: () -> Unit) {
    val items = vm.shopItems(st)
    val busy by vm.busy.collectAsState()
    var askStaples by remember { mutableStateOf(false) }
    val leftCount = items.count { it.key !in st.checked }
    if (askStaples) {
        StaplesDialog(
            vm, st,
            onDismiss = { askStaples = false },
            onContinue = { extras ->
                askStaples = false
                if (leftCount == 0 && extras.isEmpty()) vm.message.value = "Nothing to add"
                else vm.startCartReview(extras)
            },
        )
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Shopping list", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    if (!vm.signedIn) openSettings() else askStaples = true
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !vm.signedIn -> "Sign in to King Soopers (Settings)"
                        leftCount == 0 -> "Add usual items to King Soopers cart"
                        else -> "Add $leftCount items to King Soopers cart"
                    }
                )
            }
            if (items.isEmpty()) {
                Text(
                    "Your list is empty. Add recipes to your week or pick snacks.",
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                TextButton(onClick = { vm.clearChecked() }) { Text("Uncheck all") }
            }
        }
        Logic.AISLES.forEach { aisle ->
            val group = items.filter { it.aisle == aisle }
            if (group.isNotEmpty()) {
                item(key = "h_$aisle") {
                    Text(
                        aisle, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(group, key = { it.key }) { it0 ->
                    val done = it0.key in st.checked
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleChecked(it0.key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = done, onCheckedChange = { vm.toggleChecked(it0.key) })
                        Text(
                            Logic.itemLabel(it0),
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            color = if (done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartReviewScreen(vm: MealsViewModel, review: CartReview) {
    val busy by vm.busy.collectAsState()
    var picking by remember { mutableStateOf<Int?>(null) }
    val pickIdx = picking
    if (pickIdx != null && pickIdx in review.lines.indices) {
        ProductPicker(vm, pickIdx, review.lines[pickIdx]) { picking = null }
        return
    }
    Scaffold(
        modifier = Modifier.swipeBack({ vm.closeReview() }),
        topBar = { TopAppBar(title = { Text("Review before adding") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { vm.closeReview() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { vm.confirmAddToCart() },
                    enabled = !review.loading && !busy && review.lines.any { it.include },
                    modifier = Modifier.weight(1f),
                ) { Text("Add to cart (${review.lines.count { it.include }})") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                review.loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.padding(6.dp))
                    Text("Finding products at your King Soopers...")
                }
                review.error != null -> Text(review.error, modifier = Modifier.padding(20.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Tap a product to see every option or search for something else. " +
                                "Your choices are remembered for next time.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(review.lines.size) { idx ->
                        CartLineRow(
                            review.lines[idx],
                            update = { block -> vm.updateLine(idx, block) },
                            onPick = { picking = idx },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, update: ((CartLine) -> CartLine) -> Unit, onPick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = line.include && line.options.isNotEmpty(),
                    enabled = line.options.isNotEmpty(),
                    onCheckedChange = { c -> update { it.copy(include = c) } },
                )
                Column(Modifier.weight(1f)) {
                    Text(line.term.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                    Text("For: ${line.need}", style = MaterialTheme.typography.bodySmall)
                }
                if (line.options.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { update { it.copy(quantity = (it.quantity - 1).coerceAtLeast(1)) } },
                    ) { Text("−") }
                    Text("${line.quantity}", modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedButton(
                        onClick = { update { it.copy(quantity = (it.quantity + 1).coerceAtMost(20)) } },
                    ) { Text("+") }
                }
            }
            TextButton(onClick = onPick) {
                Text(
                    if (line.options.isEmpty()) "No match found. Tap to search"
                    else Logic.productLabel(line.options[line.selected]) + "  ·  ${line.options.size} options",
                    maxLines = 2,
                )
            }
        }
    }
}

/** Full list of King Soopers products for one item, with a search box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPicker(vm: MealsViewModel, index: Int, line: CartLine, onClose: () -> Unit) {
    var query by remember(index) { mutableStateOf(line.search) }
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
                    keyboardActions = KeyboardActions(onSearch = { vm.researchLine(index, query) }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.researchLine(index, query) }) { Icon(Icons.Filled.Search, "Search") }
            }
            Text(
                "For: ${line.need}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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
                                    vm.selectProduct(index, n)
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
                                    p.price?.let { "$" + String.format("%.2f", it) },
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
