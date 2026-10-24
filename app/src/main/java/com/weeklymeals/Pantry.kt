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
    const val STICK = "stick"
    const val EGG = "egg"

    /** Butter: kept in the Fridge, counted in sticks (1 stick = 8 tbsp = ½ cup = 4 oz). */
    fun isButter(name: String): Boolean {
        val n = name.lowercase()
        return Regex("\\bbutter\\b").containsMatchIn(n) && !n.contains("peanut") && !n.contains("buttermilk")
    }

    /** Eggs: kept in the Fridge, counted one by one (a carton is 12, 18...). An egg white or yolk counts as 1 egg. */
    fun isEgg(name: String): Boolean {
        val n = name.lowercase()
        return Regex("\\beggs?\\b").containsMatchIn(n) &&
            !Regex("noodle|roll|wrap|\\bnog\\b|substitute|beater|salad|replac|plant").containsMatchIn(n)
    }

    /** Eggs in a carton size like "12 ct", "18 count" or "1 dozen" (null if it doesn't say). */
    fun sizeEggs(size: String): Double? {
        Regex("(\\d+(?:\\.\\d+)?)\\s*dozen", RegexOption.IGNORE_CASE).find(size)?.let { return it.groupValues[1].toDouble() * 12 }
        Regex("\\bdozen\\b", RegexOption.IGNORE_CASE).find(size)?.let { return 12.0 }
        return parseSize(size)?.let { (n, u) -> if (u == "ct" || u == "count") n else null }
    }

    /** Items whose leftovers are logged on Finish week (instead of refilled from the cart). */
    fun loggedLeftovers(name: String): Boolean = isBinProduce(name) || isButter(name) || isEgg(name)

    /** Sticks in a butter package size like "16 oz" or "1 lb" (null if it doesn't say). */
    fun sizeSticks(size: String): Double? = sizeLb(size)?.let { it * 4 }

    enum class Fresh { OK, SOON, EXPIRED }

    /** Typical days an item stays good after you buy it (null = doesn't really go bad). */
    fun shelfLifeDays(item: PantryItem): Int? {
        val n = (item.term + " " + item.name).lowercase()
        return when {
            Regex("\\b(sugar|salt|honey|vinegar)\\b").containsMatchIn(n) && !n.contains("seasoning") -> null
            isBinProduce(n) -> 28
            isButter(n) -> 90
            isEgg(n) -> 35
            Regex("lemon juice|lime juice").containsMatchIn(n) -> 180
            item.shelf == "spices" -> 730
            item.shelf == "fridge" -> 30
            item.shelf == "produce" -> 28
            item.kind == "can" || Regex("\\b(rice|pasta|spaghetti|penne|macaroni|lentil|beans)\\b").containsMatchIn(n) -> 730
            else -> 365
        }
    }

    fun isSpice(item: PantryItem): Boolean = item.shelf == "spices"

    /** Spices don't spoil, they just lose flavor: extra days past the jar's date. */
    private fun spiceGrace(n: String): Int = when {
        Regex("\\bsalt\\b").containsMatchIn(n) && !n.contains("seasoning") -> 100_000
        Regex("whole|peppercorn|stick|seed|clove|bay lea|nutmeg|allspice berr|star anise").containsMatchIn(n) -> 900
        else -> 365
    }

    /** Typical days food stays good past its sell-by date. */
    private fun sellByGrace(item: PantryItem, n: String): Int = when {
        isSpice(item) -> spiceGrace(n)
        item.kind == "can" || Regex("broth|stock|bouillon").containsMatchIn(n) -> 365
        Regex("ground (beef|turkey|pork|chicken)|chicken|beef|pork|turkey|fish|salmon|shrimp|sausage|steak").containsMatchIn(n) -> 2
        Regex("shredded|bread|bun|tortilla").containsMatchIn(n) -> 6
        Regex("\\bmilk\\b").containsMatchIn(n) && !n.contains("buttermilk") && !n.contains("coconut") -> 7
        Regex("yogurt|sour cream|cream cheese|heavy cream|whipping cream|half and half|buttermilk").containsMatchIn(n) -> 14
        Regex("\\beggs?\\b").containsMatchIn(n) -> 28
        Regex("cheese|cheddar|parmesan|mozzarella").containsMatchIn(n) -> 28
        isButter(n) -> 60
        Regex("lemon juice|lime juice").containsMatchIn(n) -> 180
        Regex("\\b(rice|pasta|spaghetti|penne|macaroni|flour|oats|lentil|beans|noodles)\\b").containsMatchIn(n) -> 180
        item.shelf == "fridge" || item.shelf == "produce" -> 7
        else -> 30
    }

    /** The day it actually stops being good: the package date plus any grace (0 = no date set). */
    fun goodUntil(item: PantryItem): Long {
        if (item.bestBy <= 0) return 0L
        val n = (item.term + " " + item.name).lowercase()
        val grace = when {
            item.dateKind == "sell" -> sellByGrace(item, n)
            isSpice(item) -> spiceGrace(n)
            else -> 0
        }
        return item.bestBy + grace.toLong() * 86_400_000L
    }

    /** Fresh, use soon (past its typical time or within a week of its date), or past its date. */
    fun freshness(item: PantryItem, now: Long = System.currentTimeMillis()): Fresh {
        val day = 86_400_000L
        if (item.bestBy > 0) {
            val until = goodUntil(item)
            return when {
                now > until + day -> Fresh.EXPIRED
                until - now < 7 * day -> Fresh.SOON
                else -> Fresh.OK
            }
        }
        val life = shelfLifeDays(item) ?: return Fresh.OK
        return if (item.addedAt > 0 && now - item.addedAt > life * day) Fresh.SOON else Fresh.OK
    }

    /** "about 1 year", "about 3 months", "about 4 weeks". */
    fun lifeText(days: Int): String = when {
        days >= 700 -> "about ${days / 365} years"
        days >= 360 -> "about 1 year"
        days >= 60 -> "about ${days / 30} months"
        else -> "about ${days / 7} weeks"
    }

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
        "fridge" to "Fridge", "produce" to "Produce bin",
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
        // Specific foods first (so "peanut butter" is found before "peanut", "almond milk" before "almond").
        listOf(
            // Thin liquids and milks.
            "almond milk" to 6.0, "oat milk" to 6.0, "soy milk" to 6.0, "coconut milk" to 6.0, "coconut water" to 6.0,
            "rice vinegar" to 6.0, "rice wine" to 6.0, "sesame oil" to 6.0, "peanut oil" to 6.0,
            "heavy cream" to 6.0, "half and half" to 6.0,
            // Spreads, sauces and creamy things.
            "peanut butter" to 5.3, "almond butter" to 5.3, "cashew butter" to 5.3, "sunflower butter" to 5.3,
            "tahini" to 5.7, "jam" to 4.3, "jelly" to 4.3, "preserves" to 4.3, "marmalade" to 4.3,
            "salsa" to 5.2, "marinara" to 5.4, "pasta sauce" to 5.4, "spaghetti sauce" to 5.4, "pizza sauce" to 5.4,
            "pesto" to 5.3, "hummus" to 5.5, "bbq sauce" to 4.9, "barbecue" to 4.9, "sriracha" to 5.7,
            "tomato paste" to 5.3, "pumpkin puree" to 5.6, "applesauce" to 5.6, "relish" to 5.5,
            "sour cream" to 5.7, "cream cheese" to 5.9, "yogurt" to 5.6,
            // Nuts, seeds, dried fruit and other dry goods.
            "almond flour" to 14.2, "coconut flour" to 12.0, "oat flour" to 11.0,
            "almond" to 9.5, "walnut" to 13.6, "pecan" to 13.6, "peanut" to 9.3, "cashew" to 10.0, "pistachio" to 10.0,
            "sunflower seed" to 9.7, "pumpkin seed" to 10.4, "sesame" to 9.5, "chia" to 7.0, "flax" to 12.0,
            "raisin" to 9.4, "dried cranberries" to 11.4, "shredded coconut" to 17.0, "coconut flakes" to 17.0,
            "granola" to 13.6, "nutritional yeast" to 17.0, "yeast" to 9.0, "coffee" to 17.0,
            "parmesan" to 13.6, "shredded cheese" to 12.0, "cheese" to 12.0,
        ) + leafy.map { it to 30.0 } + listOf(
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
            isButter(n) -> "fridge" to "box"
            isEgg(n) -> "fridge" to "box"
            has(n, listOf("lemon juice", "lime juice")) -> "fridge" to "bottle"
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

    /**
     * Teaspoons in one ounce of this food: the cup weight you set yourself ([cupOz], 0 = not set),
     * else the list above, else a guess from the container (jars and bottles are saucy, the rest dry).
     */
    fun tspPerOzFor(name: String, kind: String, cupOz: Double = 0.0): Double =
        if (cupOz > 0) 48.0 / cupOz else density(name) ?: if (kind == "jar" || kind == "bottle") 6.0 else 9.0

    /** True when the package size is a weight (oz, lb, g, kg), so a cup weight changes the math. */
    fun isWeightSize(size: String): Boolean {
        val unit = parseSize(size)?.second ?: return false
        return unit == "oz" || unit == "lb" || unit == "lbs" || unit == "g" || unit == "kg"
    }

    /** Ounces in one cup of this food (what the item dialog shows as the automatic value). */
    fun cupOzFor(name: String, kind: String, cupOz: Double = 0.0): Double = 48.0 / tspPerOzFor(name, kind, cupOz)

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
    fun measureFor(name: String, kind: String, size: String, cupOz: Double = 0.0): Pair<String, Double> {
        if (kind == "can") return COUNT to 1.0
        if (isBinProduce(name)) return LB to (sizeLb(size) ?: 5.0)
        if (isButter(name)) return STICK to (sizeSticks(size) ?: 4.0)
        if (isEgg(name)) return EGG to (sizeEggs(size) ?: 12.0)
        val (n, unit) = parseSize(size) ?: return "" to 1.0
        val d = tspPerOzFor(name, kind, cupOz)
        val tsp = when (unit) {
            "fl oz" -> n * 6
            "ml" -> n * 0.2029
            "l" -> n * 202.9
            "gal" -> n * 768
            "qt" -> n * 192
            "pt" -> n * 96
            "oz" -> n * d
            "lb", "lbs" -> n * 16 * d
            "g" -> n / 28.35 * d
            "kg" -> n * 1000 / 28.35 * d
            else -> null
        } ?: return "" to 1.0
        return TSP to tsp
    }

    /** An amount from a recipe, in the item's measure (null = can't tell). */
    /** True when this pantry item covers a shopping-list term (e.g. "garlic" / minced garlic covers garlic cloves). */
    fun covers(item: PantryItem, term: String): Boolean =
        item.term == term || Logic.searchTerm(item.term) == term

    private fun isGarlic(item: PantryItem) =
        Regex("\\bgarlic\\b").containsMatchIn(item.term + " " + item.name) &&
            !Regex("powder|salt|sauce|paste|bread").containsMatchIn(item.term + " " + item.name)

    fun usage(item: PantryItem, q: Double, u: String): Double? = when (item.measure) {
        COUNT -> if (u == "" || u == "can" || u == "cans") q else null
        // Jarred minced garlic: ½ tsp = 1 clove.
        TSP -> if (isGarlic(item) && (u == "" || u == "clove" || u == "cloves")) q * 0.5 else when (u) {
            "tsp" -> q
            "tbsp" -> q * 3
            "cup" -> q * 48
            "pinch" -> q / 16
            "dash" -> q / 8
            "oz" -> q * tspPerOzFor(item.term + " " + item.name, item.kind, item.cupOz)
            "lb" -> q * 16 * tspPerOzFor(item.term + " " + item.name, item.kind, item.cupOz)
            "g" -> q / 28.35 * tspPerOzFor(item.term + " " + item.name, item.kind, item.cupOz)
            "fl oz" -> q * 6
            else -> null
        }
        // Butter sticks: 8 tbsp, ½ cup or 4 oz each.
        STICK -> when (u) {
            "tbsp" -> q / 8
            "tsp" -> q / 24
            "cup" -> q * 2
            "oz" -> q / 4
            "lb" -> q * 4
            "", "stick", "sticks" -> q
            else -> null
        }
        // Eggs: counted one by one.
        EGG -> if (u == "" || u == "egg" || u == "eggs" || u == "large" || u == "medium" || u == "small" || u == "extra large") q else null
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
        EGG -> {
            val c = Math.round(amount).toInt()
            if (c <= 0) "Out" else "$c ${if (c == 1) "egg" else "eggs"}"
        }
        LB -> if (amount <= 0.05) "Out" else "${Logic.fmt(Math.round(amount * 4) / 4.0)} lb left"
        STICK -> {
            val q = Math.round(amount * 4) / 4.0
            if (q <= 0) "Out" else "${Logic.fmt(q)} ${if (q == 1.0) "stick" else "sticks"}"
        }
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
        // "Chosen Foods Avocado Oil" is the cooking oil, not avocados.
        if (Regex("(?<!in )\\b(olive|vegetable|avocado) oil\\b").containsMatchIn(t)) return "olive oil"
        // "Morton Kosher Salt" is just salt (not garlic salt, seasoned salt or salted butter).
        if (Regex("\\b(kosher|sea|table|iodized|coarse) salt\\b").containsMatchIn(t) || Regex("^\\W*salt\\b").containsMatchIn(t)) return "salt"
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
