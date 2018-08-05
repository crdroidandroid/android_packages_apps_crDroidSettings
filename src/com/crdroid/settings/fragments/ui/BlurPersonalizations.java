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
package com.crdroid.settings.fragments.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.graphics.Color;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;

public class BlurPersonalizations extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    //Transluency,Radius and Scale
    private CustomSeekBarPreference mScale;
    private CustomSeekBarPreference mRadius;
    private CustomSeekBarPreference mRecentsRadius;
    private CustomSeekBarPreference mRecentsScale;
    private CustomSeekBarPreference mQuickSettPerc;
    private CustomSeekBarPreference mVolSettPerc;
    //private CustomSeekBarPreference mNotSettPerc;

    //Colors
    private ColorPickerPreference mDarkBlurColor;
    private ColorPickerPreference mLightBlurColor;
    private ColorPickerPreference mMixedBlurColor;

    public static int BLUR_LIGHT_COLOR_PREFERENCE_DEFAULT = Color.DKGRAY;
    public static int BLUR_MIXED_COLOR_PREFERENCE_DEFAULT = Color.GRAY;
    public static int BLUR_DARK_COLOR_PREFERENCE_DEFAULT = Color.LTGRAY;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.blur);
        PreferenceScreen prefSet = getPreferenceScreen();

        ContentResolver resolver = getActivity().getContentResolver();

        int intLightColor;
        int intDarkColor;
        int intMixedColor;
        String hexLightColor;
        String hexDarkColor;
        String hexMixedColor;

        mScale = (CustomSeekBarPreference) findPreference("blur_statusbar_scale");
        mScale.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_STATUSBAR_SCALE, 10, UserHandle.USER_CURRENT));
        mScale.setOnPreferenceChangeListener(this);

        mRadius = (CustomSeekBarPreference) findPreference("blur_statusbar_radius");
        mRadius.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_STATUSBAR_RADIUS, 5, UserHandle.USER_CURRENT));
        mRadius.setOnPreferenceChangeListener(this);

/*
        mNotSettPerc = (CustomSeekBarPreference) findPreference("blur_notifications_percentage");
        mNotSettPerc.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_NOTIFICATIONS_PERCENTAGE, 60, UserHandle.USER_CURRENT));
        mNotSettPerc.setOnPreferenceChangeListener(this);
*/

        mQuickSettPerc = (CustomSeekBarPreference) findPreference("blur_quicksettings_percentage");
        mQuickSettPerc.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_QUICKSETTINGS_PERCENTAGE, 60, UserHandle.USER_CURRENT));
        mQuickSettPerc.setOnPreferenceChangeListener(this);

        mVolSettPerc = (CustomSeekBarPreference) findPreference("blur_volumedialog_percentage");
        mVolSettPerc.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_VOLUMEDIALOG_PERCENTAGE, 60, UserHandle.USER_CURRENT));
        mVolSettPerc.setOnPreferenceChangeListener(this);

        mRecentsScale = (CustomSeekBarPreference) findPreference("blur_recent_scale");
        mRecentsScale.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_RECENT_SCALE, 6, UserHandle.USER_CURRENT));
        mRecentsScale.setOnPreferenceChangeListener(this);

        mRecentsRadius = (CustomSeekBarPreference) findPreference("blur_recent_radius");
        mRecentsRadius.setValue(Settings.System.getIntForUser(resolver, Settings.System.BLUR_RECENT_RADIUS, 3, UserHandle.USER_CURRENT));
        mRecentsRadius.setOnPreferenceChangeListener(this);

        mLightBlurColor = (ColorPickerPreference) findPreference("blur_light_color");
        intLightColor = Settings.System.getIntForUser(resolver, Settings.System.BLUR_LIGHT_COLOR, BLUR_LIGHT_COLOR_PREFERENCE_DEFAULT, UserHandle.USER_CURRENT);
        hexLightColor = String.format("#%08x", (0xffffffff & intLightColor));
        mLightBlurColor.setSummary(hexLightColor);
        mLightBlurColor.setNewPreviewColor(intLightColor);
        mLightBlurColor.setOnPreferenceChangeListener(this);

        mDarkBlurColor = (ColorPickerPreference) findPreference("blur_dark_color");
        intDarkColor = Settings.System.getIntForUser(resolver, Settings.System.BLUR_DARK_COLOR, BLUR_DARK_COLOR_PREFERENCE_DEFAULT, UserHandle.USER_CURRENT);
        hexDarkColor = String.format("#%08x", (0xffffffff & intDarkColor));
        mDarkBlurColor.setSummary(hexDarkColor);
        mDarkBlurColor.setNewPreviewColor(intDarkColor);
        mDarkBlurColor.setOnPreferenceChangeListener(this);

        mMixedBlurColor = (ColorPickerPreference) findPreference("blur_mixed_color");
        intMixedColor = Settings.System.getIntForUser(resolver, Settings.System.BLUR_MIXED_COLOR, BLUR_MIXED_COLOR_PREFERENCE_DEFAULT, UserHandle.USER_CURRENT);
        hexMixedColor = String.format("#%08x", (0xffffffff & intMixedColor));
        mMixedBlurColor.setSummary(hexMixedColor);
        mMixedBlurColor.setNewPreviewColor(intMixedColor);
        mMixedBlurColor.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mScale) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_STATUSBAR_SCALE, value, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mRadius) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_STATUSBAR_RADIUS, value, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mVolSettPerc) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_VOLUMEDIALOG_PERCENTAGE, value, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mQuickSettPerc) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_QUICKSETTINGS_PERCENTAGE, value, UserHandle.USER_CURRENT);
            return true;
/*
        } else if (preference == mNotSettPerc) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_NOTIFICATIONS_PERCENTAGE, value, UserHandle.USER_CURRENT);
            return true;
*/
        } else if (preference == mRecentsScale) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_RECENT_SCALE, value, UserHandle.USER_CURRENT);
            return true;
        } else if(preference == mRecentsRadius) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putIntForUser(
                resolver, Settings.System.BLUR_RECENT_RADIUS, value, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mLightBlurColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                    Settings.System.BLUR_LIGHT_COLOR, intHex, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mDarkBlurColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                    Settings.System.BLUR_DARK_COLOR, intHex, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mMixedBlurColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putIntForUser(resolver,
                    Settings.System.BLUR_MIXED_COLOR, intHex, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_STATUSBAR_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_STATUSBAR_SCALE, 10, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_STATUSBAR_RADIUS, 5, UserHandle.USER_CURRENT);
/*
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_NOTIFICATIONS_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_NOTIFICATIONS_PERCENTAGE, 70, UserHandle.USER_CURRENT);
*/
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_QUICKSETTINGS_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_QUICKSETTINGS_PERCENTAGE, 60, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_VOLUMEDIALOG_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_VOLUMEDIALOG_PERCENTAGE, 60, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_RECENT_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_RECENT_SCALE, 6, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_RECENT_RADIUS, 3, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_DARK_COLOR, Color.LTGRAY, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_LIGHT_COLOR, Color.DKGRAY, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUR_MIXED_COLOR, Color.GRAY, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
