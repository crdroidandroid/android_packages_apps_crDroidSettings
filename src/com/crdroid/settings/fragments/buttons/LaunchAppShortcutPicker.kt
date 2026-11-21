/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.buttons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.DisplayMetrics
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.widget.RadioButtonPickerFragment
import com.android.settingslib.widget.CandidateInfo

class LaunchAppShortcutPicker : RadioButtonPickerFragment() {

    private var actionSetting: String = ""
    private var app: ComponentName? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val args = arguments
        actionSetting = args?.getString(LaunchAppAction.EXTRA_ACTION_SETTING) ?: ""
        app = args?.getParcelable(LaunchAppAction.EXTRA_COMPONENT, ComponentName::class.java)
    }

    override fun getPreferenceScreenResId() = R.xml.placeholder_preference_screen

    override fun getMetricsCategory() = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    override fun getCandidates(): List<CandidateInfo> {
        val context = context ?: return emptyList()
        val app = app ?: return emptyList()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return emptyList()
        val appIcon = launcherApps.getActivityList(app.packageName, Process.myUserHandle())
            .firstOrNull { it.componentName == app }
            ?.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE)

        val candidates = mutableListOf<CandidateInfo>(
            Candidate(KEY_APP, context.getString(R.string.hardware_keys_action_launch_app),
                appIcon))
        LaunchAppAction.queryShortcuts(context, app.packageName).forEach {
            candidates.add(ShortcutCandidate(launcherApps, it))
        }
        return candidates
    }

    override fun getDefaultKey(): String? {
        val context = requireContext()
        if (LaunchAppAction.getApp(context, actionSetting) != app) return null
        val shortcutId = LaunchAppAction.getShortcut(context, actionSetting) ?: return KEY_APP
        return SHORTCUT_PREFIX + shortcutId
    }

    override fun setDefaultKey(key: String): Boolean {
        val app = app ?: return false
        if (actionSetting.isEmpty()) return false
        val shortcutId = if (key.startsWith(SHORTCUT_PREFIX)) {
            key.substring(SHORTCUT_PREFIX.length)
        } else {
            null
        }
        LaunchAppAction.save(requireContext(), actionSetting, app, shortcutId)
        return true
    }

    private open class Candidate(
        private val key: String,
        private val label: CharSequence?,
        private val icon: Drawable?,
    ) : CandidateInfo(true) {
        override fun loadLabel(): CharSequence? = label

        override fun loadIcon(): Drawable? = icon

        override fun getKey(): String = key
    }

    private class ShortcutCandidate(launcherApps: LauncherApps, info: ShortcutInfo) :
        Candidate(
            SHORTCUT_PREFIX + info.id,
            info.shortLabel ?: info.longLabel ?: info.id,
            launcherApps.getShortcutIconDrawable(info, DisplayMetrics.DENSITY_DEVICE_STABLE),
        )

    companion object {
        private const val KEY_APP = "app"
        private const val SHORTCUT_PREFIX = "shortcut:"
    }
}
