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

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.development.DevelopmentSettings;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.fragments.statusbar.BatteryBar;
import com.crdroid.settings.fragments.statusbar.Clock;
import com.crdroid.settings.fragments.statusbar.StatusBarWeather;
import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.R;

import lineageos.providers.LineageSettings;

public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String QUICK_PULLDOWN = "quick_pulldown";
    private static final String SMART_PULLDOWN = "smart_pulldown";
    private static final String DATA_ACTIVITY_ARROWS = "data_activity_arrows";
    private static final String WIFI_ACTIVITY_ARROWS = "wifi_activity_arrows";
    private static final String TICKER_MODE = "ticker_mode";
    private static final String CRDROID_LOGO = "status_bar_crdroid_logo";
    private static final String CRDROID_LOGO_COLOR = "status_bar_crdroid_logo_color";
    private static final String CRDROID_LOGO_POSITION = "status_bar_crdroid_logo_position";
    private static final String CRDROID_LOGO_STYLE = "status_bar_crdroid_logo_style";

    private ListPreference mQuickPulldown;
    private ListPreference mSmartPulldown;
    private SwitchPreference mDataActivityEnabled;
    private SwitchPreference mWifiActivityEnabled;
    private ListPreference mTickerMode;
    private SwitchPreference mCrDroidLogo;
    private ColorPickerPreference mCrDroidLogoColor;
    private ListPreference mCrDroidLogoPosition;
    private ListPreference mCrDroidLogoStyle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();

        mQuickPulldown = (ListPreference) findPreference(QUICK_PULLDOWN);
        int quickPulldownValue = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0, UserHandle.USER_CURRENT);
        mQuickPulldown.setValue(String.valueOf(quickPulldownValue));
        updatePulldownSummary(quickPulldownValue);
        mQuickPulldown.setOnPreferenceChangeListener(this);

        mSmartPulldown = (ListPreference) findPreference(SMART_PULLDOWN);
        int smartPulldown = Settings.System.getIntForUser(resolver,
                Settings.System.QS_SMART_PULLDOWN, 0, UserHandle.USER_CURRENT);
        mSmartPulldown.setValue(String.valueOf(smartPulldown));
        updateSmartPulldownSummary(smartPulldown);
        mSmartPulldown.setOnPreferenceChangeListener(this);

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
        int tickerMode = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, 0, UserHandle.USER_CURRENT);
        mTickerMode.setValue(String.valueOf(tickerMode));
        mTickerMode.setSummary(mTickerMode.getEntry());
        mTickerMode.setOnPreferenceChangeListener(this);

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
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mQuickPulldown) {
            int value = Integer.parseInt((String) newValue);
            LineageSettings.System.putIntForUser(resolver, LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
                    value, UserHandle.USER_CURRENT);
            updatePulldownSummary(value);
            return true;
        } else if (preference == mSmartPulldown) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver, Settings.System.QS_SMART_PULLDOWN, value, UserHandle.USER_CURRENT);
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
            Settings.System.putIntForUser(resolver, Settings.System.STATUS_BAR_SHOW_TICKER, value, UserHandle.USER_CURRENT);
            int index = mTickerMode.findIndexOfValue((String) newValue);
            mTickerMode.setSummary(mTickerMode.getEntries()[index]);
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
        Clock.reset(mContext);
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
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
