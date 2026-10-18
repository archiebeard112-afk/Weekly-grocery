package com.weeklymeals

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { Text("King Soopers") },
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

            Spacer(Modifier.padding(8.dp))
            UpdateSection(vm)
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
    Text("App updates", style = MaterialTheme.typography.titleSmall)
    Text("Version ${Updater.currentVersionName(ctx)}", style = MaterialTheme.typography.bodyMedium)
    when (val u = state) {
        UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Error -> {
            if (u == UpdateState.UpToDate) Text("You're on the newest version.", style = MaterialTheme.typography.bodySmall)
            if (u is UpdateState.Error) Text(u.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.checkForUpdate() }) { Text("Check for updates") }
        }
        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Checking…")
        }
        is UpdateState.Available -> Button(onClick = { vm.downloadUpdate(u.release) }) {
            Text("Update to ${u.release.name}")
        }
        is UpdateState.Downloading -> Column {
            Text("Downloading… ${u.percent}%", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { u.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
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
                OutlinedButton(onClick = { Updater.openInstallPermission(ctx) }) { Text("Allow installs") }
            }
            Button(onClick = {
                if (Updater.canInstall(ctx)) Updater.install(ctx, u.file) else Updater.openInstallPermission(ctx)
            }) { Text("Install") }
        }
    }
}
