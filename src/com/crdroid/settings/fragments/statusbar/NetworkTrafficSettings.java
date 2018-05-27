/*
 * Copyright (C) 2017-2018 crDroid Android Project
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

package com.crdroid.settings.fragments.statusbar;

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.preference.DropDownPreference;
import android.support.v7.preference.Preference;

import lineageos.preference.LineageSecureSettingSwitchPreference;
import lineageos.providers.LineageSettings;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.R;

public class NetworkTrafficSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "NetworkTrafficSettings";

    private DropDownPreference mNetTrafficMode;
    private LineageSecureSettingSwitchPreference mNetTrafficAutohide;
    private DropDownPreference mNetTrafficUnits;
    private LineageSecureSettingSwitchPreference mNetTrafficShowUnits;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.network_traffic_settings);
        final ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficMode = (DropDownPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_MODE);
        int mode = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0, UserHandle.USER_CURRENT);
        mNetTrafficMode.setValue(String.valueOf(mode));
        mNetTrafficMode.setOnPreferenceChangeListener(this);

        mNetTrafficAutohide = (LineageSecureSettingSwitchPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE);

        mNetTrafficUnits = (DropDownPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_UNITS);
        int units = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, /* Mbps */ 1, UserHandle.USER_CURRENT);
        mNetTrafficUnits.setValue(String.valueOf(units));
        mNetTrafficUnits.setOnPreferenceChangeListener(this);

        mNetTrafficShowUnits = (LineageSecureSettingSwitchPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS);

        updateEnabledStates(mode);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mNetTrafficMode) {
            int mode = Integer.valueOf((String) newValue);
            LineageSettings.Secure.putIntForUser(getActivity().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE, mode, UserHandle.USER_CURRENT);
            updateEnabledStates(mode);
            return true;
        } else if (preference == mNetTrafficUnits) {
            int units = Integer.valueOf((String) newValue);
            LineageSettings.Secure.putIntForUser(getActivity().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, units, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    private void updateEnabledStates(int mode) {
        final boolean enabled = mode != 0;
        mNetTrafficAutohide.setEnabled(enabled);
        mNetTrafficUnits.setEnabled(enabled);
        mNetTrafficShowUnits.setEnabled(enabled);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
