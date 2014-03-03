package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import java.util.ArrayList;
import java.util.List;

public class AnimationSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_TOAST_ANIMATION = "toast_animation";

    private ListPreference mToastAnimation;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.crdroid_settings_animation);
        final ContentResolver resolver = getActivity().getContentResolver();

        mToastAnimation = (ListPreference) findPreference(KEY_TOAST_ANIMATION);
        mToastAnimation.setSummary(mToastAnimation.getEntry());
        int CurrentToastAnimation = Settings.System.getInt(resolver, Settings.System.TOAST_ANIMATION, 1);
        mToastAnimation.setValueIndex(CurrentToastAnimation); //set to index of default value
        mToastAnimation.setSummary(mToastAnimation.getEntries()[CurrentToastAnimation]);
        mToastAnimation.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mToastAnimation) {
            int index = mToastAnimation.findIndexOfValue((String) newValue);
            Settings.System.putString(resolver, Settings.System.TOAST_ANIMATION, (String) newValue);
            mToastAnimation.setSummary(mToastAnimation.getEntries()[index]);
            Toast.makeText(getActivity(), R.string.toast_animation_test,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }
}
