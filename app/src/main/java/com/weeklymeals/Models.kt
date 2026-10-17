package com.weeklymeals

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(val q: Double = 0.0, val u: String = "", val name: String)

@Serializable
data class Recipe(
    val id: Int,
    val name: String,
    val emoji: String,
    val base: Int = 4,
    val steps: List<String>,
    val ingredients: List<Ingredient>,
    val sides: List<String> = emptyList(),
    /** Recipes you imported or typed in. These always show in Favorites until you remove them. */
    val imported: Boolean = false,
)

@Serializable
data class Side(
    val key: String,
    val label: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
)

@Serializable
data class RecipeBundle(val recipes: List<Recipe>, val sides: List<Side>)

// ---- Saved user data -------------------------------------------------------

@Serializable
data class StoreInfo(val id: String = "", val name: String = "")

@Serializable
data class Choice(
    val upc: String,
    val label: String,
    /** The search you used to find it, if you typed your own (e.g. "jasmine rice"). */
    val search: String = "",
)

@Serializable
data class KrogerState(
    val clientId: String = "",
    val clientSecret: String = "",
    val zip: String = "",
    val store: StoreInfo = StoreInfo(),
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val pendingState: String = "",
    /** Remembered product picks, keyed by shopping-list item key. */
    val choices: Map<String, Choice> = emptyMap(),
)

@Serializable
data class UserState(
    val week: List<Int> = emptyList(),
    val sides: Map<Int, String> = emptyMap(),
    val favs: Set<Int> = emptySet(),
    val hidden: Set<Int> = emptySet(),
    val checked: Set<String> = emptySet(),
    val servings: Int = 2,
    val custom: List<Recipe> = emptyList(),
    val kroger: KrogerState = KrogerState(),
    /** Past weeks, newest first. */
    val history: List<PastWeek> = emptyList(),
    /** Running cooking timers: key -> time it ends (milliseconds). */
    val timers: Map<String, Long> = emptyMap(),
)

@Serializable
data class PastWeek(
    /** When the week was finished, in milliseconds. */
    val date: Long,
    val recipes: List<Int>,
    val sides: Map<Int, String> = emptyMap(),
    val servings: Int = 2,
)
