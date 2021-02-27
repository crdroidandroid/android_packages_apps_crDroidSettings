/*
 * Copyright (C) 2021 Yet Another AOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments.ui.doze;

import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.SecureSettingListPreference;

import java.time.format.DateTimeFormatter;
import java.time.LocalTime;

public class AODSchedule extends SettingsPreferenceFragment implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    private static final String TAG = "AODSchedule";
    private static final String MODE_KEY = "doze_always_on_auto_mode";
    private static final String SINCE_PREF_KEY = "doze_always_on_auto_since";
    private static final String TILL_PREF_KEY = "doze_always_on_auto_till";

    private static final int MODE_DISABLED = 0;
    private static final int MODE_NIGHT = 1;
    private static final int MODE_TIME = 2;
    private static final int MODE_MIXED_SUNSET = 3;
    private static final int MODE_MIXED_SUNRISE = 4;

    private SecureSettingListPreference mModePref;
    private Preference mSincePref;
    private Preference mTillPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.always_on_display_schedule);

        ContentResolver resolver = getActivity().getContentResolver();

        mSincePref = findPreference(SINCE_PREF_KEY);
        mSincePref.setOnPreferenceClickListener(this);
        mTillPref = findPreference(TILL_PREF_KEY);
        mTillPref.setOnPreferenceClickListener(this);

        int mode = Settings.Secure.getIntForUser(resolver,
                MODE_KEY, MODE_DISABLED, UserHandle.USER_CURRENT);
        mModePref = (SecureSettingListPreference) findPreference(MODE_KEY);
        mModePref.setValue(String.valueOf(mode));
        mModePref.setSummary(mModePref.getEntry());
        mModePref.setOnPreferenceChangeListener(this);

        updateTimeEnablement(mode);
        updateTimeSummary(mode);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        int value = Integer.parseInt((String) objValue);
        int index = mModePref.findIndexOfValue((String) objValue);
        mModePref.setSummary(mModePref.getEntries()[index]);
        Settings.Secure.putIntForUser(getActivity().getContentResolver(),
                MODE_KEY, value, UserHandle.USER_CURRENT);
        updateTimeEnablement(value);
        updateTimeSummary(value);
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String[] times = getCustomTimeSetting();
        boolean isSince = preference == mSincePref;
        int hour, minute;
        TimePickerDialog.OnTimeSetListener listener = (view, hourOfDay, minute1) -> {
            updateTimeSetting(isSince, hourOfDay, minute1);
        };
        if (isSince) {
            String[] sinceValues = times[0].split(":", 0);
            hour = Integer.parseInt(sinceValues[0]);
            minute = Integer.parseInt(sinceValues[1]);
        } else {
            String[] tillValues = times[1].split(":", 0);
            hour = Integer.parseInt(tillValues[0]);
            minute = Integer.parseInt(tillValues[1]);
        }
        TimePickerDialog dialog = new TimePickerDialog(getContext(), listener,
                hour, minute, DateFormat.is24HourFormat(getContext()));
        dialog.show();
        return true;
    }

    private String[] getCustomTimeSetting() {
        String value = Settings.Secure.getStringForUser(getActivity().getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON_AUTO_TIME, UserHandle.USER_CURRENT);
        if (value == null || value.equals("")) value = "20:00,07:00";
        return value.split(",", 0);
    }

    private void updateTimeEnablement(int mode) {
        mSincePref.setEnabled(mode == MODE_TIME || mode == MODE_MIXED_SUNRISE);
        mTillPref.setEnabled(mode == MODE_TIME || mode == MODE_MIXED_SUNSET);
    }

    private void updateTimeSummary(int mode) {
        updateTimeSummary(getCustomTimeSetting(), mode);
    }

    private void updateTimeSummary(String[] times, int mode) {
        if (mode == MODE_DISABLED) {
            mSincePref.setSummary("-");
            mTillPref.setSummary("-");
            return;
        }

        if (mode == MODE_NIGHT) {
            mSincePref.setSummary(R.string.always_on_display_schedule_sunset);
            mTillPref.setSummary(R.string.always_on_display_schedule_sunrise);
            return;
        }

        String outputFormat = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "hh:mm a";
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputFormat);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime sinceDT = LocalTime.parse(times[0], formatter);
        LocalTime tillDT = LocalTime.parse(times[1], formatter);

        if (mode == MODE_MIXED_SUNSET) {
            mSincePref.setSummary(R.string.always_on_display_schedule_sunset);
            mTillPref.setSummary(tillDT.format(outputFormatter));
        } else if (mode == MODE_MIXED_SUNRISE) {
            mTillPref.setSummary(R.string.always_on_display_schedule_sunrise);
            mSincePref.setSummary(sinceDT.format(outputFormatter));
        } else {
            mSincePref.setSummary(sinceDT.format(outputFormatter));
            mTillPref.setSummary(tillDT.format(outputFormatter));
        }
    }

    private void updateTimeSetting(boolean since, int hour, int minute) {
        String[] times = getCustomTimeSetting();
        String nHour = "";
        String nMinute = "";
        if (hour < 10) nHour += "0";
        if (minute < 10) nMinute += "0";
        nHour += String.valueOf(hour);
        nMinute += String.valueOf(minute);
        times[since ? 0 : 1] = nHour + ":" + nMinute;
        Settings.Secure.putStringForUser(getActivity().getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON_AUTO_TIME,
                times[0] + "," + times[1], UserHandle.USER_CURRENT);
        updateTimeSummary(times, Integer.parseInt(mModePref.getValue()));
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
