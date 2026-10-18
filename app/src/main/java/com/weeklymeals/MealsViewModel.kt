package com.weeklymeals

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import java.util.UUID

/** The King Soopers product choice for one shopping-list item. */
data class CartLine(
    /** What gets searched at the store, e.g. "lemons" (also used to remember your pick). */
    val term: String,
    /** The search currently shown (starts as [term]; you can type your own). */
    val search: String = term,
    val searching: Boolean = false,
    /** A short heads-up, e.g. your usual product is out of stock. */
    val note: String? = null,
    /** Total amount needed when looked up, used to work out how many packages to add. */
    val needAmount: Logic.Need = Logic.Need(),
    val options: List<Product>,
    val selected: Int = 0,
    val quantity: Int = 1,
) {
    val product: Product? get() = options.getOrNull(selected)
}

/** One line on the Shop tab: everything bought as the same thing (Lemons + Lemon zest). */
data class ShopGroup(
    val term: String,
    val items: List<ShopItem>,
    val aisle: String,
    /** Which meals (or Snacks / Usual items / Added) need it. */
    val sources: List<String>,
) {
    val need: String get() = items.joinToString(", ") { Logic.needText(it).ifBlank { "1" } }
}

/** The Shop section for everything that isn't from a recipe. */
const val EXTRAS = "🛒 Extras"

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

    /** King Soopers products looked up for the Shop tab, by search term. */
    val products = MutableStateFlow<Map<String, CartLine>>(emptyMap())
    private var productsStore = ""

    /** (done, total) while products are being looked up; null when idle. */
    val productsLoading = MutableStateFlow<Pair<Int, Int>?>(null)

    /** The item whose product list is open (search term), or null. */
    val picking = MutableStateFlow<String?>(null)

    /** The 10 recipes currently on the Browse tab. */
    val browse = MutableStateFlow<List<Int>>(emptyList())

    /** Browse shows only meals with $3+ of savings when this is on. */
    val saleOnly = MutableStateFlow(false)

    init {
        refreshBrowse()
        // One time: seed Quick add with the snacks and usual items picked before.
        viewModelScope.launch {
            store.update { s ->
                if (s.boughtSeeded) s else {
                    val now = System.currentTimeMillis()
                    val names = (s.lastSnacks + s.snacks + s.lastStaples + s.pickedStaples + s.lastExtraItems).distinctBy { it.lowercase() }
                    s.copy(
                        boughtSeeded = true,
                        bought = s.bought + names.associate { Logic.searchTerm(it) to Bought(it, 1, now) },
                    )
                }
            }
        }
    }

    fun recipes(st: UserState = state.value): Map<Int, Recipe> =
        (bundle.recipes + st.custom).associateBy { it.id }

    fun refreshBrowse() {
        if (saleOnly.value) {
            val st = state.value
            val cache = salesCache(st)
            val pool = recipes(st).values.filter { Sales.savings(it, st.servings, cache) >= Sales.MIN_SAVINGS }.map { it.id }
            browse.value = pool.shuffled().take(10)
            if (pool.isEmpty()) message.value = "No meals save \$3 or more at your store today"
            return
        }
        val pool = bundle.recipes.filter { !it.imported }.map { it.id }
        browse.value = pool.shuffled().take(10)
    }

    // ---- Sales -------------------------------------------------------------------

    /** (done, total) while sale prices are being checked; null when idle. */
    val salesProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Today's sale prices for your store (empty if stale or another store). */
    fun salesCache(st: UserState = state.value): SalesCache =
        st.sales.takeIf {
            it.day == Sales.today() && it.storeId == st.kroger.store.id && it.version == Sales.CACHE_VERSION
        } ?: SalesCache()

    private suspend fun fetchSales(terms: Collection<String>) {
        val st = state.value
        val k = st.kroger
        val cache = salesCache(st)
        val todo = terms.filter { it !in cache.checked }.distinct()
        if (todo.isEmpty()) return
        val token = api.clientToken(k.clientId, k.clientSecret)
        val found = ConcurrentHashMap<String, List<SaleItem>>()
        val done = AtomicInteger(0)
        salesProgress.value = 0 to todo.size
        try {
            coroutineScope {
                val gate = Semaphore(4)
                todo.map { t ->
                    async {
                        gate.withPermit {
                            runCatching { api.searchProducts(token, k.store.id, t, limit = 15) }.getOrNull()
                                ?.let { Sales.saleOptions(t, it) }
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { found[t] = it }
                            salesProgress.value = done.incrementAndGet() to todo.size
                        }
                    }
                }.awaitAll()
            }
            store.update { s ->
                val c = s.sales.takeIf {
                    it.day == Sales.today() && it.storeId == k.store.id && it.version == Sales.CACHE_VERSION
                } ?: SalesCache()
                s.copy(
                    sales = c.copy(
                        day = Sales.today(), storeId = k.store.id, version = Sales.CACHE_VERSION,
                        checked = c.checked + todo, options = c.options + found, items = emptyMap(),
                    ),
                )
            }
        } finally {
            salesProgress.value = null
        }
    }

    private fun storeReady(): Boolean {
        val k = state.value.kroger
        if (k.store.id.isEmpty() || k.clientId.isBlank()) {
            message.value = "Pick your King Soopers store in Settings first"
            return false
        }
        return true
    }

    fun setSaleOnly(on: Boolean) {
        if (!on) {
            saleOnly.value = false
            refreshBrowse()
            return
        }
        if (!storeReady() || salesProgress.value != null) return
        viewModelScope.launch {
            try {
                val st = state.value
                fetchSales(recipes(st).values.flatMap { Sales.terms(it, st.servings) }.toSet())
                saleOnly.value = true
                refreshBrowse()
            } catch (e: Exception) {
                message.value = e.message ?: "Could not check sale prices"
            }
        }
    }

    /** Checks sale prices for just one recipe's main ingredients. */
    fun checkSales(r: Recipe) {
        if (!storeReady() || salesProgress.value != null) return
        viewModelScope.launch {
            try {
                fetchSales(Sales.terms(r, state.value.servings))
            } catch (e: Exception) {
                message.value = e.message ?: "Could not check sale prices"
            }
        }
    }

    // ---- App updates -----------------------------------------------------------

    val update = MutableStateFlow<UpdateState>(UpdateState.Idle)

    fun checkForUpdate() {
        if (update.value is UpdateState.Checking || update.value is UpdateState.Downloading) return
        update.value = UpdateState.Checking
        viewModelScope.launch {
            update.value = try {
                val r = Updater.latest()
                val mine = Updater.currentVersionCode(getApplication())
                if (r == null || r.versionCode <= mine) UpdateState.UpToDate else UpdateState.Available(r)
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Could not check for updates")
            }
        }
    }

    fun downloadUpdate(r: AppRelease) {
        if (update.value is UpdateState.Downloading) return
        update.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            update.value = try {
                val f = Updater.download(getApplication(), r) { pct -> update.value = UpdateState.Downloading(pct) }
                UpdateState.Ready(f)
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    // ---- Open the King Soopers cart ---------------------------------------------

    /** Set to true to ask the screen to open the King Soopers cart. */
    val openCart = MutableStateFlow(false)

    private fun edit(block: (UserState) -> UserState) {
        viewModelScope.launch { store.update(block) }
    }

    // ---- Week ----------------------------------------------------------------

    /** Adds a meal to the next empty day (or "Extra meals" if the week is full). */
    fun addToWeek(id: Int) = edit { s ->
        if (id in s.week) s else {
            val r = recipes(s)[id]
            val firstSide = r?.sides?.firstOrNull()
            val plan = Logic.dayPlan(s.week, s.days)
            val free = plan.slots.indexOfFirst { it == null }
            s.copy(
                week = s.week + id,
                days = if (free >= 0) plan.asMap() + (id to free) else plan.asMap(),
                sides = if (firstSide != null && id !in s.sides) s.sides + (id to firstSide) else s.sides,
            )
        }
    }

    fun removeFromWeek(id: Int) = edit { s ->
        s.copy(week = s.week - id, sides = s.sides - id, days = Logic.dayPlan(s.week, s.days).asMap() - id)
    }

    /** Moves a meal to a day; if that day already has a meal, the two swap. */
    fun moveToDay(id: Int, day: Int) = edit { s ->
        val plan = Logic.dayPlan(s.week, s.days)
        val occupant = plan.slots.getOrNull(day)
        if (occupant == id || day !in 0..6) s else {
            val m = plan.asMap().toMutableMap()
            val from = m[id]
            m[id] = day
            if (occupant != null) {
                if (from != null) m[occupant] = from else m.remove(occupant)
            }
            s.copy(days = m)
        }
    }

    fun setSide(id: Int, key: String?) = edit { s ->
        s.copy(sides = if (key == null) s.sides - id else s.sides + (id to key))
    }

    /** Saves this week to History, then clears it so you can plan the next one. */
    fun finishWeek() = edit { s ->
        if (s.week.isEmpty() && s.snacks.isEmpty()) s else s.copy(
            history = if (s.week.isEmpty()) s.history else
                listOf(
                    PastWeek(
                        System.currentTimeMillis(),
                        Logic.dayPlan(s.week, s.days).let { p -> p.slots.filterNotNull() + p.extras },
                        s.sides.filterKeys { it in s.week }, s.servings,
                    ),
                ) +
                    s.history.take(99),
            week = emptyList(),
            days = emptyMap(),
            sides = emptyMap(),
            checked = emptySet(),
            lastSnacks = if (s.snacks.isEmpty()) s.lastSnacks else s.snacks,
            snacks = emptySet(),
            lastStaples = if (s.pickedStaples.isEmpty()) s.lastStaples else s.pickedStaples,
            pickedStaples = emptySet(),
            lastExtraItems = if (s.extraItems.isEmpty()) s.lastExtraItems else s.extraItems,
            extraItems = emptyList(),
            haveTerms = emptySet(),
        )
    }

    /** Puts a past week's recipes back into this week (keeps anything already planned). */
    fun repeatWeek(w: PastWeek) = edit { s ->
        val all = recipes(s)
        val add = w.recipes.filter { it in all && it !in s.week }
        s.copy(week = s.week + add, sides = s.sides + w.sides.filterKeys { it in add })
    }

    fun deletePastWeek(w: PastWeek) = edit { s -> s.copy(history = s.history - w) }

    // ---- Cooking timers --------------------------------------------------------

    fun startTimer(key: String, label: String, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        Timers.schedule(getApplication(), key, label, end)
        edit { it.copy(timers = it.timers + (key to end)) }
    }

    fun stopTimer(key: String) {
        Timers.cancel(getApplication(), key)
        edit { it.copy(timers = it.timers - key) }
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
        Logic.shopList(st.week, st.sides, recipes(st), sideMap, st.servings) +
            st.snacks.sorted().map { n ->
                ShopItem("snack:${n.lowercase()}", n, "", SnackCatalog.find(n).qty, EXTRAS)
            } +
            st.staples.filter { it in st.pickedStaples }.map { n ->
                ShopItem("usual:${n.lowercase()}", n, "", 1.0, EXTRAS)
            } +
            st.extraItems.map { n -> ShopItem("extra:${n.lowercase()}", n, "", 1.0, EXTRAS) }

    /** Shopping list grouped by what you'd buy at the store, in store-section order. */
    fun shopGroups(st: UserState = state.value): List<ShopGroup> {
        val all = recipes(st)
        // Which meals need each ingredient (same key format as Logic.shopList).
        val from = mutableMapOf<String, MutableSet<String>>()
        for (id in st.week) {
            val r = all[id] ?: continue
            r.ingredients.forEach { from.getOrPut("${it.name}|${it.u}".lowercase()) { mutableSetOf() } += r.name }
            st.sides[id]?.let { sideMap[it] }?.let { sd ->
                sd.ingredients.forEach { from.getOrPut("${it.name}|${it.u}".lowercase()) { mutableSetOf() } += sd.label.substringAfter(" ") }
            }
        }
        fun sourceOf(i: ShopItem): List<String> = when {
            i.key.startsWith("snack:") -> listOf("Snacks")
            i.key.startsWith("usual:") -> listOf("Usual items")
            i.key.startsWith("extra:") -> listOf("Added")
            else -> from[i.key]?.toList().orEmpty()
        }
        return shopItems(st).groupBy { Logic.searchTerm(it.name) }.map { (term, items) ->
            ShopGroup(term, items, items.first().aisle, items.flatMap { sourceOf(it) }.distinct())
        }.sortedWith(compareBy<ShopGroup>({ Logic.AISLES.indexOf(it.aisle) }, { it.term }))
    }

    fun toggleHave(term: String) = edit { s ->
        s.copy(haveTerms = if (term in s.haveTerms) s.haveTerms - term else s.haveTerms + term)
    }

    fun clearHave() = edit { it.copy(haveTerms = emptySet()) }

    fun addExtraItem(name: String) {
        val n = name.trim().replaceFirstChar { it.uppercase() }
        if (n.isEmpty()) return
        edit { s -> if (s.extraItems.any { it.equals(n, true) }) s else s.copy(extraItems = s.extraItems + n) }
    }

    fun setPickedStaples(picked: Set<String>) = edit { it.copy(pickedStaples = picked) }

    fun toggleStaple(name: String) = edit { s ->
        s.copy(pickedStaples = if (name in s.pickedStaples) s.pickedStaples - name else s.pickedStaples + name)
    }

    fun toggleExtraItem(name: String) = edit { s ->
        if (s.extraItems.any { it.equals(name, true) }) s.copy(extraItems = s.extraItems.filterNot { it.equals(name, true) })
        else s.copy(extraItems = s.extraItems + name)
    }

    // ---- Search-as-you-type for the Add box --------------------------------------

    /** King Soopers products matching what's typed in the Add box. */
    val suggestions = MutableStateFlow<List<Product>>(emptyList())
    private var cachedToken: Pair<String, Long>? = null

    private suspend fun appToken(k: KrogerState): String {
        cachedToken?.let { (t, until) -> if (System.currentTimeMillis() < until) return t }
        val t = api.clientToken(k.clientId, k.clientSecret)
        cachedToken = t to System.currentTimeMillis() + 20 * 60_000L
        return t
    }

    fun searchSuggestions(query: String) {
        val q = query.trim()
        val k = state.value.kroger
        if (q.length < 2 || k.store.id.isEmpty() || k.clientId.isBlank()) {
            suggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            val found = runCatching { api.searchProducts(appToken(k), k.store.id, q, limit = 12) }.getOrDefault(emptyList())
            suggestions.value = found.filter { it.available }.take(8)
        }
    }

    /** Adds a specific King Soopers product to Extras, already picked. */
    fun addExtraProduct(p: Product) {
        val name = p.title.trim()
        if (name.isEmpty()) return
        val term = Logic.searchTerm(name)
        products.update { it + (term to CartLine(term = term, search = name, options = listOf(p), selected = 0, quantity = 1)) }
        edit { s ->
            s.copy(
                extraItems = if (s.extraItems.any { it.equals(name, true) }) s.extraItems else s.extraItems + name,
                kroger = s.kroger.copy(choices = s.kroger.choices + ("term:$term" to Choice(p.upc, p.title, name))),
            )
        }
        suggestions.value = emptyList()
    }

    // ---- Snacks ----------------------------------------------------------------

    fun toggleSnack(name: String) = edit { s ->
        s.copy(snacks = if (name in s.snacks) s.snacks - name else s.snacks + name)
    }

    fun addSnacks(names: Set<String>) = edit { s -> s.copy(snacks = s.snacks + names) }

    fun addCustomSnack(name: String) {
        val n = name.trim().replaceFirstChar { it.uppercase() }
        if (n.isEmpty()) return
        edit { s -> s.copy(customSnacks = (s.customSnacks + n).distinct(), snacks = s.snacks + n) }
    }

    fun removeCustomSnack(name: String) = edit { s ->
        s.copy(customSnacks = s.customSnacks - name, snacks = s.snacks - name, lastSnacks = s.lastSnacks - name)
    }

    // ---- Usual items -----------------------------------------------------------

    fun addStaple(name: String) {
        val n = name.trim().replaceFirstChar { it.uppercase() }
        if (n.isEmpty()) return
        edit { s -> if (s.staples.any { it.equals(n, true) }) s else s.copy(staples = s.staples + n) }
    }

    fun removeStaple(name: String) = edit { s ->
        s.copy(staples = s.staples - name, lastStaples = s.lastStaples - name)
    }

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

    fun findStore(clientId: String, secret: String, zip: String) {
        val k = KrogerState(clientId = clientId.trim(), clientSecret = secret.trim(), zip = zip.trim())
        if (k.clientId.isBlank() || k.clientSecret.isBlank() || k.zip.isBlank()) {
            message.value = "Enter client ID, secret and ZIP first"
            return
        }
        viewModelScope.launch {
            busy.value = true
            try {
                store.update { s ->
                    val old = s.kroger
                    val changedApp = old.clientId != k.clientId
                    s.copy(kroger = old.copy(
                        clientId = k.clientId, clientSecret = k.clientSecret, zip = k.zip,
                        accessToken = if (changedApp) "" else old.accessToken,
                        refreshToken = if (changedApp) "" else old.refreshToken,
                    ))
                }
                val token = api.clientToken(k.clientId, k.clientSecret)
                val found = api.findStore(token, k.zip)
                if (found == null) message.value = "No stores found within 50 miles of ${k.zip}. Check the ZIP code."
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

    /** Looks up King Soopers products for any Shop items that don't have one yet. */
    fun loadProducts() {
        val k = state.value.kroger
        if (k.store.id.isEmpty() || k.clientId.isBlank() || productsLoading.value != null) return
        if (productsStore != k.store.id) {
            products.value = emptyMap()
            productsStore = k.store.id
        }
        val todo = shopGroups().filter { it.term !in products.value }
        if (todo.isEmpty()) return
        productsLoading.value = 0 to todo.size
        viewModelScope.launch {
            var ok = false
            try {
                val token = api.clientToken(k.clientId, k.clientSecret)
                val done = AtomicInteger(0)
                coroutineScope {
                    val gate = Semaphore(4)
                    todo.map { g ->
                        async {
                            gate.withPermit {
                                val line = lookup(token, k, g)
                                products.update { it + (g.term to line) }
                                productsLoading.value = done.incrementAndGet() to todo.size
                            }
                        }
                    }.awaitAll()
                }
                ok = true
            } catch (e: Exception) {
                message.value = e.message ?: "Could not look up King Soopers products"
            } finally {
                productsLoading.value = null
            }
            // Items added while this was running get looked up next.
            if (ok && shopGroups().any { it.term !in products.value }) loadProducts()
        }
    }

    private suspend fun lookup(token: String, k: KrogerState, g: ShopGroup): CartLine {
        val saved = k.choices["term:${g.term}"] ?: g.items.firstNotNullOfOrNull { k.choices[it.key] }
        val search = saved?.search?.ifBlank { null } ?: g.term
        val all = runCatching { api.searchProducts(token, k.store.id, search) }.getOrDefault(emptyList())
        // Leave out anything out of stock, not sold here, or not deliverable.
        val options = all.filter { it.available }
        val remembered = saved?.let { c -> options.indexOfFirst { it.upc == c.upc } } ?: -1
        val note = if (saved != null && remembered < 0 && all.any { it.upc == saved.upc && !it.available }) {
            "Your usual (${saved.label}) isn't available this week"
        } else null
        val needAmount = Logic.needOf(g.items)
        val sel = if (remembered >= 0) remembered else 0
        return CartLine(
            term = g.term, search = search, options = options, selected = sel, needAmount = needAmount,
            note = note, quantity = Logic.suggestQty(needAmount, options.getOrNull(sel)),
        )
    }

    fun updateLine(term: String, block: (CartLine) -> CartLine) {
        products.update { m -> m[term]?.let { m + (term to block(it)) } ?: m }
    }

    fun setQuantity(term: String, q: Int) = updateLine(term) { it.copy(quantity = q.coerceIn(1, 20)) }

    /** Takes an Extras item (snack, usual item or added item) off this week's list. */
    fun removeExtra(g: ShopGroup) = edit { s ->
        var out = s
        g.items.forEach { i ->
            out = when {
                i.key.startsWith("snack:") -> out.copy(snacks = out.snacks.filterNot { it.equals(i.name, true) }.toSet())
                i.key.startsWith("usual:") -> out.copy(pickedStaples = out.pickedStaples.filterNot { it.equals(i.name, true) }.toSet())
                i.key.startsWith("extra:") -> out.copy(extraItems = out.extraItems.filterNot { it.equals(i.name, true) })
                else -> out
            }
        }
        out
    }

    /** Your most-bought extras: how often, with a boost for recent buys. Top 12. */
    fun quickAdd(st: UserState = state.value): List<Pair<String, Bought>> {
        val now = System.currentTimeMillis()
        val day = 86_400_000.0
        return st.bought.entries
            .sortedByDescending { (_, b) -> b.count + 2.0 / (1.0 + (now - b.last) / (7 * day)) }
            .take(12)
            .map { it.key to it.value }
    }

    /** True if this extra is on this week's list (as a snack, usual item or added item). */
    fun onList(st: UserState, name: String): Boolean =
        st.extraItems.any { it.equals(name, true) } || st.snacks.any { it.equals(name, true) } ||
            st.pickedStaples.any { it.equals(name, true) }

    fun toggleQuickAdd(name: String) = edit { s ->
        if (onList(s, name)) s.copy(
            extraItems = s.extraItems.filterNot { it.equals(name, true) },
            snacks = s.snacks.filterNot { it.equals(name, true) }.toSet(),
            pickedStaples = s.pickedStaples.filterNot { it.equals(name, true) }.toSet(),
        ) else s.copy(extraItems = s.extraItems + name)
    }

    fun forgetQuickAdd(term: String, name: String) {
        edit { it.copy(bought = it.bought - term) }
        message.value = "Removed $name from Quick add"
    }

    /** Searches King Soopers again for one item with your own words (e.g. "jasmine rice"). */
    fun researchLine(term: String, query: String) {
        val q = query.trim()
        val k = state.value.kroger
        if (q.isEmpty()) return
        updateLine(term) { it.copy(search = q, searching = true) }
        viewModelScope.launch {
            try {
                val token = api.clientToken(k.clientId, k.clientSecret)
                val options = api.searchProducts(token, k.store.id, q).filter { it.available }
                updateLine(term) {
                    it.copy(
                        options = options, selected = 0, searching = false, note = null,
                        quantity = Logic.suggestQty(it.needAmount, options.firstOrNull()),
                    )
                }
                if (options.isEmpty()) message.value = "Nothing available for delivery for \"$q\""
            } catch (e: Exception) {
                updateLine(term) { it.copy(searching = false) }
                message.value = e.message ?: "Could not search products"
            }
        }
    }

    /** Picks a different product and recalculates how many packages it needs. */
    fun selectProduct(term: String, n: Int) = updateLine(term) {
        it.copy(selected = n, note = null, quantity = Logic.suggestQty(it.needAmount, it.options.getOrNull(n)))
    }

    /** Lines that will go to the cart: not "Already have", with a product found. */
    fun cartLines(st: UserState = state.value): List<CartLine> {
        val m = products.value
        return shopGroups(st).filter { it.term !in st.haveTerms }.mapNotNull { g -> m[g.term]?.takeIf { it.product != null } }
    }

    /** Sends the chosen products to your King Soopers cart. */
    fun confirmAddToCart() {
        val chosen = cartLines()
        if (chosen.isEmpty()) {
            message.value = "Nothing to add yet"
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
                api.addToCart(token, chosen.map { it.product!!.upc to it.quantity })
                store.update { s ->
                    val newChoices = chosen.associate {
                        val p = it.product!!
                        "term:${it.term}" to Choice(p.upc, p.title, if (it.search != it.term) it.search else "")
                    }
                    // Remember the extras you buy, for Quick add.
                    val now = System.currentTimeMillis()
                    val extraTerms = shopGroups(s).filter { it.aisle == EXTRAS }.associateBy { it.term }
                    val bought = s.bought.toMutableMap()
                    chosen.filter { it.term in extraTerms }.forEach { l ->
                        val name = extraTerms.getValue(l.term).items.first().name
                        val old = bought[l.term]
                        bought[l.term] = Bought(name, (old?.count ?: 0) + 1, now)
                    }
                    s.copy(kroger = s.kroger.copy(choices = s.kroger.choices + newChoices), bought = bought)
                }
                message.value = "Added ${chosen.size} items to your King Soopers cart"
                openCart.value = true
            } catch (e: Exception) {
                message.value = e.message ?: "Could not add to cart"
            } finally {
                busy.value = false
            }
        }
    }
}
