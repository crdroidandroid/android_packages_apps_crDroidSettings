/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.security.trickystore.TrickyStoreService
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.core.SubSettingLauncher
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class TrickyStore : SettingsPreferenceFragment() {

    private val ui = TsUiState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var checkJob: Job? = null

    /** Hash of the installed keybox, null when none. */
    private var keyboxHash: String? = null
    private var keyboxXml: String? = null
    private var report: KeyboxInspector.Report? = null

    /** Set after an import so the Play Store wipe prompt follows the status result. */
    private var offerWipeAfterCheck = false

    private val keyboxPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        result.data?.data?.let { uri ->
            try {
                val bytes = requireContext().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: ByteArray(0)
                importKeybox(bytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                toast(getString(R.string.ts_failed, e.message ?: ""))
            }
        }
    }

    private val targetPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        result.data?.data?.let { uri ->
            try {
                val text = requireContext().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
                importTargets(text)
            } catch (e: Exception) {
                toast(getString(R.string.ts_failed, e.message ?: ""))
            }
        }
    }

    private val actions = object : TsActions {
        override fun onImportKeybox() = launchKeyboxPicker()

        override fun onDeleteKeybox() {
            ui.dialog = TsDialog.DeleteKeybox
        }

        override fun onCheckStatus() = runStatusCheck(userInitiated = true)

        override fun onManageTargets() {
            SubSettingLauncher(requireContext())
                .setDestination(TrickyStoreAppSettings::class.java.name)
                .setTitleRes(R.string.ts_manage_target_apps)
                .setSourceMetricsCategory(getMetricsCategory())
                .launch()
        }

        override fun onImportTargets() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
            }
            targetPicker.launch(intent)
        }

        override fun onEditPatch() {
            val current = Settings.Secure.getString(
                requireContext().contentResolver, PATCH_KEY) ?: ""
            ui.dialog = TsDialog.PatchDate(current)
        }

        override fun onConfirmDeleteKeybox() = deleteKeybox()

        override fun onSavePatch(value: String): String? {
            if (value.isNotEmpty() && !isValidPatchContent(value)) {
                return getString(R.string.ts_invalid_patch)
            }
            Settings.Secure.putString(requireContext().contentResolver, PATCH_KEY, value)
            refreshStatus()
            return null
        }

        override fun onDeletePatch() {
            Settings.Secure.putString(requireContext().contentResolver, PATCH_KEY, "")
            refreshStatus()
        }

        override fun onFlaggedKeep() {
            if (offerWipeAfterCheck) {
                offerWipeAfterCheck = false
                ui.dialog = TsDialog.WipePlayStore
            }
        }

        override fun onFlaggedReplace() {
            offerWipeAfterCheck = false
            launchKeyboxPicker()
        }

        override fun onFlaggedDelete() {
            offerWipeAfterCheck = false
            deleteKeybox()
        }

        override fun onWipePlayStore(wipe: Boolean) {
            if (!wipe) return
            try {
                requireContext().packageManager.clearApplicationUserData(VENDING_PACKAGE, null)
            } catch (_: Exception) {}
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.tricky_store_title)
        refreshStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = spoofComposeView(requireContext()).apply {
        setContent {
            SettingsTheme {
                TrickyStoreContent(state = ui, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // Re-check at most once a day while the screen is visited; the user can force a
        // check from the status row at any time.
        val last = report?.checkedAt ?: 0L
        if (keyboxXml != null &&
            System.currentTimeMillis() - last > KeyboxInspector.RECHECK_INTERVAL_MS) {
            runStatusCheck(userInitiated = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    // ---- Keybox import / delete ------------------------------------------------------------

    private fun launchKeyboxPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        keyboxPicker.launch(intent)
    }

    private fun importKeybox(content: String) {
        // Accept raw XML or a base64 export; store base64 of the XML exactly once, without a
        // BOM, since the framework decodes a single base64 layer and expects "<" first.
        val xml = KeyboxInspector.decode(content)?.removePrefix("\uFEFF")?.trim()
        if (xml == null) {
            toast(getString(R.string.ts_keybox_invalid, getString(R.string.ts_keybox_not_xml)))
            return
        }
        KeyboxInspector.validateForImport(xml)?.let { problem ->
            toast(getString(R.string.ts_keybox_invalid, problem))
            return
        }
        val encoded = Base64.encodeToString(xml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        Settings.Secure.putString(requireContext().contentResolver, KEYBOX_KEY, encoded)
        stopGmsFamily()
        toast(getString(R.string.ts_keybox_imported))
        refreshStatus()
        offerWipeAfterCheck = true
        runStatusCheck(userInitiated = true)
    }

    private fun deleteKeybox() {
        checkJob?.cancel()
        ui.checking = false
        Settings.Secure.putString(requireContext().contentResolver, KEYBOX_KEY, "")
        KeyboxInspector.clearReport(requireContext())
        stopGmsFamily()
        toast(getString(R.string.ts_keybox_deleted))
        refreshStatus()
    }

    // ---- Status check ----------------------------------------------------------------------

    private fun runStatusCheck(userInitiated: Boolean) {
        val xml = keyboxXml ?: return
        val hash = keyboxHash ?: return
        if (checkJob?.isActive == true) return

        val previous = report?.status
        ui.checking = true
        applyStatusUi(null, checking = true)
        val appContext = requireContext().applicationContext

        checkJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { KeyboxInspector.inspect(appContext, xml) }
            }
            ui.checking = false
            if (!isAdded) return@launch
            result.fold(
                onSuccess = { newReport ->
                    // A failed network check must not erase a known bad verdict.
                    val keep = newReport.status == KeyboxInspector.Status.UNVERIFIED &&
                        previous != null && previous.isBad()
                    if (!keep) {
                        KeyboxInspector.saveReport(appContext, hash, newReport)
                        report = newReport
                    }
                    applyStatusUi(report, checking = false)
                    onCheckFinished(report?.status, previous, userInitiated)
                },
                onFailure = { e ->
                    applyStatusUi(report, checking = false)
                    ui.statusSummary = getString(R.string.ts_revocation_error, e.message ?: "")
                    if (offerWipeAfterCheck) {
                        offerWipeAfterCheck = false
                        ui.dialog = TsDialog.WipePlayStore
                    }
                },
            )
        }
    }

    private fun onCheckFinished(
        status: KeyboxInspector.Status?,
        previous: KeyboxInspector.Status?,
        userInitiated: Boolean,
    ) {
        val flagged = status != null && status.isBad() && (userInitiated || status != previous)
        when {
            flagged && ui.dialog == null -> showFlaggedDialog(status!!)
            offerWipeAfterCheck && ui.dialog == null -> {
                offerWipeAfterCheck = false
                ui.dialog = TsDialog.WipePlayStore
            }
            userInitiated && status == KeyboxInspector.Status.VALID ->
                toast(getString(R.string.ts_revocation_valid))
        }
    }

    private fun showFlaggedDialog(status: KeyboxInspector.Status) {
        val reason = report?.reason.orEmpty()
        val (title, message) = when (status) {
            KeyboxInspector.Status.REVOKED ->
                R.string.ts_flag_revoked_title to getString(R.string.ts_flag_revoked_message)
            KeyboxInspector.Status.SUSPENDED ->
                R.string.ts_flag_suspended_title to getString(R.string.ts_flag_suspended_message)
            KeyboxInspector.Status.EXPIRED ->
                R.string.ts_flag_expired_title to getString(R.string.ts_flag_expired_message, reason)
            KeyboxInspector.Status.EXPIRING_SOON ->
                R.string.ts_flag_expiring_title to getString(R.string.ts_flag_expiring_message, reason)
            KeyboxInspector.Status.CHAIN_INVALID ->
                R.string.ts_flag_chain_title to getString(R.string.ts_flag_chain_message, reason)
            KeyboxInspector.Status.UNTRUSTED_ROOT ->
                R.string.ts_flag_root_title to getString(R.string.ts_flag_root_message)
            else -> return
        }
        ui.dialog = TsDialog.Flagged(getString(title), message)
    }

    private fun KeyboxInspector.Status.isBad(): Boolean = when (this) {
        KeyboxInspector.Status.VALID, KeyboxInspector.Status.UNVERIFIED -> false
        else -> true
    }

    // ---- UI refresh ------------------------------------------------------------------------

    private fun refreshStatus() {
        val resolver = requireContext().contentResolver

        keyboxXml = KeyboxInspector.decode(Settings.Secure.getString(resolver, KEYBOX_KEY))
        keyboxHash = keyboxXml?.let { KeyboxInspector.hashOf(it) }
        report = keyboxHash?.let { KeyboxInspector.loadReport(requireContext(), it) }
        ui.keyboxExists = keyboxXml != null

        val summary = keyboxXml?.let { KeyboxInspector.describe(it) }
        ui.keyboxSummary = when {
            summary == null && keyboxXml != null -> getString(R.string.ts_keybox_unreadable)
            summary == null -> ""
            else -> getString(
                R.string.ts_keybox_keys_summary,
                summary.algorithms.joinToString(" \u00b7 "),
                summary.keys.maxOf { it.chainLength },
            )
        }
        applyExpiryUi(summary?.earliestExpiry)

        // Count only installed targets: uninstalled defaults stay in the stored list so
        // they apply once installed, but they aren't "configured" from the user's view.
        val targets = TrickyStoreAppSettings.installedTargets(
            requireContext().packageManager,
            TrickyStoreAppSettings.parseTargetList(Settings.Secure.getString(resolver, TARGET_KEY)),
        )
        ui.targetCount = targets.size
        ui.verificationSummary = buildVerificationSummary(targets)

        val patch = Settings.Secure.getString(resolver, PATCH_KEY)
        ui.patchStale = !patch.isNullOrEmpty() && TrickyStoreService.isPatchLevelStale(patch.trim())
        ui.patchSummary = when {
            patch.isNullOrBlank() -> getString(R.string.ts_no_patch)
            isDevicePatchValue(patch) -> getString(R.string.ts_patch_device)
            patch.trim().lines().size > 1 || patch.trim().startsWith("{") ->
                getString(R.string.ts_patch_custom_blocks)
            else -> patch.trim()
        }

        if (!ui.checking) applyStatusUi(report, checking = false)
    }

    private fun applyExpiryUi(expiry: Long?) {
        if (expiry == null) {
            ui.expirySummary = null
            ui.expiryChip = null
            return
        }
        val days = TimeUnit.MILLISECONDS.toDays(expiry - System.currentTimeMillis())
        ui.expirySummary = getString(R.string.ts_keybox_expiry_summary, KeyboxInspector.formatDate(expiry))
        ui.expiryChip = if (days < 0) getString(R.string.ts_chip_expired)
        else getString(R.string.ts_days_left, days)
        ui.expiryTone = when {
            days < 0 -> SpoofTone.Negative
            days <= 14 -> SpoofTone.Warning
            else -> SpoofTone.Positive
        }
    }

    private fun applyStatusUi(report: KeyboxInspector.Report?, checking: Boolean) {
        if (!isAdded) return
        if (checking) {
            ui.statusChip = getString(R.string.ts_chip_checking)
            ui.statusTone = SpoofTone.Neutral
            ui.statusSummary = getString(R.string.ts_revocation_checking)
            return
        }
        if (report == null) {
            ui.statusChip = getString(R.string.ts_chip_unchecked)
            ui.statusTone = SpoofTone.Neutral
            ui.statusSummary = getString(R.string.ts_revocation_not_yet_checked)
            return
        }
        val reason = report.reason
        val (chip, tone, summary) = when (report.status) {
            KeyboxInspector.Status.VALID -> Triple(
                R.string.ts_chip_valid, SpoofTone.Positive,
                getString(R.string.ts_revocation_valid))
            KeyboxInspector.Status.EXPIRING_SOON -> Triple(
                R.string.ts_chip_expiring, SpoofTone.Warning,
                getString(R.string.ts_revocation_expiring, reason))
            KeyboxInspector.Status.REVOKED -> Triple(
                R.string.ts_chip_revoked, SpoofTone.Negative,
                getString(R.string.ts_revocation_revoked, reason.ifEmpty { "-" }))
            KeyboxInspector.Status.SUSPENDED -> Triple(
                R.string.ts_chip_suspended, SpoofTone.Negative,
                getString(R.string.ts_revocation_suspended, reason.ifEmpty { "-" }))
            KeyboxInspector.Status.EXPIRED -> Triple(
                R.string.ts_chip_expired, SpoofTone.Negative,
                getString(R.string.ts_revocation_expired, reason))
            KeyboxInspector.Status.CHAIN_INVALID -> Triple(
                R.string.ts_chip_invalid, SpoofTone.Negative,
                getString(R.string.ts_revocation_chain_invalid, reason))
            KeyboxInspector.Status.UNTRUSTED_ROOT -> Triple(
                R.string.ts_chip_unknown_root, SpoofTone.Warning,
                getString(R.string.ts_revocation_untrusted_root))
            KeyboxInspector.Status.UNVERIFIED -> Triple(
                R.string.ts_chip_unverified, SpoofTone.Neutral,
                getString(R.string.ts_revocation_unverified))
        }
        ui.statusChip = getString(chip)
        ui.statusTone = tone
        val checkedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(report.checkedAt))
        ui.statusSummary = summary + "\n" + getString(R.string.ts_revocation_last_checked, checkedAt)
    }

    private fun buildVerificationSummary(targets: Map<String, TargetMode>): String {
        if (targets.isEmpty()) return getString(R.string.ts_no_targets)
        val counts = targets.values.groupingBy { it }.eachCount()
        return buildList {
            counts[TargetMode.AUTO]?.let { add(getString(R.string.ts_verification_auto_count, it)) }
            counts[TargetMode.LEAF_HACK]?.let { add(getString(R.string.ts_verification_leaf_count, it)) }
            counts[TargetMode.CERT_GEN]?.let { add(getString(R.string.ts_verification_cert_count, it)) }
            counts[TargetMode.SKIP]?.let { add(getString(R.string.ts_verification_skip_count, it)) }
        }.joinToString(" \u00b7 ")
    }

    // ---- Target list import ----------------------------------------------------------------

    /**
     * Accepts the framework's two target formats: one package per line with an optional
     * mode suffix (`?` leaf, `!` cert, `-` skip, `#` comments), or a JSON array of
     * {"package", "mode"} objects. Anything else is refused instead of being stored.
     */
    private fun importTargets(raw: String) {
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) {
            toast(getString(R.string.ts_target_list_invalid, "-"))
            return
        }
        if (text.startsWith("[")) {
            try {
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val obj = array.get(i) as JSONObject
                    val pkg = obj.getString("package")
                    require(PACKAGE_REGEX.matches(pkg)) { pkg }
                    obj.optString("mode").takeIf { it.isNotEmpty() }?.let { mode ->
                        require(mode.uppercase() in VALID_TARGET_MODES) { "mode: $mode" }
                    }
                }
            } catch (e: Exception) {
                toast(getString(R.string.ts_target_list_invalid, e.message ?: "JSON"))
                return
            }
        } else {
            val bad = text.lines().map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !TARGET_LINE_REGEX.matches(it) }
            if (bad != null) {
                toast(getString(R.string.ts_target_list_invalid, bad))
                return
            }
        }
        Settings.Secure.putString(requireContext().contentResolver, TARGET_KEY, text)
        toast(getString(R.string.ts_target_list_imported))
        refreshStatus()
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private fun stopGmsFamily() {
        val am = requireContext().getSystemService(ActivityManager::class.java)
        killPackages(am, GMS_FAMILY_PACKAGES)
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val KEYBOX_KEY = "spoof_trickystore_keybox"
        private const val TARGET_KEY = TrickyStoreAppSettings.TARGET_KEY
        internal const val PATCH_KEY = "spoof_trickystore_patch"

        private val PACKAGE_REGEX = Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*""")
        private val TARGET_LINE_REGEX = Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*\s*[?!-]?""")
        // AttestationUtils.parsePatchLevel() strips dashes and takes 8 (YYYYMMDD) or
        // 6 (YYYYMM) digits, so 2026-07-05, 20260705, 2026-07 and 202607 all work.
        private val DATE_REGEX = Regex("""(\d{4})-?(\d{2})(?:-?(\d{2}))?""")
        // Resolved by TrickyStoreService.resolvePatchTemplate() to the current UTC month.
        private val TEMPLATE_REGEX = Regex("""YYYY-MM-(\d{2})""")

        // Mode strings TrickyStoreService.parseTargetsJson accepts; anything else there
        // falls back to AUTO, so the UI refuses it rather than let it degrade silently.
        private val VALID_TARGET_MODES = setOf("AUTO", "LEAF_HACK", "GENERATE", "SKIP")

        // parsePatchLevel() returns null for these, so the real Build patch is reported.
        private val DEVICE_PATCH_VALUES = setOf("no", "prop")
        private val PATCH_KEYS = setOf("system", "vendor", "boot", "all")

        private fun isPatchValue(raw: String): Boolean {
            val v = raw.trim()
            if (v.lowercase() in DEVICE_PATCH_VALUES) return true
            TEMPLATE_REGEX.matchEntire(v)?.let { return it.groupValues[1].toInt() in 1..31 }
            val m = DATE_REGEX.matchEntire(v) ?: return false
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3]
            return month in 1..12 && (day.isEmpty() || day.toInt() in 1..31)
        }

        /** True when [value] is a single "no" / "prop", i.e. report the device's own patch. */
        internal fun isDevicePatchValue(value: String) =
            value.trim().lowercase() in DEVICE_PATCH_VALUES

        /**
         * Patch content in the formats TrickyStoreService.refreshPatchLevel() parses: a
         * single value, `key=value` lines (system / vendor / boot / all) with optional
         * `[package]` sections, or the JSON form. A value is a date (YYYY-MM-DD, YYYYMMDD,
         * YYYY-MM or YYYYMM), a YYYY-MM-DD template, or "no" / "prop" for the device patch.
         */
        internal fun isValidPatchContent(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.startsWith("{")) {
                return try {
                    val json = JSONObject(trimmed)
                    val flat = PATCH_KEYS.mapNotNull { k -> json.optString(k).takeIf { it.isNotEmpty() } }
                    val pkgs = json.optJSONObject("packages")
                    val nested = pkgs?.keys()?.asSequence()?.flatMap { pkg ->
                        val o = pkgs.optJSONObject(pkg) ?: return@flatMap emptySequence()
                        PATCH_KEYS.mapNotNull { k -> o.optString(k).takeIf { it.isNotEmpty() } }
                            .asSequence()
                    }?.toList().orEmpty()
                    (flat + nested).isNotEmpty() && (flat + nested).all(::isPatchValue)
                } catch (_: Exception) {
                    false
                }
            }
            return trimmed.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .all { line ->
                    when {
                        line.startsWith("[") && line.endsWith("]") -> line.length > 2
                        '=' in line -> line.substringBefore('=').trim().lowercase() in PATCH_KEYS &&
                            isPatchValue(line.substringAfter('='))
                        else -> isPatchValue(line)
                    }
                }
        }
    }
}
