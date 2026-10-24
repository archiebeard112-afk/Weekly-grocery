package com.weeklymeals

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    private val baseSides: Map<String, Side> = bundle.sides.associateBy { it.key }

    val state: StateFlow<UserState> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserState())

    private var sideCache: Triple<Map<Int, List<StoreSide>>, List<Side>, Map<String, Side>>? = null

    /** All sides: built-in ones, online sides you added ("web:…"), and store-bought ones ("buy:<upc>"). */
    val sideMap: Map<String, Side>
        get() {
            val st = state.value
            val ss = st.storeSides
            val cs = st.customSides
            sideCache?.let { (a, b, v) -> if (a === ss && b === cs) return v }
            val m = baseSides + cs.associateBy { it.key } +
                ss.values.flatten().associate { storeSideKey(it.upc) to storeSide(it) }
            sideCache = Triple(ss, cs, m)
            return m
        }

    /** Recommended sides for a recipe: its usual sides, then ones you've used with it. */
    fun sideOptions(st: UserState, r: Recipe): List<Side> {
        val all = sideMap
        val keys = r.sides + st.recipeSides[r.id].orEmpty() +
            st.storeSides[r.id].orEmpty().map { storeSideKey(it.upc) }
        val skip = st.hiddenSides + st.notForDish[r.id].orEmpty()
        return keys.distinct().filter { it !in skip }.mapNotNull { all[it] }
    }

    /** Every side you can cook (built-in and online ones you've added), A to Z. */
    fun allSides(st: UserState): List<Side> =
        (baseSides.values.filter { it.key !in st.hiddenSides } + st.customSides).sortedBy { it.label.substringAfter(" ").lowercase() }

    /** Adds the meal with this side (or changes its side), and remembers it for the recipe. */
    /** A meal that was just added to the week: the app jumps to Week and highlights it. */
    val justAdded = MutableStateFlow<Int?>(null)

    private fun markAdded(id: Int) {
        if (id !in state.value.week) justAdded.value = id
    }

    fun chooseSide(id: Int, key: String?) = markAdded(id).let { edit { s0 ->
        val r = recipes(s0)[id]
        val s1 = if (key != null && key in s0.notForDish[id].orEmpty())
            s0.copy(notForDish = s0.notForDish + (id to (s0.notForDish[id].orEmpty() - key))) else s0
        val s = if (key != null && r != null && key !in r.sides && !key.startsWith("buy:")) {
            val list = (s1.recipeSides[id].orEmpty() - key + key).takeLast(6)
            s1.copy(recipeSides = s1.recipeSides + (id to list))
        } else s1
        if (id in s.week) s.copy(sides = if (key == null) s.sides - id else s.sides + (id to key))
        else withMeal(s, id, key)
    } }

    fun isBuiltInSide(key: String) = key in baseSides

    /** Calories per serving for a meal: the main, plus its side once it's in your week. */
    fun mealCalories(st: UserState, r: Recipe): Int? {
        val main = Nutrition.perServing(r) ?: return null
        val side = if (r.id in st.week) Nutrition.side(st.sides[r.id]?.let { sideMap[it] }) else null
        return main + (side ?: 0)
    }

    /** Takes a side off one dish's Recommended list (it stays under More sides). */
    fun removeSideFromDish(id: Int, key: String) = edit { s ->
        s.copy(
            notForDish = s.notForDish + (id to (s.notForDish[id].orEmpty() + key)),
            recipeSides = s.recipeSides + (id to (s.recipeSides[id].orEmpty() - key)),
            storeSides = if (key.startsWith("buy:")) s.storeSides + (id to s.storeSides[id].orEmpty().filterNot { storeSideKey(it.upc) == key }) else s.storeSides,
        )
    }

    /** Meals this week using [key] keep their spot but lose the side. */
    private fun dropFromWeek(s: UserState, key: String) = s.copy(sides = s.sides.filterValues { it != key })

    /** Hides a built-in side for every dish (Settings brings it back). */
    fun hideSide(key: String) = edit { s -> dropFromWeek(s, key).copy(hiddenSides = s.hiddenSides + key) }

    fun restoreSide(key: String) = edit { it.copy(hiddenSides = it.hiddenSides - key) }

    /** Deletes a side you added (online or store-bought) for good. */
    fun deleteSide(key: String) = edit { s0 ->
        val s = dropFromWeek(s0, key)
        s.copy(
            customSides = s.customSides.filterNot { it.key == key },
            storeSides = s.storeSides.mapValues { (_, l) -> l.filterNot { storeSideKey(it.upc) == key } },
            recipeSides = s.recipeSides.mapValues { (_, l) -> l - key },
        )
    }

    /** Saves an online recipe as a side dish (amounts set for 4, like built-in sides) and uses it. */
    fun saveWebSide(recipeId: Int, d: WebRecipeDetail) {
        if (d.ingredients.isEmpty() || d.steps.isEmpty()) {
            message.value = "This recipe is missing ingredients or steps"
            return
        }
        val key = "web:${d.id}"
        val side = Side(
            key, "${d.emoji} ${d.title}",
            d.ingredients.map { it.copy(q = it.q * 4.0 / d.servings) }, d.steps,
            rating = d.rating, ratingCount = d.ratingCount, countLabel = d.countLabel, reviews = d.reviews,
        )
        edit { s -> if (s.customSides.any { it.key == key }) s else s.copy(customSides = s.customSides + side) }
        chooseSide(recipeId, key)
        message.value = "Added ${d.title} as a side"
    }

    // Online search inside the "Pick a side" pop-up.
    val sideWeb = MutableStateFlow(WebSearch())
    private var sideJob: kotlinx.coroutines.Job? = null

    fun searchSideWeb(query: String) {
        val q = query.trim().lowercase()
        val st = state.value
        val source = RecipeSources.effective(st)
        sideJob?.cancel()
        if (q.length < 3 || source == RecipeSources.OFF ||
            (source == RecipeSources.SPOONACULAR && st.spoonKey.isBlank())
        ) {
            sideWeb.value = WebSearch(q, source)
            return
        }
        val cacheKey = "$source|$q"
        webCache[cacheKey]?.let { sideWeb.value = WebSearch(q, source, results = it); return }
        sideWeb.value = WebSearch(q, source, loading = true)
        sideJob = viewModelScope.launch {
            sideWeb.value = try {
                val found = RecipeSources.search(source, st.spoonKey, q)
                webCache[cacheKey] = found
                WebSearch(q, source, results = found)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                WebSearch(q, source, error = e.message ?: "Couldn't search online")
            }
        }
    }

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

    /** Browse shows only meals saving at least the sale threshold (Settings) when this is on. */
    val saleOnly = MutableStateFlow(false)

    /** Sets of 10 shown so far (newest last), so swiping right can go back. */
    private val browseSets = mutableListOf<List<Int>>()
    private var browseAt = -1

    init {
        refreshBrowse()
        // Pantry items from before freshness tracking start their clock today.
        viewModelScope.launch {
            store.update { s ->
                // Spice jars say "Best if used by": spices with no date yet default to BIUB.
                fun spiceBiub(p: PantryItem) = p.shelf == "spices" && p.bestBy == 0L && p.dateKind == "best"
                if (s.pantry.none { it.addedAt == 0L || spiceBiub(it) }) s else {
                    val now = System.currentTimeMillis()
                    s.copy(pantry = s.pantry.map {
                        val a = if (it.addedAt == 0L) it.copy(addedAt = now) else it
                        if (spiceBiub(a)) a.copy(dateKind = "biub") else a
                    })
                }
            }
        }
        // One time: seed Quick add with what your receipts show you buy most.
        viewModelScope.launch {
            store.update { s ->
                if (s.receiptsSeeded) s else {
                    // Mountain Dew can't be delivered, so it's not suggested.
                    val bought = s.bought.filterKeys { !it.contains("mountain dew") }.toMutableMap()
                    Receipts.quickAdd.forEach { (name, weeks) ->
                        val term = Logic.searchTerm(name)
                        val old = bought[term]
                        bought[term] = Bought(old?.name ?: name, maxOf(old?.count ?: 0, weeks), maxOf(old?.last ?: 0L, Receipts.LATEST))
                    }
                    s.copy(receiptsSeeded = true, bought = bought)
                }
            }
        }
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

    private var recipeMemo: Triple<List<Recipe>, Set<String>, Map<Int, Recipe>>? = null

    /**
     * All recipes (built-in and yours). Cuts you cook whole become "1 per person" so every
     * screen, the shopping list and cooking show e.g. "2 chicken breasts" when cooking for 2.
     */
    fun recipes(st: UserState = state.value): Map<Int, Recipe> {
        recipeMemo?.let { (c, w, m) -> if (c === st.custom && w == st.wholeCuts) return m }
        val m = (bundle.recipes + st.custom).map { juiceOnly(wholeCuts(it, st.wholeCuts)) }.associateBy { it.id }
        recipeMemo = Triple(st.custom, st.wholeCuts, m)
        return m
    }

    /**
     * A recipe that lists whole limes or lemons but only uses their juice (no zest) needs juice:
     * "1 lime" becomes "2 tbsp lime juice (1 lime)", so a bottle in the fridge covers it.
     */
    private fun juiceOnly(r: Recipe): Recipe {
        val steps = r.steps.joinToString(" ").lowercase()
        var changed = false
        val ings = r.ingredients.map { i ->
            val term = Logic.searchTerm(i.name)
            val fruit = when (term) { "limes" -> "lime"; "lemons" -> "lemon"; else -> null }
            val n = i.name.lowercase()
            if (fruit != null && i.u in setOf("", "whole") && "$fruit juice" in steps && "zest" !in steps && "zest" !in n &&
                "wedge" !in n && "slice" !in n
            ) {
                changed = true
                val perFruit = if (fruit == "lime") 2.0 else 3.0 // tbsp of juice in one
                val count = Logic.fmt(i.q)
                Ingredient(i.q * perFruit, "tbsp", "$fruit juice ($count $fruit${if (i.q > 1) "s" else ""})")
            } else i
        }
        return if (changed) r.copy(ingredients = ings) else r
    }

    private fun wholeCuts(r: Recipe, cuts: Set<String>): Recipe {
        if (cuts.isEmpty()) return r
        var changed = false
        val ings = r.ingredients.map { i ->
            if (i.u in setOf("lb", "oz", "") && Packs.wholeCut(Logic.searchTerm(i.name), cuts) != null) {
                changed = true
                Ingredient(r.base.toDouble(), "", i.name) // scales to 1 per serving
            } else i
        }
        return if (changed) r.copy(ingredients = ings) else r
    }

    fun toggleWholeCut(cut: String) = edit { s ->
        s.copy(wholeCuts = if (cut in s.wholeCuts) s.wholeCuts - cut else s.wholeCuts + cut)
    }

    fun canGoBackBrowse(): Boolean = browseAt > 0

    /** A brand-new set (e.g. after turning Sale on or off), dropping any "forward" sets. */
    private fun newBrowseSet() {
        while (browseSets.size > browseAt + 1) browseSets.removeAt(browseSets.size - 1)
        refreshBrowse()
    }

    /** Next set of 10 (a new one, or forward again after going back). */
    fun refreshBrowse() {
        if (browseAt in 0 until browseSets.size - 1) {
            browseAt++
            browse.value = browseSets[browseAt]
            return
        }
        val set = pickTen()
        browseSets += set
        if (browseSets.size > 30) browseSets.removeAt(0)
        browseAt = browseSets.size - 1
        browse.value = set
    }

    /** Previous set of 10. */
    fun previousBrowse() {
        if (browseAt > 0) {
            browseAt--
            browse.value = browseSets[browseAt]
        }
    }

    /** Main protein, used to mix up each set of 10. */
    private fun protein(r: Recipe): String {
        val text = (r.name + " " + r.ingredients.joinToString(" ") { it.name }).lowercase()
        return listOf(
            "seafood" to listOf("shrimp", "salmon", "cod", "fish", "tilapia", "tuna", "clam"),
            "chicken" to listOf("chicken", "turkey"),
            "beef" to listOf("beef", "steak", "chuck", "sirloin"),
            "pork" to listOf("pork", "sausage", "bacon", "ham", "chorizo", "kielbasa"),
            "pasta" to listOf("pasta", "spaghetti", "penne", "linguine", "macaroni", "ziti", "noodle", "tortellini", "gnocchi"),
        ).firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first ?: "veggie"
    }

    /**
     * Ten ideas: skips meals already planned or made in the last 3 weeks (and the set just shown),
     * spreads across proteins, and leans toward meals on sale when sale prices are loaded.
     */
    private fun pickTen(): List<Int> {
        val st = state.value
        val cache = salesCache(st)
        if (saleOnly.value) {
            val pool = recipes(st).values.filter { it.id !in st.removed && st.ratings[it.id] != -1 && Sales.savings(it, st.servings, cache) >= st.saleMin }.map { it.id }
            if (pool.isEmpty()) message.value = "No meals save ${Sales.money(st.saleMin)} or more at your store today"
            return pool.shuffled().take(10)
        }
        val cutoff = System.currentTimeMillis() - 21L * 86_400_000L
        val recent = st.history.filter { it.date >= cutoff }.flatMap { it.recipes }.toSet()
        val lastShown = browseSets.lastOrNull().orEmpty().toSet()
        // 👎 meals never come up as ideas (you can still search for them); 😐 meals about half as often.
        val all = bundle.recipes.filter {
            !it.imported && it.id !in st.removed && st.ratings[it.id] != -1 && (st.ratings[it.id] != 2 || Math.random() < 0.5)
        }
        var pool = all.filter { it.id !in st.week && it.id !in recent && it.id !in lastShown }
        if (pool.size < 10) pool = all.filter { it.id !in st.week }
        // Within each protein, sale meals tend to come first; then take one from each protein in turn.
        val groups = pool.groupBy { protein(it) }.mapValues { (_, list) ->
            // Decide each meal's coin flip once, then sort (a sort can't ask a random question twice).
            list.shuffled().map { r ->
                // Sale meals and 👍 meals tend to come first.
                r to (if (Sales.savings(r, st.servings, cache) >= st.saleMin && Math.random() < 0.6) 1 else 0) +
                    (if (st.ratings[r.id] == 1 && Math.random() < 0.6) 1 else 0)
            }.sortedByDescending { it.second }.map { it.first }.toMutableList()
        }
        val order = groups.keys.shuffled()
        val picked = mutableListOf<Int>()
        // Up to 3 meals that use up this week's leftovers go in first.
        pool.filter { usesLeftovers(st, it) }.shuffled().take(3).forEach { r ->
            picked += r.id
            groups.values.forEach { g -> g.removeAll { it.id == r.id } }
        }
        while (picked.size < 10 && groups.values.any { it.isNotEmpty() }) {
            for (k in order) {
                val g = groups.getValue(k)
                if (g.isNotEmpty() && picked.size < 10) picked += g.removeAt(0).id
            }
        }
        return picked.shuffled()
    }

    // ---- Sales -------------------------------------------------------------------

    /** (done, total) while sale prices are being checked; null when idle. */
    val salesProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Today's sale prices for your store (empty if stale or another store). */
    fun salesCache(st: UserState = state.value): SalesCache =
        st.sales.takeIf {
            it.day == Sales.today() && it.storeId == st.kroger.store.id && it.version == Sales.CACHE_VERSION
        } ?: SalesCache()

    /** One sale check at a time (the daily one in the background, or one you asked for). */
    private val salesLock = Mutex()
    /** Show the progress screen for a background check too (you asked for sale prices while it ran). */
    @Volatile private var watchSales = false
    @Volatile private var lastProgress: Pair<Int, Int>? = null

    private fun reportSales(p: Pair<Int, Int>, quiet: Boolean) {
        lastProgress = p
        if (!quiet || watchSales) salesProgress.value = p
    }

    private suspend fun fetchSales(terms: Collection<String>, quiet: Boolean = false) = salesLock.withLock {
        fetchSalesLocked(terms, quiet)
    }

    /**
     * Once a day, the first time the app opens: quietly loads today's sale prices for every recipe's
     * main ingredients and saves them for the rest of the day (Browse, recipe pages, On sale use them).
     */
    fun dailySales() {
        viewModelScope.launch {
            try {
                val st = store.flow.first()
                val k = st.kroger
                if (k.store.id.isEmpty() || k.clientId.isBlank()) return@launch
                val terms = recipes(st).values.flatMap { Sales.terms(it, st.servings) }.toSet()
                val cache = salesCache(st)
                if (terms.all { it in cache.checked }) return@launch
                // Wait until the saved settings have loaded into the app's state.
                state.first { it.kroger.store.id == k.store.id && it.kroger.clientId == k.clientId }
                fetchSales(terms, quiet = true)
            } catch (_: Exception) {
                // No internet: try again next time the app opens.
            }
        }
    }

    private suspend fun fetchSalesLocked(terms: Collection<String>, quiet: Boolean) {
        val st = state.value
        val k = st.kroger
        val cache = salesCache(st)
        val todo = terms.filter { it !in cache.checked }.distinct()
        if (todo.isEmpty()) return
        val token = api.clientToken(k.clientId, k.clientSecret)
        val found = ConcurrentHashMap<String, List<SaleItem>>()
        val done = AtomicInteger(0)
        reportSales(0 to todo.size, quiet)
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
                            reportSales(done.incrementAndGet() to todo.size, quiet)
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
            lastProgress = null
            if (!quiet || watchSales) salesProgress.value = null
        }
    }

    /** Before a check you asked for: if the daily one is running, show its progress while you wait. */
    private fun watchRunning() {
        if (salesLock.isLocked) { watchSales = true; lastProgress?.let { salesProgress.value = it } }
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
            newBrowseSet()
            return
        }
        if (!storeReady() || salesProgress.value != null) return
        watchRunning()
        viewModelScope.launch {
            try {
                val st = state.value
                fetchSales(recipes(st).values.flatMap { Sales.terms(it, st.servings) }.toSet())
                saleOnly.value = true
                newBrowseSet()
            } catch (e: Exception) {
                message.value = e.message ?: "Could not check sale prices"
            } finally {
                watchSales = false; salesProgress.value = null
            }
        }
    }

    /** Checks sale prices for just one recipe's main ingredients. */
    fun checkSales(r: Recipe) {
        if (!storeReady() || salesProgress.value != null) return
        watchRunning()
        viewModelScope.launch {
            try {
                fetchSales(Sales.terms(r, state.value.servings))
            } catch (e: Exception) {
                message.value = e.message ?: "Could not check sale prices"
            } finally {
                watchSales = false; salesProgress.value = null
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

    /** A newer version found when the app opened: shows the update pop-up. */
    val autoUpdate = MutableStateFlow<AppRelease?>(null)
    private var lastAutoCheck = 0L

    /** Quietly looks for a newer version (when the app opens, or after 30+ minutes away). */
    fun autoCheck() {
        // Today's sale prices, once a day (does nothing if they're already saved for today).
        dailySales()
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < 30 * 60_000L) return
        if (update.value is UpdateState.Checking || update.value is UpdateState.Downloading) return
        lastAutoCheck = now
        viewModelScope.launch {
            try {
                val r = Updater.latest() ?: return@launch
                val mine = Updater.currentVersionCode(getApplication())
                if (r.versionCode > mine) {
                    update.value = UpdateState.Available(r)
                    if (r.versionCode != store.flow.first().skippedVersion) autoUpdate.value = r
                }
            } catch (_: Exception) {
                // No internet or GitHub unreachable: try again next time.
            }
        }
    }

    fun skipVersion(r: AppRelease) {
        autoUpdate.value = null
        edit { it.copy(skippedVersion = r.versionCode) }
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

    /** A recipe link shared to the app from the browser, waiting to be imported. */
    val sharedLink = MutableStateFlow<String?>(null)

        /** Set to true to ask the screen to open the King Soopers cart. */
    val openCart = MutableStateFlow(false)

    private fun edit(block: (UserState) -> UserState) {
        viewModelScope.launch { store.update(block) }
    }

    // ---- Week ----------------------------------------------------------------

    /** Adds a meal to the next empty day (or "Extra meals" if the week is full). */
    fun addToWeek(id: Int) = addToWeek(id, null, useDefaultSide = true)

    /** Adds a meal with the chosen side (null = no side). */
    fun addToWeek(id: Int, side: String?, useDefaultSide: Boolean = false) {
        markAdded(id)
        edit { s -> withMeal(s, id, side, useDefaultSide) }
    }

    private fun withMeal(s: UserState, id: Int, side: String?, useDefaultSide: Boolean = false): UserState =
        if (id in s.week) s else {
            val r = recipes(s)[id]
            val chosen = if (useDefaultSide) s.sides[id] ?: r?.sides?.firstOrNull() else side
            val plan = Logic.dayPlan(s.week, s.days, s.weekStart)
            val free = Logic.dayOrder(s.weekStart).firstOrNull { plan.slots[it] == null } ?: -1
            s.copy(
                week = s.week + id,
                days = if (free >= 0) plan.asMap() + (id to free) else plan.asMap(),
                sides = if (chosen != null) s.sides + (id to chosen) else s.sides - id,
            )
        }

    /** Adds a meal with a King Soopers product as its side; remembers it for that recipe. */
    fun addToWeekWithProduct(id: Int, p: Product) {
        markAdded(id)
        val title = p.title.trim()
        if (title.isEmpty()) return
        val term = Logic.searchTerm(title)
        products.update { it + (term to CartLine(term = term, search = title, options = listOf(p), selected = 0, quantity = 1)) }
        edit { s0 ->
            val saved = s0.storeSides[id].orEmpty().filterNot { it.upc == p.upc } + StoreSide(p.upc, title)
            val s = s0.copy(
                storeSides = s0.storeSides + (id to saved.takeLast(5)),
                kroger = s0.kroger.copy(choices = s0.kroger.choices + ("term:$term" to Choice(p.upc, title, title))),
            )
            if (id in s.week) s.copy(sides = s.sides + (id to storeSideKey(p.upc)))
            else withMeal(s, id, storeSideKey(p.upc))
        }
        suggestions.value = emptyList()
    }

    // ---- Store-bought meals: a King Soopers product on a day (frozen pizza) -------------

    /** King Soopers products matching the Browse search while its scope is "King Soopers". */
    val storeResults = MutableStateFlow<List<Product>>(emptyList())
    val storeSearching = MutableStateFlow(false)
    private var latestStoreQuery = ""

    fun setBrowseScope(scope: String) = edit { it.copy(browseScope = scope) }

    fun setSharedOpen(open: Boolean) = edit { it.copy(sharedClosed = !open) }

    /** 👍 (1), 😐 (2), 👎 (-1) or skipped (0). Tapping the same thumb again clears it. */
    fun rate(id: Int, v: Int) = edit { s ->
        s.copy(ratings = if (v != 0 && s.ratings[id] == v) s.ratings + (id to 0) else s.ratings + (id to v))
    }

    fun searchStore(query: String) {
        val q = query.trim()
        latestStoreQuery = q
        val k = state.value.kroger
        if (q.length < 2 || k.store.id.isEmpty() || k.clientId.isBlank()) {
            storeResults.value = emptyList()
            storeSearching.value = false
            return
        }
        storeSearching.value = true
        viewModelScope.launch {
            val found = runCatching { api.searchProducts(appToken(k), k.store.id, q, limit = 30) }.getOrDefault(emptyList())
            if (q == latestStoreQuery) {
                storeResults.value = found.filter { it.available }.distinctBy { it.upc }.take(20)
                storeSearching.value = false
            }
        }
    }

    /** Puts a King Soopers product on a day as a meal. Not a recipe: it isn't saved to recipes or favorites. */
    fun addStoreMeal(day: Int, p: Product) {
        val title = p.title.trim()
        if (title.isEmpty() || day !in 0..6) return
        val term = Logic.searchTerm(title)
        val meal = StoreMeal(UUID.randomUUID().toString(), p.upc, title, p.size, p.promo ?: p.price, day, image = p.image)
        // The same product on two days is two in the cart.
        val n = state.value.storeMeals.count { it.upc == p.upc } + 1
        products.update { it + (term to CartLine(term = term, search = title, options = listOf(p), selected = 0, quantity = n)) }
        edit { s ->
            s.copy(
                storeMeals = s.storeMeals + meal,
                recentStoreMeals = (listOf(meal.copy(day = -1)) + s.recentStoreMeals.filterNot { it.upc == p.upc }).take(10),
                kroger = s.kroger.copy(choices = s.kroger.choices + ("term:$term" to Choice(p.upc, title, title))),
            )
        }
        message.value = "Added to ${Logic.DAY_LONG[day]}"
    }

    /** Adds one of the recent store meals again, with today's price (or says it's not available). */
    fun addRecentStoreMeal(day: Int, m: StoreMeal) {
        val k = state.value.kroger
        viewModelScope.launch {
            val fresh = if (k.store.id.isNotEmpty() && k.clientId.isNotBlank())
                runCatching { api.productsById(appToken(k), k.store.id, listOf(m.upc)) }.getOrNull()?.firstOrNull { it.upc == m.upc }
            else null
            if (fresh != null && !fresh.available) {
                message.value = "That's not available at your store right now"
                return@launch
            }
            addStoreMeal(day, fresh ?: Product(m.upc, m.title, m.size, m.price, image = m.image))
        }
    }

    fun removeStoreMeal(id: String) {
        val cur = state.value.storeMeals
        val gone = cur.firstOrNull { it.id == id } ?: return
        val remaining = cur.count { it.upc == gone.upc && it.id != id }
        val term = Logic.searchTerm(gone.title)
        if (remaining > 0) products.update { m -> m[term]?.let { m + (term to it.copy(quantity = remaining)) } ?: m }
        edit { s -> s.copy(storeMeals = s.storeMeals.filterNot { it.id == id }) }
    }

    fun forgetRecentStoreMeal(upc: String) = edit { s -> s.copy(recentStoreMeals = s.recentStoreMeals.filterNot { it.upc == upc }) }

    /** The recipe waiting for a side to be picked before it's added (shows the "Pick a side" pop-up). */
    val sidePicker = MutableStateFlow<Int?>(null)

    /** Add from a tile or recipe page: asks for a side first if the recipe has any. */
    fun requestAdd(id: Int) {
        val r = recipes()[id] ?: return
        if (id in state.value.week) return
        sidePicker.value = id
    }

    /** Change the side on a meal that's already planned (same picker as when adding). */
    fun changeSide(id: Int) { sidePicker.value = id }

    fun removeFromWeek(id: Int) = edit { s ->
        s.copy(week = s.week - id, sides = s.sides - id, days = Logic.dayPlan(s.week, s.days, s.weekStart).asMap() - id)
    }

    /** Moves a meal to a day; if that day already has a meal, the two swap. */
    fun moveToDay(id: Int, day: Int) = edit { s ->
        val plan = Logic.dayPlan(s.week, s.days, s.weekStart)
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
    fun finishWeek() = edit { s0 ->
        val s = if (s0.week.isEmpty()) s0 else s0.copy(pantry = binAfterWeek(s0, usedUp(s0)), freezer = freezerAfterWeek(s0))
        if (s.week.isEmpty() && s.snacks.isEmpty() && s.storeMeals.isEmpty()) s else s.copy(
            history = if (s.week.isEmpty() && s.storeMeals.isEmpty()) s.history else
                listOf(
                    PastWeek(
                        System.currentTimeMillis(),
                        Logic.dayPlan(s.week, s.days, s.weekStart).let { p -> Logic.dayOrder(s.weekStart).mapNotNull { p.slots[it] } + p.extras },
                        s.sides.filterKeys { it in s.week }, s.servings,
                        storeMeals = s.storeMeals.sortedBy { Logic.dayRank(it.day, s.weekStart) }.map { Logic.DAY_SHORT.getOrElse(it.day) { "" } + " · " + it.title },
                    ),
                ) +
                    s.history.take(99),
            storeMeals = emptyList(),
            week = emptyList(),
            usedAll = emptyMap(),
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
            pantryBuy = emptySet(),
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

    // ---- Cooking bubble ---------------------------------------------------------

    /** Opens cooking mode for a recipe (a different recipe replaces the one you were cooking). */
    fun startCooking(id: Int) = edit { s ->
        val now = System.currentTimeMillis()
        if (s.cookingId == id) s.copy(cookingSince = now)
        else s.copy(cookingId = id, cookingSince = now, cookScroll = 0, cookDone = emptySet())
    }

    /** Remembers your spot when you leave the cook screen. */
    fun saveCookPlace(id: Int, scroll: Int, done: Set<Int>) = edit { s ->
        if (s.cookingId == id) s.copy(cookScroll = scroll, cookDone = done) else s
    }

    fun toggleShelf(key: String) = edit { s ->
        s.copy(openShelves = if (key in s.openShelves) s.openShelves - key else s.openShelves + key)
    }

    /** Set when the floating cooking bubble is tapped: open the cook screen. */
    val openCook = MutableStateFlow(false)

    fun setOverlayAsked() = edit { it.copy(overlayAsked = true) }

    /** "Used it all": the other meals that shared this get their own (back on the Shop list). */
    fun useAll(term: String, id: Int) = edit { it.copy(usedAll = it.usedAll + (term to id)) }

    fun undoUseAll(term: String) = edit { it.copy(usedAll = it.usedAll - term) }

    fun stopCooking() = edit { it.copy(cookingId = null, cookScroll = 0, cookDone = emptySet()) }

    fun setBubblePos(x: Float, y: Float) = edit { it.copy(bubbleX = x, bubbleY = y) }

    fun stopTimer(key: String) {
        Timers.cancel(getApplication(), key)
        edit { it.copy(timers = it.timers - key) }
    }

    fun setServings(n: Int) = edit { it.copy(servings = n.coerceIn(1, 12)) }

    fun setWeekStart(day: Int) = edit { it.copy(weekStart = day.coerceIn(0, 6)) }

    // ---- Favorites -----------------------------------------------------------

    fun addFav(id: Int) = edit { it.copy(favs = it.favs + id, hidden = it.hidden - id) }

    /** Call only after the "are you sure?" dialog is confirmed. */
    fun removeFav(id: Int) = edit { it.copy(favs = it.favs - id, hidden = it.hidden + id) }

    // ---- Online recipes (Spoonacular) --------------------------------------------

    /** Online search results for the Browse search box. */
    data class WebSearch(
        val query: String = "",
        val source: String = "",
        val loading: Boolean = false,
        val results: List<WebRecipe> = emptyList(),
        val error: String? = null,
    )

    val webSearch = MutableStateFlow(WebSearch())
    private val webCache = mutableMapOf<String, List<WebRecipe>>()
    private val detailCache = mutableMapOf<String, WebRecipeDetail>()
    private var webJob: kotlinx.coroutines.Job? = null

    fun setSpoonKey(key: String) {
        webCache.clear()
        edit { it.copy(spoonKey = key.trim()) }
        message.value = "Saved"
    }

    fun setWebSource(source: String) = edit { it.copy(webSource = source) }

    fun setWebSort(sort: String) = edit { it.copy(webSort = sort) }

    /** If a search page didn't show ratings, read them from each recipe page (also makes previews instant). */
    private suspend fun fillRatings(q: String, source: String, cacheKey: String, found: List<WebRecipe>) {
        if (source != RecipeSources.ALLRECIPES || found.any { it.rating != null }) return
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        kotlinx.coroutines.coroutineScope {
            found.forEach { w ->
                launch {
                    val d = gate.withPermit {
                        detailCache[w.id] ?: runCatching { RecipeSources.details(w.id, "") }.getOrNull()?.also { detailCache[w.id] = it }
                    } ?: return@launch
                    val cur = webSearch.value
                    if (cur.query != q || cur.source != source) return@launch
                    val updated = cur.results.map {
                        if (it.id == w.id) it.copy(rating = d.rating, ratingCount = d.ratingCount, minutes = it.minutes ?: d.minutes) else it
                    }
                    webCache[cacheKey] = updated
                    webSearch.value = cur.copy(results = updated)
                }
            }
        }
    }

    fun searchWeb(query: String) {
        val q = query.trim().lowercase()
        val st = state.value
        val source = RecipeSources.effective(st)
        webJob?.cancel()
        if (q.length < 3 || source == RecipeSources.OFF ||
            (source == RecipeSources.SPOONACULAR && st.spoonKey.isBlank())
        ) {
            webSearch.value = WebSearch(q, source)
            return
        }
        val cacheKey = "$source|$q"
        webCache[cacheKey]?.let { webSearch.value = WebSearch(q, source, results = it); return }
        webSearch.value = WebSearch(q, source, loading = true)
        webJob = viewModelScope.launch {
            webSearch.value = try {
                val found = RecipeSources.search(source, st.spoonKey, q)
                webCache[cacheKey] = found
                WebSearch(q, source, results = found).also {
                    webSearch.value = it
                    fillRatings(q, source, cacheKey, found)
                }.let { webSearch.value }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                WebSearch(q, source, error = e.message ?: "Couldn't search online")
            }
        }
    }

    /** The online recipe being previewed (null = loading or none). */
    val webPreview = MutableStateFlow<WebRecipeDetail?>(null)
    val webPreviewError = MutableStateFlow<String?>(null)

    fun openWebRecipe(id: String) {
        webPreviewError.value = null
        detailCache[id]?.let { webPreview.value = it; return }
        webPreview.value = null
        viewModelScope.launch {
            try {
                val d = RecipeSources.details(id, state.value.spoonKey)
                detailCache[id] = d
                webPreview.value = d
            } catch (e: Exception) {
                webPreviewError.value = e.message ?: "Couldn't load this recipe"
            }
        }
    }

    /** The saved recipe for an online result, if you've already added it. */
    fun savedWebRecipe(st: UserState, ref: String): Recipe? = st.custom.firstOrNull { it.webRef == ref }

    /** Saves an approved online recipe to your recipes (it shows in Favorites). Returns its new id. */
    fun saveWebRecipe(d: WebRecipeDetail): Int? {
        if (d.ingredients.isEmpty() || d.steps.isEmpty()) {
            message.value = "This recipe is missing ingredients or steps"
            return null
        }
        savedWebRecipe(state.value, d.id)?.let { return it.id }
        val st = state.value
        val newId = (recipes(st).keys.maxOrNull() ?: 99).coerceAtLeast(99) + 1
        val r = Recipe(
            id = newId, name = d.title, emoji = d.emoji, base = d.servings,
            steps = d.steps, ingredients = d.ingredients,
            sides = bundle.sides.take(4).map { it.key }, imported = true,
            tags = d.tags, webRef = d.id,
            rating = d.rating, ratingCount = d.ratingCount, countLabel = d.countLabel, reviews = d.reviews,
            calories = d.calories,
        )
        edit { s -> if (s.custom.any { it.webRef == d.id }) s else s.copy(custom = s.custom + r) }
        message.value = "Added to Favorites"
        return newId
    }

    /**
     * Hold-to-delete: recipes you added are deleted for good; built-in ones are hidden
     * (Settings → Hidden recipes brings them back). Either way it comes off this week.
     */
    fun deleteRecipe(r: Recipe) {
        edit { s ->
            val base = s.copy(
                week = s.week - r.id,
                days = s.days - r.id,
                sides = s.sides - r.id,
                favs = s.favs - r.id,
                storeSides = s.storeSides - r.id,
            )
            if (s.custom.any { it.id == r.id }) base.copy(
                custom = s.custom.filterNot { it.id == r.id },
                deletedNames = s.deletedNames + (r.id to "${r.emoji} ${r.name}"),
            ) else base.copy(removed = s.removed + r.id)
        }
        browse.value = browse.value - r.id
        for (i in browseSets.indices) browseSets[i] = browseSets[i] - r.id
        message.value = if (r.imported) "Deleted ${r.name}" else "Hid ${r.name}"
    }

    fun restoreRecipe(id: Int) = edit { it.copy(removed = it.removed - id) }

    private val reviewTried = mutableSetOf<Int>()

    /** Recipes saved from online before reviews were kept: fetch their rating and reviews once. */
    fun fetchReviewsIfMissing(r: Recipe) {
        if (r.webRef.isEmpty() || r.rating != null || r.reviews.isNotEmpty() || !reviewTried.add(r.id)) return
        viewModelScope.launch {
            val d = runCatching { RecipeSources.details(r.webRef, state.value.spoonKey) }.getOrNull() ?: return@launch
            if (d.rating == null && d.reviews.isEmpty()) return@launch
            edit { s ->
                s.copy(custom = s.custom.map {
                    if (it.id == r.id) it.copy(
                        rating = d.rating, ratingCount = d.ratingCount, countLabel = d.countLabel,
                        reviews = d.reviews, calories = d.calories ?: it.calories,
                    ) else it
                })
            }
        }
    }

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
            // A shared ingredient you used all of in one meal: buy it again for the others ("… #2").
            st.usedAll.flatMap { (term, used) ->
                st.week.filter { it != used }.flatMap { id -> mealItems(st, id)[term].orEmpty() }
                    .mapIndexed { n, i -> ShopItem("rebuy:$term|$n", "$term #2", i.unit, i.qty, Logic.aisle(term)) }
            } +
            st.snacks.sorted().map { n ->
                ShopItem("snack:${n.lowercase()}", n, "", SnackCatalog.find(n).qty, EXTRAS)
            } +
            st.staples.filter { it in st.pickedStaples }.map { n ->
                ShopItem("usual:${n.lowercase()}", n, "", 1.0, EXTRAS)
            } +
            st.extraItems.map { n -> ShopItem("extra:${n.lowercase()}", n, "", 1.0, EXTRAS) } +
            // Store-bought sides: one of the product per meal it goes with.
            st.week.mapNotNull { id -> st.sides[id]?.takeIf { it.startsWith("buy:") } }
                .groupingBy { it }.eachCount()
                .mapNotNull { (key, n) ->
                    sideMap[key]?.let { sd ->
                        val title = storeSideTitle(sd)
                        ShopItem("side:${key.removePrefix("buy:")}", title, "", n.toDouble(), Logic.aisle(title))
                    }
                } +
            // Store-bought meals (frozen pizza on Wednesday): one of the product per day it's planned.
            st.storeMeals.groupBy { it.upc }.map { (upc, list) ->
                ShopItem("meal:$upc", list.first().title, "", list.size.toDouble(), Logic.aisle(list.first().title))
            }

    /** Shopping list grouped by what you'd buy at the store, in store-section order. */
    fun shopGroups(st: UserState = state.value): List<ShopGroup> {
        val all = recipes(st)
        // Which meals need each ingredient (same key format as Logic.shopList).
        val from = mutableMapOf<String, MutableSet<String>>()
        for (id in st.week) {
            val r = all[id] ?: continue
            r.ingredients.forEach { from.getOrPut("${it.name}|${it.u}".lowercase()) { mutableSetOf() } += r.name }
            st.sides[id]?.takeIf { it.startsWith("buy:") }?.let { key ->
                from.getOrPut("side:${key.removePrefix("buy:")}") { mutableSetOf() } += "for ${r.name}"
            }
            st.sides[id]?.let { sideMap[it] }?.let { sd ->
                sd.ingredients.forEach { from.getOrPut("${it.name}|${it.u}".lowercase()) { mutableSetOf() } += sd.label.substringAfter(" ") }
            }
        }
        for (m in st.storeMeals) {
            from.getOrPut("meal:${m.upc}") { mutableSetOf() } += "Store meal · " + Logic.DAY_SHORT.getOrElse(m.day) { "" }
        }
        fun sourceOf(i: ShopItem): List<String> = when {
            i.key.startsWith("snack:") -> listOf("Snacks")
            i.key.startsWith("usual:") -> listOf("Usual items")
            i.key.startsWith("extra:") -> listOf("Added")
            i.key.startsWith("rebuy:") -> listOf("Again (used it all earlier)")
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
        // Your pick, or else the product your receipts show you usually buy.
        val saved = k.choices["term:${g.term}"] ?: g.items.firstNotNullOfOrNull { k.choices[it.key] } ?: Receipts.usual(g.term)
        val search = saved?.search?.ifBlank { null } ?: Logic.baseExtra(g.term)
        val all = runCatching { api.searchProducts(token, k.store.id, search) }.getOrDefault(emptyList())
        // Leave out anything out of stock, not sold here, or not deliverable.
        val options = all.filter { it.available }
        val remembered = saved?.let { c -> options.indexOfFirst { it.upc == c.upc } } ?: -1
        val note = if (saved != null && remembered < 0 && all.any { it.upc == saved.upc && !it.available }) {
            "Your usual (${saved.label}) isn't available this week"
        } else null
        val needAmount = Logic.needOf(g.items)
        val sel = if (remembered >= 0) remembered else 0
        // Store-bought meals and sides are exact products: one per day or meal, whatever the package size says.
        val exact = g.items.isNotEmpty() && g.items.all { it.key.startsWith("meal:") || it.key.startsWith("side:") }
        return CartLine(
            term = g.term, search = search, options = options, selected = sel, needAmount = needAmount,
            note = note,
            quantity = if (exact) g.items.sumOf { it.qty }.toInt().coerceAtLeast(1) else Logic.suggestQty(needAmount, options.getOrNull(sel)),
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

    /**
     * Quick add: each tap adds another one as its own row ("Kool-Aid #2", "#3"…), so you can
     * pick a different product (flavor) for each. Remove extras from the list itself.
     */
    fun toggleQuickAdd(name: String) = edit { s ->
        var n = name
        var k = 2
        while (s.extraItems.any { it.equals(n, true) }) n = "$name #${k++}"
        s.copy(extraItems = s.extraItems + n)
    }

    /** How many rows of this Quick add item are on the list. */
    fun quickCount(st: UserState, name: String): Int =
        st.extraItems.count { Logic.baseExtra(it).equals(name, true) }

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
    // ---- Pantry -------------------------------------------------------------------

    /** Every ingredient name the recipes use, for matching pantry products ("thyme"). */
    private val knownTerms: Set<String> by lazy {
        (bundle.recipes.flatMap { it.ingredients } + bundle.sides.flatMap { it.ingredients })
            .map { Logic.searchTerm(it.name) }.toSet()
    }

    fun pantryItem(st: UserState, term: String): PantryItem? =
        st.pantry.firstOrNull { it.term == term } ?: st.pantry.firstOrNull { Pantry.covers(it, term) }

    /** How much of a shopping-list group the week needs, in the pantry item's measure (null = can't tell). */
    private fun need(item: PantryItem, g: ShopGroup): Double? {
        var total = 0.0
        for (i in g.items) total += Pantry.usage(item, i.qty, i.unit) ?: return null
        return total
    }

    enum class PantryStatus { COVERED, LOW, EXPIRED }

    /** Past its date and you haven't said you're fine using it. */
    fun isExpired(item: PantryItem): Boolean =
        !item.useAnyway && Pantry.freshness(item) == Pantry.Fresh.EXPIRED

    /** Ingredients on a recipe page that are past their date in the pantry: term -> label. */
    fun expiredTerms(st: UserState): Map<String, String> =
        st.pantry.filter { isExpired(it) }.flatMap { p ->
            val label = if (Pantry.isSpice(p)) "may be weak" else "check date"
            listOf(p.term to label, Logic.searchTerm(p.term) to label)
        }.toMap()

    /** Keep using an item past its date (or stop). */
    fun setUseAnyway(term: String, on: Boolean) = edit { s ->
        s.copy(pantry = s.pantry.map { if (Pantry.covers(it, term)) it.copy(useAnyway = on) else it })
    }

    /** Whether the pantry covers this item for the week, is short, or doesn't have it (null). */
    fun pantryStatus(st: UserState, g: ShopGroup): PantryStatus? {
        if (g.aisle == EXTRAS) return null
        val item = pantryItem(st, g.term) ?: return null
        if (isExpired(item)) return PantryStatus.EXPIRED
        val n = need(item, g)
        val enough = if (n != null && item.measure != "") item.left + 1e-6 >= n else !Pantry.isLow(item)
        return if (enough) PantryStatus.COVERED else PantryStatus.LOW
    }

    /** Goes in "Already have": you said so, or the pantry has enough (unless you chose to buy it anyway). */
    fun isHave(st: UserState, g: ShopGroup): Boolean =
        g.term in st.haveTerms || (Pantry.alwaysHave(g.term) && g.term !in st.pantryBuy) ||
            (g.term !in st.pantryBuy && (pantryStatus(st, g) == PantryStatus.COVERED || freezerCovers(st, g)))

    fun togglePantryBuy(term: String) = edit { s ->
        s.copy(pantryBuy = if (term in s.pantryBuy) s.pantryBuy - term else s.pantryBuy + term)
    }

    /** A new pantry item from a product name and size (full, on its guessed shelf). */
    fun pantryDraft(title: String, size: String = "", upc: String = "", shelf: String? = null): PantryItem {
        val term = Pantry.termFor(title, knownTerms)
        val (guessShelf, kind) = Pantry.classify(term + " " + title) ?: ("dry" to "box")
        val (measure, full) = Pantry.measureFor(term + " " + title, kind, size)
        return PantryItem(
            id = java.util.UUID.randomUUID().toString(), name = title.trim(), term = term,
            shelf = shelf ?: guessShelf, kind = kind, upc = upc, size = size,
            measure = measure, full = full, left = full, addedAt = System.currentTimeMillis(),
            dateKind = if ((shelf ?: guessShelf) == "spices") "biub" else "best",
        )
    }

    /** Adds or updates a pantry item. */
    fun savePantryItem(item: PantryItem) = edit { s ->
        val exists = s.pantry.any { it.id == item.id }
        val old = s.pantry.firstOrNull { it.id == item.id }
        var saved = if (item.addedAt == 0L) item.copy(addedAt = System.currentTimeMillis()) else item
        // A new date (or "Just bought") means it's fresh again: stop the "use anyway".
        if (old != null && (old.bestBy != saved.bestBy || old.dateKind != saved.dateKind || old.addedAt != saved.addedAt)) {
            saved = saved.copy(useAnyway = false)
        }
        s.copy(pantry = if (exists) s.pantry.map { if (it.id == item.id) saved else it } else s.pantry + saved)
    }

    fun removePantryItem(id: String) = edit { s -> s.copy(pantry = s.pantry.filterNot { it.id == id }) }

    /** What the week's meals used, taken out of the pantry (on Finish week). */
    private fun usedUp(s: UserState): List<PantryItem> {
        val used = Logic.shopList(s.week, s.sides, recipes(s), sideMap, s.servings)
            .groupBy { Logic.searchTerm(it.name) }
        return s.pantry.map { found ->
            val items = used[found.term] ?: used[Logic.searchTerm(found.term)] ?: return@map found
            // A plain jar (level only) starts being measured once its size can be turned into teaspoons.
            val item = if (found.measure != "") found else {
                val (m, full) = Pantry.measureFor(found.term + " " + found.name, found.kind, found.size, found.cupOz)
                if (m == Pantry.TSP) found.copy(measure = m, full = full, left = found.level * full) else return@map found
            }
            var total = 0.0
            for (i in items) total += Pantry.usage(item, i.qty, i.unit) ?: 0.0
            item.copy(left = (item.left - total).coerceAtLeast(0.0))
        }
    }

    /**
     * Finish week: what's left of bagged produce (potatoes), butter and eggs goes in the Kitchen —
     * the package size from King Soopers (5 lb, 4 sticks, 12 eggs) minus what the meals used,
     * if at least ½ lb, ¼ stick or 1 egg is left.
     */
    private fun binAfterWeek(s: UserState, pantry: List<PantryItem>): List<PantryItem> {
        val out = pantry.toMutableList()
        val now = System.currentTimeMillis()
        for (g in shopGroups(s)) {
            val butter = Pantry.isButter(g.term)
            val egg = Pantry.isEgg(g.term)
            if (!(Pantry.isBinProduce(g.term) || butter || egg) || g.term in s.haveTerms) continue
            val probe = PantryItem("", g.term, g.term, if (butter || egg) "fridge" else "produce", if (butter || egg) "box" else "bag",
                measure = if (egg) Pantry.EGG else if (butter) Pantry.STICK else Pantry.LB)
            val need = g.items.sumOf { Pantry.usage(probe, it.qty, it.unit) ?: 0.0 }
            if (need <= 0) continue
            val before = s.pantry.firstOrNull { it.term == g.term }?.left ?: 0.0
            val buy = need - before
            if (buy <= 1e-6) continue // the bin covered it (already taken out above)
            val size = products.value[g.term]?.product?.size.orEmpty()
            val bag = when {
                egg -> Pantry.sizeEggs(size) ?: 12.0
                butter -> Pantry.sizeSticks(size) ?: 4.0
                else -> Pantry.sizeLb(size) ?: 5.0
            }
            val bags = kotlin.math.ceil(buy / bag - 1e-6).coerceAtLeast(1.0)
            // Eggs are whole; everything else rounds to quarters.
            val leftover = if (egg) Math.floor(bags * bag - buy + 1e-6) else Math.round((bags * bag - buy) * 4) / 4.0
            // Potatoes under ½ lb aren't worth logging; butter keeps quarter sticks; eggs need at least 1.
            if (leftover < (if (egg) 1.0 else if (butter) 0.25 else 0.5)) continue
            val i = out.indexOfFirst { it.term == g.term }
            if (i >= 0) {
                val cur = out[i]
                val left = cur.left + leftover
                out[i] = cur.copy(left = left, full = maxOf(cur.full, bag, left), addedAt = now)
            } else {
                out += PantryItem(
                    java.util.UUID.randomUUID().toString(), g.term.replaceFirstChar { it.uppercase() }, g.term,
                    shelf = probe.shelf, kind = probe.kind, measure = probe.measure, full = bag, left = leftover, addedAt = now,
                )
            }
        }
        return out
    }

    /** Pantry items bought through the cart are refilled; new pantry-type items are added. */
    private fun refilled(pantry: List<PantryItem>, lines: List<CartLine>): List<PantryItem> {
        val out = pantry.toMutableList()
        for (l in lines) {
            val p = l.product ?: continue
            // Potatoes and butter are logged on Finish week instead (what's left of the package).
            if (Pantry.loggedLeftovers(l.term + " " + p.title)) continue
            val i = out.indexOfFirst { it.term == l.term || (p.upc.isNotEmpty() && it.upc == p.upc) }
            if (i >= 0) {
                val cur = out[i]
                out[i] = if (cur.measure == "") {
                    cur.copy(left = cur.full, upc = p.upc, size = p.size.ifBlank { cur.size }, addedAt = System.currentTimeMillis(), bestBy = 0L, useAnyway = false)
                } else {
                    val (m, pack) = Pantry.measureFor(cur.term + " " + p.title, cur.kind, p.size, cur.cupOz)
                    val add = (if (m == cur.measure) pack else cur.full) * l.quantity
                    val left = cur.left + add
                    cur.copy(
                        left = left, full = maxOf(cur.full, left), upc = p.upc, size = p.size.ifBlank { cur.size },
                        addedAt = System.currentTimeMillis(), bestBy = 0L,
                    )
                }
            } else if (Pantry.classify(l.term + " " + p.title) != null) {
                val d = pantryDraft(p.title, p.size, p.upc)
                out += d.copy(term = l.term, left = d.full * l.quantity, full = d.full * maxOf(1, l.quantity))
            }
        }
        return out
    }

    /** Looks up a scanned barcode: King Soopers first, then Open Food Facts. */
    suspend fun lookupBarcode(code: String): Product? {
        val digits = code.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val k = state.value.kroger
        // Kroger product ids are the UPC without its check digit, padded to 13.
        val ids = listOf(digits.dropLast(1).padStart(13, '0'), digits.padStart(13, '0')).distinct()
        if (k.clientId.isNotBlank() && k.store.id.isNotEmpty()) {
            runCatching { api.productsById(appToken(k), k.store.id, ids) }.getOrNull()?.firstOrNull()?.let { return it }
        }
        return runCatching { OpenFoodFacts.lookup(digits) }.getOrNull()
    }

    // ---- Backup -------------------------------------------------------------------

    /** Everything in the app as one file (sign-in tokens left out). */
    fun backupText(): String {
        val s = state.value
        return store.toJson(
            s.copy(
                kroger = s.kroger.copy(accessToken = "", refreshToken = "", expiresAt = 0L, pendingState = ""),
                timers = emptyMap(),
            ),
        )
    }

    /** Replaces everything with a backup file. Returns false if it isn't a backup. */
    fun restoreBackup(text: String): Boolean {
        val restored = runCatching { store.fromJson(text) }.getOrNull() ?: return false
        viewModelScope.launch { store.update { restored } }
        message.value = "Backup restored"
        return true
    }

    fun setSaleMin(v: Double) = edit { it.copy(saleMin = v) }

    fun setBudgetMax(v: Double) = edit { it.copy(budgetMax = v) }

    /** What a meal costs: [total] to buy, and [saved] by what you already have (pantry, Already have). */
    /** One shopping item's price for a meal; [have] = you already have it; [note] e.g. "≈3 lb pack". */
    data class PriceLine(val cost: Double, val have: Boolean, val note: String = "", val zone: String? = null)

    /** Where you keep what covers a shopping item: "freezer", "fridge", "produce" or "pantry" (null if you don't have it). */
    fun haveZone(st: UserState, term: String): String? = when {
        freezerFor(st, term) != null -> "freezer"
        else -> pantryItem(st, term)?.let { zoneOf(it.shelf) }
    }

    data class MealCost(
        val total: Double,
        val saved: Double,
        /** Price per shopping item (by store search term). */
        val lines: Map<String, PriceLine> = emptyMap(),
    )

    private val costCache = HashMap<List<Any?>, MealCost?>()

    /** A meat pack worked out for a shopping item: how many packs, what's left, and the price. */
    data class MeatPack(
        val pack: Packs.Pack, val packs: Int, val cost: Double, val leftoverLb: Double, val key: String,
        /** For cuts cooked whole: pieces per pack, whether you set it, and pieces left over. */
        val perPack: Int = 0, val perPackYours: Boolean = false, val leftoverPieces: Int = 0, val cut: String? = null,
    )

    /**
     * Whole-pack price for meat sold by the pound, for [needLb]. Uses the sale price, then the
     * King Soopers product, then a typical price. Null if this isn't meat sold by weight.
     */
    fun meatPack(st: UserState, term: String, needLb: Double, product: Product?, sale: SaleItem?, pieces: Double = 0.0): MeatPack? {
        val cut = Packs.wholeCut(term, st.wholeCuts)
        if (cut != null && pieces > 0) return piecePack(st, term, cut, pieces, product, sale)
        if (needLb <= 0 || !Packs.weighed(term)) return null
        val title = sale?.title ?: product?.title
        val size = sale?.size ?: product?.size
        val upc = product?.upc.orEmpty()
        val key = if (upc.isNotEmpty()) upc else "term:$term"
        val yours = st.packWeights[key] ?: st.packWeights["term:$term"]
        val listed = Packs.packFor(term, title, size, yours)
        var price = sale?.promo ?: product?.let { it.promo ?: it.price } ?: Prices.perLb(term) ?: return null
        val ground = Packs.isGround(term)
        // Ground meat: 1 lb packs (unless you set a size). A bigger listed pack is priced per pound.
        val pack = if (ground && yours == null) {
            if (!listed.perLb && listed.lb > 1.05) price /= listed.lb
            Packs.Pack(1.0, true, listed.source)
        } else listed
        // Typical prices are per pound, so a fixed-size pack still costs weight × price.
        val p = if (sale == null && product == null) pack.copy(perLb = true) else pack
        val (packs, cost) = Packs.cost(needLb, price, p)
        // Ground meat is used up completely: no leftovers.
        val leftover = if (ground) 0.0 else (packs * p.lb - needLb).coerceAtLeast(0.0)
        return MeatPack(p, packs, cost, leftover, key)
    }

    /** Whole cuts: how many packs for [pieces], priced by the pack. */
    private fun piecePack(st: UserState, term: String, cut: String, pieces: Double, product: Product?, sale: SaleItem?): MeatPack? {
        val title = sale?.title ?: product?.title
        val size = sale?.size ?: product?.size
        val upc = product?.upc.orEmpty()
        val key = if (upc.isNotEmpty()) upc else "term:$term"
        val pack = Packs.packFor(term, title, size, st.packWeights[key] ?: st.packWeights["term:$term"])
        val yoursCount = st.packCounts[key] ?: st.packCounts["term:$term"]
        val perPack = yoursCount ?: Math.round(pack.lb / Packs.pieceLb(cut)).toInt().coerceAtLeast(1)
        val price = sale?.promo ?: product?.let { it.promo ?: it.price } ?: Prices.perLb(term) ?: return null
        val p = if (sale == null && product == null) pack.copy(perLb = true) else pack
        val need = kotlin.math.ceil(pieces - 1e-6).toInt().coerceAtLeast(1)
        val packs = ((need + perPack - 1) / perPack).coerceAtLeast(1)
        val cost = packs * (if (p.perLb) p.lb * price else price)
        return MeatPack(p, packs, cost, 0.0, key, perPack, yoursCount != null, packs * perPack - need, cut)
    }

    fun setPackCount(key: String, n: Int?) = edit { s ->
        s.copy(packCounts = if (n == null || n <= 0) s.packCounts - key else s.packCounts + (key to n))
    }

    // ---- Freezer -------------------------------------------------------------------

    /** How much of a shopping item the week needs: pieces for whole cuts, else pounds. */
    fun meatNeed(st: UserState, term: String, items: List<ShopItem>): Double =
        if (Packs.wholeCut(term, st.wholeCuts) != null) items.filter { it.unit == "" }.sumOf { it.qty }
        else Logic.needOf(items).lb

    fun freezerFor(st: UserState, term: String): FreezerItem? = st.freezer.firstOrNull { it.term == term }

    /** True when the freezer has all the week needs of this item. */
    fun freezerCovers(st: UserState, g: ShopGroup): Boolean {
        val f = freezerFor(st, g.term) ?: return false
        val need = meatNeed(st, g.term, g.items)
        return need > 0 && f.amount() + 1e-6 >= need
    }

    fun saveFreezerItem(item: FreezerItem) = edit { s ->
        val exists = s.freezer.any { it.id == item.id }
        s.copy(freezer = if (exists) s.freezer.map { if (it.id == item.id) item else it } else s.freezer + item)
    }

    fun removeFreezerItem(id: String) = edit { s -> s.copy(freezer = s.freezer.filterNot { it.id == id }) }

    /**
     * Finish week: take what the meals used out of the freezer, then put leftover meat in:
     * every leftover piece for cuts cooked whole, or more than 1 lb for meat by weight.
     */
    private fun freezerAfterWeek(s: UserState): List<FreezerItem> {
        val out = s.freezer.toMutableList()
        val now = System.currentTimeMillis()
        val sales = salesCache(s)
        for (g in shopGroups(s)) {
            if (!Packs.weighed(g.term) || g.term in s.haveTerms) continue
            val cut = Packs.wholeCut(g.term, s.wholeCuts)
            val need = meatNeed(s, g.term, g.items)
            if (need <= 0) continue
            val i = out.indexOfFirst { it.term == g.term }
            val have = if (i >= 0) out[i].amount() else 0.0
            val used = minOf(have, need)
            if (i >= 0) {
                val left = have - used
                out[i] = if (out[i].byCount) out[i].copy(count = Math.round(left).toInt()) else out[i].copy(lb = left)
            }
            val buy = need - used
            if (buy <= 1e-6) continue
            val sale = sales.options[g.term]?.minByOrNull { it.promo }
            val m = meatPack(s, g.term, if (cut == null) buy else 0.0, products.value[g.term]?.product, sale, if (cut != null) buy else 0.0) ?: continue
            val extra = if (cut != null) m.leftoverPieces.toDouble() else m.leftoverLb
            val keep = if (cut != null) extra >= 1 else extra > 1.0
            if (!keep) continue
            val j = out.indexOfFirst { it.term == g.term }
            if (j >= 0) {
                val f = out[j]
                out[j] = if (f.byCount) f.copy(count = f.count + Math.round(extra).toInt())
                else f.copy(lb = f.lb + extra)
            } else {
                out += FreezerItem(
                    java.util.UUID.randomUUID().toString(), g.term, g.term.replaceFirstChar { it.uppercase() },
                    count = if (cut != null) Math.round(extra).toInt() else 0,
                    lb = if (cut == null) Math.round(extra * 4) / 4.0 else 0.0,
                    frozenAt = now, byCount = cut != null,
                )
            }
        }
        return out.filter { it.amount() > 0.01 }
    }

    fun setPackWeight(key: String, lb: Double?) = edit { s ->
        s.copy(packWeights = if (lb == null || lb <= 0) s.packWeights - key else s.packWeights + (key to lb))
    }

    /** Cost to make a meal for your Cooking for number, with its side once it's in your week. */
    fun mealCost(st: UserState, r: Recipe): MealCost? {
        val side = if (r.id in st.week) st.sides[r.id] else null
        val prods = products.value
        val sales = salesCache(st)
        val key = listOf(
            r.id, r.ingredients.hashCode(), side, st.servings, st.pantry, st.haveTerms, prods.size,
            sales.day, sales.options.size, st.packWeights, if (r.id in st.week) st.week else null, st.sides,
            st.freezer, st.wholeCuts, st.packCounts, st.usedAll, st.days, st.pantryBuy,
        )
        if (costCache.size > 2000) costCache.clear()
        return costCache.getOrPut(key) {
            val items = Logic.shopList(listOf(r.id), side?.let { mapOf(r.id to it) } ?: emptyMap(), mapOf(r.id to r), sideMap, st.servings)
            // Meat in planned meals shares packs across the week, so we need the week's totals too.
            val weekItems = if (r.id in st.week) Logic.shopList(st.week, st.sides, recipes(st), sideMap, st.servings)
                .groupBy { Logic.searchTerm(it.name) } else emptyMap()
            var total = 0.0
            var saved = 0.0
            var known = 0
            val lines = mutableMapOf<String, PriceLine>()
            // Sale prices found for "On sale this week" win over everything else.
            val onSale = Sales.lines(r, st.servings, sales).associateBy { it.term }
            // Shared with an earlier meal this week (or left over from one): already paid for.
            val shares = if (r.id in st.week) weekShares(st) else emptyMap()
            val leftover = if (r.id !in st.week) weekLeftovers(st).map { it.term }.toSet() else emptySet()
            items.groupBy { Logic.searchTerm(it.name) }.forEach { (term, list) ->
                val sale = onSale[term]?.sale
                val cut = Packs.wholeCut(term, st.wholeCuts)
                val need = meatNeed(st, term, list) // pieces for whole cuts, else lb
                var fromFreezer = false
                val meat = if (need > 0 && Packs.weighed(term)) {
                    val weekNeed = weekItems[term]?.let { meatNeed(st, term, it) }?.takeIf { it > need } ?: need
                    val frozen = freezerFor(st, term)?.amount() ?: 0.0
                    val buy = (weekNeed - frozen).coerceAtLeast(0.0)
                    fromFreezer = buy <= 1e-6
                    // Price what you'd still buy (or, if the freezer covers it, what it would have cost).
                    val amount = if (fromFreezer) weekNeed else buy
                    meatPack(st, term, if (cut == null) amount else 0.0, prods[term]?.product, sale, if (cut != null) amount else 0.0)?.let { m ->
                        val note = when {
                            fromFreezer -> "from the freezer"
                            else -> (if (weekNeed > need) "share of " else "") + Packs.label(m.pack) +
                                (m.cut?.let { " · ${m.perPack} ${Packs.pieceWord(it, m.perPack)}" } ?: "") +
                                (if (frozen > 0) " · rest from freezer" else "")
                        }
                        // This meal's share of the pack(s) bought for the week; from the freezer, just what it uses.
                        val held = m.packs * (if (m.cut != null) m.perPack.toDouble() else m.pack.lb)
                        Triple(if (fromFreezer && held > 0) m.cost * (need / held).coerceAtMost(1.0) else m.cost * need / weekNeed, note, m)
                    }
                } else null
                var c = meat?.first
                    ?: onSale[term]?.let { it.sale.promo * it.packages }
                    ?: Prices.cost(term + " " + list.first().name, list, prods[term]?.product)
                    ?: return@forEach
                var reuseNote = ""
                if (meat == null) {
                    val sh = shares[term]
                    val weekList = weekItems[term]
                    if (sh != null && weekList != null && sh.meals.size > 1) {
                        // Shared this week: the package(s) bought for the week, split by how much each meal uses.
                        val name = term + " " + list.first().name
                        val mealBase = Prices.cost(name, list, prods[term]?.product)
                        val weekBase = Prices.cost(name, weekList, prods[term]?.product)
                        val weekCost = if (mealBase != null && weekBase != null && mealBase > 0) c * weekBase / mealBase else c
                        c = weekCost * shareOf(list, weekList, sh.meals.size)
                        val size = sh.pack ?: Prices.packGrams(name)?.let { "${Logic.fmt(Math.round(it / 28.35).toDouble())} oz" }
                        reuseNote = "share of " + (size?.let { "$it pkg" } ?: "package")
                    } else if (term in leftover) {
                        c = 0.0
                        reuseNote = "♻️ left over this week"
                    } else if (Pantry.keepsMonths(term + " " + list.first().name)) {
                        // Keeps for months (spices, rice, oil): this meal costs only the part it uses.
                        c = Prices.usedValue(term + " " + list.first().name, list, prods[term]?.product, c)
                    }
                }
                known++
                // Planned meals: the Kitchen has to cover the whole week's amount, same as the Shop list
                // (2 lb of potatoes left doesn't cover this meal if another meal needs them too).
                val g = ShopGroup(term, weekItems[term] ?: list, list.first().aisle, emptyList())
                // A spice past its date is still yours (just "may be weak").
                val weakSpice = pantryItem(st, term)?.let { Pantry.isSpice(it) && isExpired(it) && term !in st.pantryBuy } == true
                val have = isHave(st, g) || fromFreezer || weakSpice
                // What you have saves only what this meal uses of it (½ tsp of thyme, not the jar).
                if (have) saved += if (fromFreezer) c else Prices.usedValue(term + " " + list.first().name, list, prods[term]?.product, c)
                else total += c
                lines[term] = PriceLine(c, have, meat?.second ?: reuseNote, if (fromFreezer) "freezer" else haveZone(st, term))
            }
            if (known == 0) null else MealCost(total, saved, lines)
        }
    }

    /** This meal's part of the week's amount (by weight when it can tell, else an even split). */
    private fun shareOf(meal: List<ShopItem>, week: List<ShopItem>, meals: Int): Double {
        fun grams(l: List<ShopItem>) = l.mapNotNull { Nutrition.grams(Ingredient(it.qty, it.unit, it.name)) }.sum()
        val m = grams(meal)
        val w = grams(week)
        if (m > 0 && w > 0) return (m / w).coerceIn(0.0, 1.0)
        val mq = meal.sumOf { it.qty }
        val wq = week.sumOf { it.qty }
        if (mq > 0 && wq > 0 && meal.all { it.unit == meal.first().unit } && week.all { it.unit == meal.first().unit }) return (mq / wq).coerceIn(0.0, 1.0)
        return 1.0 / meals
    }

    /** Pack details for a meat item on the Shop list: what's still to buy after the freezer. */
    fun shopPack(st: UserState, g: ShopGroup): MeatPack? {
        val cut = Packs.wholeCut(g.term, st.wholeCuts)
        val need = meatNeed(st, g.term, g.items)
        val buy = (need - (freezerFor(st, g.term)?.amount() ?: 0.0)).coerceAtLeast(0.0)
        if (buy <= 1e-6) return null
        val sale = salesCache(st).options[g.term]?.minByOrNull { it.promo }
        return meatPack(st, g.term, if (cut == null) buy else 0.0, products.value[g.term]?.product, sale, if (cut != null) buy else 0.0)
    }

    fun isBudget(st: UserState, r: Recipe): Boolean = mealCost(st, r)?.let { it.total <= st.budgetMax } ?: false

    fun cartLines(st: UserState = state.value): List<CartLine> {
        val m = products.value
        return shopGroups(st).filter { !isHave(st, it) }.mapNotNull { g -> m[g.term]?.takeIf { it.product != null } }
    }

    /** Cart lines that would also top up (or start) a Kitchen item; the pop-up on "Add to cart" lists these. */
    fun kitchenCandidates(st: UserState = state.value): List<CartLine> = cartLines(st).filter { l ->
        val p = l.product ?: return@filter false
        !Pantry.loggedLeftovers(l.term + " " + p.title) &&
            (st.pantry.any { it.term == l.term || (p.upc.isNotEmpty() && it.upc == p.upc) } ||
                Pantry.classify(l.term + " " + p.title) != null)
    }

    private fun inKitchen(s: UserState, l: CartLine): Boolean {
        val upc = l.product?.upc.orEmpty()
        return s.pantry.any { it.term == l.term || (upc.isNotEmpty() && it.upc == upc) }
    }

    /** Of these cart lines, the ones you skipped before and don't already have in the Kitchen: they start unticked. */
    fun kitchenSkipped(st: UserState, lines: List<CartLine>): Set<String> =
        lines.filter { it.term in st.skipKitchen && !inKitchen(st, it) }.map { it.term }.toSet()

    /** Remembers what you left out of the Kitchen (items it doesn't have yet) and forgets the ones you ticked. */
    fun rememberKitchenChoices(shown: List<CartLine>, off: Set<String>) = edit { s ->
        val newlySkipped = shown.filter { it.term in off && !inKitchen(s, it) }.map { it.term }
        s.copy(skipKitchen = (s.skipKitchen - shown.map { it.term }.toSet()) + newlySkipped)
    }

    /** Sends the chosen products to your King Soopers cart; [kitchenTerms] are the ones to also put in the Kitchen. */
    fun confirmAddToCart(kitchenTerms: Set<String> = emptySet()) {
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
                    // Extra rows of the same thing ("… #2") count toward the one Quick add item.
                    chosen.filter { it.term in extraTerms }.map { l ->
                        Logic.baseExtra(extraTerms.getValue(l.term).items.first().name)
                    }.distinctBy { it.lowercase() }.forEach { name ->
                        val term = Logic.searchTerm(name)
                        val old = bought[term]
                        bought[term] = Bought(old?.name ?: name, (old?.count ?: 0) + 1, now)
                    }
                    s.copy(
                        kroger = s.kroger.copy(choices = s.kroger.choices + newChoices), bought = bought,
                        pantry = refilled(s.pantry, chosen.filter { it.term in kitchenTerms }),
                    )
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

// ---- Store-bought sides ------------------------------------------------------------

fun storeSideKey(upc: String) = "buy:$upc"

/** A King Soopers product as a side: no ingredients or steps; it's bought ready to go. */
fun storeSide(s: StoreSide) = Side(storeSideKey(s.upc), "🛒 ${s.title}", emptyList(), emptyList())

fun storeSideTitle(sd: Side) = sd.label.removePrefix("🛒 ")

fun isStoreSide(sd: Side) = sd.key.startsWith("buy:")
