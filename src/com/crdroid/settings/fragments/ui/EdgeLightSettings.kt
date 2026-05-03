/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings

import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

class EdgeLightSettings : SettingsPreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.edge_light_settings)
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        @JvmStatic
        fun reset(context: Context) {
            val resolver = context.contentResolver
            Settings.System.putIntForUser(resolver,
                    Settings.System.EDGE_LIGHT_ENABLED, 0, UserHandle.USER_CURRENT)
            Settings.System.putStringForUser(resolver,
                    Settings.System.EDGE_LIGHT_COLOR_MODE, "accent", UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                    Settings.System.EDGE_LIGHT_CUSTOM_COLOR, Color.WHITE, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                    Settings.System.EDGE_LIGHT_PULSE_COUNT, 1, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                    Settings.System.EDGE_LIGHT_STROKE_WIDTH, 8, UserHandle.USER_CURRENT)
            Settings.System.putStringForUser(resolver,
                    Settings.System.EDGE_LIGHT_STYLE, "default", UserHandle.USER_CURRENT)
            Settings.System.putStringForUser(resolver,
                    Settings.System.EDGE_LIGHT_ANIMATION_EFFECT, "none", UserHandle.USER_CURRENT)
        }
    }
}
