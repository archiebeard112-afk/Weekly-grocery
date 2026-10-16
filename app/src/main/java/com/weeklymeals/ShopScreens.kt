package com.weeklymeals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun ShopScreen(vm: MealsViewModel, st: UserState, openSettings: () -> Unit) {
    val items = vm.shopItems(st)
    val busy by vm.busy.collectAsState()
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Your shopping list is empty. Add recipes to your week first.")
        }
        return
    }
    val leftCount = items.count { it.key !in st.checked }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Shopping list", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    if (!vm.signedIn) openSettings() else vm.startCartReview()
                },
                enabled = !busy && leftCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.signedIn) "Add $leftCount items to King Soopers cart" else "Sign in to King Soopers (Settings)")
            }
            TextButton(onClick = { vm.clearChecked() }) { Text("Uncheck all") }
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
    Scaffold(
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
                            "Pick the product for each item. Your choices are remembered for next time.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(review.lines.size) { idx ->
                        CartLineRow(review.lines[idx]) { block -> vm.updateLine(idx, block) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, update: ((CartLine) -> CartLine) -> Unit) {
    var menu by remember { mutableStateOf(false) }
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
            if (line.options.isEmpty()) {
                Text("No match found at your store", style = MaterialTheme.typography.bodySmall)
            } else {
                Box {
                    TextButton(onClick = { menu = true }) {
                        Text(Logic.productLabel(line.options[line.selected]), maxLines = 2)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        line.options.forEachIndexed { n, p ->
                            DropdownMenuItem(
                                text = { Text(Logic.productLabel(p)) },
                                onClick = { update { it.copy(selected = n) }; menu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
