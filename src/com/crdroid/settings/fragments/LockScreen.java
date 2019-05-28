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
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.lockscreen.LockScreenVisualizer;
import com.crdroid.settings.fragments.lockscreen.LockScreenWeather;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.preferences.SystemSettingListPreference;

import java.util.List;
import java.util.ArrayList;

import lineageos.providers.LineageSettings;

public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener, Indexable  {

    public static final String TAG = "LockScreen";

    private static final String FACE_UNLOCK_PREF = "face_auto_unlock";
    private static final String FACE_UNLOCK_PACKAGE = "com.android.facelock";
    private static final String LOCKSCREEN_GESTURES_CATEGORY = "lockscreen_gestures_category";
    private static final String FP_SUCCESS_VIBRATE = "fp_success_vibrate";
    private static final String KEY_LOCK_SCREEN_WEATHER = "lock_screen_weather";

    private SwitchPreference mFaceUnlock;
    private Preference mFingerprintVib;
    private Preference mWeatherSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);

        PreferenceCategory gestCategory = (PreferenceCategory) findPreference(LOCKSCREEN_GESTURES_CATEGORY);

        boolean mFaceUnlockEnabled = Settings.Secure.getIntForUser(getActivity().getContentResolver(),
                Settings.Secure.FACE_AUTO_UNLOCK, getActivity().getResources().getBoolean(
                com.android.internal.R.bool.config_face_unlock_enabled_by_default) ? 1 : 0,
                UserHandle.USER_CURRENT) != 0;

        mFaceUnlock = (SwitchPreference) findPreference(FACE_UNLOCK_PREF);
        mFaceUnlock.setChecked(mFaceUnlockEnabled);

        if (!Utils.isPackageInstalled(getActivity(), FACE_UNLOCK_PACKAGE)) {
            mFaceUnlock.setEnabled(false);
            mFaceUnlock.setSummary(getActivity().getString(
                    R.string.face_auto_unlock_not_available));
        }

        FingerprintManager mFingerprintManager = (FingerprintManager) 
                getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintVib = (Preference) findPreference(FP_SUCCESS_VIBRATE);

        if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
            gestCategory.removePreference(mFingerprintVib);
        }

        mWeatherSettings = findPreference(KEY_LOCK_SCREEN_WEATHER);
        if (!Utils.isPackageInstalled(getActivity(), "com.crdroid.weather.client")) {
            mWeatherSettings.setEnabled(false);
            mWeatherSettings.setSummary(getActivity().getString(
                    R.string.weather_client_not_available));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver,
                Settings.Global.LOCKSCREEN_ENABLE_POWER_MENU, 1);
        Settings.Global.putInt(resolver,
                Settings.Global.LOCKSCREEN_ENABLE_QS, 1);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.LOCKSCREEN_ALBUMART_FILTER, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.FACE_AUTO_UNLOCK, mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_face_unlock_enabled_by_default) ? 1 : 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_SUCCESS_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCK_CLOCK_FONT_STYLE, 4, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCK_DATE_FONT_STYLE, 14, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BATTERY_INFO, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_CHARGING_ANIMATION, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.LOCKSCREEN_MEDIA_METADATA, 1, UserHandle.USER_CURRENT);
        LockScreenVisualizer.reset(mContext);
        LockScreenWeather.reset(mContext);
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
                    sir.xmlResId = R.xml.crdroid_settings_lockscreen;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    FingerprintManager mFingerprintManager = (FingerprintManager)
                            context.getSystemService(Context.FINGERPRINT_SERVICE);
                    if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
                        keys.add(FP_SUCCESS_VIBRATE);
                    }

                    return keys;
                }
            };
}
