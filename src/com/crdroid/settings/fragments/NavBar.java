/*
 * Copyright (C) 2016-2017 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.development.DevelopmentSettings;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;

public class NavBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "NavBar";

    private static final String NAVBAR_VISIBILITY = "navbar_visibility";

    private SwitchPreference mNavbarVisibility;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_navigation);

        ContentResolver resolver = getActivity().getContentResolver();

        mNavbarVisibility = (SwitchPreference) findPreference(NAVBAR_VISIBILITY);

        boolean showing = Settings.Secure.getInt(resolver,
                Settings.Secure.NAVIGATION_BAR_ENABLED,
                deviceNavigationDefault(getActivity())) != 0;
        mNavbarVisibility.setChecked(showing);
        mNavbarVisibility.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference.equals(mNavbarVisibility)) {
            boolean showing = ((Boolean)newValue);
            Settings.Secure.putInt(resolver, Settings.Secure.NAVIGATION_BAR_ENABLED,
                    showing ? 1 : 0);
            mNavbarVisibility.setChecked(showing);
            return true;
        }
        return false;
    }

    public static int deviceNavigationDefault(Context context) {
        final boolean showByDefault = context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);

        if (showByDefault) {
            return 1;
        }

        return 0;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putInt(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_NAVBAR, 0);
        Settings.System.putInt(resolver,
                Settings.System.PIXEL_NAV_ANIMATION, 1);
        Settings.Secure.putInt(resolver,
                Settings.Secure.NAVIGATION_BAR_ENABLED, deviceNavigationDefault(mContext));
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
