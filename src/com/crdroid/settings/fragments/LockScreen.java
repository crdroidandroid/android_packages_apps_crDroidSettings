/*
 * Copyright (C) 2016-2021 crDroid Android Project
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
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.FodUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.lockscreen.LockScreenWeather;
import com.crdroid.settings.utils.DeviceUtils;

import java.util.List;

import lineageos.providers.LineageSettings;

@SearchIndexable
public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    public static final String TAG = "LockScreen";

    private static final String LOCKSCREEN_INTERFACE_CATEGORY = "lockscreen_interface_category";
    private static final String LOCKSCREEN_GESTURES_CATEGORY = "lockscreen_gestures_category";
    private static final String FP_SUCCESS_VIBRATE = "fp_success_vibrate";
    private static final String FP_ERROR_VIBRATE = "fp_error_vibrate";

    private static final String FOD_ICON_PICKER_CATEGORY = "fod_icon_picker_category";
    private static final String FOD_ICON_PICKER = "fod_icon_picker";
    private static final String FOD_GESTURE = "fod_gesture";
    private static final String FOD_RECOGNIZING_ANIMATION = "fod_recognizing_animation";
    private static final String FOD_ANIM = "fod_anim";
    private static final String FOD_NIGHT_LIGHT = "fod_night_light";
    private static final String FOD_COLOR = "fod_color";
    private static final String FOD_FOOTER = "fod_footer";

    private static final String LOCKSCREEN_BLUR = "lockscreen_blur";

    private PreferenceCategory mFODIconPickerCategory;

    private Preference mFingerprintVib;
    private Preference mFingerprintVibErr;
    private Preference mLockscreenBlur;
    private Preference mScreenOffFOD;
    private Preference mFODnightlight;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);
        PreferenceScreen prefSet = getPreferenceScreen();
        Context mContext = getContext();

        mFODIconPickerCategory = findPreference(FOD_ICON_PICKER_CATEGORY);
        if (!FodUtils.hasFodSupport(getContext())) {
            prefSet.removePreference(mFODIconPickerCategory);
        } else {
            findPreference(FOD_FOOTER).setTitle(R.string.fod_pressed_color_footer);

            mScreenOffFOD = (Preference) findPreference(FOD_GESTURE);
            final boolean isScreenOffFodSupported = mContext.getResources().getBoolean(
                    R.bool.config_supportScreenOffFod);
            if (!isScreenOffFodSupported) {
                mFODIconPickerCategory.removePreference(mScreenOffFOD);
            }
            mFODnightlight = (Preference) findPreference(FOD_NIGHT_LIGHT);
            final boolean isFodNightLightSupported = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.disable_fod_night_light);
            if (!isFodNightLightSupported) {
                mFODIconPickerCategory.removePreference(mFODnightlight);
            }
        }

        PreferenceCategory gestCategory = (PreferenceCategory) findPreference(LOCKSCREEN_GESTURES_CATEGORY);

        FingerprintManager mFingerprintManager = (FingerprintManager)
                getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintVib = (Preference) findPreference(FP_SUCCESS_VIBRATE);
        mFingerprintVibErr = (Preference) findPreference(FP_ERROR_VIBRATE);

        if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
            gestCategory.removePreference(mFingerprintVib);
            gestCategory.removePreference(mFingerprintVibErr);
        }

        PreferenceCategory interfaceCategory = (PreferenceCategory) findPreference(LOCKSCREEN_INTERFACE_CATEGORY);

        mLockscreenBlur = (Preference) findPreference(LOCKSCREEN_BLUR);
        if (!DeviceUtils.isBlurSupported()) {
            interfaceCategory.removePreference(mLockscreenBlur);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.LOCKSCREEN_MEDIA_METADATA, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_ERROR_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_SUCCESS_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_ALBUMART_FILTER, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BATTERY_INFO, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BLUR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_CHARGING_ANIMATION_STYLE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_ENABLE_POWER_MENU, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_ENABLE_QS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_LOCK_ICON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_POWERMENU_SECURE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_STATUS_BAR, 1, UserHandle.USER_CURRENT);

        LockScreenWeather.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_lockscreen) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    FingerprintManager mFingerprintManager = (FingerprintManager)
                            context.getSystemService(Context.FINGERPRINT_SERVICE);
                    if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
                        keys.add(FP_SUCCESS_VIBRATE);
                        keys.add(FP_ERROR_VIBRATE);
                    }

                    if (!FodUtils.hasFodSupport(context)) {
                        keys.add(FOD_ICON_PICKER_CATEGORY);
                        keys.add(FOD_ICON_PICKER);
                        keys.add(FOD_GESTURE);
                        keys.add(FOD_RECOGNIZING_ANIMATION);
                        keys.add(FOD_ANIM);
                        keys.add(FOD_NIGHT_LIGHT);
                        keys.add(FOD_COLOR);
                    } else {
                        final boolean isScreenOffFodSupported = context.getResources().getBoolean(
                                R.bool.config_supportScreenOffFod);
                        if (!isScreenOffFodSupported) {
                            keys.add(FOD_GESTURE);
                        }
                        final boolean isFodNightLightSupported = context.getResources().getBoolean(
                                com.android.internal.R.bool.disable_fod_night_light);
                        if (!isFodNightLightSupported) {
                            keys.add(FOD_NIGHT_LIGHT);
                        }
                    }

                    if (!DeviceUtils.isBlurSupported()) {
                        keys.add(LOCKSCREEN_BLUR);
                    }

                    return keys;
                }
            };
}
