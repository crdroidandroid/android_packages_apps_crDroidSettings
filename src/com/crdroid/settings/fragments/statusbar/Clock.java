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
package com.crdroid.settings.fragments.statusbar;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Menu;
import android.widget.EditText;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;

import java.util.Date;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

public class Clock extends SettingsPreferenceFragment {
//            implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "Clock";

    private static final String STATUS_BAR_AM_PM = "status_bar_am_pm";
/*
    private static final String CLOCK_DATE_DISPLAY = "status_bar_date";
    private static final String CLOCK_DATE_POSITION = "statusbar_clock_date_position";
    private static final String CLOCK_DATE_STYLE = "status_bar_date_style";
    private static final String CLOCK_DATE_FORMAT = "status_bar_date_format";

    public static final int CLOCK_DATE_STYLE_LOWERCASE = 1;
    public static final int CLOCK_DATE_STYLE_UPPERCASE = 2;
    private static final int CUSTOM_CLOCK_DATE_FORMAT_INDEX = 18;
*/

    private LineageSystemSettingListPreference mStatusBarAmPm;

/*
    private ListPreference mClockDateDisplay;
    private ListPreference mClockDatePosition;
    private ListPreference mClockDateStyle;
    private ListPreference mClockDateFormat;
*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.status_bar_clock);

        ContentResolver resolver = getActivity().getContentResolver();

        mStatusBarAmPm =
                (LineageSystemSettingListPreference) findPreference(STATUS_BAR_AM_PM);

        if (DateFormat.is24HourFormat(getActivity())) {
            mStatusBarAmPm.setEnabled(false);
            mStatusBarAmPm.setSummary(R.string.status_bar_am_pm_info);
        }

/*
        mClockDateDisplay = (ListPreference) findPreference(CLOCK_DATE_DISPLAY);
        int dateDisplay = Settings.System.getIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY, 0, UserHandle.USER_CURRENT);
        mClockDateDisplay.setValue(Integer.toString(dateDisplay));
        mClockDateDisplay.setSummary(mClockDateDisplay.getEntry());
        mClockDateDisplay.setOnPreferenceChangeListener(this);

        mClockDatePosition = (ListPreference) findPreference(CLOCK_DATE_POSITION);
        mClockDatePosition.setValue(Integer.toString(Settings.System.getIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_POSITION, 0, UserHandle.USER_CURRENT)));
        mClockDatePosition.setSummary(mClockDatePosition.getEntry());
        mClockDatePosition.setEnabled(dateDisplay > 0);
        mClockDatePosition.setOnPreferenceChangeListener(this);

        mClockDateStyle = (ListPreference) findPreference(CLOCK_DATE_STYLE);
        mClockDateStyle.setValue(Integer.toString(Settings.System.getIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_STYLE, 0, UserHandle.USER_CURRENT)));
        mClockDateStyle.setSummary(mClockDateStyle.getEntry());
        mClockDateStyle.setEnabled(dateDisplay > 0);
        mClockDateStyle.setOnPreferenceChangeListener(this);

        mClockDateFormat = (ListPreference) findPreference(CLOCK_DATE_FORMAT);
        if (mClockDateFormat.getValue() == null) {
            mClockDateFormat.setValue("EEE");
        }
        parseClockDateFormats();
        mClockDateFormat.setEnabled(dateDisplay > 0);
        mClockDateFormat.setOnPreferenceChangeListener(this);
*/
    }

/*
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      AlertDialog dialog;
      ContentResolver resolver = getActivity().getContentResolver();
      if (preference == mClockDateDisplay) {
          int val = Integer.parseInt((String) newValue);
          int index = mClockDateDisplay.findIndexOfValue((String) newValue);
          Settings.System.putIntForUser(resolver,
                  Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY, val, UserHandle.USER_CURRENT);
          mClockDateDisplay.setSummary(mClockDateDisplay.getEntries()[index]);
          if (val == 0) {
              mClockDatePosition.setEnabled(false);
              mClockDateStyle.setEnabled(false);
              mClockDateFormat.setEnabled(false);
          } else {
              mClockDatePosition.setEnabled(true);
              mClockDateStyle.setEnabled(true);
              mClockDateFormat.setEnabled(true);
          }
          return true;
        } else if (preference == mClockDatePosition) {
            int val = Integer.parseInt((String) newValue);
            int index = mClockDatePosition.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.STATUSBAR_CLOCK_DATE_POSITION, val, UserHandle.USER_CURRENT);
            mClockDatePosition.setSummary(mClockDatePosition.getEntries()[index]);
            parseClockDateFormats();
            return true;
      } else if (preference == mClockDateStyle) {
          int val = Integer.parseInt((String) newValue);
          int index = mClockDateStyle.findIndexOfValue((String) newValue);
          Settings.System.putIntForUser(resolver,
                  Settings.System.STATUSBAR_CLOCK_DATE_STYLE, val, UserHandle.USER_CURRENT);
          mClockDateStyle.setSummary(mClockDateStyle.getEntries()[index]);
          parseClockDateFormats();
          return true;
      } else if (preference == mClockDateFormat) {
          int index = mClockDateFormat.findIndexOfValue((String) newValue);

          if (index == CUSTOM_CLOCK_DATE_FORMAT_INDEX) {
              AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
              alert.setTitle(R.string.status_bar_date_string_edittext_title);
              alert.setMessage(R.string.status_bar_date_string_edittext_summary);

              final EditText input = new EditText(getActivity());
              String oldText = Settings.System.getString(
                  resolver,
                  Settings.System.STATUSBAR_CLOCK_DATE_FORMAT);
              if (oldText != null) {
                  input.setText(oldText);
              }
              alert.setView(input);

              alert.setPositiveButton(R.string.menu_save, new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialogInterface, int whichButton) {
                      String value = input.getText().toString();
                      if (value.equals("")) {
                          return;
                      }
                      Settings.System.putString(resolver,
                          Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, value);

                      return;
                  }
              });

              alert.setNegativeButton(R.string.menu_cancel,
                  new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialogInterface, int which) {
                      return;
                  }
              });
              dialog = alert.create();
              dialog.show();
          } else {
              if ((String) newValue != null) {
                  Settings.System.putString(resolver,
                      Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, (String) newValue);
              }
          }
          return true;
      }
      return false;
    }

    private void parseClockDateFormats() {
        String[] dateEntries = getResources().getStringArray(
                R.array.status_bar_date_format_entries_values);
        CharSequence parsedDateEntries[];
        parsedDateEntries = new String[dateEntries.length];
        Date now = new Date();

        int lastEntry = dateEntries.length - 1;
        int dateFormat = Settings.System.getIntForUser(getActivity()
                .getContentResolver(), Settings.System.STATUSBAR_CLOCK_DATE_STYLE, 0, UserHandle.USER_CURRENT);
        for (int i = 0; i < dateEntries.length; i++) {
            if (i == lastEntry) {
                parsedDateEntries[i] = dateEntries[i];
            } else {
                String newDate;
                CharSequence dateString = DateFormat.format(dateEntries[i], now);
                if (dateFormat == CLOCK_DATE_STYLE_LOWERCASE) {
                    newDate = dateString.toString().toLowerCase();
                } else if (dateFormat == CLOCK_DATE_STYLE_UPPERCASE) {
                    newDate = dateString.toString().toUpperCase();
                } else {
                    newDate = dateString.toString();
                }

                parsedDateEntries[i] = newDate;
            }
        }
        mClockDateFormat.setEntries(parsedDateEntries);
    }
*/

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

/*
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_CLOCK_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_POSITION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putString(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, "");
*/
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CLOCK_SECONDS, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_AM_PM, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
