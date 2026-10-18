package com.weeklymeals

import android.net.Uri
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One online search result. [id] says where it's from: "sp:<id>", "ar:<link>" or "md:<id>". */
data class WebRecipe(
    val id: String,
    val title: String,
    val minutes: Int?,
    val source: String,
    /** Stars out of 5, and how many ratings (or likes, for Spoonacular). */
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val countLabel: String = "ratings",
)

/** A full online recipe, ready to preview before saving. */
data class WebRecipeDetail(
    val id: String,
    val title: String,
    val emoji: String,
    val servings: Int,
    val minutes: Int?,
    val source: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val tags: List<String>,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val countLabel: String = "ratings",
    val reviews: List<Review> = emptyList(),
)

class WebSearchException(msg: String) : Exception(msg)

/** The online recipe sites you can search from Browse. */
object RecipeSources {
    const val SPOONACULAR = "spoonacular"
    const val ALLRECIPES = "allrecipes"
    const val MEALDB = "mealdb"
    const val OFF = "off"

    val CHOICES = listOf(
        SPOONACULAR to "Spoonacular",
        ALLRECIPES to "Allrecipes",
        MEALDB to "TheMealDB",
        OFF to "Off",
    )

    // Sorting online results.
    const val SORT_MATCH = "match"
    const val SORT_TOP = "top"
    const val SORT_REVIEWS = "reviews"
    val SORTS = listOf(SORT_MATCH to "Best match", SORT_TOP to "Top rated", SORT_REVIEWS to "Most reviews")

    fun sort(list: List<WebRecipe>, by: String): List<WebRecipe> = when (by) {
        // Weighted so a 5-star recipe with 3 ratings doesn't beat 4.8 stars from 5,000.
        SORT_TOP -> list.sortedByDescending { w ->
            val r = w.rating ?: return@sortedByDescending -1.0
            val n = (w.ratingCount ?: 0).toDouble()
            (r * n + 4.0 * 50) / (n + 50)
        }
        SORT_REVIEWS -> list.sortedByDescending { it.ratingCount ?: -1 }
        else -> list
    }

    fun label(source: String) = CHOICES.firstOrNull { it.first == source }?.second ?: ""

    /** The site actually used: your choice, or Spoonacular if you have a key, otherwise Allrecipes. */
    fun effective(st: UserState): String = when (st.webSource) {
        "" -> if (st.spoonKey.isNotBlank()) SPOONACULAR else ALLRECIPES
        else -> st.webSource
    }

    suspend fun search(source: String, key: String, query: String): List<WebRecipe> = when (source) {
        SPOONACULAR -> Spoonacular.search(key, query)
        ALLRECIPES -> Allrecipes.search(query)
        MEALDB -> MealDb.search(query)
        else -> emptyList()
    }

    suspend fun details(ref: String, key: String): WebRecipeDetail = when {
        ref.startsWith("sp:") -> Spoonacular.details(key, ref)
        ref.startsWith("ar:") -> Allrecipes.details(ref)
        ref.startsWith("md:") -> MealDb.details(ref)
        else -> throw WebSearchException("Unknown recipe")
    }

    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    internal fun get(url: String, site: String, browser: Boolean = false): String {
        val b = Request.Builder().url(url)
        if (browser) {
            b.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36")
            b.header("Accept", "text/html")
        }
        http.newCall(b.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when {
                site == "Spoonacular" && resp.code == 401 ->
                    throw WebSearchException("Your Spoonacular key wasn't accepted. Check it in Settings.")
                site == "Spoonacular" && resp.code == 402 ->
                    throw WebSearchException("Today's free Spoonacular searches are used up. Try again tomorrow, or pick another site in Settings.")
                !resp.isSuccessful -> throw WebSearchException("$site said ${resp.code}")
            }
            return body
        }
    }

    /** Cooking-method and Budget badges, guessed from the title and steps. */
    fun tagsFor(title: String, steps: List<String>, budget: Boolean = false): List<String> {
        val t = (title + " " + steps.joinToString(" ")).lowercase()
        val tags = mutableListOf<String>()
        if (Regex("slow cooker|crock[- ]?pot").containsMatchIn(t)) tags += "crockpot"
        if (Regex("instant pot|instapot|pressure cook").containsMatchIn(t)) tags += "instantpot"
        if (Regex("air[- ]?fr(y|ie)").containsMatchIn(t)) tags += "airfryer"
        if (Regex("sheet[- ]pan").containsMatchIn(t)) tags += "sheetpan"
        if (Regex("\\bgrill").containsMatchIn(t)) tags += "grill"
        if (Regex("one[- ](pan|pot|skillet)").containsMatchIn(t)) tags += "onepan"
        if (budget) tags += "budget"
        return tags
    }

    fun fmt(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    /** Ingredient lines -> ingredients, dropping duplicates. */
    fun ingredients(lines: List<String>): List<Ingredient> =
        lines.mapNotNull { Logic.parseIngredient(it) }.distinctBy { "${it.name.lowercase()}|${it.u}" }
}

/** spoonacular.com/food-api: needs a free key. */
object Spoonacular {
    private const val BASE = "https://api.spoonacular.com"

    suspend fun search(key: String, query: String): List<WebRecipe> = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw WebSearchException("Add your Spoonacular key in Settings, or pick another site.")
        val url = Uri.parse("$BASE/recipes/complexSearch").buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("number", "10")
            .appendQueryParameter("instructionsRequired", "true")
            .appendQueryParameter("addRecipeInformation", "true")
            .appendQueryParameter("apiKey", key.trim())
            .build().toString()
        val results = JSONObject(RecipeSources.get(url, "Spoonacular")).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            WebRecipe(
                id = "sp:" + o.optInt("id"),
                title = RecipeSources.clean(o.optString("title")),
                minutes = o.optInt("readyInMinutes", 0).takeIf { it > 0 },
                source = RecipeSources.clean(o.optString("sourceName")).ifBlank { "Spoonacular" },
                // Spoonacular has no star ratings: its 0-100 score shown as stars, and likes.
                rating = o.optDouble("spoonacularScore").takeIf { !it.isNaN() && it > 0 }?.let { Math.round(it / 2.0) / 10.0 },
                ratingCount = o.optInt("aggregateLikes", -1).takeIf { it >= 0 },
                countLabel = "likes",
            )
        }.filter { it.id != "sp:0" && it.title.isNotBlank() }
    }

    suspend fun details(key: String, ref: String): WebRecipeDetail = withContext(Dispatchers.IO) {
        val id = ref.removePrefix("sp:")
        val url = Uri.parse("$BASE/recipes/$id/information").buildUpon()
            .appendQueryParameter("includeNutrition", "false")
            .appendQueryParameter("apiKey", key.trim())
            .build().toString()
        val o = JSONObject(RecipeSources.get(url, "Spoonacular"))
        val title = RecipeSources.clean(o.optString("title"))

        val ingArr = o.optJSONArray("extendedIngredients") ?: JSONArray()
        val lines = (0 until ingArr.length()).mapNotNull { i ->
            val ing = ingArr.optJSONObject(i) ?: return@mapNotNull null
            val us = ing.optJSONObject("measures")?.optJSONObject("us")
            val amount = us?.optDouble("amount") ?: ing.optDouble("amount")
            val unit = us?.optString("unitShort").orEmpty().ifBlank { ing.optString("unit") }
            val name = ing.optString("nameClean").ifBlank { ing.optString("name") }
            if (amount.isNaN() || amount <= 0) ing.optString("original") else "${RecipeSources.fmt(amount)} $unit $name"
        }

        val steps = mutableListOf<String>()
        val analyzed = o.optJSONArray("analyzedInstructions") ?: JSONArray()
        for (i in 0 until analyzed.length()) {
            val list = analyzed.optJSONObject(i)?.optJSONArray("steps") ?: continue
            for (j in 0 until list.length()) {
                list.optJSONObject(j)?.optString("step")?.let { RecipeSources.clean(it) }?.takeIf { it.isNotBlank() }?.let { steps += it }
            }
        }
        if (steps.isEmpty()) {
            steps += Html.fromHtml(o.optString("instructions").replace(Regex("</(li|p)>", RegexOption.IGNORE_CASE), "\n"), Html.FROM_HTML_MODE_LEGACY)
                .toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
        // Spoonacular's price is in cents per serving.
        val price = o.optDouble("pricePerServing")
        val budget = o.optBoolean("cheap") || (!price.isNaN() && price in 1.0..200.0)

        WebRecipeDetail(
            id = ref, title = title, emoji = Importer.emojiFor(title),
            servings = o.optInt("servings", 4).coerceIn(1, 20),
            minutes = o.optInt("readyInMinutes", 0).takeIf { it > 0 },
            source = RecipeSources.clean(o.optString("sourceName")).ifBlank { "Spoonacular" },
            ingredients = RecipeSources.ingredients(lines), steps = steps,
            tags = RecipeSources.tagsFor(title, steps, budget),
            rating = o.optDouble("spoonacularScore").takeIf { !it.isNaN() && it > 0 }?.let { Math.round(it / 2.0) / 10.0 },
            ratingCount = o.optInt("aggregateLikes", -1).takeIf { it >= 0 },
            countLabel = "likes",
        )
    }
}

/** allrecipes.com: reads its search page, then the recipe page (same as importing a link). */
object Allrecipes {
    private val linkRx = Regex(
        "<a[^>]+href=\"(https://www\\.allrecipes\\.com/recipe/\\d+/[^\"?#]+)\"[^>]*>(.*?)</a>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val titleRx = Regex("card__title-text[^>]*>(.*?)</span>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    suspend fun search(query: String): List<WebRecipe> = withContext(Dispatchers.IO) {
        val url = Uri.parse("https://www.allrecipes.com/search").buildUpon()
            .appendQueryParameter("q", query).build().toString()
        val html = RecipeSources.get(url, "Allrecipes", browser = true)
        linkRx.findAll(html).mapNotNull { m ->
            val link = m.groupValues[1]
            val title = titleRx.find(m.groupValues[2])?.groupValues?.get(1)?.let { RecipeSources.clean(it) }
                ?: link.trimEnd('/').substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }
            if (title.isBlank()) null else {
                val card = m.groupValues[2]
                WebRecipe("ar:$link", title, null, "Allrecipes", cardStars(card), cardCount(card))
            }
        }.distinctBy { it.id }.take(10).toList()
    }

    /** Stars drawn on a search card: full star icons plus half stars. */
    private fun cardStars(card: String): Double? {
        val full = Regex("icon-star(?![-_\\w])").findAll(card).count()
        val half = Regex("icon-star-half").findAll(card).count()
        return (full + 0.5 * half).takeIf { it > 0 && it <= 5 }
    }

    /** "1,234 Ratings" on a search card. */
    private fun cardCount(card: String): Int? =
        Regex("([\\d,]+)\\s*(?:<[^>]*>\\s*)*Ratings?", RegexOption.IGNORE_CASE).find(card)
            ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

    suspend fun details(ref: String): WebRecipeDetail {
        val r = Importer.fromUrl(ref.removePrefix("ar:"))
        return WebRecipeDetail(
            id = ref, title = r.name, emoji = r.emoji, servings = (r.serves ?: 4).coerceIn(1, 20),
            minutes = r.minutes, source = "Allrecipes",
            ingredients = RecipeSources.ingredients(r.ingredients), steps = r.steps,
            tags = RecipeSources.tagsFor(r.name, r.steps),
            rating = r.rating, ratingCount = r.ratingCount, reviews = r.reviews,
        )
    }
}

/** themealdb.com: free, no key, small catalog. */
object MealDb {
    private const val BASE = "https://www.themealdb.com/api/json/v1/1"

    suspend fun search(query: String): List<WebRecipe> = withContext(Dispatchers.IO) {
        val url = Uri.parse("$BASE/search.php").buildUpon().appendQueryParameter("s", query).build().toString()
        val meals = JSONObject(RecipeSources.get(url, "TheMealDB")).optJSONArray("meals") ?: JSONArray()
        (0 until meals.length()).mapNotNull { i ->
            val o = meals.optJSONObject(i) ?: return@mapNotNull null
            WebRecipe("md:" + o.optString("idMeal"), o.optString("strMeal").trim(), null, "TheMealDB")
        }.filter { it.title.isNotBlank() }.take(10)
    }

    suspend fun details(ref: String): WebRecipeDetail = withContext(Dispatchers.IO) {
        val url = Uri.parse("$BASE/lookup.php").buildUpon().appendQueryParameter("i", ref.removePrefix("md:")).build().toString()
        val o = JSONObject(RecipeSources.get(url, "TheMealDB")).optJSONArray("meals")?.optJSONObject(0)
            ?: throw WebSearchException("Couldn't load this recipe")
        val title = o.optString("strMeal").trim()
        val lines = (1..20).mapNotNull { n ->
            val ing = o.optString("strIngredient$n").takeUnless { it == "null" }.orEmpty().trim()
            val measure = o.optString("strMeasure$n").takeUnless { it == "null" }.orEmpty().trim()
            if (ing.isEmpty()) null else "$measure $ing".trim()
        }
        val steps = o.optString("strInstructions").split(Regex("\\r?\\n+"))
            .map { it.trim().replace(Regex("^(step\\s*)?\\d+[.):]?\\s*", RegexOption.IGNORE_CASE), "") }
            .filter { it.length > 3 }
        WebRecipeDetail(
            id = ref, title = title, emoji = Importer.emojiFor(title), servings = 4, minutes = null,
            source = "TheMealDB", ingredients = RecipeSources.ingredients(lines), steps = steps,
            tags = RecipeSources.tagsFor(title, steps),
        )
    }
}
