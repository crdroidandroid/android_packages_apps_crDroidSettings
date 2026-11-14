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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.clock_chip_settings)

        findPreference<Preference>(KEY_CHIP_STYLE)?.onPreferenceChangeListener = this
        GRADIENT_KEYS.mapNotNullTo(gradientPrefs) { findPreference<Preference>(it) }

        val currentStyle = Settings.System.getIntForUser(
            requireContext().contentResolver,
            Settings.System.STATUSBAR_CLOCK_CHIP,
            0,
            UserHandle.USER_CURRENT,
        )
        updateGradientPrefsVisibility(currentStyle)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference.key == KEY_CHIP_STYLE) {
            (newValue as? Int)?.let(::updateGradientPrefsVisibility)
            return true
        }
        return false
    }

    private fun updateGradientPrefsVisibility(chipStyle: Int) {
        val isGradient = chipStyle == CHIP_STYLE_CUSTOM_GRADIENT
        gradientPrefs.forEach { it.isVisible = isGradient }
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private const val KEY_CHIP_STYLE = "statusbar_clock_chip"

        private val GRADIENT_KEYS = arrayOf(
            "statusbar_clock_chip_gradient_start_color",
            "statusbar_clock_chip_gradient_end_color",
            "statusbar_clock_chip_gradient_angle",
            "statusbar_clock_chip_gradient_mask_text",
        )

        /** Must match HomeStatusBarViewBinder's CHIP_STYLE_CUSTOM_GRADIENT in SystemUI. */
        const val CHIP_STYLE_CUSTOM_GRADIENT = 13

        const val DEFAULT_GRADIENT_START_COLOR = 0xFFFF6B6B.toInt()
        const val DEFAULT_GRADIENT_END_COLOR = 0xFF4ECDC4.toInt()
        const val DEFAULT_GRADIENT_ANGLE = 0
        const val GRADIENT_TEXT_STYLE_NORMAL = 0
        const val GRADIENT_TEXT_STYLE_MASK = 1

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
        }
    }
}
