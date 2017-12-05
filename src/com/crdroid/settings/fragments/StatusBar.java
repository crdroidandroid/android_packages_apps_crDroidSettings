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
import android.content.res.Resources;
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

import com.crdroid.settings.fragments.statusbar.BatteryBar;
import com.crdroid.settings.fragments.statusbar.NetworkTraffic;
import com.crdroid.settings.R;

import lineageos.providers.LineageSettings;

public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String QUICK_PULLDOWN = "quick_pulldown";
    private static final String SMART_PULLDOWN = "smart_pulldown";

    private ListPreference mQuickPulldown;
    private ListPreference mSmartPulldown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();

        mQuickPulldown = (ListPreference) findPreference(QUICK_PULLDOWN);
        int quickPulldownValue = LineageSettings.System.getInt(resolver,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0);
        mQuickPulldown.setValue(String.valueOf(quickPulldownValue));
        updatePulldownSummary(quickPulldownValue);
        mQuickPulldown.setOnPreferenceChangeListener(this);

        mSmartPulldown = (ListPreference) findPreference(SMART_PULLDOWN);
        int smartPulldown = Settings.System.getInt(resolver,
                Settings.System.QS_SMART_PULLDOWN, 0);
        mSmartPulldown.setValue(String.valueOf(smartPulldown));
        updateSmartPulldownSummary(smartPulldown);
        mSmartPulldown.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQuickPulldown) {
            int value = Integer.parseInt((String) newValue);
            LineageSettings.System.putInt(resolver, LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
                    value);
            updatePulldownSummary(value);
            return true;
        } else if (preference == mSmartPulldown) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putInt(resolver, Settings.System.QS_SMART_PULLDOWN, value);
            updateSmartPulldownSummary(value);
            return true;
        }
        return false;
    }

    private void updatePulldownSummary(int value) {
        Resources res = getResources();

        if (value == 0) {
            // quick pulldown deactivated
            mQuickPulldown.setSummary(res.getString(R.string.status_bar_quick_qs_pulldown_off));
        } else if (value == 3) {
            // quick pulldown always
            mQuickPulldown.setSummary(res.getString(R.string.status_bar_quick_qs_pulldown_always));
        } else {
            String direction = res.getString(value == 2
                    ? R.string.status_bar_quick_qs_pulldown_left
                    : R.string.status_bar_quick_qs_pulldown_right);
            mQuickPulldown.setSummary(res.getString(R.string.status_bar_quick_qs_pulldown_summary, direction));
        }
    }

    private void updateSmartPulldownSummary(int value) {
        Resources res = getResources();

        if (value == 0) {
            // Smart pulldown deactivated
            mSmartPulldown.setSummary(res.getString(R.string.smart_pulldown_off));
        } else if (value == 3) {
            mSmartPulldown.setSummary(res.getString(R.string.smart_pulldown_none_summary));
        } else {
            String type = res.getString(value == 1
                    ? R.string.smart_pulldown_dismissable
                    : R.string.smart_pulldown_ongoing);
            mSmartPulldown.setSummary(res.getString(R.string.smart_pulldown_summary, type));
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        BatteryBar.reset(mContext);
        NetworkTraffic.reset(mContext);
        Settings.System.putInt(resolver,
                Settings.System.SHOW_FOURG_ICON, 0);
        Settings.System.putInt(resolver,
                Settings.System.ROAMING_INDICATOR_ICON, 1);
        Settings.System.putInt(resolver,
                Settings.System.BLUETOOTH_SHOW_BATTERY, 1);
        Settings.System.putInt(resolver,
                Settings.System.SHOW_VOLTE_ICON, 0);
        LineageSettings.System.putInt(resolver,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0);
        Settings.System.putInt(resolver,
                Settings.System.QS_SMART_PULLDOWN, 0);
        LineageSettings.System.putInt(resolver,
                LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE, 1);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
