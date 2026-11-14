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

    private fun t(title: String, video: Int) = Technique(title, video)

    /** Every major technique, one clip each, for the Techniques menu: (section, techniques). */
    val all: List<Pair<String, List<Technique>>> = listOf(
        "Knife work" to listOf(
            t("Dice", R.raw.tech_dice_onion),
            t("Chop", R.raw.tech_chop_vegetables),
            t("Mince", R.raw.tech_mince),
            t("Slice", R.raw.tech_slice_onion),
            t("Julienne", R.raw.tech_julienne),
            t("Grate", R.raw.tech_grate),
            t("Zest", R.raw.tech_zest),
            t("Peel", R.raw.tech_peel),
        ),
        "Dry heat" to listOf(
            t("Sear", R.raw.tech_sear_main),
            t("Sauté", R.raw.tech_saute_main),
            t("Stir-fry", R.raw.tech_stir_fry_wok),
            t("Pan-fry", R.raw.tech_fry_pan),
            t("Deep-fry", R.raw.tech_deep_fry),
            t("Roast", R.raw.tech_roast_oven),
            t("Broil", R.raw.tech_broil),
            t("Grill", R.raw.tech_grill_grill),
            t("Air fry", R.raw.tech_air_fry_air_fryer),
        ),
        "Wet heat" to listOf(
            t("Boil", R.raw.tech_boil_pasta),
            t("Blanch", R.raw.tech_blanch),
            t("Simmer", R.raw.tech_simmer_stovetop),
            t("Poach", R.raw.tech_poach),
            t("Steam", R.raw.tech_steam_pan),
            t("Braise", R.raw.tech_braise),
        ),
        "Sauces and finishing" to listOf(
            t("Deglaze", R.raw.tech_deglaze),
            t("Reduce a sauce", R.raw.tech_reduce),
            t("Make a roux", R.raw.tech_roux),
            t("Thicken with a slurry", R.raw.tech_slurry),
            t("Glaze", R.raw.tech_glaze),
            t("Baste", R.raw.tech_baste),
            t("Caramelize onions", R.raw.tech_caramelize_onions),
            t("Rest meat", R.raw.tech_rest_meat),
        ),
        "Mixing and coating" to listOf(
            t("Whisk", R.raw.tech_whisk_main),
            t("Whip", R.raw.tech_whip),
            t("Fold", R.raw.tech_fold_batter),
            t("Dredge / bread", R.raw.tech_dredge),
            t("Marinate", R.raw.tech_marinate),
        ),
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
