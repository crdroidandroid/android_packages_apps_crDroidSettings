/*
 * Copyright (C) 2017-2019 crDroid Android Project
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
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import lineageos.preference.LineageSecureSettingSwitchPreference;
import lineageos.providers.LineageSettings;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

public class NetworkTrafficSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "NetworkTrafficSettings";

    private ListPreference mNetTrafficLocation;
    private ListPreference mNetTrafficMode;
    private CustomSeekBarPreference mNetTrafficAutohide;
    private ListPreference mNetTrafficUnits;
    private LineageSecureSettingSwitchPreference mNetTrafficShowUnits;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.network_traffic_settings);
        final ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficLocation = (ListPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_LOCATION);
        mNetTrafficLocation.setOnPreferenceChangeListener(this);

        mNetTrafficMode = (ListPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_MODE);

        mNetTrafficAutohide = (CustomSeekBarPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE);
        int mAutohideThreshold = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT);
        mNetTrafficAutohide.setValue(mAutohideThreshold);
        mNetTrafficAutohide.setOnPreferenceChangeListener(this);

        mNetTrafficUnits = (ListPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_UNITS);
        int units = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, /* Mbps */ 1, UserHandle.USER_CURRENT);
        mNetTrafficUnits.setValue(String.valueOf(units));

        mNetTrafficShowUnits = (LineageSecureSettingSwitchPreference)
                findPreference(LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS);

        int location = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_LOCATION, 0, UserHandle.USER_CURRENT);
        updateEnabledStates(location);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mNetTrafficLocation) {
            int location = Integer.valueOf((String) newValue);
            updateEnabledStates(location);
            return true;
        } else if (preference == mNetTrafficAutohide) {
            int value = (Integer) newValue;
            LineageSettings.Secure.putIntForUser(getActivity().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    private void updateEnabledStates(int location) {
        final boolean enabled = location != 0;
        mNetTrafficMode.setEnabled(enabled);
        mNetTrafficAutohide.setEnabled(enabled);
        mNetTrafficUnits.setEnabled(enabled);
        mNetTrafficShowUnits.setEnabled(enabled);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_LOCATION, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, 1, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
