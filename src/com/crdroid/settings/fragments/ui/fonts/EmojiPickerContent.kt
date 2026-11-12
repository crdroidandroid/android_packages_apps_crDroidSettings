/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.ui.fonts

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import java.io.File

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
            label = vm.currentLabel,
            family = vm.currentTypeface.toFamily(),
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
                    is EmojiBusy.Applying -> downloadText(busy.label)
                        ?: stringResource(R.string.emoji_applying, busy.label)
                    is EmojiBusy.Downloading -> downloadText(busy.label)
                        ?: stringResource(R.string.emoji_downloading, busy.label, 0)
                    is EmojiBusy.Installing -> stringResource(R.string.font_installing, busy.name)
                    EmojiBusy.Removing -> stringResource(R.string.emoji_removing)
                    EmojiBusy.UpdatingApps -> stringResource(R.string.emoji_updating_apps)
                    else -> stringResource(R.string.font_reading)
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        SpoofPrimaryAction(
            text = stringResource(
                if (vm.customName != null) R.string.emoji_replace_custom else R.string.emoji_add,
            ),
            icon = Icons.Rounded.AddReaction,
            onClick = onPickFont,
            enabled = busy == null,
            loading = busy is EmojiBusy.Reading || busy is EmojiBusy.Installing,
        )

        val customName = vm.customName
        if (customName != null) {
            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.emoji_custom_section))
            EmojiCard(
                label = customName,
                family = vm.customTypeface.toFamily(),
                selected = true,
                applying = false,
                enabled = false,
                badge = stringResource(R.string.font_custom_chip),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
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
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.emoji_presets_section))
        if (vm.loading && vm.options.isEmpty()) {
            SpoofingLoadingBox(Modifier.padding(vertical = 48.dp))
        } else {
            if (vm.catalogFailed) {
                CatalogErrorCard(
                    enabled = busy == null,
                    onRetry = { vm.refresh(reloadCatalog = true) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            EmojiGrid(
                options = vm.options,
                selectedKey = vm.selectedKey,
                applyingKey = when (busy) {
                    is EmojiBusy.Applying -> busy.key
                    is EmojiBusy.Downloading -> busy.key
                    else -> null
                },
                enabled = busy == null,
                onClick = vm::onOptionClicked,
            )
        }

        if (vm.selectedKey != EmojiRepository.DEFAULT_KEY) {
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
private fun EmojiGrid(
    options: List<EmojiOption>,
    selectedKey: String,
    applyingKey: String?,
    enabled: Boolean,
    onClick: (EmojiOption) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { option ->
                        EmojiCard(
                            label = option.label,
                            family = option.typeface.toFamily(),
                            preview = if (option.typeface == null) {
                                rememberPreview(option.preview)
                            } else {
                                null
                            },
                            downloadable = option.entry != null && option.typeface == null,
                            selected = option.key == selectedKey,
                            applying = option.key == applyingKey,
                            enabled = enabled,
                            badge = when {
                                option.entry == null -> null
                                option.downloaded -> stringResource(R.string.emoji_downloaded)
                                else -> Formatter.formatShortFileSize(
                                    LocalContext.current, option.sizeBytes,
                                )
                            },
                            onClick = { onClick(option) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun downloadText(label: String): String? =
    EmojiOps.downloadProgress?.let {
        stringResource(R.string.emoji_downloading, label, (it * 100).toInt())
    }

@Composable
private fun rememberPreview(file: File?): ImageBitmap? = remember(file) {
    file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
}

@Composable
private fun CatalogErrorCard(enabled: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.emoji_catalog_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.emoji_catalog_retry))
            }
        }
    }
}

@Composable
private fun EmojiCard(
    label: String,
    family: FontFamily,
    selected: Boolean,
    preview: ImageBitmap? = null,
    /** A catalog font not downloaded yet: only its [preview] image, if any, can show it. */
    downloadable: Boolean = false,
    applying: Boolean,
    enabled: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected || applying) scheme.primaryContainer else scheme.surfaceBright,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "emoji_card_container",
    )
    val content = if (selected || applying) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 128.dp)
            .semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(40.dp),
                        )
                    } else if (!downloadable) {
                        // Default, a downloaded or applied font: draw the sample with it.
                        Column {
                            EmojiRow(
                                stringResource(R.string.emoji_sample_short),
                                MaterialTheme.typography.headlineMedium, family,
                            )
                            EmojiRow(
                                stringResource(R.string.emoji_sample_short_2),
                                MaterialTheme.typography.headlineMedium, family,
                            )
                        }
                    } else {
                        // A catalog font without a preview image: nothing to draw it with yet.
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.72f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                when {
                    applying -> LoadingIndicator(Modifier.size(28.dp))
                    selected -> Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.font_selected),
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f, fill = true).heightIn(min = 12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    EmojiRow(
        stringResource(R.string.emoji_sample_faces), MaterialTheme.typography.headlineLarge, family,
    )
    EmojiRow(
        stringResource(R.string.emoji_sample_things), MaterialTheme.typography.headlineLarge, family,
    )
}

@Composable
private fun EmojiRow(text: String, style: TextStyle, family: FontFamily) {
    Text(
        text = text,
        style = emojiStyle(style, family),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
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
        is EmojiDialog.Apply -> {
            val option = dialog.option
            val context = LocalContext.current
            val size = remember(option) { Formatter.formatShortFileSize(context, option.sizeBytes) }
            // Downloaded: draw with the font itself. Otherwise the catalog's preview image.
            val preview = if (option.downloaded) null else rememberPreview(option.preview)
            EmojiActionDialog(
                title = option.label,
                details = stringResource(
                    if (option.downloaded) R.string.emoji_downloaded_size
                    else R.string.emoji_download_size,
                    size,
                ),
                family = option.typeface.toFamily(),
                message = stringResource(R.string.emoji_install_message),
                primaryText = stringResource(R.string.emoji_apply_restart_now),
                secondaryText = stringResource(R.string.emoji_apply_restart_later),
                onPrimary = { vm.applyCatalog(option, restartNow = true) },
                onSecondary = { vm.applyCatalog(option, restartNow = false) },
                onDismiss = vm::dismissDialog,
                showSample = option.typeface != null || preview != null,
                previewImage = preview,
                tertiaryText = stringResource(
                    if (option.downloaded) R.string.emoji_delete_download
                    else R.string.emoji_download_only,
                ),
                onTertiary = {
                    if (option.downloaded) vm.deleteDownload(option) else vm.download(option)
                },
            )
        }
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
    previewImage: ImageBitmap? = null,
    tertiaryText: String? = null,
    onTertiary: () -> Unit = {},
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
                        Column(Modifier.padding(16.dp)) {
                            if (previewImage != null) {
                                Image(
                                    bitmap = previewImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                                )
                            } else {
                                EmojiSample(family)
                            }
                        }
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
                if (tertiaryText != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onTertiary,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SpoofShapes.pill,
                    ) { Text(tertiaryText) }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}
