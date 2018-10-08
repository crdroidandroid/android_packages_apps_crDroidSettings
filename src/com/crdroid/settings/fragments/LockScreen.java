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
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.UserHandle;
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

import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.fragments.lockscreen.LockScreenWeather;
import com.crdroid.settings.R;

import lineageos.providers.LineageSettings;

public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    public static final String TAG = "LockScreen";

    private static final String LOCKSCREEN_GESTURES_CATEGORY = "lockscreen_gestures_category";
    private static final String LOCKSCREEN_MAX_NOTIF_CONFIG = "lockscreen_max_notif_config";
    private static final String FP_SUCCESS_VIBRATE = "fp_success_vibrate";
    private static final String FP_WAKE_AND_UNLOCK = "fp_wake_and_unlock";
//    private static final String FP_UNLOCK_KEYSTORE = "fp_unlock_keystore";
    private static final String FACE_UNLOCK_PREF = "face_auto_unlock";
    private static final String FACE_UNLOCK_PACKAGE = "com.android.facelock";

    private CustomSeekBarPreference mMaxKeyguardNotifConfig;
    private FingerprintManager mFingerprintManager;
    private SwitchPreference mFingerprintVib;
    private SwitchPreference mFpWakeAndUnlock;
//    private SwitchPreference mFpKeystore;
    private Preference mFaceUnlock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);

        PreferenceCategory gestCategory = (PreferenceCategory) findPreference(LOCKSCREEN_GESTURES_CATEGORY);

        ContentResolver resolver = mContext.getContentResolver();

        mMaxKeyguardNotifConfig = (CustomSeekBarPreference) findPreference(LOCKSCREEN_MAX_NOTIF_CONFIG);
        int kgconf = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 5, UserHandle.USER_CURRENT);
        mMaxKeyguardNotifConfig.setValue(kgconf);
        mMaxKeyguardNotifConfig.setOnPreferenceChangeListener(this);

        mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintVib = (SwitchPreference) findPreference(FP_SUCCESS_VIBRATE);
        mFpWakeAndUnlock = (SwitchPreference) findPreference(FP_WAKE_AND_UNLOCK);
//        mFpKeystore = (SwitchPreference) findPreference(FP_UNLOCK_KEYSTORE);

        if (mFingerprintManager != null && mFingerprintManager.isHardwareDetected()){
            mFingerprintVib.setChecked((Settings.System.getIntForUser(resolver,
                    Settings.System.FP_SUCCESS_VIBRATE, 1, UserHandle.USER_CURRENT) == 1));
            mFingerprintVib.setOnPreferenceChangeListener(this);

            mFpWakeAndUnlock.setChecked(Settings.System.getIntForUser(resolver,
                Settings.System.FP_WAKE_AND_UNLOCK, 1, UserHandle.USER_CURRENT) == 1);
            mFpWakeAndUnlock.setOnPreferenceChangeListener(this);

/*
            mFpKeystore.setChecked((Settings.System.getIntForUser(resolver,
                    Settings.System.FP_UNLOCK_KEYSTORE, 0, UserHandle.USER_CURRENT) == 1));
            mFpKeystore.setOnPreferenceChangeListener(this);
*/
        } else {
            gestCategory.removePreference(mFingerprintVib);
            gestCategory.removePreference(mFpWakeAndUnlock);
//            gestCategory.removePreference(mFpKeystore);
        }

        mFaceUnlock = (Preference) findPreference(FACE_UNLOCK_PREF);

        if (!Utils.isPackageInstalled(mContext, FACE_UNLOCK_PACKAGE)) {
            mFaceUnlock.setEnabled(false);
            mFaceUnlock.setSummary(getActivity().getString(
                    R.string.face_auto_unlock_not_available));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mMaxKeyguardNotifConfig) {
            int value = (Integer) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, value, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mFingerprintVib) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.FP_SUCCESS_VIBRATE, value ? 1: 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mFpWakeAndUnlock) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.FP_WAKE_AND_UNLOCK, value ? 1: 0, UserHandle.USER_CURRENT);
            return true;
/*
        } else if (preference == mFpKeystore) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver,
                    Settings.System.FP_UNLOCK_KEYSTORE, value ? 1: 0, UserHandle.USER_CURRENT);
            return true;
*/
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BATTERY_INFO, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCK_SCREEN_CUSTOM_NOTIF, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 5, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.FACE_AUTO_UNLOCK, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_SUCCESS_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_DATE_SELECTION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);
/*
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_UNLOCK_KEYSTORE, 0, UserHandle.USER_CURRENT);
*/
        Settings.Global.putInt(resolver,
                Settings.Global.LOCKSCREEN_ENABLE_POWER_MENU, 1);
        Settings.Global.putInt(resolver,
                Settings.Global.LOCKSCREEN_ENABLE_QS, 1);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.LOCKSCREEN_MEDIA_METADATA, 1, UserHandle.USER_CURRENT);
        LockScreenWeather.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
