/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.crdroid.settings.fragments.statusbar

import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.Preference
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

class ClockChipSettings : SettingsPreferenceFragment(), Preference.OnPreferenceChangeListener {

    private val gradientPrefs = mutableListOf<Preference>()
    private val customColorPrefs = mutableListOf<Preference>()
    private var chipStyle = 0
    private var colorMode = COLOR_MODE_AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.clock_chip_settings)

        findPreference<Preference>(KEY_CHIP_STYLE)?.onPreferenceChangeListener = this
        findPreference<Preference>(KEY_GRADIENT_COLOR_MODE)?.onPreferenceChangeListener = this
        GRADIENT_KEYS.mapNotNullTo(gradientPrefs) { findPreference<Preference>(it) }
        CUSTOM_COLOR_KEYS.mapNotNullTo(customColorPrefs) { findPreference<Preference>(it) }

        updateGradientSettings()
    }

    override fun onResume() {
        super.onResume()
        updateGradientSettings()
    }

    private fun updateGradientSettings() {
        val resolver = requireContext().contentResolver
        chipStyle = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_CLOCK_CHIP, 0, UserHandle.USER_CURRENT)
        colorMode = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_COLOR_MODE,
            COLOR_MODE_AUTO, UserHandle.USER_CURRENT)
        updateGradientPrefs()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            KEY_CHIP_STYLE -> (newValue as? Int)?.let { chipStyle = it }
            // List preferences hand over the entry value as a String.
            KEY_GRADIENT_COLOR_MODE -> newValue?.toString()?.toIntOrNull()?.let { colorMode = it }
            else -> return false
        }
        updateGradientPrefs()
        return true
    }

    private fun updateGradientPrefs() {
        val isGradient = chipStyle == CHIP_STYLE_CUSTOM_GRADIENT
        val isCustomColor = colorMode == COLOR_MODE_CUSTOM
        gradientPrefs.forEach { it.isVisible = isGradient }
        customColorPrefs.forEach {
            it.isVisible = isGradient
            it.isEnabled = isCustomColor
        }
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private const val KEY_CHIP_STYLE = "statusbar_clock_chip"
        private const val KEY_GRADIENT_COLOR_MODE = "statusbar_clock_chip_gradient_color_mode"

        /** Shown whenever the custom gradient style is selected. */
        private val GRADIENT_KEYS = arrayOf(
            KEY_GRADIENT_COLOR_MODE,
            "statusbar_clock_chip_gradient_angle",
            "statusbar_clock_chip_gradient_mask_text",
        )

        /** Shown only for the custom gradient style in custom colour mode. */
        private val CUSTOM_COLOR_KEYS = arrayOf(
            "statusbar_clock_chip_gradient_start_color",
            "statusbar_clock_chip_gradient_end_color",
        )

        /** Must match HomeStatusBarViewBinder's CHIP_STYLE_CUSTOM_GRADIENT in SystemUI. */
        const val CHIP_STYLE_CUSTOM_GRADIENT = 13

        const val DEFAULT_GRADIENT_START_COLOR = 0xFFFF6B6B.toInt()
        const val DEFAULT_GRADIENT_END_COLOR = 0xFF4ECDC4.toInt()
        const val DEFAULT_GRADIENT_ANGLE = 0
        const val GRADIENT_TEXT_STYLE_NORMAL = 0
        const val GRADIENT_TEXT_STYLE_MASK = 1
        const val COLOR_MODE_AUTO = 0
        const val COLOR_MODE_CUSTOM = 1

        @JvmStatic
        fun reset(context: Context) {
            val resolver = context.contentResolver
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP, 0, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_START_COLOR,
                DEFAULT_GRADIENT_START_COLOR, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_END_COLOR,
                DEFAULT_GRADIENT_END_COLOR, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_ANGLE,
                DEFAULT_GRADIENT_ANGLE, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_MASK_TEXT,
                GRADIENT_TEXT_STYLE_NORMAL, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_COLOR_MODE,
                COLOR_MODE_AUTO, UserHandle.USER_CURRENT)
        }
    }
}
