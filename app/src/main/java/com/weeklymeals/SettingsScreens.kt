package com.weeklymeals
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.animation.AnimatedVisibility

import androidx.compose.foundation.clickable

import androidx.compose.material3.RadioButton

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MealsViewModel, st: UserState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val busy by vm.busy.collectAsState()
    val k = st.kroger
    var id by remember { mutableStateOf(k.clientId) }
    var secret by remember { mutableStateOf(k.clientSecret) }
    var zip by remember { mutableStateOf(k.zip) }
    var spoon by remember { mutableStateOf(st.spoonKey) }

    Scaffold(
        // Shown under the app's top bar, so the status bar is already accounted for.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        modifier = Modifier.swipeBack(onBack),
    ) { pad ->
        Column(
            // Same margins and title spot as the tabs.
            Modifier.padding(pad).imePadding().padding(horizontal = 10.dp, vertical = 6.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            val storeLine = listOf(
                if (k.store.id.isEmpty()) "No store yet" else k.store.name,
                if (vm.signedIn) "Signed in" else "Not signed in",
            ).joinToString(" · ")
            // How many you cook for (moved here from the Week tab).
            SettingsCard("🍽️", "Cooking for", "${st.servings} ${if (st.servings == 1) "person" else "people"}") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recipes and the Shop list scale to", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = { vm.setServings(st.servings - 1) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(34.dp).width(40.dp),
                    ) { Text("−") }
                    Text("${st.servings}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 10.dp))
                    FilledTonalButton(
                        onClick = { vm.setServings(st.servings + 1) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(34.dp).width(40.dp),
                    ) { Text("+") }
                }
                Text(
                    "Recipes you added keep their own servings and amounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Which day the Week tab starts on (your shopping day works well).
            SettingsCard("📅", "Week starts on", Logic.DAY_LONG[st.weekStart]) {
                Text(
                    "The Week tab lists days from here, and new meals fill the first empty day from here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Logic.DAY_SHORT.forEachIndexed { d, name ->
                        FilterChip(selected = st.weekStart == d, onClick = { vm.setWeekStart(d) }, label = { Text(name) })
                    }
                }
            }
            SettingsCard("🛒", "King Soopers", storeLine) {
                Text(
                    "Create a free app at developer.kroger.com. Set its redirect URI to ${KrogerApi.REDIRECT} " +
                        "and enable the Product, Location and Cart APIs. Then paste the keys below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(id, { id = it }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    secret, { secret = it }, label = { Text("Client secret") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    zip, { zip = it }, label = { Text("Your ZIP code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.findStore(id, secret, zip) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                ) { Text("Save and find my store") }
                Text(
                    if (k.store.id.isEmpty()) "Store: not set" else "Store: ${k.store.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.padding(4.dp))
                Text(
                    if (vm.signedIn) "Signed in to King Soopers" else "Not signed in",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val uri = vm.beginLogin(id, secret)
                            if (uri != null) CustomTabsIntent.Builder().build().launchUrl(ctx, uri)
                        },
                        enabled = !busy && id.isNotBlank() && secret.isNotBlank(),
                    ) { Text(if (vm.signedIn) "Sign in again" else "Sign in") }
                    if (vm.signedIn) OutlinedButton(onClick = { vm.signOut() }) { Text("Sign out") }
                }
                Text(
                    "Checkout still happens in the King Soopers app or website. This app only fills your cart.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SettingsCard("🔍", "Recipe search", RecipeSources.label(RecipeSources.effective(st))) {
                Text(
                    "Browse search also looks for recipes online. Pick which site to search:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val current = RecipeSources.effective(st)
                val notes = mapOf(
                    RecipeSources.SPOONACULAR to "Big selection. Needs a free key.",
                    RecipeSources.ALLRECIPES to "Big selection, no key. Could stop working if the site changes.",
                    RecipeSources.MEALDB to "No key, but only about 600 recipes.",
                    RecipeSources.OFF to "Search only the recipes in the app.",
                )
                Column {
                    RecipeSources.CHOICES.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setWebSource(key) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current == key, onClick = { vm.setWebSource(key) })
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    notes[key].orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (current == RecipeSources.SPOONACULAR) {
                    Text(
                        "Sign up for free at spoonacular.com/food-api, open your Profile, copy your API key and paste it below.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        spoon, { spoon = it }, label = { Text("Spoonacular API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { vm.setSpoonKey(spoon) },
                        enabled = spoon.trim() != st.spoonKey,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (st.spoonKey.isBlank()) "Save key" else "Update key") }
                    if (st.spoonKey.isNotBlank()) {
                        Text("Key saved.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            SettingsCard(
                "🍗", "Cook whole",
                if (st.wholeCuts.isEmpty()) "Off" else Packs.WHOLE_CUTS.filter { it.first in st.wholeCuts }.joinToString(", ") { it.second },
            ) {
                Text(
                    "Cuts you cook whole, 1 per person. Recipes, shopping and the freezer count them by the piece instead of by weight.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Packs.WHOLE_CUTS.forEach { (key, label) ->
                        FilterChip(selected = key in st.wholeCuts, onClick = { vm.toggleWholeCut(key) }, label = { Text(label) })
                    }
                }
            }
            val hiddenList = vm.recipes(st).values.filter { it.id in st.removed }.sortedBy { it.name }
            val hiddenSides = vm.sideMap.values.filter { it.key in st.hiddenSides }.sortedBy { it.label.substringAfter(" ") }
            val hiddenCount = hiddenList.size + hiddenSides.size
            SettingsCard("🙈", "Hidden recipes & sides", if (hiddenCount == 0) "None" else "$hiddenCount hidden") {
                if (hiddenCount == 0) {
                    Text(
                        "Hold down on a recipe tile, or on a side in the Pick a side pop-up, to hide it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                hiddenList.forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${r.emoji}  ${r.name}", style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.restoreRecipe(r.id) }) { Text("Show again") }
                    }
                }
                hiddenSides.forEach { sd ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${sd.label} (side)", style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.restoreSide(sd.key) }) { Text("Show again") }
                    }
                }
            }
            SettingsCard("🏷️", "Sales and budget", "Sale ${Sales.money(st.saleMin).substringBefore(".")}+ · Budget ${Sales.money(st.budgetMax).substringBefore(".")} a meal") {
                Text(
                    "A meal counts as on sale (the Sale filter and 🏷️ on tiles) when it saves at least:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.0, 2.0, 3.0, 5.0).forEach { v ->
                        FilterChip(selected = st.saleMin == v, onClick = { vm.setSaleMin(v) }, label = { Text(Sales.money(v).substringBefore(".")) })
                    }
                }
                Text(
                    "A meal is 💲 Budget (and its price shows green) when what you'd buy for it costs at most:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10.0, 15.0, 20.0, 25.0).forEach { v ->
                        FilterChip(selected = st.budgetMax == v, onClick = { vm.setBudgetMax(v) }, label = { Text(Sales.money(v).substringBefore(".")) })
                    }
                }
                Text(
                    "Things in your pantry or marked Already have don't count toward the price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BackupCard(vm)

            // Updates: just a button at the bottom.
            Spacer(Modifier.height(8.dp))
            UpdateSection(vm)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(vm: MealsViewModel, linkMode: Boolean, startLink: String?, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍽️") }
    var base by remember { mutableStateOf("4") }
    var ings by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf(startLink.orEmpty()) }
    var importing by remember { mutableStateOf(false) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val doImport: () -> Unit = {
        val url = Importer.findUrl(link) ?: link.trim()
        if (url.isNotBlank() && !importing) {
            importing = true
            importMsg = null
            scope.launch {
                try {
                    val r = Importer.fromUrl(url)
                    name = r.name
                    emoji = r.emoji
                    r.serves?.let { base = it.toString() }
                    ings = r.ingredients.joinToString("\n")
                    steps = r.steps.joinToString("\n")
                    importMsg = "Imported! Check everything below, then save."
                } catch (e: Exception) {
                    importMsg = e.message ?: "Couldn't read this page. Paste the ingredients and steps instead."
                } finally {
                    importing = false
                }
            }
        }
    }
    // Shared from the browser: import right away.
    LaunchedEffect(startLink) { if (!startLink.isNullOrBlank()) doImport() }

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { Text("Add a recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).imePadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (linkMode) {
                Text("Paste a link to a recipe page.", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        link, { link = it }, label = { Text("Recipe link") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { doImport() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = doImport, enabled = !importing && link.isNotBlank()) { Text("Import") }
                }
                if (importing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Reading the recipe…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                importMsg?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("Imported")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            } else {
                Text(
                    "Copy the ingredients and directions from any recipe page and paste them here, one per line.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(name, { name = it }, label = { Text("Recipe name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(emoji, { emoji = it }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    base, { base = it.filter(Char::isDigit) }, label = { Text("Serves") }, singleLine = true,
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
            Button(
                onClick = {
                    val ok = vm.addCustomRecipe(name, emoji, base.toIntOrNull() ?: 4, ings, steps)
                    if (ok) onBack() else error = "Needs a name, at least one ingredient and one step"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save to favorites") }
        }
    }
}

/** "App updates": version, Check for updates, download progress and install. */
@Composable
private fun UpdateSection(vm: MealsViewModel) {
    val ctx = LocalContext.current
    val state by vm.update.collectAsState()
    val full = Modifier.fillMaxWidth()
    when (val u = state) {
        UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Error ->
            OutlinedButton(onClick = { vm.checkForUpdate() }, modifier = full) { Text("Check for updates") }
        UpdateState.Checking -> OutlinedButton(onClick = {}, modifier = full) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Checking…")
        }
        is UpdateState.Available -> Button(onClick = { vm.downloadUpdate(u.release) }, modifier = full) {
            Text("Update to ${u.release.name}")
        }
        is UpdateState.Downloading -> OutlinedButton(onClick = {}, modifier = full) {
            Text("Downloading… ${u.percent}%")
        }
        is UpdateState.Ready -> {
            LaunchedEffect(u.file) {
                if (Updater.canInstall(ctx)) Updater.install(ctx, u.file)
            }
            if (!Updater.canInstall(ctx)) {
                Text(
                    "One-time step: allow Weekly Meals to install updates, then come back and tap Install.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { Updater.openInstallPermission(ctx) }, modifier = full) { Text("Allow installs") }
            }
            Button(onClick = {
                if (Updater.canInstall(ctx)) Updater.install(ctx, u.file) else Updater.openInstallPermission(ctx)
            }, modifier = full) { Text("Install") }
        }
    }
    if (state is UpdateState.Downloading) {
        LinearProgressIndicator(
            progress = { (state as UpdateState.Downloading).percent / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
    Text(
        "Version ${Updater.currentVersionName(ctx)}" + when (val u = state) {
            UpdateState.UpToDate -> " · You're on the newest version"
            is UpdateState.Error -> " · ${u.message}"
            else -> ""
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (state is UpdateState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

/** A tappable settings group: icon, title and one-line summary; tap to open or close. */
@Composable
private fun SettingsCard(
    icon: String, title: String, summary: String, startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    if (open) "Close" else "Open",
                )
            }
            AnimatedVisibility(open) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

/** Save everything to a file, or restore from one. */
@Composable
private fun BackupCard(vm: MealsViewModel) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupText().toByteArray()) }
            }.onSuccess { vm.message.value = "Backup saved" }
                .onFailure { vm.message.value = "Couldn't save the backup" }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pending = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            if (pending == null) vm.message.value = "Couldn't read that file"
        }
    }
    SettingsCard("💾", "Backup and restore", "Save everything to a file") {
        Text(
            "Saves your recipes, sides, favorites, pantry, history, this week and settings (including your keys) " +
                "to one file. Keep it in Google Drive or Downloads. After restoring, sign in to King Soopers again.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                save.launch("weekly-meals-backup-$day.json")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save backup") }
        OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from backup")
        }
    }
    pending?.let { text ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app now will be replaced with what's in the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!vm.restoreBackup(text)) vm.message.value = "That file isn't a Weekly Meals backup"
                    pending = null
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
