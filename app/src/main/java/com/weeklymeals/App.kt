package com.weeklymeals

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.animation.core.LinearEasing

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.withStyle

import androidx.compose.ui.text.buildAnnotatedString

import androidx.compose.ui.text.SpanStyle

import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp

private enum class Overlay { None, Settings, AddRecipe, History, Pantry }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MealsViewModel) {
    val st by vm.state.collectAsState()
    val picking by vm.picking.collectAsState()
    // Kept here so the Shop list keeps its scroll position while a product list is open.
    val shopListState = rememberLazyListState()
    // Same for Favorites, so going back from a recipe returns to the same spot.
    val favGridState = rememberLazyGridState()
    // Search text, filters and scroll kept here so Back from a recipe returns to the same results.
    val browseSearch = remember { SearchState() }
    val favSearch = remember { SearchState() }
    val browseResults = rememberLazyGridState()
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    // Tabs: 0 Week (opens here), 1 Browse, 2 Favorites, 3 Shop.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var confirmRemove by remember { mutableStateOf<Recipe?>(null) }
    // Hold a recipe tile: delete (your recipes) or hide (built-in ones), after confirming.
    var confirmDelete by remember { mutableStateOf<Recipe?>(null) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    var cooking by rememberSaveable { mutableStateOf<Int?>(null) }
    // An online recipe being previewed (Spoonacular id).
    var webViewing by rememberSaveable { mutableStateOf<String?>(null) }
    // When previewing an online recipe to use as a side: the meal it's for.
    var sideWebFor by rememberSaveable { mutableStateOf<Int?>(null) }
    // A just-saved recipe to open once it's in your recipes.
    var openWhenSaved by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(openWhenSaved, st.custom) {
        openWhenSaved?.let { id ->
            if (vm.recipes(st).containsKey(id)) { viewing = id; webViewing = null; openWhenSaved = null }
        }
    }
    var addFromLink by rememberSaveable { mutableStateOf<String?>(null) }
    var addLinkMode by rememberSaveable { mutableStateOf(false) }
    var askAddMode by remember { mutableStateOf(false) }
    var askOverlay by remember { mutableStateOf(false) }
    var pendingFilter by remember { mutableStateOf<String?>(null) }

    // The floating bubble outside the app: tell it what's cooking, and open the steps when it's tapped.
    val bubbleCtx = LocalContext.current
    val appVisible by CookOverlay.appVisible.collectAsState()
    val cookOn = cookBubbleOn(st)
    LaunchedEffect(cookOn, st.cookingId, st.timers, appVisible) {
        val id = st.cookingId
        CookOverlay.info.value = if (cookOn && id != null) vm.recipes(st)[id]?.let { r ->
            CookOverlay.Info(id, r.name, st.timers.filterKeys { it.startsWith("r$id:") || it.startsWith("r${id}side:") }.values.toList())
        } else null
        if (appVisible) CookOverlay.sync(bubbleCtx)
    }
    val openCook by vm.openCook.collectAsState()
    LaunchedEffect(openCook, st.cookingId) {
        if (openCook && st.cookingId != null) {
            vm.openCook.value = false
            overlay = Overlay.None
            cooking = st.cookingId
            st.cookingId?.let { vm.startCooking(it) }
        }
    }
    val sharedLink by vm.sharedLink.collectAsState()
    LaunchedEffect(sharedLink) {
        sharedLink?.let {
            addFromLink = it
            addLinkMode = true
            viewing = null
            cooking = null
            overlay = Overlay.AddRecipe
            vm.sharedLink.value = null
        }
    }
    val ctx = LocalContext.current
    val openCart by vm.openCart.collectAsState()
    LaunchedEffect(openCart) {
        if (openCart) {
            vm.openCart.value = false
            openKingSoopersCart(ctx)
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) vm.message.value = "Allow notifications so timers can ring"
    }
    val timerCtl: (Recipe) -> TimerCtl = { r ->
        TimerCtl(
            prefix = "r${r.id}", title = r.name, running = st.timers,
            start = { key, label, minutes ->
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.startTimer(key, label, minutes)
            },
            stop = { vm.stopTimer(it) },
        )
    }

    LaunchedEffect(msg) {
        msg?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }

    // On the four main tabs the keyboard slides over the screen instead of squishing it.
    // Other screens (Settings, Add recipe, product picker) still make room for it.
    val onMainTabs = picking == null && overlay == Overlay.None && cooking == null && viewing == null && webViewing == null
    LaunchedEffect(onMainTabs) {
        (ctx as? android.app.Activity)?.window?.setSoftInputMode(
            if (onMainTabs) android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            else android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
    }

    // Leaving Browse or Favorites for another tab (or Pantry/History/Settings) clears the
    // search, so coming back shows the plain page. Opening a recipe doesn't change the tab,
    // so search results are still there when you go back from one.
    LaunchedEffect(tab, overlay) {
        listOf(browseSearch, favSearch).forEach { it.query.value = ""; it.filters.value = emptyList() }
        // From the Week tab's leftovers card: open Browse filtered to meals that use them up.
        pendingFilter?.let { browseSearch.filters.value = listOf(it); pendingFilter = null }
        launch { runCatching { browseResults.scrollToItem(0) } }
        launch { runCatching { favGridState.scrollToItem(0) } }
    }

    // After adding a meal, jump to Week so you can see it (it flashes once).
    val added by vm.justAdded.collectAsState()
    var flashId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(added) {
        added?.let { id ->
            flashId = id
            tab = 0
            overlay = Overlay.None
            viewing = null
            webViewing = null
            sideWebFor = null
            vm.justAdded.value = null
        }
    }

    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }
    BackHandler(enabled = overlay == Overlay.None && viewing != null && cooking == null && webViewing == null) { viewing = null }
    // An online preview can sit on top of a recipe page (picking a side from it); Back closes just the preview.
    BackHandler(enabled = overlay == Overlay.None && webViewing != null) { webViewing = null; sideWebFor = null }

    val toggleFav: (Recipe) -> Unit = { r ->
        if (Logic.isFav(r, st)) confirmRemove = r else vm.addFav(r.id)
    }

    Box(Modifier.fillMaxSize()) {
    if (picking != null) {
        ProductPicker(vm, picking!!) { vm.picking.value = null }
    } else if (overlay == Overlay.None && cooking != null) {
        CookScreen(vm, st, cooking!!, timerCtl, onDone = { cooking = null }) { cooking = null }
    } else if (overlay == Overlay.None && webViewing != null) {
        WebRecipeScreen(
            vm, st, webViewing!!, forSide = sideWebFor,
            onSaved = { openWhenSaved = it },
        ) { webViewing = null; sideWebFor = null }
    } else if (overlay == Overlay.None && viewing != null) {
        RecipeScreen(
            vm, st, viewing!!, toggleFav, timerCtl, onCook = {
                viewing?.let { vm.startCooking(it) }
                CookOverlay.hiddenFor = null
                cooking = viewing
                if (!st.overlayAsked && !CookOverlay.canDraw(ctx)) askOverlay = true
            },
            // After holding to remove: back to Week to see the open day.
            onRemoved = { viewing = null; overlay = Overlay.None; tab = 0 },
        ) { viewing = null }
    } else if (overlay == Overlay.AddRecipe) {
        AddRecipeScreen(vm, linkMode = addLinkMode, startLink = addFromLink) { overlay = Overlay.None; addFromLink = null }
    } else {
        Scaffold(
            // Ignore the keyboard here so it slides over the tabs instead of shrinking them.
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text("Weekly Meals") },
                    actions = {
                        // Tap to open below the bar (highlighted); tap again to close.
                        val toggle: (Overlay) -> Unit = { o -> overlay = if (overlay == o) Overlay.None else o }
                        TopIcon(PantryIcon, "Kitchen", overlay == Overlay.Pantry) { toggle(Overlay.Pantry) }
                        TopIcon(Icons.Filled.List, "History", overlay == Overlay.History) { toggle(Overlay.History) }
                        TopIcon(Icons.Filled.Settings, "Settings", overlay == Overlay.Settings) { toggle(Overlay.Settings) }
                        Spacer(Modifier.width(4.dp))
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    // The open tab sits in a lime pill.
                    val navColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    )
                    NavigationBarItem(
                        selected = tab == 0 && overlay == Overlay.None, onClick = { tab = 0; overlay = Overlay.None },
                        icon = { CountIcon(mealCount(st)) { Icon(Icons.Filled.DateRange, null) } },
                        label = { TabLabel("Week") }, colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == 1 && overlay == Overlay.None, onClick = { tab = 1; overlay = Overlay.None },
                        icon = { Icon(Icons.Filled.Search, null) }, label = { TabLabel("Browse") }, colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == 2 && overlay == Overlay.None, onClick = { tab = 2; overlay = Overlay.None },
                        icon = { Icon(Icons.Filled.Favorite, null) }, label = { TabLabel("Favorites") }, colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == 3 && overlay == Overlay.None, onClick = { tab = 3; overlay = Overlay.None },
                        icon = { Icon(Icons.Filled.ShoppingCart, null) }, label = { TabLabel("Shop") }, colors = navColors,
                    )
                }
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize()
                    .then(if (overlay == Overlay.None) Modifier.keepFullHeight() else Modifier),
            ) {
                when {
                    overlay == Overlay.Pantry -> PantryScreen(vm, st) { overlay = Overlay.None }
                    overlay == Overlay.History -> HistoryScreen(vm, st) { overlay = Overlay.None }
                    overlay == Overlay.Settings -> SettingsScreen(vm, st) { overlay = Overlay.None }
                    else -> when (tab) {
                    0 -> WeekScreen(
                        vm, st, onOpen = { viewing = it }, onBrowse = { tab = 1 }, flashId = flashId,
                        onLeftovers = { pendingFilter = "leftovers"; tab = 1 },
                        onAddStore = { vm.setBrowseScope(BrowseScope.STORE); tab = 1 },
                        onCook = { id -> vm.startCooking(id); cooking = id },
                    ) { flashId = null }
                    1 -> BrowseScreen(
                        vm, st, toggleFav,
                        browseSearch, browseResults, onHold = { confirmDelete = it },
                        onWeb = { webViewing = it }, onSettings = { overlay = Overlay.Settings },
                    ) { viewing = it }
                    2 -> FavoritesScreen(vm, st, toggleFav, favGridState, favSearch, onHold = { confirmDelete = it }, onOpen = { viewing = it }) { askAddMode = true }
                    else -> ShopScreen(vm, st, shopListState) { overlay = Overlay.Settings }
                    }
                }
            }
        }
    }
    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    // Left cooking mode mid-cook: a bubble to get back to it from anywhere in the app.
    if (cooking == null && picking == null && cookBubbleOn(st)) {
        CookBubble(
            vm, st,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 92.dp),
        ) { cooking = st.cookingId; st.cookingId?.let { vm.startCooking(it) } }
    }
    }

    // "Pick a side" when adding a meal to the week.
    val pickSide by vm.sidePicker.collectAsState()
    pickSide?.let { id ->
        val r = vm.recipes(st)[id]
        if (r == null) { vm.sidePicker.value = null } else {
            SidePicker(
                vm, st, r,
                onOnlineSide = { ref -> sideWebFor = id; webViewing = ref },
            )
        }
    }

    // A newer version came out: offer it (not while you're cooking).
    val newVersion by vm.autoUpdate.collectAsState()
    newVersion?.let { rel ->
        if (cooking == null) {
            val up by vm.update.collectAsState()
            LaunchedEffect(up) {
                (up as? UpdateState.Ready)?.let { r ->
                    if (Updater.canInstall(ctx)) Updater.install(ctx, r.file) else Updater.openInstallPermission(ctx)
                    vm.autoUpdate.value = null
                }
            }
            val busy = up is UpdateState.Downloading
            AlertDialog(
                onDismissRequest = { if (!busy) vm.autoUpdate.value = null },
                title = { Text("Update available") },
                text = {
                    Column {
                        Text("${rel.name} is ready to install.")
                        (up as? UpdateState.Downloading)?.let { d ->
                            LinearProgressIndicator(
                                progress = { d.percent / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            Text("Downloading… ${d.percent}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        (up as? UpdateState.Error)?.let { e ->
                            Text(e.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = { vm.downloadUpdate(rel) }) { Text("Update") }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = !busy, onClick = { vm.skipVersion(rel) }) { Text("Skip this version") }
                        TextButton(enabled = !busy, onClick = { vm.autoUpdate.value = null }) { Text("Later") }
                    }
                },
            )
        }
    }

    // The app crashed last time: show what happened so it can be sent in and fixed.
    var crash by remember { mutableStateOf(CrashLog.pending(ctx)) }
    crash?.let { text ->
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { CrashLog.clear(ctx); crash = null },
            title = { Text("The app closed unexpectedly") },
            text = {
                Column {
                    Text("Copy the details and send them over so it can be fixed.")
                    Text(
                        text.lines().take(8).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clip.setText(androidx.compose.ui.text.AnnotatedString(text))
                    vm.message.value = "Crash details copied"
                    CrashLog.clear(ctx); crash = null
                }) { Text("Copy details") }
            },
            dismissButton = { TextButton(onClick = { CrashLog.clear(ctx); crash = null }) { Text("Close") } },
        )
    }

    if (askOverlay) {
        AlertDialog(
            onDismissRequest = { askOverlay = false; vm.setOverlayAsked() },
            title = { Text("Keep the bubble outside the app?") },
            text = {
                Text(
                    "The cooking bubble can float over your home screen and other apps, so you can jump back " +
                        "to your steps. Turn on \"Allow display over other apps\" for Weekly Meals on the next screen.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askOverlay = false; vm.setOverlayAsked()
                    runCatching { ctx.startActivity(CookOverlay.permissionIntent(ctx)) }
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { askOverlay = false; vm.setOverlayAsked() }) { Text("Not now") } },
        )
    }

    if (askAddMode) {
        AlertDialog(
            onDismissRequest = { askAddMode = false },
            title = { Text("Add a recipe") },
            text = { Text("Import it from a recipe website, or type it in yourself.") },
            confirmButton = {
                TextButton(onClick = {
                    askAddMode = false; addLinkMode = true; addFromLink = null; overlay = Overlay.AddRecipe
                }) { Text("From a link") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askAddMode = false; addLinkMode = false; addFromLink = null; overlay = Overlay.AddRecipe
                }) { Text("Type it in") }
            },
        )
    }

    confirmDelete?.let { r ->
        val mine = r.imported
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (mine) "Delete ${r.name}?" else "Hide ${r.name}?") },
            text = {
                Text(
                    (if (mine) "This removes the recipe for good."
                    else "It won't show in Browse, search or Favorites. You can bring it back in Settings → Hidden recipes.") +
                        if (r.id in st.week) " It will also come off this week and the shopping list." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteRecipe(r); confirmDelete = null }) {
                    Text(if (mine) "Delete" else "Hide", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    confirmRemove?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from favorites?") },
            text = { Text("Are you sure you want to remove ${r.name} from your favorites?") },
            confirmButton = {
                TextButton(onClick = { vm.removeFav(r.id); confirmRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Keep") } },
        )
    }
}

/** Bottom-bar label, always centered on one line. */
@Composable
private fun TabLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
}

/** Tab icon with a small number bubble when count > 0. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountIcon(count: Int, icon: @Composable () -> Unit) {
    if (count <= 0) icon() else BadgedBox(badge = { androidx.compose.material3.Badge { Text("$count") } }) { icon() }
}

// ---- Shared pieces ----------------------------------------------------------------

/** Lets steps show timer buttons (only for recipes in your week). */
data class TimerCtl(
    val prefix: String,
    val title: String,
    val running: Map<String, Long>,
    val start: (key: String, label: String, minutes: Int) -> Unit,
    val stop: (key: String) -> Unit,
)

@Composable
private fun Steps(steps: List<String>, ctl: TimerCtl? = null) {
    steps.forEachIndexed { n, s ->
        Text("${n + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
        if (ctl != null) {
            val times = Logic.stepTimes(s)
            if (times.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    times.forEachIndexed { k, t ->
                        TimerButton("${ctl.prefix}:$n:$k", "${ctl.title}: step ${n + 1}", t, ctl)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimerButton(key: String, label: String, t: Logic.StepTime, ctl: TimerCtl) {
    val end = ctl.running[key]
    val small = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    if (end == null) {
        OutlinedButton(
            onClick = { ctl.start(key, label, t.minutes) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { Text("⏱ " + Logic.fmtMinutes(t.minutes) + if (t.perSide) " per side" else "") }
        return
    }
    var now by remember(key) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key, end) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= end) break
            delay(500)
        }
    }
    val left = ((end - now) / 1000).coerceAtLeast(0)
    if (left > 0) {
        FilledTonalButton(
            onClick = { ctl.stop(key) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { Text("⏱ %d:%02d  ·  Stop".format(left / 60, left % 60)) }
    } else {
        Button(
            onClick = { ctl.stop(key) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { Text("⏰ Done! Tap to stop") }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun RecipeDetails(
    r: Recipe, serv: Int, side: Side?, ctl: TimerCtl? = null,
    cost: MealsViewModel.MealCost? = null, budgetMax: Double = 0.0,
    expired: Map<String, String> = emptyMap(),
    shared: List<Pair<String, String>> = emptyList(),
) {
    // Each shopping item's price shows once, on its first ingredient line.
    val priced = mutableSetOf<String>()
    @Composable
    fun line(i: Ingredient, serv: Int, base: Int) {
        val term = Logic.searchTerm(i.name)
        val price = cost?.lines?.get(term)
        val first = price != null && priced.add(term)
        val zone = price?.zone
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("• " + Logic.ingText(i, serv, base), style = MaterialTheme.typography.bodyMedium)
                // In the pantry but past its date.
                expired[term]?.let {
                    val amber = androidx.compose.ui.graphics.Color(0xFFBA7517)
                    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        HaveIcon(zone ?: "pantry", Modifier.size(13.dp), amber)
                        Text(" $it", style = MaterialTheme.typography.labelSmall, color = amber)
                    }
                }
            }
            if (cost != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Same icon as its Kitchen zone (pantry, fridge, freezer, produce), in a
                        // fixed slot on the left so every dollar amount stays in line.
                        Box(Modifier.size(16.dp)) {
                            if (first && price?.have == true) HaveIcon(zone ?: "pantry")
                        }
                        // ♻️ = shared with another meal this week (or left over): always right by the price.
                        if (first && price != null && price.note.startsWith("♻️")) {
                            Text("♻️", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                price == null -> "—"
                                !first -> "incl. above"
                                price.have -> "$0.00"
                                else -> Sales.money(price.cost)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (first && price?.have == true) BUDGET_GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                    // The pack it comes from, or this meal's share of one used by other meals this week.
                    if (first && price != null && price.note.isNotEmpty() && !price.note.startsWith("♻️")) {
                        Text(
                            price.note, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    val mainServ = Logic.servFor(r, serv)
    Heading("Ingredients (for $mainServ)")
    r.ingredients.forEach { line(it, mainServ, r.base) }
    if (side != null && side.ingredients.isNotEmpty()) {
        Text("Side: ${side.label}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
        side.ingredients.forEach { line(it, serv, 4) }
    }
    if (cost != null) {
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row {
            Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(
                Sales.money(cost.total), style = MaterialTheme.typography.titleSmall,
                color = if (cost.total <= budgetMax) BUDGET_GREEN else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (cost.saved >= 0.01) {
            Row {
                Text(
                    "You save with what you have", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                )
                Text(Sales.money(cost.saved), style = MaterialTheme.typography.bodySmall, color = BUDGET_GREEN, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(
            "Prices are for whole packages, as you'd buy them. Shared ones are split between this week's meals. " +
                "Spices and pantry staples count only what you use.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
    // Shared this week: one line per ingredient, each meal and how much it gets.
    if (shared.isNotEmpty()) {
        Heading("Shared this week")
        shared.forEach { (name, meals) ->
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$name: ") }
                    append(meals)
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
    Heading("Steps")
    Steps(r.steps, ctl)
    if (side != null) {
        Heading("Side: ${side.label}")
        Spacer(Modifier.padding(2.dp))
        Steps(side.steps, ctl?.copy(prefix = ctl.prefix + "side", title = side.label.substringAfter(" ")))
    }
}

@Composable
private fun FavButton(r: Recipe, st: UserState, onToggle: (Recipe) -> Unit, modifier: Modifier = Modifier) {
    val fav = Logic.isFav(r, st)
    IconButton(onClick = { onToggle(r) }, modifier = modifier) {
        Icon(
            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private val cardColors @Composable get() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

// ---- Browse ------------------------------------------------------------------------

@Composable
private fun BrowseScreen(
    vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit,
    search: SearchState, resultsState: LazyGridState,
    onHold: (Recipe) -> Unit,
    onWeb: (String) -> Unit, onSettings: () -> Unit, onOpen: (Int) -> Unit,
) {
    val ids by vm.browse.collectAsState()
    val saleOnly by vm.saleOnly.collectAsState()
    val progress by vm.salesProgress.collectAsState()
    val all = vm.recipes(st)
    val cache = vm.salesCache(st)
    var query by search.query
    var filters by search.filters
    var showFilters by remember { mutableStateOf(false) }
    val searching = query.isNotBlank() || filters.isNotEmpty()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }

    // Slide the grid off to one side, swap the set, slide the new one in.
    fun slide(next: Boolean) {
        if (!next && !vm.canGoBackBrowse()) return
        scope.launch {
            offset.animateTo(if (next) -widthPx else widthPx, tween(180))
            if (next) vm.refreshBrowse() else vm.previousBrowse()
            offset.snapTo(if (next) widthPx else -widthPx)
            offset.animateTo(0f, tween(220))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    st.browseScope != BrowseScope.APP -> "Search"
                    searching -> "Search"
                    saleOnly -> "On sale"
                    else -> "10 ideas"
                },
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            // Filters live behind this button (swipe the tiles for more ideas).
            FiltersButton(filters.size + if (saleOnly) 1 else 0, showLabel = true) { showFilters = true }
        }
        // Where to search, one at a time (menu inside the search bar): this app's recipes, online recipes, or King Soopers products.
        val scopeKey = st.browseScope
        CompactSearch(
            query, { query = it }, Modifier.padding(top = 6.dp, bottom = 4.dp),
            placeholder = BrowseScope.hint(scopeKey),
            trailing = { ScopeMenu(scopeKey) { vm.setBrowseScope(it) } },
        )
        if (showFilters) {
            FiltersDialog(
                filters, onChange = { filters = it }, onDismiss = { showFilters = false },
                sale = saleOnly, onSale = { if (progress == null) vm.setSaleOnly(it) },
            )
        }

        progress?.let { (done, total) ->
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Checking King Soopers sale prices…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Text("$done of $total ingredients", style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }

        // Online and store results load a moment after you stop typing (only for the place you picked).
        val web by vm.webSearch.collectAsState()
        LaunchedEffect(query, st.spoonKey, st.webSource, scopeKey) {
            when (scopeKey) {
                BrowseScope.WEB -> { delay(900); vm.searchWeb(query) }
                BrowseScope.STORE -> { delay(500); vm.searchStore(query) }
                else -> {}
            }
        }

        if (scopeKey == BrowseScope.STORE) {
            StoreSearch(vm, st, query, onSettings, Modifier.weight(1f))
            return@Column
        }
        if (scopeKey == BrowseScope.WEB) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                when {
                    RecipeSources.effective(st) == RecipeSources.OFF -> {
                        Text(
                            "Online recipe search is turned off. You can turn it on in Settings.",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                        )
                        TextButton(onClick = onSettings) { Text("Open Settings") }
                    }
                    query.trim().length < 3 -> Text(
                        "Type at least 3 letters to search online recipes.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    )
                    else -> OnlineResults(vm, st, web, query, onWeb, onSettings)
                }
            }
            return@Column
        }

        if (searching) {
            // All matches, scrolling.
            val q = query.trim().lowercase()
            val matches = all.values.filter { r ->
                r.id !in st.removed &&
                matchesFilters(r, q, filters, { vm.mealCalories(st, it) }, { vm.isBudget(st, it) }, { vm.usesLeftovers(st, it) }) && (!saleOnly || Sales.savings(r, st.servings, cache) >= st.saleMin)
            }.let { list ->
                // Uses-leftovers filter: the meals that save you the most come first.
                if ("leftovers" in filters) list.sortedByDescending { vm.leftoverSavings(st, it) } else list.sortedBy { it.name }
            }
            LazyVerticalGrid(
                state = resultsState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "inapp") {
                    Text(
                        "${matches.size} match${if (matches.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(matches, key = { it.id }) { r ->
                    RecipeTile(
                        r, st, toggleFav, onAdd = { vm.requestAdd(r.id) }, onOpen = { onOpen(r.id) },
                        Modifier.fillMaxWidth().height(104.dp),
                        saleSave = Sales.savings(r, st.servings, cache),
                        onHold = { onHold(r) },
                        calories = vm.mealCalories(st, r),
                cost = vm.mealCost(st, r),
                        filters = filters,
                        leftover = vm.usesLeftovers(st, r),
                leftoverSave = vm.leftoverSavings(st, r),
                    )
                }
            }
            return@Column
        }

        val shown = ids.mapNotNull { all[it] }
        if (saleOnly && shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No meals save ${Sales.money(st.saleMin)} or more at your store today.", textAlign = TextAlign.Center)
            }
            return@Column
        }

        // The set of 10: swipe left for more, right to go back. It follows your finger.
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offset.value
                            when {
                                v < -widthPx * 0.25f -> slide(true)
                                v > widthPx * 0.25f && vm.canGoBackBrowse() -> slide(false)
                                else -> scope.launch { offset.animateTo(0f, spring()) }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, dx ->
                        change.consume()
                        // Resist when there's no previous set to go back to.
                        val step = if (dx > 0 && offset.value >= 0f && !vm.canGoBackBrowse()) dx * 0.25f else dx
                        scope.launch { offset.snapTo(offset.value + step) }
                    }
                }
                .graphicsLayer {
                    translationX = offset.value
                    alpha = 1f - (kotlin.math.abs(offset.value) / widthPx).coerceIn(0f, 0.5f)
                },
        ) {
            shown.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pair.forEach { r ->
                        RecipeTile(
                            r, st, toggleFav, onAdd = { vm.requestAdd(r.id) }, onOpen = { onOpen(r.id) },
                            Modifier.weight(1f).fillMaxHeight(),
                            saleSave = Sales.savings(r, st.servings, cache),
                            onHold = { onHold(r) },
                            calories = vm.mealCalories(st, r),
                cost = vm.mealCost(st, r),
                            filters = filters,
                            leftover = vm.usesLeftovers(st, r),
                leftoverSave = vm.leftoverSavings(st, r),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            repeat(5 - (shown.size + 1) / 2) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun RecipeTile(
    r: Recipe, st: UserState, toggleFav: (Recipe) -> Unit,
    onAdd: () -> Unit, onOpen: () -> Unit, modifier: Modifier,
    saleSave: Double = 0.0,
    onHold: (() -> Unit)? = null,
    calories: Int? = null,
    cost: MealsViewModel.MealCost? = null,
    filters: List<String> = emptyList(),
    leftover: Boolean = false,
    leftoverSave: Double = 0.0,
) {
    val inWeek = r.id in st.week
    // Each recipe is a block of its own color; a lime ring means it's in your week.
    Card(
        colors = CardDefaults.cardColors(containerColor = Look.color(r.id), contentColor = Look.Ink),
        border = if (inWeek) androidx.compose.foundation.BorderStroke(3.dp, Look.Lime) else null,
        modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onHold),
    ) {
      Box(Modifier.fillMaxSize()) {
      OnColor {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 3.dp, end = 2.dp, bottom = 1.dp)) {
            // Icon-only badges so they always fit (full labels are on the recipe page).
            // At most 2 badges: on sale, then cooking method, then Budget. A badge you're
            // filtering by always gets a spot, so you can see why the meal matched.
            val all = (if (saleSave >= st.saleMin) listOf("sale" to "Sale " + Sales.money(saleSave).substringBefore(".")) else emptyList()) +
                (if (leftover) listOf("leftovers" to "Leftovers") else emptyList()) +
                Logic.badgeKeys(r, cost?.let { it.total <= st.budgetMax } ?: false)
            val keep = (all.filter { it.first in filters } + all).distinct().take(2).toSet()
            val badges = all.filter { it in keep }.map { it.second }
            // Name, then the badges on their own line so two always fit, then calories and price.
            Row(Modifier.weight(1f).fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    val infoLine = badges.isNotEmpty() || calories != null
                    Text(
                        r.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp),
                        maxLines = if (infoLine || cost != null) 1 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (badges.isNotEmpty()) {
                        Row(
                            Modifier.padding(top = 2.dp, bottom = 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            badges.forEach { Badge(it, small = true) }
                        }
                    }
                    // Calories, then what you'd pay for this meal (bold at or under your budget line).
                    if (calories != null || cost != null) {
                        Text(
                            buildAnnotatedString {
                                calories?.let {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("%,d cal".format(it)) }
                                    if (cost != null) append("  ·  ")
                                }
                                cost?.let { c ->
                                    val cheap = c.total <= st.budgetMax
                                    withStyle(SpanStyle(color = if (cheap) Look.Ink else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (cheap) FontWeight.Bold else FontWeight.Normal)) {
                                        append(Sales.money(c.total))
                                    }
                                    if (c.saved >= 0.5) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" · pantry") }
                                }
                            },
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Logic.fmtShort(Logic.cookMinutes(r)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Compact buttons leave room above for name, calories/badges and price.
                FavButton(r, st, toggleFav, Modifier.size(32.dp))
                IconButton(onClick = onAdd, enabled = !inWeek, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (inWeek) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = if (inWeek) "In your week" else "Add to week",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
      }
      }
    }
}

/** "On sale this week" for one recipe's main ingredients. */
@Composable
private fun SaleSection(vm: MealsViewModel, st: UserState, r: Recipe) {
    if (st.kroger.store.id.isEmpty()) return
    val progress by vm.salesProgress.collectAsState()
    val cache = vm.salesCache(st)
    Heading("On sale this week")
    when {
        !Sales.isChecked(r, st.servings, cache) -> {
            if (progress != null) {
                Text("Checking prices…", style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedButton(onClick = { vm.checkSales(r) }) { Text("Check sales") }
            }
        }
        else -> {
            val lines = Sales.lines(r, st.servings, cache)
            if (lines.isEmpty()) {
                Text("No big savings on this one this week.", style = MaterialTheme.typography.bodyMedium)
            } else {
                lines.forEach { l ->
                    Text(
                        "• ${l.term.replaceFirstChar { it.uppercase() }}: ${Sales.money(l.sale.promo)}, " +
                            "was ${Sales.money(l.sale.regular)}" +
                            (if (l.sale.size.isNotBlank()) " (${l.sale.size})" else "") +
                            " · save ~${Sales.money(l.savings)}" + (if (l.packages > 1) " for ${l.packages}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Save about ${Sales.money(lines.sumOf { it.savings })} on this meal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A small rounded label like "🍲 Crock-Pot" or "💲 Budget". */
@Composable
internal fun Badge(text: String, small: Boolean) {
    Text(
        text,
        // Small (tile) badges are compact so the price line always fits.
        style = if (small) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
        else MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = if (small) 5.dp else 10.dp, vertical = if (small) 0.dp else 4.dp),
    )
}

/** On a planned recipe: which day, the side dish, and Remove from week. */
@Composable
private fun WeekControls(vm: MealsViewModel, st: UserState, r: Recipe, side: Side?) {
    val day = Logic.dayPlan(st.week, st.days, st.weekStart).dayOf(r.id)
    Heading(if (day != null) "Planned for ${Logic.DAY_LONG[day]}" else "In your week (extra meal)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Side: ", style = MaterialTheme.typography.bodyMedium)
        // Opens the full Pick a side window: search, Recommended, More sides, online and store sides.
        OutlinedButton(onClick = { vm.changeSide(r.id) }) { Text(side?.label ?: "None") }
    }
}

/** One recipe on its own page: buttons at the top, then ingredients and steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeScreen(
    vm: MealsViewModel, st: UserState, id: Int, toggleFav: (Recipe) -> Unit,
    timerCtl: (Recipe) -> TimerCtl, onCook: () -> Unit, onRemoved: () -> Unit = {}, onBack: () -> Unit,
) {
    val r = vm.recipes(st)[id]
    if (r == null) { onBack(); return }
    val side = st.sides[id]?.let { vm.sideMap[it] }
    val inWeek = id in st.week
    Scaffold(
        modifier = Modifier.swipeBack(onBack),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    val ctx = LocalContext.current
                    IconButton(onClick = { shareRecipe(ctx, r, st.servings, if (inWeek) side else null) }) {
                        Icon(Icons.Filled.Share, "Share recipe")
                    }
                    FavButton(r, st, toggleFav)
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            // The recipe's color block with its name in big type.
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Look.color(r.id)),
            ) {
                ColorBlob(r.id, 150.dp, 40.dp, (-30).dp, Modifier.align(Alignment.TopEnd))
                OnColor {
                    Text(
                        r.name, style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 36.sp),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 18.dp),
                    )
                }
            }
            val badges = Logic.badges(r, vm.isBudget(st, r))
            // Badges, then your rating (👍 Loved it / 😐 It was fine / 👎 Not for us), or "Rate" if you haven't yet. Tap it to change.
            var rating by remember { mutableStateOf(false) }
            val mine = st.ratings[r.id]
            Row(
                Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                badges.forEach { Badge(it, small = false) }
                val rated = mine == 1 || mine == 2 || mine == -1
                Text(
                    when (mine) { 1 -> "👍 Loved it"; 2 -> "😐 It was fine"; -1 -> "👎 Not for us"; else -> "👍😐👎 Rate" },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rated) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .then(
                            if (rated) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
                        )
                        .clickable { rating = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (rating) {
                fun pick(v: Int) { rating = false; vm.rate(r.id, v) }
                AlertDialog(
                    onDismissRequest = { rating = false },
                    title = { Text("How was ${r.name}?") },
                    text = {
                        // Three choices, then Clear / Cancel under them.
                        Column(Modifier.fillMaxWidth()) {
                            RatingChoices(mine, ::pick)
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                if (mine == 1 || mine == 2 || mine == -1) {
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        TextButton(onClick = { rating = false; vm.rate(r.id, 0) }) { Text("Clear") }
                                    }
                                }
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { rating = false }) { Text("Cancel") }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            }
            Text(
                "About ${Logic.fmtMinutes(Logic.cookMinutes(r))} total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            LaunchedEffect(r.id) { vm.fetchReviewsIfMissing(r) }
            Nutrition.perServing(r)?.let { cal ->
                val sideCal = if (inWeek) Nutrition.side(side) else null
                Text(
                    "≈ %,d cal per serving".format(cal) +
                        (if (sideCal != null && side != null) " · + %,d with ${side.label.substringAfter(" ")}".format(sideCal) else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            r.rating?.let { Row(Modifier.padding(top = 4.dp)) { RatingLine(it, r.ratingCount, r.countLabel, 16.dp) } }
            // Two equal buttons, labels on one line so they line up.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pad = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                Button(onClick = onCook, contentPadding = pad, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("Start cooking", textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.Bold)
                }
                // Adds the meal; once it's planned, you hold for 3 seconds to remove it.
                if (inWeek) {
                    HoldToRemoveButton(
                        Modifier.weight(1f).fillMaxHeight(),
                        onHint = { vm.message.value = "Hold to remove from week" },
                    ) {
                        vm.removeFromWeek(id)
                        vm.message.value = "Removed ${r.name} from your week"
                        onRemoved()
                    }
                } else {
                    OutlinedButton(
                        onClick = { vm.requestAdd(id) },
                        contentPadding = pad, modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text("Add to week", textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            if (inWeek) WeekControls(vm, st, r, side)
            SaleSection(vm, st, r)
            RecipeDetails(r, st.servings, if (inWeek) side else null, cost = vm.mealCost(st, r), budgetMax = st.budgetMax, expired = vm.expiredTerms(st))
            ReviewsSection(r.id, r.rating, r.ratingCount, r.countLabel, r.reviews)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The recipe as plain text (amounts for your Cooking for number), for texting or email. No prices or Kitchen info. */
fun recipeShareText(r: Recipe, serv: Int, side: Side?): String = buildString {
    appendLine("${r.emoji} ${r.name}")
    val info = listOfNotNull(
        "Serves ${Logic.servFor(r, serv)}",
        Logic.fmtShort(Logic.cookMinutes(r)),
        Nutrition.perServing(r)?.let { "%,d cal".format(it) },
    )
    appendLine(info.joinToString(" · "))
    appendLine()
    appendLine("Ingredients")
    r.ingredients.forEach { appendLine("• " + Logic.ingText(it, Logic.servFor(r, serv), r.base)) }
    if (side != null && side.ingredients.isNotEmpty()) {
        appendLine()
        appendLine("Side: ${side.label}")
        side.ingredients.forEach { appendLine("• " + Logic.ingText(it, serv, 4)) }
    }
    appendLine()
    appendLine("Steps")
    r.steps.forEachIndexed { n, t -> appendLine("${n + 1}. $t") }
    if (side != null && side.steps.isNotEmpty()) {
        appendLine()
        appendLine("Side: ${side.label}")
        side.steps.forEachIndexed { n, t -> appendLine("${n + 1}. $t") }
    }
    appendLine()
    append("Shared from Weekly Meals")
}

/** Opens the phone's share menu (texts, email, notes…) with the recipe as text. */
fun shareRecipe(ctx: android.content.Context, r: Recipe, serv: Int, side: Side?) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, r.name)
        putExtra(android.content.Intent.EXTRA_TEXT, recipeShareText(r, serv, side))
    }
    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Share ${r.name}")) }
}

/** Slim one-line "Search" box used on Browse and Favorites. */
@Composable
internal fun CompactSearch(
    query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "Search",
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Search,
    onDone: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(21.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                query, onChange,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done
                    else androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onDone?.invoke() },
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, "Clear",
                Modifier.size(18.dp).clickable { onChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

/** Recipes matching a search (name or ingredient) and every selected filter chip. */
private val BUDGET_GREEN = androidx.compose.ui.graphics.Color(0xFF5DCAA5)

private fun matchesFilters(
    r: Recipe, q: String, filters: List<String>,
    calories: (Recipe) -> Int? = { null }, budget: (Recipe) -> Boolean = { false },
    leftovers: (Recipe) -> Boolean = { false },
): Boolean =
    (q.isEmpty() || r.name.lowercase().contains(q) || r.ingredients.any { it.name.lowercase().contains(q) }) &&
        filters.all { f ->
            when (f) {
                "quick" -> Logic.cookMinutes(r) <= 30
                // 600 or less per serving, side included; unknown calories don't count.
                "lowcal" -> calories(r)?.let { it <= LOW_CAL } ?: false
                "budget" -> budget(r)
                "leftovers" -> leftovers(r)
                else -> f in r.tags
            }
        }

private const val LOW_CAL = 600

/** The green bold down arrow on the "600 cal" filter chip. */
@Composable
private fun LowCalArrow() {
    val green = androidx.compose.ui.graphics.Color(0xFF5DCAA5)
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val u = size.width / 24f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(9 * u, 3 * u); lineTo(15 * u, 3 * u); lineTo(15 * u, 12 * u); lineTo(19.5f * u, 12 * u)
            lineTo(12 * u, 21 * u); lineTo(4.5f * u, 12 * u); lineTo(9 * u, 12 * u); close()
        }
        drawPath(p, green)
        drawPath(
            p, green,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.5f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/** A filter chip; the low-calorie one shows the green arrow. */
@Composable
private fun FilterChipFor(key: String, label: String, on: Boolean, onClick: () -> Unit) {
    val icon: (@Composable () -> Unit)? = if (key == "lowcal") { { LowCalArrow() } } else null
    FilterChip(selected = on, onClick = onClick, leadingIcon = icon, label = { Text(label) })
}

// ---- Favorites ---------------------------------------------------------------------

private val FAV_FILTERS = listOf(
    "crockpot" to "🍲 Crock-Pot", "instantpot" to "⚡ Instapot", "airfryer" to "🌬️ Air Fryer",
    "sheetpan" to "♨️ Sheet-Pan", "grill" to "🔥 Grill", "onepan" to "🍳 One-Pan",
    "budget" to "💲 Budget", "quick" to "⏱ Quick", "lowcal" to "$LOW_CAL cal",
    "leftovers" to "♻️ Uses leftovers",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesScreen(
    vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit,
    gridState: LazyGridState, search: SearchState, onHold: (Recipe) -> Unit, onOpen: (Int) -> Unit, onAdd: () -> Unit,
) {
    var query by search.query
    var filters by search.filters
    var showFilters by remember { mutableStateOf(false) }
    val cache = vm.salesCache(st)
    val favs = vm.recipes(st).values.filter { Logic.isFav(it, st) }.sortedBy { it.name }
    val q = query.trim().lowercase()
    val shown = favs.filter { r -> matchesFilters(r, q, filters, { vm.mealCalories(st, it) }, { vm.isBudget(st, it) }, { vm.usesLeftovers(st, it) }) }
    // Same layout and spacing as Browse so the title, search bar and tiles line up.
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Favorites", style = MaterialTheme.typography.titleLarge)
            if (favs.isNotEmpty()) {
                Text(
                    if (shown.size == favs.size) "${favs.size}" else "${shown.size} of ${favs.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            // Filters (icon only, to leave room), then Add recipe.
            FiltersButton(filters.size, showLabel = false) { showFilters = true }
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add recipe")
            }
        }
        CompactSearch(query, { query = it }, Modifier.padding(top = 6.dp, bottom = 4.dp))
        if (showFilters) FiltersDialog(filters, onChange = { filters = it }, onDismiss = { showFilters = false })
        if (favs.isEmpty()) {
            Text(
                "No favorites yet. Tap the ♥ on any recipe, or add your own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        // Browse's tile rows have 3dp above them, so the first tile starts at the same height.
        contentPadding = PaddingValues(top = 3.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f),
    ) {
        items(shown, key = { it.id }) { r ->
            RecipeTile(
                r, st, toggleFav, onAdd = { vm.requestAdd(r.id) }, onOpen = { onOpen(r.id) },
                Modifier.fillMaxWidth().height(104.dp),
                saleSave = Sales.savings(r, st.servings, cache),
                onHold = { onHold(r) },
                calories = vm.mealCalories(st, r),
                cost = vm.mealCost(st, r),
                filters = filters,
                leftover = vm.usesLeftovers(st, r),
                leftoverSave = vm.leftoverSavings(st, r),
            )
        }
    }
    }
}

// ---- History -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: MealsViewModel, st: UserState, onBack: () -> Unit) {
    val all = vm.recipes(st)
    val thisYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val weekFmt: (Long) -> String = { ms ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val pattern = if (cal.get(java.util.Calendar.YEAR) == thisYear) "MMM d" else "MMM d, yyyy"
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
    var confirmDelete by remember { mutableStateOf<PastWeek?>(null) }
    // Every week starts closed, one line each until tapped.
    var openWeek by remember { mutableStateOf<Long?>(null) }
    Scaffold(
        // Shown under the app's top bar, so the status bar is already accounted for.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        modifier = Modifier.swipeBack(onBack),
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            // Same margins and title spot as the tabs.
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (st.history.isEmpty()) {
                item {
                    Text(
                        "No past weeks yet. When you're done with a week, tap \"Finish week\" at the bottom of My Week to save it here.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(st.history, key = { it.date }) { w ->
                var open by remember { mutableStateOf<Int?>(null) }
                val expanded = openWeek == w.date
                Card(
                    colors = if (expanded) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) else cardColors,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // One short line per week; tap it to show that week's meals (one week open at a time).
                    Row(
                        Modifier.fillMaxWidth().clickable { openWeek = if (expanded) null else w.date }
                            .heightIn(min = 52.dp).padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Week of ${weekFmt(w.date)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${w.recipes.size + w.storeMeals.size} meals", style = MaterialTheme.typography.bodySmall)
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            if (expanded) "Close" else "Open",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (expanded) Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                        HorizontalDivider(Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        w.recipes.forEach { id ->
                            val r = all[id]
                            if (r == null) {
                                Text(
                                    st.deletedNames[id]?.let { "$it (deleted)" } ?: "(recipe no longer available)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                )
                            } else {
                                val side = w.sides[id]?.let { vm.sideMap[it] }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable { open = if (open == id) null else id }
                                        .heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                        side?.let { Text("+ ${it.label}", style = MaterialTheme.typography.bodySmall) }
                                    }
                                    when (st.ratings[id]) { 1 -> Text("👍"); 2 -> Text("😐") }
                                }
                                if (open == id) {
                                    Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                                        RecipeDetails(r, w.servings, side)
                                        TextButton(onClick = { vm.addToWeek(id) }, enabled = id !in st.week) {
                                            Text(if (id in st.week) "In this week" else "Add to this week")
                                        }
                                    }
                                }
                            }
                        }
                        w.storeMeals.forEach {
                            Column(Modifier.heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center) {
                                Text(it, style = MaterialTheme.typography.bodyLarge)
                                Text("Store meal", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cooking for ${w.servings}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                vm.repeatWeek(w)
                                vm.message.value = "Added to My Week"
                            }) { Text("Repeat this week") }
                            IconButton(onClick = { confirmDelete = w }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this week?") },
            text = { Text("This removes it from History. Your recipes and favorites are not affected.") },
            confirmButton = { TextButton(onClick = { vm.deletePastWeek(w); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

const val KING_SOOPERS_CART = "https://www.kingsoopers.com/cart"

/** Opens the King Soopers cart: in their app if it handles the link, otherwise the browser. */
fun openKingSoopersCart(ctx: android.content.Context) {
    try {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(KING_SOOPERS_CART))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: android.content.ActivityNotFoundException) {
    }
}

/** A search box's text and filter chips, kept while you open a recipe and come back. */
class SearchState {
    val query = androidx.compose.runtime.mutableStateOf("")
    val filters = androidx.compose.runtime.mutableStateOf(listOf<String>())
}

/** A top-bar icon; while its page is open it sits in a highlight pill like the bottom tabs. */
@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(48.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val HOLD_MS = 3000

/**
 * "Hold to remove": fills with red while you hold it. Let go early and it fades back;
 * hold the full 3 seconds and it buzzes and calls [onRemove]. A quick tap shows a hint.
 */
@Composable
private fun HoldToRemoveButton(modifier: Modifier, onHint: () -> Unit, onRemove: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val red = MaterialTheme.colorScheme.error
    val holding = progress.value > 0.02f
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, if (holding) red else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .background(red.copy(alpha = 0.85f * progress.value))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val fill = scope.launch {
                        progress.animateTo(1f, tween(((1f - progress.value) * HOLD_MS).toInt(), easing = LinearEasing))
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onRemove()
                        progress.snapTo(0f)
                    }
                    val released = tryAwaitRelease()
                    if (fill.isActive) {
                        fill.cancel()
                        if (released && progress.value < 0.15f) onHint()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                })
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (holding) "Keep holding…" else "Hold to remove",
            textAlign = TextAlign.Center, maxLines = 2,
            style = MaterialTheme.typography.labelLarge,
            color = if (progress.value > 0.5f) MaterialTheme.colorScheme.onError else if (holding) red else MaterialTheme.colorScheme.primary,
        )
    }
}

/** "Filters" button that opens the filter chips; shows how many are on. */
@Composable
private fun FiltersButton(count: Int, showLabel: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp),
    ) {
        Icon(TuneIcon, null, Modifier.size(18.dp))
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text("Filters")
        }
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(if (showLabel) "· $count" else "$count")
        }
    }
}

/** Pop-up with every filter chip; tap as many as you like. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FiltersDialog(
    filters: List<String>, onChange: (List<String>) -> Unit, onDismiss: () -> Unit,
    sale: Boolean? = null, onSale: (Boolean) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filters") },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (sale != null) {
                    FilterChip(selected = sale, onClick = { onSale(!sale) }, label = { Text("🏷️ Sale") })
                }
                FAV_FILTERS.forEach { (key, label) ->
                    val on = key in filters
                    FilterChipFor(key, label, on) { onChange(if (on) filters - key else filters + key) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (filters.isNotEmpty() || sale == true) {
                TextButton(onClick = { onChange(emptyList()); if (sale == true) onSale(false) }) { Text("Clear all") }
            }
        },
    )
}

/** Sliders icon for the Filters button. */
private val TuneIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Tune", 24.dp, 24.dp, 24f, 24f).apply {
        fun r(x: Float, y: Float, w: Float, h: Float) = addPath(
            androidx.compose.ui.graphics.vector.PathData {
                moveTo(x, y); horizontalLineToRelative(w); verticalLineToRelative(h); horizontalLineToRelative(-w); close()
            },
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
        r(3f, 5f, 18f, 2f); r(14f, 3f, 2.5f, 6f)
        r(3f, 11f, 18f, 2f); r(7f, 9f, 2.5f, 6f)
        r(3f, 17f, 18f, 2f); r(12f, 15f, 2.5f, 6f)
    }.build()
}

/** The rating choices in a row: 👍 Loved it, 😐 It was fine, 👎 Not for us. The ones you didn't pick are dimmed. */
@Composable
fun RatingChoices(mine: Int?, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        listOf(Triple(1, "👍", "Loved it"), Triple(2, "😐", "It was fine"), Triple(-1, "👎", "Not for us")).forEach { (v, emoji, label) ->
            val dim = (mine == 1 || mine == 2 || mine == -1) && mine != v
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TextButton(onClick = { onPick(v) }, modifier = Modifier.alpha(if (dim) 0.4f else 1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emoji, fontSize = 40.sp)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
