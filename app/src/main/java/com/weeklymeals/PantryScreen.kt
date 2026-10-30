package com.weeklymeals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PLENTY = Look.Good
private val LOW = Look.Warn
private val SHELF_WOOD = Color(0xFF8B5A2B)
private val FREEZER = Color(0xFF85B7EB)

/** A best-by date from the date picker (stored as midnight UTC), e.g. "Mar 4, 2027". */
private fun bestByText(at: Long): String {
    val f = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    f.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return f.format(java.util.Date(at))
}

/** Shows typed digits as MM/DD/YY: the slashes fill in by themselves. */
private object DateSlashes : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = buildString {
            append(d.take(2))
            if (d.length > 2) append("/").append(d.substring(2, minOf(4, d.length)))
            if (d.length > 4) append("/").append(d.substring(4))
        }
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (if (offset <= 2) offset else if (offset <= 4) offset + 1 else offset + 2).coerceAtMost(out.length)
            override fun transformedToOriginal(offset: Int): Int =
                (if (offset <= 2) offset else if (offset <= 5) offset - 1 else offset - 2).coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(out), map)
    }
}

/**
 * Typed digits to a date (midnight UTC): MMDD (the next one coming up), MMDDYY or MMDDYYYY.
 * Null while it's incomplete or not a real date.
 */
private fun parseTypedDate(d: String, past: Boolean = false): Long? {
    if (d.length != 4 && d.length != 6 && d.length != 8) return null
    val m = d.substring(0, 2).toInt()
    val day = d.substring(2, 4).toInt()
    val utc = java.util.TimeZone.getTimeZone("UTC")
    val today = java.util.Calendar.getInstance()
    var year = when (d.length) {
        4 -> today.get(java.util.Calendar.YEAR)
        6 -> 2000 + d.substring(4, 6).toInt()
        else -> d.substring(4, 8).toInt()
    }
    fun make(y: Int): Long? {
        val c = java.util.Calendar.getInstance(utc).apply { isLenient = false; clear(); set(y, m - 1, day) }
        return try { c.timeInMillis } catch (e: Exception) { null }
    }
    if (m !in 1..12 || day !in 1..31 || year !in 2000..2100) return null
    var ms = make(year) ?: return null
    // Just month and day: if that's already passed this year, it means next year.
    // A date already behind you (frozen on): month and day still to come this year means last year.
    if (past) {
        if (d.length == 4 && ms > System.currentTimeMillis() + 86_400_000L) { year--; ms = make(year) ?: return null }
        return ms
    }
    if (d.length == 4 && ms < System.currentTimeMillis() - 86_400_000L) { year++; ms = make(year) ?: return null }
    return ms
}

/** The digits to show for a saved date (MMDDYY). */
private fun digitsFor(at: Long): String {
    val f = java.text.SimpleDateFormat("MMddyy", java.util.Locale.US)
    f.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return f.format(java.util.Date(at))
}

private val DATE_RED = Look.Danger

/** Order on a shelf: past best-by, past sell-by, low, then the rest. */
private val MARK_ORDER = listOf("best", "sell", "low", "")

/**
 * What a jar is marked with: "best" (past its best-by), "sell" (past its sell-by plus the
 * usual grace, or a spice past its date), "low", or "".
 */
private fun markFor(p: PantryItem): String {
    if (!p.useAnyway && Pantry.freshness(p) == Pantry.Fresh.EXPIRED) {
        return if (p.dateKind == "sell" || Pantry.isSpice(p)) "sell" else "best"
    }
    return if (Pantry.isLow(p)) "low" else ""
}

/** Red ✕ (past best-by) or orange ? (past sell-by) in a little circle, drawn so it sits dead center. */
@Composable
private fun JarMark(kind: String, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(16.dp)) {
        val r = size.minDimension / 2
        drawCircle(ring, r)
        drawCircle(if (kind == "best") DATE_RED else SELL_ORANGE, r - 1.5.dp.toPx())
        val c = center
        val w = 1.8.dp.toPx()
        if (kind == "best") {
            val d = r * 0.38f
            drawLine(Color.White, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), w, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), w, StrokeCap.Round)
        } else {
            // A question mark: hook, stem, dot.
            val u = r * 0.12f
            val path = Path().apply {
                moveTo(c.x - 2.6f * u, c.y - 2.2f * u)
                cubicTo(c.x - 2.6f * u, c.y - 5.4f * u, c.x + 2.8f * u, c.y - 5.4f * u, c.x + 2.8f * u, c.y - 2.2f * u)
                cubicTo(c.x + 2.8f * u, c.y + 0.2f * u, c.x, c.y + 0.2f * u, c.x, c.y + 2.0f * u)
            }
            drawPath(path, Color.White, style = Stroke(w, cap = StrokeCap.Round))
            drawCircle(Color.White, w * 0.65f, Offset(c.x, c.y + 4.6f * u))
        }
    }
}

/** Kitchen zones and which shelves are in each. */
private data class Zone(val key: String, val label: String, val color: Color)
private val ZONES = listOf(
    Zone("pantry", "Pantry", Color(0xFFBA7517)),
    Zone("fridge", "Fridge", Color(0xFF378ADD)),
    Zone("freezer", "Freezer", Color(0xFF85B7EB)),
    Zone("produce", "Produce", Color(0xFF639922)),
)
internal fun zoneOf(shelf: String) = when (shelf) { "fridge" -> "fridge"; "produce" -> "produce"; else -> "pantry" }

/** A zone on the Kitchen overview: icon, name, count and anything that needs a look. */
@Composable
private fun ZoneCard(z: Zone, st: UserState, modifier: Modifier, onClick: () -> Unit) {
    val items = st.pantry.filter { zoneOf(it.shelf) == z.key }
    val best = items.count { markFor(it) == "best" }
    val sell = items.count { markFor(it) == "sell" }
    val low = items.count { markFor(it) == "low" }
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        ZoneIcon(z.key, z.color, Modifier.size(34.dp))
        Text(z.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        val count = if (z.key == "freezer") st.freezer.size else items.size
        Text(
            "$count " + (if (z.key == "freezer") (if (count == 1) "bag" else "bags") else if (count == 1) "item" else "items"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 6.dp).height(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (z.key == "freezer") {
                val oldest = st.freezer.minOfOrNull { it.frozenAt }
                Text(
                    if (oldest == null) "Empty" else "Oldest " + frozenAgo(oldest).removePrefix("frozen "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (best + sell + low == 0) {
                Text(if (items.isEmpty()) "Empty" else "All good", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (best > 0) { JarMark("best"); Text(" $best  ", style = MaterialTheme.typography.bodySmall) }
                if (sell > 0) { JarMark("sell"); Text(" $sell  ", style = MaterialTheme.typography.bodySmall) }
                if (low > 0) Text("$low low", style = MaterialTheme.typography.bodySmall, color = LOW)
            }
        }
    }
}

/** The Kitchen zone's own icon, small, in its color: "you have it" (pantry, fridge, freezer or produce). */
@Composable
internal fun HaveIcon(zone: String?, modifier: Modifier = Modifier.size(16.dp), color: Color? = null) {
    val z = ZONES.firstOrNull { it.key == zone } ?: ZONES.first()
    ZoneIcon(z.key, color ?: z.color, modifier)
}

/** Drawn zone icons: cupboard with jars, fridge, freezer chest with a snowflake, produce basket. */
@Composable
internal fun ZoneIcon(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 34f
        val w = 2f * u
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun box(x: Float, y: Float, bw: Float, bh: Float, r: Float = 3f, fill: Boolean = false) =
            if (fill) drawRoundRect(color, p(x, y), Size(bw * u, bh * u), CornerRadius(r * u))
            else drawRoundRect(color, p(x, y), Size(bw * u, bh * u), CornerRadius(r * u), style = Stroke(w))
        when (kind) {
            "pantry" -> {
                box(4f, 3f, 26f, 28f)
                drawLine(color, p(17f, 3f), p(17f, 31f), w)
                box(7f, 8f, 4f, 7f, 1f, fill = true); box(21f, 7f, 5f, 8f, 1f, fill = true)
                drawCircle(color, 1.4f * u, p(14f, 24f)); drawCircle(color, 1.4f * u, p(20f, 24f))
            }
            "fridge" -> {
                box(8f, 2f, 18f, 30f)
                drawLine(color, p(8f, 12f), p(26f, 12f), w)
                drawLine(color, p(11.5f, 6f), p(11.5f, 9f), w, StrokeCap.Round)
                drawLine(color, p(11.5f, 16f), p(11.5f, 21f), w, StrokeCap.Round)
            }
            "freezer" -> {
                box(3f, 7f, 28f, 22f)
                drawLine(color, p(17f, 11f), p(17f, 25f), w * 0.9f, StrokeCap.Round)
                drawLine(color, p(11f, 14f), p(23f, 22f), w * 0.9f, StrokeCap.Round)
                drawLine(color, p(23f, 14f), p(11f, 22f), w * 0.9f, StrokeCap.Round)
            }
            else -> {
                val basket = Path().apply { moveTo(5f * u, 14f * u); lineTo(29f * u, 14f * u); lineTo(26f * u, 29f * u); lineTo(8f * u, 29f * u); close() }
                drawPath(basket, color, style = Stroke(w))
                drawLine(color, p(11f, 14f), p(16f, 6f), w, StrokeCap.Round)
                drawLine(color, p(23f, 14f), p(18f, 6f), w, StrokeCap.Round)
                drawCircle(color.copy(alpha = 0.7f), 2.5f * u, p(13f, 21f)); drawCircle(color.copy(alpha = 0.7f), 2.5f * u, p(20f, 22f))
            }
        }
    }
}

private val SELL_ORANGE = Look.Warn

private fun boughtAgo(at: Long): String {
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return "bought " + when { days < 1 -> "today"; days < 14 -> "$days d ago"; else -> "${days / 7} wks ago" }
}

private fun freezerAmount(f: FreezerItem): String =
    if (f.byCount) "${f.count}" else "${Logic.fmt(f.lb)} lb"

/** "Frozen Oct 12" (with the year when it isn't this year). */
private fun frozenOn(at: Long): String {
    if (at <= 0) return ""
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val y = java.util.Calendar.getInstance().apply { timeInMillis = at }.get(java.util.Calendar.YEAR)
    val f = java.text.SimpleDateFormat(if (y == thisYear) "MMM d" else "MMM d, yyyy", java.util.Locale.getDefault())
    return "Frozen " + f.format(java.util.Date(at))
}

/** The typed digits (MMDDYY) for a frozen date, in your own time zone. */
private fun frozenDigits(at: Long): String =
    java.text.SimpleDateFormat("MMddyy", java.util.Locale.US).format(java.util.Date(at))

/** "frozen today", "frozen 2 wks ago", "frozen 4 mo ago". */
private fun frozenAgo(at: Long): String {
    if (at <= 0) return ""
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return "frozen " + when {
        days < 1 -> "today"
        days < 14 -> "$days d ago"
        days < 60 -> "${days / 7} wks ago"
        else -> "${days / 30} mo ago"
    }
}

/** A freezer bag with a frosty fill; amber edge when it's been frozen over 3 months. */
@Composable
private fun FreezerBag(modifier: Modifier, old: Boolean, what: String) {
    // The food's own emoji with a little snowflake; an amber ring once it's been frozen 3+ months.
    Box(
        modifier
            .background(FREEZER.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .border(if (old) 2.dp else 0.dp, if (old) LOW else Color.Transparent, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(meatEmoji(what), fontSize = 30.sp)
        Text("❄️", fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 1.dp))
    }
}

/** Emoji for what's in the freezer: 🍗 chicken, 🐟 fish, 🍤 shrimp, 🌭 sausage, 🥓 bacon, 🍖 ribs/pork, 🍔 patties, else 🥩. */
private fun meatEmoji(what: String): String {
    val n = what.lowercase()
    return when {
        Regex("shrimp|prawn").containsMatchIn(n) -> "🍤"
        Regex("salmon|tilapia|\\bcod\\b|tuna|fish|halibut|trout|mahi|catfish|pollock").containsMatchIn(n) -> "🐟"
        Regex("sausage|brat|hot dog|kielbasa|chorizo").containsMatchIn(n) -> "🌭"
        Regex("bacon").containsMatchIn(n) -> "🥓"
        Regex("patt|burger").containsMatchIn(n) -> "🍔"
        Regex("chicken|turkey|poultry|wing|drumstick|thigh").containsMatchIn(n) && !n.contains("ground turkey") -> "🍗"
        Regex("\\bribs?\\b|pork|ham\\b").containsMatchIn(n) -> "🍖"
        else -> "🥩"
    }
}

/** Add or adjust something in the freezer. */
@Composable
private fun FreezerDialog(start: FreezerItem, isNew: Boolean, onSave: (FreezerItem) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var item by remember(start.id) { mutableStateOf(start) }
    var frozenText by remember(start.id) { mutableStateOf(if (start.frozenAt > 0) frozenDigits(start.frozenAt) else "") }
    val frozenBad = frozenText.isNotEmpty() && parseTypedDate(frozenText, past = true) == null
    AppDialog(
        onDismiss = onDismiss,
        eyebrow = "Freezer",
        title = if (isNew) "Add to freezer" else item.name,
        centered = true,
        actions = listOfNotNull(
            DialogAction(if (isNew) "Add" else "Save", ActionKind.Primary) {
                val name = item.name.trim()
                if (name.isNotEmpty()) {
                    onSave(item.copy(name = name.replaceFirstChar { it.uppercase() }, term = item.term.ifEmpty { Logic.searchTerm(name) }))
                }
            },
            if (!isNew) DialogAction("Remove from freezer", ActionKind.Danger, onClick = onRemove) else null,
            DialogAction("Cancel", onClick = onDismiss),
        ),
    ) {
            Column {
                if (isNew) {
                    OutlinedTextField(
                        item.name, { item = item.copy(name = it) },
                        label = { Text("What is it? (e.g. chicken breast)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = item.byCount, onClick = { item = item.copy(byCount = true, count = maxOf(item.count, 1)) }, label = { Text("Pieces") })
                        FilterChip(selected = !item.byCount, onClick = { item = item.copy(byCount = false, lb = maxOf(item.lb, 1.0)) }, label = { Text("Pounds") })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = {
                        item = if (item.byCount) item.copy(count = (item.count - 1).coerceAtLeast(0))
                        else item.copy(lb = (item.lb - 0.25).coerceAtLeast(0.0))
                    }) { Text("−") }
                    Text(
                        if (item.byCount) "${item.count}" else "${Logic.fmt(item.lb)} lb",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    OutlinedButton(onClick = {
                        item = if (item.byCount) item.copy(count = item.count + 1) else item.copy(lb = item.lb + 0.25)
                    }) { Text("+") }
                }
                // The day it went in the freezer (today unless you change it).
                OutlinedTextField(
                    frozenText,
                    { v ->
                        frozenText = v.filter { it.isDigit() }.take(8)
                        // Typed dates are midnight UTC; noon keeps it on the same day in your time zone.
                        parseTypedDate(frozenText, past = true)?.let { item = item.copy(frozenAt = it + 12 * 3_600_000L) }
                    },
                    label = { Text("Date frozen") },
                    placeholder = { Text("MM/DD/YY") },
                    singleLine = true,
                    isError = frozenBad,
                    supportingText = if (frozenBad) { { Text("Not a real date") } } else if (item.frozenAt > 0) { { Text(frozenAgo(item.frozenAt).replaceFirstChar { it.uppercase() }) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = DateSlashes,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
    }
}

/** Top-bar icon: two shelves with jars, drawn in the same solid style as the other icons. */
val PantryIcon: ImageVector by lazy {
    ImageVector.Builder("Pantry", 24.dp, 24.dp, 24f, 24f).apply {
        fun r(x: Float, y: Float, w: Float, h: Float) = addPath(
            PathData { moveTo(x, y); horizontalLineToRelative(w); verticalLineToRelative(h); horizontalLineToRelative(-w); close() },
            fill = SolidColor(Color.Black),
        )
        // Top shelf: small jar with lid, tall jar, wide jar.
        r(4f, 5f, 4f, 1.5f); r(3.5f, 7f, 5f, 5f)
        r(10f, 4f, 4f, 8f)
        r(15.5f, 6f, 5f, 6f)
        r(2f, 13f, 20f, 2f)
        // Bottom shelf: jar, bottle, jar.
        r(4f, 16.5f, 5f, 4f)
        r(11f, 17.5f, 1.6f, 3f); r(10f, 16f, 3.6f, 1.5f)
        r(15f, 16.5f, 5f, 4f)
        r(2f, 21f, 20f, 2f)
    }.build()
}

/** A barcode: tap to scan something into the pantry. */
val BarcodeIcon: ImageVector by lazy {
    ImageVector.Builder("Barcode", 24.dp, 24.dp, 24f, 24f).apply {
        fun r(x: Float, w: Float) = addPath(
            PathData { moveTo(x, 5f); horizontalLineToRelative(w); verticalLineToRelative(14f); horizontalLineToRelative(-w); close() },
            fill = SolidColor(Color.Black),
        )
        r(2f, 2f); r(5f, 1f); r(7f, 2.5f); r(10.5f, 1f); r(12.5f, 2f); r(15.5f, 1f); r(17.5f, 2.5f); r(21f, 1f)
    }.build()
}

/** Outline of a container in a 100 x 120 box, scaled to [w] x [h]. */
private fun containerPath(kind: String, w: Float, h: Float): Path {
    val sx = w / 100f
    val sy = h / 120f
    fun rr(l: Float, t: Float, rw: Float, rh: Float, r: Float) =
        RoundRect(l * sx, t * sy, (l + rw) * sx, (t + rh) * sy, CornerRadius(r * sx, r * sy))
    return Path().apply {
        when (kind) {
            "bottle" -> {
                addRoundRect(rr(43f, 4f, 14f, 10f, 2f))
                moveTo(45 * sx, 14 * sy); lineTo(55 * sx, 14 * sy); lineTo(55 * sx, 28 * sy)
                quadraticBezierTo(69 * sx, 36 * sy, 69 * sx, 50 * sy); lineTo(69 * sx, 108 * sy)
                quadraticBezierTo(69 * sx, 114 * sy, 63 * sx, 114 * sy); lineTo(37 * sx, 114 * sy)
                quadraticBezierTo(31 * sx, 114 * sy, 31 * sx, 108 * sy); lineTo(31 * sx, 50 * sy)
                quadraticBezierTo(31 * sx, 36 * sy, 45 * sx, 28 * sy); close()
            }
            "bag" -> {
                moveTo(28 * sx, 10 * sy); lineTo(72 * sx, 10 * sy); lineTo(68 * sx, 20 * sy)
                quadraticBezierTo(76 * sx, 26 * sy, 76 * sx, 38 * sy); lineTo(76 * sx, 108 * sy)
                quadraticBezierTo(76 * sx, 114 * sy, 70 * sx, 114 * sy); lineTo(30 * sx, 114 * sy)
                quadraticBezierTo(24 * sx, 114 * sy, 24 * sx, 108 * sy); lineTo(24 * sx, 38 * sy)
                quadraticBezierTo(24 * sx, 26 * sy, 32 * sx, 20 * sy); close()
            }
            "can" -> addRoundRect(rr(24f, 30f, 52f, 84f, 7f))
            "box" -> addRoundRect(rr(22f, 14f, 56f, 100f, 4f))
            else -> { // jar
                addRoundRect(rr(34f, 6f, 32f, 12f, 3f))
                addRoundRect(rr(26f, 18f, 48f, 96f, 8f))
            }
        }
    }
}

/** A container drawn filled to [level] (0..1): green when there's plenty, amber when low. */
@Composable
fun ContainerView(kind: String, level: Double, low: Boolean, modifier: Modifier, stroke: Dp = 2.dp) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val path = containerPath(kind, size.width, size.height)
        val top = 6f * size.height / 120f
        val bottom = 114f * size.height / 120f
        val y = bottom - (bottom - top) * level.toFloat().coerceIn(0f, 1f)
        clipPath(path) {
            drawRect(if (low) LOW else PLENTY, topLeft = Offset(0f, y), size = Size(size.width, bottom - y))
        }
        drawPath(path, outline, style = Stroke(stroke.toPx()))
    }
}

/** The Pantry: shelves of containers. Tap one to set how much is left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(vm: MealsViewModel, st: UserState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<PantryItem?>(null) }
    var editingNew by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) } // shelf key, or "" for any
    var freezing by remember { mutableStateOf<FreezerItem?>(null) }
    // Adding opens the barcode scanner first; backing out of it (or a barcode we can't find)
    // opens the type-it-in dialog instead.
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lookingUp by remember { mutableStateOf(false) }
    var addNote by remember { mutableStateOf<String?>(null) }
    fun scanFirst(shelf: String?) {
        addNote = null
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            .build()
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { code ->
                val raw = code.rawValue.orEmpty()
                lookingUp = true
                scope.launch {
                    val p = vm.lookupBarcode(raw)
                    lookingUp = false
                    if (p != null) {
                        editing = vm.pantryDraft(p.title, p.size, p.upc.ifBlank { raw }, shelf)
                        editingNew = true
                    } else {
                        addNote = "Couldn't find that barcode. Type the name instead."
                        adding = shelf ?: ""
                    }
                }
            }
            .addOnCanceledListener { adding = shelf ?: "" }
            .addOnFailureListener { adding = shelf ?: "" }
    }
    var freezingNew by remember { mutableStateOf(false) }
    // Which zone is open (null = the Kitchen overview). Back returns to the overview.
    var zone by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = zone != null) { zone = null }
    fun newFreezerItem() {
        freezing = FreezerItem(java.util.UUID.randomUUID().toString(), "", "", count = 1,
            frozenAt = System.currentTimeMillis(), byCount = true)
        freezingNew = true
    }
    val lowCount = st.pantry.count { markFor(it) == "low" }
    val bestCount = st.pantry.count { markFor(it) == "best" }
    val sellCount = st.pantry.count { markFor(it) == "sell" }
    var showOnly by remember { mutableStateOf("") } // "", "best", "sell" or "low"
    LaunchedEffect(lowCount, bestCount, sellCount) {
        val n = when (showOnly) { "low" -> lowCount; "best" -> bestCount; "sell" -> sellCount; else -> 1 }
        if (n == 0) showOnly = ""
    }

    Scaffold(
        // Shown under the app's top bar, so the status bar is already accounted for.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        // Swipe right: from a zone back to the Kitchen overview, from the overview out of the Kitchen.
        modifier = Modifier.swipeBack { if (zone != null) zone = null else onBack() },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (zone != null) {
                        IconButton(onClick = { zone = null }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ArrowBack, "Back to Kitchen")
                        }
                    }
                    Text(
                        ZONES.firstOrNull { it.key == zone }?.label ?: "Kitchen",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = {
                            when (zone) {
                                "freezer" -> newFreezerItem()
                                "fridge", "produce" -> scanFirst(zone)
                                else -> scanFirst(null)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) { Icon(if (zone == "freezer") Icons.Filled.Add else BarcodeIcon, "Add an item", Modifier.size(22.dp)) }
                }
            }
            if (zone == null) {
                // The whole kitchen at a glance: tap a zone to see what's in it.
                item(key = "zones") {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ZONES.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { z ->
                                    ZoneCard(z, st, Modifier.weight(1f)) { zone = z.key }
                                }
                            }
                        }
                    }
                }
            }
            if (zone != null && zone != "freezer") Pantry.SHELVES.filter { zoneOf(it.first) == zone }.forEach { (key, label) ->
                // Problems first (past a date, then low), so they show even with the shelf closed.
                val onShelf = st.pantry.filter { it.shelf == key && (showOnly == "" || markFor(it) == showOnly) }
                    .sortedWith(compareBy({ MARK_ORDER.indexOf(markFor(it)) }, { it.term }))
                if (showOnly != "" && onShelf.isEmpty()) return@forEach
                item(key = "shelf_$key") {
                    // Closed shelves show their first row; tap the name to open the rest.
                    // Fridge and produce are one shelf each: always open.
                    val open = key in st.openShelves || showOnly != "" || zone != "pantry"
                    val cells: List<PantryItem?> = onShelf + (if (showOnly == "") listOf(null) else emptyList())
                    val more = cells.size > 4
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = more) { vm.toggleShelf(key) }
                            .padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label + if (onShelf.isNotEmpty()) " · ${onShelf.size}" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (more) {
                            Text(
                                if (open) "▾" else (onShelf.size - cells.take(4).count { it != null }).let { n -> if (n > 0) "+$n ▸" else "▸" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    // Four across, wrapping to more rows when the shelf is open.
                    // Jars line up along the top, so an extra line under one never shifts the others.
                    (if (open) cells.chunked(4) else cells.chunked(4).take(1)).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                            row.forEach { p ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    if (p == null) {
                                        Box(
                                            Modifier.size(50.dp, 62.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .clickable { scanFirst(key) },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(BarcodeIcon, "Scan into $label", tint = MaterialTheme.colorScheme.outline) }
                                    } else {
                                        Column(
                                            Modifier.fillMaxWidth().clickable { editing = p; editingNew = false }.padding(horizontal = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box {
                                                ContainerView(p.kind, p.level, Pantry.isLow(p), Modifier.size(52.dp, 62.dp))
                                                // On the lid: red ✕ = past best-by, orange ? = past sell-by. Low is just the amber fill.
                                                val mark = markFor(p)
                                                if (mark == "best" || mark == "sell") JarMark(mark, Modifier.align(Alignment.TopEnd))
                                            }
                                            Text(
                                                p.term.replaceFirstChar { it.uppercase() },
                                                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
                                            )
                                            if (p.measure == Pantry.LB || p.measure == Pantry.STICK || p.measure == Pantry.EGG) {
                                                Text(Pantry.amountText(p), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp).height(4.dp)
                            .background(SHELF_WOOD, RoundedCornerShape(2.dp)),
                    )
                }
            }
            // The freezer: leftover meat, counted by the piece (whole cuts) or by the pound.
            if (zone == "freezer") item(key = "freezer") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(FREEZER.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, FREEZER.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    val freezerOpen = true
                    val freezerMore = false
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = freezerMore) { vm.toggleShelf("freezer") },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "🧊 Freezer" + if (st.freezer.isNotEmpty()) " · ${st.freezer.size}" else "",
                            style = MaterialTheme.typography.labelLarge, color = FREEZER, modifier = Modifier.weight(1f),
                        )
                        if (freezerMore) {
                            Text(if (freezerOpen) "▾" else "+${st.freezer.size - 4} ▸", style = MaterialTheme.typography.labelLarge, color = FREEZER)
                        }
                    }
                    if (st.freezer.isEmpty()) {
                        Text(
                            "Leftover meat goes here when you finish a week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    // Lined up along the top so the text under an item never shifts its icon.
                    LazyRow(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                        items(st.freezer, key = { it.id }) { f ->
                            val old = System.currentTimeMillis() - f.frozenAt > 90L * 86_400_000L
                            Column(
                                Modifier.width(76.dp).clickable { freezing = f; freezingNew = false }.padding(bottom = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FreezerBag(Modifier.size(52.dp, 56.dp), old, f.term + " " + f.name)
                                Text(
                                    f.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
                                )
                                Text(freezerAmount(f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    frozenOn(f.frozenAt).removePrefix("Frozen "), fontSize = 10.sp,
                                    color = if (old) LOW else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item {
                            Box(
                                Modifier.padding(start = 6.dp).size(50.dp, 56.dp)
                                    .border(1.dp, FREEZER.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .clickable { newFreezerItem() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Add to freezer", tint = FREEZER) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    JarMark("best")
                    Text(" Past best-by    ", style = MaterialTheme.typography.bodySmall)
                    JarMark("sell")
                    Text(" Past sell-by    ", style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.size(12.dp).background(LOW, RoundedCornerShape(2.dp)))
                    Text(" Low", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    freezing?.let { f ->
        FreezerDialog(
            f, isNew = freezingNew,
            onSave = { vm.saveFreezerItem(it); freezing = null },
            onRemove = { vm.removeFreezerItem(f.id); freezing = null },
            onDismiss = { freezing = null },
        )
    }
    if (lookingUp) {
        AppDialog(onDismiss = {}, title = "Looking it up…", centered = true, dismissable = false) {
            CircularProgressIndicator()
        }
    }
    adding?.let { shelf ->
        PantryAddDialog(
            vm, st, shelf.ifEmpty { null }, addNote,
            onPicked = { draft -> adding = null; editing = draft; editingNew = true },
            onDismiss = { adding = null },
        )
    }
    editing?.let { item ->
        PantryItemDialog(
            item, isNew = editingNew,
            onSave = { vm.savePantryItem(it); editing = null },
            onRemove = { vm.removePantryItem(item.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** Scan a barcode or search King Soopers to add an item. */
@Composable
private fun PantryAddDialog(
    vm: MealsViewModel, st: UserState, shelf: String?, note: String?,
    onPicked: (PantryItem) -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(note) }
    var scannedUpc by remember { mutableStateOf("") }
    val found by vm.suggestions.collectAsState()
    LaunchedEffect(query) {
        delay(350)
        vm.searchSuggestions(query)
    }
    val close = { vm.suggestions.value = emptyList(); onDismiss() }

    AppDialog(
        onDismiss = close,
        eyebrow = "Kitchen",
        title = if (shelf != null) "Add to ${Pantry.shelfLabel(shelf)}" else "Add an item",
        centered = true,
        actions = listOf(DialogAction("Cancel", onClick = close)),
    ) {
            Column {
                Button(
                    onClick = {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                            .build()
                        GmsBarcodeScanning.getClient(ctx, options).startScan()
                            .addOnSuccessListener { code ->
                                val raw = code.rawValue.orEmpty()
                                scannedUpc = raw
                                status = "Looking up $raw…"
                                scope.launch {
                                    val p = vm.lookupBarcode(raw)
                                    if (p != null) {
                                        status = null
                                        vm.suggestions.value = emptyList()
                                        onPicked(vm.pantryDraft(p.title, p.size, p.upc.ifBlank { raw }, shelf))
                                    } else {
                                        status = "Couldn't find that barcode. Type the name below."
                                    }
                                }
                            }
                            .addOnFailureListener { e -> status = "Scanner unavailable: ${e.message ?: "try again"}" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Scan again") }
                status?.let {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (it.startsWith("Looking")) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "or search", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                CompactSearch(query, { query = it }, placeholder = "Thyme, olive oil, flour…")
                if (query.trim().length >= 2) {
                    Text(
                        "Add \"${query.trim()}\"",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { vm.suggestions.value = emptyList(); onPicked(vm.pantryDraft(query.trim(), "", scannedUpc, shelf)) }
                            .padding(vertical = 10.dp),
                    )
                    HorizontalDivider()
                    found.forEach { p ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { vm.suggestions.value = emptyList(); onPicked(vm.pantryDraft(p.title, p.size, p.upc, shelf)) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.size.isNotBlank()) {
                                Text(p.size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                    if (st.kroger.store.id.isEmpty()) {
                        Text(
                            "Pick your store in Settings to see King Soopers products.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
    }
}

/** One item: drag the big container up or down to set how much is left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryItemDialog(
    start: PantryItem, isNew: Boolean,
    onSave: (PantryItem) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit,
) {
    var item by remember(start.id) { mutableStateOf(start) }
    var level by remember(start.id) { mutableDoubleStateOf(start.level) }
    var confirmRemove by remember { mutableStateOf(false) }
    var dateDigits by remember(start.id) { mutableStateOf(if (start.bestBy > 0) digitsFor(start.bestBy) else "") }
    fun plainNum(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else "%.1f".format(java.util.Locale.US, d)
    var cupText by remember(start.id) { mutableStateOf(if (start.cupOz > 0) plainNum(start.cupOz) else "") }
    val dateBad = dateDigits.isNotEmpty() && parseTypedDate(dateDigits) == null
    val counted = item.measure == Pantry.COUNT

    fun setFrom(y: Float, h: Float) {
        val v = 1.0 - ((y / h) * 120.0 - 6.0) / 108.0
        level = (Math.round(v.coerceIn(0.0, 1.0) * 20) / 20.0)
    }
    val current = if (counted) item.left else level * item.full

    AppDialog(
        onDismiss = onDismiss,
        eyebrow = Pantry.shelfLabel(item.shelf),
        title = if (isNew) "Add to the Kitchen" else item.term.replaceFirstChar { it.uppercase() },
        centered = true,
        actions = listOfNotNull(
            DialogAction(if (isNew) "Add" else "Save", ActionKind.Primary, enabled = !dateBad) {
                val term = item.term.trim().ifEmpty { Logic.searchTerm(item.name) }
                onSave(if (counted) item.copy(term = term) else item.copy(term = term, left = level * item.full))
            },
            if (!isNew) DialogAction("Remove from the Kitchen", ActionKind.Danger) { confirmRemove = true } else null,
            DialogAction("Cancel", onClick = onDismiss),
        ),
    ) {
            Column {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.size.isNotBlank()) {
                    Text(item.size, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    val shown = if (counted) (if (item.left > 0) 1.0 else 0.0) else level
                    ContainerView(
                        item.kind, shown, low = shown < 0.25,
                        modifier = Modifier.size(130.dp, 156.dp)
                            .then(
                                if (counted) Modifier else Modifier
                                    .pointerInput(Unit) { detectTapGestures { setFrom(it.y, size.height.toFloat()) } }
                                    .pointerInput(Unit) {
                                        detectDragGestures(onDragStart = { setFrom(it.y, size.height.toFloat()) }) { change, _ ->
                                            change.consume()
                                            setFrom(change.position.y, size.height.toFloat())
                                        }
                                    },
                            ),
                        stroke = 3.dp,
                    )
                }
                if (counted) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { item = item.copy(left = (item.left - 1).coerceAtLeast(0.0)) }) { Text("−") }
                        Text(Pantry.amountText(item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        OutlinedButton(onClick = { item = item.copy(left = item.left + 1, full = maxOf(item.full, item.left + 1)) }) { Text("+") }
                    }
                } else {
                    Text(
                        "${Math.round(level * 100)}% · ${Pantry.amountText(item, current)}",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Drag up or down to set how much is left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Freshness: how long it's been, how long it usually keeps, and the package's best-by date.
                val fresh = Pantry.freshness(item)
                val life = Pantry.shelfLifeDays(item)
                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp))
                Text("Freshness", style = MaterialTheme.typography.labelLarge)
                if (!isNew && item.addedAt > 0) {
                    Text(
                        boughtAgo(item.addedAt).replaceFirstChar { it.uppercase() } + (life?.let { " · good for ${Pantry.lifeText(it)}" } ?: " · keeps a long time"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (life != null) {
                    Text("Usually good for ${Pantry.lifeText(life)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val dateWord = when (item.dateKind) { "sell" -> "sell-by"; "biub" -> "BIUB"; else -> "best-by" }
                when (fresh) {
                    Pantry.Fresh.EXPIRED -> Text(
                        if (Pantry.isSpice(item)) "Past its $dateWord: may be weak" else "Past its $dateWord date",
                        style = MaterialTheme.typography.bodySmall,
                        color = LOW,
                    )
                    Pantry.Fresh.SOON -> Text(if (item.bestBy > 0) "Date is coming up: use soon" else "Getting old: use soon", style = MaterialTheme.typography.bodySmall, color = LOW)
                    else -> {}
                }
                if (fresh == Pantry.Fresh.EXPIRED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("I'm fine using it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = item.useAnyway, onCheckedChange = { item = item.copy(useAnyway = it) })
                    }
                }
                // The date off the package: pick which kind, then type it (slashes fill in).
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = item.dateKind == "best", onClick = { item = item.copy(dateKind = "best") }, label = { Text("Best by") })
                    FilterChip(selected = item.dateKind == "sell", onClick = { item = item.copy(dateKind = "sell") }, label = { Text("Sell by") })
                    // "Best if used by": what spice jars say.
                    FilterChip(selected = item.dateKind == "biub", onClick = { item = item.copy(dateKind = "biub") }, label = { Text("BIUB") })
                }
                val badNote: (@Composable () -> Unit)? = if (dateBad) { { Text("Not a real date") } } else null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        dateDigits,
                        { v ->
                            dateDigits = v.filter { it.isDigit() }.take(8)
                            val ms = parseTypedDate(dateDigits)
                            if (dateDigits.isEmpty()) item = item.copy(bestBy = 0L)
                            else if (ms != null) item = item.copy(bestBy = ms)
                        },
                        label = { Text(when (item.dateKind) { "sell" -> "Sell-by date"; "biub" -> "Best if used by"; else -> "Best-by date" }) },
                        placeholder = { Text("MM/DD/YY") },
                        singleLine = true,
                        isError = dateBad,
                        supportingText = badNote,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = DateSlashes,
                        modifier = Modifier.weight(1f),
                    )
                    if (dateDigits.isNotEmpty()) {
                        TextButton(onClick = { dateDigits = ""; item = item.copy(bestBy = 0L) }) { Text("Clear") }
                    }
                }
                if (item.bestBy > 0 && !dateBad) {
                    val until = Pantry.goodUntil(item)
                    Text(
                        if (until > item.bestBy) "Usually good until about ${bestByText(until)}" else "Good until ${bestByText(until)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isNew) {
                    TextButton(onClick = { item = item.copy(addedAt = System.currentTimeMillis()) }) { Text("Just bought (restart the clock)") }
                }

                OutlinedTextField(
                    item.term, { item = item.copy(term = it.lowercase()) },
                    label = { Text("Covers this shopping item") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                // Sold by weight: how much 1 cup weighs lets the app turn recipe cups and spoons into package weight.
                if (Pantry.isWeightSize(item.size) && item.measure != Pantry.COUNT && item.measure != Pantry.STICK && item.measure != Pantry.LB && item.measure != Pantry.EGG) {
                    val auto = Pantry.cupOzFor(item.term + " " + item.name, item.kind)
                    OutlinedTextField(
                        cupText,
                        { v ->
                            cupText = v.filter { it.isDigit() || it == '.' }.take(6)
                            val oz = cupText.toDoubleOrNull() ?: 0.0
                            val (m, full) = Pantry.measureFor(item.term + " " + item.name, item.kind, item.size, oz)
                            item = item.copy(cupOz = oz, measure = m, full = full, left = level * full)
                        },
                        label = { Text("How much is 1 cup of this? (oz)") },
                        placeholder = { Text("about ${plainNum(Math.round(auto * 10) / 10.0)} (the app's guess)") },
                        supportingText = { Text("Optional. Helps track how much is left when recipes use cups or spoons.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Text("Container", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pantry.KINDS.forEach { (k, label) ->
                        FilterChip(
                            selected = item.kind == k,
                            onClick = {
                                if (k != item.kind) {
                                    val (m, full) = Pantry.measureFor(item.term + " " + item.name, k, item.size)
                                    item = item.copy(kind = k, measure = m, full = full, left = if (m == Pantry.COUNT) 1.0 else level * full)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Text("Shelf", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pantry.SHELVES.forEach { (k, label) ->
                        FilterChip(
                            selected = item.shelf == k,
                            onClick = {
                                // Moving to Spices with no date yet: spice jars say "Best if used by".
                                item = item.copy(shelf = k, dateKind = if (k == "spices" && item.bestBy == 0L && item.dateKind == "best") "biub" else item.dateKind)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
    }

    if (confirmRemove) {
        AppDialog(
            onDismiss = { confirmRemove = false },
            title = "Remove ${item.term}?",
            centered = true,
            actions = listOf(
                DialogAction("Remove", ActionKind.Danger) { confirmRemove = false; onRemove() },
                DialogAction("Cancel") { confirmRemove = false },
            ),
        ) {
            Text("It comes off your Kitchen shelves. You can add it again any time.")
        }
    }
}
