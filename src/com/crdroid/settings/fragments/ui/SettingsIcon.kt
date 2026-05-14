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

class SettingsIcon : SettingsPreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings_icons)
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        @JvmStatic
        fun reset(context: Context) {
            val resolver = context.contentResolver
            Settings.System.putIntForUser(resolver,
                    "settings_icon_style", 0, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                    "settings_icon_random_colors", 0, UserHandle.USER_CURRENT)
            Settings.System.putIntForUser(resolver,
                    "settings_icon_corner_style", 0, UserHandle.USER_CURRENT)
        }
    }
}
