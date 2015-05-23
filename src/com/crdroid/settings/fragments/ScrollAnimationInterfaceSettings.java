package com.crdroid.settings.fragments;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.ListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import com.crdroid.settings.preferences.SeekBarPreference;

public class ScrollAnimationInterfaceSettings extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "ScrollAnimationInterfaceSettings";

    private static final String ANIMATION_FLING_VELOCITY = "animation_fling_velocity";
    private static final String ANIMATION_SCROLL_FRICTION = "animation_scroll_friction";
    private static final String ANIMATION_OVERSCROLL_DISTANCE = "animation_overscroll_distance";
    private static final String ANIMATION_OVERFLING_DISTANCE = "animation_overfling_distance";
    private static final float MULTIPLIER_SCROLL_FRICTION = 10000f;
    private static final String ANIMATION_NO_SCROLL = "animation_no_scroll";

    private static final int MENU_RESET = Menu.FIRST;

    private SeekBarPreference mAnimationFling;
    private SeekBarPreference mAnimationScroll;
    private SeekBarPreference mAnimationOverScroll;
    private SeekBarPreference mAnimationOverFling;
    private SwitchPreference mAnimNoScroll;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.scroll_animation_interface_settings);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver();

        mAnimNoScroll = (SwitchPreference) prefSet.findPreference(ANIMATION_NO_SCROLL);
        mAnimNoScroll.setChecked(Settings.System.getInt(resolver,
                Settings.System.ANIMATION_CONTROLS_NO_SCROLL, 0) == 1);
        mAnimNoScroll.setOnPreferenceChangeListener(this);

        float defaultScroll = Settings.System.getFloat(resolver,
                Settings.System.CUSTOM_SCROLL_FRICTION, ViewConfiguration.DEFAULT_SCROLL_FRICTION);
        mAnimationScroll = (SeekBarPreference) prefSet.findPreference(ANIMATION_SCROLL_FRICTION);
        mAnimationScroll.setValue((int) (defaultScroll * MULTIPLIER_SCROLL_FRICTION));
        mAnimationScroll.setOnPreferenceChangeListener(this);

        int defaultFling = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_FLING_VELOCITY, ViewConfiguration.DEFAULT_MAXIMUM_FLING_VELOCITY);
        mAnimationFling = (SeekBarPreference) prefSet.findPreference(ANIMATION_FLING_VELOCITY);
        mAnimationFling.setValue(defaultFling);
        mAnimationFling.setOnPreferenceChangeListener(this);

        int defaultOverScroll = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_OVERSCROLL_DISTANCE, ViewConfiguration.DEFAULT_OVERSCROLL_DISTANCE);
        mAnimationOverScroll = (SeekBarPreference) prefSet.findPreference(ANIMATION_OVERSCROLL_DISTANCE);
        mAnimationOverScroll.setValue(defaultOverScroll);
        mAnimationOverScroll.setOnPreferenceChangeListener(this);

        int defaultOverFling = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_OVERFLING_DISTANCE, ViewConfiguration.DEFAULT_OVERFLING_DISTANCE);
        mAnimationOverFling = (SeekBarPreference) prefSet.findPreference(ANIMATION_OVERFLING_DISTANCE);
        mAnimationOverFling.setValue(defaultOverFling);
        mAnimationOverFling.setOnPreferenceChangeListener(this);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
                .setIcon(R.drawable.ic_settings_backup_restore) // use the backup icon
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                resetToDefault();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void resetToDefault() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(R.string.reset);
        alertDialog.setMessage(R.string.animation_settings_reset_message);
        alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                resetAllValues();
                resetAllSettings();
            }
        });
        alertDialog.setNegativeButton(R.string.cancel, null);
        alertDialog.create().show();
    }

    private void resetAllValues() {
        mAnimationFling.setValue(ViewConfiguration.DEFAULT_MAXIMUM_FLING_VELOCITY);
        mAnimationScroll.setValue((int) (ViewConfiguration.DEFAULT_SCROLL_FRICTION * MULTIPLIER_SCROLL_FRICTION));
        mAnimationOverScroll.setValue(ViewConfiguration.DEFAULT_OVERSCROLL_DISTANCE);
        mAnimationOverFling.setValue(ViewConfiguration.DEFAULT_OVERFLING_DISTANCE);
        mAnimNoScroll.setChecked(false);
    }

    private void resetAllSettings() {
        setProperVal(mAnimationFling, ViewConfiguration.DEFAULT_MAXIMUM_FLING_VELOCITY);
        Settings.System.putFloat(getActivity().getContentResolver(),
                   Settings.System.CUSTOM_SCROLL_FRICTION, ViewConfiguration.DEFAULT_SCROLL_FRICTION);
        setProperVal(mAnimationOverScroll, ViewConfiguration.DEFAULT_OVERSCROLL_DISTANCE);
        setProperVal(mAnimationOverFling, ViewConfiguration.DEFAULT_OVERFLING_DISTANCE);
        setProperVal(mAnimNoScroll, 0);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mAnimNoScroll) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver, Settings.System.ANIMATION_CONTROLS_NO_SCROLL, value ? 1 : 0);
            if (value)
                Toast.makeText(getActivity(), R.string.scroll_disclaimer_summary,
                        Toast.LENGTH_LONG).show();
            return true;
        } else if (preference == mAnimationScroll) {
            int val = (Integer) newValue;
            Settings.System.putFloat(resolver,
                   Settings.System.CUSTOM_SCROLL_FRICTION,
                   ((float) (val / MULTIPLIER_SCROLL_FRICTION)));
            return true;
        } else if (preference == mAnimationFling) {
            int val = (Integer) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.CUSTOM_FLING_VELOCITY,
                    val);
            return true;
        } else if (preference == mAnimationOverScroll) {
            int val = (Integer) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.CUSTOM_OVERSCROLL_DISTANCE,
                    val);
            return true;
        } else if (preference == mAnimationOverFling) {
            int val = (Integer) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.CUSTOM_OVERFLING_DISTANCE,
                    val);
            return true;
        }
        return false;
    }

    private void setProperVal(Preference preference, int val) {
        String mString = "";

        if (preference == mAnimNoScroll) {
            mString = Settings.System.ANIMATION_CONTROLS_NO_SCROLL;
        } else if (preference == mAnimationFling) {
            mString = Settings.System.CUSTOM_FLING_VELOCITY;
        } else if (preference == mAnimationOverScroll) {
            mString = Settings.System.CUSTOM_OVERSCROLL_DISTANCE;
        } else if (preference == mAnimationOverFling) {
            mString = Settings.System.CUSTOM_OVERFLING_DISTANCE;
        } else {
            return;
        }

        Settings.System.putInt(getActivity().getContentResolver(), mString, val);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }
}
