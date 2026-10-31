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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime

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
            val mine = st.timers.filterKeys { it.startsWith("r$id:") || it.startsWith("r${id}side:") }
            val soonest = mine.minByOrNull { it.value }?.key
            CookOverlay.Info(id, r.name, mine.values.toList(), soonest?.let { st.timerStarts[it] })
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
    remember { TileSize.load(ctx) }
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
    // A message with Undo (e.g. after removing a meal from the week).
    val undo by vm.undo.collectAsState()
    LaunchedEffect(undo) {
        undo?.let { (text, action) ->
            val r = snack.showSnackbar(text, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) action()
            vm.undo.value = null
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
    } else if (overlay != Overlay.None) {
        // Kitchen, History and Settings are full pages with a back arrow (no tabs at the bottom).
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            when (overlay) {
                Overlay.Pantry -> PantryScreen(vm, st) { overlay = Overlay.None }
                Overlay.History -> HistoryScreen(vm, st) { overlay = Overlay.None }
                else -> SettingsScreen(vm, st) { overlay = Overlay.None }
            }
        }
    } else {
        Scaffold(
            // Ignore the keyboard here so it slides over the tabs instead of shrinking them.
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                        selected = tab == 1 && overlay == Overlay.None,
                        // Coming from another tab, Browse opens on Meals.
                        onClick = { if (tab != 1) vm.setBrowseScope(BrowseScope.APP); tab = 1; overlay = Overlay.None },
                        icon = { Icon(Icons.Filled.Search, null) }, label = { TabLabel("Browse") }, colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == 2 && overlay == Overlay.None,
                        // Coming from another tab, Favorites opens on All.
                        onClick = { if (tab != 2) vm.setFavKind("all"); tab = 2; overlay = Overlay.None },
                        icon = { Icon(Icons.Filled.Favorite, null) }, label = { TabLabel("Favorites") }, colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == 3 && overlay == Overlay.None, onClick = { tab = 3; overlay = Overlay.None },
                        icon = { Icon(Icons.Filled.ShoppingCart, null) }, label = { TabLabel("Shop") }, colors = navColors,
                    )
                }
            },
        ) { pad ->
            // Kitchen, History and Settings: at the right end of each page's header.
            val topIcons: @Composable () -> Unit = {
                Row(Modifier.padding(start = Space.XS)) {
                    TopIcon(PantryIcon, "Kitchen") { overlay = Overlay.Pantry }
                    TopIcon(Icons.Filled.List, "History") { overlay = Overlay.History }
                    TopIcon(Icons.Filled.Settings, "Settings") { overlay = Overlay.Settings }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalTopIcons provides topIcons) {
            Column(
                Modifier.padding(pad).statusBarsPadding().fillMaxSize().keepFullHeight(),
            ) {
                // A quick fade between tabs.
                androidx.compose.animation.Crossfade(targetState = tab, animationSpec = tween(180), label = "tab") { shown ->
                    when (shown) {
                    0 -> WeekScreen(
                        vm, st, onOpen = { viewing = it }, onBrowse = { vm.setBrowseScope(BrowseScope.APP); tab = 1 }, flashId = flashId,
                        onLeftovers = { vm.setBrowseScope(BrowseScope.APP); pendingFilter = "leftovers"; tab = 1 },
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
    }
    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp)) { AppSnackbar(it) }
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

    // "Which day?" when adding a dessert: each day shows that night's dinner.
    val pickDay by vm.dessertPicker.collectAsState()
    pickDay?.let { id ->
        val all = vm.recipes(st)
        val r = all[id]
        if (r == null) { vm.dessertPicker.value = null } else {
            val plan = Logic.dayPlan(st)
            DayPickerDialog(
                st, "${r.emoji} ${r.name}",
                onPick = { d -> vm.addDessert(id, d); vm.dessertPicker.value = null },
                onDismiss = { vm.dessertPicker.value = null },
                detail = { d -> plan.slots[d]?.let { all[it]?.name } },
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
            AppDialog(
                onDismiss = { vm.autoUpdate.value = null },
                dismissable = !busy,
                eyebrow = "Update available",
                title = "${rel.name} is ready",
                actions = listOf(
                    DialogAction("Update", ActionKind.Primary, enabled = !busy) { vm.downloadUpdate(rel) },
                    DialogAction("Later", enabled = !busy) { vm.autoUpdate.value = null },
                    DialogAction("Skip this version", enabled = !busy) { vm.skipVersion(rel) },
                ),
            ) {
                    Column {
                        Text("Install it now, or later from Settings.")
                        (up as? UpdateState.Downloading)?.let { d ->
                            LinearProgressIndicator(
                                progress = { d.percent / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            Text("Downloading… ${d.percent}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        (up as? UpdateState.Error)?.let { e ->
                            Text(e.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
            }
        }
    }

    // The app crashed last time: show what happened so it can be sent in and fixed.
    var crash by remember { mutableStateOf(CrashLog.pending(ctx)) }
    crash?.let { text ->
        val clip = androidx.compose.ui.platform.LocalClipboardManager.current
        AppDialog(
            onDismiss = { CrashLog.clear(ctx); crash = null },
            title = "The app closed unexpectedly",
            actions = listOf(
                DialogAction("Copy details", ActionKind.Primary) {
                    clip.setText(androidx.compose.ui.text.AnnotatedString(text))
                    vm.message.value = "Crash details copied"
                    CrashLog.clear(ctx); crash = null
                },
                DialogAction("Close") { CrashLog.clear(ctx); crash = null },
            ),
        ) {
            Text("Copy the details and send them over so it can be fixed.")
            Text(
                text.lines().take(8).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (askOverlay) {
        AppDialog(
            onDismiss = { askOverlay = false; vm.setOverlayAsked() },
            title = "Keep the bubble outside the app?",
            actions = listOf(
                DialogAction("Turn on", ActionKind.Primary) {
                    askOverlay = false; vm.setOverlayAsked()
                    runCatching { ctx.startActivity(CookOverlay.permissionIntent(ctx)) }
                },
                DialogAction("Not now") { askOverlay = false; vm.setOverlayAsked() },
            ),
        ) {
            Text(
                "The cooking bubble can float over your home screen and other apps, so you can jump back " +
                    "to your steps. Turn on \"Allow display over other apps\" for Thyme Table on the next screen.",
            )
        }
    }

    if (askAddMode) {
        AppDialog(
            onDismiss = { askAddMode = false },
            title = "Add a recipe",
            actions = listOf(
                DialogAction("From a link", ActionKind.Primary) {
                    askAddMode = false; addLinkMode = true; addFromLink = null; overlay = Overlay.AddRecipe
                },
                DialogAction("Type it in") {
                    askAddMode = false; addLinkMode = false; addFromLink = null; overlay = Overlay.AddRecipe
                },
            ),
        ) {
            Text("Import it from a recipe website, or type it in yourself.")
        }
    }

    confirmDelete?.let { r ->
        val mine = r.imported
        AppDialog(
            onDismiss = { confirmDelete = null },
            title = if (mine) "Delete ${r.name}?" else "Hide ${r.name}?",
            actions = listOf(
                DialogAction(if (mine) "Delete" else "Hide", ActionKind.Danger) { vm.deleteRecipe(r); confirmDelete = null },
                DialogAction("Cancel") { confirmDelete = null },
            ),
        ) {
            Text(
                (if (mine) "This removes the recipe for good."
                else "It won't show in Browse, search or Favorites. You can bring it back in Settings → Hidden recipes.") +
                    if (r.id in st.week) " It will also come off this week and the shopping list." else "",
            )
        }
    }

    confirmRemove?.let { r ->
        AppDialog(
            onDismiss = { confirmRemove = null },
            title = "Remove from favorites?",
            actions = listOf(
                DialogAction("Remove", ActionKind.Danger) { vm.removeFav(r.id); confirmRemove = null },
                DialogAction("Keep it") { confirmRemove = null },
            ),
        ) {
            Text("${r.name} will come off your favorites. You can heart it again any time.")
        }
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
private fun Steps(steps: List<String>, ctl: TimerCtl? = null, leftNames: List<String> = emptyList()) {
    steps.forEachIndexed { n, s ->
        // The step number in a small lime circle, the text beside it.
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${n + 1}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Look.Display),
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            // Leftover ingredients get the leftover icon in a lime chip.
            StepText(
                listOf(Cook.Piece(s, false)), leftNames, MaterialTheme.colorScheme.primary, MaterialTheme.typography.bodyLarge,
                Modifier.weight(1f).padding(top = 2.dp),
            )
        }
        if (ctl != null) {
            val times = Logic.stepTimes(s)
            if (times.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 38.dp, bottom = 4.dp),
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
        ) { TimerGlyph(); Spacer(Modifier.width(6.dp)); Text(Logic.fmtMinutes(t.minutes) + if (t.perSide) " per side" else "") }
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
        ) { TimerGlyph(); Spacer(Modifier.width(6.dp)); Text("%d:%02d  ·  Stop".format(left / 60, left % 60)) }
    } else {
        Button(
            onClick = { ctl.stop(key) },
            contentPadding = small, modifier = Modifier.height(34.dp),
        ) { TimerGlyph(); Spacer(Modifier.width(6.dp)); Text("Done! Tap to stop") }
    }
}

/** A small line-drawn timer (a clock face with a button on top), in the button's text color. */
@Composable
internal fun TimerGlyph(modifier: Modifier = Modifier.size(16.dp)) {
    val c = androidx.compose.material3.LocalContentColor.current
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(c, radius = 8f * u, center = androidx.compose.ui.geometry.Offset(12f * u, 13.5f * u), style = stroke)
        drawLine(c, androidx.compose.ui.geometry.Offset(12f * u, 13.5f * u), androidx.compose.ui.geometry.Offset(12f * u, 9f * u), 2f * u, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c, androidx.compose.ui.geometry.Offset(9.5f * u, 2.5f * u), androidx.compose.ui.geometry.Offset(14.5f * u, 2.5f * u), 2f * u, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
internal fun Heading(text: String) {
    // A grey all-caps section label, like the ones on Week and Settings.
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun RecipeDetails(
    r: Recipe, serv: Int, side: Side?, ctl: TimerCtl? = null,
    cost: MealsViewModel.MealCost? = null, budgetMax: Double = 0.0,
    sideSize: Int = Logic.SIDE_NORMAL, onSideSize: ((Int) -> Unit)? = null,
    expired: Map<String, String> = emptyMap(),
    shared: List<Pair<String, String>> = emptyList(),
    leftNotes: Map<String, String> = emptyMap(),
    leftNames: List<String> = emptyList(),
) {
    // Each shopping item's price shows once, on its first ingredient line.
    val priced = mutableSetOf<String>()
    // "left over from Mon · Chili" (or "rest goes to Thu · Tacos") under an ingredient's first line.
    val noted = mutableSetOf<String>()
    @Composable
    fun line(i: Ingredient, serv: Int, base: Int) {
        val term = Logic.searchTerm(i.name)
        val price = cost?.lines?.get(term)
        val first = price != null && priced.add(term)
        val zone = price?.zone
        Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(Logic.ingText(i, serv, base), style = MaterialTheme.typography.bodyLarge)
                leftNotes[term]?.takeIf { noted.add(term) }?.let { note ->
                    Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        LeftoverIcon(Modifier.padding(end = 4.dp).size(13.dp))
                        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                // In the pantry but past its date.
                expired[term]?.let {
                    val amber = Look.Warn
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
                        if (first && price != null && (price.note.startsWith("♻️") || term in leftNotes)) {
                            Icon(Icons.Filled.Refresh, "Shared this week", Modifier.padding(start = 4.dp).size(16.dp), tint = Look.Info)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                price == null -> ""
                                // Same item as a line above: its price is up there.
                                !first -> "↑"
                                price.have -> "$0.00"
                                else -> Sales.money(price.cost)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
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
    // Ingredients sit in a dark card, prices lined up on the right.
    val card = Modifier.fillMaxWidth().clip(Radius.Card).background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(horizontal = 14.dp, vertical = 6.dp)
    Heading("Ingredients · for $mainServ")
    Column(card) { r.ingredients.forEach { line(it, mainServ, r.base) } }
    if (side != null && side.ingredients.isNotEmpty()) {
        Heading("Side · ${plainLabel(side.label)}" + if (sideSize != Logic.SIDE_NORMAL) " · for ${sideServesText(serv, sideSize)}" else "")
        if (onSideSize != null) SideSizeChoice(sideSize, onSideSize, Modifier.padding(bottom = 6.dp))
        Column(card) { side.ingredients.forEach { line(it, Logic.sideServ(serv, sideSize), Logic.SIDE_BASE) } }
    }
    if (cost != null) {
        Row(Modifier.padding(top = 12.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text(
                Sales.money(cost.total), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.ExtraBold),
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
                Text(Sales.money(cost.saved), style = MaterialTheme.typography.bodySmall, color = BUDGET_GREEN, modifier = Modifier.padding(start = 12.dp, end = 14.dp))
            }
        }
        Text(
            "Prices are for whole packages, as you'd buy them. Shared ones are split between this week's meals.",
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
    Steps(r.steps, ctl, leftNames)
    if (side != null) {
        Heading("Side: ${side.label}")
        Spacer(Modifier.padding(2.dp))
        Steps(side.steps, ctl?.copy(prefix = ctl.prefix + "side", title = side.label.substringAfter(" ")), leftNames)
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

    Column(Modifier.fillMaxSize().padding(horizontal = Space.Page, vertical = Space.S)) {
        PageHeader(
            "Browse",
            when {
                st.browseScope == BrowseScope.APP && searching -> "Search"
                st.browseScope == BrowseScope.APP && saleOnly -> "On sale"
                else -> BrowseScope.label(st.browseScope)
            },
        )
        // Where to search, one at a time (menu inside the search bar): this app's recipes, online recipes, or King Soopers products.
        // Filters sit right of the search bar (sides have no cooking methods, prices or sales, so no Filters there).
        val scopeKey = st.browseScope
        Row(Modifier.fillMaxWidth().padding(top = Space.S, bottom = Space.XS), verticalAlignment = Alignment.CenterVertically) {
            CompactSearch(
                query, { query = it }, Modifier.weight(1f),
                placeholder = BrowseScope.hint(scopeKey),
                trailing = { ScopeMenu(scopeKey) { vm.setBrowseScope(it) } },
            )
            if (st.browseScope != BrowseScope.SIDE) {
                Spacer(Modifier.width(Space.S))
                FiltersButton(filters.size + if (saleOnly) 1 else 0, showLabel = false) { showFilters = true }
            }
        }
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

        if (scopeKey == BrowseScope.DESSERT) {
            DessertResults(vm, st, toggleFav, query, filters, resultsState, onHold, onOpen, Modifier.weight(1f))
            return@Column
        }
        if (scopeKey == BrowseScope.SIDE) {
            SideResults(vm, st, query, resultsState, Modifier.weight(1f))
            return@Column
        }

        if (searching) {
            // All matches, scrolling.
            val q = query.trim().lowercase()
            val matches = all.values.filter { r ->
                r.id !in st.removed && !Logic.isDessert(r) &&
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
                        Modifier.fillMaxWidth().height(TileSize.height),
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
        SwipeSets(
            shown, canBack = vm.canGoBackBrowse(), onNext = { vm.refreshBrowse() }, onBack = { vm.previousBrowse() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { r, mod ->
            RecipeTile(
                r, st, toggleFav, onAdd = { vm.requestAdd(r.id) }, onOpen = { onOpen(r.id) }, mod,
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

/**
 * Up to 10 tiles in 5 rows that fill the space (no scrolling; the tiles fit their text to it). Swipe left for the next set,
 * right to go back ([canBack]); the tiles follow your finger and slide off and back in.
 */
@Composable
private fun <T> SwipeSets(
    shown: List<T>, canBack: Boolean, onNext: () -> Unit, onBack: () -> Unit, modifier: Modifier,
    tile: @Composable (T, Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }
    // Read inside the gesture, which is set up once.
    val back by androidx.compose.runtime.rememberUpdatedState(canBack)
    val next by androidx.compose.runtime.rememberUpdatedState(onNext)
    val prev by androidx.compose.runtime.rememberUpdatedState(onBack)
    // Slide the grid off to one side, swap the set, slide the new one in.
    fun slide(forward: Boolean) {
        if (!forward && !back) return
        scope.launch {
            offset.animateTo(if (forward) -widthPx else widthPx, tween(180))
            if (forward) next() else prev()
            offset.snapTo(if (forward) widthPx else -widthPx)
            offset.animateTo(0f, tween(220))
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tileCtx = LocalContext.current
    Column(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = offset.value
                        when {
                            v < -widthPx * 0.25f -> slide(true)
                            v > widthPx * 0.25f && back -> slide(false)
                            else -> scope.launch { offset.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                ) { change, dx ->
                    change.consume()
                    // Resist when there's no previous set to go back to.
                    val step = if (dx > 0 && offset.value >= 0f && !back) dx * 0.25f else dx
                    scope.launch { offset.snapTo(offset.value + step) }
                }
            }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (kotlin.math.abs(offset.value) / widthPx).coerceIn(0f, 0.5f)
            },
    ) {
        // The keyboard squeezes these tiles; don't copy that size to the other pages.
        val imeOpen = WindowInsets.ime.getBottom(density) > 0
        shown.take(10).chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pair.forEach { item ->
                    tile(
                        item,
                        Modifier.weight(1f).fillMaxHeight()
                            .onSizeChanged { if (!imeOpen) TileSize.save(tileCtx, with(density) { it.height.toDp() }) },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        repeat(5 - (shown.take(10).size + 1) / 2) { Spacer(Modifier.weight(1f)) }
        // So it's clear there are more: arrows (tap them, or swipe) around a short hint.
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { slide(false) }, enabled = canBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowLeft, "Previous ideas")
            }
            Text(
                "Swipe for more ideas", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { slide(true) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowRight, "More ideas")
            }
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
    // Each recipe is a block of its own color; a dark "IN WEEK · WED" chip means it's in your week.
    val day = if (inWeek) Logic.dayPlan(st).dayOf(r.id) else null
    Card(
        colors = CardDefaults.cardColors(containerColor = Look.color(r.id), contentColor = Look.Ink),
        modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onHold),
    ) {
      FitTileText {
      Box(Modifier.fillMaxSize()) {
      OnColor {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 4.dp, end = 2.dp)) {
            // At most 2 tags: in your week, on sale, then cooking method, then Budget. A tag you're
            // filtering by always gets a spot, so you can see why the meal matched.
            val all = (if (saleSave >= st.saleMin) listOf("sale" to "Sale " + Sales.money(saleSave).substringBefore(".")) else emptyList()) +
                (if (leftover) listOf("leftovers" to "Leftovers") else emptyList()) +
                Logic.badgeKeys(r, cost?.let { it.total <= st.budgetMax } ?: false)
            val week = if (inWeek) listOf("week" to ("In week" + (day?.let { " · " + Logic.DAY_SHORT[it] } ?: ""))) else emptyList()
            val keep = (week + all.filter { it.first in filters } + all).distinct().take(2).toSet()
            val badges = (week + all).filter { it in keep }
            // Every card: the name (one or two lines) with its tags right under it, then calories and
            // price, time and buttons in the same spots at the bottom.
            Column(Modifier.weight(1f).fillMaxWidth().padding(end = 8.dp)) {
                    Text(
                        r.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 18.sp),
                        // Up to two lines; the tags sit right under it and move down when it wraps.
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 1.dp),
                    )
                    Row(
                        Modifier.heightIn(min = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        badges.forEach { (key, label) -> Badge(label, small = true, dark = key == "week") }
                    }
                    // Calories, then what you'd pay for this meal (bold at or under your budget line),
                    // then the Kitchen icon when things you have cover part of it. Always in the same
                    // spot, just above the time.
                    Spacer(Modifier.weight(1f))
                    TileInfo(calories, cost, st.budgetMax)
            }
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Logic.fmtShort(Logic.cookMinutes(r)),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Compact buttons (the touch area is still the full 48dp).
                FavButton(r, st, toggleFav, Modifier.size(28.dp))
                IconButton(
                    onClick = onAdd, enabled = !inWeek,
                    colors = if (inWeek) androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        disabledContainerColor = Look.Ink, disabledContentColor = Look.Lime,
                    ) else androidx.compose.material3.IconButtonDefaults.iconButtonColors(),
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        if (inWeek) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = if (inWeek) "In your week" else "Add to week",
                        tint = if (inWeek) Look.Lime else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
      }
      }
      }
    }
}

/**
 * Browse always shows 10 tiles that fill the screen, so a tile's height is set by the phone. If the
 * phone's text size is too big for a tile's lines (tags, two-line name, calories, time and
 * buttons), the text inside is made just small enough to fit instead of being cut off.
 */
@Composable
private fun FitTileText(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = androidx.compose.ui.platform.LocalDensity.current
        // Fixed parts: 4dp top, 1dp gap, 16dp tag row, 28dp bottom row, 2dp spare.
        // Text parts: name 2 x 18sp, calories line 15sp.
        val fit = if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) d.fontScale else (maxHeight.value - 51f) / 51f
        val scale = minOf(d.fontScale, fit).coerceAtLeast(0.75f)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, scale),
        ) { content() }
    }
}

/** A tile's calories · price line, with the Kitchen icon when things you have cover part of it. Never shrinks. */
@Composable
private fun TileInfo(calories: Int?, cost: MealsViewModel.MealCost?, budgetMax: Double, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                calories?.let {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("%,d cal".format(it)) }
                    if (cost != null) append(" · ")
                }
                cost?.let { c ->
                    val cheap = c.total <= budgetMax
                    withStyle(SpanStyle(color = if (cheap) Look.Ink else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (cheap) FontWeight.Bold else FontWeight.Normal)) {
                        append(Sales.money(c.total))
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (cost != null && cost.saved >= 0.5) {
            Icon(PantryIcon, "Uses things you have", Modifier.padding(start = 4.dp).size(13.dp), tint = Look.Ink)
        }
    }
}

/** How desserts are made, as chips over the Desserts list ("" = all). */
private val DESSERT_METHODS = listOf("" to "All", "oven" to "Oven", "stovetop" to "Stovetop", "nobake" to "No bake")

/** Browse with "Desserts" picked: every dessert (built-in and yours), A to Z, with a chip row for how it's made. */
@Composable
private fun DessertResults(
    vm: MealsViewModel, st: UserState, toggleFav: (Recipe) -> Unit, query: String, filters: List<String>,
    gridState: LazyGridState, onHold: (Recipe) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier,
) {
    var method by rememberSaveable { mutableStateOf("") }
    val cache = vm.salesCache(st)
    val q = query.trim().lowercase()
    val matches = vm.recipes(st).values.filter { r ->
        Logic.isDessert(r) && r.id !in st.removed && (method.isEmpty() || method in r.tags) &&
            matchesFilters(r, q, filters, { vm.mealCalories(st, it) }, { vm.isBudget(st, it) }, { vm.usesLeftovers(st, it) })
    }
    val shown = matches.sortedBy { it.name }
    val browsing = q.isEmpty() && filters.isEmpty()
    // Not searching: sets of 10 in a shuffled order, swipe for the next 10 (the chip you picked stays).
    val deck = remember(method, matches.size) { matches.map { it.id }.shuffled() }
    var page by rememberSaveable(method) { mutableIntStateOf(0) }
    val tile: @Composable (Recipe, Modifier) -> Unit = { r, mod ->
        RecipeTile(
            r, st, toggleFav, onAdd = { vm.requestAdd(r.id) }, onOpen = { onOpen(r.id) }, mod,
            saleSave = Sales.savings(r, st.servings, cache),
            onHold = { onHold(r) },
            calories = vm.mealCalories(st, r),
            cost = vm.mealCost(st, r),
            filters = filters + listOfNotNull(method.ifEmpty { null }),
            leftover = vm.usesLeftovers(st, r),
            leftoverSave = vm.leftoverSavings(st, r),
        )
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DESSERT_METHODS.forEach { (key, label) ->
                FilterChip(selected = method == key, onClick = { method = key }, label = { Text(label) })
            }
        }
        if (browsing && deck.isNotEmpty()) {
            val pages = (deck.size + 9) / 10
            val byId = matches.associateBy { it.id }
            SwipeSets(
                deck.drop((page % pages) * 10).take(10).mapNotNull { byId[it] },
                canBack = page > 0, onNext = { page++ }, onBack = { if (page > 0) page-- },
                modifier = Modifier.fillMaxWidth().weight(1f), tile = tile,
            )
            return@Column
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                    Text(
                        "No desserts match.", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { r -> tile(r, Modifier.fillMaxWidth().height(TileSize.height)) }
        }
    }
}

/**
 * Browse with "Sides" picked: every side you can cook, in sets of 10 to swipe through (a search scrolls).
 * + asks which night's dinner it goes with; tap a side to see it first.
 */
@Composable
private fun SideResults(vm: MealsViewModel, st: UserState, query: String, gridState: LazyGridState, modifier: Modifier) {
    val q = query.trim().lowercase()
    val all = vm.allSides(st)
    var adding by remember { mutableStateOf<Side?>(null) }
    var addSize by remember { mutableIntStateOf(Logic.SIDE_NORMAL) }
    var lookAt by remember { mutableStateOf<Side?>(null) }
    val tile: @Composable (Side, Modifier) -> Unit = { sd, mod ->
        val fav = vm.isFavSide(st, sd.key)
        BrowseSideTile(
            sd, mod, calories = Nutrition.side(sd), cost = vm.sideCost(st, sd), budgetMax = st.budgetMax,
            fav = fav, canFav = vm.isBuiltInSide(sd.key),
            onFav = { if (fav) vm.removeFavSide(sd.key) else vm.addFavSide(sd.key) },
            onOpen = { lookAt = sd }, onAdd = { addSize = Logic.SIDE_NORMAL; adding = sd },
        )
    }
    if (q.isEmpty()) {
        val deck = remember(all.size) { all.shuffled() }
        var page by rememberSaveable { mutableIntStateOf(0) }
        val pages = ((deck.size + 9) / 10).coerceAtLeast(1)
        SwipeSets(
            deck.drop((page % pages) * 10).take(10),
            canBack = page > 0, onNext = { page++ }, onBack = { if (page > 0) page-- },
            modifier = modifier.fillMaxWidth(), tile = tile,
        )
    } else {
        val shown = all.filter { sd -> sd.label.lowercase().contains(q) || sd.ingredients.any { it.name.lowercase().contains(q) } }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = modifier,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "count") {
                Text(
                    "${shown.size} match${if (shown.size == 1) "" else "es"}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            items(shown, key = { it.key }) { sd -> tile(sd, Modifier.fillMaxWidth().height(TileSize.height)) }
        }
    }
    lookAt?.let { sd ->
        SidePreview(
            st, sd, onUse = { size -> lookAt = null; addSize = size; adding = sd }, onBack = { lookAt = null },
            fav = if (vm.isBuiltInSide(sd.key)) vm.isFavSide(st, sd.key) else null,
            onFav = { if (vm.isFavSide(st, sd.key)) vm.removeFavSide(sd.key) else vm.addFavSide(sd.key) },
        )
    }
    adding?.let { sd -> SideToDinnerDialog(vm, st, sd, addSize) { adding = null } }
}

/** "Which day?" for a side: each day shows its dinner, days without one are greyed out; the side goes with that dinner. */
@Composable
private fun SideToDinnerDialog(vm: MealsViewModel, st: UserState, sd: Side, size: Int, onDone: () -> Unit) {
    val recipes = vm.recipes(st)
    val plan = Logic.dayPlan(st)
    DayPickerDialog(
        st, "${sd.label} goes with…",
        onPick = { d ->
            plan.slots[d]?.let { id ->
                vm.chooseSide(id, sd.key, size)
                vm.message.value = "${plainLabel(sd.label)} added to ${Logic.DAY_LONG[d]}'s dinner"
            }
            onDone()
        },
        onDismiss = onDone,
        detail = { d -> plan.slots[d]?.let { recipes[it]?.name } ?: "no dinner yet" },
        enabled = { d -> plan.slots[d] != null },
    )
}

/** A side in Browse: color block, name, calories and price, then ♥ (built-in sides) and + to put it with a dinner. */
@Composable
private fun BrowseSideTile(
    sd: Side, modifier: Modifier, calories: Int?, cost: MealsViewModel.MealCost?, budgetMax: Double,
    fav: Boolean, canFav: Boolean, onFav: () -> Unit, onOpen: () -> Unit, onAdd: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Look.color(sd.key.hashCode()), contentColor = Look.Ink),
        modifier = modifier.clickable(onClick = onOpen),
    ) {
        OnColor {
            Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 3.dp, end = 2.dp, bottom = 1.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp, end = 8.dp)) {
                    Text(
                        plainLabel(sd.label),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 2.dp).height(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge("Side", small = true)
                    }
                    TileInfo(calories, cost, budgetMax, Modifier.padding(top = 2.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    if (canFav) {
                        IconButton(onClick = onFav, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (fav) "Remove from favorites" else "Add to favorites", tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, "Add to a dinner", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * Recipe tile height, taken from Browse's sets of 10 so Favorites and search results match.
 * Remembered on the phone, so Favorites matches from the start, even before Browse is opened.
 * Always follows Browse's latest size (except while the keyboard is open), so the pages match exactly.
 */
private object TileSize {
    var height by mutableStateOf(104.dp)
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("tile_size", android.content.Context.MODE_PRIVATE)
    fun load(ctx: android.content.Context) {
        val saved = prefs(ctx).getFloat("height", 0f)
        if (saved > 0f) height = saved.dp
    }
    fun save(ctx: android.content.Context, h: androidx.compose.ui.unit.Dp) {
        if (kotlin.math.abs(h.value - height.value) < 0.5f && prefs(ctx).contains("height")) return
        height = h
        prefs(ctx).edit().putFloat("height", h.value).apply()
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
internal fun Badge(text: String, small: Boolean, dark: Boolean = false) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        else MaterialTheme.typography.labelLarge,
        color = if (dark) Look.Lime else MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .background(if (dark) Look.Ink else MaterialTheme.colorScheme.secondaryContainer, if (small) RoundedCornerShape(7.dp) else Radius.Pill)
            .padding(horizontal = if (small) 5.dp else 10.dp, vertical = if (small) 0.dp else 4.dp),
    )
}

/** On a planned recipe: which day, the side dish, and Remove from week. */
@Composable
private fun WeekControls(vm: MealsViewModel, st: UserState, r: Recipe, side: Side?) {
    val day = Logic.dayPlan(st).dayOf(r.id)
    if (Logic.isDessert(r)) {
        Heading(if (day != null) "Dessert on ${Logic.DAY_LONG[day]}" else "Dessert this week")
        OutlinedButton(onClick = { vm.dessertPicker.value = r.id }) { Text("Change day") }
        return
    }
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
                    IconButton(onClick = { shareRecipe(ctx, r, st.servings, if (inWeek) side else null, st.sideSize[id]) }) {
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
            // Your rating (Loved it / It was fine / Not for us), changed from its fact tile.
            var rating by remember { mutableStateOf(false) }
            val mine = st.ratings[r.id]
            // The recipe's color block: its name in big type, then four facts (time, calories, cost, your rating).
            Box(
                Modifier.fillMaxWidth().clip(Radius.Hero).background(Look.color(r.id)),
            ) {
                ColorBlob(r.id, 150.dp, 40.dp, (-30).dp, Modifier.align(Alignment.TopEnd))
                OnColor {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 14.dp)) {
                        Text(r.name, style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 36.sp), modifier = Modifier.padding(end = 24.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val cal = Nutrition.perServing(r)
                            val cost = vm.mealCost(st, r)?.total
                            HeroFact(Logic.fmtShort(Logic.cookMinutes(r)), "time", Modifier.weight(1f))
                            HeroFact(cal?.let { "%,d".format(it) } ?: "—", "cal", Modifier.weight(1f))
                            HeroFact(cost?.let { "$" + Math.round(it) } ?: "—", "cost", Modifier.weight(1f))
                            HeroFact(
                                when (mine) { 1 -> "Loved"; 2 -> "Fine"; -1 -> "Not for us"; else -> "Rate" },
                                if (mine == 1 || mine == 2 || mine == -1) "you" else "tap",
                                Modifier.weight(1f), onClick = { rating = true },
                            )
                        }
                    }
                }
            }
            val badges = Logic.badges(r, vm.isBudget(st, r))
            if (badges.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    badges.forEach { Badge(it, small = false) }
                }
            }
            if (rating) {
                fun pick(v: Int) { rating = false; vm.rate(r.id, v) }
                // Three choices, then Clear rating / Cancel under them.
                AppDialog(
                    onDismiss = { rating = false },
                    eyebrow = "Your rating",
                    title = "How was ${r.name}?",
                    actions = listOfNotNull(
                        if (mine == 1 || mine == 2 || mine == -1) DialogAction("Clear rating") { rating = false; vm.rate(r.id, 0) } else null,
                        DialogAction("Cancel") { rating = false },
                    ),
                ) {
                    RatingChoices(mine, ::pick)
                }
            }
            LaunchedEffect(r.id) { vm.fetchReviewsIfMissing(r) }
            // Calories are per serving; the side adds its own.
            if (inWeek && side != null) Nutrition.side(side)?.let { sideCal ->
                Text(
                    "Calories are per serving · + %,d with ${side.label.substringAfter(" ")}".format(sideCal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            r.rating?.let { Row(Modifier.padding(top = 4.dp)) { RatingLine(it, r.ratingCount, r.countLabel, 16.dp) } }
            // Two equal buttons, labels on one line so they line up.
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pad = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                Button(onClick = onCook, contentPadding = pad, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("Start cooking", textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.Bold)
                }
                // Adds the meal; once it's planned, removes it (with Undo on the message).
                if (inWeek) {
                    OutlinedButton(
                        onClick = { vm.removeFromWeekUndoable(id, r.name); onRemoved() },
                        contentPadding = pad, modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text("Remove from week", textAlign = TextAlign.Center, maxLines = 2)
                    }
                } else {
                    OutlinedButton(
                        onClick = { vm.requestAdd(id) },
                        contentPadding = pad, modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text(if (Logic.isDessert(r)) "Add to a day" else "Add to week", textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            // Leftovers shared with other planned meals are noted on each ingredient row.
            val left = if (inWeek) vm.mealLeftovers(st, id) else emptyList()
            val all = vm.recipes(st)
            if (inWeek) WeekControls(vm, st, r, side)
            if (r.machine.isNotEmpty()) MachineCard(r.machine)
            SaleSection(vm, st, r)
            RecipeDetails(
                r, st.servings, if (inWeek) side else null, cost = vm.mealCost(st, r), budgetMax = st.budgetMax, expired = vm.expiredTerms(st),
                sideSize = st.sideSize[id] ?: Logic.SIDE_NORMAL,
                onSideSize = if (inWeek && side != null && !isStoreSide(side)) ({ size: Int -> vm.setSideSize(id, size) }) else null,
                leftNotes = left.associate { l ->
                    l.term to (if (l.usesUp) "left over from " else "rest goes to ") + Shares.mealLabel(st, l.other, all[l.other]?.name ?: "")
                },
                leftNames = leftoverNames(left, r, if (inWeek) side else null),
            )
            ReviewsSection(r.id, r.rating, r.ratingCount, r.countLabel, r.reviews)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One fact on a recipe's color block: a bold value over a small label. */
@Composable
private fun HeroFact(value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Look.Ink.copy(alpha = 0.1f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp),
            maxLines = 2, textAlign = TextAlign.Center,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = Look.Ink.copy(alpha = 0.72f), maxLines = 1)
    }
}

/** Bread maker settings for a recipe made in one: cycle, loaf size, crust, and the order things go in. */
@Composable
private fun MachineCard(settings: List<String>) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            "BREAD MAKER SETTINGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.forEach { s ->
                Column(Modifier.weight(1f)) {
                    Text(s.substringBefore(":").trim(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        s.substringAfter(":").trim(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = Look.Display),
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            if (settings.any { it.substringAfter(":").trim() == "Dough" }) "The machine makes the dough; you shape and bake it in the oven."
            else "Add the wet things first and the yeast or leavening last, then press Start.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The recipe as plain text (amounts for your Cooking for number), for texting or email. No prices or Kitchen info. */
fun recipeShareText(r: Recipe, serv: Int, side: Side?, sideSize: Int? = null): String = buildString {
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
        side.ingredients.forEach { appendLine("• " + Logic.ingText(it, Logic.sideServ(serv, sideSize), Logic.SIDE_BASE)) }
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
    append("Shared from Thyme Table")
}

/** Opens the phone's share menu (texts, email, notes…) with the recipe as text. */
fun shareRecipe(ctx: android.content.Context, r: Recipe, serv: Int, side: Side?, sideSize: Int? = null) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, r.name)
        putExtra(android.content.Intent.EXTRA_TEXT, recipeShareText(r, serv, side, sideSize))
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
            .clip(Radius.Pill)
            .border(1.dp, MaterialTheme.colorScheme.outline, Radius.Pill)
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
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Clear", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Recipes matching a search (name or ingredient) and every selected filter chip. */
private val BUDGET_GREEN = Look.Good

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
    val green = Look.Good
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
    "crockpot" to "Crock-Pot", "instantpot" to "Instant Pot", "airfryer" to "Air fryer",
    "sheetpan" to "Sheet pan", "grill" to "Grill", "onepan" to "One pan",
    "budget" to "Budget", "quick" to "Quick", "lowcal" to "$LOW_CAL cal",
    "leftovers" to "Uses leftovers",
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
    // The dropdown in the search bar: all, meals, sides or desserts.
    val kind = st.favKind
    val favs = vm.recipes(st).values.filter { r ->
        Logic.isFav(r, st) && when (kind) {
            "main" -> !Logic.isDessert(r)
            "dessert" -> Logic.isDessert(r)
            "side" -> false
            else -> true
        }
    }.sortedBy { it.name }
    val favSides = if (kind == "all" || kind == "side") vm.favoriteSides(st) else emptyList()
    val q = query.trim().lowercase()
    val shown = favs.filter { r -> matchesFilters(r, q, filters, { vm.mealCalories(st, it) }, { vm.isBudget(st, it) }, { vm.usesLeftovers(st, it) }) }
    // Sides have no cooking-method tags, so they hide while a filter is on.
    val shownSides = if (filters.isNotEmpty()) emptyList() else favSides.filter { sd ->
        q.isEmpty() || sd.label.lowercase().contains(q) || sd.ingredients.any { it.name.lowercase().contains(q) }
    }
    val total = favs.size + favSides.size
    val count = shown.size + shownSides.size
    var lookAt by remember { mutableStateOf<Side?>(null) }
    var unfav by remember { mutableStateOf<Side?>(null) }
    var adding by remember { mutableStateOf<Side?>(null) }
    var addSize by remember { mutableIntStateOf(Logic.SIDE_NORMAL) }
    // Same layout and spacing as Browse so the title, search bar and tiles line up.
    Column(Modifier.fillMaxSize().padding(horizontal = Space.Page, vertical = Space.S)) {
        PageHeader(
            "Favorites",
            when {
                total == 0 -> "Nothing saved yet"
                count == total -> "$total saved"
                else -> "$count of $total"
            },
        )
        // Search (with the All / Meals / Sides / Desserts menu), then Filters and Add a recipe.
        Row(Modifier.fillMaxWidth().padding(top = Space.S, bottom = Space.XS), verticalAlignment = Alignment.CenterVertically) {
            CompactSearch(
                query, { query = it }, Modifier.weight(1f),
                placeholder = FAV_HINTS[kind] ?: "Search favorites",
                trailing = { KindMenu(kind) { vm.setFavKind(it) } },
            )
            Spacer(Modifier.width(Space.S))
            FiltersButton(filters.size, showLabel = false) { showFilters = true }
            Spacer(Modifier.width(Space.S))
            FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(42.dp)) {
                Icon(Icons.Filled.Add, "Add a recipe", Modifier.size(22.dp))
            }
        }
        if (showFilters) FiltersDialog(filters, onChange = { filters = it }, onDismiss = { showFilters = false })
        if (total == 0) {
            EmptyState(
                Icons.Filled.FavoriteBorder,
                when (kind) { "main" -> "No favorite meals yet"; "side" -> "No favorite sides yet"; "dessert" -> "No favorite desserts yet"; else -> "Keep the meals you love here" },
                when (kind) {
                    "side" -> "Tap the heart on a side when you pick one, and it shows up here."
                    "dessert" -> "Find some in Browse → Desserts and tap the heart."
                    else -> "Tap the heart on any recipe and it shows up here, ready to add to a week."
                },
                action = "Add your own recipe", onAction = onAdd,
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
                Modifier.fillMaxWidth().height(TileSize.height),
                saleSave = Sales.savings(r, st.servings, cache),
                onHold = { onHold(r) },
                calories = vm.mealCalories(st, r),
                cost = vm.mealCost(st, r),
                filters = filters,
                leftover = vm.usesLeftovers(st, r),
                leftoverSave = vm.leftoverSavings(st, r),
            )
        }
        items(shownSides, key = { "side:" + it.key }) { sd ->
            SideTile(
                sd, Modifier.fillMaxWidth().height(TileSize.height), onOpen = { lookAt = sd },
                onAdd = { addSize = Logic.SIDE_NORMAL; adding = sd },
            ) { unfav = sd }
        }
    }
    }
    lookAt?.let { sd -> SidePreview(st, sd, onUse = { size -> lookAt = null; addSize = size; adding = sd }, onBack = { lookAt = null }) }
    adding?.let { sd -> SideToDinnerDialog(vm, st, sd, addSize) { adding = null } }
    unfav?.let { sd ->
        val mine = !vm.isBuiltInSide(sd.key)
        AppDialog(
            onDismiss = { unfav = null },
            title = if (mine) "Delete ${plainLabel(sd.label)}?" else "Remove from favorites?",
            actions = listOf(
                DialogAction(if (mine) "Delete" else "Remove", ActionKind.Danger) { vm.removeFavSide(sd.key); unfav = null },
                DialogAction("Keep it") { unfav = null },
            ),
        ) {
            Text(
                if (mine) "This removes the side you added for good."
                else "${plainLabel(sd.label)} stays in the side list when you pick a side.",
            )
        }
    }
}

private val FAV_KINDS = listOf("all" to "All", "main" to "Meals", "side" to "Sides", "dessert" to "Desserts")
private val FAV_HINTS = mapOf("main" to "Search meals", "side" to "Search sides", "dessert" to "Search desserts")

/** The "what kind" button at the right end of the Favorites search bar: All, Meals, Sides or Desserts. */
@Composable
private fun KindMenu(kind: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { open = true }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                FAV_KINDS.firstOrNull { it.first == kind }?.second ?: "All", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, maxLines = 1,
            )
            Icon(Icons.Filled.ArrowDropDown, "What to show", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FAV_KINDS.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text((if (key == kind) "✓  " else "     ") + label) },
                    onClick = { open = false; onPick(key) },
                )
            }
        }
    }
}

/** A side in Favorites: its own color block, name, calories and a SIDE tag. Tap to see it; ♥ to remove it. */
@Composable
private fun SideTile(sd: Side, modifier: Modifier, onOpen: () -> Unit, onAdd: () -> Unit, onUnfav: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Look.color(sd.key.hashCode()), contentColor = Look.Ink),
        modifier = modifier.clickable(onClick = onOpen),
    ) {
        OnColor {
            Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 3.dp, end = 2.dp, bottom = 1.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp, end = 8.dp)) {
                    Text(
                        plainLabel(sd.label),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Look.Display, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 18.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 2.dp).height(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge("Side", small = true)
                    }
                    Nutrition.side(sd)?.let {
                        Text(
                            "%,d cal".format(it), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onUnfav, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Favorite, "Remove from favorites", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, "Add to a dinner", tint = MaterialTheme.colorScheme.primary)
                    }
                }
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
            contentPadding = PaddingValues(horizontal = Space.Page, vertical = Space.S),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PageHeader(
                    "History",
                    when (st.history.size) { 0 -> "No weeks yet"; 1 -> "1 week"; else -> "${st.history.size} weeks" },
                    onBack = onBack,
                )
            }
            if (st.history.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.DateRange, "No past weeks yet",
                        "When you're done with a week, tap Finish week on the Week tab. It's saved here so you can repeat it later.",
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
                        w.desserts.entries.sortedBy { Logic.dayRank(it.value, st.weekStart) }.forEach { (id, day) ->
                            val name = all[id]?.name ?: st.deletedNames[id]?.let { "$it (deleted)" } ?: "(recipe no longer available)"
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .then(if (all[id] != null) Modifier.clickable { open = if (open == id) null else id } else Modifier)
                                    .heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                Text("Dessert · ${Logic.DAY_SHORT.getOrElse(day) { "" }}", style = MaterialTheme.typography.bodySmall)
                            }
                            all[id]?.takeIf { open == id }?.let { r ->
                                Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                                    RecipeDetails(r, w.servings, null)
                                    TextButton(onClick = { vm.requestAdd(id) }, enabled = id !in st.week) {
                                        Text(if (id in st.week) "In this week" else "Add to a day")
                                    }
                                }
                            }
                        }
                        Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cooking for ${w.servings}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                vm.repeatWeek(w)
                                vm.message.value = "Added to this week"
                            }) { Text("Repeat this week") }
                            IconButton(onClick = { confirmDelete = w }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { w ->
        AppDialog(
            onDismiss = { confirmDelete = null },
            title = "Delete this week?",
            actions = listOf(
                DialogAction("Delete", ActionKind.Danger) { vm.deletePastWeek(w); confirmDelete = null },
                DialogAction("Keep it") { confirmDelete = null },
            ),
        ) {
            Text("This removes it from History. Your recipes and favorites are not affected.")
        }
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

/** A header icon (Kitchen, History, Settings): a 44dp round button. */
@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}


/** "Filters" button that opens the filter chips; shows how many are on. */
@Composable
private fun FiltersButton(count: Int, showLabel: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(42.dp),
    ) {
        Icon(TuneIcon, "Filters", Modifier.size(20.dp))
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
    AppDialog(
        onDismiss = onDismiss,
        title = "Filters",
        actions = listOfNotNull(
            DialogAction("Done", ActionKind.Primary, onClick = onDismiss),
            if (filters.isNotEmpty() || sale == true) DialogAction("Clear all") { onChange(emptyList()); if (sale == true) onSale(false) } else null,
        ),
    ) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.S),
            ) {
                if (sale != null) {
                    FilterChip(selected = sale, onClick = { onSale(!sale) }, label = { Text("On sale") })
                }
                FAV_FILTERS.forEach { (key, label) ->
                    val on = key in filters
                    FilterChipFor(key, label, on) { onChange(if (on) filters - key else filters + key) }
                }
            }
    }
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
