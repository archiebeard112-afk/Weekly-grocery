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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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

private val PLENTY = Color(0xFF1D9E75)
private val LOW = Color(0xFFBA7517)
private val SHELF_WOOD = Color(0xFF8B5A2B)
private val FREEZER = Color(0xFF85B7EB)

private fun freezerAmount(f: FreezerItem): String =
    if (f.byCount) "${f.count}" else "${Logic.fmt(f.lb)} lb"

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
private fun FreezerBag(modifier: Modifier, old: Boolean) {
    val outline = if (old) LOW else FREEZER
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bag = RoundRect(w * 0.12f, h * 0.18f, w * 0.88f, h * 0.96f, CornerRadius(w * 0.12f, w * 0.12f))
        val path = Path().apply { addRoundRect(bag) }
        drawPath(path, FREEZER.copy(alpha = 0.25f))
        drawPath(path, outline, style = Stroke(2.dp.toPx()))
        // Zip seal across the top.
        drawLine(outline, Offset(w * 0.12f, h * 0.30f), Offset(w * 0.88f, h * 0.30f), strokeWidth = 2.dp.toPx())
        // Label.
        drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(w * 0.26f, h * 0.45f), Size(w * 0.48f, h * 0.22f), CornerRadius(3f, 3f))
    }
}

/** Add or adjust something in the freezer. */
@Composable
private fun FreezerDialog(start: FreezerItem, isNew: Boolean, onSave: (FreezerItem) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var item by remember(start.id) { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add to freezer" else item.name) },
        text = {
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
                if (!isNew) {
                    Text(
                        frozenAgo(item.frozenAt), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    TextButton(onClick = onRemove, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Remove from freezer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = item.name.trim()
                    if (name.isNotEmpty()) {
                        onSave(item.copy(name = name.replaceFirstChar { it.uppercase() }, term = item.term.ifEmpty { Logic.searchTerm(name) }))
                    }
                },
            ) { Text(if (isNew) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    var freezingNew by remember { mutableStateOf(false) }
    val lowCount = st.pantry.count { Pantry.isLow(it) }

    Scaffold(
        modifier = Modifier.swipeBack(onBack),
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            item {
                // Same title spot and button style as Favorites' "Add recipe".
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pantry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = { adding = "" },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add item")
                    }
                }
            }
            item {
                Text(
                    if (st.pantry.isEmpty()) "Your pantry is empty. Tap + to scan or search for what you have."
                    else "${st.pantry.size} item${if (st.pantry.size == 1) "" else "s"}" + if (lowCount > 0) " · $lowCount low" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                )
            }
            Pantry.SHELVES.forEach { (key, label) ->
                val onShelf = st.pantry.filter { it.shelf == key }.sortedBy { it.term }
                item(key = "shelf_$key") {
                    Text(
                        label, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                    LazyRow(verticalAlignment = Alignment.Bottom) {
                        items(onShelf, key = { it.id }) { p ->
                            Column(
                                Modifier.width(70.dp).clickable { editing = p; editingNew = false }.padding(bottom = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ContainerView(p.kind, p.level, Pantry.isLow(p), Modifier.size(52.dp, 62.dp))
                                Text(
                                    p.term.replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        item {
                            Box(
                                Modifier.padding(start = 6.dp, bottom = 20.dp).size(50.dp, 62.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clickable { adding = key },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Add to $label", tint = MaterialTheme.colorScheme.outline) }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp).height(6.dp)
                            .background(SHELF_WOOD, RoundedCornerShape(2.dp)),
                    )
                }
            }
            // The freezer: leftover meat, counted by the piece (whole cuts) or by the pound.
            item(key = "freezer") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(FREEZER.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, FREEZER.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    Text("🧊 Freezer", style = MaterialTheme.typography.labelLarge, color = FREEZER)
                    if (st.freezer.isEmpty()) {
                        Text(
                            "Leftover meat goes here when you finish a week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    LazyRow(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                        items(st.freezer, key = { it.id }) { f ->
                            val old = System.currentTimeMillis() - f.frozenAt > 90L * 86_400_000L
                            Column(
                                Modifier.width(76.dp).clickable { freezing = f; freezingNew = false }.padding(bottom = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FreezerBag(Modifier.size(52.dp, 56.dp), old)
                                Text(
                                    f.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
                                )
                                Text(freezerAmount(f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    frozenAgo(f.frozenAt), fontSize = 10.sp,
                                    color = if (old) LOW else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item {
                            Box(
                                Modifier.padding(start = 6.dp, bottom = 34.dp).size(50.dp, 56.dp)
                                    .border(1.dp, FREEZER.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        freezing = FreezerItem(java.util.UUID.randomUUID().toString(), "", "", count = 1,
                                            frozenAt = System.currentTimeMillis(), byCount = true)
                                        freezingNew = true
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Add to freezer", tint = FREEZER) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(PLENTY, RoundedCornerShape(2.dp)))
                    Text(" Plenty    ", style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.size(10.dp).background(LOW, RoundedCornerShape(2.dp)))
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
    adding?.let { shelf ->
        PantryAddDialog(
            vm, st, shelf.ifEmpty { null },
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
    vm: MealsViewModel, st: UserState, shelf: String?,
    onPicked: (PantryItem) -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var scannedUpc by remember { mutableStateOf("") }
    val found by vm.suggestions.collectAsState()
    LaunchedEffect(query) {
        delay(350)
        vm.searchSuggestions(query)
    }
    val close = { vm.suggestions.value = emptyList(); onDismiss() }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (shelf != null) "Add to ${Pantry.shelfLabel(shelf)}" else "Add to pantry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                ) { Text("Scan barcode") }
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
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/** One item: drag the big container up or down to set how much is left. */
@Composable
private fun PantryItemDialog(
    start: PantryItem, isNew: Boolean,
    onSave: (PantryItem) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit,
) {
    var item by remember(start.id) { mutableStateOf(start) }
    var level by remember(start.id) { mutableDoubleStateOf(start.level) }
    var confirmRemove by remember { mutableStateOf(false) }
    val counted = item.measure == Pantry.COUNT

    fun setFrom(y: Float, h: Float) {
        val v = 1.0 - ((y / h) * 120.0 - 6.0) / 108.0
        level = (Math.round(v.coerceIn(0.0, 1.0) * 20) / 20.0)
    }
    val current = if (counted) item.left else level * item.full

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add to pantry" else item.term.replaceFirstChar { it.uppercase() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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

                OutlinedTextField(
                    item.term, { item = item.copy(term = it.lowercase()) },
                    label = { Text("Covers this shopping item") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
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
                        FilterChip(selected = item.shelf == k, onClick = { item = item.copy(shelf = k) }, label = { Text(label) })
                    }
                }
                if (!isNew) {
                    TextButton(onClick = { confirmRemove = true }, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Remove from pantry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val term = item.term.trim().ifEmpty { Logic.searchTerm(item.name) }
                onSave(if (counted) item.copy(term = term) else item.copy(term = term, left = level * item.full))
            }) { Text(if (isNew) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${item.term}?") },
            text = { Text("It comes off your pantry shelves. You can add it again any time.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}
