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

/** One online search result. */
data class WebRecipe(val id: Int, val title: String, val minutes: Int?, val source: String)

/** A full online recipe, ready to preview before saving. */
data class WebRecipeDetail(
    val id: Int,
    val title: String,
    val emoji: String,
    val servings: Int,
    val minutes: Int?,
    val source: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val tags: List<String>,
)

class SpoonacularException(msg: String) : Exception(msg)

/** Recipe search through the Spoonacular API (spoonacular.com/food-api). */
object Spoonacular {
    private const val BASE = "https://api.spoonacular.com"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(url: Uri): String {
        val req = Request.Builder().url(url.toString()).build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> throw SpoonacularException("Your Spoonacular key wasn't accepted. Check it in Settings.")
                resp.code == 402 -> throw SpoonacularException("Today's free Spoonacular searches are used up. Try again tomorrow.")
                !resp.isSuccessful -> throw SpoonacularException("Spoonacular said ${resp.code}")
            }
            return body
        }
    }

    suspend fun search(key: String, query: String): List<WebRecipe> = withContext(Dispatchers.IO) {
        val url = Uri.parse("$BASE/recipes/complexSearch").buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("number", "10")
            .appendQueryParameter("instructionsRequired", "true")
            .appendQueryParameter("addRecipeInformation", "true")
            .appendQueryParameter("apiKey", key.trim())
            .build()
        val results = JSONObject(get(url)).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            WebRecipe(
                id = o.optInt("id"),
                title = clean(o.optString("title")),
                minutes = o.optInt("readyInMinutes", 0).takeIf { it > 0 },
                source = clean(o.optString("sourceName")),
            )
        }.filter { it.id != 0 && it.title.isNotBlank() }
    }

    suspend fun details(key: String, id: Int): WebRecipeDetail = withContext(Dispatchers.IO) {
        val url = Uri.parse("$BASE/recipes/$id/information").buildUpon()
            .appendQueryParameter("includeNutrition", "false")
            .appendQueryParameter("apiKey", key.trim())
            .build()
        val o = JSONObject(get(url))
        val title = clean(o.optString("title"))

        val ingArr = o.optJSONArray("extendedIngredients") ?: JSONArray()
        val ingredients = (0 until ingArr.length()).mapNotNull { i ->
            val ing = ingArr.optJSONObject(i) ?: return@mapNotNull null
            val us = ing.optJSONObject("measures")?.optJSONObject("us")
            val amount = us?.optDouble("amount") ?: ing.optDouble("amount")
            val unit = us?.optString("unitShort").orEmpty().ifBlank { ing.optString("unit") }
            val name = ing.optString("nameClean").ifBlank { ing.optString("name") }
            val line = if (amount.isNaN() || amount <= 0) ing.optString("original") else "${fmt(amount)} $unit $name"
            Logic.parseIngredient(line)
        }.distinctBy { "${it.name.lowercase()}|${it.u}" }

        val steps = mutableListOf<String>()
        val analyzed = o.optJSONArray("analyzedInstructions") ?: JSONArray()
        for (i in 0 until analyzed.length()) {
            val list = analyzed.optJSONObject(i)?.optJSONArray("steps") ?: continue
            for (j in 0 until list.length()) {
                list.optJSONObject(j)?.optString("step")?.let { clean(it) }?.takeIf { it.isNotBlank() }?.let { steps += it }
            }
        }
        if (steps.isEmpty()) {
            steps += Html.fromHtml(o.optString("instructions").replace(Regex("</(li|p)>", RegexOption.IGNORE_CASE), "\n"), Html.FROM_HTML_MODE_LEGACY)
                .toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }

        WebRecipeDetail(
            id = id,
            title = title,
            emoji = Importer.emojiFor(title),
            servings = o.optInt("servings", 4).coerceIn(1, 20),
            minutes = o.optInt("readyInMinutes", 0).takeIf { it > 0 },
            source = clean(o.optString("sourceName")),
            ingredients = ingredients,
            steps = steps,
            tags = tagsFor(title, steps, o.optBoolean("cheap"), o.optDouble("pricePerServing")),
        )
    }

    /** Cooking-method and Budget badges, guessed from the title and steps. */
    private fun tagsFor(title: String, steps: List<String>, cheap: Boolean, pricePerServing: Double): List<String> {
        val t = (title + " " + steps.joinToString(" ")).lowercase()
        val tags = mutableListOf<String>()
        if (Regex("slow cooker|crock[- ]?pot").containsMatchIn(t)) tags += "crockpot"
        if (Regex("instant pot|instapot|pressure cook").containsMatchIn(t)) tags += "instantpot"
        if (Regex("air[- ]?fr(y|ie)").containsMatchIn(t)) tags += "airfryer"
        if (Regex("sheet[- ]pan").containsMatchIn(t)) tags += "sheetpan"
        if (Regex("\\bgrill").containsMatchIn(t)) tags += "grill"
        if (Regex("one[- ](pan|pot|skillet)").containsMatchIn(t)) tags += "onepan"
        // Spoonacular's price is in cents per serving.
        if (cheap || (!pricePerServing.isNaN() && pricePerServing in 1.0..200.0)) tags += "budget"
        return tags
    }

    private fun fmt(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
}
