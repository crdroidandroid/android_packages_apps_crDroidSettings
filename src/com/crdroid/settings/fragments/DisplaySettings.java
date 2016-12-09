package com.crdroid.settings.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class DisplaySettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_DOZE_FRAGMENT = "doze_fragment";

    private SwitchPreference mDozeFragement;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_display);

        mDozeFragement = (SwitchPreference) findPreference(KEY_DOZE_FRAGMENT);
        mDozeFragement.setChecked(isDozeEnabled());
        mDozeFragement.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mDozeFragement) {
            boolean value = (Boolean) newValue;
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.DOZE_ENABLED, value ? 1 : 0);
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mDozeFragement != null) {
            mDozeFragement.setSummary(isDozeEnabled()
                    ? R.string.summary_doze_enabled : R.string.summary_doze_disabled);
        }
    }

    public boolean isDozeEnabled() {
        return Settings.Secure.getInt(getContentResolver(), Settings.Secure.DOZE_ENABLED,
                getActivity().getResources().getBoolean(
                com.android.internal.R.bool.config_doze_enabled_by_default) ? 1 : 0) != 0;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }
}
