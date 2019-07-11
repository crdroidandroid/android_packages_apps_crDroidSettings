/*
 * Copyright (C) 2016-2019 crDroid Android Project
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
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.view.View;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.statusbar.BatteryBar;
import com.crdroid.settings.fragments.statusbar.Clock;
import com.crdroid.settings.fragments.statusbar.NetworkTrafficSettings;
import com.crdroid.settings.fragments.statusbar.Ticker;
import com.crdroid.settings.preferences.SystemSettingListPreference;
import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.utils.TelephonyUtils;

import java.util.List;
import java.util.ArrayList;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

import org.lineageos.internal.util.FileUtils;

public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {

    public static final String TAG = "StatusBar";

    private static final String QUICK_PULLDOWN = "qs_quick_pulldown";
    private static final String SMART_PULLDOWN = "qs_smart_pulldown";
    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String CRDROID_LOGO_COLOR = "status_bar_logo_color";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String TEXT_CHARGING_SYMBOL = "text_charging_symbol";
    private static final String SU_INDICATOR = "show_su_indicator";
    private static final String KEY_OLD_MOBILETYPE = "use_old_mobiletype";
    private static final String KEY_SHOW_DATA_DISABLED = "data_disabled_icon";
    private static final String KEY_SHOW_FOURG = "show_fourg_icon";
    private static final String KEY_SHOW_ROAMING = "roaming_indicator_icon";
    private static final String KEY_SHOW_VOLTE = "show_volte_icon";

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;
    private static final int PULLDOWN_DIR_ALWAYS = 3;

    public static final int BATTERY_STYLE_PORTRAIT = 0;
    public static final int BATTERY_STYLE_CIRCLE = 1;
    public static final int BATTERY_STYLE_DOTTED_CIRCLE = 2;
    public static final int BATTERY_STYLE_SQUARE = 3;
    public static final int BATTERY_STYLE_TEXT = 4;
    public static final int BATTERY_STYLE_HIDDEN = 5;

    private SystemSettingListPreference mSmartPulldown;

    private LineageSystemSettingListPreference mQuickPulldown;
    private LineageSystemSettingListPreference mStatusBarClock;
    private ColorPickerPreference mCrDroidLogoColor;
    private ListPreference mBatteryStyle;
    private ListPreference mBatteryPercent;
    private ListPreference mTextSymbol;
    private SwitchPreference mSuIndicator;
    private SwitchPreference mOldType;
    private SwitchPreference mDataDisabled;
    private SwitchPreference mShowFourg;
    private SwitchPreference mShowRoaming;
    private SwitchPreference mShowVolte;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);

        ContentResolver resolver = getActivity().getContentResolver();

        final PreferenceScreen prefScreen = getPreferenceScreen();

        mQuickPulldown =
                (LineageSystemSettingListPreference) findPreference(QUICK_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));

        mSmartPulldown = (SystemSettingListPreference) findPreference(SMART_PULLDOWN);
        mSmartPulldown.setOnPreferenceChangeListener(this);
        updateSmartPulldownSummary(mSmartPulldown.getIntValue(0));

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

        mCrDroidLogoColor =
                (ColorPickerPreference) findPreference(CRDROID_LOGO_COLOR);
        int intColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_COLOR, 0xFFFFFFFF,
                UserHandle.USER_CURRENT);
        String hexColor = ColorPickerPreference.convertToARGB(intColor);
        mCrDroidLogoColor.setNewPreviewColor(intColor);
        if (intColor != 0xFFFFFFFF) {
            mCrDroidLogoColor.setSummary(hexColor);
        } else {
            mCrDroidLogoColor.setSummary(R.string.default_string);
        }
        mCrDroidLogoColor.setOnPreferenceChangeListener(this);

        mBatteryPercent = (ListPreference) findPreference(STATUS_BAR_SHOW_BATTERY_PERCENT);
        mTextSymbol = (ListPreference) findPreference(TEXT_CHARGING_SYMBOL);

        mBatteryStyle = (ListPreference) findPreference(STATUS_BAR_BATTERY_STYLE);
        int batterystyle = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_BATTERY_STYLE, BATTERY_STYLE_CIRCLE,
                UserHandle.USER_CURRENT);
        mBatteryStyle.setOnPreferenceChangeListener(this);

        updateBatteryOptions(batterystyle);

        mSuIndicator = (SwitchPreference) findPreference(SU_INDICATOR);
        if (!FileUtils.fileExists("/system/xbin/su"))
            prefScreen.removePreference(mSuIndicator);

        mOldType = (SwitchPreference) findPreference(KEY_OLD_MOBILETYPE);
        mDataDisabled = (SwitchPreference) findPreference(KEY_SHOW_DATA_DISABLED);
        mShowFourg = (SwitchPreference) findPreference(KEY_SHOW_FOURG);
        mShowRoaming = (SwitchPreference) findPreference(KEY_SHOW_ROAMING);
        mShowVolte = (SwitchPreference) findPreference(KEY_SHOW_VOLTE);

        if (!TelephonyUtils.isVoiceCapable(getActivity())) {
            prefScreen.removePreference(mOldType);
            prefScreen.removePreference(mDataDisabled);
            prefScreen.removePreference(mShowFourg);
            prefScreen.removePreference(mShowRoaming);
            prefScreen.removePreference(mShowVolte);
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
        } else if (preference == mCrDroidLogoColor) {
            String hex = ColorPickerPreference.convertToARGB(
                Integer.parseInt(String.valueOf(newValue)));
            int value = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_COLOR, value,
                UserHandle.USER_CURRENT);
            if (value != 0xFFFFFFFF) {
                mCrDroidLogoColor.setSummary(hex);
            } else {
                mCrDroidLogoColor.setSummary(R.string.default_string);
            }
            return true;
        } else if (preference == mBatteryStyle) {
            int value = Integer.parseInt((String) newValue);
            updateBatteryOptions(value);
            return true;
        }
        return false;
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

    private void updateBatteryOptions(int batterystyle) {
        mBatteryPercent.setEnabled(batterystyle != BATTERY_STYLE_TEXT && batterystyle != BATTERY_STYLE_HIDDEN);
        mTextSymbol.setEnabled(batterystyle == BATTERY_STYLE_TEXT);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

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
                Settings.System.DATA_DISABLED_ICON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SMART_PULLDOWN, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ROAMING_INDICATOR_ICON, 1, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_FOURG_ICON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_SU_INDICATOR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_VOLTE_ICON, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_BATTERY_STYLE, BATTERY_STYLE_CIRCLE, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_COLOR, 0xFFFFFFFF, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_POSITION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.TEXT_CHARGING_SYMBOL, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.USE_OLD_MOBILETYPE, 0, UserHandle.USER_CURRENT);
        BatteryBar.reset(mContext);
        Clock.reset(mContext);
        NetworkTrafficSettings.reset(mContext);
        Ticker.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.crdroid_settings_statusbar;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    if (!FileUtils.fileExists("/system/xbin/su"))
                        keys.add(SU_INDICATOR);

                    if (!TelephonyUtils.isVoiceCapable(context)) {
                        keys.add(KEY_SHOW_FOURG);
                        keys.add(KEY_SHOW_ROAMING);
                        keys.add(KEY_SHOW_VOLTE);
                    }

                    return keys;
                }
            };
}
