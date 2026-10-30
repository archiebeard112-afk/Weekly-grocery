package com.weeklymeals
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 4.dp))

            SettingsSection("Meals") {
                // How many you cook for (moved here from the Week tab).
                SettingsCard("Cooking for", "${st.servings} ${if (st.servings == 1) "person" else "people"}", first = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundButton("−") { vm.setServings(st.servings - 1) }
                        Text(
                            "${st.servings}", style = MaterialTheme.typography.headlineMedium, color = Look.Lime,
                            textAlign = TextAlign.Center, modifier = Modifier.width(44.dp),
                        )
                        RoundButton("+") { vm.setServings(st.servings + 1) }
                        Spacer(Modifier.width(12.dp))
                        Note("Recipes and the Shop list scale to this", Modifier.weight(1f))
                    }
                    Note("Recipes you added keep their own servings and amounts.")
                }
                // Which day the Week tab starts on (your shopping day works well).
                SettingsCard("Week starts on", Logic.DAY_LONG[st.weekStart]) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Logic.DAY_SHORT.forEachIndexed { d, name ->
                            PillChip(name, st.weekStart == d) { vm.setWeekStart(d) }
                        }
                    }
                    Note("The Week tab starts here, and new meals fill the first empty day from here.")
                }
                SettingsCard(
                    "Cook whole",
                    if (st.wholeCuts.isEmpty()) "Off" else Packs.WHOLE_CUTS.filter { it.first in st.wholeCuts }.joinToString(", ") { it.second },
                ) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Packs.WHOLE_CUTS.forEach { (key, label) ->
                            PillChip(label, key in st.wholeCuts) { vm.toggleWholeCut(key) }
                        }
                    }
                    Note("Cuts you cook whole, 1 per person. Recipes, shopping and the freezer count them by the piece instead of by weight.")
                }
            }

            SettingsSection("Shopping") {
                SettingsCard("King Soopers", if (vm.signedIn) "Signed in" else if (k.store.id.isEmpty()) "Not set up" else "Not signed in", first = true) {
                    Note(
                        "Create a free app at developer.kroger.com. Set its redirect URI to ${KrogerApi.REDIRECT} " +
                            "and enable the Product, Location and Cart APIs. Then paste the keys below.",
                    )
                    SettingsField(id, { id = it }, "Client ID")
                    SettingsField(secret, { secret = it }, "Client secret", hidden = true)
                    SettingsField(zip, { zip = it }, "Your ZIP code", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Button(
                        onClick = { vm.findStore(id, secret, zip) },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save and find my store", fontFamily = Look.Display, fontWeight = FontWeight.Bold) }
                    Note(if (k.store.id.isEmpty()) "Store: not set" else "Store: ${k.store.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = vm.beginLogin(id, secret)
                                if (uri != null) CustomTabsIntent.Builder().build().launchUrl(ctx, uri)
                            },
                            enabled = !busy && id.isNotBlank() && secret.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text(if (vm.signedIn) "Sign in again" else "Sign in") }
                        if (vm.signedIn) OutlinedButton(onClick = { vm.signOut() }, modifier = Modifier.weight(1f)) { Text("Sign out") }
                    }
                    Note("Checkout still happens in the King Soopers app or website. This app only fills your cart.")
                }
                SettingsCard("Sales and budget", "${Sales.money(st.saleMin).substringBefore(".")}+ · ${Sales.money(st.budgetMax).substringBefore(".")}") {
                    Note("A meal counts as on sale (the Sale filter and tag on tiles) when it saves at least")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1.0, 2.0, 3.0, 5.0).forEach { v ->
                            PillChip(Sales.money(v).substringBefore("."), st.saleMin == v) { vm.setSaleMin(v) }
                        }
                    }
                    Note("A meal is Budget (and its price shows green) when what you'd buy for it costs at most")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10.0, 15.0, 20.0, 25.0).forEach { v ->
                            PillChip(Sales.money(v).substringBefore("."), st.budgetMax == v) { vm.setBudgetMax(v) }
                        }
                    }
                    Note("Things in your pantry or marked Already have don't count toward the price.")
                }
            }

            SettingsSection("Recipes") {
                SettingsCard("Recipe search", RecipeSources.label(RecipeSources.effective(st)), first = true) {
                    Note("Browse search also looks for recipes online. Pick which site to search:")
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
                                Modifier.fillMaxWidth().clickable { vm.setWebSource(key) }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = current == key, onClick = { vm.setWebSource(key) })
                                Column {
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                    Note(notes[key].orEmpty())
                                }
                            }
                        }
                    }
                    if (current == RecipeSources.SPOONACULAR) {
                        Note("Sign up for free at spoonacular.com/food-api, open your Profile, copy your API key and paste it below.")
                        SettingsField(spoon, { spoon = it }, "Spoonacular API key", hidden = true)
                        Button(
                            onClick = { vm.setSpoonKey(spoon) },
                            enabled = spoon.trim() != st.spoonKey,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (st.spoonKey.isBlank()) "Save key" else "Update key", fontFamily = Look.Display, fontWeight = FontWeight.Bold) }
                        if (st.spoonKey.isNotBlank()) Note("Key saved.")
                    }
                }
                val hiddenList = vm.recipes(st).values.filter { it.id in st.removed }.sortedBy { it.name }
                val hiddenSides = vm.sideMap.values.filter { it.key in st.hiddenSides }.sortedBy { it.label.substringAfter(" ") }
                val hiddenCount = hiddenList.size + hiddenSides.size
                SettingsCard("Hidden recipes & sides", if (hiddenCount == 0) "None" else "$hiddenCount hidden") {
                    if (hiddenCount == 0) {
                        Note("Hold down on a recipe tile, or on a side in the Pick a side pop-up, to hide it.")
                    }
                    hiddenList.forEach { r ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.name, style = MaterialTheme.typography.bodyLarge,
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
            }

            SettingsSection("Reminders") {
                SettingsCard(
                    "Thaw reminders", if (st.thawOn) st.thawTimes.joinToString(" · ") { Thaw.label(it) } else "Off",
                    first = true,
                ) { ThawSettings(vm, st) }
            }

            SettingsSection("App") {
                BackupCard(vm)
                UpdateCard(vm)
            }
            Text(
                "Version ${Updater.currentVersionName(ctx)}",
                style = MaterialTheme.typography.bodySmall, color = Look.Muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(vm: MealsViewModel, linkMode: Boolean, startLink: String?, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍽️") }
    var base by remember { mutableStateOf("4") }
    // Main meal, side or dessert: guessed from the name when a link is imported; tap to change.
    var kind by remember { mutableStateOf("main") }
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
                    kind = Logic.guessKind(r.name)
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
            KindChoice(kind) { kind = it }
            OutlinedTextField(name, { name = it }, label = { Text("Recipe name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmojiPicker(emoji, { emoji = it }, Modifier.weight(1f))
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
                    val ok = vm.addCustomRecipe(name, emoji, base.toIntOrNull() ?: 4, ings, steps, kind)
                    if (ok) onBack() else error = "Needs a name, at least one ingredient and one step"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save to favorites") }
        }
    }
}

/** "This is a" Main meal / Side / Dessert: three buttons, the picked one filled. */
@Composable
internal fun KindChoice(kind: String, onPick: (String) -> Unit) {
    Column {
        Text("This is a", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("main" to "Main meal", "side" to "Side", "dessert" to "Dessert").forEach { (key, label) ->
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                if (key == kind) {
                    Button(onClick = { onPick(key) }, contentPadding = pad, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { onPick(key) }, contentPadding = pad, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}

private val EMOJI_GROUPS = listOf(
    "Desserts" to listOf("🍪", "🍰", "🎂", "🧁", "🥧", "🍩", "🍫", "🍮", "🍨", "🍦", "🍞", "🥐", "🍌", "🍓", "🍎", "🍑", "🍒", "🍋"),
    "Meals and sides" to listOf(
        "🍝", "🍕", "🌮", "🌯", "🍔", "🥪", "🍛", "🍜", "🍲", "🥘", "🍗", "🥩", "🍖", "🐟", "🍤", "🥗",
        "🥔", "🌽", "🥦", "🥕", "🍚", "🥖", "🍳", "🥞", "🍽️",
    ),
)

/** The Emoji box: tap it for a grid to pick from (or type your own at the bottom). */
@Composable
private fun EmojiPicker(emoji: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    Box(modifier) {
        OutlinedTextField(
            emoji, {}, readOnly = true, label = { Text("Emoji") }, singleLine = true,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The whole box opens the grid (a read-only field would just show a cursor).
        Box(Modifier.matchParentSize().padding(top = 8.dp).clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EMOJI_GROUPS.forEach { (title, list) ->
                Text(
                    title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                )
                list.chunked(6).forEach { row ->
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        row.forEach { e ->
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                    .then(if (e == emoji) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                                    .clickable { onPick(e); open = false },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 24.sp) }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(264.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    typed, { typed = it }, label = { Text("Or type one") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { if (typed.isNotBlank()) { onPick(typed.trim()); typed = ""; open = false } }) { Text("Use") }
            }
        }
    }
}

/** "Check for updates" row: shows where the update stands; open it for the buttons. */
@Composable
private fun UpdateCard(vm: MealsViewModel) {
    val ctx = LocalContext.current
    val state by vm.update.collectAsState()
    val full = Modifier.fillMaxWidth()
    val bold = FontWeight.Bold
    val value = when (state) {
        UpdateState.UpToDate -> "Up to date"
        UpdateState.Checking -> "Checking…"
        is UpdateState.Available -> "Update ready"
        is UpdateState.Downloading -> "Downloading"
        is UpdateState.Ready -> "Ready to install"
        is UpdateState.Error -> "Couldn't check"
        UpdateState.Idle -> ""
    }
    SettingsCard("Check for updates", value, dim = state !is UpdateState.Available && state !is UpdateState.Ready) {
        when (val u = state) {
            UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Error ->
                Button(onClick = { vm.checkForUpdate() }, modifier = full) { Text("Check for updates", fontFamily = Look.Display, fontWeight = bold) }
            UpdateState.Checking -> OutlinedButton(onClick = {}, modifier = full) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking…")
            }
            is UpdateState.Available -> Button(onClick = { vm.downloadUpdate(u.release) }, modifier = full) {
                Text("Update to ${u.release.name}", fontFamily = Look.Display, fontWeight = bold)
            }
            is UpdateState.Downloading -> {
                OutlinedButton(onClick = {}, modifier = full) { Text("Downloading… ${u.percent}%") }
                LinearProgressIndicator(progress = { u.percent / 100f }, modifier = full)
            }
            is UpdateState.Ready -> {
                LaunchedEffect(u.file) {
                    if (Updater.canInstall(ctx)) Updater.install(ctx, u.file)
                }
                if (!Updater.canInstall(ctx)) {
                    Note("One-time step: allow Weekly Meals to install updates, then come back and tap Install.")
                    OutlinedButton(onClick = { Updater.openInstallPermission(ctx) }, modifier = full) { Text("Allow installs") }
                }
                Button(onClick = {
                    if (Updater.canInstall(ctx)) Updater.install(ctx, u.file) else Updater.openInstallPermission(ctx)
                }, modifier = full) { Text("Install", fontFamily = Look.Display, fontWeight = bold) }
            }
        }
        when (val u = state) {
            UpdateState.UpToDate -> Note("You're on the newest version.")
            is UpdateState.Error -> Text(u.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}

/** A small grey section name over one dark card that holds its rows. */
@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp, color = Look.Muted, modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
    Surface(shape = RoundedCornerShape(16.dp), color = Look.Card, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** A row in a section: title, its current setting in lime, and a chevron; tap to open or close. */
@Composable
private fun SettingsCard(
    title: String, summary: String, first: Boolean = false, dim: Boolean = false, startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column {
        // Thin line between rows; none above the first row or between a row and what it opens.
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }
                .padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = if (open) 6.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(12.dp))
            Text(
                summary, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (dim) FontWeight.Normal else FontWeight.SemiBold,
                color = if (dim) Look.Muted else Look.Lime,
                textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown, if (open) "Close" else "Open",
                tint = Look.Muted, modifier = Modifier.size(20.dp).rotate(turn),
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

/** Small grey help text inside a settings row. */
@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Look.Muted, modifier = modifier)
}

/** A pill choice: outlined, or filled lime when picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Look.Lime, selectedLabelColor = Look.Ink,
        ),
    )
}

/** Round − / + button for the servings stepper. */
@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(38.dp),
    ) { Text(label, fontSize = 18.sp) }
}

/** A dark rounded typing box (no Android outline until you tap it). */
@Composable
private fun SettingsField(
    value: String, onChange: (String) -> Unit, label: String, hidden: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, keyboardOptions = keyboard,
        visualTransformation = if (hidden) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Look.Lime,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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
    SettingsCard("Backup and restore", "Save to a file", first = true, dim = true) {
        Note(
            "Saves your recipes, sides, favorites, pantry, history, this week and settings (including your keys) " +
                "to one file. Keep it in Google Drive or Downloads. After restoring, sign in to King Soopers again.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    save.launch("weekly-meals-backup-$day.json")
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save backup", fontFamily = Look.Display, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.weight(1f),
            ) { Text("Restore") }
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
