package com.weeklymeals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Leftovers inside one planned meal: the rest of something an earlier meal opened (½ bag of spinach
 * from Monday's chili), or something this meal opens and a later meal finishes.
 */
data class MealLeft(
    val term: String,
    /** How much this meal uses, e.g. "½ lb". */
    val amount: String,
    /** The meal it comes from ([usesUp]) or the meal the rest goes to. */
    val other: Int,
    /** True when this meal uses up what an earlier meal left. */
    val usesUp: Boolean,
)

/** Same as the Week tab's LEFTOVERS tag: shares where this meal isn't the first to use the package. */
fun MealsViewModel.mealLeftovers(st: UserState, id: Int): List<MealLeft> {
    if (id !in st.week) return emptyList()
    return weekShares(st).values.mapNotNull { s ->
        val i = s.meals.indexOf(id)
        when {
            i > 0 -> MealLeft(s.term, s.amounts[id].orEmpty(), s.meals.first(), true)
            i == 0 && s.meals.size > 1 -> MealLeft(s.term, s.amounts[id].orEmpty(), s.meals[1], false)
            else -> null
        }
    }
}

/** The words to look for in the steps: each leftover ingredient as the recipe writes it, and its shopping term. */
fun leftoverNames(left: List<MealLeft>, r: Recipe, side: Side?): List<String> {
    val terms = left.filter { it.usesUp }.map { it.term }.toSet()
    if (terms.isEmpty()) return emptyList()
    val ings = r.ingredients + (side?.ingredients ?: emptyList())
    return (ings.filter { Logic.searchTerm(it.name) in terms }.map { it.name } + terms).distinct()
}

/** Dark green chip behind leftover words (same as the LEFTOVERS tag on the Week tab). */
internal val LeftoverBg = Color(0xFF26301A)

/** The leftover icon (circling arrows), as on a left-over price. */
@Composable
internal fun LeftoverIcon(modifier: Modifier = Modifier.size(16.dp), tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(Icons.Filled.Refresh, "Leftover", modifier, tint = tint)
}

/**
 * A step with its highlighted amounts, and every leftover ingredient in a small lime chip
 * (the note under the step says where it's left over from).
 */
@Composable
internal fun StepText(
    pieces: List<Cook.Piece>, leftNames: List<String>, highlight: Color, style: TextStyle,
    modifier: Modifier = Modifier, prefix: (AnnotatedString.Builder.() -> Unit)? = null,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
) {
    val text = pieces.joinToString("") { it.text }
    val amounts = mutableListOf<IntRange>()
    var at = 0
    pieces.forEach { p -> if (p.isAmount && p.text.isNotEmpty()) amounts += at until at + p.text.length; at += p.text.length }
    val left = Cook.mentions(text, leftNames)
    val cuts = (listOf(0, text.length) + (amounts + left).flatMap { listOf(it.first, it.last + 1) }).distinct().sorted()
    val chip = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        prefix?.invoke(this)
        cuts.zipWithNext().forEach { (a, b) ->
            if (b <= a) return@forEach
            val inLeft = left.any { a >= it.first && a <= it.last }
            val inAmount = amounts.any { a >= it.first && a <= it.last }
            val st = when {
                inLeft -> SpanStyle(background = LeftoverBg, color = if (inAmount) highlight else chip, fontWeight = FontWeight.SemiBold)
                inAmount -> SpanStyle(color = highlight, fontWeight = FontWeight.Bold)
                else -> null
            }
            if (st != null) withStyle(st) { append(text.substring(a, b)) } else append(text.substring(a, b))
        }
    }
    Text(annotated, style = style, textDecoration = textDecoration, modifier = modifier)
}
