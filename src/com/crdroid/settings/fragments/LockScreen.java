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
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.development.DevelopmentSettings;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    public static final String TAG = "LockScreen";

    private static final String LOCKSCREEN_MAX_NOTIF_CONFIG = "lockscreen_max_notif_config";

    private CustomSeekBarPreference mMaxKeyguardNotifConfig;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);

        ContentResolver resolver = mContext.getContentResolver();

        mMaxKeyguardNotifConfig = (CustomSeekBarPreference) findPreference(LOCKSCREEN_MAX_NOTIF_CONFIG);
        int kgconf = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 5, UserHandle.USER_CURRENT);
        mMaxKeyguardNotifConfig.setValue(kgconf);
        mMaxKeyguardNotifConfig.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mMaxKeyguardNotifConfig) {
            int value = (Integer) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BATTERY_INFO, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCK_SCREEN_CUSTOM_NOTIF, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 5, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_MEDIA_METADATA, 1, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
