package com.weeklymeals

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

/** One row on the "review before adding to cart" screen. */
data class CartLine(
    val item: ShopItem,
    val options: List<Product>,
    val selected: Int = 0,
    val include: Boolean = true,
    val quantity: Int = 1,
)

data class CartReview(
    val loading: Boolean = true,
    val lines: List<CartLine> = emptyList(),
    val error: String? = null,
)

class MealsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val api = KrogerApi()
    private val json = Json { ignoreUnknownKeys = true }

    val bundle: RecipeBundle = app.assets.open("recipes.json").bufferedReader().use {
        json.decodeFromString(RecipeBundle.serializer(), it.readText())
    }
    val sideMap: Map<String, Side> = bundle.sides.associateBy { it.key }

    val state: StateFlow<UserState> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserState())

    /** One-line messages shown as a snackbar (success or error). */
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val review = MutableStateFlow<CartReview?>(null)

    /** The 10 recipes currently on the Browse tab. */
    val browse = MutableStateFlow<List<Int>>(emptyList())

    init {
        refreshBrowse()
    }

    fun recipes(st: UserState = state.value): Map<Int, Recipe> =
        (bundle.recipes + st.custom).associateBy { it.id }

    fun refreshBrowse() {
        val pool = bundle.recipes.filter { !it.imported }.map { it.id }
        browse.value = pool.shuffled().take(10)
    }

    private fun edit(block: (UserState) -> UserState) {
        viewModelScope.launch { store.update(block) }
    }

    // ---- Week ----------------------------------------------------------------

    fun addToWeek(id: Int) = edit { s ->
        if (id in s.week) s else {
            val r = recipes(s)[id]
            val firstSide = r?.sides?.firstOrNull()
            s.copy(
                week = s.week + id,
                sides = if (firstSide != null && id !in s.sides) s.sides + (id to firstSide) else s.sides,
            )
        }
    }

    fun removeFromWeek(id: Int) = edit { s -> s.copy(week = s.week - id, sides = s.sides - id) }

    fun move(id: Int, delta: Int) = edit { s ->
        val list = s.week.toMutableList()
        val from = list.indexOf(id)
        val to = from + delta
        if (from < 0 || to < 0 || to >= list.size) s else {
            list.removeAt(from)
            list.add(to, id)
            s.copy(week = list)
        }
    }

    fun setSide(id: Int, key: String?) = edit { s ->
        s.copy(sides = if (key == null) s.sides - id else s.sides + (id to key))
    }

    fun setServings(n: Int) = edit { it.copy(servings = n.coerceIn(1, 12)) }

    // ---- Favorites -----------------------------------------------------------

    fun addFav(id: Int) = edit { it.copy(favs = it.favs + id, hidden = it.hidden - id) }

    /** Call only after the "are you sure?" dialog is confirmed. */
    fun removeFav(id: Int) = edit { it.copy(favs = it.favs - id, hidden = it.hidden + id) }

    fun addCustomRecipe(name: String, emoji: String, base: Int, ingText: String, stepText: String): Boolean {
        val ings = ingText.lines().mapNotNull { Logic.parseIngredient(it) }
        val steps = stepText.lines().map { it.trim().replace(Regex("^\\d+[.)]\\s*"), "") }.filter { it.isNotEmpty() }
        if (name.isBlank() || ings.isEmpty() || steps.isEmpty()) return false
        edit { s ->
            val nextId = (recipes(s).keys.maxOrNull() ?: 99).coerceAtLeast(99) + 1
            val r = Recipe(
                id = nextId, name = name.trim(), emoji = emoji.ifBlank { "🍽️" },
                base = base.coerceAtLeast(1), steps = steps, ingredients = ings,
                sides = bundle.sides.take(4).map { it.key }, imported = true,
            )
            s.copy(custom = s.custom + r)
        }
        return true
    }

    // ---- Shopping list ---------------------------------------------------------

    fun shopItems(st: UserState = state.value): List<ShopItem> =
        Logic.shopList(st.week, st.sides, recipes(st), sideMap, st.servings)

    fun toggleChecked(key: String) = edit { s ->
        s.copy(checked = if (key in s.checked) s.checked - key else s.checked + key)
    }

    fun clearChecked() = edit { it.copy(checked = emptySet()) }

    // ---- King Soopers ----------------------------------------------------------

    fun saveKrogerSettings(clientId: String, secret: String, zip: String) = edit { s ->
        val k = s.kroger
        val changedApp = k.clientId != clientId.trim()
        s.copy(
            kroger = k.copy(
                clientId = clientId.trim(), clientSecret = secret.trim(), zip = zip.trim(),
                accessToken = if (changedApp) "" else k.accessToken,
                refreshToken = if (changedApp) "" else k.refreshToken,
            ),
        )
    }

    fun findStore() {
        val k = state.value.kroger
        if (k.clientId.isBlank() || k.clientSecret.isBlank() || k.zip.isBlank()) {
            message.value = "Enter client ID, secret and ZIP first"
            return
        }
        viewModelScope.launch {
            busy.value = true
            try {
                val token = api.clientToken(k.clientId, k.clientSecret)
                val found = api.findStore(token, k.zip)
                if (found == null) message.value = "No King Soopers found near ${k.zip}"
                else {
                    store.update { it.copy(kroger = it.kroger.copy(store = StoreInfo(found.id, found.name))) }
                    message.value = "Store set: ${found.name}"
                }
            } catch (e: Exception) {
                message.value = e.message ?: "Could not reach Kroger"
            } finally {
                busy.value = false
            }
        }
    }

    /** Returns the web address to open in a Custom Tab, or null if settings are missing. */
    fun beginLogin(clientId: String, secret: String): Uri? {
        if (clientId.isBlank() || secret.isBlank()) {
            message.value = "Add your Kroger client ID and secret first"
            return null
        }
        val st = UUID.randomUUID().toString()
        edit {
            it.copy(kroger = it.kroger.copy(
                clientId = clientId.trim(), clientSecret = secret.trim(), pendingState = st,
            ))
        }
        return api.authorizeUrl(clientId.trim(), st)
    }

    /** Called from MainActivity when Kroger sends you back via weeklymeals://kroger */
    fun handleRedirect(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val returned = uri.getQueryParameter("state")
        val err = uri.getQueryParameter("error")
        if (err != null) {
            message.value = "Sign-in cancelled ($err)"
            return
        }
        viewModelScope.launch {
            val k = state.value.kroger
            if (code == null || returned != k.pendingState || k.pendingState.isEmpty()) {
                message.value = "Sign-in response did not match, try again"
                return@launch
            }
            busy.value = true
            try {
                val t = api.exchangeCode(k.clientId, k.clientSecret, code)
                store.update {
                    it.copy(kroger = it.kroger.copy(
                        accessToken = t.accessToken, refreshToken = t.refreshToken,
                        expiresAt = t.expiresAt, pendingState = "",
                    ))
                }
                message.value = "Signed in to King Soopers"
            } catch (e: Exception) {
                message.value = e.message ?: "Sign-in failed"
            } finally {
                busy.value = false
            }
        }
    }

    fun signOut() = edit {
        it.copy(kroger = it.kroger.copy(accessToken = "", refreshToken = "", expiresAt = 0L))
    }

    val signedIn: Boolean get() = state.value.kroger.refreshToken.isNotEmpty() ||
        state.value.kroger.accessToken.isNotEmpty()

    /** Returns a valid user token, refreshing if needed, or null if sign-in is required. */
    private suspend fun userToken(): String? {
        val k = state.value.kroger
        if (k.accessToken.isNotEmpty() && System.currentTimeMillis() < k.expiresAt) return k.accessToken
        if (k.refreshToken.isEmpty()) return null
        return try {
            val t = api.refresh(k.clientId, k.clientSecret, k.refreshToken)
            store.update {
                it.copy(kroger = it.kroger.copy(
                    accessToken = t.accessToken, refreshToken = t.refreshToken, expiresAt = t.expiresAt,
                ))
            }
            t.accessToken
        } catch (e: Exception) {
            null
        }
    }

    /** Looks up King Soopers products for each unchecked item so you can review them. */
    fun startCartReview() {
        val st = state.value
        val k = st.kroger
        if (k.store.id.isEmpty()) {
            message.value = "Pick your store in Settings first"
            return
        }
        val items = shopItems(st).filter { it.key !in st.checked }
        if (items.isEmpty()) {
            message.value = "Nothing left to add"
            return
        }
        review.value = CartReview(loading = true)
        viewModelScope.launch {
            try {
                val token = api.clientToken(k.clientId, k.clientSecret)
                val lines = items.map { item ->
                    val options = runCatching { api.searchProducts(token, k.store.id, item.name) }
                        .getOrDefault(emptyList())
                    val remembered = k.choices[item.key]?.let { c -> options.indexOfFirst { it.upc == c.upc } } ?: -1
                    CartLine(
                        item = item,
                        options = options,
                        selected = if (remembered >= 0) remembered else 0,
                        include = options.isNotEmpty(),
                        quantity = 1,
                    )
                }
                review.value = CartReview(loading = false, lines = lines)
            } catch (e: Exception) {
                review.value = CartReview(loading = false, error = e.message ?: "Could not search products")
            }
        }
    }

    fun updateLine(index: Int, block: (CartLine) -> CartLine) {
        val r = review.value ?: return
        if (index !in r.lines.indices) return
        review.value = r.copy(lines = r.lines.toMutableList().also { it[index] = block(it[index]) })
    }

    fun closeReview() {
        review.value = null
    }

    /** Sends the chosen products to your King Soopers cart. */
    fun confirmAddToCart() {
        val r = review.value ?: return
        val chosen = r.lines.filter { it.include && it.options.isNotEmpty() }
        if (chosen.isEmpty()) {
            message.value = "No items selected"
            return
        }
        viewModelScope.launch {
            busy.value = true
            try {
                val token = userToken()
                if (token == null) {
                    message.value = "Sign in to King Soopers first (Settings)"
                    return@launch
                }
                api.addToCart(token, chosen.map { it.options[it.selected].upc to it.quantity })
                store.update { s ->
                    val newChoices = chosen.associate {
                        val p = it.options[it.selected]
                        it.item.key to Choice(p.upc, p.title)
                    }
                    s.copy(kroger = s.kroger.copy(choices = s.kroger.choices + newChoices))
                }
                review.value = null
                message.value = "Added ${chosen.size} items to your King Soopers cart"
            } catch (e: Exception) {
                message.value = e.message ?: "Could not add to cart"
            } finally {
                busy.value = false
            }
        }
    }
}
