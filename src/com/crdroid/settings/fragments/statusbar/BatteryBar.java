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
package com.crdroid.settings.fragments.statusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

public class BatteryBar extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    private static final String PREF_BATT_BAR = "statusbar_battery_bar_list";
    private static final String PREF_BATT_BAR_NO_NAVBAR = "statusbar_battery_bar_no_navbar_list";
    private static final String PREF_BATT_BAR_STYLE = "statusbar_battery_bar_style";
    private static final String PREF_BATT_BAR_COLOR = "statusbar_battery_bar_color";
    private static final String PREF_BATT_BAR_CHARGING_COLOR = "statusbar_battery_bar_charging_color";
    private static final String PREF_BATT_BAR_BATTERY_LOW_COLOR = "statusbar_battery_bar_battery_low_color";
    private static final String PREF_BATT_BAR_WIDTH = "statusbar_battery_bar_thickness";
    private static final String PREF_BATT_ANIMATE = "statusbar_battery_bar_animate";
    private static final String PREF_BATT_USE_CHARGING_COLOR = "statusbar_battery_bar_enable_charging_color";
    private static final String PREF_BATT_BLEND_COLOR = "statusbar_battery_bar_blend_color";
    private static final String PREF_BATT_BLEND_COLOR_REVERSE = "statusbar_battery_bar_blend_color_reverse";

    private ListPreference mBatteryBar;
    private ListPreference mBatteryBarNoNavbar;
    private ListPreference mBatteryBarStyle;
    private CustomSeekBarPreference mBatteryBarThickness;
    private SwitchPreference mBatteryBarChargingAnimation;
    private SwitchPreference mBatteryBarUseChargingColor;
    private SwitchPreference mBatteryBarBlendColor;
    private SwitchPreference mBatteryBarBlendColorReverse;
    private ColorPickerPreference mBatteryBarColor;
    private ColorPickerPreference mBatteryBarChargingColor;
    private ColorPickerPreference mBatteryBarBatteryLowColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.battery_bar);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        int intColor;
        String hexColor;

        mBatteryBar = (ListPreference) prefSet.findPreference(PREF_BATT_BAR);
        mBatteryBar.setValue((Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT)) + "");
        mBatteryBar.setSummary(mBatteryBar.getEntry());
        mBatteryBar.setOnPreferenceChangeListener(this);

        mBatteryBarNoNavbar = (ListPreference) prefSet.findPreference(PREF_BATT_BAR_NO_NAVBAR);
        mBatteryBarNoNavbar.setValue((Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT)) + "");
        mBatteryBarNoNavbar.setSummary(mBatteryBarNoNavbar.getEntry());
        mBatteryBarNoNavbar.setOnPreferenceChangeListener(this);

        mBatteryBarStyle = (ListPreference) prefSet.findPreference(PREF_BATT_BAR_STYLE);
        mBatteryBarStyle.setValue((Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_STYLE, 0, UserHandle.USER_CURRENT)) + "");
        mBatteryBarStyle.setSummary(mBatteryBarStyle.getEntry());
        mBatteryBarStyle.setOnPreferenceChangeListener(this);

        mBatteryBarColor = (ColorPickerPreference) prefSet.findPreference(PREF_BATT_BAR_COLOR);
        intColor = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_COLOR, 0xffffffff, UserHandle.USER_CURRENT);
        hexColor = ColorPickerPreference.convertToARGB(intColor);
        mBatteryBarColor.setNewPreviewColor(intColor);
        mBatteryBarColor.setSummary(hexColor);
        mBatteryBarColor.setOnPreferenceChangeListener(this);

        mBatteryBarChargingColor = (ColorPickerPreference) prefSet.findPreference(PREF_BATT_BAR_CHARGING_COLOR);
        intColor = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR, 0xffffff00, UserHandle.USER_CURRENT);
        hexColor = ColorPickerPreference.convertToARGB(intColor);
        mBatteryBarChargingColor.setNewPreviewColor(intColor);
        mBatteryBarChargingColor.setSummary(hexColor);
        mBatteryBarChargingColor.setEnabled(Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR, 1, UserHandle.USER_CURRENT) == 1);
        mBatteryBarChargingColor.setOnPreferenceChangeListener(this);

        mBatteryBarBatteryLowColor = (ColorPickerPreference) prefSet.findPreference(PREF_BATT_BAR_BATTERY_LOW_COLOR);
        intColor = Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR, 0xffffffff, UserHandle.USER_CURRENT);
        hexColor = ColorPickerPreference.convertToARGB(intColor);
        mBatteryBarBatteryLowColor.setNewPreviewColor(intColor);
        mBatteryBarBatteryLowColor.setSummary(hexColor);
        mBatteryBarBatteryLowColor.setOnPreferenceChangeListener(this);

        mBatteryBarChargingAnimation = (SwitchPreference) prefSet.findPreference(PREF_BATT_ANIMATE);
        mBatteryBarChargingAnimation.setChecked(Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE, 0, UserHandle.USER_CURRENT) == 1);
        mBatteryBarChargingAnimation.setOnPreferenceChangeListener(this);

        mBatteryBarThickness = (CustomSeekBarPreference) prefSet.findPreference(PREF_BATT_BAR_WIDTH);
        mBatteryBarThickness.setValue(Settings.System.getIntForUser(resolver,
            Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS, 1, UserHandle.USER_CURRENT));
        mBatteryBarThickness.setOnPreferenceChangeListener(this);

        mBatteryBarUseChargingColor = (SwitchPreference) findPreference(PREF_BATT_USE_CHARGING_COLOR);
        mBatteryBarUseChargingColor.setOnPreferenceChangeListener(this);

        mBatteryBarBlendColor = (SwitchPreference) findPreference(PREF_BATT_BLEND_COLOR);
        mBatteryBarBlendColorReverse = (SwitchPreference) findPreference(PREF_BATT_BLEND_COLOR_REVERSE);

/*
        boolean hasNavBarByDefault = getResources().getBoolean(
            com.android.internal.R.bool.config_showNavigationBar);
        boolean enableNavigationBar = Settings.Secure.getIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_VISIBLE, hasNavBarByDefault ? 1 : 0, UserHandle.USER_CURRENT) == 1;
        boolean batteryBarSupported = Settings.Secure.getIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_MODE, 0, UserHandle.USER_CURRENT) == 0;
        if (!enableNavigationBar || !batteryBarSupported) {
*/
            prefSet.removePreference(mBatteryBar);
/*
        } else {
            prefSet.removePreference(mBatteryBarNoNavbar);
        }
*/

        updateBatteryBarOptions();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mBatteryBarColor) {
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
        } else if (preference == mBatteryBar) {
            int val = Integer.parseInt((String) newValue);
            int index = mBatteryBar.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR, val, UserHandle.USER_CURRENT);
            mBatteryBar.setSummary(mBatteryBar.getEntries()[index]);
            updateBatteryBarOptions();
        } else if (preference == mBatteryBarNoNavbar) {
            int val = Integer.parseInt((String) newValue);
            int index = mBatteryBarNoNavbar.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR, val, UserHandle.USER_CURRENT);
            mBatteryBarNoNavbar.setSummary(mBatteryBarNoNavbar.getEntries()[index]);
            updateBatteryBarOptions();
            return true;
        } else if (preference == mBatteryBarStyle) {
            int val = Integer.parseInt((String) newValue);
            int index = mBatteryBarStyle.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_STYLE, val, UserHandle.USER_CURRENT);
            mBatteryBarStyle.setSummary(mBatteryBarStyle.getEntries()[index]);
            return true;
        } else if (preference == mBatteryBarThickness) {
            int val =  (Integer) newValue;
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBatteryBarChargingAnimation) {
            int val = ((Boolean) newValue) ? 1 : 0;
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBatteryBarUseChargingColor)
            boolean enabled = (Boolean) newValue;
            mBatteryBarChargingColor.setEnabled(enabled);
            return true;
        }
        return false;
    }

    private void updateBatteryBarOptions() {
        if (Settings.System.getIntForUser(getActivity().getContentResolver(),
            Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT) == 0) {
            mBatteryBarStyle.setEnabled(false);
            mBatteryBarThickness.setEnabled(false);
            mBatteryBarChargingAnimation.setEnabled(false);
            mBatteryBarColor.setEnabled(false);
            mBatteryBarChargingColor.setEnabled(false);
            mBatteryBarBatteryLowColor.setEnabled(false);
            mBatteryBarUseChargingColor.setEnabled(false);
            mBatteryBarBlendColor.setEnabled(false);
            mBatteryBarBlendColorReverse.setEnabled(false);
        } else {
            mBatteryBarStyle.setEnabled(true);
            mBatteryBarThickness.setEnabled(true);
            mBatteryBarChargingAnimation.setEnabled(true);
            mBatteryBarColor.setEnabled(true);
            mBatteryBarChargingColor.setEnabled(true);
            mBatteryBarBatteryLowColor.setEnabled(true);
            mBatteryBarUseChargingColor.setEnabled(true);
            mBatteryBarBlendColor.setEnabled(true);
            mBatteryBarBlendColorReverse.setEnabled(true);
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_COLOR, 0xffffffff, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR, 0xffffff00, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR, 0xffffffff, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
