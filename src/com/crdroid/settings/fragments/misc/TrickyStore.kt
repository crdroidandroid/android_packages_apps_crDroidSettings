/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.nio.charset.StandardCharsets

class TrickyStore : SettingsPreferenceFragment() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * The keybox is validated and replaced by system_server, so the screen only has
     * to trigger the work and follow the Settings the daemon leaves behind.
     */
    private val statusObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refreshStatus()
        }
    }

    private val keyboxPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: ByteArray(0)
                    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    Settings.Secure.putString(
                        requireContext().contentResolver,
                        KEYBOX_KEY,
                        encoded
                    )
                    Settings.Secure.putString(
                        requireContext().contentResolver,
                        SOURCE_KEY,
                        SOURCE_USER
                    )
                    killGms()
                    toast(getString(R.string.ts_keybox_imported))
                    // Let system_server validate what was just imported.
                    ActivityManager.getService().refreshSpoofTrickyStoreStatus()
                    refreshStatus()
                } catch (e: Exception) {
                    toast(getString(R.string.ts_failed, e.message ?: ""))
                }
            }
        }
    }

    private val targetPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val text = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(StandardCharsets.UTF_8)
                    } ?: ""
                    Settings.Secure.putString(
                        requireContext().contentResolver,
                        TARGET_KEY,
                        text
                    )
                    toast(getString(R.string.ts_target_list_imported))
                    refreshStatus()
                } catch (e: Exception) {
                    toast(getString(R.string.ts_failed, e.message ?: ""))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.tricky_store)

        findPreference<SwitchPreferenceCompat>("ts_auto_update")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: false
                Settings.Secure.putInt(
                    requireContext().contentResolver,
                    ENABLED_KEY,
                    if (enabled) 1 else 0
                )
                summary = getString(
                    if (enabled) R.string.ts_auto_update_summary
                    else R.string.ts_auto_update_off_summary
                )
                true
            }
        }

        findPreference<Preference>("ts_fetch_keybox")?.setOnPreferenceClickListener {
            requestKeyBoxUpdate()
            true
        }

        findPreference<Preference>("ts_check_revocation")?.setOnPreferenceClickListener {
            requestStatusCheck()
            true
        }

        findPreference<Preference>("ts_import_keybox")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            keyboxPicker.launch(intent)
            true
        }

        findPreference<Preference>("ts_delete_keybox")?.setOnPreferenceClickListener {
            showDeleteKeyboxDialog()
            true
        }

        findPreference<Preference>("ts_security_patch")?.setOnPreferenceClickListener {
            showPatchDateDialog()
            true
        }

        findPreference<Preference>("ts_import_targets")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
            }
            targetPicker.launch(intent)
            true
        }

        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        val resolver = requireContext().contentResolver
        listOf(
            KEYBOX_KEY, SOURCE_KEY, STATUS_KEY, REASON_KEY, FETCHED_KEY, ENABLED_KEY
        ).forEach { key ->
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(key), false, statusObserver, UserHandle.USER_ALL
            )
        }
    }

    override fun onStop() {
        super.onStop()
        requireContext().contentResolver.unregisterContentObserver(statusObserver)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestKeyBoxUpdate() {
        try {
            ActivityManager.getService().refreshSpoofTrickyStoreKeyBox()
            toast(getString(R.string.ts_updating_keybox))
            refreshStatus()
        } catch (e: Exception) {
            toast(getString(R.string.ts_failed, e.message ?: ""))
        }
    }

    private fun requestStatusCheck() {
        try {
            ActivityManager.getService().refreshSpoofTrickyStoreStatus()
            toast(getString(R.string.ts_checking))
            refreshStatus()
        } catch (e: Exception) {
            toast(getString(R.string.ts_failed, e.message ?: ""))
        }
    }

    private fun autoUpdateEnabled(): Boolean {
        val value = Settings.Secure.getInt(
            requireContext().contentResolver, ENABLED_KEY, 1
        )
        return value != 0
    }

    private fun refreshStatus() {
        if (!isAdded) return

        val resolver = requireContext().contentResolver
        val keyboxExists = !Settings.Secure.getString(resolver, KEYBOX_KEY).isNullOrEmpty()

        findPreference<SwitchPreferenceCompat>("ts_auto_update")?.apply {
            val enabled = autoUpdateEnabled()
            if (isChecked != enabled) isChecked = enabled
            summary = getString(
                if (enabled) R.string.ts_auto_update_summary
                else R.string.ts_auto_update_off_summary
            )
        }

        val targetContent = Settings.Secure.getString(resolver, TARGET_KEY)
        val targetCount = if (!targetContent.isNullOrEmpty()) {
            targetContent.lines().count { it.isNotBlank() }
        } else 0

        findPreference<Preference>("ts_import_keybox")?.summary =
            if (keyboxExists) getString(R.string.ts_keybox_installed)
            else getString(R.string.ts_no_keybox)

        findPreference<Preference>("ts_delete_keybox")?.isEnabled = keyboxExists

        findPreference<Preference>("ts_manage_targets")?.summary =
            if (targetCount > 0) getString(R.string.ts_target_apps_count, targetCount)
            else getString(R.string.ts_no_targets)

        val patchDate = Settings.Secure.getString(resolver, PATCH_KEY)
        findPreference<Preference>("ts_security_patch")?.summary =
            if (!patchDate.isNullOrEmpty()) patchDate
            else getString(R.string.ts_no_patch)

        findPreference<Preference>("ts_verification_mode")?.summary = buildVerificationSummary()

        findPreference<Preference>("ts_keybox_status")?.summary = buildStatusSummary(keyboxExists)
    }

    private fun buildStatusSummary(keyboxExists: Boolean): String {
        if (!keyboxExists) return getString(R.string.ts_status_no_keybox)

        val parts = mutableListOf<String>()
        val status = Settings.Secure.getString(requireContext().contentResolver, STATUS_KEY)
        val reason = Settings.Secure.getString(requireContext().contentResolver, REASON_KEY)
            ?.takeIf { it.isNotEmpty() }
        parts += describeStatus(status, reason)

        val source = Settings.Secure.getString(
            requireContext().contentResolver, SOURCE_KEY
        )
        parts += getString(
            if (source == SOURCE_OFFICIAL) R.string.ts_keybox_source_official
            else R.string.ts_keybox_source_user
        )

        val fetched = Settings.Secure.getLong(
            requireContext().contentResolver, FETCHED_KEY, 0L
        )
        if (fetched > 0L) {
            parts += getString(
                R.string.ts_last_updated,
                DateUtils.getRelativeTimeSpanString(
                    fetched, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
            )
        }
        return parts.joinToString(" · ")
    }

    private fun describeStatus(status: String?, reason: String?): String =
        when (status) {
            STATUS_VALID -> getString(R.string.ts_status_valid)
            STATUS_EXPIRING_SOON -> statusLabel(R.string.ts_status_expiring_soon, reason)
            STATUS_REVOKED -> statusLabel(R.string.ts_status_revoked, reason)
            STATUS_SUSPENDED -> statusLabel(R.string.ts_status_suspended, reason)
            STATUS_CHAIN_INVALID -> statusLabel(R.string.ts_status_chain_invalid, reason)
            STATUS_UNTRUSTED_ROOT -> statusLabel(R.string.ts_status_untrusted_root, reason)
            STATUS_NO_KEYBOX -> getString(R.string.ts_status_no_keybox)
            else -> getString(R.string.ts_status_unknown)
        }

    private fun statusLabel(labelRes: Int, reason: String?): String {
        val label = getString(labelRes)
        return if (reason.isNullOrEmpty()) label else "$label ($reason)"
    }

    private fun buildVerificationSummary(): String {
        val content = Settings.Secure.getString(
            requireContext().contentResolver, TARGET_KEY
        ) ?: return getString(R.string.ts_verification_mode_auto)

        var auto = 0; var cert = 0; var leaf = 0
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) when {
                trimmed.endsWith("!") -> cert++
                trimmed.endsWith("?") -> leaf++
                else                  -> auto++
            }
        }

        if (auto == 0 && cert == 0 && leaf == 0)
            return getString(R.string.ts_verification_mode_auto)

        return buildList {
            if (auto > 0) add(getString(R.string.ts_verification_auto_count, auto))
            if (cert > 0) add(getString(R.string.ts_verification_cert_count, cert))
            if (leaf > 0) add(getString(R.string.ts_verification_leaf_count, leaf))
        }.joinToString(" · ")
    }

    private fun showDeleteKeyboxDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ts_delete_keybox_title)
            .setMessage(R.string.ts_delete_keybox_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    Settings.Secure.putString(
                        requireContext().contentResolver, KEYBOX_KEY, "")
                    Settings.Secure.putString(
                        requireContext().contentResolver, SOURCE_KEY, "")
                    toast(getString(R.string.ts_keybox_deleted))
                    refreshStatus()
                } catch (e: Exception) {
                    toast(getString(R.string.ts_failed, e.message ?: ""))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPatchDateDialog() {
        val current = Settings.Secure.getString(requireContext().contentResolver, PATCH_KEY) ?: ""
        val input = android.widget.EditText(requireContext()).apply {
            setText(current)
            hint = getString(R.string.ts_patch_date_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 24)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.ts_security_patch)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.delete) { _, _ ->
                Settings.Secure.putString(
                    requireContext().contentResolver, PATCH_KEY, "")
                refreshStatus()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isNotEmpty() && !value.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    toast(getString(R.string.ts_invalid_patch_date))
                    return@setOnClickListener
                }
                Settings.Secure.putString(
                    requireContext().contentResolver, PATCH_KEY, value)
                refreshStatus()
                dialog.dismiss()
            }
            if (current.isEmpty()) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
            }
        }

        dialog.show()
    }

    private fun killGms() {
        try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage(VENDING_PACKAGE)
            am.forceStopPackage(DROIDGUARD_PACKAGE)
            am.forceStopPackage(GMS_PACKAGE)
            am.forceStopPackage(RKPD_PACKAGE)
            // Clear Play Store's cached attestation results so the new
            // keybox/config takes effect immediately (mirrors Specter's gms.sh).
            requireContext().packageManager.clearApplicationUserData(
                VENDING_PACKAGE, null)
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private const val ENABLED_KEY = "spoof_trickystore_enabled"
        private const val KEYBOX_KEY = "spoof_trickystore_keybox"
        private const val SOURCE_KEY = "spoof_trickystore_keybox_source"
        private const val STATUS_KEY = "spoof_trickystore_last_revocation_status"
        private const val REASON_KEY = "spoof_trickystore_last_revocation_reason"
        private const val FETCHED_KEY = "spoof_trickystore_last_fetched"
        private const val TARGET_KEY = TrickyStoreAppSettings.TARGET_KEY
        internal const val PATCH_KEY = "spoof_trickystore_patch"
        private const val SOURCE_OFFICIAL = "official"
        private const val SOURCE_USER = "user"
        private const val STATUS_VALID = "VALID"
        private const val STATUS_EXPIRING_SOON = "EXPIRING_SOON"
        private const val STATUS_REVOKED = "REVOKED"
        private const val STATUS_SUSPENDED = "SUSPENDED"
        private const val STATUS_CHAIN_INVALID = "CHAIN_INVALID"
        private const val STATUS_UNTRUSTED_ROOT = "UNTRUSTED_ROOT"
        private const val STATUS_NO_KEYBOX = "NO_KEYBOX"
        private const val VENDING_PACKAGE = "com.android.vending"
        private const val DROIDGUARD_PACKAGE = "com.google.android.gms.unstable"
        private const val GMS_PACKAGE = "com.google.android.gms"
        private const val RKPD_PACKAGE = "com.google.android.rkpdapp"
    }
}
