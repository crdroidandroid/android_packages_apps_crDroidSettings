/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.om.OverlayManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrickyStore : SettingsPreferenceFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val keyboxPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val dir = File(TRICKYSTORE_PATH).also { if (!it.exists()) it.mkdirs() }
                    val keyboxFile = File(dir, KEYBOX_FILE)
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        keyboxFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    keyboxFile.setReadable(true, false)
                    killPlayStore()
                    toast(getString(R.string.ts_keybox_imported))
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
                    val dir = File(TRICKYSTORE_PATH).also { if (!it.exists()) it.mkdirs() }
                    val targetFile = File(dir, TARGET_FILE)
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    targetFile.setReadable(true, false)
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

        findPreference<ListPreference>("ts_target_mode")?.setOnPreferenceChangeListener { _, newValue ->
            // Re-save existing targets with updated mode suffix
            resaveTargetsWithMode(newValue as String)
            true
        }

        findPreference<Preference>("ts_manage_targets")?.setOnPreferenceClickListener {
            showTargetAppPicker()
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

        File(TRICKYSTORE_PATH).also { if (!it.exists()) it.mkdirs() }
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun refreshStatus() {
        val keyboxExists = File(TRICKYSTORE_PATH, KEYBOX_FILE).exists()
        val targetFile = File(TRICKYSTORE_PATH, TARGET_FILE)
        val targetCount = if (targetFile.exists()) {
            targetFile.readLines().count { it.isNotBlank() }
        } else 0

        findPreference<Preference>("ts_import_keybox")?.summary =
            if (keyboxExists) getString(R.string.ts_keybox_installed)
            else getString(R.string.ts_no_keybox)

        findPreference<Preference>("ts_delete_keybox")?.isEnabled = keyboxExists

        findPreference<Preference>("ts_manage_targets")?.summary =
            if (targetCount > 0) getString(R.string.ts_target_apps_count, targetCount)
            else getString(R.string.ts_no_targets)
 
        findPreference<ListPreference>("ts_target_mode")?.value = readCurrentMode()
    }

    private fun readCurrentMode(): String {
        val targetFile = File(TRICKYSTORE_PATH, TARGET_FILE)
        if (!targetFile.exists()) return "auto"
        val firstLine = targetFile.readLines().firstOrNull { it.isNotBlank() } ?: return "auto"
        return when {
            firstLine.trimEnd().endsWith("!") -> "cert"
            firstLine.trimEnd().endsWith("?") -> "leaf"
            else -> "auto"
        }
    }

    private fun showDeleteKeyboxDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ts_delete_keybox_title)
            .setMessage(R.string.ts_delete_keybox_message)
            .setPositiveButton(R.string.ts_delete) { _, _ ->
                try {
                    File(TRICKYSTORE_PATH, KEYBOX_FILE).delete()
                    toast(getString(R.string.ts_keybox_deleted))
                    refreshStatus()
                } catch (e: Exception) {
                    toast(getString(R.string.ts_failed, e.message ?: ""))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getTargetModeSuffix(): String {
        val mode = findPreference<ListPreference>("ts_target_mode")?.value ?: "auto"
        return when (mode) {
            "leaf" -> "?"
            "cert" -> "!"
            else -> ""
        }
    }

    private fun showTargetAppPicker() {
        scope.launch {
            val progress = AlertDialog.Builder(requireContext())
                .setMessage(R.string.ts_loading_apps)
                .setCancelable(false)
                .show()

            try {
                val (labels, packages, checked) = withContext(Dispatchers.IO) {
                    loadAppList()
                }

                progress.dismiss()

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ts_manage_target_apps)
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        checked[which] = isChecked
                    }
                    .setPositiveButton(R.string.ts_save) { _, _ ->
                        saveTargetFile(packages, checked)
                        refreshStatus()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (e: Exception) {
                progress.dismiss()
                toast(getString(R.string.ts_failed, e.message ?: ""))
            }
        }
    }

    private fun getOverlayPackages(): Set<String> {
        val om = requireContext().getSystemService(Context.OVERLAY_SERVICE) as OverlayManager
        val userHandle = Process.myUserHandle()

        val androidOverlays = om.getOverlayInfosForTarget("android", userHandle)
        val systemUiOverlays = om.getOverlayInfosForTarget("com.android.systemui", userHandle)
        val settingsOverlays = om.getOverlayInfosForTarget("com.android.settings", userHandle)
        val launcherOverlays = om.getOverlayInfosForTarget("com.android.launcher3", userHandle)

        return (androidOverlays + systemUiOverlays + settingsOverlays + launcherOverlays)
            .map { it.packageName }
            .toSet()
    }

    private fun loadAppList(): Triple<Array<String>, Array<String>, BooleanArray> {
        val pm = requireContext().packageManager
        val overlayPackages = getOverlayPackages()

        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                val systemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isExcluded = app.packageName.contains(".auto_generated") ||
                                app.packageName.contains(".appsearch") ||
                                app.packageName.contains(".backup") ||
                                app.packageName.contains(".carrier") ||
                                app.packageName.contains(".cellbroadcast") ||
                                app.packageName.contains(".cts") ||
                                app.packageName.contains(".federated") ||
                                app.packageName.contains(".ims") ||
                                app.packageName.contains(".overlay") ||
                                app.packageName.contains(".qti") ||
                                app.packageName.contains(".qualcomm") ||
                                app.packageName.contains(".resources") ||
                                app.packageName.contains(".systemui.clocks") ||
                                app.packageName.contains(".systemui.plugin") ||
                                app.packageName.contains(".theme") ||
                                app.packageName.contains(".iconpack")
                val isOverlay = app.packageName in overlayPackages

                !isOverlay && !(systemApp && isExcluded)
            }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val currentTargets = readTargetPackages()
        val labels = installed.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val packages = installed.map { it.packageName }.toTypedArray()
        val checked = packages.map { it in currentTargets }.toBooleanArray()

        return Triple(labels, packages, checked)
    }

    private fun readTargetPackages(): Set<String> {
        val targetFile = File(TRICKYSTORE_PATH, TARGET_FILE)
        if (!targetFile.exists()) return emptySet()
        return targetFile.readLines()
            .map { it.trim().removeSuffix("?").removeSuffix("!") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun saveTargetFile(packages: Array<String>, checked: BooleanArray) {
        try {
            val suffix = getTargetModeSuffix()
            val dir = File(TRICKYSTORE_PATH).also { if (!it.exists()) it.mkdirs() }
            val targetFile = File(dir, TARGET_FILE)
            val selected = packages.zip(checked.toList())
                .filter { it.second }
                .map { it.first + suffix }
            targetFile.writeText(selected.joinToString("\n"))
            targetFile.setReadable(true, false)
            toast(getString(R.string.ts_targets_saved))
        } catch (e: Exception) {
            toast(getString(R.string.ts_failed, e.message ?: ""))
        }
    }

    /**
     * Re-saves existing target packages with a new mode suffix
     * when the user changes the target mode preference.
     */
    private fun resaveTargetsWithMode(mode: String) {
        try {
            val suffix = when (mode) {
                "leaf" -> "?"
                "cert" -> "!"
                else -> ""
            }
            val currentPackages = readTargetPackages()
            if (currentPackages.isEmpty()) return

            val dir = File(TRICKYSTORE_PATH).also { if (!it.exists()) it.mkdirs() }
            val targetFile = File(dir, TARGET_FILE)
            targetFile.writeText(currentPackages.joinToString("\n") { it + suffix })
            targetFile.setReadable(true, false)
        } catch (e: Exception) {
            toast(getString(R.string.ts_failed, e.message ?: ""))
        }
    }

    private fun killPlayStore() {
        try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage(VENDING_PACKAGE)
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private const val TRICKYSTORE_PATH = "/data/system/tricky_store"
        private const val KEYBOX_FILE = "keybox.xml"
        private const val TARGET_FILE = "target.txt"
        private const val VENDING_PACKAGE = "com.android.vending"
    }
}
