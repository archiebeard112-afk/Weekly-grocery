package com.weeklymeals

/**
 * Pantry rules: which shelf and container an item gets, how big a package is,
 * and how much of it a week's recipes use.
 *
 * Amounts are tracked in teaspoons where possible. Recipes use spoons and cups but jars
 * are sold by weight, so common items have a "teaspoons per ounce" figure. Anything we
 * can't measure is tracked as a simple level you set (Full / Half / Low / Out).
 */
object Pantry {
    const val TSP = "tsp"
    const val COUNT = "count"
    const val LB = "lb"

    /** Produce kept in the Produce bin (bagged, keeps a few weeks). Just potatoes for now. */
    fun isBinProduce(name: String): Boolean {
        val n = name.lowercase()
        return Regex("\\bpotato").containsMatchIn(n) && !n.contains("sweet potato") && !n.contains("chip")
    }

    /** Pounds in a bag size like "5 lb" or "80 oz" (null if it doesn't say). */
    fun sizeLb(size: String): Double? = parseSize(size)?.let { (n, u) ->
        when (u) { "lb", "lbs" -> n; "oz" -> n / 16; "kg" -> n * 2.2046; "g" -> n / 453.6; else -> null }
    }

    val SHELVES = listOf(
        "spices" to "Spices", "oils" to "Oils and sauces", "baking" to "Baking", "dry" to "Canned and dry",
        "produce" to "Produce bin",
    )
    val KINDS = listOf("jar" to "Jar", "bottle" to "Bottle", "bag" to "Bag", "can" to "Can", "box" to "Box")

    fun shelfLabel(key: String) = SHELVES.firstOrNull { it.first == key }?.second ?: "Canned and dry"

    private val leafy = listOf(
        "italian seasoning", "herbes de provence", "thyme", "oregano", "basil", "rosemary", "parsley", "dill",
        "sage", "marjoram", "tarragon", "chives", "bay leaves", "bay leaf",
    )
    private val ground = listOf(
        "smoked paprika", "paprika", "cumin", "chili powder", "cinnamon", "garlic powder", "onion powder",
        "ground ginger", "turmeric", "curry powder", "coriander", "cayenne", "nutmeg", "black pepper",
        "red pepper flakes", "crushed red pepper", "mustard powder", "dry mustard", "allspice", "ground cloves",
        "cajun seasoning", "taco seasoning", "garlic salt", "celery salt", "onion salt", "old bay", "chipotle powder",
        "five spice", "garam masala", "everything bagel", "lemon pepper", "seasoning", "pepper",
    )

    /** Teaspoons per ounce (by weight, or fl oz for liquids). Checked in order, first match wins. */
    private val tspPerOz: List<Pair<String, Double>> =
        leafy.map { it to 30.0 } + listOf(
            "kosher salt" to 9.0, "sea salt" to 5.0, "salt" to 5.0,
        ) + ground.map { it to 12.0 } + listOf(
            "brown sugar" to 6.4, "powdered sugar" to 13.0, "sugar" to 7.0,
            "flour" to 11.3, "cornstarch" to 9.5, "baking soda" to 6.0, "baking powder" to 6.0,
            "cocoa" to 14.0, "chocolate chip" to 8.0, "oats" to 15.0, "panko" to 24.0, "breadcrumb" to 13.0,
            "bread crumb" to 13.0, "cornmeal" to 9.5, "rice" to 7.4, "quinoa" to 8.7, "lentil" to 7.5,
            "honey" to 4.0, "maple syrup" to 4.5, "syrup" to 4.5, "molasses" to 4.0, "peanut butter" to 5.3,
            "mayonnaise" to 6.0, "mayo" to 6.0,
            // Thin liquids: 1 fl oz = 6 tsp.
            "oil" to 6.0, "vinegar" to 6.0, "soy sauce" to 6.0, "sauce" to 6.0, "broth" to 6.0, "stock" to 6.0,
            "extract" to 6.0, "ketchup" to 6.0, "mustard" to 6.0, "dressing" to 6.0, "wine" to 6.0, "milk" to 6.0,
        )

    private fun has(n: String, words: List<String>) = words.any { Regex("\\b" + Regex.escape(it)).containsMatchIn(n) }

    /** Shelf and container for an item, or null if it isn't a pantry item (fresh food, meat, dairy). */
    fun classify(name: String): Pair<String, String>? {
        val n = name.lowercase()
        return when {
            isBinProduce(n) -> "produce" to "bag"
            has(n, listOf("tomato sauce", "tomato paste", "diced tomatoes", "crushed tomatoes", "whole tomatoes",
                "beans", "chickpeas", "garbanzo", "tuna", "coconut milk", "green chiles", "enchilada sauce",
                "soup", "olives", "corn kernels", "canned", "pumpkin puree", "evaporated milk", "condensed milk")) -> "dry" to "can"
            has(n, listOf("broth", "stock")) -> "dry" to "box"
            has(n, listOf("pasta", "spaghetti", "penne", "macaroni", "rigatoni", "linguine", "fettuccine", "noodles",
                "ziti", "orzo", "lasagna", "elbow", "rotini", "farfalle", "crackers", "cereal")) -> "dry" to "box"
            has(n, listOf("rice", "quinoa", "lentils", "couscous", "barley")) -> "dry" to "bag"
            has(n, listOf("baking powder")) -> "baking" to "can"
            has(n, listOf("baking soda")) -> "baking" to "box"
            has(n, listOf("flour", "sugar", "cornstarch", "cocoa", "chocolate chips", "oats", "breadcrumbs",
                "bread crumbs", "panko", "cornmeal", "yeast")) -> "baking" to "bag"
            has(n, listOf("peanut butter", "mayonnaise", "mayo", "salsa", "jam", "jelly", "pesto")) -> "oils" to "jar"
            has(n, listOf("oil", "vinegar", "sauce", "soy", "worcestershire", "sriracha", "honey", "syrup", "ketchup",
                "mustard", "dressing", "extract", "molasses", "cooking wine")) -> "oils" to "bottle"
            has(n, leafy) || has(n, ground) || has(n, listOf("salt", "spice", "seasoning", "bouillon")) -> "spices" to "jar"
            else -> null
        }
    }

    private fun density(name: String): Double? {
        val n = name.lowercase()
        return tspPerOz.firstOrNull { (k, _) -> Regex("\\b" + Regex.escape(k)).containsMatchIn(n) }?.second
    }

    /** "0.75 oz", "16.9 fl oz", "5 lb", "32 fl oz", "1 gal", "21 g" -> amount and unit. */
    fun parseSize(size: String): Pair<Double, String>? {
        val m = Regex("(\\d+(?:\\.\\d+)?|\\d+/\\d+)\\s*(fl\\.?\\s*oz|oz|lbs?|kg|g|ml|l|gal|qt|pt|ct|count)\\b", RegexOption.IGNORE_CASE)
            .find(size) ?: return null
        val raw = m.groupValues[1]
        val n = if ('/' in raw) raw.split('/').let { it[0].toDouble() / it[1].toDouble() } else raw.toDoubleOrNull() ?: return null
        val unit = m.groupValues[2].lowercase().replace(".", "").replace(Regex("\\s+"), " ")
        return n to unit
    }

    /** How a new item is measured and how much a full package holds. */
    fun measureFor(name: String, kind: String, size: String): Pair<String, Double> {
        if (kind == "can") return COUNT to 1.0
        if (isBinProduce(name)) return LB to (sizeLb(size) ?: 5.0)
        val (n, unit) = parseSize(size) ?: return "" to 1.0
        val d = density(name)
        val tsp = when (unit) {
            "fl oz" -> n * 6
            "ml" -> n * 0.2029
            "l" -> n * 202.9
            "gal" -> n * 768
            "qt" -> n * 192
            "pt" -> n * 96
            "oz" -> d?.let { n * it }
            "lb", "lbs" -> d?.let { n * 16 * it }
            "g" -> d?.let { n / 28.35 * it }
            "kg" -> d?.let { n * 1000 / 28.35 * it }
            else -> null
        } ?: return "" to 1.0
        return TSP to tsp
    }

    /** An amount from a recipe, in the item's measure (null = can't tell). */
    fun usage(item: PantryItem, q: Double, u: String): Double? = when (item.measure) {
        COUNT -> if (u == "" || u == "can" || u == "cans") q else null
        TSP -> when (u) {
            "tsp" -> q
            "tbsp" -> q * 3
            "cup" -> q * 48
            "pinch" -> q / 16
            "dash" -> q / 8
            "oz" -> density(item.term + " " + item.name)?.let { q * it }
            "lb" -> density(item.term + " " + item.name)?.let { q * 16 * it }
            else -> null
        }
        // Pounds: weights directly, otherwise through the food's typical weight (1 potato ≈ ½ lb).
        LB -> when (u) {
            "lb" -> q
            "oz" -> q / 16
            else -> Nutrition.grams(Ingredient(q, u, item.term))?.let { it / 453.6 }
        }
        else -> null
    }

    /** "about 6 tbsp", "2 cans", "Half". */
    fun amountText(item: PantryItem, amount: Double = item.left): String = when (item.measure) {
        COUNT -> {
            val c = Math.round(amount).toInt()
            if (c <= 0) "Out" else "$c ${if (c == 1) "can" else "cans"}"
        }
        TSP -> when {
            amount <= 0.05 -> "Out"
            amount < 3 -> "about ${Logic.fmt(Math.round(amount * 4) / 4.0)} tsp"
            amount < 48 -> "about ${Logic.fmt(Math.round(amount / 3 * 4) / 4.0)} tbsp"
            else -> "about ${Logic.fmt(Math.round(amount / 48 * 4) / 4.0)} cups"
        }
        LB -> if (amount <= 0.05) "Out" else "${Logic.fmt(Math.round(amount * 4) / 4.0)} lb left"
        else -> levelWord(if (item.full <= 0) 0.0 else amount / item.full)
    }

    fun levelWord(level: Double) = when {
        level <= 0.05 -> "Out"
        level < 0.35 -> "Low"
        level < 0.75 -> "Half"
        else -> "Full"
    }

    /** True when an unmeasured or nearly empty item should be bought. */
    fun isLow(item: PantryItem) = item.level < 0.25

    /**
     * The shopping-list ingredient a product covers: the longest known ingredient name found in
     * its title ("McCormick Thyme Leaves" -> "thyme"), or a cleaned-up title.
     */
    fun termFor(title: String, known: Collection<String>): String {
        val t = title.lowercase()
        return known.filter { it.length >= 3 && Regex("\\b" + Regex.escape(it) + "(s|es)?\\b").containsMatchIn(t) }
            .maxByOrNull { it.length }
            ?: Logic.searchTerm(title)
    }
}

/** Free product database used when King Soopers doesn't know a scanned barcode. */
object OpenFoodFacts {
    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun lookup(code: String): Product? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = okhttp3.Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,quantity")
            .header("User-Agent", "WeeklyMeals Android app")
            .build()
        val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@withContext null
        val o = org.json.JSONObject(body)
        if (o.optInt("status") != 1) return@withContext null
        val p = o.optJSONObject("product") ?: return@withContext null
        val name = p.optString("product_name").trim()
        if (name.isEmpty()) return@withContext null
        val brand = p.optString("brands").substringBefore(",").trim()
        Product(
            upc = code,
            title = if (brand.isNotEmpty() && !name.contains(brand, true)) "$brand $name" else name,
            size = p.optString("quantity"),
            price = null,
        )
    }
}
