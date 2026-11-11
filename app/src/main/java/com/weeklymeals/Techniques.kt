package com.weeklymeals

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Short looping how-to clips for cooking techniques (res/raw/tech_*.mp4, made in Blender).
 * A step that names a technique gets a "▶ How to …" chip in cooking mode; tapping it plays the clip.
 */
object Techniques {
    data class Technique(val title: String, val video: Int)

    private val BRAISE = Technique("Braise", R.raw.tech_braise)
    private val ROUX = Technique("Make a roux", R.raw.tech_roux)
    private val CARAMELIZE = Technique("Caramelize onions", R.raw.tech_caramelize_onions)
    private val DEGLAZE = Technique("Deglaze", R.raw.tech_deglaze)
    private val REDUCE = Technique("Reduce a sauce", R.raw.tech_reduce)
    private val SEAR = Technique("Sear", R.raw.tech_sear_main)
    private val WOK = Technique("Stir-fry (wok)", R.raw.tech_stir_fry_wok)
    private val SKILLET = Technique("Stir-fry (skillet)", R.raw.tech_stir_fry_skillet)
    private val BASTE = Technique("Baste", R.raw.tech_baste)
    private val FOLD_BATTER = Technique("Fold into a batter", R.raw.tech_fold_batter)
    private val FOLD_OMELETTE = Technique("Fold an omelette", R.raw.tech_fold_omelette)
    private val GLAZE = Technique("Glaze", R.raw.tech_glaze)
    private val SLURRY = Technique("Thicken with a slurry", R.raw.tech_slurry)
    private val PC_NATURAL = Technique("Pressure cook (natural release)", R.raw.tech_pressure_cook_natural)
    private val PC_QUICK = Technique("Pressure cook (quick release)", R.raw.tech_pressure_cook_quick)
    private val DREDGE = Technique("Dredge / bread", R.raw.tech_dredge)
    private val JULIENNE = Technique("Julienne", R.raw.tech_julienne)
    private val DICE_ONION = Technique("Dice an onion", R.raw.tech_dice_onion)
    private val CHOP_ROMAINE = Technique("Chop romaine", R.raw.tech_chop_romaine)
    private val CHOP_HERBS = Technique("Chop herbs", R.raw.tech_chop_herbs)
    private val CHOP_VEG = Technique("Chop vegetables", R.raw.tech_chop_vegetables)
    private val PEEL = Technique("Peel", R.raw.tech_peel)
    private val ZEST = Technique("Zest", R.raw.tech_zest)

    /** Every clip, for the Techniques menu: (section, techniques). */
    val all: List<Pair<String, List<Technique>>> = listOf(
        "Knife and prep" to listOf(DICE_ONION, CHOP_VEG, CHOP_HERBS, CHOP_ROMAINE, JULIENNE, PEEL, ZEST),
        "Heat and sauces" to listOf(SEAR, BRAISE, BASTE, GLAZE, DEGLAZE, REDUCE, ROUX, SLURRY, CARAMELIZE),
        "Pans and pots" to listOf(SKILLET, WOK, PC_NATURAL, PC_QUICK),
        "Mixing and coating" to listOf(FOLD_BATTER, FOLD_OMELETTE, DREDGE),
    )

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** Onions that aren't diced: green onions, slices/strips/rings, powder, fried onions. */
    private val notDicedOnion = rx("green onion|spring onion|onion powder|fried onion|onion soup|slic\\w*[^;.]{0,25}\\bonion|onions?\\b[^;.]{0,20}\\b(strips|rings|slices|wedges)|carameli[sz]")

    /** Checked in order; a step shows at most [MAX_PER_STEP] chips. */
    private val rules: List<Pair<Regex, (String) -> Technique?>> = listOf(
        rx("\\bbrais(e|ed|ing)\\b") to { _ -> BRAISE },
        rx("\\broux\\b") to { _ -> ROUX },
        rx("carameli[sz]e") to { _ -> CARAMELIZE },
        rx("\\bdeglaz") to { _ -> DEGLAZE },
        rx("until (slightly )?reduced|\\breduce (the )?(sauce|liquid)|\\breduced by") to { _ -> REDUCE },
        rx("\\bsear(ed|ing)?\\b") to { _ -> SEAR },
        rx("stir[- ]?fr(y|ied|ying)") to { s -> if (rx("\\bwok\\b").containsMatchIn(s)) WOK else SKILLET },
        rx("\\bbast(e|ing)\\b") to { _ -> BASTE },
        rx("\\bfold\\b") to { s ->
            if (rx("\\begg|omelet").containsMatchIn(s) && !rx("fold in|fold (it )?(into|through)|batter").containsMatchIn(s)) FOLD_OMELETTE
            else FOLD_BATTER
        },
        rx("\\bglaz(e|ed|ing)\\b") to { _ -> GLAZE },
        rx("slurry|cornstarch (and|with|mixed with|in) (a little |some )?(cold )?(water|milk|broth)") to { _ -> SLURRY },
        rx("pressure[- ]cook|\\b(natural|quick)(ly)?[- ]release|release (the )?pressure|lock the lid") to { s ->
            if (rx("quick(ly)?[- ]release|release (the )?pressure manually|manual release").containsMatchIn(s)) PC_QUICK else PC_NATURAL
        },
        rx("\\bdredg|\\bbread(ed)? (the )?(chicken|pork|fish|cutlets)|\\bdip\\b.*\\b(flour|breadcrumbs|panko|egg)") to { _ -> DREDGE },
        rx("julienne|matchstick") to { _ -> JULIENNE },
        // Any onion that goes in diced, even when the step only says "cook the onion".
        rx("\\bonions?\\b") to { s -> if (notDicedOnion.containsMatchIn(s)) null else DICE_ONION },
        rx("\\bchop(ped)?\\b[^;.]{0,20}\\b(romaine|lettuce)") to { _ -> CHOP_ROMAINE },
        rx("\\bchop(ped)?\\b[^;.]{0,20}\\b(parsley|cilantro|basil|herbs|dill|chives|mint)") to { _ -> CHOP_HERBS },
        rx("\\bchop(ped)?\\b[^;.]{0,12}\\b(the )?(vegetables|veggies|cucumbers?|tomato(es)?|peppers?|celery|carrots?|broccoli|zucchini)") to { _ -> CHOP_VEG },
        rx("\\bpeel(ed)?\\b") to { _ -> PEEL },
        rx("\\bzest\\b") to { _ -> ZEST },
    )

    private const val MAX_PER_STEP = 2

    /** The technique clips that fit this step (no repeats). */
    fun forStep(step: String): List<Technique> =
        rules.mapNotNull { (re, pick) -> if (re.containsMatchIn(step)) pick(step) else null }
            .distinctBy { it.video }
            .take(MAX_PER_STEP)
}

/** Lime "▶ How to …" chips under a step; each opens the looping clip. */
@Composable
fun TechniqueChips(step: String, modifier: Modifier = Modifier) {
    val list = remember(step) { Techniques.forStep(step) }
    if (list.isEmpty()) return
    var open by remember { mutableStateOf<Techniques.Technique?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { t ->
            Text(
                "▶  How to: ${t.title}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { open = t }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    open?.let { t ->
        AppDialog(
            onDismiss = { open = null },
            eyebrow = "Technique",
            title = t.title,
            actions = listOf(DialogAction("Close") { open = null }),
        ) {
            TechniqueVideo(t.video)
        }
    }
}

/** Plays a res/raw clip on a loop, muted, on the clip's own dark card colour. */
@Composable
fun TechniqueVideo(video: Int) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxWidth().aspectRatio(5f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1D211D)),
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                    setVideoURI(Uri.parse("android.resource://${context.packageName}/$video"))
                }
            },
            onRelease = { it.stopPlayback() },
            modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f),
        )
    }
}

/**
 * The "Techniques" pop-up in cooking mode: every clip, grouped. Tap one to play it;
 * "All techniques" goes back to the list.
 */
@Composable
fun TechniquesMenu(onDismiss: () -> Unit) {
    var playing by remember { mutableStateOf<Techniques.Technique?>(null) }
    val p = playing
    AppDialog(
        onDismiss = onDismiss,
        eyebrow = if (p == null) "Cooking techniques" else "Technique",
        title = p?.title ?: "How to…",
        actions = if (p == null) listOf(DialogAction("Close", onClick = onDismiss))
        else listOf(DialogAction("All techniques") { playing = null }, DialogAction("Close", onClick = onDismiss)),
    ) {
        if (p != null) {
            TechniqueVideo(p.video)
        } else {
            Techniques.all.forEach { (section, list) ->
                MenuSection(section)
                list.forEach { t -> MenuRow(t) { playing = t } }
            }
        }
    }
}

@Composable
private fun MenuSection(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuRow(t: Techniques.Technique, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            "▶  ${t.title}", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
