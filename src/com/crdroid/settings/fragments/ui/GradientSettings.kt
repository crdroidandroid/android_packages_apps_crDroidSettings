/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.crdroid.settings.preferences.SystemSettingListPreference

class GradientSettings : SettingsPreferenceFragment() {

    private lateinit var colorModePref: SystemSettingListPreference
    private lateinit var startColorPref: Preference
    private lateinit var endColorPref: Preference

    private val resolver: ContentResolver
        get() = requireContext().contentResolver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.gradient_settings)

        colorModePref = findPreference(KEY_CUSTOM_GRADIENT_COLOR_MODE)!!
        startColorPref = findPreference(KEY_CUSTOM_GRADIENT_START_COLOR)!!
        endColorPref = findPreference(KEY_CUSTOM_GRADIENT_END_COLOR)!!

        colorModePref.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            val enabled = (newValue as? String)?.toIntOrNull() == 1
            updateCustomColorPrefs(enabled)
            true
        }

        syncCustomColorPrefsFromSetting()
    }

    override fun onResume() {
        super.onResume()
        syncCustomColorPrefsFromSetting()
    }

    private fun syncCustomColorPrefsFromSetting() {
        val mode = Settings.System.getIntForUser(
            resolver, Settings.System.CUSTOM_GRADIENT_COLOR_MODE, 0,
            UserHandle.USER_CURRENT
        )
        updateCustomColorPrefs(mode == 1)
    }

    private fun updateCustomColorPrefs(enabled: Boolean) {
        startColorPref.isEnabled = enabled
        endColorPref.isEnabled = enabled
    }

    override fun getMetricsCategory(): Int {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS
    }

    companion object {
        private const val KEY_CUSTOM_GRADIENT_COLOR_MODE = "custom_gradient_color_mode"
        private const val KEY_CUSTOM_GRADIENT_START_COLOR = "custom_gradient_start_color"
        private const val KEY_CUSTOM_GRADIENT_END_COLOR = "custom_gradient_end_color"

        @JvmStatic
        fun reset(context: Context) {
            val resolver = context.contentResolver
            Settings.System.putIntForUser(
                resolver, Settings.System.QS_TILE_GRADIENT, 0,
                UserHandle.USER_CURRENT
            )
            Settings.System.putIntForUser(
                resolver, Settings.System.QS_BRIGHTNESS_SLIDER_GRADIENT, 0,
                UserHandle.USER_CURRENT
            )
            Settings.System.putIntForUser(
                resolver, Settings.System.VOLUME_SLIDER_GRADIENT, 0,
                UserHandle.USER_CURRENT
            )
            Settings.System.putIntForUser(
                resolver, Settings.System.CUSTOM_GRADIENT_COLOR_MODE, 0,
                UserHandle.USER_CURRENT
            )
            Settings.System.putIntForUser(
                resolver, Settings.System.CUSTOM_GRADIENT_START_COLOR, 0,
                UserHandle.USER_CURRENT
            )
            Settings.System.putIntForUser(
                resolver, Settings.System.CUSTOM_GRADIENT_END_COLOR, 0,
                UserHandle.USER_CURRENT
            )
        }
    }
}
