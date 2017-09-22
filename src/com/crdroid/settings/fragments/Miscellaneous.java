/*
 * Copyright (C) 2016-2017 crDroid Android
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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.development.DevelopmentSettings;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;

public class Miscellaneous extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "Miscellaneous";

    private static final String KEY_LOCK_CLOCK = "lock_clock";
    private static final String KEY_LOCK_CLOCK_PACKAGE_NAME = "com.cyanogenmod.lockclock";
    private static final String SHOW_CPU_INFO_KEY = "show_cpu_info";
    private static final String MEDIA_SCANNER_ON_BOOT = "media_scanner_on_boot";

    private SwitchPreference mShowCpuInfo;
    private ListPreference mMSOB;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_misc);

        // mLockClock
        if (!DevelopmentSettings.isPackageInstalled(getActivity(), KEY_LOCK_CLOCK_PACKAGE_NAME)) {
            getPreferenceScreen().removePreference(findPreference(KEY_LOCK_CLOCK));
        }

        mShowCpuInfo = (SwitchPreference) findPreference(SHOW_CPU_INFO_KEY);
        mShowCpuInfo.setChecked(Settings.Global.getInt(getActivity().getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, 0) == 1);
        mShowCpuInfo.setOnPreferenceChangeListener(this);

        // MediaScanner behavior on boot
        mMSOB = (ListPreference) findPreference(MEDIA_SCANNER_ON_BOOT);
        int mMSOBValue = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.MEDIA_SCANNER_ON_BOOT, 0);
        mMSOB.setValue(String.valueOf(mMSOBValue));
        mMSOB.setSummary(mMSOB.getEntry());
        mMSOB.setOnPreferenceChangeListener(this);
    }

    private void writeCpuInfoOptions(boolean value) {
        Settings.Global.putInt(getActivity().getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, value ? 1 : 0);
        Intent service = (new Intent())
                .setClassName("com.android.systemui", "com.android.systemui.CPUInfoService");
        if (value) {
            getActivity().startService(service);
        } else {
            getActivity().stopService(service);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mShowCpuInfo) {
            writeCpuInfoOptions((Boolean) newValue);
            return true;
        } else if (preference == mMSOB) {
            int value = Integer.parseInt(((String) newValue).toString());
            Settings.System.putInt(getContentResolver(),
                    Settings.System.MEDIA_SCANNER_ON_BOOT, value);
            mMSOB.setValue(String.valueOf(value));
            mMSOB.setSummary(mMSOB.getEntries()[value]);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
