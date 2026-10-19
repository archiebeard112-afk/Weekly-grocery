package com.weeklymeals

/**
 * What 8 weeks of your King Soopers receipts (Aug 29 – Oct 17, 2026) showed:
 * the products you actually buy (used as your default pick on the Shop list),
 * and the extras you buy most (seeded into Quick add).
 */
object Receipts {
    /** Your usual product for a shopping item: keyword in the item, UPC, label, and what to search. */
    private class Usual(val match: Regex, val upc: String, val label: String, val search: String)

    private val usuals = listOf(
        Usual(Regex("chicken breast"), "0028334900000", "Heritage Farm Boneless Skinless Chicken Breasts", "heritage farm boneless skinless chicken breasts"),
        Usual(Regex("chicken thigh"), "0025313650000", "Heritage Farm Boneless Skinless Chicken Thighs", "heritage farm boneless skinless chicken thighs"),
        Usual(Regex("ground beef"), "0001111096972", "Kroger 80/20 Ground Beef 1 lb", "kroger 80/20 ground beef"),
        Usual(Regex("ground turkey"), "0001111096874", "Kroger 93/7 Lean Ground Turkey 1 lb", "kroger ground turkey"),
        Usual(Regex("pork chop"), "0029210250000", "Kroger Pork Loin Assorted End Chops", "kroger pork chops"),
        Usual(Regex("^(whole )?carrots?$"), "0001111091622", "Kroger Whole Carrots, 2 lb", "kroger whole carrots 2 lb"),
        Usual(Regex("^(salted |unsalted )?butter$"), "0001111089301", "Kroger Salted Butter Sticks", "kroger salted butter sticks"),
        Usual(Regex("^(2% )?milk$|whole milk"), "0001111050236", "King Soopers 2% Milk Half Gallon", "king soopers 2% milk half gallon"),
        Usual(Regex("sour cream"), "0001111016371", "Kroger Original Sour Cream, 8 oz", "kroger sour cream"),
        Usual(Regex("baby spinach|^spinach$"), "0001111091128", "Simple Truth Organic Baby Spinach, 5 oz", "simple truth baby spinach"),
        Usual(Regex("russet|^potatoes?$"), "0001111091754", "Kroger Russet Potatoes, 10 lb", "kroger russet potatoes"),
        Usual(Regex("mushroom"), "0001111091493", "Kroger Sliced Baby Bella Mushrooms", "kroger baby bella mushrooms"),
        Usual(Regex("lime juice"), "0003084900001", "Sicilia Organic Lime Juice, 4 oz", "sicilia lime juice"),
        Usual(Regex("coconut milk"), "0073762801150", "Thai Kitchen Unsweetened Coconut Milk", "thai kitchen coconut milk"),
        Usual(Regex("flour tortilla"), "0007373100830", "Mission Super Soft Flour Tortillas, Fajita Size", "mission flour tortillas fajita"),
        // Quick add extras.
        Usual(Regex("pedigree|dog food"), "0002310001527", "Pedigree Choice Cuts in Gravy Beef", "pedigree choice cuts"),
        Usual(Regex("kool-aid"), "0004300000573", "Kool-Aid Zero Sugar Grape Water Enhancer", "kool-aid zero"),
        Usual(Regex("don julio|spicy tortilla chips"), "0079309910240", "Don Julio Hint of Heat Tortilla Chips", "don julio tortilla chips"),
        Usual(Regex("garlic bread"), "0004157362075", "Whole Loaf Garlic Bread", "whole loaf garlic bread"),
        Usual(Regex("hawaiian (sandwich )?buns?"), "0001111011454", "Private Selection Sweet Hawaiian Sandwich Buns", "private selection hawaiian buns"),
        Usual(Regex("trident"), "0007339005530", "Trident Tropical Twist Gum", "trident tropical twist"),
        Usual(Regex("pretzel crisps"), "0004950800140", "Snack Factory Original Pretzel Crisps", "snack factory pretzel crisps"),
        Usual(Regex("ready rice|rice pouch"), "0001111013822", "Kroger 90 Second Basmati Rice Pouch", "90 second basmati rice"),
        Usual(Regex("sabra|hummus"), "0004082234616", "Sabra Olive Tapenade Hummus", "sabra hummus"),
        Usual(Regex("cottage cheese"), "0001111044760", "Kroger 4% Small Curd Cottage Cheese", "kroger cottage cheese"),
        Usual(Regex("pickle chicken bites"), "0001111016505", "Kroger Dill Pickle Boneless Chicken Bites", "kroger dill pickle chicken bites"),
        Usual(Regex("saffron road"), "0085002995367", "Saffron Road Beef Birria Rigatoni", "saffron road"),
    )

    /** Your usual product for this shopping item, if the receipts show one. */
    fun usual(term: String): Choice? {
        val t = term.lowercase().trim()
        return usuals.firstOrNull { it.match.containsMatchIn(t) }?.let { Choice(it.upc, it.label, it.search) }
    }

    /** Extras you buy most, with how many of the 8 weeks they showed up (Mountain Dew left out: no delivery). */
    val quickAdd = listOf(
        "Pedigree canned dog food" to 6,
        "2% milk" to 5,
        "Saffron Road frozen meal" to 5,
        "Kool-Aid Zero grape" to 4,
        "Kroger whole carrots" to 4,
        "Hawaiian sandwich buns" to 4,
        "Ready rice pouch" to 4,
        "Cottage cheese" to 4,
        "Theater box candy" to 4,
        "Don Julio spicy tortilla chips" to 3,
        "Whole loaf garlic bread" to 3,
        "Trident gum" to 3,
        "Pretzel crisps" to 3,
        "Sabra hummus" to 3,
        "Dill pickle chicken bites" to 2,
    )

    /** Oct 17, 2026: the newest receipt (for Quick add's "recent" boost). */
    const val LATEST = 1_792_195_200_000L
}
