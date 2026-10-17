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
    /** Terms that are on sale -> the best matching sale product. */
    val items: Map<String, SaleItem> = emptyMap(),
)

object Sales {
    /** Only meals that save at least this much count as "on sale". */
    const val MIN_SAVINGS = 3.0

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
            val s = cache.items[term] ?: return@mapNotNull null
            val packages = Logic.suggestQty(Logic.needOf(group), Product("", s.title, s.size, s.regular))
            Line(term, s, packages, packages * (s.regular - s.promo))
        }.sortedByDescending { it.savings }

    fun savings(r: Recipe, serv: Int, cache: SalesCache): Double = lines(r, serv, cache).sumOf { it.savings }

    /** Every word of the search (e.g. "chicken", "thigh") must appear in the product name. */
    private fun matches(term: String, title: String): Boolean {
        val t = title.lowercase()
        return term.lowercase().split(" ")
            .filter { it.length >= 3 }
            .map { it.trimEnd('s') }
            .all { t.contains(it) }
    }

    /** From search results, the relevant product with the biggest discount (if any). */
    fun bestSale(term: String, products: List<Product>): SaleItem? =
        products.take(15)
            .filter { it.price != null && it.promo != null && it.promo < it.price && matches(term, it.title) }
            .maxByOrNull { it.price!! - it.promo!! }
            ?.let { SaleItem(term, it.title, it.size, it.price!!, it.promo!!) }

    fun money(v: Double): String = "$" + String.format(Locale.US, "%.2f", v)
}
