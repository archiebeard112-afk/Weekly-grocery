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
    /** Cooking method / budget labels, e.g. "crockpot", "instantpot", "budget". */
    val tags: List<String> = emptyList(),
    /** Where an online recipe came from, e.g. "sp:123" or "ar:<link>" ("" = not from online). */
    val webRef: String = "",
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
    /** Which day each planned recipe is on: recipe id -> 0 (Mon) .. 6 (Sun). */
    val days: Map<Int, Int> = emptyMap(),
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
    /** Snacks picked for this week (by name). */
    val snacks: Set<String> = emptySet(),
    /** Snacks picked last week, for quick re-adding. */
    val lastSnacks: Set<String> = emptySet(),
    /** Snacks you added yourself. */
    val customSnacks: List<String> = emptyList(),
    /** Things you buy most weeks, offered before adding to the cart. */
    val staples: List<String> = Staples.DEFAULT,
    /** Usual items you picked last time (pre-checked next time). */
    val lastStaples: Set<String> = emptySet(),
    /** Usual items picked for this week's list. */
    val pickedStaples: Set<String> = emptySet(),
    /** One-off items typed into the Shop tab this week. */
    val extraItems: List<String> = emptyList(),
    /** Last week's one-off items, offered as Quick add chips. */
    val lastExtraItems: List<String> = emptyList(),
    /** Extras you've sent to the cart through the app, by store search term (drives Quick add). */
    val bought: Map<String, Bought> = emptyMap(),
    /** True once Quick add has been seeded from your earlier snacks and usual items. */
    val boughtSeeded: Boolean = false,
    /** Shop items marked "Already have" (by store search term); left out of the cart. */
    val haveTerms: Set<String> = emptySet(),
    /** Today's sale prices for main ingredients. */
    val sales: SalesCache = SalesCache(),
    /** Store-bought sides you've picked for each recipe (offered again next time). */
    val storeSides: Map<Int, List<StoreSide>> = emptyMap(),
    /** Spoonacular API key, for searching recipes online. */
    val spoonKey: String = "",
    /** Online recipe search: "spoonacular", "allrecipes", "mealdb", "off", or "" (automatic). */
    val webSource: String = "",
    /** How online results are sorted: "top", "reviews" or "match". */
    val webSort: String = "top",
)

/** A King Soopers product used as a side (e.g. a box of mac & cheese). Its side key is "buy:<upc>". */
@Serializable
data class StoreSide(val upc: String, val title: String)

@Serializable
data class PastWeek(
    /** When the week was finished, in milliseconds. */
    val date: Long,
    val recipes: List<Int>,
    val sides: Map<Int, String> = emptyMap(),
    val servings: Int = 2,
)

/** An extra item you've bought through the app: how often and when. */
@Serializable
data class Bought(
    val name: String,
    val count: Int = 1,
    val last: Long = 0L,
)
