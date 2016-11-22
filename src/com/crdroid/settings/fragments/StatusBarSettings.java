package com.crdroid.settings.fragments;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.text.format.DateFormat;
import android.provider.Settings;
import android.os.UserHandle;
import android.view.View;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.ColorPickerPreference;

import cyanogenmod.preference.CMSystemSettingListPreference;

public class StatusBarSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_CRDROID_LOGO_COLOR = "status_bar_crdroid_logo_color";
    private static final String KEY_CRDROID_LOGO_STYLE = "status_bar_crdroid_logo_style";
    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String STATUS_BAR_AM_PM = "status_bar_am_pm";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";

    private static final int STATUS_BAR_BATTERY_STYLE_HIDDEN = 4;
    private static final int STATUS_BAR_BATTERY_STYLE_TEXT = 6;

    private ColorPickerPreference mCrDroidLogoColor;
    private ListPreference mCrDroidLogoStyle;
    private CMSystemSettingListPreference mStatusBarClock;
    private CMSystemSettingListPreference mStatusBarAmPm;
    private CMSystemSettingListPreference mStatusBarBattery;
    private CMSystemSettingListPreference mStatusBarBatteryShowPercent;
    private CMSystemSettingListPreference mQuickPulldown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);
        final ContentResolver resolver = getActivity().getContentResolver();

        mCrDroidLogoStyle = (ListPreference) findPreference(KEY_CRDROID_LOGO_STYLE);
        int crdroidLogoStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);
        mCrDroidLogoStyle.setValue(String.valueOf(crdroidLogoStyle));
        mCrDroidLogoStyle.setSummary(mCrDroidLogoStyle.getEntry());
        mCrDroidLogoStyle.setOnPreferenceChangeListener(this);

        // CrDroid logo color
        mCrDroidLogoColor =
                (ColorPickerPreference) findPreference(KEY_CRDROID_LOGO_COLOR);
        mCrDroidLogoColor.setOnPreferenceChangeListener(this);
        int intColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, 0xffffffff);
        String hexColor = String.format("#%08x", (0xffffffff & intColor));
        mCrDroidLogoColor.setSummary(hexColor);
        mCrDroidLogoColor.setNewPreviewColor(intColor);

        mStatusBarClock = (CMSystemSettingListPreference) findPreference(STATUS_BAR_CLOCK_STYLE);
        mStatusBarAmPm = (CMSystemSettingListPreference) findPreference(STATUS_BAR_AM_PM);
        mStatusBarBattery = (CMSystemSettingListPreference) findPreference(STATUS_BAR_BATTERY_STYLE);
        mStatusBarBatteryShowPercent =
                (CMSystemSettingListPreference) findPreference(STATUS_BAR_SHOW_BATTERY_PERCENT);
        mQuickPulldown = (CMSystemSettingListPreference) findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);

        if (DateFormat.is24HourFormat(getActivity())) {
            mStatusBarAmPm.setEnabled(false);
            mStatusBarAmPm.setSummary(R.string.status_bar_am_pm_info);
        }

        mStatusBarBattery.setOnPreferenceChangeListener(this);
        enableStatusBarBatteryDependents(mStatusBarBattery.getIntValue(0));
        updatePulldownSummary(mQuickPulldown.getIntValue(0));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Adjust clock position for RTL if necessary
        Configuration config = getResources().getConfiguration();
        if (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                mStatusBarClock.setEntries(getActivity().getResources().getStringArray(
                        R.array.status_bar_clock_style_entries_rtl));
                mStatusBarClock.setSummary(mStatusBarClock.getEntry());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mCrDroidLogoColor) {
            String hex = ColorPickerPreference.convertToARGB(
                Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, intHex);
            return true;
        } else if (preference == mCrDroidLogoStyle) {
            int crdroidLogoStyle = Integer.valueOf((String) newValue);
            int index = mCrDroidLogoStyle.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, crdroidLogoStyle,
                UserHandle.USER_CURRENT);
            mCrDroidLogoStyle.setSummary(
                    mCrDroidLogoStyle.getEntries()[index]);
            return true;
        } else if (preference == mStatusBarBattery) {
            int batteryStyle = Integer.valueOf((String) newValue);
            enableStatusBarBatteryDependents(batteryStyle);
            return true;
        }

        return false;
    }

    private void enableStatusBarBatteryDependents(int batteryIconStyle) {
        if (batteryIconStyle == STATUS_BAR_BATTERY_STYLE_HIDDEN ||
                batteryIconStyle == STATUS_BAR_BATTERY_STYLE_TEXT) {
            mStatusBarBatteryShowPercent.setEnabled(false);
        } else {
            mStatusBarBatteryShowPercent.setEnabled(true);
        }
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    private void updatePulldownSummary(int value) {
        Resources res = getResources();

        if (value == 0) {
            // quick pulldown deactivated
            mQuickPulldown.setSummary(res.getString(R.string.status_bar_quick_qs_pulldown_off));
        } else {
            String direction = res.getString(value == 2
                    ? R.string.status_bar_quick_qs_pulldown_summary_left
                    : R.string.status_bar_quick_qs_pulldown_summary_right);
            mQuickPulldown.setSummary(res.getString(R.string.status_bar_quick_qs_pulldown_summary, direction));
        }
    }

}
