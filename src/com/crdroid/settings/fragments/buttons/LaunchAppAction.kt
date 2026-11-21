/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.buttons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import lineageos.providers.LineageSettings
import org.lineageos.internal.util.DeviceKeysConstants.Action
import org.lineageos.internal.util.DeviceKeysConstants.getLaunchAppSetting
import org.lineageos.internal.util.DeviceKeysConstants.getLaunchShortcutSetting

object LaunchAppAction {
    private const val TAG = "LaunchAppAction"

    const val EXTRA_ACTION_SETTING = "launch_app_action_setting"
    const val EXTRA_COMPONENT = "launch_app_component"

    /** The list preference value of the "Open app" action. */
    @JvmField val VALUE: String = Action.LAUNCH_APP.ordinal.toString()

    @JvmStatic fun isLaunchApp(value: Any?): Boolean = value?.toString() == VALUE

    /** Opens the app picker that sets [actionSetting] to "Open app" once an app is picked. */
    @JvmStatic
    fun openPicker(context: Context, actionSetting: String, title: CharSequence?,
            sourceMetricsCategory: Int) {
        SubSettingLauncher(context)
            .setDestination(LaunchAppPicker::class.java.name)
            .setArguments(Bundle().apply { putString(EXTRA_ACTION_SETTING, actionSetting) })
            .setTitleText(title ?: context.getString(R.string.hardware_keys_action_launch_app))
            .setSourceMetricsCategory(sourceMetricsCategory)
            .launch()
    }

    @JvmStatic
    fun getApp(context: Context, actionSetting: String): ComponentName? {
        val app = LineageSettings.System.getStringForUser(context.contentResolver,
            getLaunchAppSetting(actionSetting), UserHandle.USER_CURRENT)
        return if (app.isNullOrEmpty()) null else ComponentName.unflattenFromString(app)
    }

    @JvmStatic
    fun getShortcut(context: Context, actionSetting: String): String? {
        val shortcut = LineageSettings.System.getStringForUser(context.contentResolver,
            getLaunchShortcutSetting(actionSetting), UserHandle.USER_CURRENT)
        return if (shortcut.isNullOrEmpty()) null else shortcut
    }

    /** Stores the app (and optional shortcut) and switches [actionSetting] to "Open app". */
    @JvmStatic
    fun save(context: Context, actionSetting: String, app: ComponentName, shortcutId: String?) {
        val resolver = context.contentResolver
        LineageSettings.System.putStringForUser(resolver, getLaunchAppSetting(actionSetting),
            app.flattenToString(), UserHandle.USER_CURRENT)
        LineageSettings.System.putStringForUser(resolver, getLaunchShortcutSetting(actionSetting),
            shortcutId ?: "", UserHandle.USER_CURRENT)
        LineageSettings.System.putIntForUser(resolver, actionSetting,
            Action.LAUNCH_APP.ordinal, UserHandle.USER_CURRENT)
    }

    /** Label of the app (and shortcut) [actionSetting] opens, for list summaries. */
    @JvmStatic
    fun getSummary(context: Context, actionSetting: String): CharSequence {
        val app = getApp(context, actionSetting)
            ?: return context.getString(R.string.launch_app_no_selection)
        val pm = context.packageManager
        val appLabel = try {
            pm.getActivityInfo(app, 0).loadLabel(pm)
        } catch (e: PackageManager.NameNotFoundException) {
            return context.getString(R.string.launch_app_not_installed)
        }
        val shortcutId = getShortcut(context, actionSetting)
            ?: return context.getString(R.string.launch_app_summary, appLabel)
        val shortcutLabel = queryShortcuts(context, app.packageName)
            .firstOrNull { it.id == shortcutId }
            ?.let { it.shortLabel ?: it.longLabel }
            ?: return context.getString(R.string.launch_app_summary, appLabel)
        return context.getString(R.string.launch_app_shortcut_summary, appLabel, shortcutLabel)
    }

    /** Enabled dynamic and manifest shortcuts, of [packageName] or of all apps if null. */
    @JvmStatic
    fun queryShortcuts(context: Context, packageName: String?): List<ShortcutInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST)
        if (packageName != null) query.setPackage(packageName)
        return try {
            launcherApps.getShortcuts(query, Process.myUserHandle())
                ?.filter { it.isEnabled }
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query shortcuts", e)
            emptyList()
        }
    }
}
