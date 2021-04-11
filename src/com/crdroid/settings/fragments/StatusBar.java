/*
 * Copyright (C) 2016-2021 crDroid Android Project
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
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.statusbar.BatteryBar;
import com.crdroid.settings.fragments.statusbar.Clock;
import com.crdroid.settings.fragments.statusbar.NetworkTrafficSettings;
import com.crdroid.settings.preferences.SystemSettingSeekBarPreference;
import com.crdroid.settings.utils.DeviceUtils;
import com.crdroid.settings.utils.TelephonyUtils;

import java.util.List;
import java.util.ArrayList;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

@SearchIndexable
public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String KEY_SHOW_ROAMING = "roaming_indicator_icon";
    private static final String KEY_SHOW_FOURG = "show_fourg_icon";
    private static final String KEY_SHOW_DATA_DISABLED = "data_disabled_icon";
    private static final String KEY_USE_OLD_MOBILETYPE = "use_old_mobiletype";

    private LineageSystemSettingListPreference mStatusBarClock;
    private SwitchPreference mShowRoaming;
    private SwitchPreference mShowFourg;
    private SwitchPreference mDataDisabled;
    private SwitchPreference mOldMobileType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();
        Context mContext = getActivity().getApplicationContext();

        final PreferenceScreen prefScreen = getPreferenceScreen();

        mStatusBarClock =
                (LineageSystemSettingListPreference) findPreference(STATUS_BAR_CLOCK_STYLE);

        // Adjust status bar preferences for RTL
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (DeviceUtils.hasNotch(mContext)) {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch_rtl);
            } else {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_rtl);
            }
        } else if (DeviceUtils.hasNotch(mContext)) {
            mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch);
            mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch);
        }

        mShowRoaming = (SwitchPreference) findPreference(KEY_SHOW_ROAMING);
        mShowFourg = (SwitchPreference) findPreference(KEY_SHOW_FOURG);
        mDataDisabled = (SwitchPreference) findPreference(KEY_SHOW_DATA_DISABLED);
        mOldMobileType = (SwitchPreference) findPreference(KEY_USE_OLD_MOBILETYPE);

        if (!TelephonyUtils.isVoiceCapable(getActivity())) {
            prefScreen.removePreference(mShowRoaming);
            prefScreen.removePreference(mShowFourg);
            prefScreen.removePreference(mDataDisabled);
            prefScreen.removePreference(mOldMobileType);
        } else {
            boolean mConfigUseOldMobileType = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_useOldMobileIcons);
            boolean showing = Settings.System.getIntForUser(resolver,
                    Settings.System.USE_OLD_MOBILETYPE,
                    mConfigUseOldMobileType ? 1 : 0, UserHandle.USER_CURRENT) != 0;
            mOldMobileType.setChecked(showing);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        boolean mConfigUseOldMobileType = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_useOldMobileIcons);

        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE, 1, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_BRIGHTNESS_CONTROL, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_CLOCK, 2, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUETOOTH_SHOW_BATTERY, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ROAMING_INDICATOR_ICON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_FOURG_ICON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DATA_DISABLED_ICON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_TEXT_CHARGING, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SMART_PULLDOWN, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.USE_OLD_MOBILETYPE, mConfigUseOldMobileType ? 1 : 0, UserHandle.USER_CURRENT);

        BatteryBar.reset(mContext);
        Clock.reset(mContext);
        NetworkTrafficSettings.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_statusbar) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    if (!TelephonyUtils.isVoiceCapable(context)) {
                        keys.add(KEY_SHOW_ROAMING);
                        keys.add(KEY_SHOW_FOURG);
                        keys.add(KEY_SHOW_DATA_DISABLED);
                        keys.add(KEY_USE_OLD_MOBILETYPE);
                    }

                    return keys;
                }
            };
}
