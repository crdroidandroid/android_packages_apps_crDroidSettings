/*
 * Copyright (C) 2024 Yet Another AOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.fragments.sound;

import android.content.ContentResolver;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.CustomSeekBarPreference;

public class VolumeSteps extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "VolumeSteps";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.volume_steps_settings);

        ContentResolver resolver = getActivity().getContentResolver();
        PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference pref = screen.getPreference(i);
            if (!(pref instanceof CustomSeekBarPreference))
                continue;
            String key = pref.getKey();
            final int def = Settings.System.getIntForUser(resolver, "default_" + key, 15, UserHandle.USER_CURRENT);
            final int value = Settings.System.getIntForUser(resolver, key, def, UserHandle.USER_CURRENT);
            CustomSeekBarPreference sbPref = (CustomSeekBarPreference) pref;
            sbPref.setDefaultValue(def);
            sbPref.setValue(value);
            sbPref.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(preference instanceof CustomSeekBarPreference))
            return false;
        Settings.System.putIntForUser(getActivity().getContentResolver(),
                preference.getKey(), (Integer) newValue, UserHandle.USER_CURRENT);
        return true;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
