/*
 * Copyright (C) 2016-2020 crDroid Android Project
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
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

public class BatteryBar extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    private static final String PREF_BATT_BAR = "statusbar_battery_bar";
    private static final String PREF_BATT_BAR_COLOR = "statusbar_battery_bar_color";
    private static final String PREF_BATT_BAR_CHARGING_COLOR = "statusbar_battery_bar_charging_color";
    private static final String PREF_BATT_BAR_BATTERY_LOW_COLOR = "statusbar_battery_bar_battery_low_color";
    private static final String PREF_BATT_USE_CHARGING_COLOR = "statusbar_battery_bar_enable_charging_color";
    private static final String PREF_BATT_BLEND_COLOR = "statusbar_battery_bar_blend_color";
    private static final String PREF_BATT_BLEND_COLOR_REVERSE = "statusbar_battery_bar_blend_color_reverse";

    private SwitchPreference mBatteryBar;
    private ColorPickerPreference mBatteryBarColor;
    private ColorPickerPreference mBatteryBarChargingColor;
    private ColorPickerPreference mBatteryBarBatteryLowColor;

    private boolean mIsBarSwitchingMode = false;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.battery_bar);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        int intColor;
        String hexColor;

        mBatteryBar = (SwitchPreference) findPreference(PREF_BATT_BAR);
        mHandler = new Handler();

        boolean showing = Settings.System.getIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT) != 0;
        mBatteryBar.setChecked(showing);
        mBatteryBar.setOnPreferenceChangeListener(this);

        mBatteryBarColor = (ColorPickerPreference) prefSet.findPreference(PREF_BATT_BAR_COLOR);
        intColor = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_COLOR, 0xff76c124, UserHandle.USER_CURRENT);
        hexColor = ColorPickerPreference.convertToARGB(intColor);
        mBatteryBarColor.setNewPreviewColor(intColor);
        mBatteryBarColor.setSummary(hexColor);
        mBatteryBarColor.setOnPreferenceChangeListener(this);

        mBatteryBarChargingColor = (ColorPickerPreference) prefSet.findPreference(PREF_BATT_BAR_CHARGING_COLOR);
        intColor = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR, 0xffffc90f, UserHandle.USER_CURRENT);
        hexColor = ColorPickerPreference.convertToARGB(intColor);
        mBatteryBarChargingColor.setNewPreviewColor(intColor);
        mBatteryBarChargingColor.setSummary(hexColor);
        mBatteryBarChargingColor.setEnabled(Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR, 1, UserHandle.USER_CURRENT) == 1);
        mBatteryBarChargingColor.setOnPreferenceChangeListener(this);

        mBatteryBarBatteryLowColor = (ColorPickerPreference) prefSet.findPreference(PREF_BATT_BAR_BATTERY_LOW_COLOR);
        intColor = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR, 0xfff90028, UserHandle.USER_CURRENT);
        hexColor = ColorPickerPreference.convertToARGB(intColor);
        mBatteryBarBatteryLowColor.setNewPreviewColor(intColor);
        mBatteryBarBatteryLowColor.setSummary(hexColor);
        mBatteryBarBatteryLowColor.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mBatteryBar) {
            if (mIsBarSwitchingMode) {
                return false;
            }
            mIsBarSwitchingMode = true;
            boolean showing = ((Boolean)newValue);
            Settings.System.putIntForUser(resolver, Settings.System.STATUSBAR_BATTERY_BAR,
                    showing ? 1 : 0, UserHandle.USER_CURRENT);
            mBatteryBar.setChecked(showing);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mIsBarSwitchingMode = false;
                }
            }, 1500);
            return true;
        } else if (preference == mBatteryBarColor) {
            String hex = ColorPickerPreference.convertToARGB(Integer
                .parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_COLOR, intHex, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBatteryBarChargingColor) {
            String hex = ColorPickerPreference.convertToARGB(Integer
                .parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR, intHex, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBatteryBarBatteryLowColor) {
            String hex = ColorPickerPreference.convertToARGB(Integer
                .parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR, intHex, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_COLOR, 0xff76c124, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS, 2, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR, 0xffffc90f, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR, 0xfff90028, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
