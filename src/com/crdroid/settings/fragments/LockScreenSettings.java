package com.crdroid.settings.fragments;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import com.android.settings.R;
import com.crdroid.settings.preferences.SeekBarPreference;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import com.crdroid.settings.preferences.SystemSettingSwitchPreference;

public class LockScreenSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String LS_GEST_CAT = "lockscreen_gestures_category";
    private static final String LOCKSCREEN_MAX_NOTIF_CONFIG = "lockscreen_max_notif_cofig";
    private static final String FP_SUCCESS_VIBRATE = "fp_success_vibrate";
    private static final String FP_UNLOCK_KEYSTORE = "fp_unlock_keystore";

    private SeekBarPreference mMaxKeyguardNotifConfig;
    private FingerprintManager mFingerprintManager;
    private SystemSettingSwitchPreference mFingerprintVib;
    private SystemSettingSwitchPreference mFpKeystore;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ContentResolver resolver = getActivity().getContentResolver();
        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);

        PreferenceScreen prefSet = getPreferenceScreen();
        PreferenceCategory gestCategory = (PreferenceCategory) findPreference(LS_GEST_CAT);

        mMaxKeyguardNotifConfig = (SeekBarPreference) findPreference(LOCKSCREEN_MAX_NOTIF_CONFIG);
        int kgconf = Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, 5);
        mMaxKeyguardNotifConfig.setValue(kgconf);
        mMaxKeyguardNotifConfig.setOnPreferenceChangeListener(this);

        mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintVib = (SystemSettingSwitchPreference) findPreference(FP_SUCCESS_VIBRATE);
        mFingerprintVib.setChecked((Settings.System.getInt(resolver,
                Settings.System.FP_SUCCESS_VIBRATE, 1) == 1));

        mFpKeystore = (SystemSettingSwitchPreference) findPreference(FP_UNLOCK_KEYSTORE);
        mFpKeystore.setChecked((Settings.System.getInt(resolver,
                Settings.System.FP_UNLOCK_KEYSTORE, 0) == 1));

        if (!mFingerprintManager.isHardwareDetected()){
            gestCategory.removePreference(mFingerprintVib);
            gestCategory.removePreference(mFpKeystore);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mMaxKeyguardNotifConfig) {
            int kgconf = (Integer) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.LOCKSCREEN_MAX_NOTIF_CONFIG, kgconf);
            return true;
        }
        return false;
    }
}
