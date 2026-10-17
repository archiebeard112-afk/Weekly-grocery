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
        "🍿 Snacks",
        "🛒 Other",
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

    private val seasonings = Regex("masala|lemon pepper|paprika|coriander|cumin|turmeric|cayenne|cinnamon|oregano|seasoning|powder|pepper flakes|black pepper|kosher salt|dried|ground ginger")
    private val frozen = Regex("frozen|egg rolls|hash browns|pie crust|edamame")
    private val deli = Regex("\\bham\\b|kielbasa|pepperoni|deli |smoked sausage")
    private val pantryFirst = Regex("broth|stock|soup|peanut butter|coconut|noodles|chips|breadcrumbs|panko|croutons|tuna|sun-dried|crushed|diced|pineapple|pickles|cornbread|sauce|dressing|pesto")
    private val produce = Regex("\\bkale\\b|eggplant|\\bpears?\\b|rosemary|fresh thyme|onion|garlic|bell pepper|jalape|chile|tomato|cucumber|lettuce|greens|spinach|broccoli|carrot|celery|potato|squash|zucchini|mushroom|avocado|lemon|lime|orange|apple|banana|berries|grapes|cilantro|basil|dill|parsley|ginger|asparagus|green beans|corn on the cob|cauliflower|bok choy|cabbage|coleslaw|sprouts|scallion|romaine")
    private val meat = Regex("chicken|beef|pork|sausage|chorizo|bacon|turkey|steak|salmon|shrimp|cod|fish|tenderloin")
    private val dairy = Regex("egg|milk|butter|cream|cheese|mozzarella|cheddar|parmesan|feta|goat|ricotta|yogurt|cotija|swiss")
    private val bakery = Regex("bread|baguette|buns|tortillas|pita|naan|rolls|flatbread|dough|wraps|hoagie")

    fun aisle(name: String): String {
        val n = name.lowercase()
        return when {
            seasonings.containsMatchIn(n) -> AISLES[6]
            frozen.containsMatchIn(n) -> AISLES[4]
            deli.containsMatchIn(n) -> AISLES[1]
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

    // ---- What to search for at the store ------------------------------------

    /** Recipe ingredients you get from buying something else (lemon zest -> lemons). */
    private val buyAs = mapOf(
        "lemon zest" to "lemons", "lemon juice" to "lemons", "lemon" to "lemons",
        "lime zest" to "limes", "lime juice" to "limes", "lime" to "limes",
        "orange zest" to "oranges", "orange juice" to "oranges", "orange" to "oranges",
        "cooked rice" to "long grain white rice", "cooked chicken" to "rotisserie chicken",
        "egg" to "eggs", "large egg" to "eggs", "large eggs" to "eggs",
        "celery stalks" to "celery",
        "scallion" to "green onions", "scallions" to "green onions",
        "garlic" to "garlic bulb",
        "fresh ginger" to "ginger root",
        "tomato" to "tomatoes", "large tomato" to "tomatoes",
        "bell pepper" to "bell peppers",
        "jalapeño" to "jalapeno peppers",
        "fresno chile" to "fresno chili peppers",
        "avocado" to "avocados",
        "cucumber" to "cucumbers",
        "onion" to "yellow onions", "yellow onion" to "yellow onions",
        "small yellow onion" to "yellow onions", "red onion" to "red onions",
    )

    /** Words describing how it's prepped or its size, not what to buy. */
    private val prepWords = Regex(
        "\\b(zest|zested|juiced|chopped|minced|sliced|grated|peeled|cooked|" +
            "drained|rinsed|softened|melted|large|small|medium|for serving|to taste)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Turns a recipe ingredient name into what to look for at King Soopers. */
    fun searchTerm(name: String): String {
        var n = name.lowercase()
            .replace(Regex("\\(.*?\\)"), " ")   // "(drained)"
            .substringBefore(",")                // "lemon, zested"
            .trim()
        if (" or " in n) {                       // "cotija or feta cheese" -> "cotija cheese"
            val first = n.substringBefore(" or ").trim()
            val rest = n.substringAfter(" or ").trim().split(" ")
            n = if (first.contains(" ") || rest.size == 1) first else "$first ${rest.drop(1).joinToString(" ")}"
        }
        buyAs[n]?.let { return it }
        val stripped = n.replace(prepWords, " ").replace(Regex("\\s+"), " ").trim()
        buyAs[stripped]?.let { return it }
        return stripped.ifEmpty { n }
    }

    // ---- How many packages to buy --------------------------------------------

    /** How much the recipes call for, in pounds and/or whole pieces. */
    data class Need(val lb: Double = 0.0, val count: Double = 0.0)

    /** Rough weight of one piece, for recipes that count meat or fish by the piece. */
    private val pieceLb = listOf(
        "pork tenderloin" to 1.25, "tenderloin" to 0.12, "thigh" to 0.3, "drumstick" to 0.25,
        "wing" to 0.12, "breast" to 0.5, "chop" to 0.45, "fillet" to 0.4, "steak" to 0.6,
        "sausage" to 0.25, "bratwurst" to 0.25,
    )

    fun needOf(items: List<ShopItem>): Need {
        var lb = 0.0
        var count = 0.0
        for (i in items) {
            when (i.unit) {
                "lb" -> lb += i.qty
                "oz" -> lb += i.qty / 16.0
                "" -> {
                    val w = pieceLb.firstOrNull { i.name.lowercase().contains(it.first) }?.second
                    if (w != null) lb += i.qty * w else count += i.qty
                }
            }
        }
        return Need(lb, count)
    }

    private val sizeRx = Regex("(\\d+(?:\\.\\d+)?)\\s*(lbs?|oz|ct|count|pk|pack|each|ea)\\b")

    /** Package size from text like "1.5 lb", "16 oz", "3 ct": (amount, isWeightInLb), or null if unknown. */
    fun packAmount(size: String): Pair<Double, Boolean>? {
        val m = sizeRx.find(size.lowercase()) ?: return null
        val v = m.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return when (val u = m.groupValues[2]) {
            "oz" -> v / 16.0 to true
            else -> if (u.startsWith("lb")) v to true else v to false
        }
    }

    /**
     * Number of packages to add, using the product's size ("1.5 lb", "3 ct", "16 oz").
     * 4 chicken thighs (~1.2 lb) with a 1.5 lb pack -> 1. 3 lemons sold each -> 3.
     */
    fun suggestQty(need: Need, p: Product?): Int {
        if (p == null) return 1
        val m = sizeRx.find(p.size.lowercase())
        val v = m?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }
        val unit = m?.groupValues?.get(2).orEmpty()
        val q = when {
            need.lb > 0 && v != null && unit.startsWith("lb") -> need.lb / v
            need.lb > 0 && v != null && unit == "oz" -> need.lb * 16 / v
            need.count > 0 && v != null && unit in setOf("ct", "count", "pk", "pack") -> need.count / v
            need.count > 0 && (v == null || unit in setOf("each", "ea")) -> need.count
            else -> 1.0
        }
        return ceil(q - 0.05).toInt().coerceIn(1, 20)
    }

    // ---- Cooking times -------------------------------------------------------

    /** A time found in a step, e.g. "4-5 min per side" -> 5 minutes, per side. */
    data class StepTime(val minutes: Int, val perSide: Boolean)

    private val timeRx = Regex(
        "(\\d+)(?:\\s*(?:-|–|to)\\s*(\\d+))?\\s*(minutes|minute|mins|min|hours|hour|hrs|hr)\\b(\\s+(?:per|each) side)?",
        RegexOption.IGNORE_CASE,
    )

    fun stepTimes(step: String): List<StepTime> = timeRx.findAll(step).mapNotNull { m ->
        val n = (m.groupValues[2].ifEmpty { m.groupValues[1] }).toIntOrNull() ?: return@mapNotNull null
        val mins = if (m.groupValues[3].lowercase().startsWith("h")) n * 60 else n
        if (mins <= 0) null else StepTime(mins, m.groupValues[4].isNotEmpty())
    }.toList()

    /** Rough total time: prep (based on ingredient count) plus every timed step. */
    fun cookMinutes(r: Recipe): Int {
        val prep = 8 + r.ingredients.size
        val cook = r.steps.sumOf { s ->
            val t = stepTimes(s)
            if (t.isEmpty()) 3 else t.sumOf { if (it.perSide) it.minutes * 2 else it.minutes }
        }
        return ((prep + cook + 4) / 5) * 5
    }

    fun fmtMinutes(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} hr"
        else -> "${m / 60} hr ${m % 60} min"
    }

    fun fmtShort(m: Int): String = when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }

    // ---- Badges --------------------------------------------------------------

    private val BADGES = linkedMapOf(
        "crockpot" to "🍲 Crock-Pot",
        "instantpot" to "⚡ Instapot",
        "airfryer" to "🌬️ Air Fryer",
        "sheetpan" to "♨️ Sheet-Pan",
        "grill" to "🔥 Grill",
        "onepan" to "🍳 One-Pan",
        "budget" to "💲 Budget",
    )

    /** Badge labels for a recipe, cooking method first, then Budget. */
    fun badges(r: Recipe): List<String> = BADGES.filterKeys { it in r.tags }.values.toList()

    fun productLabel(p: Product): String = buildString {
        append(p.title)
        if (p.size.isNotBlank()) append(" · ").append(p.size)
        if (p.price != null) append(" · \$").append(String.format("%.2f", p.price))
        if (p.lowStock) append(" · ⚠️ Low stock")
    }
}
