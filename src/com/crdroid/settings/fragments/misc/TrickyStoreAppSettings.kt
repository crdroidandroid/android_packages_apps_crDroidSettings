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
import android.os.Bundle
import android.provider.Settings
import android.security.trickystore.TrickyStoreService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** TrickyStore target modes, with the suffix each one has in SPOOF_TRICKYSTORE_TARGET. */
enum class TargetMode(val symbol: String) {
    AUTO(""),
    LEAF_HACK("?"),
    CERT_GEN("!"),
    SKIP("-"),
}

/** Kept for external references; the picker itself uses [AppListEntry] plus a mode. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystem: Boolean = false,
    var targetMode: TargetMode = TargetMode.AUTO,
    var isInTarget: Boolean = false,
)

private val EXCLUDED_SUFFIXES = listOf(
    ".auto_generated", ".appsearch", ".backup", ".carrier",
    ".cellbroadcast", ".cts", ".federated", ".ims", ".overlay",
    ".qti", ".qualcomm", ".resources", ".systemui.clocks",
    ".systemui.plugin", ".theme", ".iconpack",
)

class TrickyStoreAppSettings : SettingsPreferenceFragment() {

    companion object {
        const val TARGET_KEY = "spoof_trickystore_target"

        /**
         * Defaults, parsed from the framework's [TrickyStoreService.DEFAULT_TARGET_LIST] so
         * "Reset" here and AxSpoofManager's seed-on-keybox-load never drift apart.
         */
        val DEFAULT_TARGET_MAP: Map<String, TargetMode> =
            parseTargetList(TrickyStoreService.DEFAULT_TARGET_LIST)

        val DEFAULT_TARGETS: Set<String> = DEFAULT_TARGET_MAP.keys

        /** Backwards-compatible alias: only the non-AUTO defaults with their modes. */
        val DEFAULT_TARGET_MODES: Map<String, TargetMode> =
            DEFAULT_TARGET_MAP.filterValues { it != TargetMode.AUTO }

        /**
         * The targets that are installed for this user. Uninstalled defaults stay in the
         * stored list (so an app installed later is covered at once) but are not counted
         * as configured anywhere in the UI.
         */
        fun installedTargets(pm: PackageManager, targets: Map<String, TargetMode>) =
            targets.filterKeys { isInstalled(pm, it) }

        /**
         * Parses the text target format ("pkg", "pkg?", "pkg!", "pkg-", "#" comments) or the
         * JSON array form ({"package", "mode"}) into package -> mode, keeping order. Skip
         * entries are kept: dropping them here would silently remove them on the next save.
         */
        fun parseTargetList(list: String?): Map<String, TargetMode> {
            val result = LinkedHashMap<String, TargetMode>()
            if (list.isNullOrBlank()) return result
            val trimmed = list.trim()
            if (trimmed.startsWith("[")) {
                runCatching {
                    val array = org.json.JSONArray(trimmed)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val pkg = obj.optString("package").takeIf { it.isNotEmpty() } ?: continue
                        result[pkg] = when (obj.optString("mode").uppercase()) {
                            "LEAF_HACK" -> TargetMode.LEAF_HACK
                            "GENERATE" -> TargetMode.CERT_GEN
                            "SKIP" -> TargetMode.SKIP
                            else -> TargetMode.AUTO
                        }
                    }
                }
                return result
            }
            trimmed.lines().forEach { raw ->
                val t = raw.trim()
                if (t.isEmpty() || t.startsWith("#")) return@forEach
                when {
                    t.endsWith("?") -> result[t.dropLast(1).trim()] = TargetMode.LEAF_HACK
                    t.endsWith("!") -> result[t.dropLast(1).trim()] = TargetMode.CERT_GEN
                    t.endsWith("-") -> result[t.dropLast(1).trim()] = TargetMode.SKIP
                    else -> result[t] = TargetMode.AUTO
                }
            }
            return result
        }

        fun serializeTargetList(targets: Map<String, TargetMode>): String =
            targets.entries.joinToString("\n") { (pkg, mode) -> pkg + mode.symbol }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.ts_manage_target_apps)
    }

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = spoofComposeView(requireContext()).apply {
        setContent {
            SettingsTheme {
                TrickyStoreAppSettingsContent(context = requireContext())
            }
        }
    }
}

private data class TrickyAppState(
    val entry: AppListEntry,
    val mode: TargetMode,
)

@Composable
private fun TrickyStoreAppSettingsContent(context: Context) {
    val resolver = context.contentResolver
    val activityManager = remember { context.getSystemService(ActivityManager::class.java) }

    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateListOf<TrickyAppState>() }

    // Targets that are configured but not in the visible list: installed but filtered out
    // (Xposed managers, system apps while hidden) or defaults that aren't installed. They
    // are written back untouched on save.
    var hiddenTargets by remember { mutableStateOf<Map<String, TargetMode>>(emptyMap()) }
    // How many of hiddenTargets are installed; only those count as configured.
    var hiddenInstalledCount by remember { mutableStateOf(0) }

    fun saveTargets() {
        val visible = allApps.filter { it.entry.isSelected }
            .associate { it.entry.packageName to it.mode }
        Settings.Secure.putString(
            resolver,
            TrickyStoreAppSettings.TARGET_KEY,
            TrickyStoreAppSettings.serializeTargetList(hiddenTargets + visible),
        )
    }

    fun updateApp(pkg: String, transform: (TrickyAppState) -> TrickyAppState) {
        val i = allApps.indexOfFirst { it.entry.packageName == pkg }
        if (i >= 0) allApps[i] = transform(allApps[i])
    }

    LaunchedEffect(showSystemApps) {
        isLoading = true
        val (apps, hidden) = withContext(Dispatchers.IO) {
            // null means "never configured"; "" is a list the user cleared and is respected,
            // the same rule AxSpoofManager uses when it seeds the defaults.
            val stored = Settings.Secure.getString(resolver, TrickyStoreAppSettings.TARGET_KEY)
            if (stored == null) {
                Settings.Secure.putString(
                    resolver, TrickyStoreAppSettings.TARGET_KEY,
                    TrickyStoreService.DEFAULT_TARGET_LIST,
                )
            }
            val pm = context.packageManager
            val targetMap = TrickyStoreAppSettings.parseTargetList(
                stored ?: TrickyStoreService.DEFAULT_TARGET_LIST)

            // Drop targets that are no longer installed for this user; defaults stay.
            val cleaned = targetMap.filterKeys { pkg ->
                pkg in TrickyStoreAppSettings.DEFAULT_TARGETS || isInstalled(pm, pkg)
            }
            if (cleaned.size != targetMap.size) {
                Settings.Secure.putString(
                    resolver, TrickyStoreAppSettings.TARGET_KEY,
                    TrickyStoreAppSettings.serializeTargetList(cleaned),
                )
            }
            val targeted = cleaned.keys

            val listed = filterInstalledApps(
                pm = pm,
                showSystem = showSystemApps,
                targeted = targeted,
                extraFilter = { app ->
                    val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    when {
                        // The framework always skips Xposed managers (attesting through a
                        // hooked process breaks STRONG), so they are not offered as targets.
                        app.packageName in TrickyStoreService.XPOSED_PACKAGES -> false
                        // Targeted packages stay visible even if the suffix filter matches.
                        app.packageName in targeted -> true
                        else -> !(isSystem && EXCLUDED_SUFFIXES.any { app.packageName.contains(it) })
                    }
                },
            ).sortedWith(targetedFirstComparator(pm, targeted))

            val states = listed.map { app ->
                TrickyAppState(
                    entry = AppListEntry(
                        packageName = app.packageName,
                        label = pm.getApplicationLabel(app).toString(),
                        icon = runCatching { pm.getApplicationIcon(app) }.getOrNull(),
                        isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        isSelected = app.packageName in cleaned,
                    ),
                    mode = cleaned[app.packageName] ?: TargetMode.AUTO,
                )
            }
            val visiblePkgs = states.map { it.entry.packageName }.toSet()
            val hiddenMap = cleaned.filterKeys { it !in visiblePkgs }
            states to (hiddenMap to TrickyStoreAppSettings.installedTargets(pm, hiddenMap).size)
        }
        hiddenTargets = hidden.first
        hiddenInstalledCount = hidden.second
        allApps.clear()
        allApps.addAll(apps)
        isLoading = false
    }

    val filteredApps = remember(searchQuery, allApps.toList()) {
        val query = searchQuery.trim().lowercase()
        allApps.filter { state ->
            query.isEmpty() ||
                state.entry.label.lowercase().contains(query) ||
                state.entry.packageName.lowercase().contains(query)
        }
    }

    val activeCount = allApps.count { it.entry.isSelected } + hiddenInstalledCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SpoofDimens.ScreenInset),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SpoofingHeaderCard(
            title = stringResource(R.string.ts_manage_target_apps),
            subtitle = if (activeCount == 0) stringResource(R.string.ts_no_targets)
            else stringResource(R.string.ts_target_apps_count, activeCount),
        ) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = spoofHeaderIconTint(null),
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        AppPickerSearchField(query = searchQuery, onQueryChange = { searchQuery = it })
        Spacer(modifier = Modifier.height(8.dp))

        SpoofActionRow(
            listOf(
                SpoofAction(
                    text = stringResource(R.string.ts_system_apps),
                    icon = Icons.Rounded.PhoneAndroid,
                    selected = showSystemApps,
                    enabled = !isLoading,
                    onClick = { showSystemApps = !showSystemApps },
                ),
                SpoofAction(
                    text = stringResource(R.string.select_all),
                    icon = Icons.Rounded.DoneAll,
                    enabled = !isLoading,
                    onClick = {
                        filteredApps.forEach { s ->
                            updateApp(s.entry.packageName) {
                                it.copy(entry = it.entry.copy(isSelected = true))
                            }
                        }
                        saveTargets()
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SpoofActionRow(
            listOf(
                SpoofAction(
                    text = stringResource(R.string.ts_add_user_apps),
                    icon = Icons.Rounded.AddCircleOutline,
                    enabled = !isLoading,
                    onClick = {
                        var added = 0
                        allApps.indices.forEach { i ->
                            val s = allApps[i]
                            if (!s.entry.isSelected && !s.entry.isSystem) {
                                allApps[i] = s.copy(entry = s.entry.copy(isSelected = true))
                                added++
                            }
                        }
                        saveTargets()
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(
                                R.plurals.ts_added_installed_count, added, added),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ),
                SpoofAction(
                    text = stringResource(R.string.reset),
                    icon = Icons.Rounded.History,
                    enabled = !isLoading,
                    onClick = {
                        val defaults = TrickyStoreAppSettings.DEFAULT_TARGET_MAP
                        val before = (allApps.filter { it.entry.isSelected }
                            .map { it.entry.packageName } + hiddenTargets.keys).toSet()
                        allApps.indices.forEach { i ->
                            val pkg = allApps[i].entry.packageName
                            allApps[i] = allApps[i].copy(
                                entry = allApps[i].entry.copy(isSelected = pkg in defaults),
                                mode = defaults[pkg] ?: TargetMode.AUTO,
                            )
                        }
                        val visible = allApps.map { it.entry.packageName }.toSet()
                        hiddenTargets = defaults.filterKeys { it !in visible }
                        hiddenInstalledCount = TrickyStoreAppSettings
                            .installedTargets(context.packageManager, hiddenTargets).size
                        saveTargets()
                        // Restart whatever the reset added or removed.
                        val after = defaults.keys
                        killPackages(activityManager, (before - after) + (after - before))
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> SpoofingLoadingBox(modifier = Modifier.weight(1f))
            filteredApps.isEmpty() -> Column(Modifier.weight(1f)) {
                SpoofingEmptyState(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.ts_no_targets),
                    description = stringResource(R.string.ts_target_apps_empty_description),
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .toolbarNestedScroll(),
                verticalArrangement = Arrangement.spacedBy(SpoofDimens.RowGap),
                contentPadding = PaddingValues(bottom = 24.dp + spoofBottomInset()),
            ) {
                itemsIndexed(filteredApps, key = { _, s -> s.entry.packageName }) { index, state ->
                    AppPickerItem(
                        packageName = state.entry.packageName,
                        label = state.entry.label,
                        icon = state.entry.icon,
                        isSystem = state.entry.isSystem,
                        checked = state.entry.isSelected,
                        shape = SpoofShapes.forIndex(index, filteredApps.size),
                        onToggle = { enabled ->
                            updateApp(state.entry.packageName) {
                                it.copy(entry = it.entry.copy(isSelected = enabled))
                            }
                            saveTargets()
                            stopPackage(activityManager, state.entry.packageName)
                        },
                        extraContent = {
                            AnimatedVisibility(visible = state.entry.isSelected) {
                                TargetModeSelector(
                                    mode = state.mode,
                                    onModeChange = { mode ->
                                        updateApp(state.entry.packageName) { it.copy(mode = mode) }
                                        saveTargets()
                                        stopPackage(activityManager, state.entry.packageName)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetModeSelector(mode: TargetMode, onModeChange: (TargetMode) -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TargetMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onModeChange(entry) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = TargetMode.entries.size,
                    ),
                    label = {
                        Text(
                            text = stringResource(
                                when (entry) {
                                    TargetMode.AUTO -> R.string.ts_mode_auto
                                    TargetMode.LEAF_HACK -> R.string.ts_mode_leaf
                                    TargetMode.CERT_GEN -> R.string.ts_mode_cert
                                    TargetMode.SKIP -> R.string.ts_mode_skip
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
    pm.getPackageInfo(pkg, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
