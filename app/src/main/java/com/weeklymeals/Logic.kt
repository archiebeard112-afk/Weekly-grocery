package com.weeklymeals

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class ShopItem(
    val key: String,
    val name: String,
    val unit: String,
    val qty: Double,
    val aisle: String,
)

/** Pure logic: portion scaling, merging the shopping list, store sections, typing in recipes. */
object Logic {

    val AISLES = listOf(
        "🥬 Produce",
        "🥩 Meat & Seafood",
        "🧀 Dairy & Eggs",
        "🍞 Bakery",
        "🧊 Frozen",
        "🥫 Pantry",
        "🧂 Seasonings & Spices",
    )

    /** Things you buy whole (cans, cloves, a bag...) round up to whole numbers. */
    private val COUNT_UNITS = setOf("", "can", "bag", "bunch", "clove", "slices", "jar", "pkg", "box")

    fun isFav(r: Recipe, st: UserState): Boolean =
        r.id !in st.hidden && (r.imported || r.id in st.favs)

    fun scale(i: Ingredient, serv: Int, base: Int): Ingredient {
        var q = i.q * serv / base
        q = if (i.u in COUNT_UNITS) {
            max(1.0, ceil(q - 0.01))
        } else {
            max(0.25, Math.round(q * 4) / 4.0)
        }
        return i.copy(q = q)
    }

    fun fmt(n: Double): String {
        val w = floor(n + 1e-9).toInt()
        val f = Math.round((n - w) * 4) / 4.0
        val frac = when (f) {
            0.25 -> "¼"
            0.5 -> "½"
            0.75 -> "¾"
            else -> ""
        }
        val whole = w + (if (f >= 1.0) 1 else 0)
        val s = (if (whole > 0) whole.toString() else "") + frac
        return s.ifEmpty { "0" }
    }

    fun ingText(i: Ingredient, serv: Int, base: Int): String {
        val s = scale(i, serv, base)
        return listOf(fmt(s.q), s.u, s.name).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun itemLabel(i: ShopItem): String =
        listOf(fmt(i.qty), i.unit, i.name).filter { it.isNotBlank() }.joinToString(" ")

    fun needText(i: ShopItem): String =
        listOf(fmt(i.qty), i.unit).filter { it.isNotBlank() }.joinToString(" ")

    // ---- Store sections ------------------------------------------------------

    private val seasonings = Regex("paprika|coriander|cumin|turmeric|cayenne|cinnamon|oregano|seasoning|powder|pepper flakes|black pepper|kosher salt|dried|ground ginger")
    private val frozen = Regex("frozen|egg rolls|hash browns|pie crust|edamame")
    private val pantryFirst = Regex("broth|stock|soup|peanut butter|coconut|noodles|chips|breadcrumbs|panko|croutons|tuna|sun-dried|crushed|diced|pineapple|pickles|cornbread|sauce|dressing|pesto")
    private val produce = Regex("onion|garlic|bell pepper|jalape|chile|tomato|cucumber|lettuce|greens|spinach|broccoli|carrot|celery|potato|squash|zucchini|mushroom|avocado|lemon|lime|orange|apple|banana|berries|grapes|cilantro|basil|dill|parsley|ginger|asparagus|green beans|corn on the cob|cauliflower|bok choy|cabbage|coleslaw|sprouts|scallion|romaine")
    private val meat = Regex("chicken|beef|pork|sausage|chorizo|bacon|turkey|steak|salmon|shrimp|cod|fish|tenderloin")
    private val dairy = Regex("egg|milk|butter|cream|cheese|mozzarella|cheddar|parmesan|feta|goat|ricotta|yogurt|cotija|swiss")
    private val bakery = Regex("bread|baguette|buns|tortillas|pita|naan|rolls|flatbread|dough|wraps|hoagie")

    fun aisle(name: String): String {
        val n = name.lowercase()
        return when {
            seasonings.containsMatchIn(n) -> AISLES[6]
            frozen.containsMatchIn(n) -> AISLES[4]
            pantryFirst.containsMatchIn(n) -> AISLES[5]
            produce.containsMatchIn(n) -> AISLES[0]
            meat.containsMatchIn(n) -> AISLES[1]
            dairy.containsMatchIn(n) -> AISLES[2]
            bakery.containsMatchIn(n) -> AISLES[3]
            else -> AISLES[5]
        }
    }

    // ---- Shopping list -------------------------------------------------------

    fun shopList(
        week: List<Int>,
        sides: Map<Int, String>,
        recipes: Map<Int, Recipe>,
        sideMap: Map<String, Side>,
        serv: Int,
    ): List<ShopItem> {
        val merged = LinkedHashMap<String, ShopItem>()
        for (id in week) {
            val r = recipes[id] ?: continue
            val sideIngredients = sides[id]?.let { sideMap[it] }?.ingredients ?: emptyList()
            val all = r.ingredients.map { scale(it, serv, r.base) } +
                sideIngredients.map { scale(it, serv, 4) }
            for (i in all) {
                val key = "${i.name}|${i.u}".lowercase()
                val cur = merged[key]
                merged[key] = if (cur == null) {
                    ShopItem(key, i.name, i.u, i.q, aisle(i.name))
                } else {
                    cur.copy(qty = cur.qty + i.q)
                }
            }
        }
        return merged.values.sortedWith(
            compareBy<ShopItem>({ AISLES.indexOf(it.aisle) }, { it.name.lowercase() })
        )
    }

    // ---- Typing in your own recipe ------------------------------------------

    private val unitMap = mapOf(
        "cup" to "cup", "cups" to "cup", "c" to "cup",
        "tbsp" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp",
        "tsp" to "tsp", "teaspoon" to "tsp", "teaspoons" to "tsp",
        "lb" to "lb", "lbs" to "lb", "pound" to "lb", "pounds" to "lb",
        "oz" to "oz", "ounce" to "oz", "ounces" to "oz",
        "can" to "can", "cans" to "can",
        "clove" to "clove", "cloves" to "clove",
        "slice" to "slices", "slices" to "slices",
        "bunch" to "bunch",
        "jar" to "jar", "jars" to "jar",
        "pkg" to "pkg", "package" to "pkg", "packages" to "pkg",
        "box" to "box", "bag" to "bag", "bags" to "bag",
    )

    private fun num(t: String): Double? {
        val d = t.toDoubleOrNull()
        if (d != null) return d
        if ('/' in t) {
            val p = t.split("/")
            if (p.size == 2) {
                val a = p[0].toDoubleOrNull()
                val b = p[1].toDoubleOrNull()
                if (a != null && b != null && b != 0.0) return a / b
            }
        }
        return when (t) {
            "¼" -> 0.25
            "½" -> 0.5
            "¾" -> 0.75
            else -> null
        }
    }

    /** "1 1/2 cup flour" -> 1.5 cup Flour. "3 eggs" -> 3 Eggs. "salt" -> Salt. */
    fun parseIngredient(line: String): Ingredient? {
        val t = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (t.isEmpty()) return null
        var q = 0.0
        var u = ""
        val first = num(t[0])
        if (first != null) {
            q = first
            t.removeAt(0)
            if (t.isNotEmpty()) {
                val f = num(t[0])
                if (f != null && f < 1.0) {
                    q += f
                    t.removeAt(0)
                }
            }
        }
        if (q > 0 && t.size > 1) {
            val mapped = unitMap[t[0].lowercase().trimEnd('.')]
            if (mapped != null) {
                u = mapped
                t.removeAt(0)
            }
        }
        if (t.isEmpty()) return null
        val name = t.joinToString(" ").replaceFirstChar { it.uppercase() }
        return Ingredient(q, u, name)
    }

    fun productLabel(p: Product): String = buildString {
        append(p.title)
        if (p.size.isNotBlank()) append(" · ").append(p.size)
        if (p.price != null) append(" · \$").append(String.format("%.2f", p.price))
    }
}
