package com.weeklymeals

import kotlin.math.ceil

/**
 * Meat sold by the pound comes in whole packs (a value pack of chicken can be 3-6 lb).
 * This works out how big a pack is and what you'll actually pay for it.
 *
 * Pack weight, best first: what you set for that product > the store listing's size >
 * a typical weight for that kind of pack (marked "≈").
 */
object Packs {
    /** How the pack weight was found. */
    enum class Source { YOU, LISTING, TYPICAL }

    /** [lb] per pack; [perLb] = the price is per pound (fresh meat) rather than per pack. */
    data class Pack(val lb: Double, val perLb: Boolean, val source: Source)

    private val regular = listOf(
        "bone-in skin-on chicken thigh" to 2.5, "chicken breast" to 2.5, "chicken thigh" to 2.0,
        "chicken tenderloin" to 1.5, "chicken wing" to 2.5, "drumstick" to 2.5, "whole chicken" to 4.5,
        "ground beef" to 1.0, "ground turkey" to 1.0, "ground chicken" to 1.0, "ground pork" to 1.0,
        "chuck roast" to 3.0, "beef chuck" to 3.0, "stew meat" to 1.5, "sirloin" to 1.0, "flank steak" to 1.5,
        "skirt steak" to 1.5, "ribeye" to 1.0, "cube steak" to 1.0, "steak" to 1.0,
        "pork chop" to 1.5, "pork loin" to 3.0, "pork tenderloin" to 1.25, "pork shoulder" to 4.0, "ham steak" to 1.0,
        "salmon" to 1.0, "cod" to 1.0, "tilapia" to 1.0, "white fish" to 1.0, "shrimp" to 1.0, "chicken" to 3.0,
    )
    private val valuePack = listOf(
        "chicken breast" to 3.5, "chicken thigh" to 4.0, "chicken wing" to 4.0, "chicken tenderloin" to 3.0,
        "drumstick" to 4.0, "ground beef" to 3.0, "ground turkey" to 3.0, "ground chicken" to 3.0,
        "pork chop" to 3.0, "stew meat" to 3.0, "steak" to 2.5,
    )
    private val packWord = Regex("value pack|family pack|club pack|mega pack|bulk", RegexOption.IGNORE_CASE)

    private fun lookup(list: List<Pair<String, Double>>, name: String): Double? {
        val n = name.lowercase()
        return list.filter { n.contains(it.first) }.maxByOrNull { it.first.length }?.second
    }

    /** True for fresh meat and fish bought by weight (not bacon, sausage links, deli meat). */
    fun weighed(term: String): Boolean = lookup(regular, term) != null

    /** The pack for a product (title and size from the store), with your saved weight if any. */
    fun packFor(term: String, title: String?, size: String?, yours: Double?): Pack {
        val sized = size?.let { Pantry.parseSize(it) }?.let { (n, u) ->
            when (u) { "lb", "lbs" -> n; "oz" -> n / 16; "kg" -> n * 2.2046; "g" -> n / 453.6; else -> null }
        }
        // A listed weight over 1 lb is a fixed-size pack, priced per pack (like a 3 lb frozen bag).
        val fixed = sized != null && sized > 1.05
        val perLb = !fixed
        if (yours != null && yours > 0) return Pack(yours, perLb, Source.YOU)
        if (fixed) return Pack(sized!!, false, Source.LISTING)
        val typical = if (title != null && packWord.containsMatchIn(title)) lookup(valuePack, term) ?: 3.0
        else lookup(regular, term) ?: 1.0
        return Pack(typical, true, Source.TYPICAL)
    }

    /** Whole packs needed for [needLb], and what they cost at [price] (per lb or per pack). */
    fun cost(needLb: Double, price: Double, pack: Pack): Pair<Int, Double> {
        val packs = ceil(needLb / pack.lb - 1e-6).toInt().coerceAtLeast(1)
        return packs to if (pack.perLb) packs * pack.lb * price else packs * price
    }

    fun label(pack: Pack): String =
        (if (pack.source == Source.TYPICAL) "≈" else "") + Logic.fmt(pack.lb) + " lb pack"
}
