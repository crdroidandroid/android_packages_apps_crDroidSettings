/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.buttons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.util.DisplayMetrics
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.android.settings.widget.RadioButtonPickerFragment
import com.android.settingslib.widget.CandidateInfo
import com.android.settingslib.widget.SelectorWithWidgetPreference

class LaunchAppPicker : RadioButtonPickerFragment() {

    private var actionSetting: String = ""
    private var appsWithShortcuts: Set<String> = emptySet()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        actionSetting = arguments?.getString(LaunchAppAction.EXTRA_ACTION_SETTING) ?: ""
    }

    override fun onResume() {
        super.onResume()
        // Reflect a selection made in the shortcut picker.
        refreshSelection()
    }

    override fun getPreferenceScreenResId() = R.xml.placeholder_preference_screen

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    override fun getCandidates(): List<CandidateInfo> {
        val context = context ?: return emptyList()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return emptyList()
        appsWithShortcuts = LaunchAppAction.queryShortcuts(context, null)
            .map { it.`package` }
            .toSet()
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .sortedBy { it.label.toString().lowercase() }
            .map { AppCandidate(it) }
    }

    override fun getDefaultKey(): String? =
        LaunchAppAction.getApp(requireContext(), actionSetting)?.flattenToString()

    override fun setDefaultKey(key: String): Boolean {
        if (actionSetting.isEmpty()) return false
        val app = ComponentName.unflattenFromString(key) ?: return false
        LaunchAppAction.save(requireContext(), actionSetting, app, null)
        return true
    }

    override fun onSelectionPerformed(success: Boolean) {
        if (success) refreshSelection()
    }

    /** Checks the selected app and shows what it opens as its summary. */
    private fun refreshSelection() {
        val screen = preferenceScreen ?: return
        val selectedKey = defaultKey
        updateCheckedState(selectedKey)
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            pref.summary = if (pref.key == selectedKey) {
                LaunchAppAction.getSummary(requireContext(), actionSetting)
            } else {
                null
            }
        }
    }

    override fun bindPreferenceExtra(pref: SelectorWithWidgetPreference, key: String,
            info: CandidateInfo, defaultKey: String?, systemDefaultKey: String?) {
        val app = ComponentName.unflattenFromString(key) ?: return
        if (key == defaultKey) {
            pref.summary = LaunchAppAction.getSummary(requireContext(), actionSetting)
        }
        if (app.packageName in appsWithShortcuts) {
            pref.setExtraWidgetContentDescription(
                getString(R.string.launch_app_shortcuts_a11y, info.loadLabel()))
            pref.setExtraWidgetOnClickListener {
                SubSettingLauncher(requireContext())
                    .setDestination(LaunchAppShortcutPicker::class.java.name)
                    .setArguments(Bundle().apply {
                        putString(LaunchAppAction.EXTRA_ACTION_SETTING, actionSetting)
                        putParcelable(LaunchAppAction.EXTRA_COMPONENT, app)
                    })
                    .setTitleText(info.loadLabel())
                    .setSourceMetricsCategory(metricsCategory)
                    .launch()
            }
        }
    }

    private class AppCandidate(private val info: LauncherActivityInfo) : CandidateInfo(true) {
        override fun loadLabel(): CharSequence = info.label

        override fun loadIcon(): Drawable = info.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE)

        override fun getKey(): String = info.componentName.flattenToString()
    }
}
