package com.weeklymeals

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A recipe read from a web page, ready to review on the Add recipe form. */
data class ImportedRecipe(
    val name: String,
    val emoji: String,
    val serves: Int?,
    val ingredients: List<String>,
    val steps: List<String>,
    /** Total time in minutes, if the page says. */
    val minutes: Int? = null,
    /** Average star rating (0-5) and how many ratings, if the page says. */
    val rating: Double? = null,
    val ratingCount: Int? = null,
    /** Written reviews included on the page. */
    val reviews: List<Review> = emptyList(),
)

/** One written review. */
@kotlinx.serialization.Serializable
data class Review(val author: String, val stars: Double?, val text: String)

/**
 * Reads recipes from web pages. Most recipe sites include the recipe in a standard
 * format (schema.org "Recipe" data) for search engines; this finds and reads that.
 */
object Importer {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val scriptRx = Regex(
        "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** First web address in shared text, e.g. from Chrome's Share menu. */
    fun findUrl(text: String): String? = Regex("https?://\\S+").find(text)?.value?.trimEnd(')', '.', ',')

    suspend fun fromUrl(url: String): ImportedRecipe = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url.trim())
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36")
            .header("Accept", "text/html")
            .build()
        val html = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("That site said ${resp.code}")
            resp.body?.string().orEmpty()
        }
        fromHtml(html) ?: throw Exception("Couldn't read this page. Paste the ingredients and steps instead.")
    }

    fun fromHtml(html: String): ImportedRecipe? {
        for (m in scriptRx.findAll(html)) {
            val raw = m.groupValues[1].trim()
            val parsed = runCatching { if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw) }.getOrNull() ?: continue
            val recipe = findRecipe(parsed) ?: continue
            val name = clean(recipe.optString("name"))
            val ings = strings(recipe.opt("recipeIngredient") ?: recipe.opt("ingredients")).map { clean(it) }.filter { it.isNotBlank() }
            val steps = instructions(recipe.opt("recipeInstructions")).map { clean(it) }.filter { it.isNotBlank() }
            if (name.isBlank() || ings.isEmpty()) continue
            val total = minutes(recipe.optString("totalTime"))
                ?: listOfNotNull(minutes(recipe.optString("prepTime")), minutes(recipe.optString("cookTime"))).sum().takeIf { it > 0 }
            val agg = recipe.optJSONObject("aggregateRating")
            val rating = agg?.optString("ratingValue")?.toDoubleOrNull()?.takeIf { it > 0 }
            val count = (agg?.optString("ratingCount")?.toIntOrNull() ?: agg?.optString("reviewCount")?.toIntOrNull())?.takeIf { it > 0 }
            return ImportedRecipe(
                name, emojiFor(name), serves(recipe.opt("recipeYield")), ings, steps, total,
                rating, count, reviews(recipe.opt("review")),
            )
        }
        return null
    }

    /** Finds the node whose @type is (or includes) "Recipe", searching arrays and @graph. */
    private fun findRecipe(node: Any?): JSONObject? = when (node) {
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it)) }
        is JSONObject -> {
            val type = node.opt("@type")
            val isRecipe = when (type) {
                is String -> type.equals("Recipe", true)
                is JSONArray -> (0 until type.length()).any { type.optString(it).equals("Recipe", true) }
                else -> false
            }
            if (isRecipe) node else findRecipe(node.opt("@graph")) ?: findRecipe(node.opt("mainEntity"))
        }
        else -> null
    }

    private fun strings(v: Any?): List<String> = when (v) {
        is JSONArray -> (0 until v.length()).mapNotNull { v.opt(it)?.toString() }
        is String -> v.split("\n")
        else -> emptyList()
    }

    /** Steps can be text, a list of text, a list of HowToStep {text}, or HowToSection groups. */
    private fun instructions(v: Any?): List<String> = when (v) {
        is String -> v.split(Regex("\\n+|(?<=\\.)\\s{2,}"))
        is JSONArray -> (0 until v.length()).flatMap { instructions(v.opt(it)) }
        is JSONObject -> when {
            v.has("itemListElement") -> instructions(v.opt("itemListElement"))
            v.has("text") -> listOf(v.optString("text"))
            v.has("name") -> listOf(v.optString("name"))
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun reviews(v: Any?): List<Review> {
        val list = when (v) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
            is JSONObject -> listOf(v)
            else -> emptyList()
        }
        return list.mapNotNull { r ->
            val text = clean(r.optString("reviewBody").ifBlank { r.optString("description") })
            if (text.isBlank()) return@mapNotNull null
            val author = when (val a = r.opt("author")) {
                is JSONObject -> a.optString("name")
                is JSONArray -> a.optJSONObject(0)?.optString("name").orEmpty()
                is String -> a
                else -> ""
            }.let { clean(it) }.ifBlank { "Someone" }
            val stars = r.optJSONObject("reviewRating")?.optString("ratingValue")?.toDoubleOrNull()
            Review(author, stars, text)
        }
    }

    /** "PT1H30M" -> 90. */
    private fun minutes(iso: String): Int? {
        val m = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE).find(iso.trim()) ?: return null
        val (d, h, min) = m.destructured
        val total = (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }

    private fun serves(v: Any?): Int? {
        val s = when (v) {
            is JSONArray -> (0 until v.length()).map { v.opt(it).toString() }.firstOrNull { Regex("\\d").containsMatchIn(it) }
            null -> null
            else -> v.toString()
        } ?: return null
        return Regex("\\d+").find(s)?.value?.toIntOrNull()?.takeIf { it in 1..50 }
    }

    /** Removes HTML tags and entities (&amp;, &#39;, ...) and extra spaces. */
    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    fun emojiFor(name: String): String {
        val n = name.lowercase()
        return listOf(
            "soup" to "🥣", "stew" to "🍲", "chili" to "🌶️", "taco" to "🌮", "burrito" to "🌯", "enchilada" to "🫔",
            "pizza" to "🍕", "burger" to "🍔", "salad" to "🥗", "curry" to "🍛", "pasta" to "🍝", "spaghetti" to "🍝",
            "lasagna" to "🍝", "noodle" to "🍜", "ramen" to "🍜", "rice" to "🍚", "shrimp" to "🍤", "salmon" to "🐟",
            "fish" to "🐟", "cod" to "🐟", "chicken" to "🍗", "steak" to "🥩", "beef" to "🥩", "pork" to "🍖",
            "sandwich" to "🥪", "egg" to "🍳", "pancake" to "🥞", "waffle" to "🧇", "potato" to "🥔", "pie" to "🥧",
        ).firstOrNull { n.contains(it.first) }?.second ?: "🍽️"
    }
}
