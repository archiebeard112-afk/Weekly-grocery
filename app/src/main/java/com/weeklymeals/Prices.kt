package com.weeklymeals

import kotlin.math.ceil

/**
 * Your usual King Soopers prices (regular price, from 8 weeks of your receipts) where known,
 * otherwise typical King Soopers prices, used when the app hasn't looked an item up yet.
 * Each entry is a package price and its size in grams; [byWeight] items (meat, fish, some
 * produce) are priced per pound for the exact amount instead of whole packages.
 */
object Prices {
    private class P(val price: Double, val grams: Double, val byWeight: Boolean = false)

    private fun lb(price: Double) = P(price, 453.6, byWeight = true)

    private val table: List<Pair<String, P>> = listOf(
        // Specific names first in meaning (matching prefers the main noun anyway).
        "cream of chicken soup" to P(1.49, 298.0), "cream of mushroom soup" to P(1.49, 298.0),
        "diced tomatoes with green chiles" to P(1.29, 284.0), "sun-dried tomatoes in olive oil" to P(4.99, 240.0),
        "ciabatta bread" to P(3.99, 450.0), "sourdough bread" to P(4.49, 680.0), "naan bread" to P(3.99, 360.0),
        "pita bread" to P(2.99, 360.0), "egg roll" to P(5.99, 680.0), "dried split peas" to P(1.79, 454.0),
        // Meat and fish
        "ground beef" to lb(7.29), "ground turkey" to lb(5.49), "ground chicken" to lb(4.99), "ground pork" to lb(4.49),
        "chuck roast" to lb(7.99), "beef chuck" to lb(7.99), "stew meat" to lb(7.49), "sirloin" to lb(9.99),
        "flank steak" to lb(10.99), "skirt steak" to lb(11.99), "ribeye" to lb(14.99), "cube steak" to lb(6.99), "steak" to lb(10.99),
        "bone-in skin-on chicken thigh" to lb(1.99), "chicken thigh" to lb(4.25), "chicken breast" to lb(2.59),
        "chicken tenderloin" to lb(4.99), "chicken wing" to lb(3.99), "cooked chicken" to P(6.99, 567.0), "chicken" to lb(3.49),
        "pork chop" to lb(4.48), "pork loin" to lb(2.99), "pork tenderloin" to lb(4.49), "pork shoulder" to lb(2.49),
        "bacon" to P(6.99, 340.0), "breakfast sausage" to P(4.49, 454.0), "italian sausage" to P(5.99, 454.0),
        "kielbasa" to P(4.99, 397.0), "smoked sausage" to P(4.49, 397.0), "chorizo" to P(3.99, 340.0),
        "ham steak" to lb(4.99), "deli ham" to P(5.99, 255.0), "ham" to P(5.99, 454.0), "pepperoni" to P(3.99, 170.0),
        "deli turkey" to P(5.99, 255.0), "meatball" to P(7.99, 794.0), "shrimp" to lb(9.99), "salmon" to lb(10.99),
        "cod" to lb(9.99), "tilapia" to lb(6.99), "white fish" to lb(7.99), "tuna" to P(1.49, 142.0),
        "chopped clams" to P(2.79, 184.0), "clam juice" to P(2.99, 236.0), "tofu" to P(2.49, 397.0),
        // Dairy and eggs
        "butter" to P(3.79, 454.0), "half and half" to P(2.99, 473.0), "heavy cream" to P(4.49, 473.0),
        "coconut cream" to P(2.99, 400.0), "coconut milk" to P(4.39, 400.0), "sour cream" to P(1.49, 227.0),
        "greek yogurt" to P(4.99, 907.0), "cream cheese" to P(2.79, 226.0), "milk" to P(2.69, 1890.0),
        "cheddar" to P(3.99, 226.0), "fresh mozzarella" to P(4.99, 226.0), "mozzarella" to P(3.99, 226.0),
        "parmesan" to P(4.99, 142.0), "feta" to P(4.49, 170.0), "cotija" to P(4.49, 283.0), "goat cheese" to P(3.99, 113.0),
        "ricotta" to P(3.99, 425.0), "provolone" to P(3.99, 226.0), "swiss" to P(4.49, 226.0), "pepper jack" to P(3.99, 226.0),
        "blue cheese" to P(3.99, 142.0), "mexican cheese" to P(2.00, 226.0), "cheese" to P(3.99, 226.0),
        "egg noodle" to P(2.00, 454.0), "egg" to P(3.99, 600.0),
        // Grains, pasta, bread
        "cooked rice" to P(3.49, 567.0), "arborio" to P(4.49, 907.0), "rice noodle" to P(2.99, 227.0), "rice" to P(2.99, 907.0),
        "quinoa" to P(4.99, 340.0), "lentil" to P(1.99, 454.0), "split pea" to P(1.79, 454.0), "dried black beans" to P(1.99, 454.0),
        "oats" to P(4.29, 1190.0), "tortellini" to P(4.49, 255.0), "ravioli" to P(7.99, 709.0), "gnocchi" to P(2.99, 454.0),
        "pierogi" to P(3.99, 454.0), "lo mein" to P(2.49, 227.0), "chow mein noodles" to P(2.49, 142.0), "grits" to P(3.49, 680.0),
        "lasagna noodle" to P(1.99, 454.0), "jumbo shells" to P(1.99, 340.0), "pasta" to P(1.34, 454.0),
        "orzo" to P(1.49, 454.0), "spaghetti" to P(1.34, 454.0), "linguine" to P(1.49, 454.0), "fettuccine" to P(1.49, 454.0),
        "macaroni" to P(1.49, 454.0), "penne" to P(1.49, 454.0), "ziti" to P(1.49, 454.0),
        "slider bun" to P(3.99, 360.0), "hawaiian" to P(3.79, 595.0), "burger bun" to P(2.99, 416.0), "hoagie roll" to P(3.49, 510.0), "french roll" to P(3.49, 420.0),
        "baguette" to P(2.99, 250.0), "ciabatta" to P(3.99, 450.0), "sourdough" to P(4.49, 680.0), "naan" to P(3.99, 360.0),
        "pita" to P(2.99, 360.0), "flatbread" to P(3.99, 280.0), "corn tortilla" to P(2.49, 780.0), "tortilla wrap" to P(3.49, 560.0),
        "flour tortilla" to P(3.99, 652.0), "pizza dough" to P(2.99, 454.0), "pie crust" to P(3.49, 456.0),
        "biscuit mix" to P(4.29, 1130.0), "cornbread mix" to P(0.79, 240.0), "pancake mix" to P(3.29, 907.0),
        "panko" to P(2.99, 227.0), "breadcrumb" to P(2.49, 425.0), "crouton" to P(2.49, 142.0),
        "tortilla chips" to P(3.99, 369.0), "tortilla strips" to P(2.99, 99.0), "potato chips" to P(4.29, 283.0), "bread" to P(2.99, 567.0),
        // Produce
        "green onion" to P(0.99, 100.0), "scallion" to P(0.99, 100.0), "red onion" to lb(1.78), "white onion" to lb(1.49), "sweet onion" to lb(1.69), "onion" to lb(1.29), "minced garlic" to P(3.49, 227.0),
        "garlic" to P(0.69, 50.0), "green bell pepper" to P(0.89, 120.0), "bell pepper" to P(1.29, 120.0), "jalapeño" to lb(0.99), "jalapeno" to lb(0.99), "fresno" to lb(2.99),
        "shredded carrot" to P(1.99, 283.0), "carrot" to P(2.29, 907.0), "celery" to P(3.49, 567.0), "sweet potato" to lb(1.29),
        "red-skinned potato" to P(4.99, 1360.0), "yukon" to P(4.99, 2268.0), "hash brown" to P(3.49, 850.0), "potato" to P(5.00, 4536.0),
        "butternut" to lb(1.49), "zucchini" to lb(1.69), "yellow squash" to lb(1.49), "eggplant" to P(1.99, 450.0),
        "broccolini" to P(3.99, 200.0), "broccoli" to P(2.49, 340.0), "cauliflower" to P(3.49, 575.0), "green bean" to P(2.99, 340.0),
        "asparagus" to lb(3.69), "spinach" to P(3.49, 142.0), "kale" to P(2.49, 200.0), "romaine" to P(2.99, 626.0),
        "boston lettuce" to P(2.99, 160.0), "lettuce" to P(2.49, 500.0), "mixed greens" to P(3.99, 142.0), "coleslaw" to P(2.49, 397.0),
        "cabbage" to lb(0.99), "bok choy" to lb(2.49), "mushroom" to P(2.99, 227.0), "cherry tomato" to P(3.49, 283.0),
        "sun-dried tomato" to P(4.99, 240.0), "tomato paste" to P(0.99, 170.0), "tomato sauce" to P(1.19, 425.0),
        "crushed tomato" to P(2.49, 794.0), "diced tomato" to P(1.00, 411.0), "tomato" to lb(2.49),
        "persian cucumber" to P(3.99, 454.0), "small cucumber" to P(3.99, 454.0), "cucumber" to P(0.79, 300.0),
        "avocado" to P(2.50, 150.0), "corn on the cob" to P(1.25, 100.0), "corn" to P(1.79, 454.0), "peas" to P(1.79, 340.0),
        "edamame" to P(2.99, 340.0), "bean sprout" to P(1.99, 340.0), "kalamata" to P(4.99, 283.0), "olive oil" to P(9.99, 1000.0),
        "olive" to P(2.99, 170.0), "caper" to P(3.49, 99.0), "pickle" to P(3.49, 680.0), "pepperoncini" to P(3.29, 454.0),
        "pineapple" to P(1.99, 567.0), "cilantro" to P(0.99, 50.0), "parsley" to P(0.99, 50.0), "fresh basil" to P(2.99, 20.0),
        "dill" to P(2.49, 20.0), "rosemary" to P(2.49, 20.0), "ginger" to lb(4.00), "lemon juice" to P(2.99, 444.0),
        "lime juice" to P(1.50, 118.0), "lemon zest" to P(0.69, 60.0), "lemon" to P(0.70, 60.0), "lime" to P(0.39, 67.0),
        "orange juice" to P(3.99, 1500.0), "orange" to P(0.99, 140.0), "apple juice" to P(2.99, 1890.0), "asian pear" to P(1.99, 275.0),
        "apple" to lb(1.99), "banana" to P(0.29, 118.0), "blueberr" to P(3.99, 170.0), "strawberr" to P(3.49, 454.0), "grape" to lb(2.99),
        // Canned and jarred
        "refried beans" to P(1.49, 454.0), "baked beans" to P(2.99, 794.0), "chickpea" to P(1.19, 425.0), "black beans" to P(1.19, 425.0),
        "kidney beans" to P(1.00, 440.0), "pinto beans" to P(1.19, 425.0), "cannellini" to P(1.00, 440.0), "great northern" to P(1.19, 425.0),
        "green chile" to P(1.29, 113.0), "cream of" to P(1.49, 298.0), "enchilada sauce" to P(2.49, 283.0),
        "salsa verde" to P(3.49, 454.0), "salsa" to P(2.19, 454.0), "marinara" to P(3.49, 680.0), "pizza sauce" to P(2.49, 397.0),
        "alfredo" to P(3.49, 425.0), "hummus" to P(3.99, 283.0), "pesto" to P(4.99, 190.0), "broth" to P(1.59, 907.0), "stock" to P(2.49, 907.0),
        // Sauces, oils, baking
        "soy sauce" to P(2.99, 296.0), "fish sauce" to P(3.49, 200.0), "oyster sauce" to P(3.49, 255.0), "hoisin" to P(3.49, 240.0),
        "teriyaki" to P(3.49, 296.0), "sweet and sour" to P(2.99, 340.0), "sweet chili" to P(2.49, 296.0), "bbq sauce" to P(2.49, 510.0),
        "buffalo sauce" to P(3.99, 354.0), "katsu" to P(4.99, 300.0), "chili garlic sauce" to P(3.49, 226.0), "sriracha" to P(4.49, 482.0),
        "worcestershire" to P(2.99, 296.0), "curry paste" to P(3.99, 113.0), "ketchup" to P(2.99, 907.0), "dijon" to P(1.89, 340.0),
        "mustard" to P(1.99, 396.0), "mayonnaise" to P(7.29, 887.0), "ranch dressing" to P(3.99, 473.0), "caesar dressing" to P(3.99, 473.0),
        "honey" to P(6.99, 340.0), "maple syrup" to P(8.99, 354.0), "brown sugar" to P(2.99, 907.0), "sugar" to P(3.99, 1814.0),
        "flour" to P(3.49, 2268.0), "cornstarch" to P(2.49, 454.0), "baking powder" to P(2.99, 226.0), "peanut butter" to P(3.49, 454.0),
        "peanut" to P(3.99, 454.0), "cashew" to P(7.99, 454.0), "sesame seed" to P(3.99, 100.0), "sesame oil" to P(4.99, 236.0),
        "coconut oil" to P(6.99, 414.0), "vegetable oil" to P(4.99, 1420.0), "oil" to P(4.99, 1420.0),
        "balsamic glaze" to P(5.99, 250.0), "balsamic" to P(3.99, 500.0), "vinegar" to P(3.49, 1890.0),
        "marsala" to P(4.99, 473.0), "wine" to P(7.99, 750.0), "gravy mix" to P(1.29, 28.0), "soup mix" to P(1.99, 56.0),
        "taco seasoning" to P(1.00, 28.0), "seasoning" to P(1.49, 28.0),
        // Baking and desserts
        "chocolate chip" to P(3.49, 340.0), "cocoa" to P(4.49, 226.0), "vanilla extract" to P(5.99, 59.0),
        "baking soda" to P(1.29, 454.0), "powdered sugar" to P(2.99, 907.0), "yeast" to P(4.99, 113.0),
        "raisin" to P(3.49, 340.0), "graham cracker" to P(3.99, 408.0), "condensed milk" to P(2.49, 397.0),
        "evaporated milk" to P(1.49, 354.0), "pecan" to P(6.99, 227.0), "walnut" to P(5.99, 227.0),
        "peach" to P(2.49, 822.0), "cherry pie filling" to P(3.49, 595.0), "maraschino" to P(2.99, 284.0),
        "pumpkin" to P(1.99, 425.0), "whipped topping" to P(2.49, 226.0), "marshmallow" to P(1.99, 283.0),
        "rice cereal" to P(4.29, 340.0), "vanilla wafer" to P(3.99, 312.0), "pudding mix" to P(1.49, 96.0),
        "molasses" to P(4.49, 355.0), "corn syrup" to P(3.99, 473.0), "pectin" to P(3.49, 49.0),
        "mixed berr" to P(4.99, 454.0), "ice cream" to P(3.99, 1420.0), "pineapple juice" to P(2.49, 1360.0),
    )

    private val compiled = table.map { (k, v) -> Triple(Regex("\\b" + Regex.escape(k)), k.length, v) }

    private val free = Regex("\\b(water|club soda|ice(?! cream))\\b")
    private val jarred = Regex(
        "\\b(salt|black pepper|pepper flakes|cumin|paprika|cinnamon|oregano|thyme|chili powder|garlic powder|onion powder|" +
            "cayenne|turmeric|coriander|curry powder|garam masala|dry mustard|italian seasoning|nutmeg|dried basil|ground ginger)\\b",
    )

    private val memo = HashMap<String, P?>()

    /** Main-noun match, same rule as calories ("chicken broth" is broth). Remembered per name. */
    private fun find(name: String): P? = memo.getOrPut(name) { match(name) }

    private fun match(name: String): P? {
        var best: Pair<Int, Int>? = null
        var pick: P? = null
        for ((rx, len, v) in compiled) {
            val m = rx.find(name) ?: continue
            var end = m.range.last + 1
            while (end < name.length && name[end].isLetter()) end++
            val score = end to len
            if (best == null || score.first > best.first || (score.first == best.first && score.second > best.second)) {
                best = score
                pick = v
            }
        }
        return pick
    }

    /** Typical price per pound for meat and fish sold by weight (null if not one of those). */
    /** Grams in the usual package of something sold by the package (null if sold by weight or unknown). */
    fun packGrams(name: String): Double? = find(name.lowercase())?.takeIf { !it.byWeight }?.grams

    /** Grams in a store size like "5 oz" (public for leftovers). */
    fun productGrams(size: String): Double? = packGramsOf(size)

    /** Price of the usual package of something sold by the package. */
    fun packPrice(name: String): Double? = find(name.lowercase())?.takeIf { !it.byWeight }?.price

    fun perLb(name: String): Double? = find(name.lowercase())?.takeIf { it.byWeight }?.price

    /** Grams in a store package size like "16 oz", "2 lb", "32 fl oz" (null if it doesn't say). */
    private fun packGramsOf(size: String): Double? {
        val (n, unit) = Pantry.parseSize(size) ?: return null
        return when (unit) {
            "oz" -> n * 28.35
            "fl oz" -> n * 29.57
            "lb", "lbs" -> n * 453.6
            "g" -> n
            "kg" -> n * 1000
            "ml" -> n
            "l" -> n * 1000
            "gal" -> n * 3785
            "qt" -> n * 946
            "pt" -> n * 473
            else -> null
        }
    }

    /**
     * What the amount a meal uses is worth, for things you already have: the used part of a
     * package ([whole] is what buying it would cost). Spice jars count about 20 tsp.
     */
    fun usedValue(name: String, items: List<ShopItem>, product: Product?, whole: Double): Double {
        val n = name.lowercase()
        if (product == null && jarred.containsMatchIn(n)) {
            val tsp = items.sumOf {
                when (it.unit) { "tsp" -> it.qty; "tbsp" -> it.qty * 3; "pinch", "dash" -> it.qty / 16; else -> return whole }
            }
            return minOf(whole, tsp * 3.49 / 20)
        }
        val grams = items.mapNotNull { Nutrition.grams(Ingredient(it.qty, it.unit, it.name)) }.sum()
        if (grams <= 0) return whole
        val p = find(n)
        if (product == null && p?.byWeight == true) return whole // Already priced for the exact amount.
        val pack = product?.let { packGramsOf(it.size) } ?: p?.grams ?: return whole
        val price = product?.let { it.promo ?: it.price } ?: p?.price ?: return whole
        return minOf(whole, grams / pack * price)
    }

    /**
     * What it costs to buy enough of one shopping item ([items] are its amounts in the meal).
     * Uses the King Soopers product when known, otherwise typical prices. Null = unknown.
     */
    fun cost(name: String, items: List<ShopItem>, product: Product?): Double? {
        val n = name.lowercase()
        if (free.containsMatchIn(n)) return 0.0
        val grams = items.mapNotNull { Nutrition.grams(Ingredient(it.qty, it.unit, it.name)) }.sum()
        product?.let { p ->
            val price = p.promo ?: p.price ?: return@let
            // The same number of packages the cart would order (cans one for one, bags of pieces, fl oz…).
            return price * Logic.suggestQty(Logic.needOf(items), p)
        }
        if (jarred.containsMatchIn(n)) return 3.49 // One jar of spice.
        val p = find(n) ?: return null
        if (grams <= 0) return p.price
        // Like the cart: a sliver over a package (under 5%) doesn't add another one.
        return if (p.byWeight) grams / 453.6 * p.price else ceil(grams / p.grams - 0.05).coerceAtLeast(1.0) * p.price
    }
}
