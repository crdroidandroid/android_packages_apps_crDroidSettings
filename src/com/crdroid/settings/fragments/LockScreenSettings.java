package com.crdroid.settings.fragments;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.widget.Toast;

import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class LockScreenSettings extends SettingsPreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

}
