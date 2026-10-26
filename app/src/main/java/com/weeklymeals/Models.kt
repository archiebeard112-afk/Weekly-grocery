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
    /** Rating and written reviews saved from the site it came from. */
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val countLabel: String = "ratings",
    val reviews: List<Review> = emptyList(),
    /** Calories per serving from the recipe's site (null = we estimate from ingredients). */
    val calories: Int? = null,
    /** Bread maker settings shown on the recipe page, each "Label: value" (e.g. "Cycle: Sweet"). */
    val machine: List<String> = emptyList(),
)

@Serializable
data class Side(
    val key: String,
    val label: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    /** Rating and reviews, for sides added from an online recipe. */
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val countLabel: String = "ratings",
    val reviews: List<Review> = emptyList(),
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
    /** How much of each meal's side to make: 1 = half, 4 = double (missing = normal). See [Logic.sideServ]. */
    val sideSize: Map<Int, Int> = emptyMap(),
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
    /** Built-in recipes you've hidden (they stop showing in Browse, search and Favorites). */
    val removed: Set<Int> = emptySet(),
    /** Names of recipes you deleted, so past weeks in History can still show them. */
    val deletedNames: Map<Int, String> = emptyMap(),
    /** Side dishes you added from online recipes (key "web:<ref>"). */
    val customSides: List<Side> = emptyList(),
    /** Sides you've used with each recipe beyond its usual ones (offered first next time). */
    val recipeSides: Map<Int, List<String>> = emptyMap(),
    /** Built-in sides you've hidden everywhere. */
    val hiddenSides: Set<String> = emptySet(),
    /** Sides taken off a dish's Recommended list (still under More sides). */
    val notForDish: Map<Int, Set<String>> = emptyMap(),
    /** Your pantry: spices, oils, baking and dry goods with how much is left. */
    val pantry: List<PantryItem> = emptyList(),
    /** Shop items you want to buy even though the pantry has enough (cleared each week). */
    val pantryBuy: Set<String> = emptySet(),
    /** Shop items you left out of the Kitchen when adding to cart (probably only for the week's meals); they start unticked next time. */
    val skipKitchen: Set<String> = emptySet(),
    /** King Soopers products planned as meals this week (frozen pizza), each on a day. Cleared on Finish week. */
    val storeMeals: List<StoreMeal> = emptyList(),
    /** The last 10 store meals you added, offered again in Browse → King Soopers. */
    val recentStoreMeals: List<StoreMeal> = emptyList(),
    /** Where the Browse search looks: "app", "web" (online recipes) or "store" (King Soopers). */
    val browseScope: String = "app",
    /** Cook screen: you folded the "Shared this week" list (it starts open). */
    val sharedClosed: Boolean = false,
    /** Your rating for a meal: 1 = 👍, 2 = 😐 meh (suggested half as often), -1 = 👎 (left out of Browse's ideas), 0 = asked, skipped. */
    val ratings: Map<Int, Int> = emptyMap(),
    /** The day your week starts on: 0 = Monday .. 6 = Sunday. */
    val weekStart: Int = 0,
    /** A meal counts as "on sale" when it saves at least this much. */
    val saleMin: Double = 2.0,
    /** A meal is "budget" when what you'd buy for it costs this much or less. */
    val budgetMax: Double = 15.0,
    /** Pack weights you set for meat sold by the pound (by product UPC, or "term:<name>"), in lb. */
    val packWeights: Map<String, Double> = emptyMap(),
    /** Pieces per pack you set for cuts cooked whole (by product UPC, or "term:<name>"). */
    val packCounts: Map<String, Int> = emptyMap(),
    /** Cuts you cook whole, 1 per person, counted by the piece (e.g. "chicken breast"). */
    val wholeCuts: Set<String> = setOf("chicken breast"),
    /** Meat in the freezer. */
    val freezer: List<FreezerItem> = emptyList(),
    /** The recipe you're cooking (shows the cooking bubble after you leave the cook screen). */
    val cookingId: Int? = null,
    /** When cooking mode was last opened; the bubble goes away 6 hours after. */
    val cookingSince: Long = 0L,
    /** Where you were on the cook screen: scroll position and steps ticked off. */
    val cookScroll: Int = 0,
    val cookDone: Set<Int> = emptySet(),
    /** Where you dragged the cooking bubble (offset from its usual corner, in pixels). */
    val bubbleX: Float = 0f,
    val bubbleY: Float = 0f,
    /** Pantry shelves you've opened up (the rest show just their first row). */
    val openShelves: Set<String> = emptySet(),
    /** Asked once whether the cooking bubble may float over other apps. */
    val overlayAsked: Boolean = false,
    /** A version you chose to skip in the update pop-up (0 = none). */
    val skippedVersion: Int = 0,
    /** True once Quick add has been seeded from your receipts. */
    val receiptsSeeded: Boolean = false,
    /** Shared ingredients you used all of in one meal: term -> that meal (the others get their own). */
    val usedAll: Map<String, Int> = emptyMap(),
    /** Desserts planned this week: recipe id -> day 0 (Mon) .. 6 (Sun). They are in [week] too, but don't take a day's dinner spot. */
    val dessertDays: Map<Int, Int> = emptyMap(),
    /** Built-in sides you hearted (sides you added yourself are always in Favorites). */
    val favSides: Set<String> = emptySet(),
    /** What Favorites shows: "all", "main", "side" or "dessert". */
    val favKind: String = "all",
) {
    /** This week's dinners: everything planned except desserts. */
    val mains: List<Int> get() = if (dessertDays.isEmpty()) week else week.filter { it !in dessertDays }
}

/** A King Soopers product used as a side (e.g. a box of mac & cheese). Its side key is "buy:<upc>". */
@Serializable
data class StoreSide(val upc: String, val title: String)

/**
 * A King Soopers product eaten as a meal on a day (a frozen pizza): not a recipe, never saved to recipes or favorites.
 * [day] is 0 (Mon) .. 6 (Sun); recent ones (kept for one-tap re-adding) have day -1.
 */
@Serializable
data class StoreMeal(
    val id: String,
    val upc: String,
    val title: String,
    val size: String = "",
    /** The price when it was added (sale price if there was one). */
    val price: Double? = null,
    val day: Int = -1,
    /** Product photo from King Soopers ("" if none). */
    val image: String = "",
)

@Serializable
data class PastWeek(
    /** When the week was finished, in milliseconds. */
    val date: Long,
    val recipes: List<Int>,
    val sides: Map<Int, String> = emptyMap(),
    val servings: Int = 2,
    /** Store-bought meals that week, like "Wed · Frozen pizza". */
    val storeMeals: List<String> = emptyList(),
    /** Desserts that week: recipe id -> day. */
    val desserts: Map<Int, Int> = emptyMap(),
)

/**
 * Something on a pantry shelf. [measure] is "tsp" (amounts tracked in teaspoons),
 * "count" (cans), or "" (no measuring: [left]/[full] is just a level you set).
 */
@Serializable
data class PantryItem(
    val id: String,
    val name: String,
    /** The shopping-list ingredient it covers, e.g. "thyme". */
    val term: String,
    val shelf: String,
    val kind: String,
    val upc: String = "",
    val size: String = "",
    val measure: String = "",
    val full: Double = 1.0,
    val left: Double = 1.0,
    /** When it was bought (for freshness), 0 = unknown. */
    val addedAt: Long = 0L,
    /** Date from the package (0 = not set): a best-by or sell-by date, see [dateKind]. */
    val bestBy: Long = 0L,
    /** "best" (best-by) or "sell" (sell-by). */
    val dateKind: String = "best",
    /** You chose to keep using it past its date (cleared when the date changes or it's refilled). */
    val useAnyway: Boolean = false,
    /** Ounces in one cup of this food, set by you (0 = let the app guess). Used to turn recipe cups into package weight. */
    val cupOz: Double = 0.0,
) {
    val level: Double get() = if (full <= 0) 0.0 else (left / full).coerceIn(0.0, 1.0)
}

/** Meat in the freezer: [count] pieces for cuts cooked whole, otherwise [lb] pounds. */
@Serializable
data class FreezerItem(
    val id: String,
    val term: String,
    val name: String,
    val count: Int = 0,
    val lb: Double = 0.0,
    /** When it went in (oldest, if more was added later). */
    val frozenAt: Long = 0L,
    /** True when it's counted by the piece. */
    val byCount: Boolean = false,
) {
    fun amount(): Double = if (byCount) count.toDouble() else lb
}

/** An extra item you've bought through the app: how often and when. */
@Serializable
data class Bought(
    val name: String,
    val count: Int = 1,
    val last: Long = 0L,
)
