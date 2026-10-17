package com.weeklymeals

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One main ingredient that's on sale at your store today. */
@Serializable
data class SaleItem(
    val term: String,
    val title: String,
    val size: String,
    val regular: Double,
    val promo: Double,
)

/** Sale prices looked up today, so they only need fetching once a day. */
@Serializable
data class SalesCache(
    val day: String = "",
    val storeId: String = "",
    /** Search terms already checked today (on sale or not). */
    val checked: Set<String> = emptySet(),
    /** Older format (one product per term); no longer used. */
    val items: Map<String, SaleItem> = emptyMap(),
    /** Terms that are on sale -> every matching sale package (different sizes). */
    val options: Map<String, List<SaleItem>> = emptyMap(),
    val version: Int = 0,
)

object Sales {
    /** Only meals that save at least this much count as "on sale". */
    const val MIN_SAVINGS = 3.0

    /** Sale packs more than this many times what a recipe needs are ignored. */
    const val MAX_PACK_RATIO = 2.0

    /** Bump when the saved format changes, so old saved sales are re-checked. */
    const val CACHE_VERSION = 2

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** The costly ingredients: meat and seafood, plus anything bought by the pound or more. */
    private fun isMain(i: ShopItem): Boolean =
        i.aisle == Logic.AISLES[1] ||
            (i.unit == "lb" && i.qty >= 1.0) ||
            (i.unit == "oz" && i.qty >= 16.0)

    private fun mainItems(r: Recipe, serv: Int): List<ShopItem> =
        Logic.shopList(listOf(r.id), emptyMap(), mapOf(r.id to r), emptyMap(), serv).filter { isMain(it) }

    /** Store search terms for a recipe's costly ingredients. */
    fun terms(r: Recipe, serv: Int): List<String> =
        mainItems(r, serv).map { Logic.searchTerm(it.name) }.distinct()

    /** True once every main ingredient of this recipe has been checked today. */
    fun isChecked(r: Recipe, serv: Int, cache: SalesCache): Boolean =
        terms(r, serv).all { it in cache.checked }

    data class Line(val term: String, val sale: SaleItem, val packages: Int, val savings: Double)

    /** What's on sale in this recipe and how much it saves for the amount needed. */
    fun lines(r: Recipe, serv: Int, cache: SalesCache): List<Line> =
        mainItems(r, serv).groupBy { Logic.searchTerm(it.name) }.mapNotNull { (term, group) ->
            val need = Logic.needOf(group)
            cache.options[term].orEmpty()
                .mapNotNull { s -> fit(need, s) }
                // closest to what you need first, then the bigger saving
                .sortedWith(compareBy<Pair<Line, Double>> { it.second }.thenByDescending { it.first.savings })
                .firstOrNull()?.first?.copy(term = term)
        }.sortedByDescending { it.savings }

    /**
     * How a sale pack fits the amount a recipe needs, or null if the pack is
     * more than double what you need. Returns the line and the leftover amount.
     */
    private fun fit(need: Logic.Need, s: SaleItem): Pair<Line, Double>? {
        val product = Product("", s.title, s.size, s.regular)
        val packages = Logic.suggestQty(need, product)
        val pack = Logic.packAmount(s.size)
        val wanted = when {
            pack == null -> null
            pack.second && need.lb > 0 -> need.lb
            !pack.second && need.count > 0 -> need.count
            else -> null
        }
        if (pack != null && wanted != null && pack.first > wanted * MAX_PACK_RATIO) return null
        val leftover = if (pack != null && wanted != null) packages * pack.first - wanted else 0.0
        return Line(s.term, s, packages, packages * (s.regular - s.promo)) to leftover
    }

    fun savings(r: Recipe, serv: Int, cache: SalesCache): Double = lines(r, serv, cache).sumOf { it.savings }

    /** Every word of the search (e.g. "chicken", "thigh") must appear in the product name. */
    private fun matches(term: String, title: String): Boolean {
        val t = title.lowercase()
        return term.lowercase().split(" ")
            .filter { it.length >= 3 }
            .map { it.trimEnd('s') }
            .all { t.contains(it) }
    }

    /** From search results, every relevant product that's on sale (one per size). */
    fun saleOptions(term: String, products: List<Product>): List<SaleItem> =
        products.take(15)
            .filter { it.price != null && it.promo != null && it.promo < it.price && matches(term, it.title) }
            .map { SaleItem(term, it.title, it.size, it.price!!, it.promo!!) }
            .distinctBy { it.title + "|" + it.size }

    fun money(v: Double): String = "$" + String.format(Locale.US, "%.2f", v)
}
