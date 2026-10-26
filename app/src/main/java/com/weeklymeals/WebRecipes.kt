package com.weeklymeals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp

/** The "Online" section under Browse search results. */
@Composable
internal fun OnlineResults(
    vm: MealsViewModel, st: UserState, web: MealsViewModel.WebSearch, query: String,
    onWeb: (String) -> Unit, onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        val source = RecipeSources.effective(st)
        Text(
            "Online · " + RecipeSources.label(source) + if (web.results.isNotEmpty() && web.source == source) " · ${web.results.size}" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        val q = query.trim().lowercase()
        when {
            source == RecipeSources.SPOONACULAR && st.spoonKey.isBlank() -> {
                Text(
                    "Add your free Spoonacular key in Settings, or pick a different site there.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                TextButton(onClick = onSettings) { Text("Open Settings") }
            }
            web.loading || web.query != q || web.source != source -> Row(
                Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Searching online…", style = MaterialTheme.typography.bodyMedium)
            }
            web.error != null -> Text(
                web.error, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            web.results.isEmpty() -> Text(
                "No online recipes found.", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            else -> {
              // Sort chips (only when the site has ratings).
              if (source != RecipeSources.MEALDB) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RecipeSources.SORTS.forEach { (key, label) ->
                        FilterChip(selected = st.webSort == key, onClick = { vm.setWebSort(key) }, label = { Text(label) })
                    }
                }
              }
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
              ) {
                Column {
                    RecipeSources.sort(web.results, st.webSort).forEachIndexed { i, w ->
                        if (i > 0) HorizontalDivider()
                        val saved = vm.savedWebRecipe(st, w.id) != null
                        Row(
                            Modifier.fillMaxWidth().clickable { onWeb(w.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Importer.emojiFor(w.title), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                w.rating?.let { RatingLine(it, w.ratingCount, w.countLabel) }
                                Text(
                                    listOfNotNull(
                                        w.minutes?.let { "⏱ " + Logic.fmtShort(it) },
                                        w.source.ifBlank { null },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (saved) {
                                Text("✓ Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
              }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Preview of an online recipe. Nothing is saved until you tap "Add to my recipes". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebRecipeScreen(
    vm: MealsViewModel, st: UserState, webId: String,
    forSide: Int? = null,
    onSaved: (Int) -> Unit, onBack: () -> Unit,
) {
    LaunchedEffect(webId) { vm.openWebRecipe(webId) }
    val loaded by vm.webPreview.collectAsState()
    val error by vm.webPreviewError.collectAsState()
    val d = loaded?.takeIf { it.id == webId }
    val saved = vm.savedWebRecipe(st, webId)
    // Main meal, side or dessert when you save it (guessed from the title).
    var kind by remember(webId) { mutableStateOf<String?>(null) }
    val pickedKind = kind ?: d?.let { Logic.guessKind(it.title) } ?: "main"

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { Text(if (forSide != null) "Online side" else "Online recipe") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (d != null) {
                Surface(tonalElevation = 3.dp) {
                  Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (forSide == null && saved == null) {
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) { KindChoice(pickedKind) { kind = it } }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                        Button(
                            onClick = {
                                when {
                                    forSide != null -> { vm.saveWebSide(forSide, d); onBack() }
                                    saved != null -> onSaved(saved.id)
                                    pickedKind == "side" -> { vm.saveWebSideOnly(d); onBack() }
                                    else -> vm.saveWebRecipe(d, pickedKind)?.let(onSaved)
                                }
                            },
                            modifier = Modifier.weight(2f),
                        ) {
                            Text(
                                when {
                                    forSide != null -> "Use as side"
                                    saved != null -> "Open my copy"
                                    pickedKind == "side" -> "Add to my sides"
                                    else -> "Add to my recipes"
                                },
                            )
                        }
                    }
                  }
                }
            }
        },
    ) { pad ->
        when {
            error != null && d == null -> Box(Modifier.padding(pad).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
            d == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.emoji, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(12.dp))
                    Text(d.title, style = MaterialTheme.typography.headlineSmall)
                }
                val badges = Logic.badges(Recipe(0, d.title, d.emoji, steps = emptyList(), ingredients = emptyList(), tags = d.tags))
                if (badges.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) { badges.forEach { Badge(it, small = false) } }
                }
                Text(
                    listOfNotNull(
                        "Serves ${d.servings}",
                        d.minutes?.let { "⏱ About ${Logic.fmtMinutes(it)}" },
                        d.source.ifBlank { null }?.let { "from $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                d.rating?.let {
                    Row(Modifier.padding(top = 4.dp)) { RatingLine(it, d.ratingCount, d.countLabel, 16.dp) }
                }
                Heading("Ingredients (serves ${d.servings})")
                d.ingredients.forEach { Text("• " + Logic.ingText(it, d.servings, d.servings), style = MaterialTheme.typography.bodyMedium) }
                Heading("Steps")
                d.steps.forEachIndexed { i, s ->
                    Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
                ReviewsSection(d.id, d.rating, d.ratingCount, d.countLabel, d.reviews)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val STAR_GOLD = Color(0xFFF5B301)

/** Five stars, filled to [rating] (partial stars too). */
@Composable
internal fun Stars(rating: Double, size: Dp = 14.dp) {
    Row {
        for (i in 0 until 5) {
            val fill = (rating - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.size(size)) {
                Icon(Icons.Filled.Star, null, Modifier.size(size), tint = MaterialTheme.colorScheme.outlineVariant)
                if (fill > 0f) {
                    Icon(
                        Icons.Filled.Star, null,
                        Modifier.size(size).drawWithContent {
                            clipRect(right = this.size.width * fill) { this@drawWithContent.drawContent() }
                        },
                        tint = STAR_GOLD,
                    )
                }
            }
        }
    }
}

/** "★★★★½ 4.6 (2,341)" */
@Composable
internal fun RatingLine(rating: Double, count: Int?, countLabel: String, size: Dp = 14.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Stars(rating, size)
        Spacer(Modifier.width(4.dp))
        Text(
            String.format(java.util.Locale.US, "%.1f", rating) +
                (count?.let { " (" + String.format(java.util.Locale.US, "%,d", it) + if (countLabel == "likes") " likes)" else ")" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One written review; tap to show all of a long one. */
@Composable
private fun ReviewItem(r: Review) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            r.stars?.let { Stars(it, 12.dp); Spacer(Modifier.width(6.dp)) }
            Text(r.author, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            r.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (open) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** "Reviews": the rating, 3 written reviews, and See more for the rest. */
@Composable
internal fun ReviewsSection(key: Any, rating: Double?, count: Int?, countLabel: String, reviews: List<Review>) {
    if (reviews.isEmpty()) return
    Heading("Reviews")
    rating?.let { RatingLine(it, count, countLabel, 16.dp) }
    var shown by remember(key) { mutableIntStateOf(3) }
    reviews.take(shown).forEach { rv ->
        ReviewItem(rv)
        HorizontalDivider()
    }
    if (shown < reviews.size) {
        TextButton(onClick = { shown += 3 }) { Text("See more") }
    }
}
