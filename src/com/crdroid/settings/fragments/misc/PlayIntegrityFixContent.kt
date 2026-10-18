/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.settings.R

/** Dialog shown over the Play Integrity Fix screen. The fragment sets it, [PifDialogs] renders it. */
internal sealed interface PifDialog {
    object Channel : PifDialog
    class DevicePicker(val names: List<String>, val selected: Int) : PifDialog
    object Delete : PifDialog
    object ResetFlags : PifDialog
    class VerboseLogs(val entries: List<String>, val selected: Int) : PifDialog

    /** Offer to clear Play Store data after a new fingerprint was applied. */
    object WipePlayStore : PifDialog
}

/** Observable state the [PlayIntegrityFix] fragment pushes into the Compose screen. */
internal class PifUiState {
    var dialog by mutableStateOf<PifDialog?>(null)

    var config by mutableStateOf<Map<String, String>>(emptyMap())
    var fetching by mutableStateOf(false)
    var fetchStatus by mutableStateOf<String?>(null)
    var photosSpoof by mutableStateOf(true)
    var verboseLabel by mutableStateOf("")

    /** Days since the config's SECURITY_PATCH, null when absent or unparsable. */
    var patchAgeDays by mutableStateOf<Long?>(null)
}

internal interface PifActions {
    fun onFetch()
    fun onPickChannel(canary: Boolean)
    fun onPickDevice(index: Int)
    fun onImport()
    fun onDelete()
    fun onConfirmDelete()
    fun onResetFlags()
    fun onConfirmResetFlags()
    fun onFlagChange(key: String, value: Boolean)
    fun onVerboseLogs()
    fun onPickVerbose(index: Int)
    fun onPhotosChange(enabled: Boolean)

    /** Returns an error message to keep the edit dialog open, or null once saved. */
    fun onEditValue(key: String, value: String): String?

    /** [wipe] is true when the user agreed to clear Play Store data. */
    fun onWipePlayStore(wipe: Boolean)
}

/** Spoofing flags shown as switches, with the framework default used when a key is absent. */
private class FlagRow(val key: String, val title: Int, val summary: Int, val default: Boolean)

private val FLAG_ROWS = listOf(
    FlagRow("spoofBuild", R.string.pif_flag_spoof_build_title,
        R.string.pif_flag_spoof_build_summary, true),
    FlagRow("spoofProps", R.string.pif_flag_spoof_props_title,
        R.string.pif_flag_spoof_props_summary, true),
    FlagRow("spoofProvider", R.string.pif_flag_spoof_provider_title,
        R.string.pif_flag_spoof_provider_summary, false),
    FlagRow("spoofSignature", R.string.pif_flag_spoof_signature_title,
        R.string.pif_flag_spoof_signature_summary, false),
    FlagRow(PlayIntegrityFix.KEY_VENDING_BUILD, R.string.pif_flag_spoof_vending_build_title,
        R.string.pif_flag_spoof_vending_build_summary, true),
    FlagRow(PlayIntegrityFix.KEY_VENDING_SDK, R.string.pif_flag_spoof_vending_sdk_title,
        R.string.pif_flag_spoof_vending_sdk_summary, false),
)

private val DISPLAY_ORDER = listOf(
    "MODEL", "MANUFACTURER", "BRAND", "PRODUCT", "DEVICE",
    "FINGERPRINT", "SECURITY_PATCH", "ID", "RELEASE", "DEVICE_INITIAL_SDK_INT",
)

/** Same truthiness rule as PlayIntegritySpoofService.processKeyValue(). */
internal fun pifFlagOn(value: String?, default: Boolean) =
    if (value.isNullOrEmpty()) default else value == "1" || value.equals("true", true)

@Composable
internal fun PlayIntegrityFixContent(state: PifUiState, actions: PifActions) {
    val config = state.config
    val hasConfig = config.isNotEmpty()
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }

    SpoofScreen {
        val model = config["MODEL"].orEmpty()
        val isCanary = config["RELEASE"].equals("CANARY", ignoreCase = true)
        SpoofingHeaderCard(
            title = stringResource(R.string.play_integrity_fix_title),
            subtitle = when {
                !hasConfig -> stringResource(R.string.pif_no_config)
                model.isEmpty() -> stringResource(R.string.pif_config_loaded)
                isCanary -> stringResource(R.string.pif_active_model_canary, model)
                else -> stringResource(R.string.pif_active_model, model)
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = spoofHeaderIconTint(null),
            )
        }

        Spacer(Modifier.height(16.dp))
        SpoofPrimaryAction(
            text = stringResource(R.string.pif_fetch_pixel_beta),
            icon = Icons.Rounded.CloudDownload,
            onClick = actions::onFetch,
            loading = state.fetching,
        )
        state.fetchStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        SpoofActionRow(
            listOf(
                SpoofAction(
                    text = stringResource(R.string.pif_import),
                    icon = Icons.Rounded.FileOpen,
                    enabled = !state.fetching,
                    onClick = actions::onImport,
                ),
                SpoofAction(
                    text = stringResource(R.string.delete),
                    icon = Icons.Rounded.DeleteOutline,
                    enabled = hasConfig && !state.fetching,
                    onClick = actions::onDelete,
                ),
                SpoofAction(
                    text = stringResource(R.string.reset),
                    icon = Icons.Rounded.History,
                    enabled = hasConfig && !state.fetching,
                    onClick = actions::onResetFlags,
                ),
            ),
        )

        SpoofGroup(
            title = stringResource(R.string.pif_active_config),
            collapsible = hasConfig,
        ) {
            if (!hasConfig) {
                item {
                    SpoofRow(
                        title = stringResource(R.string.pif_no_config),
                        summary = stringResource(R.string.pif_no_config_summary),
                    )
                }
            } else {
                val patch = config["SECURITY_PATCH"].orEmpty()
                val age = state.patchAgeDays
                if (patch.isNotEmpty()) {
                    item {
                        SpoofNavRow(
                            title = stringResource(
                                if (isCanary) R.string.pif_canary_build else R.string.pif_beta_build),
                            summary = stringResource(R.string.pif_patch_summary, patch),
                            icon = Icons.Rounded.Policy,
                            chip = age?.let { stringResource(R.string.pif_patch_age, it) },
                            chipTone = when {
                                age == null -> SpoofTone.Neutral
                                age <= PATCH_FRESH_DAYS -> SpoofTone.Positive
                                age <= PATCH_AGING_DAYS -> SpoofTone.Warning
                                else -> SpoofTone.Negative
                            },
                        )
                    }
                }
                val hasFingerprint = !config["FINGERPRINT"].isNullOrEmpty()
                val extras = config.keys.filter {
                    it !in DISPLAY_ORDER && !it.startsWith("spoof") &&
                        it != "DEBUG" && it != "verboseLogs" && it != "VERBOSE_LOGS"
                }.sorted()
                (DISPLAY_ORDER + extras).forEach { key ->
                    val value = config[key] ?: return@forEach
                    // The framework re-derives these from FINGERPRINT, so editing them
                    // on their own would have no effect.
                    val derived = hasFingerprint && key in PlayIntegrityFix.FINGERPRINT_DERIVED_KEYS
                    item {
                        SpoofValueRow(
                            label = key,
                            value = value,
                            note = if (derived) stringResource(R.string.pif_value_from_fingerprint)
                            else null,
                            onClick = if (derived) null else ({ editKey = key }),
                        )
                    }
                }
            }
        }

        SpoofGroup(title = stringResource(R.string.pif_settings)) {
            item {
                SpoofSwitchRow(
                    title = stringResource(R.string.pif_spoof_photos),
                    summary = stringResource(R.string.pif_spoof_photos_summary),
                    icon = Icons.Rounded.Photo,
                    checked = state.photosSpoof,
                    onCheckedChange = actions::onPhotosChange,
                )
            }
        }

        SpoofGroup(title = stringResource(R.string.pif_flags_title)) {
            val vendingBuild = pifFlagOn(config[PlayIntegrityFix.KEY_VENDING_BUILD], true)
            FLAG_ROWS.forEach { flag ->
                // The framework only applies spoofVendingSdk while Play Store build
                // spoofing is off.
                val enabled = hasConfig &&
                    !(flag.key == PlayIntegrityFix.KEY_VENDING_SDK && vendingBuild)
                item {
                    SpoofSwitchRow(
                        title = stringResource(flag.title),
                        summary = stringResource(flag.summary),
                        checked = pifFlagOn(config[flag.key], flag.default),
                        enabled = enabled,
                        onCheckedChange = { actions.onFlagChange(flag.key, it) },
                    )
                }
            }
        }

        SpoofGroup(title = stringResource(R.string.pif_logging_title)) {
            item {
                SpoofSwitchRow(
                    title = stringResource(R.string.pif_flag_debug_title),
                    summary = stringResource(R.string.pif_flag_debug_summary),
                    icon = Icons.Rounded.BugReport,
                    checked = pifFlagOn(config["DEBUG"], false),
                    enabled = hasConfig,
                    onCheckedChange = { actions.onFlagChange("DEBUG", it) },
                )
            }
            item {
                SpoofNavRow(
                    title = stringResource(R.string.pif_flag_verbose_logs_title),
                    icon = Icons.AutoMirrored.Rounded.Subject,
                    chip = state.verboseLabel,
                    enabled = hasConfig,
                    onClick = actions::onVerboseLogs,
                )
            }
        }

        SpoofFooter(stringResource(R.string.pif_footer_body))
    }

    PifDialogs(state, actions)

    editKey?.let { key ->
        PifEditDialog(
            key = key,
            initial = config[key].orEmpty(),
            onSave = { actions.onEditValue(key, it) },
            onDismiss = { editKey = null },
        )
    }
}

private const val PATCH_FRESH_DAYS = 45L
private const val PATCH_AGING_DAYS = 90L

@Composable
private fun PifEditDialog(
    key: String,
    initial: String,
    onSave: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var text by remember(key) { mutableStateOf(initial) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    SpoofDialog(
        onDismissRequest = onDismiss,
        title = key,
        text = {
            Column {
                if (key == "FINGERPRINT") {
                    Text(
                        text = stringResource(R.string.pif_edit_fingerprint_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                SpoofTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    isError = error != null,
                    supportingText = error,
                    singleLine = key != "FINGERPRINT",
                    minLines = if (key == "FINGERPRINT") 3 else 1,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onSave(text.trim())
                if (result == null) onDismiss() else error = result
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun PifDialogs(state: PifUiState, actions: PifActions) {
    val dismiss = { state.dialog = null }
    when (val dialog = state.dialog) {
        null -> Unit
        PifDialog.Channel -> SpoofChoiceDialog(
            title = stringResource(R.string.pif_select_channel),
            options = listOf(
                stringResource(R.string.pif_channel_latest_release),
                stringResource(R.string.pif_channel_canary_release),
            ),
            summaries = listOf(
                stringResource(R.string.pif_channel_latest_release_summary),
                stringResource(R.string.pif_channel_canary_release_summary),
            ),
            selected = -1,
            onPick = { actions.onPickChannel(canary = it == 1) },
            onDismiss = dismiss,
        )
        is PifDialog.DevicePicker -> SpoofChoiceDialog(
            title = stringResource(R.string.pif_select_device),
            options = dialog.names,
            selected = dialog.selected,
            onPick = actions::onPickDevice,
            onDismiss = dismiss,
        )
        PifDialog.Delete -> SpoofConfirmDialog(
            title = stringResource(R.string.pif_delete_title, PlayIntegrityFix.PIF_CONFIG_NAME),
            message = stringResource(R.string.pif_delete_message),
            confirmText = stringResource(R.string.delete),
            onConfirm = actions::onConfirmDelete,
            onDismiss = dismiss,
            destructive = true,
        )
        PifDialog.ResetFlags -> SpoofConfirmDialog(
            title = stringResource(R.string.pif_reset_flags_title),
            message = stringResource(R.string.pif_reset_flags_message),
            confirmText = stringResource(R.string.reset),
            onConfirm = actions::onConfirmResetFlags,
            onDismiss = dismiss,
        )
        is PifDialog.VerboseLogs -> SpoofChoiceDialog(
            title = stringResource(R.string.pif_flag_verbose_logs_title),
            options = dialog.entries,
            selected = dialog.selected,
            onPick = actions::onPickVerbose,
            onDismiss = dismiss,
        )
        PifDialog.WipePlayStore -> {
            val decline = { dismiss(); actions.onWipePlayStore(false) }
            SpoofDialog(
                onDismissRequest = decline,
                title = stringResource(R.string.spoof_wipe_playstore_title),
                text = {
                    Text(
                        stringResource(R.string.spoof_wipe_playstore_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { dismiss(); actions.onWipePlayStore(true) }) {
                        Text(stringResource(R.string.spoof_wipe_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = decline) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}
