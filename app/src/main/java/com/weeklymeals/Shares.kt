package com.weeklymeals

import kotlin.math.ceil

/**
 * Ingredients split across this week's meals (½ lb ground beef Monday, ½ lb Thursday), and
 * fresh things you'll have left over (½ bag of spinach) so Browse can suggest a meal to use them.
 */
data class Share(
    val term: String,
    /** Meals that use it, in the order you'll cook them. */
    val meals: List<Int>,
    /** How much each meal uses, e.g. "½ lb". */
    val amounts: Map<Int, String>,
    /** What you're buying, e.g. "1 lb pack" (null if unknown). */
    val pack: String?,
)

data class Leftover(val term: String, val frac: Double, val label: String)

object Shares {
    fun amountText(items: List<ShopItem>): String =
        items.groupBy { it.unit }.map { (u, l) -> Logic.fmt(l.sumOf { it.qty }) + if (u.isBlank()) "" else " $u" }
            .joinToString(" + ")

    /** "Mon · Chili" (or "Extra · Chili" for a meal without a day). */
    fun mealLabel(st: UserState, id: Int, name: String): String {
        val day = Logic.dayPlan(st.week, st.days).dayOf(id)
        return (if (day != null) Logic.DAY_SHORT[day] else "Extra") + " · " + name
    }

    /** Fresh things that spoil once bought or opened (worth planning a second meal around). */
    private val perishable = Regex(
        "coconut milk|broth|stock|tortilla|\\bbuns?\\b|tomato sauce|salsa|cream|cilantro|parsley|basil|dill|" +
            "spinach|greens|lettuce|cabbage|celery|mushroom|zucchini|pepper|cucumber|herb|ricotta|cheese|yogurt|milk",
    )

    fun isPerishable(g: ShopGroup): Boolean =
        g.aisle == Logic.AISLES[0] || g.aisle == Logic.AISLES[2] || perishable.containsMatchIn(g.term)

    /** Pantry staples (spices, oils, baking) aren't "shared": they live in the Pantry. */
    fun isStaple(term: String): Boolean = Pantry.classify(term)?.first in setOf("spices", "oils", "baking")
}

private var shareMemo: Pair<List<Any?>, Map<String, Share>>? = null
private var leftMemo: Pair<List<Any?>, List<Leftover>>? = null

/** What one planned meal (with its side) needs, by shopping term. */
fun MealsViewModel.mealItems(st: UserState, id: Int): Map<String, List<ShopItem>> =
    Logic.shopList(listOf(id), st.sides.filterKeys { it == id }, recipes(st), sideMap, st.servings)
        .filter { it.aisle != EXTRAS && !it.key.startsWith("side:") }
        .groupBy { Logic.searchTerm(it.name) }

/** Ingredients used by two or more of this week's meals (unless you used it all in one). */
fun MealsViewModel.weekShares(st: UserState): Map<String, Share> {
    val key = listOf(st.week, st.days, st.sides, st.servings, st.custom, st.usedAll, products.value.size, st.packWeights)
    shareMemo?.let { (k, v) -> if (k == key) return v }
    val plan = Logic.dayPlan(st.week, st.days)
    val order = st.week.sortedBy { plan.dayOf(it) ?: 99 }
    val perMeal = order.associateWith { mealItems(st, it) }
    val terms = perMeal.values.flatMap { it.keys }.groupingBy { it }.eachCount().filter { it.value >= 2 }.keys
    val groups by lazy { shopGroups(st).associateBy { it.term } }
    val out = terms.filter { it !in st.usedAll && !Shares.isStaple(it) }.associateWith { term ->
        val meals = order.filter { term in perMeal.getValue(it) }
        val pack = if (Packs.weighed(term)) groups[term]?.let { g -> shopPack(st, g) }?.let { m ->
            if (m.cut != null) "${m.packs * m.perPack} ${Packs.pieceWord(m.cut, m.packs * m.perPack)}"
            else Logic.fmt(m.packs * m.pack.lb) + " lb " + (if (m.packs > 1) "of packs" else "pack")
        } else products.value[term]?.product?.size?.ifBlank { null }
        Share(term, meals, meals.associateWith { Shares.amountText(perMeal.getValue(it).getValue(term)) }, pack)
    }
    shareMemo = key to out
    return out
}

/** Fresh things you'll have extra of after this week's meals (a bag, can or tub only partly used). */
fun MealsViewModel.weekLeftovers(st: UserState): List<Leftover> {
    val key = listOf(st.week, st.sides, st.servings, st.custom, st.haveTerms, st.pantry, products.value.size)
    leftMemo?.let { (k, v) -> if (k == key) return v }
    val out = shopGroups(st).filter { g ->
        g.aisle != EXTRAS && !Packs.weighed(g.term) && !isHave(st, g) && !Shares.isStaple(g.term) && Shares.isPerishable(g)
    }.mapNotNull { g ->
        val size = products.value[g.term]?.product?.size
        val pack = size?.let { Prices.productGrams(it) } ?: Prices.packGrams(g.term + " " + g.items.first().name) ?: return@mapNotNull null
        val need = g.items.mapNotNull { Nutrition.grams(Ingredient(it.qty, it.unit, it.name)) }.sum()
        if (need <= 0 || pack <= 0) return@mapNotNull null
        val packs = ceil(need / pack - 1e-6).coerceAtLeast(1.0)
        val frac = Math.round((packs * pack - need) / pack * 4) / 4.0
        if (frac < 0.25 || frac >= 1.0) null else Leftover(g.term, frac, Logic.fmt(frac))
    }
    leftMemo = key to out
    return out
}

/** True when a meal (not already planned) would use up something left over this week. */
fun MealsViewModel.usesLeftovers(st: UserState, r: Recipe): Boolean {
    if (r.id in st.week) return false
    val left = weekLeftovers(st).map { it.term }.toSet()
    if (left.isEmpty()) return false
    return r.ingredients.any { Logic.searchTerm(it.name) in left }
}
