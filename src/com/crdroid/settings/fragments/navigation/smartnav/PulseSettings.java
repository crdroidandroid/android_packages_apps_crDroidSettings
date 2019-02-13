/*
 * Copyright (C) 2015 The Dirty Unicorns Project
 *           (C) 2018-2019 crDroid Android Project
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

package com.crdroid.settings.fragments.navigation.smartnav;

import android.app.ActionBar;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.preference.PreferenceCategory;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.ListPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.R;

public class PulseSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = PulseSettings.class.getSimpleName();

    ColorPickerPreference mPulseColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pulse_settings);

        ContentResolver resolver = getActivity().getContentResolver();

        int pulseColor = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.FLING_PULSE_COLOR, Color.WHITE, UserHandle.USER_CURRENT);
        mPulseColor = (ColorPickerPreference) findPreference("fling_pulse_color");
        mPulseColor.setNewPreviewColor(pulseColor);
        mPulseColor.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (preference == mPulseColor) {
            int color = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_COLOR, color, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_RENDER_STYLE_URI,
             1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.FLING_PULSE_ENABLED,
             0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_AUTO_COLOR,
             0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.FLING_PULSE_COLOR,
             Color.WHITE, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED,
             1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_CUSTOM_DIMEN,
             14, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_CUSTOM_DIV,
             16, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_FILLED_BLOCK_SIZE,
             4, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_EMPTY_BLOCK_SIZE,
             1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR,
             4, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_SOLID_FUDGE_FACTOR,
             5, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_LAVALAMP_SOLID_SPEED,
             10000, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.FLING_PULSE_LAVALAMP_SPEED,
             10000, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_SOLID_UNITS_COUNT,
             64, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_SOLID_UNITS_OPACITY,
             200, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PULSE_CUSTOM_BUTTONS_OPACITY,
             200, UserHandle.USER_CURRENT);
        Settings.Secure.putStringForUser(resolver, Settings.Secure.PULSE_APPS_BLACKLIST,
             "", UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
