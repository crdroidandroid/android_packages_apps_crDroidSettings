/*
 * Copyright (C) 2016-2018 crDroid Android Project
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
package com.crdroid.settings.fragments.navbar;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

public class EdgeGestureSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "EdgeGestureSettings";

    private static final String EDGE_GESTURES_SCREEN_PERCENT = "edge_gestures_back_screen_percent";

    private CustomSeekBarPreference mScreenPercent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.edge_gestures);

        ContentResolver resolver = getActivity().getContentResolver();

        mFooterPreferenceMixin.createFooterPreference().setTitle(R.string.edge_gestures_description);

        int size = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.EDGE_GESTURES_BACK_SCREEN_PERCENT, 60, UserHandle.USER_CURRENT);
        mScreenPercent = (CustomSeekBarPreference) findPreference(EDGE_GESTURES_SCREEN_PERCENT);
        mScreenPercent.setValue(size);
        mScreenPercent.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (preference == mScreenPercent) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.EDGE_GESTURES_BACK_SCREEN_PERCENT, val, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_FEEDBACK_DURATION, 100, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_LONG_PRESS_DURATION, 500, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_BACK_EDGES, 5, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_LANDSCAPE_BACK_EDGES, 5, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_BACK_SCREEN_PERCENT, 60, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.EDGE_GESTURES_BACK_SHOW_UI_FEEDBACK, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
