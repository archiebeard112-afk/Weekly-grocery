package com.weeklymeals

/**
 * Calorie estimates from ingredients. Each entry: calories per 100 g, grams per cup,
 * grams per item (one onion, one egg…) and grams per package/can when it differs from
 * the default. Numbers are typical values; results are estimates (shown with "≈").
 */
object Nutrition {
    private class N(val kcal100: Double, val cup: Double = 0.0, val each: Double = 0.0, val pack: Double = 0.0)

    private val table: List<Pair<String, N>> = listOf(
        // Specific names that would otherwise match the wrong word.
        "cream of chicken soup" to N(85.0, 250.0, pack = 298.0), "cream of mushroom soup" to N(85.0, 250.0, pack = 298.0),
        "diced tomatoes with green chiles" to N(20.0, 240.0, pack = 284.0), "sun-dried tomatoes in olive oil" to N(213.0, 110.0),
        "ciabatta bread" to N(270.0, each = 150.0), "sourdough bread" to N(270.0, each = 50.0), "naan bread" to N(290.0, each = 90.0),
        "pita bread" to N(275.0, each = 60.0), "egg roll" to N(250.0, each = 85.0), "dried split peas" to N(341.0, 196.0),
        // Meat and fish (raw)
        "ground beef" to N(250.0, 225.0), "ground turkey" to N(150.0, 225.0), "ground chicken" to N(143.0, 225.0),
        "ground pork" to N(263.0, 225.0), "chuck roast" to N(250.0), "beef chuck" to N(250.0), "stew meat" to N(200.0),
        "sirloin" to N(180.0, each = 300.0), "flank steak" to N(165.0), "skirt steak" to N(200.0), "ribeye" to N(290.0, each = 340.0),
        "cube steak" to N(160.0, each = 115.0), "steak" to N(200.0, each = 250.0),
        "bone-in skin-on chicken thigh" to N(230.0, each = 170.0), "chicken thigh" to N(177.0, each = 115.0),
        "chicken breast" to N(120.0, each = 200.0), "chicken tenderloin" to N(110.0, each = 40.0),
        "chicken wing" to N(200.0, each = 45.0), "cooked chicken" to N(165.0, 140.0), "chicken" to N(150.0, 140.0, 150.0),
        "pork chop" to N(200.0, each = 180.0), "pork loin" to N(145.0), "pork tenderloin" to N(120.0, each = 450.0),
        "pork shoulder" to N(250.0), "bacon" to N(300.0, each = 25.0), "breakfast sausage" to N(330.0, each = 25.0, pack = 454.0),
        "italian sausage" to N(300.0, each = 110.0, pack = 540.0), "kielbasa" to N(300.0, pack = 397.0),
        "smoked sausage" to N(300.0, pack = 397.0), "chorizo" to N(345.0, each = 75.0, pack = 340.0),
        "ham steak" to N(140.0, each = 450.0), "deli ham" to N(110.0, each = 28.0, pack = 255.0), "ham" to N(145.0, 140.0),
        "pepperoni" to N(500.0, 120.0, 2.0, 170.0), "deli turkey" to N(100.0, each = 28.0, pack = 255.0),
        "meatball" to N(290.0, each = 30.0, pack = 794.0), "shrimp" to N(85.0, each = 15.0),
        "salmon" to N(208.0, each = 170.0), "cod" to N(82.0, each = 170.0), "tilapia" to N(96.0, each = 115.0),
        "white fish" to N(90.0, each = 150.0), "tuna" to N(116.0, pack = 142.0), "chopped clams" to N(74.0, pack = 184.0),
        "clam juice" to N(2.0, 240.0), "tofu" to N(85.0, each = 397.0, pack = 397.0),
        // Dairy and eggs
        "butter" to N(717.0, 227.0), "half and half" to N(130.0, 242.0), "heavy cream" to N(340.0, 238.0),
        "coconut cream" to N(330.0, 240.0, pack = 400.0), "coconut milk" to N(197.0, 226.0, pack = 400.0),
        "sour cream" to N(198.0, 230.0), "greek yogurt" to N(60.0, 245.0), "cream cheese" to N(342.0, 232.0, pack = 226.0),
        "milk" to N(50.0, 245.0), "cheddar" to N(403.0, 113.0), "fresh mozzarella" to N(280.0, 120.0, 226.0, 226.0),
        "mozzarella" to N(300.0, 112.0), "parmesan" to N(420.0, 100.0), "feta" to N(265.0, 150.0), "cotija" to N(366.0, 120.0),
        "goat cheese" to N(364.0, 130.0, 113.0, 113.0), "ricotta" to N(174.0, 246.0), "provolone" to N(350.0, 113.0, 28.0),
        "swiss" to N(380.0, 113.0, 28.0), "pepper jack" to N(370.0, 113.0, 28.0), "blue cheese" to N(353.0, 135.0),
        "mexican cheese" to N(400.0, 113.0), "cheese" to N(390.0, 113.0, 28.0), "egg noodle" to N(384.0, 40.0, pack = 340.0),
        "egg" to N(143.0, 243.0, 50.0),
        // Grains, pasta, bread (dry unless noted)
        "cooked rice" to N(130.0, 158.0), "arborio" to N(360.0, 190.0), "rice noodle" to N(360.0, 90.0, pack = 227.0),
        "rice" to N(365.0, 185.0), "quinoa" to N(368.0, 170.0), "lentil" to N(352.0, 192.0), "split pea" to N(341.0, 196.0),
        "dried black beans" to N(341.0, 194.0), "rolled oats" to N(379.0, 81.0), "oats" to N(379.0, 81.0),
        "tortellini" to N(300.0, 100.0, pack = 255.0), "ravioli" to N(250.0, 120.0, pack = 709.0), "gnocchi" to N(150.0, 150.0, pack = 454.0),
        "pierogi" to N(200.0, each = 40.0, pack = 454.0), "lo mein" to N(370.0, 90.0, pack = 227.0),
        "chow mein noodles" to N(520.0, 45.0), "grits" to N(370.0, 156.0), "lasagna noodle" to N(371.0, each = 20.0, pack = 454.0),
        "jumbo shells" to N(371.0, each = 9.0, pack = 340.0), "orzo" to N(371.0, 170.0, pack = 454.0),
        "spaghetti" to N(371.0, 100.0, pack = 454.0), "linguine" to N(371.0, 100.0, pack = 454.0), "fettuccine" to N(371.0, 100.0, pack = 454.0),
        "macaroni" to N(371.0, 105.0, pack = 454.0), "penne" to N(371.0, 105.0, pack = 454.0), "ziti" to N(371.0, 105.0, pack = 454.0),
        "pasta" to N(371.0, 105.0, pack = 454.0),
        "slider bun" to N(280.0, each = 30.0), "burger bun" to N(280.0, each = 52.0), "hoagie roll" to N(280.0, each = 85.0),
        "french roll" to N(280.0, each = 70.0), "baguette" to N(270.0, each = 250.0), "ciabatta" to N(270.0, each = 150.0),
        "sourdough" to N(270.0, each = 50.0), "naan" to N(290.0, each = 90.0), "pita" to N(275.0, each = 60.0),
        "flatbread" to N(300.0, each = 70.0), "corn tortilla" to N(218.0, each = 26.0), "tortilla wrap" to N(300.0, each = 70.0),
        "flour tortilla" to N(310.0, each = 45.0), "pizza dough" to N(250.0, each = 454.0, pack = 454.0),
        "pie crust" to N(410.0, each = 228.0), "biscuit mix" to N(430.0, 120.0), "cornbread mix" to N(410.0, 140.0, pack = 240.0),
        "pancake mix" to N(370.0, 120.0), "panko" to N(360.0, 55.0), "breadcrumb" to N(395.0, 108.0), "crouton" to N(465.0, 30.0),
        "tortilla chips" to N(490.0, 30.0), "tortilla strips" to N(490.0, 30.0), "potato chips" to N(536.0, 28.0), "bread" to N(265.0, 45.0, 30.0),
        // Vegetables and fruit (raw)
        "green onion" to N(32.0, 100.0, 15.0, 100.0), "scallion" to N(32.0, 100.0, 15.0), "onion" to N(40.0, 160.0, 110.0),
        "minced garlic" to N(149.0, 136.0, 3.0), "garlic" to N(149.0, 136.0, 3.0), "bell pepper" to N(26.0, 150.0, 120.0),
        "jalapeño" to N(29.0, 90.0, 14.0), "jalapeno" to N(29.0, 90.0, 14.0), "fresno" to N(40.0, 90.0, 15.0),
        "shredded carrot" to N(41.0, 110.0), "carrot" to N(41.0, 128.0, 61.0), "celery" to N(14.0, 101.0, 40.0),
        "sweet potato" to N(86.0, 133.0, 130.0), "red-skinned potato" to N(70.0, 150.0, 120.0), "yukon" to N(77.0, 150.0, 150.0),
        "hash brown" to N(82.0, 80.0, pack = 850.0), "potato" to N(77.0, 150.0, 213.0), "butternut" to N(45.0, 140.0, 1200.0),
        "zucchini" to N(17.0, 124.0, 200.0), "yellow squash" to N(16.0, 124.0, 200.0), "eggplant" to N(25.0, 82.0, 450.0),
        "broccolini" to N(35.0, 90.0, pack = 200.0), "broccoli" to N(34.0, 71.0, 300.0, 340.0), "cauliflower" to N(25.0, 107.0, 575.0),
        "green bean" to N(31.0, 110.0, pack = 340.0), "asparagus" to N(20.0, 134.0, pack = 450.0),
        "spinach" to N(23.0, 30.0, pack = 284.0), "kale" to N(49.0, 21.0, pack = 200.0), "romaine" to N(17.0, 47.0, 626.0),
        "boston lettuce" to N(13.0, 55.0, 160.0), "lettuce" to N(15.0, 47.0, 500.0), "mixed greens" to N(20.0, 30.0, pack = 142.0),
        "coleslaw" to N(25.0, 70.0, pack = 397.0), "cabbage" to N(25.0, 70.0, 900.0, 397.0), "bok choy" to N(13.0, 70.0, 100.0),
        "mushroom" to N(24.0, 70.0, 18.0, 227.0), "cherry tomato" to N(18.0, 149.0, 17.0), "sun-dried tomato" to N(230.0, 80.0),
        "tomato paste" to N(82.0, 262.0, pack = 170.0), "tomato sauce" to N(24.0, 245.0, pack = 425.0),
        "crushed tomato" to N(32.0, 240.0, pack = 794.0), "diced tomato" to N(20.0, 240.0, pack = 411.0),
        "tomato" to N(18.0, 180.0, 123.0), "persian cucumber" to N(15.0, 120.0, 100.0), "small cucumber" to N(15.0, 120.0, 100.0),
        "cucumber" to N(15.0, 120.0, 300.0), "avocado" to N(160.0, 150.0, 150.0), "corn on the cob" to N(86.0, each = 100.0),
        "corn" to N(88.0, 145.0, pack = 454.0), "peas" to N(81.0, 145.0, pack = 340.0), "edamame" to N(121.0, 155.0, pack = 340.0),
        "bean sprout" to N(30.0, 104.0), "kalamata" to N(230.0, 135.0, 4.0), "olive oil" to N(884.0, 216.0),
        "olive" to N(145.0, 135.0, 4.0), "caper" to N(23.0, 136.0), "pickle" to N(11.0, 143.0, 65.0), "pepperoncini" to N(30.0, 140.0, 10.0),
        "pineapple" to N(50.0, 165.0, pack = 567.0), "cilantro" to N(23.0, 16.0, pack = 50.0), "parsley" to N(36.0, 60.0, pack = 50.0),
        "fresh basil" to N(23.0, 24.0, pack = 20.0), "dill" to N(43.0, 9.0), "rosemary" to N(131.0, 30.0),
        "ginger" to N(80.0, 96.0, 15.0), "lemon juice" to N(22.0, 244.0), "lime juice" to N(25.0, 244.0),
        "lemon zest" to N(47.0, 96.0), "lemon" to N(29.0, 210.0, 60.0), "lime" to N(30.0, 210.0, 67.0),
        "orange juice" to N(45.0, 248.0), "orange" to N(47.0, 180.0, 140.0), "apple juice" to N(46.0, 248.0),
        "asian pear" to N(42.0, 120.0, 275.0), "apple" to N(52.0, 125.0, 182.0), "banana" to N(89.0, 150.0, 118.0),
        "blueberr" to N(57.0, 148.0), "strawberr" to N(32.0, 152.0), "grape" to N(69.0, 151.0, 5.0),
        // Beans and canned
        "refried beans" to N(90.0, 240.0, pack = 454.0), "baked beans" to N(125.0, 254.0, pack = 454.0),
        "chickpea" to N(105.0, 165.0, pack = 425.0), "black beans" to N(91.0, 172.0, pack = 425.0),
        "kidney beans" to N(84.0, 177.0, pack = 425.0), "pinto beans" to N(86.0, 171.0, pack = 425.0),
        "cannellini" to N(85.0, 179.0, pack = 425.0), "great northern" to N(80.0, 179.0, pack = 425.0),
        "green chile" to N(20.0, 135.0, pack = 113.0), "cream of" to N(85.0, 250.0, pack = 298.0),
        "enchilada sauce" to N(30.0, 250.0, pack = 283.0), "salsa verde" to N(30.0, 260.0, pack = 454.0),
        "salsa" to N(36.0, 260.0, pack = 454.0), "marinara" to N(50.0, 250.0, pack = 680.0), "pizza sauce" to N(55.0, 250.0, pack = 397.0),
        "alfredo" to N(160.0, 250.0, pack = 425.0), "hummus" to N(166.0, 246.0, pack = 283.0), "pesto" to N(400.0, 240.0, pack = 190.0),
        "broth" to N(6.0, 240.0, pack = 907.0), "stock" to N(6.0, 240.0, pack = 907.0),
        // Sauces, oils, sweeteners, nuts
        "soy sauce" to N(53.0, 255.0), "fish sauce" to N(35.0, 288.0), "oyster sauce" to N(51.0, 288.0), "hoisin" to N(220.0, 260.0),
        "teriyaki" to N(89.0, 288.0), "sweet and sour" to N(150.0, 280.0), "sweet chili" to N(220.0, 280.0),
        "bbq sauce" to N(172.0, 280.0), "buffalo sauce" to N(30.0, 250.0), "katsu" to N(120.0, 280.0),
        "chili garlic sauce" to N(90.0, 270.0), "sriracha" to N(93.0, 270.0), "worcestershire" to N(78.0, 275.0),
        "curry paste" to N(100.0, 260.0), "ketchup" to N(100.0, 240.0), "dijon" to N(66.0, 250.0), "mustard" to N(60.0, 250.0),
        "mayonnaise" to N(680.0, 220.0), "ranch dressing" to N(430.0, 240.0), "caesar dressing" to N(480.0, 240.0),
        "honey" to N(304.0, 340.0), "maple syrup" to N(260.0, 315.0), "brown sugar" to N(380.0, 220.0), "sugar" to N(387.0, 200.0),
        "flour" to N(364.0, 125.0), "cornstarch" to N(381.0, 128.0), "baking powder" to N(53.0, 220.0),
        "peanut butter" to N(588.0, 258.0), "peanut" to N(567.0, 146.0), "cashew" to N(553.0, 137.0), "sesame seed" to N(573.0, 144.0),
        "sesame oil" to N(884.0, 218.0), "coconut oil" to N(884.0, 218.0), "vegetable oil" to N(884.0, 218.0), "oil" to N(884.0, 218.0),
        "balsamic glaze" to N(250.0, 300.0), "balsamic" to N(88.0, 255.0), "vinegar" to N(20.0, 240.0),
        "marsala" to N(150.0, 235.0), "wine" to N(82.0, 235.0),
        "gravy mix" to N(350.0, 128.0, pack = 28.0), "soup mix" to N(300.0, 128.0, pack = 28.0),
        "seasoning" to N(300.0, 128.0, pack = 28.0),
    )

    /** Each keyword with its pattern compiled once. */
    private val compiled = table.map { (k, v) -> Triple(Regex("\\b" + Regex.escape(k)), k.length, v) }

    private val zero = Regex("\\b(water|club soda|salt|pepper flakes|black pepper|ice)\\b")
    private val spice = Regex("\\b(cumin|paprika|cinnamon|oregano|thyme|basil|chili powder|garlic powder|onion powder|cayenne|turmeric|coriander|curry powder|garam masala|dry mustard|italian seasoning|nutmeg|ginger)\\b")

    /**
     * The food an ingredient name is about: the match that ends last (the main noun, so
     * "chicken broth" is broth, not chicken), and the longest one if two end together.
     */
    private fun find(name: String): N? {
        val n = name.lowercase()
        var best: Pair<Int, Int>? = null // (word end, keyword length)
        var pick: N? = null
        for ((rx, len, v) in compiled) {
            val m = rx.find(n) ?: continue
            var end = m.range.last + 1
            while (end < n.length && n[end].isLetter()) end++
            val score = end to len
            if (best == null || score.first > best.first || (score.first == best.first && score.second > best.second)) {
                best = score
                pick = v
            }
        }
        return pick
    }

    /** Calories in one ingredient amount, or null if we don't know it. */
    fun calories(i: Ingredient): Double? {
        val n = i.name.lowercase()
        if (zero.containsMatchIn(n) && !n.contains("pepper jack")) return 0.0
        // Ground/dried spices are a few calories a teaspoon.
        if (spice.containsMatchIn(n) && !n.contains("pesto") && i.u in setOf("tsp", "tbsp", "pinch", "")) {
            val tsp = when (i.u) { "tbsp" -> 3.0; "pinch" -> 0.06; else -> 1.0 } * i.q
            return tsp * 6
        }
        val f = find(n) ?: return null
        val grams = gramsWith(f, i) ?: return null
        // A cup or more of oil is for frying: only about 15% ends up in the food.
        val eaten = if (Regex("\\boil\\b").containsMatchIn(n) && i.u == "cup" && i.q >= 1) 0.15 else 1.0
        return grams * f.kcal100 / 100.0 * eaten
    }

    /** Grams of food in one ingredient amount (null if we can't tell). */
    fun grams(i: Ingredient): Double? = find(i.name.lowercase())?.let { gramsWith(it, i) }

    private fun gramsWith(f: N, i: Ingredient): Double? {
        val one = when (i.u) {
            "lb" -> 453.6
            "oz" -> 28.35
            "cup" -> f.cup.takeIf { it > 0 } ?: 150.0
            "tbsp" -> (f.cup.takeIf { it > 0 } ?: 150.0) / 16
            "tsp" -> (f.cup.takeIf { it > 0 } ?: 150.0) / 48
            "pinch" -> 0.3
            "clove" -> 3.0
            // A slice of a loaf, not the whole loaf.
            "slices", "slice" -> f.each.takeIf { it in 1.0..60.0 } ?: 35.0
            "can" -> f.pack.takeIf { it > 0 } ?: 425.0
            "pkg", "box" -> f.pack.takeIf { it > 0 } ?: 340.0
            "bag" -> f.pack.takeIf { it > 0 } ?: 340.0
            "jar" -> f.pack.takeIf { it > 0 } ?: 680.0
            "bunch" -> f.pack.takeIf { it > 0 } ?: 100.0
            "" -> f.each.takeIf { it > 0 } ?: f.pack.takeIf { it > 0 } ?: return null
            else -> return null
        }
        return one * i.q
    }

    private val cache = HashMap<Pair<String, Int>, Int?>()

    /** Estimated calories per serving for a list of ingredients that serves [servings]. */
    fun estimate(key: String, ings: List<Ingredient>, servings: Int): Int? = cache.getOrPut(key to (ings.hashCode() * 31 + servings)) {
        var total = 0.0
        var known = 0
        for (i in ings) {
            val c = calories(i) ?: continue
            total += c
            known++
        }
        if (known == 0) null else Math.round(total / servings.coerceAtLeast(1)).toInt()
    }

    /** Calories per serving for a recipe: the site's number when we have it, otherwise an estimate. */
    fun perServing(r: Recipe): Int? = r.calories ?: estimate("r${r.id}", r.ingredients, r.base)

    /** Calories per serving of a side (sides are written for 4); store-bought sides are unknown. */
    fun side(sd: Side?): Int? = sd?.takeIf { it.ingredients.isNotEmpty() }?.let { estimate("s${it.key}", it.ingredients, 4) }
}
