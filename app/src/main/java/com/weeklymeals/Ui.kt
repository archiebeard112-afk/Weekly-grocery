package com.weeklymeals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.ArrowBack

/** How a pop-up button looks: lime (the thing to do), dark (the other choice), or red (removes something). */
enum class ActionKind { Primary, Secondary, Danger }

data class DialogAction(
    val label: String,
    val kind: ActionKind = ActionKind.Secondary,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Every pop-up in the app. It slides up from the bottom (easy to reach with a thumb) with a big
 * title and full-width buttons. Pop-ups with a text box use [centered] so the keyboard has room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    eyebrow: String? = null,
    centered: Boolean = false,
    dismissable: Boolean = true,
    /** Something small at the right end of the title (an Edit button, a heart). */
    titleEnd: (@Composable () -> Unit)? = null,
    actions: List<DialogAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val body: @Composable ColumnScope.() -> Unit = {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1,
            )
            Box(Modifier.height(Space.XS))
        }
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp),
                modifier = Modifier.weight(1f),
            )
            titleEnd?.invoke()
        }
        Column(
            Modifier.weight(1f, fill = false).padding(top = Space.M).verticalScroll(rememberScrollState()),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) { content() }
        }
        if (actions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = Space.L), verticalArrangement = Arrangement.spacedBy(Space.S)) {
                actions.forEach { ActionButton(it) }
            }
        }
    }
    if (centered) {
        Dialog(
            onDismissRequest = { if (dismissable) onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissable, dismissOnClickOutside = dismissable),
        ) {
            Surface(
                shape = Radius.Hero, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.L),
            ) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = Space.L), content = body)
            }
        }
    } else {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { dismissable || it != androidx.compose.material3.SheetValue.Hidden },
        )
        ModalBottomSheet(
            onDismissRequest = { if (dismissable) onDismiss() },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = {
                Box(
                    Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(Radius.Pill)
                        .background(MaterialTheme.colorScheme.outline),
                )
            },
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = Space.S, bottom = Space.L), content = body)
        }
    }
}

/** A full-width pop-up button. */
@Composable
fun ActionButton(a: DialogAction, modifier: Modifier = Modifier) {
    val (bg, fg) = when (a.kind) {
        ActionKind.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ActionKind.Secondary -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
        ActionKind.Danger -> Look.DangerBg to Look.DangerText
    }
    Button(
        onClick = a.onClick, enabled = a.enabled, shape = Radius.Pill,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = modifier.fillMaxWidth().height(if (a.kind == ActionKind.Primary) 52.dp else 48.dp),
    ) {
        Text(a.label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A short message at the bottom of the screen: dark card, lime action (like Undo). */
@Composable
fun AppSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = Radius.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Space.Page),
    )
}

/** A short grey note under a pop-up's main text. */
@Composable
fun DialogNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Space.S),
    )
}

/** The Kitchen / History / Settings buttons, shown at the right of each main page's header. */
val LocalTopIcons = androidx.compose.runtime.staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * The header every page uses: a lime eyebrow (WEEK, BROWSE...), a big headline, and an optional
 * grey line under it. Main tabs show the Kitchen / History / Settings buttons on the right;
 * pages opened from them show a back arrow instead.
 */
@Composable
fun PageHeader(
    eyebrow: String, title: String, modifier: Modifier = Modifier, sub: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (onBack != null) {
            androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.padding(end = Space.XS).padding(top = 6.dp).size(40.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowBack, "Back")
            }
        }
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(
                eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1,
            )
            Text(
                title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
        if (onBack == null) LocalTopIcons.current?.invoke()
    }
}
