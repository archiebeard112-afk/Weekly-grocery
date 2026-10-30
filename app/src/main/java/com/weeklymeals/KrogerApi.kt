package com.weeklymeals

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val upc: String,
    val title: String,
    val size: String,
    /** Regular price. */
    val price: Double?,
    /** Sale price, when lower than the regular price. */
    val promo: Double? = null,
    /** Kroger's stock estimate at your store: "HIGH", "LOW", "TEMPORARILY_OUT_OF_STOCK", or null if unknown. */
    val stock: String? = null,
    /** Whether it can be delivered from your store; null if Kroger didn't say. */
    val delivery: Boolean? = null,
    /** Product photo (front of the package), or "" if King Soopers has none. */
    val image: String = "",
) {
    /** In stock (or unknown), priced at your store, and deliverable (or unknown). */
    val available: Boolean
        get() = price != null && stock != "TEMPORARILY_OUT_OF_STOCK" && delivery != false

    val lowStock: Boolean get() = stock == "LOW"
}

data class TokenResult(val accessToken: String, val refreshToken: String, val expiresAt: Long)

data class StoreResult(val id: String, val name: String)

class KrogerException(message: String) : Exception(message)

/** Thin wrapper around the public Kroger API (King Soopers is a Kroger brand). */
class KrogerApi {
    companion object {
        const val BASE = "https://api.kroger.com/v1"
        const val REDIRECT = "weeklymeals://kroger"
        const val USER_SCOPES = "product.compact cart.basic:write"
    }

    private val http = OkHttpClient()

    private fun basic(id: String, secret: String): String =
        "Basic " + Base64.encodeToString("$id:$secret".toByteArray(), Base64.NO_WRAP)

    private fun call(req: Request): String {
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw KrogerException("Kroger said ${resp.code}: ${body.take(200)}")
            }
            return body
        }
    }

    private fun parseToken(body: String, oldRefresh: String = ""): TokenResult {
        val j = JSONObject(body)
        val expires = j.optLong("expires_in", 1800)
        return TokenResult(
            accessToken = j.getString("access_token"),
            refreshToken = j.optString("refresh_token", oldRefresh),
            expiresAt = System.currentTimeMillis() + (expires - 60) * 1000,
        )
    }

    fun authorizeUrl(clientId: String, state: String): Uri =
        Uri.parse("$BASE/connect/oauth2/authorize").buildUpon()
            .appendQueryParameter("scope", USER_SCOPES)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("state", state)
            .build()

    suspend fun exchangeCode(id: String, secret: String, code: String): TokenResult =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT)
                .build()
            val req = Request.Builder().url("$BASE/connect/oauth2/token")
                .header("Authorization", basic(id, secret)).post(form).build()
            parseToken(call(req))
        }

    suspend fun refresh(id: String, secret: String, refreshToken: String): TokenResult =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
            val req = Request.Builder().url("$BASE/connect/oauth2/token")
                .header("Authorization", basic(id, secret)).post(form).build()
            parseToken(call(req), refreshToken)
        }

    /** App-only token for searching stores and products. */
    suspend fun clientToken(id: String, secret: String): String =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("scope", "product.compact")
                .build()
            val req = Request.Builder().url("$BASE/connect/oauth2/token")
                .header("Authorization", basic(id, secret)).post(form).build()
            parseToken(call(req)).accessToken
        }

    /** Finds the nearest King Soopers to a ZIP code. */
    suspend fun findStore(token: String, zip: String): StoreResult? =
        withContext(Dispatchers.IO) {
            val url = Uri.parse("$BASE/locations").buildUpon()
                .appendQueryParameter("filter.zipCode.near", zip.trim())
                .appendQueryParameter("filter.radiusInMiles", "50")
                .appendQueryParameter("filter.limit", "50")
                .build().toString()
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json").build()
            val data = JSONObject(call(req)).optJSONArray("data") ?: JSONArray()
            // Results come back nearest first. Prefer the closest King Soopers,
            // otherwise fall back to the closest Kroger-family store.
            val stores = (0 until data.length()).map { data.getJSONObject(it) }
                .filter { it.optString("locationId").isNotEmpty() }
            fun isKingSoopers(o: JSONObject): Boolean {
                val chain = o.optString("chain").replace(" ", "").uppercase()
                val name = o.optString("name").replace(" ", "").uppercase()
                return chain.contains("KINGSOOPERS") || name.contains("KINGSOOPERS")
            }
            val pick = stores.firstOrNull { isKingSoopers(it) } ?: stores.firstOrNull()
            pick?.let { StoreResult(it.getString("locationId"), it.optString("name", "King Soopers")) }
        }

    suspend fun searchProducts(token: String, storeId: String, term: String, limit: Int = 50): List<Product> =
        withContext(Dispatchers.IO) {
            val url = Uri.parse("$BASE/products").buildUpon()
                .appendQueryParameter("filter.term", term)
                .appendQueryParameter("filter.locationId", storeId)
                .appendQueryParameter("filter.limit", limit.toString())
                .build().toString()
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json").build()
            parseProducts(call(req))
        }

    /** Products by Kroger product id (a scanned UPC without its check digit, padded to 13). */
    suspend fun productsById(token: String, storeId: String, ids: List<String>): List<Product> =
        withContext(Dispatchers.IO) {
            val url = Uri.parse("$BASE/products").buildUpon()
                .appendQueryParameter("filter.productId", ids.joinToString(","))
                .appendQueryParameter("filter.locationId", storeId)
                .build().toString()
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json").build()
            parseProducts(call(req))
        }

    /** The front photo (or the featured one), medium size if there is one. */
    private fun photoUrl(images: JSONArray?): String {
        if (images == null || images.length() == 0) return ""
        val all = (0 until images.length()).mapNotNull { images.optJSONObject(it) }
        val pick = all.firstOrNull { it.optBoolean("featured") } ?: all.firstOrNull { it.optString("perspective") == "front" } ?: all.first()
        val sizes = pick.optJSONArray("sizes") ?: return ""
        val list = (0 until sizes.length()).mapNotNull { sizes.optJSONObject(it) }
        val best = listOf("medium", "small", "large", "thumbnail", "xlarge")
            .firstNotNullOfOrNull { want -> list.firstOrNull { it.optString("size") == want } } ?: list.firstOrNull()
        return best?.optString("url").orEmpty()
    }

    private fun parseProducts(body: String): List<Product> {
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val out = mutableListOf<Product>()
        for (n in 0 until data.length()) {
            val o = data.getJSONObject(n)
            val item = o.optJSONArray("items")?.optJSONObject(0)
            val priceObj = item?.optJSONObject("price")
            val price = priceObj?.let { if (it.has("regular")) it.optDouble("regular") else null }
            val promo = priceObj?.optDouble("promo", 0.0)?.takeIf { p -> p > 0 && price != null && p < price }
            out += Product(
                upc = o.optString("upc"),
                title = o.optString("description"),
                size = item?.optString("size").orEmpty(),
                price = price,
                promo = promo,
                stock = item?.optJSONObject("inventory")?.optString("stockLevel")?.ifBlank { null },
                delivery = item?.optJSONObject("fulfillment")?.let { f ->
                    if (f.has("delivery")) f.optBoolean("delivery") else null
                },
                image = photoUrl(o.optJSONArray("images")),
            )
        }
        return out
    }

    /** Adds products to the signed-in user's King Soopers cart. */
    suspend fun addToCart(userToken: String, items: List<Pair<String, Int>>) =
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            items.forEach { (upc, qty) ->
                arr.put(JSONObject().put("upc", upc).put("quantity", qty))
            }
            val body = JSONObject().put("items", arr).toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$BASE/cart/add")
                .header("Authorization", "Bearer $userToken")
                .header("Accept", "application/json")
                .put(body).build()
            call(req)
            Unit
        }
}
