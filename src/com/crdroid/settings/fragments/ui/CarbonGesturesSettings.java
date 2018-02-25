/*
 * Copyright (C) 2018 CarbonROM
 *               2018 crDroid Android Project
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
package com.crdroid.settings.fragments.ui;

import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.ListPreference;
import android.support.v14.preference.SwitchPreference;

import com.crdroid.settings.R;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.utils.HAFRAppChooserDialog;
import com.crdroid.settings.utils.HAFRAppChooserAdapter.AppItem;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

public class CarbonGesturesSettings extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    private static final String TAG = "CarbonGestures";
    private CustomSeekBarPreference mCarbonGestureFingers;
    private ListPreference mCarbonGestureRight;
    private ListPreference mCarbonGestureLeft;
    private ListPreference mCarbonGestureUp;
    private ListPreference mCarbonGestureDown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.carbon_gestures);

        ContentResolver resolver = getActivity().getContentResolver();

        mCarbonGestureFingers = (CustomSeekBarPreference) findPreference("carbon_gestures_fingers");
        mCarbonGestureFingers.setOnPreferenceChangeListener(this);
        int carbonGestureFingers = Settings.System.getIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_FINGERS,
                2, UserHandle.USER_CURRENT);
        mCarbonGestureFingers.setValue(carbonGestureFingers);

        mCarbonGestureRight = (ListPreference) findPreference("carbon_gestures_right");
        mCarbonGestureRight.setOnPreferenceChangeListener(this);
        int carbonGestureRight = Settings.System.getIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_RIGHT,
                0, UserHandle.USER_CURRENT);
        mCarbonGestureRight.setValue(String.valueOf(carbonGestureRight));
        if (carbonGestureRight == 1001) {
            setApplicationNamePreferenceSummary(Settings.System.getStringForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_RIGHT,
                UserHandle.USER_CURRENT), mCarbonGestureRight);
        } else {
            mCarbonGestureRight.setSummary(mCarbonGestureRight.getEntry());
        }

        mCarbonGestureLeft = (ListPreference) findPreference("carbon_gestures_left");
        mCarbonGestureLeft.setOnPreferenceChangeListener(this);
        int carbonGestureLeft = Settings.System.getIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_LEFT,
                0, UserHandle.USER_CURRENT);
        mCarbonGestureLeft.setValue(String.valueOf(carbonGestureLeft));
        if (carbonGestureLeft == 1001) {
            setApplicationNamePreferenceSummary(Settings.System.getStringForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_LEFT,
                UserHandle.USER_CURRENT), mCarbonGestureLeft);
        } else {
            mCarbonGestureLeft.setSummary(mCarbonGestureLeft.getEntry());
        }

        mCarbonGestureUp = (ListPreference) findPreference("carbon_gestures_up");
        mCarbonGestureUp.setOnPreferenceChangeListener(this);
        int carbonGestureUp = Settings.System.getIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_UP,
                0, UserHandle.USER_CURRENT);
        mCarbonGestureUp.setValue(String.valueOf(carbonGestureUp));
        if (carbonGestureUp == 1001) {
            setApplicationNamePreferenceSummary(Settings.System.getStringForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_UP,
                UserHandle.USER_CURRENT), mCarbonGestureUp);
        } else {
            mCarbonGestureUp.setSummary(mCarbonGestureUp.getEntry());
        }

        mCarbonGestureDown = (ListPreference) findPreference("carbon_gestures_down");
        mCarbonGestureDown.setOnPreferenceChangeListener(this);
        int carbonGestureDown = Settings.System.getIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_DOWN,
                0, UserHandle.USER_CURRENT);
        mCarbonGestureDown.setValue(String.valueOf(carbonGestureDown));
        if (carbonGestureDown == 1001) {
            setApplicationNamePreferenceSummary(Settings.System.getStringForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_DOWN,
                UserHandle.USER_CURRENT), mCarbonGestureDown);
        } else {
            mCarbonGestureDown.setSummary(mCarbonGestureDown.getEntry());
        }
    }

    private void launchAppChooseDialog(String setting, ListPreference pref) {
        HAFRAppChooserDialog dDialog = new HAFRAppChooserDialog(getActivity()) {
            @Override
            public void onListViewItemClick(AppItem info, int id) {
                Settings.System.putStringForUser(getContentResolver(),
                    setting, info.packageName, UserHandle.USER_CURRENT);
                setApplicationNamePreferenceSummary(info.packageName, pref);
            }
        };
        dDialog.setCancelable(true);
        dDialog.setLauncherFilter(true);
        dDialog.show(1);
        dDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                setPreferenceToDefaults(pref);
            }
        });
    }

    private void setApplicationNamePreferenceSummary(String pkg, ListPreference pref) {
        PackageManager packageManager = getActivity().getApplicationContext().getPackageManager();
        String packageInfo = "";
        try {
            packageInfo = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        pref.setSummary(getActivity().getString(R.string.carbon_gesture_launch) + " " + packageInfo);
    }

    private void setPreferenceToDefaults(ListPreference pref) {
        if (pref.equals(mCarbonGestureRight)) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_RIGHT, 0, UserHandle.USER_CURRENT);
        } else if (pref.equals(mCarbonGestureLeft)) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_LEFT, 0, UserHandle.USER_CURRENT);
        } else if (pref.equals(mCarbonGestureUp)) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_UP, 0, UserHandle.USER_CURRENT);
        } else if (pref.equals(mCarbonGestureDown)) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_DOWN, 0, UserHandle.USER_CURRENT);
        }
        pref.setValue(String.valueOf(0));
        pref.setSummary(pref.getEntry());
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_RIGHT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_LEFT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_UP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.CARBON_CUSTOM_GESTURE_DOWN, 0, UserHandle.USER_CURRENT);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference.equals(mCarbonGestureFingers)) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_FINGERS, (Integer) objValue, UserHandle.USER_CURRENT);
            return true;
        }

        if (preference.equals(mCarbonGestureRight)) {
            int carbonGestureRight = Integer.parseInt(((String) objValue).toString());
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_RIGHT, carbonGestureRight, UserHandle.USER_CURRENT);
            int index = mCarbonGestureRight.findIndexOfValue((String) objValue);
            if (carbonGestureRight == 1001) {
                launchAppChooseDialog(Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_RIGHT, mCarbonGestureRight);
            } else {
                mCarbonGestureRight.setSummary(
                        mCarbonGestureRight.getEntries()[index]);
            }
            return true;
        }

        if (preference.equals(mCarbonGestureLeft)) {
            int carbonGestureLeft = Integer.parseInt(((String) objValue).toString());
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_LEFT, carbonGestureLeft, UserHandle.USER_CURRENT);
            int index = mCarbonGestureLeft.findIndexOfValue((String) objValue);
            if (carbonGestureLeft == 1001) {
                launchAppChooseDialog(Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_LEFT, mCarbonGestureLeft);
            } else {
                mCarbonGestureLeft.setSummary(
                        mCarbonGestureLeft.getEntries()[index]);
            }
            return true;
        }

        if (preference.equals(mCarbonGestureUp)) {
            int carbonGestureUp = Integer.parseInt(((String) objValue).toString());
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_UP, carbonGestureUp, UserHandle.USER_CURRENT);
            int index = mCarbonGestureUp.findIndexOfValue((String) objValue);
            if (carbonGestureUp == 1001) {
                launchAppChooseDialog(Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_UP, mCarbonGestureUp);
            } else {
                mCarbonGestureUp.setSummary(
                        mCarbonGestureUp.getEntries()[index]);
            }
            return true;
        }

        if (preference.equals(mCarbonGestureDown)) {
            int carbonGestureDown = Integer.parseInt(((String) objValue).toString());
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CARBON_CUSTOM_GESTURE_DOWN, carbonGestureDown, UserHandle.USER_CURRENT);
            int index = mCarbonGestureDown.findIndexOfValue((String) objValue);
            if (carbonGestureDown == 1001) {
                launchAppChooseDialog(Settings.System.CARBON_CUSTOM_GESTURE_PACKAGE_DOWN, mCarbonGestureDown);
            } else {
                mCarbonGestureDown.setSummary(
                        mCarbonGestureDown.getEntries()[index]);
            }
            return true;
        }

        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
