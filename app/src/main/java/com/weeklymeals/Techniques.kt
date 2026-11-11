package com.weeklymeals

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Cooking mode's "Techniques" bubble lists them all; tapping one plays the clip.
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
