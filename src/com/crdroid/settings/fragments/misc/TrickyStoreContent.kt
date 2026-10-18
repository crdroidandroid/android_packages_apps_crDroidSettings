/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.android.settings.R

/** Dialog shown over the TrickyStore screen. The fragment sets it, [TsDialogs] renders it. */
internal sealed interface TsDialog {
    object DeleteKeybox : TsDialog
    class PatchDate(val current: String) : TsDialog

    /** The status check flagged the installed keybox. */
    class Flagged(val title: String, val message: String) : TsDialog

    /** Offer to clear Play Store data after the keybox changed. */
    object WipePlayStore : TsDialog
}

/** Observable state the [TrickyStore] fragment pushes into the Compose screen. */
internal class TsUiState {
    var dialog by mutableStateOf<TsDialog?>(null)

    var keyboxExists by mutableStateOf(false)
    var keyboxSummary by mutableStateOf("")
    var expirySummary by mutableStateOf<String?>(null)
    var expiryTone by mutableStateOf(SpoofTone.Neutral)
    var expiryChip by mutableStateOf<String?>(null)

    var statusChip by mutableStateOf("")
    var statusTone by mutableStateOf(SpoofTone.Neutral)
    var statusSummary by mutableStateOf("")
    var checking by mutableStateOf(false)

    var targetCount by mutableStateOf(0)
    var verificationSummary by mutableStateOf("")
    var patchSummary by mutableStateOf("")
    var patchStale by mutableStateOf(false)
}

internal interface TsActions {
    fun onImportKeybox()
    fun onDeleteKeybox()
    fun onCheckStatus()
    fun onManageTargets()
    fun onImportTargets()
    fun onEditPatch()

    fun onConfirmDeleteKeybox()

    /** Returns an error message to keep the patch dialog open, or null once saved. */
    fun onSavePatch(value: String): String?
    fun onDeletePatch()

    /** Flagged-keybox dialog answered: keep it, import another, or delete it. */
    fun onFlaggedKeep()
    fun onFlaggedReplace()
    fun onFlaggedDelete()

    /** [wipe] is true when the user agreed to clear Play Store data. */
    fun onWipePlayStore(wipe: Boolean)
}

@Composable
internal fun TrickyStoreContent(state: TsUiState, actions: TsActions) {
    TsDialogs(state, actions)
    SpoofScreen {
        SpoofingHeaderCard(
            title = stringResource(R.string.tricky_store_title),
            subtitle = if (state.keyboxExists) {
                stringResource(R.string.ts_keybox_installed) + " \u00b7 " +
                    stringResource(R.string.ts_target_apps_count, state.targetCount)
            } else {
                stringResource(R.string.ts_no_keybox)
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.VpnKey,
                contentDescription = null,
                tint = spoofHeaderIconTint(null),
            )
        }

        Spacer(Modifier.height(16.dp))
        SpoofPrimaryAction(
            text = stringResource(
                if (state.keyboxExists) R.string.ts_replace_keybox else R.string.ts_import_keybox,
            ),
            icon = Icons.Rounded.FileOpen,
            onClick = actions::onImportKeybox,
        )
        Spacer(Modifier.height(8.dp))
        SpoofActionRow(
            listOf(
                SpoofAction(
                    text = stringResource(R.string.ts_check_status),
                    icon = Icons.Rounded.Refresh,
                    enabled = state.keyboxExists && !state.checking,
                    onClick = actions::onCheckStatus,
                ),
                SpoofAction(
                    text = stringResource(R.string.delete),
                    icon = Icons.Rounded.DeleteOutline,
                    enabled = state.keyboxExists,
                    onClick = actions::onDeleteKeybox,
                ),
            ),
        )

        SpoofingAnimatedVisibility(visible = state.keyboxExists) {
            SpoofGroup(title = stringResource(R.string.ts_keybox_status)) {
                item {
                    SpoofNavRow(
                        title = stringResource(R.string.ts_revocation_status),
                        summary = state.statusSummary,
                        icon = Icons.Rounded.VerifiedUser,
                        chip = state.statusChip,
                        chipTone = state.statusTone,
                        enabled = !state.checking,
                        onClick = actions::onCheckStatus,
                    )
                }
                item {
                    SpoofRow(
                        title = stringResource(R.string.ts_keybox_keys),
                        summary = state.keyboxSummary,
                        icon = Icons.Rounded.Key,
                    )
                }
                state.expirySummary?.let { expiry ->
                    item {
                        SpoofNavRow(
                            title = stringResource(R.string.ts_keybox_expiry),
                            summary = expiry,
                            icon = Icons.Rounded.CalendarMonth,
                            chip = state.expiryChip,
                            chipTone = state.expiryTone,
                        )
                    }
                }
            }
        }

        SpoofGroup(title = stringResource(R.string.ts_target_configuration)) {
            item {
                SpoofNavRow(
                    title = stringResource(R.string.ts_manage_target_apps),
                    summary = if (state.targetCount > 0)
                        stringResource(R.string.ts_target_apps_count, state.targetCount)
                    else stringResource(R.string.ts_no_targets),
                    icon = Icons.Rounded.Apps,
                    onClick = actions::onManageTargets,
                )
            }
            item {
                SpoofNavRow(
                    title = stringResource(R.string.ts_import_target_list),
                    summary = stringResource(R.string.ts_import_target_list_summary),
                    icon = Icons.Rounded.UploadFile,
                    onClick = actions::onImportTargets,
                )
            }
            item {
                SpoofRow(
                    title = stringResource(R.string.ts_verification_mode),
                    summary = state.verificationSummary,
                    icon = Icons.Rounded.Tune,
                )
            }
            item {
                SpoofNavRow(
                    title = stringResource(R.string.ts_security_patch),
                    summary = state.patchSummary,
                    icon = Icons.Rounded.Policy,
                    chip = if (state.patchStale) stringResource(R.string.ts_patch_stale_chip)
                    else null,
                    chipTone = SpoofTone.Warning,
                    onClick = actions::onEditPatch,
                )
            }
        }

        SpoofFooter(stringResource(R.string.ts_footer_body))
    }
}

@Composable
private fun TsDialogs(state: TsUiState, actions: TsActions) {
    val dismiss = { state.dialog = null }
    when (val dialog = state.dialog) {
        null -> Unit
        TsDialog.DeleteKeybox -> SpoofConfirmDialog(
            title = stringResource(R.string.ts_delete_keybox_title),
            message = stringResource(R.string.ts_delete_keybox_message),
            confirmText = stringResource(R.string.delete),
            onConfirm = actions::onConfirmDeleteKeybox,
            onDismiss = dismiss,
            destructive = true,
        )
        is TsDialog.PatchDate -> TsPatchDateDialog(
            current = dialog.current,
            onSave = actions::onSavePatch,
            onDelete = actions::onDeletePatch,
            onDismiss = dismiss,
        )
        is TsDialog.Flagged -> SpoofDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = dialog.title,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(dialog.message, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { dismiss(); actions.onFlaggedReplace() }) {
                    Text(stringResource(R.string.ts_import_another))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { dismiss(); actions.onFlaggedDelete() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.delete)) }
                    TextButton(onClick = { dismiss(); actions.onFlaggedKeep() }) {
                        Text(stringResource(R.string.ts_keep_keybox))
                    }
                }
            },
        )
        TsDialog.WipePlayStore -> {
            // Cancel and tap-outside both mean "don't wipe".
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

@Composable
private fun TsPatchDateDialog(
    current: String,
    onSave: (String) -> String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    SpoofDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.ts_security_patch),
        text = {
            Column {
                Text(
                    text = stringResource(R.string.ts_patch_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SpoofTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = stringResource(R.string.ts_patch_date_hint),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 10,
                    isError = error != null,
                    supportingText = error,
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
            Row {
                TextButton(
                    onClick = { onDelete(); onDismiss() },
                    enabled = current.isNotEmpty(),
                ) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}
