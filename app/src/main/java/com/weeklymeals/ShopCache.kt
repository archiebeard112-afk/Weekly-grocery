package com.weeklymeals

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One Shop item's King Soopers results and your pick, as saved on the phone. */
@Serializable
data class SavedLine(
    val term: String,
    val search: String = term,
    val note: String? = null,
    val options: List<Product> = emptyList(),
    val selected: Int = 0,
    val quantity: Int = 1,
    val needText: String = "",
)

/**
 * The Shop tab's King Soopers lookups for one week at one store, so opening the tab again
 * (even after the app was closed) doesn't search the store again. A new week starts fresh.
 */
@Serializable
data class ShopCache(
    /** First day of the week these were looked up in, e.g. "2026-10-27". */
    val week: String = "",
    val store: String = "",
    val lines: Map<String, SavedLine> = emptyMap(),
)

object ShopCacheFile {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(context: Context) = File(context.filesDir, "shop_cache.json")

    fun load(context: Context): ShopCache =
        runCatching { json.decodeFromString(ShopCache.serializer(), file(context).readText()) }.getOrDefault(ShopCache())

    fun save(context: Context, cache: ShopCache) {
        runCatching {
            val f = file(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(ShopCache.serializer(), cache))
            tmp.renameTo(f)
        }
    }
}

fun CartLine.saved() = SavedLine(term, search, note, options, selected, quantity, needText)

fun SavedLine.line() = CartLine(
    term = term, search = search, note = note, options = options, selected = selected.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
    quantity = quantity, needText = needText,
)
