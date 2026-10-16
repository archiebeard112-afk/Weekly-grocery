package com.weeklymeals

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(vm: MealsViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍽️") }
    var base by remember { mutableStateOf("4") }
    var ings by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
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
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Copy the ingredients and directions from any recipe page and paste them here, one per line.",
                style = MaterialTheme.typography.bodyMedium,
            )
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
