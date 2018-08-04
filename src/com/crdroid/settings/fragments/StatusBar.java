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
package com.crdroid.settings.fragments;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
import android.view.View;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.development.DevelopmentSettings;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.fragments.statusbar.BatteryBar;
import com.crdroid.settings.fragments.statusbar.CarrierLabel;
import com.crdroid.settings.fragments.statusbar.Clock;
import com.crdroid.settings.fragments.statusbar.NetworkTrafficSettings;
import com.crdroid.settings.fragments.statusbar.StatusBarWeather;
import com.crdroid.settings.preferences.SystemSettingListPreference;
import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.R;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String QUICK_PULLDOWN = "qs_quick_pulldown";
    private static final String SMART_PULLDOWN = "qs_smart_pulldown";
    private static final String DATA_ACTIVITY_ARROWS = "data_activity_arrows";
    private static final String WIFI_ACTIVITY_ARROWS = "wifi_activity_arrows";
    private static final String TICKER_MODE = "status_bar_show_ticker";
    private static final String TICKER_MODE_ANIMATION = "status_bar_ticker_animation_mode";
    private static final String CRDROID_LOGO = "status_bar_crdroid_logo";
    private static final String CRDROID_LOGO_COLOR = "status_bar_crdroid_logo_color";
    private static final String CRDROID_LOGO_POSITION = "status_bar_crdroid_logo_position";
    private static final String CRDROID_LOGO_STYLE = "status_bar_crdroid_logo_style";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String SHOW_BATTERY_PERCENT = "show_battery_percent";
    private static final String TEXT_CHARGING_SYMBOL = "text_charging_symbol";
    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";

    public static final int BATTERY_STYLE_PORTRAIT = 0;
    public static final int BATTERY_STYLE_CIRCLE = 1;
    public static final int BATTERY_STYLE_DOTTED_CIRCLE = 2;
    public static final int BATTERY_STYLE_SQUARE = 3;
    public static final int BATTERY_STYLE_TEXT = 4;
    public static final int BATTERY_STYLE_HIDDEN = 5;

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;
    private static final int PULLDOWN_DIR_ALWAYS = 3;

    private SystemSettingListPreference mSmartPulldown;
    private SwitchPreference mDataActivityEnabled;
    private SwitchPreference mWifiActivityEnabled;
    private ListPreference mTickerMode;
    private ListPreference mTickerModeAnimation;
    private SwitchPreference mCrDroidLogo;
    private ColorPickerPreference mCrDroidLogoColor;
    private ListPreference mCrDroidLogoPosition;
    private ListPreference mCrDroidLogoStyle;
    private ListPreference mBatteryStyle;
    private ListPreference mBatteryPercent;
    private ListPreference mTextSymbol;

    private LineageSystemSettingListPreference mQuickPulldown;
    private LineageSystemSettingListPreference mStatusBarClock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();

        mQuickPulldown =
                (LineageSystemSettingListPreference) findPreference(QUICK_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));

        mSmartPulldown = (SystemSettingListPreference) findPreference(SMART_PULLDOWN);
        mSmartPulldown.setOnPreferenceChangeListener(this);
        updateSmartPulldownSummary(mSmartPulldown.getIntValue(0));

        mDataActivityEnabled = (SwitchPreference) findPreference(DATA_ACTIVITY_ARROWS);
        boolean mActivityEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.DATA_ACTIVITY_ARROWS,
                showActivityDefault(getActivity()), UserHandle.USER_CURRENT) != 0;
        mDataActivityEnabled.setChecked(mActivityEnabled);
        mDataActivityEnabled.setOnPreferenceChangeListener(this);

        mWifiActivityEnabled = (SwitchPreference) findPreference(WIFI_ACTIVITY_ARROWS);
        mActivityEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.WIFI_ACTIVITY_ARROWS,
                showActivityDefault(getActivity()), UserHandle.USER_CURRENT) != 0;
        mWifiActivityEnabled.setChecked(mActivityEnabled);
        mWifiActivityEnabled.setOnPreferenceChangeListener(this);

        mTickerMode = (ListPreference) findPreference(TICKER_MODE);
        mTickerMode.setOnPreferenceChangeListener(this);

        mTickerModeAnimation = (ListPreference) findPreference(TICKER_MODE_ANIMATION);
        int tickerMode = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, 0, UserHandle.USER_CURRENT);
        mTickerModeAnimation.setEnabled(tickerMode > 0);

        mCrDroidLogo = (SwitchPreference) findPreference(CRDROID_LOGO);
        mCrDroidLogo.setOnPreferenceChangeListener(this);

        mCrDroidLogoPosition = (ListPreference) findPreference(CRDROID_LOGO_POSITION);
        int crdroidLogoPosition = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_POSITION, 0,
                UserHandle.USER_CURRENT);
        mCrDroidLogoPosition.setValue(String.valueOf(crdroidLogoPosition));
        mCrDroidLogoPosition.setSummary(mCrDroidLogoPosition.getEntry());
        mCrDroidLogoPosition.setOnPreferenceChangeListener(this);

        mCrDroidLogoColor =
                (ColorPickerPreference) findPreference(CRDROID_LOGO_COLOR);
        int intColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, 0xFFFFFFFF,
                UserHandle.USER_CURRENT);
        String hexColor = ColorPickerPreference.convertToARGB(intColor);
        mCrDroidLogoColor.setNewPreviewColor(intColor);
        if (intColor != 0xFFFFFFFF) {
            mCrDroidLogoColor.setSummary(hexColor);
        } else {
            mCrDroidLogoColor.setSummary(R.string.default_string);
        }
        mCrDroidLogoColor.setOnPreferenceChangeListener(this);

        mCrDroidLogoStyle = (ListPreference) findPreference(CRDROID_LOGO_STYLE);
        int crdroidLogoStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);
        mCrDroidLogoStyle.setValue(String.valueOf(crdroidLogoStyle));
        mCrDroidLogoStyle.setSummary(mCrDroidLogoStyle.getEntry());
        mCrDroidLogoStyle.setOnPreferenceChangeListener(this);

        boolean mLogoEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO,
                0, UserHandle.USER_CURRENT) != 0;
        toggleLogo(mLogoEnabled);

        mBatteryStyle = (ListPreference) findPreference(STATUS_BAR_BATTERY_STYLE);
        int batterystyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0,
                UserHandle.USER_CURRENT);
        mBatteryStyle.setValue(String.valueOf(batterystyle));
        mBatteryStyle.setSummary(mBatteryStyle.getEntry());
        mBatteryStyle.setOnPreferenceChangeListener(this);

        mBatteryPercent = (ListPreference) findPreference(SHOW_BATTERY_PERCENT);
        int batterypercent = Settings.System.getIntForUser(resolver,
                Settings.System.SHOW_BATTERY_PERCENT, 0,
                UserHandle.USER_CURRENT);
        mBatteryPercent.setValue(String.valueOf(batterypercent));
        mBatteryPercent.setSummary(mBatteryPercent.getEntry());
        mBatteryPercent.setOnPreferenceChangeListener(this);

        mTextSymbol = (ListPreference) findPreference(TEXT_CHARGING_SYMBOL);
        int textsymbol = Settings.System.getIntForUser(resolver,
                Settings.System.TEXT_CHARGING_SYMBOL, 0,
                UserHandle.USER_CURRENT);
        mTextSymbol.setValue(String.valueOf(textsymbol));
        mTextSymbol.setSummary(mTextSymbol.getEntry());
        updateBatteryOptions();
        mTextSymbol.setOnPreferenceChangeListener(this);

        mStatusBarClock =
                (LineageSystemSettingListPreference) findPreference(STATUS_BAR_CLOCK_STYLE);

        final boolean hasNotch = getResources().getBoolean(
                org.lineageos.platform.internal.R.bool.config_haveNotch);

        // Adjust status bar preferences for RTL
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (hasNotch) {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch_rtl);
            } else {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_rtl);
            }
            mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries_rtl);
            mQuickPulldown.setEntryValues(R.array.status_bar_quick_qs_pulldown_values_rtl);
        } else if (hasNotch) {
            mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch);
            mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQuickPulldown) {
            int value = Integer.parseInt((String) newValue);
            updateQuickPulldownSummary(value);
            return true;
        } else if (preference == mSmartPulldown) {
            int value = Integer.parseInt((String) newValue);
            updateSmartPulldownSummary(value);
            return true;
        } else if (preference == mDataActivityEnabled) {
            boolean showing = ((Boolean)newValue);
            Settings.System.putIntForUser(resolver, Settings.System.DATA_ACTIVITY_ARROWS,
                    showing ? 1 : 0, UserHandle.USER_CURRENT);
            mDataActivityEnabled.setChecked(showing);
            return true;
        } else if (preference == mWifiActivityEnabled) {
            boolean showing = ((Boolean)newValue);
            Settings.System.putIntForUser(resolver, Settings.System.WIFI_ACTIVITY_ARROWS,
                    showing ? 1 : 0, UserHandle.USER_CURRENT);
            mWifiActivityEnabled.setChecked(showing);
            return true;
        } else if (preference.equals(mTickerMode)) {
            int value = Integer.parseInt((String) newValue);
            mTickerModeAnimation.setEnabled(value > 0);
            return true;
        } else if (preference == mCrDroidLogo) {
            boolean value = (Boolean) newValue;
            toggleLogo(value);
            return true;
        } else if (preference == mCrDroidLogoColor) {
            String hex = ColorPickerPreference.convertToARGB(
                Integer.parseInt(String.valueOf(newValue)));
            int value = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, value,
                UserHandle.USER_CURRENT);
            if (value != 0xFFFFFFFF) {
                mCrDroidLogoColor.setSummary(hex);
            } else {
                mCrDroidLogoColor.setSummary(R.string.default_string);
            }
            return true;
        } else if (preference == mCrDroidLogoPosition) {
            int value = Integer.parseInt((String) newValue);
            int index = mCrDroidLogoPosition.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.STATUS_BAR_CRDROID_LOGO_POSITION, value,
                UserHandle.USER_CURRENT);
            mCrDroidLogoPosition.setSummary(
                    mCrDroidLogoPosition.getEntries()[index]);
            return true;
        } else if (preference == mCrDroidLogoStyle) {
            int value = Integer.parseInt((String) newValue);
            int index = mCrDroidLogoStyle.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, value,
                UserHandle.USER_CURRENT);
            mCrDroidLogoStyle.setSummary(
                    mCrDroidLogoStyle.getEntries()[index]);
            return true;
        } else if (preference == mBatteryStyle) {
            int value = Integer.parseInt((String) newValue);
            int index = mBatteryStyle.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.STATUS_BAR_BATTERY_STYLE, value,
                UserHandle.USER_CURRENT);
            mBatteryStyle.setSummary(
                    mBatteryStyle.getEntries()[index]);
            updateBatteryOptions();
            return true;
        } else if (preference == mBatteryPercent) {
            int value = Integer.parseInt((String) newValue);
            int index = mBatteryPercent.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.SHOW_BATTERY_PERCENT, value,
                UserHandle.USER_CURRENT);
            mBatteryPercent.setSummary(
                    mBatteryPercent.getEntries()[index]);
            updateBatteryOptions();
            return true;
        } else if (preference == mTextSymbol) {
            int value = Integer.parseInt((String) newValue);
            int index = mTextSymbol.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.TEXT_CHARGING_SYMBOL, value,
                UserHandle.USER_CURRENT);
            mTextSymbol.setSummary(
                    mTextSymbol.getEntries()[index]);
            return true;
        }
        return false;
    }

    private void updateBatteryOptions() {
        ContentResolver resolver = getActivity().getContentResolver();
        int batterystyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, 1,
                UserHandle.USER_CURRENT);
        mBatteryPercent.setEnabled(batterystyle != BATTERY_STYLE_TEXT && batterystyle != BATTERY_STYLE_HIDDEN);
        mTextSymbol.setEnabled(batterystyle == BATTERY_STYLE_TEXT);
    }

    private void updateQuickPulldownSummary(int value) {
        String summary="";
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_off);
                break;
            case PULLDOWN_DIR_ALWAYS:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_always);
                break;
            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary,
                    getResources().getString(value == PULLDOWN_DIR_LEFT
                        ? R.string.status_bar_quick_qs_pulldown_summary_left
                        : R.string.status_bar_quick_qs_pulldown_summary_right));
                break;
        }
        mQuickPulldown.setSummary(summary);
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

    public static int showActivityDefault(Context context) {
/*
        final boolean showByDefault = context.getResources().getBoolean(
                com.android.internal.R.bool.config_showActivity);

        if (showByDefault) {
            return 1;
        }
*/
        return 0;
    }

    public void toggleLogo(boolean enabled) {
        mCrDroidLogoColor.setEnabled(enabled);
        mCrDroidLogoPosition.setEnabled(enabled);
        mCrDroidLogoStyle.setEnabled(enabled);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        BatteryBar.reset(mContext);
        CarrierLabel.reset(mContext);
        Clock.reset(mContext);
        NetworkTrafficSettings.reset(mContext);
        StatusBarWeather.reset(mContext);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_SU_INDICATOR, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_FOURG_ICON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ROAMING_INDICATOR_ICON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUETOOTH_SHOW_BATTERY, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_VOLTE_ICON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_BATTERY_PERCENT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.TEXT_CHARGING_SYMBOL, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SMART_PULLDOWN, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DATA_ACTIVITY_ARROWS, showActivityDefault(mContext), UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.WIFI_ACTIVITY_ARROWS, showActivityDefault(mContext), UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE, 1, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_BRIGHTNESS_CONTROL, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, 0xFFFFFFFF, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_POSITION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_UNITS, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, 1, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_CLOCK, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
