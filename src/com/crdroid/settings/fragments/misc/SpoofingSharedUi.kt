/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.ActivityManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.R
import java.util.Locale

// ---------------------------------------------------------------------------
// Shared data model
// Replaces: SensorBlockAppItem, TensorTargetEntry, PpAppEntry, AppItem (partial)
// The `state` field carries whatever per-screen boolean the screen needs
// (isTargeted, isBlocked, etc). Screens that need no state field leave it null.
// ---------------------------------------------------------------------------

data class AppListEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    /** Per-screen selection state: targeted (PixelProps/Tensor), blocked (SensorBlock), etc. */
    val isSelected: Boolean = false,
)

// ---------------------------------------------------------------------------
// Shared icon sizes — use these everywhere for consistency
// ---------------------------------------------------------------------------

internal const val APP_ICON_SIZE_LIST = 32   // compact picker list rows
internal const val APP_ICON_SIZE_CARD = 40   // app cards (SensorBlock, configured lists)
internal const val APP_ICON_SIZE_LARGE = 42  // IdleManager AppConfigCard
internal const val APP_ICON_SIZE_RECORD = 36 // IdleManager EnforcementRecordCard

// ---------------------------------------------------------------------------
// stopPackage — replaces the identical try/catch in every screen
// ---------------------------------------------------------------------------

fun stopPackage(activityManager: ActivityManager?, pkg: String) {
    try { activityManager?.forceStopPackage(pkg) } catch (_: Exception) {}
}

// ---------------------------------------------------------------------------
// killPackages — replaces killAllTargets / killTargets variants
// ---------------------------------------------------------------------------

fun killPackages(activityManager: ActivityManager?, packages: Set<String>) {
    packages.forEach { stopPackage(activityManager, it) }
}

// ---------------------------------------------------------------------------
// filterInstalledApps — shared overlay + system-app filter used by
// PixelProps, TensorTargets, TrickyStore. Each screen adds its own
// additional filter (hidden apps set, settings package exclusion, etc.)
// via the optional `extraFilter` predicate.
// ---------------------------------------------------------------------------

fun filterInstalledApps(
    pm: PackageManager,
    showSystem: Boolean,
    targeted: Set<String>,
    hidden: Set<String> = emptySet(),
    extraFilter: ((ApplicationInfo) -> Boolean)? = null,
): List<ApplicationInfo> {
    val overlayPackages = pm.getInstalledPackages(0)
        .filter { it.overlayTarget != null }
        .map { it.packageName }
        .toSet()

    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { app ->
            if (app.packageName in overlayPackages) return@filter false
            if (app.packageName in hidden) return@filter false
            if (extraFilter != null && !extraFilter(app)) return@filter false
            val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (isSystem && !showSystem && app.packageName !in targeted) return@filter false
            true
        }
}

// ---------------------------------------------------------------------------
// targetedFirstComparator — "targeted apps first, then alphabetical"
// Used by PixelProps, TensorTargets, TrickyStore
// ---------------------------------------------------------------------------

fun targetedFirstComparator(
    pm: PackageManager,
    targeted: Set<String>,
): Comparator<ApplicationInfo> = compareBy(
    { app -> app.packageName !in targeted },
    { app -> pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault()) },
)

// ---------------------------------------------------------------------------
// AppIconOrPlaceholder
// Replaces the repeated if (iconBitmap != null) Image else Box pattern
// ---------------------------------------------------------------------------

@Composable
fun AppIconOrPlaceholder(
    packageName: String,
    icon: Drawable?,
    sizeDp: Int = APP_ICON_SIZE_CARD,
) {
    val density = LocalDensity.current
    val physicalPx = with(density) { sizeDp.dp.roundToPx() }
    val bitmap: ImageBitmap? = androidx.compose.runtime.remember(packageName, physicalPx) {
        runCatching { icon?.toBitmap(physicalPx, physicalPx)?.asImageBitmap() }.getOrNull()
    }
    val cornerDp = (sizeDp * 0.25f).dp
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(cornerDp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(cornerDp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

// ---------------------------------------------------------------------------
// SystemAppBadge
// Replaces the repeated Badge { Text("SYS") } across every file
// ---------------------------------------------------------------------------

@Composable
fun SystemAppBadge(
    isCritical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier,
        containerColor = if (isCritical)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.tertiary,
        contentColor = if (isCritical)
            MaterialTheme.colorScheme.onError
        else
            MaterialTheme.colorScheme.onTertiary,
    ) {
        Text(stringResource(R.string.system_badge))
    }
}

// ---------------------------------------------------------------------------
// SpoofingHeaderCard
// The big header card with icon circle + title + subtitle.
// Screens that have a master switch pass checked/onCheckedChange;
// TrickyStore (no switch) passes checked = null.
// The `icon` slot receives the icon composable so each screen keeps
// its own icon without branching here.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked != false) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
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
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (checked != null && onCheckedChange != null) {
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
                            if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                            else Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SpoofingAnimatedVisibility
// Shared enter/exit spec used by all six screens below the header card
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

// ---------------------------------------------------------------------------
// SpoofingLoadingBox
// Centered LoadingIndicator with weight(1f) — identical across
// PixelProps, TensorTargets, TrickyStore
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpoofingLoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

// ---------------------------------------------------------------------------
// SpoofingEmptyState
// Shared empty-state card — icon, title, description
// Replaces: EmptyAppsState, EmptyDashboardState (IdleManager),
//           empty blocks in SensorBlock, TrickyStore, UserSelectedAppSpoof
// ---------------------------------------------------------------------------

@Composable
fun SpoofingEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SectionLabel
// The small coloured "CONFIGURED APPS" / "RECENT ACTIVITY" labels
// used in multiple screens
// ---------------------------------------------------------------------------

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    includeBottomPadding: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = if (includeBottomPadding)
            modifier.padding(start = 4.dp, bottom = 8.dp)
        else
            modifier.padding(start = 4.dp),
    )
}
