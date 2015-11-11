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

import com.crdroid.settings.fragments.statusbar.NetworkTraffic;
import com.crdroid.settings.R;

public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String QUICK_PULLDOWN = "quick_pulldown";

    private ListPreference mQuickPulldown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();

        mQuickPulldown = (ListPreference) findPreference(QUICK_PULLDOWN);
        int quickPulldownValue = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0);
        mQuickPulldown.setValue(String.valueOf(quickPulldownValue));
        updatePulldownSummary(quickPulldownValue);
        mQuickPulldown.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQuickPulldown) {
            int quickPulldownValue = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver, Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
                    quickPulldownValue, UserHandle.USER_CURRENT);
            updatePulldownSummary(quickPulldownValue);
            return true;
        }
        return false;
    }

    private void updatePulldownSummary(int value) {
        Resources res = getResources();

        if (value == 0) {
            // quick pulldown deactivated
            mQuickPulldown.setSummary(res.getString(R.string.quick_pulldown_off));
        } else if (value == 3) {
            // quick pulldown always
            mQuickPulldown.setSummary(res.getString(R.string.quick_pulldown_summary_always));
        } else {
            String direction = res.getString(value == 2
                    ? R.string.quick_pulldown_left
                    : R.string.quick_pulldown_right);
            mQuickPulldown.setSummary(res.getString(R.string.quick_pulldown_summary, direction));
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        NetworkTraffic.reset(mContext);
        Settings.System.putInt(resolver,
                Settings.System.SHOW_FOURG_ICON, 0);
        Settings.System.putInt(resolver,
                Settings.System.ROAMING_INDICATOR_ICON, 1);
        Settings.System.putInt(resolver,
                Settings.System.BLUETOOTH_SHOW_BATTERY, 1);
        Settings.System.putInt(resolver,
                Settings.System.SHOW_VOLTE_ICON, 0);
        Settings.System.putInt(resolver,
                Settings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
