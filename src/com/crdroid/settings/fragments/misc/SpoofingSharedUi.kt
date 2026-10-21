/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.misc

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import com.android.settings.R
import java.util.Locale

// ---------------------------------------------------------------------------
// Shared building blocks for the spoofing screens (TrickyStore, Play Integrity
// Fix, Game spoofing and the app pickers).
//
// Layout follows the Material 3 Expressive settings look: 16dp screen inset,
// grouped rows with large outer / small inner corners and 2dp gaps,
// surfaceBright row containers and pill buttons. Only stable Material 3 APIs
// plus LoadingIndicator / motionScheme (already used by crDroid Settings) are
// used, so no SettingsLib SPA expressive tokens are required.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Shared data model
// ---------------------------------------------------------------------------

data class AppListEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    /** Per-screen selection state: targeted (TrickyStore), blocked (SensorBlock), etc. */
    val isSelected: Boolean = false,
)

internal const val APP_ICON_SIZE_LIST = 32   // compact picker list rows
internal const val APP_ICON_SIZE_CARD = 40   // app cards (SensorBlock, configured lists)
internal const val APP_ICON_SIZE_LARGE = 42  // IdleManager AppConfigCard
internal const val APP_ICON_SIZE_RECORD = 36 // IdleManager EnforcementRecordCard

// ---------------------------------------------------------------------------
// Package helpers
// ---------------------------------------------------------------------------

fun stopPackage(activityManager: ActivityManager?, pkg: String) {
    try { activityManager?.forceStopPackage(pkg) } catch (_: Exception) {}
}

fun killPackages(activityManager: ActivityManager?, packages: Set<String>) {
    packages.forEach { stopPackage(activityManager, it) }
}

/**
 * Packages the GMS / Play Store attestation path runs in. Force-stopped after a keybox or
 * fingerprint change so no process keeps a cached verdict or attestation key.
 */
internal val GMS_FAMILY_PACKAGES = setOf(
    "com.android.vending",
    "com.google.android.gms.unstable",
    "com.google.android.gms",
    "com.google.android.gms.persistent",
    "com.google.android.gsf",
    "com.google.android.rkpdapp",
    "com.google.android.contactkeys",
    "com.google.android.safetycore",
)

internal const val VENDING_PACKAGE = "com.android.vending"

// Overlay packages rarely change while the screen is open, so the set is cached for the
// process lifetime instead of scanning every installed package on each list reload.
@Volatile
private var cachedOverlayPackages: Set<String>? = null

private fun getOverlayPackages(pm: PackageManager): Set<String> {
    cachedOverlayPackages?.let { return it }
    return pm.getInstalledPackages(0)
        .filter { it.overlayTarget != null }
        .map { it.packageName }
        .toSet()
        .also { cachedOverlayPackages = it }
}

/**
 * Installed apps without overlays. System apps are only listed when [showSystem] is set,
 * unless they are already in [targeted]. [extraFilter] runs before the system check, so a
 * screen that wants to keep targeted packages must allow them itself.
 */
fun filterInstalledApps(
    pm: PackageManager,
    showSystem: Boolean,
    targeted: Set<String>,
    hidden: Set<String> = emptySet(),
    extraFilter: ((ApplicationInfo) -> Boolean)? = null,
): List<ApplicationInfo> {
    val overlayPackages = getOverlayPackages(pm)
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { app ->
            if (app.packageName in overlayPackages) return@filter false
            if (app.packageName in hidden) return@filter false
            if (extraFilter != null && !extraFilter(app)) return@filter false
            val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            !(isSystem && !showSystem && app.packageName !in targeted)
        }
}

/** "Targeted apps first, then alphabetical". */
fun targetedFirstComparator(
    pm: PackageManager,
    targeted: Set<String>,
): Comparator<ApplicationInfo> = compareBy(
    { app -> app.packageName !in targeted },
    { app -> pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault()) },
)

// ---------------------------------------------------------------------------
// Tokens
// ---------------------------------------------------------------------------

internal object SpoofDimens {
    val ScreenInset = 16.dp
    val RowGap = 2.dp
    val RowMinHeight = 64.dp
    val RowPaddingH = 16.dp
    val RowPaddingV = 14.dp
    val ButtonHeight = 56.dp
}

/** Corner roles: 24dp outer corners, 4dp inner corners between grouped rows. */
internal object SpoofShapes {
    private val outer = 24.dp
    private val inner = 4.dp

    val single: Shape = RoundedCornerShape(outer)
    val first: Shape = RoundedCornerShape(
        topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner,
    )
    val middle: Shape = RoundedCornerShape(inner)
    val last: Shape = RoundedCornerShape(
        topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer,
    )
    val field: Shape = RoundedCornerShape(16.dp)
    val pill: Shape = CircleShape

    fun forIndex(index: Int, count: Int): Shape = when {
        count <= 1 -> single
        index == 0 -> first
        index == count - 1 -> last
        else -> middle
    }
}

/** Semantic colour roles for [SpoofStatusChip]. Mapped onto theme roles, never hard-coded. */
enum class SpoofTone { Positive, Warning, Negative, Neutral }

private val LocalSpoofRowShape = compositionLocalOf<Shape> { SpoofShapes.single }

// ---------------------------------------------------------------------------
// Page scaffolding
// ---------------------------------------------------------------------------

/**
 * The ComposeView every spoofing fragment returns from onCreateView. Nested scrolling is
 * enabled so the scroll events [toolbarNestedScroll] forwards reach the activity's
 * CoordinatorLayout, which is what collapses the CollapsingToolbarLayout.
 */
fun spoofComposeView(context: Context): ComposeView = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    ViewCompat.setNestedScrollingEnabled(this, true)
}

/**
 * Forwards this scroll container's nested scroll to the hosting View hierarchy. Without it
 * the collapsing toolbar never collapses, and because AppBarLayout.ScrollingViewBehavior
 * sizes the content for the collapsed toolbar, the last part of the page stays below the
 * screen edge. Must come before verticalScroll / on the LazyColumn modifier.
 */
@Composable
fun Modifier.toolbarNestedScroll(): Modifier =
    this.nestedScroll(rememberNestedScrollInteropConnection())

/** Bottom navigation bar inset, so the last row can scroll clear of the gesture bar. */
@Composable
fun spoofBottomInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Scrollable page body with the standard screen inset. */
@Composable
fun SpoofScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .toolbarNestedScroll()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SpoofDimens.ScreenInset)
            .padding(top = 8.dp, bottom = 32.dp + spoofBottomInset()),
        content = content,
    )
}

/** Builder passed to [SpoofGroup]; every [item] becomes one segment of the group. */
class SpoofGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

/**
 * A titled group of rows drawn as one segmented card. When [collapsible] is set the title
 * toggles the rows and the state survives configuration changes.
 */
@Composable
fun SpoofGroup(
    title: String?,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: SpoofGroupScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val items = SpoofGroupScope().apply(content).items
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        if (!title.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (collapsible) Modifier.clickable { expanded = !expanded }
                        else Modifier
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    text = title,
                    modifier = Modifier.weight(1f),
                    includeBottomPadding = false,
                )
                if (collapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(visible = !collapsible || expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpoofDimens.RowGap),
            ) {
                items.forEachIndexed { index, row ->
                    CompositionLocalProvider(
                        LocalSpoofRowShape provides SpoofShapes.forIndex(index, items.size),
                    ) { row() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/**
 * Base row: surfaceBright segment with optional leading icon, overline, title, summary and a
 * trailing slot. Takes its corner shape from the enclosing [SpoofGroup].
 */
@Composable
fun SpoofRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    overline: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    selectableText: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = LocalSpoofRowShape.current
    val color = MaterialTheme.colorScheme.surfaceBright
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpoofDimens.RowMinHeight)
                .alpha(if (enabled) 1f else 0.38f)
                .padding(
                    horizontal = SpoofDimens.RowPaddingH,
                    vertical = SpoofDimens.RowPaddingV,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!overline.isNullOrEmpty()) {
                    Text(
                        text = overline,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val titleText: @Composable () -> Unit = {
                    Text(
                        text = title,
                        style = if (overline.isNullOrEmpty()) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (selectableText) SelectionContainer { titleText() } else titleText()
                if (!summary.isNullOrEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.widthIn(min = 40.dp),
                    contentAlignment = Alignment.Center,
                ) { trailing() }
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
        ) { body() }
    }
}

/** Navigation row: [SpoofRow] with a trailing chevron, or a status [chip] when supplied. */
@Composable
fun SpoofNavRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    chip: String? = null,
    chipTone: SpoofTone = SpoofTone.Neutral,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val trailing: (@Composable () -> Unit)? =
        if (chip != null) {
            { SpoofStatusChip(chip, chipTone) }
        } else if (onClick != null) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        }
    SpoofRow(
        title = title,
        modifier = modifier,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        trailing = trailing,
    )
}

/** Switch row: tapping anywhere on the row toggles the switch. */
@Composable
fun SpoofSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SpoofRow(
        title = title,
        modifier = modifier,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            CheckCloseSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

/**
 * Key/value row used for configuration values. The key is a small primary label; the value
 * is selectable when the row is read-only so a fingerprint can be copied.
 */
@Composable
fun SpoofValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    SpoofRow(
        title = value,
        overline = label,
        summary = note,
        modifier = modifier,
        selectableText = onClick == null,
        onClick = onClick,
    )
}

/** Small pill for status values ("Valid", "12d old"). */
@Composable
fun SpoofStatusChip(
    text: String,
    tone: SpoofTone = SpoofTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        SpoofTone.Positive -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SpoofTone.Warning -> scheme.secondaryContainer to scheme.onSecondaryContainer
        SpoofTone.Negative -> scheme.errorContainer to scheme.onErrorContainer
        SpoofTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.widthIn(max = 160.dp),
        shape = SpoofShapes.pill,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/** Full-width primary action with an optional loading state. */
@Composable
fun SpoofPrimaryAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SpoofDimens.ButtonHeight),
        enabled = enabled && !loading,
        shape = SpoofShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

data class SpoofAction(
    val text: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    /** Null for a plain action; non-null renders a toggle that shows a check when true. */
    val selected: Boolean? = null,
    val onClick: () -> Unit,
)

/** Equal-width row of tonal pill buttons ("Import", "Delete", "Reset"). */
@Composable
fun SpoofActionRow(
    actions: List<SpoofAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action ->
            val checked = action.selected == true
            FilledTonalButton(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = SpoofShapes.pill,
                colors = if (checked) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = action.text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Switch, text field, footer
// ---------------------------------------------------------------------------

/** Material 3 switch with a check / close thumb icon, as in stock Settings. */
@Composable
fun CheckCloseSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

/** Filled, indicator-less text field used by every dialog on these screens. */
@Composable
fun SpoofTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = SpoofShapes.field,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

/** Explanatory footer below the last group of a screen. */
@Composable
fun SpoofFooter(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 16.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/** Shared dialog shell with the screen's container colour and corners. */
@Composable
fun SpoofDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title?.let {
            { Text(text = it, style = MaterialTheme.typography.headlineSmall) }
        },
        text = text,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        properties = properties,
    )
}

/** Confirmation dialog. The dialog dismisses itself before [onConfirm] runs. */
@Composable
fun SpoofConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = stringResource(android.R.string.cancel),
    destructive: Boolean = false,
) {
    SpoofDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(); onConfirm() },
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}

/**
 * Single-choice list dialog. Picking a row calls [onPick] and dismisses the dialog;
 * [summaries] adds an optional second line per option.
 */
@Composable
fun SpoofChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    summaries: List<String?> = emptyList(),
) {
    SpoofDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(
                                selected = index == selected,
                                role = Role.RadioButton,
                                onClick = { onDismiss(); onPick(index) },
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                            summaries.getOrNull(index)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------
// App icon and badge
// ---------------------------------------------------------------------------

@Composable
fun AppIconOrPlaceholder(
    packageName: String,
    icon: Drawable?,
    sizeDp: Int = APP_ICON_SIZE_CARD,
) {
    val density = LocalDensity.current
    val physicalPx = with(density) { sizeDp.dp.roundToPx() }
    val bitmap: ImageBitmap? = remember(packageName, physicalPx) {
        runCatching { icon?.toBitmap(physicalPx, physicalPx)?.asImageBitmap() }.getOrNull()
    }
    val corner = (sizeDp * 0.25f).dp
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(corner)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@Composable
fun SystemAppBadge(
    isCritical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier,
        containerColor = if (isCritical) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.tertiary,
        contentColor = if (isCritical) MaterialTheme.colorScheme.onError
        else MaterialTheme.colorScheme.onTertiary,
    ) {
        Text(stringResource(R.string.system_badge))
    }
}

// ---------------------------------------------------------------------------
// Header card, visibility, loading, empty state, section label.
// These keep their original names and signatures so other screens using them
// (SensorBlock, IdleManager, ...) keep compiling.
// ---------------------------------------------------------------------------

/**
 * Header card: icon circle, title, subtitle and an optional master switch. Screens without
 * a switch pass `checked = null`.
 */
@Composable
fun SpoofingHeaderCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    hapticOnToggle: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked != false) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (checked != null && onCheckedChange != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        if (hapticOnToggle) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onCheckedChange(newValue)
                    },
                    thumbContent = {
                        Crossfade(
                            targetState = checked,
                            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            label = "header_switch_thumb",
                        ) { on ->
                            Icon(
                                if (on) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Header icon tint matching [SpoofingHeaderCard]'s circle colour. */
@Composable
fun spoofHeaderIconTint(checked: Boolean?): Color =
    if (checked != false) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun SpoofingAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            expandVertically(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                expandFrom = Alignment.Top,
            ),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            shrinkVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                shrinkTowards = Alignment.Top,
            ),
        content = { content() },
    )
}

@Composable
fun SpoofingLoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
fun SpoofingEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    includeBottomPadding: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = if (includeBottomPadding)
            modifier.padding(start = 4.dp, bottom = 8.dp)
        else
            modifier.padding(start = 4.dp),
    )
}
