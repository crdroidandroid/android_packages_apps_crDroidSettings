/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.ui.fonts

import android.graphics.Typeface
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.settings.R
import com.crdroid.settings.fragments.misc.SectionLabel
import com.crdroid.settings.fragments.misc.SpoofAction
import com.crdroid.settings.fragments.misc.SpoofActionRow
import com.crdroid.settings.fragments.misc.SpoofDialog
import com.crdroid.settings.fragments.misc.SpoofFooter
import com.crdroid.settings.fragments.misc.SpoofPrimaryAction
import com.crdroid.settings.fragments.misc.SpoofScreen
import com.crdroid.settings.fragments.misc.SpoofShapes
import com.crdroid.settings.fragments.misc.SpoofStatusChip
import com.crdroid.settings.fragments.misc.SpoofTone
import com.crdroid.settings.fragments.misc.SpoofingAnimatedVisibility
import com.crdroid.settings.fragments.misc.SpoofingLoadingBox

private val CardShape = RoundedCornerShape(20.dp)

private fun Typeface?.toFamily(): FontFamily = if (this == null) FontFamily.Default else FontFamily(this)

@Composable
internal fun FontPickerContent(vm: FontPickerViewModel, onAddFont: () -> Unit) {
    val context = LocalContext.current
    val busy = FontOps.busy

    // An operation finished (possibly started by a previous instance of this screen).
    LaunchedEffect(FontOps.generation) { if (FontOps.generation > 0) vm.refresh() }
    // "Apply and restart SystemUI" is carried out by whichever screen is alive now.
    LaunchedEffect(FontOps.restartRequest) { vm.consumeRestartRequest(context) }

    FontDialogs(vm)

    SpoofScreen {
        CurrentFontCard(
            label = vm.currentLabel,
            family = vm.currentTypeface.toFamily(),
            isCustom = vm.customName != null,
        )

        val pending = vm.pending
        SpoofingAnimatedVisibility(visible = pending != null && busy == null) {
            if (pending != null) {
                PendingRestartBanner(
                    pending = pending,
                    onRestart = { vm.restartNow(context) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        SpoofingAnimatedVisibility(visible = busy is FontBusy.Reading || busy is FontBusy.Installing) {
            BusyBanner(
                text = when (busy) {
                    is FontBusy.Installing -> stringResource(R.string.font_installing, busy.name)
                    else -> stringResource(R.string.font_reading)
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        SpoofPrimaryAction(
            text = stringResource(if (vm.customName != null) R.string.font_replace_custom else R.string.add_font),
            icon = Icons.Rounded.FontDownload,
            onClick = onAddFont,
            enabled = busy == null,
            loading = busy is FontBusy.Reading || busy is FontBusy.Installing,
        )

        val customName = vm.customName
        if (customName != null) {
            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.font_custom_section))
            FontCard(
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
                        onClick = onAddFont,
                    ),
                    SpoofAction(
                        text = stringResource(R.string.font_remove),
                        icon = Icons.Rounded.DeleteOutline,
                        enabled = busy == null,
                        onClick = vm::onRemoveCustom,
                    ),
                ),
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.font_system_section))
        if (vm.loading && vm.options.isEmpty()) {
            SpoofingLoadingBox(Modifier.padding(vertical = 48.dp))
        } else {
            FontGrid(
                options = vm.options,
                selectedKey = vm.selectedKey,
                applyingPackage = (busy as? FontBusy.Applying)?.packageName,
                enabled = busy == null,
                onClick = vm::onFontClicked,
            )
        }

        SpoofFooter(text = stringResource(R.string.font_picker_footer))
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun CurrentFontCard(label: String, family: FontFamily, isCustom: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.font_current),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isCustom) {
                    SpoofStatusChip(stringResource(R.string.font_custom_chip), SpoofTone.Positive)
                }
            }
            // transitionSpec isn't composable, so read the motion specs out here.
            val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = label to family,
                transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
                label = "current_font",
            ) { (name, fam) ->
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = fam,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.font_preview_quote),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = fam,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FontGrid(
    options: List<FontOption>,
    selectedKey: String,
    applyingPackage: String?,
    enabled: Boolean,
    onClick: (FontOption) -> Unit,
) {
    // A plain grid: the page scrolls as a whole and there are rarely more than ~20 fonts.
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
                        FontCard(
                            label = option.label,
                            family = option.typeface.toFamily(),
                            selected = option.packageName == selectedKey,
                            applying = option.packageName == applyingPackage,
                            enabled = enabled,
                            badge = null,
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
private fun FontCard(
    label: String,
    family: FontFamily,
    selected: Boolean,
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
        label = "font_card_container",
    )
    val content = if (selected || applying) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 128.dp)
            .semantics { role = Role.RadioButton },
        shape = CardShape,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(R.string.font_sample_glyphs),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = family,
                    modifier = Modifier.weight(1f),
                )
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
                fontFamily = family,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = badge ?: stringResource(R.string.font_sample_short),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = family,
                color = content.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Banners
// ---------------------------------------------------------------------------

@Composable
private fun PendingRestartBanner(
    pending: PendingRestart,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reboot = pending.kind == RestartKind.REBOOT
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (reboot) R.string.font_pending_reboot_title
                            else R.string.font_pending_systemui_title,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (reboot) R.string.font_pending_reboot_body
                            else R.string.font_pending_systemui_body,
                            pending.fontLabel,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRestart,
                modifier = Modifier.align(Alignment.End),
                shape = SpoofShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text(
                    stringResource(
                        if (reboot) R.string.action_reboot_now else R.string.font_restart_systemui,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BusyBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun FontDialogs(vm: FontPickerViewModel) {
    when (val dialog = vm.dialog) {
        null -> Unit
        is FontDialog.Apply -> {
            val reboot = dialog.fromCustom
            FontActionDialog(
                title = dialog.option.label,
                details = null,
                family = dialog.option.typeface.toFamily(),
                message = stringResource(
                    if (reboot) R.string.font_apply_reboot_message
                    else R.string.font_apply_systemui_message,
                    dialog.option.label,
                ),
                primaryText = stringResource(
                    if (reboot) R.string.font_apply_reboot_now else R.string.font_apply_restart_now,
                ),
                secondaryText = stringResource(
                    if (reboot) R.string.font_apply_reboot_later else R.string.font_apply_restart_later,
                ),
                onPrimary = { vm.apply(dialog.option, dialog.fromCustom, restartNow = true) },
                onSecondary = { vm.apply(dialog.option, dialog.fromCustom, restartNow = false) },
                onDismiss = vm::dismissDialog,
            )
        }
        is FontDialog.Install -> {
            val preview = dialog.preview
            val context = LocalContext.current
            val size = remember(preview) { Formatter.formatShortFileSize(context, preview.sizeBytes) }
            FontActionDialog(
                title = preview.postScriptName,
                details = listOfNotNull(preview.displayName, size).joinToString("  ·  "),
                family = remember(preview) { FontFamily(preview.typeface) },
                message = stringResource(R.string.font_install_message),
                primaryText = stringResource(R.string.font_install_reboot_now),
                secondaryText = stringResource(R.string.font_install_reboot_later),
                onPrimary = { vm.install(preview, rebootNow = true) },
                onSecondary = { vm.install(preview, rebootNow = false) },
                onDismiss = vm::dismissDialog,
            )
        }
        is FontDialog.Error -> SpoofDialog(
            onDismissRequest = vm::dismissDialog,
            title = stringResource(R.string.font_error_title),
            text = { Text(dialog.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = vm::dismissDialog) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

/**
 * Preview plus the two ways to go ahead. Choosing up front replaces the old flow, where a
 * separate restart prompt appeared after the change and was lost when the activity recreated.
 */
@Composable
private fun FontActionDialog(
    title: String,
    details: String?,
    family: FontFamily,
    message: String,
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
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
                Spacer(Modifier.height(16.dp))
                FontSpecimen(family)
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

/** Glyphs, weights and running text, so the font can be judged before applying it. */
@Composable
private fun FontSpecimen(family: FontFamily) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.font_sample_glyphs),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = family,
            )
            Text(
                text = stringResource(R.string.font_sample_alphabet),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = family,
            )
            Text(
                text = stringResource(R.string.font_sample_digits),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.font_preview_quote),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = family,
            )
        }
    }
}
