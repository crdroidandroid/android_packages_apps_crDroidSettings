/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.misc

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.security.trickystore.TrickyStoreService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TargetMode(val symbol: String) {
    AUTO(""),
    LEAF_HACK("?"),
    CERT_GEN("!"),
}

// AppEntry kept for external references; internally we use AppListEntry +
// a parallel targetMode list. The targetMode for each package is stored in
// a separate map so we don't duplicate the whole AppListEntry on every mode change.
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
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
         * Single source of truth for the defaults. Parsed from the framework's
         * [TrickyStoreService.DEFAULT_TARGET_LIST] so the app's "auto select" and the
         * backend's automatic seed-on-keybox-load can never drift apart. Modes come
         * straight from the suffixes (`?` leaf, `!` cert-gen, `-` skip, plain auto).
         */
        val DEFAULT_TARGET_MAP: Map<String, TargetMode> =
            parseTargetList(TrickyStoreService.DEFAULT_TARGET_LIST)

        /** Packages selected by "auto select" (every default, whatever its mode). */
        val DEFAULT_TARGETS: Set<String> = DEFAULT_TARGET_MAP.keys

        /** Backwards-compatible alias: only the non-AUTO defaults with their modes. */
        val DEFAULT_TARGET_MODES: Map<String, TargetMode> =
            DEFAULT_TARGET_MAP.filterValues { it != TargetMode.AUTO }

        /**
         * Parses a SPOOF_TRICKYSTORE_TARGET-format list ("pkg", "pkg?", "pkg!",
         * "pkg-", with "#" comments) into a package → mode map. Shared by the
         * defaults above and by the per-screen loader so both read suffixes the
         * same way.
         */
        fun parseTargetList(list: String?): Map<String, TargetMode> {
            val result = LinkedHashMap<String, TargetMode>()
            if (list.isNullOrEmpty()) return result
            list.lines().forEach { raw ->
                val t = raw.trim()
                if (t.isEmpty() || t.startsWith("#")) return@forEach
                when {
                    t.endsWith("?") -> result[t.dropLast(1).trim()] = TargetMode.LEAF_HACK
                    t.endsWith("!") -> result[t.dropLast(1).trim()] = TargetMode.CERT_GEN
                    t.endsWith("-") -> return@forEach
                    else -> result[t] = TargetMode.AUTO
                }
            }
            return result
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.tricky_store_title)
    }

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.VIEW_UNKNOWN

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SettingsTheme {
                TrickyStoreAppSettingsContent(context = requireContext())
            }
        }
    }
}

// Internal model that pairs AppListEntry (isSelected = isInTarget) with a TargetMode.
// We keep a parallel mutableStateList for targetModes so that changing a mode
// does not recreate the full AppListEntry list.
private data class TrickyAppState(
    val entry: AppListEntry,
    val mode: TargetMode,
)

@Composable
private fun TrickyStoreAppSettingsContent(
    context: android.content.Context,
) {
    val scope = rememberCoroutineScope()
    val activityManager = remember {
        context.getSystemService(ActivityManager::class.java)
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateListOf<TrickyAppState>() }

    fun loadTargetMap(): Map<String, TargetMode> =
        TrickyStoreAppSettings.parseTargetList(
            Settings.Secure.getString(
                context.contentResolver, TrickyStoreAppSettings.TARGET_KEY,
            ),
        )

    fun saveTargets() {
        val lines = allApps
            .filter { it.entry.isSelected }
            .map { it.entry.packageName + it.mode.symbol }
        Settings.Secure.putString(
            context.contentResolver,
            TrickyStoreAppSettings.TARGET_KEY,
            lines.joinToString("\n"),
        )
    }

    LaunchedEffect(showSystemApps) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val targetMap = loadTargetMap()
            val targeted = targetMap.keys.toSet()

            val installed = filterInstalledApps(
                pm = pm,
                showSystem = showSystemApps,
                targeted = targeted,
                extraFilter = { app ->
                    val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val isSuffixExcluded = EXCLUDED_SUFFIXES.any { app.packageName.contains(it) }
                    !(isSystem && isSuffixExcluded)
                },
            )
                .sortedWith(targetedFirstComparator(pm, targeted))
                .map { app ->
                    TrickyAppState(
                        entry = AppListEntry(
                            packageName = app.packageName,
                            label = pm.getApplicationLabel(app).toString(),
                            icon = runCatching { pm.getApplicationIcon(app) }.getOrNull(),
                            isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            isSelected = targetMap.containsKey(app.packageName),
                        ),
                        mode = targetMap[app.packageName] ?: TargetMode.AUTO,
                    )
                }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(installed)
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, allApps.toList()) {
        val query = searchQuery.lowercase()
        allApps.filter { state ->
            query.isEmpty() ||
                state.entry.label.lowercase().contains(query) ||
                state.entry.packageName.lowercase().contains(query)
        }
    }

    val activeCount = allApps.count { it.entry.isSelected }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // No switch — TrickyStore is always active
            SpoofingHeaderCard(
                title = stringResource(R.string.ts_manage_target_apps),
                subtitle = if (activeCount == 0)
                    stringResource(R.string.ts_no_targets)
                else
                    stringResource(R.string.ts_target_apps_count, activeCount),
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppPickerSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text(stringResource(R.string.show_system_apps)) },
                    leadingIcon = if (showSystemApps) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        filteredApps.forEach { s ->
                            val i = allApps.indexOfFirst {
                                it.entry.packageName == s.entry.packageName }
                            if (i >= 0 && !allApps[i].entry.isSelected)
                                allApps[i] = allApps[i].copy(
                                    entry = allApps[i].entry.copy(isSelected = true))
                        }
                        scope.launch(Dispatchers.IO) { saveTargets() }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        stringResource(R.string.select_all),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                OutlinedButton(
                    onClick = {
                        allApps.indices.forEach { i ->
                            val pkg = allApps[i].entry.packageName
                            val defMode = TrickyStoreAppSettings.DEFAULT_TARGET_MAP[pkg]
                            allApps[i] = allApps[i].copy(
                                entry = allApps[i].entry.copy(
                                    isSelected = defMode != null,
                                ),
                                mode = defMode ?: TargetMode.AUTO,
                            )
                        }
                        scope.launch(Dispatchers.IO) {
                            saveTargets()
                            killPackages(
                                activityManager,
                                TrickyStoreAppSettings.DEFAULT_TARGETS,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        stringResource(R.string.auto_select),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                SpoofingLoadingBox(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredApps, key = { it.entry.packageName }) { state ->
                        AppPickerItem(
                            packageName = state.entry.packageName,
                            label = state.entry.label,
                            icon = state.entry.icon,
                            isSystem = state.entry.isSystem,
                            checked = state.entry.isSelected,
                            onToggle = { nowEnabled ->
                                val i = allApps.indexOfFirst {
                                    it.entry.packageName == state.entry.packageName }
                                if (i >= 0) {
                                    allApps[i] = allApps[i].copy(
                                        entry = allApps[i].entry.copy(isSelected = nowEnabled),
                                    )
                                    scope.launch(Dispatchers.IO) { saveTargets() }
                                }
                            },
                            extraContent = if (state.entry.isSelected) {
                                {
                                    AnimatedVisibility(
                                        visible = state.entry.isSelected,
                                        enter = fadeIn(
                                            MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                                expandVertically(
                                                    MaterialTheme.motionScheme.defaultSpatialSpec()),
                                        exit = fadeOut(
                                            MaterialTheme.motionScheme.fastEffectsSpec()) +
                                               shrinkVertically(
                                                   MaterialTheme.motionScheme.fastSpatialSpec()),
                                    ) {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SingleChoiceSegmentedButtonRow(
                                                modifier = Modifier.fillMaxWidth()) {
                                                TargetMode.entries.forEachIndexed { index, mode ->
                                                    SegmentedButton(
                                                        selected = state.mode == mode,
                                                        onClick = {
                                                            val i = allApps.indexOfFirst {
                                                                it.entry.packageName ==
                                                                    state.entry.packageName }
                                                            if (i >= 0) {
                                                                allApps[i] = allApps[i].copy(
                                                                    mode = mode)
                                                                scope.launch(Dispatchers.IO) {
                                                                    saveTargets() }
                                                            }
                                                        },
                                                        shape = SegmentedButtonDefaults.itemShape(
                                                            index = index,
                                                            count = TargetMode.entries.size,
                                                        ),
                                                        label = {
                                                            Text(
                                                                text = when (mode) {
                                                                    TargetMode.AUTO ->
                                                                        stringResource(
                                                                            R.string.ts_mode_auto)
                                                                    TargetMode.LEAF_HACK ->
                                                                        stringResource(
                                                                            R.string.ts_mode_leaf)
                                                                    TargetMode.CERT_GEN ->
                                                                        stringResource(
                                                                            R.string.ts_mode_cert)
                                                                },
                                                                style = MaterialTheme.typography
                                                                    .labelSmall,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else null,
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}
