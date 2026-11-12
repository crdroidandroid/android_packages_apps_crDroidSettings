/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.ui.fonts

import android.graphics.Typeface
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.settings.R
import com.crdroid.settings.fragments.misc.SectionLabel
import com.crdroid.settings.fragments.misc.SpoofAction
import com.crdroid.settings.fragments.misc.SpoofActionRow
import com.crdroid.settings.fragments.misc.SpoofConfirmDialog
import com.crdroid.settings.fragments.misc.SpoofDialog
import com.crdroid.settings.fragments.misc.SpoofFooter
import com.crdroid.settings.fragments.misc.SpoofPrimaryAction
import com.crdroid.settings.fragments.misc.SpoofScreen
import com.crdroid.settings.fragments.misc.SpoofShapes
import com.crdroid.settings.fragments.misc.SpoofStatusChip
import com.crdroid.settings.fragments.misc.SpoofTone
import com.crdroid.settings.fragments.misc.SpoofingAnimatedVisibility
import com.crdroid.settings.fragments.misc.SpoofingLoadingBox

private fun Typeface?.toFamily(): FontFamily = if (this == null) FontFamily.Default else FontFamily(this)

/**
 * Compose would otherwise hand emoji to EmojiCompat, which can draw them with the GMS
 * downloaded font instead of the one being previewed.
 */
private fun emojiStyle(base: TextStyle, family: FontFamily): TextStyle = base.copy(
    fontFamily = family,
    platformStyle = PlatformTextStyle(emojiSupportMatch = EmojiSupportMatch.None),
)

@Composable
internal fun EmojiPickerContent(vm: EmojiPickerViewModel, onPickFont: () -> Unit) {
    val context = LocalContext.current
    val busy = vm.busy

    LaunchedEffect(EmojiOps.generation) { if (EmojiOps.generation > 0) vm.refresh() }
    LaunchedEffect(EmojiOps.restartRequested) { vm.consumeRestartRequest(context) }

    EmojiDialogs(vm)

    SpoofScreen {
        CurrentEmojiCard(
            label = vm.customName ?: stringResource(R.string.font_default),
            family = vm.customTypeface.toFamily(),
            isCustom = vm.customName != null,
        )

        val pending = vm.pending
        SpoofingAnimatedVisibility(visible = pending != null && busy == null) {
            if (pending != null) {
                RestartBanner(
                    title = stringResource(R.string.font_pending_systemui_title),
                    body = stringResource(R.string.emoji_pending_systemui_body, pending.fontLabel),
                    action = stringResource(R.string.font_restart_systemui),
                    onRestart = { vm.restartNow(context) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        SpoofingAnimatedVisibility(visible = busy != null) {
            BusyBanner(
                text = when (busy) {
                    is EmojiBusy.Installing -> stringResource(R.string.font_installing, busy.name)
                    EmojiBusy.Removing -> stringResource(R.string.emoji_removing)
                    EmojiBusy.UpdatingApps -> stringResource(R.string.emoji_updating_apps)
                    else -> stringResource(R.string.font_reading)
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        val customName = vm.customName
        if (vm.loading) {
            SpoofingLoadingBox(Modifier.padding(vertical = 48.dp))
        } else if (customName == null) {
            SpoofPrimaryAction(
                text = stringResource(R.string.emoji_add),
                icon = Icons.Rounded.AddReaction,
                onClick = onPickFont,
                enabled = busy == null,
                loading = busy is EmojiBusy.Reading,
            )
        } else {
            SpoofActionRow(
                listOf(
                    SpoofAction(
                        text = stringResource(R.string.font_replace),
                        icon = Icons.Rounded.SwapHoriz,
                        enabled = busy == null,
                        onClick = onPickFont,
                    ),
                    SpoofAction(
                        text = stringResource(R.string.font_remove),
                        icon = Icons.Rounded.DeleteOutline,
                        enabled = busy == null,
                        onClick = { vm.showDialog(EmojiDialog.Remove) },
                    ),
                ),
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.emoji_apps_section))
            AppsCard(
                enabled = busy == null,
                loading = busy is EmojiBusy.UpdatingApps,
                onClick = { vm.showDialog(EmojiDialog.UpdateApps) },
            )
        }

        SpoofFooter(text = stringResource(R.string.emoji_picker_footer))
    }
}

@Composable
private fun CurrentEmojiCard(label: String, family: FontFamily, isCustom: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.emoji_current),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isCustom) {
                    SpoofStatusChip(stringResource(R.string.font_custom_chip), SpoofTone.Positive)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            EmojiSample(family)
        }
    }
}

@Composable
private fun EmojiSample(family: FontFamily) {
    Text(
        text = stringResource(R.string.emoji_sample_faces),
        style = emojiStyle(MaterialTheme.typography.headlineLarge, family),
    )
    Text(
        text = stringResource(R.string.emoji_sample_things),
        style = emojiStyle(MaterialTheme.typography.headlineLarge, family),
    )
}

@Composable
private fun AppsCard(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.emoji_apps_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SpoofPrimaryAction(
                text = stringResource(R.string.emoji_apps_reset),
                icon = Icons.Rounded.Apps,
                onClick = onClick,
                enabled = enabled,
                loading = loading,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun EmojiDialogs(vm: EmojiPickerViewModel) {
    when (val dialog = vm.dialog) {
        null -> Unit
        is EmojiDialog.Install -> {
            val preview = dialog.preview
            val context = LocalContext.current
            val size = remember(preview) { Formatter.formatShortFileSize(context, preview.sizeBytes) }
            EmojiActionDialog(
                title = preview.postScriptName,
                details = listOfNotNull(preview.displayName, size).joinToString("  ·  "),
                family = remember(preview) { FontFamily(preview.typeface) },
                message = stringResource(R.string.emoji_install_message),
                primaryText = stringResource(R.string.emoji_install_restart_now),
                secondaryText = stringResource(R.string.emoji_install_restart_later),
                onPrimary = { vm.install(preview, restartNow = true) },
                onSecondary = { vm.install(preview, restartNow = false) },
                onDismiss = vm::dismissDialog,
            )
        }
        EmojiDialog.Remove -> EmojiActionDialog(
            title = stringResource(R.string.emoji_remove_title),
            details = null,
            family = FontFamily.Default,
            message = stringResource(R.string.emoji_remove_message),
            primaryText = stringResource(R.string.emoji_remove_restart_now),
            secondaryText = stringResource(R.string.emoji_remove_restart_later),
            onPrimary = { vm.remove(restartNow = true) },
            onSecondary = { vm.remove(restartNow = false) },
            onDismiss = vm::dismissDialog,
            showSample = false,
        )
        EmojiDialog.UpdateApps -> SpoofConfirmDialog(
            title = stringResource(R.string.emoji_apps_reset),
            message = stringResource(R.string.emoji_apps_confirm),
            confirmText = stringResource(R.string.emoji_apps_confirm_button),
            onConfirm = vm::updateApps,
            onDismiss = vm::dismissDialog,
        )
        is EmojiDialog.AppsUpdated -> SpoofDialog(
            onDismissRequest = vm::dismissDialog,
            title = stringResource(R.string.emoji_apps_reset),
            text = {
                Text(
                    pluralStringResource(R.plurals.emoji_apps_done, dialog.files, dialog.files),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = vm::dismissDialog) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is EmojiDialog.Error -> SpoofDialog(
            onDismissRequest = vm::dismissDialog,
            title = stringResource(R.string.emoji_error_title),
            text = { Text(dialog.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = vm::dismissDialog) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
private fun EmojiActionDialog(
    title: String,
    details: String?,
    family: FontFamily,
    message: String,
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
    showSample: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!details.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showSample) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(Modifier.padding(16.dp)) { EmojiSample(family) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SpoofShapes.pill,
                ) { Text(primaryText) }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SpoofShapes.pill,
                ) { Text(secondaryText) }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}
