package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings.System;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class SoundSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String PREF_LESS_NOTIFICATION_SOUNDS = "less_notification_sounds";

    private ListPreference mAnnoyingNotifications;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_sound);

        mAnnoyingNotifications = (ListPreference) findPreference(PREF_LESS_NOTIFICATION_SOUNDS);
        int notificationThreshold = System.getInt(getContentResolver(),
                System.MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD, 0);
        mAnnoyingNotifications.setValue(Integer.toString(notificationThreshold));
        mAnnoyingNotifications.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mAnnoyingNotifications) {
            int val = Integer.parseInt((String) newValue);
            System.putInt(getContentResolver(),
                    System.MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD, val);
            return true;
        }
        return false;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }
}
