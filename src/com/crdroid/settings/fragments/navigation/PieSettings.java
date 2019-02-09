/*
 * Copyright (C) 2017-2019 crDroid Android Project
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

package com.crdroid.settings.fragments.navigation;

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

import com.crdroid.settings.R;

public class PieSettings extends SettingsPreferenceFragment {

    private static final String TAG = PieSettings.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pie_settings);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PIE_STATE,
             0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PIE_THEME_MODE,
             0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PIE_BATTERY_MODE,
             2, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PIE_STATUS_INDICATOR,
             0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.PIE_GRAVITY,
             2, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
