package com.crdroid.settings.fragments;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.crdroid.settings.preferences.SeekBarPreference;
import com.android.internal.logging.MetricsProto.MetricsEvent;

public class AppSidebar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private static final String TAG = "AppSideBar";

    private static final String KEY_ENABLED = "sidebar_enable";
    private static final String KEY_TRANSPARENCY = "sidebar_transparency";
    private static final String KEY_SETUP_ITEMS = "sidebar_setup_items";
    private static final String KEY_POSITION = "sidebar_position";
    private static final String KEY_HIDE_LABELS = "sidebar_hide_labels";
    private static final String KEY_TRIGGER_WIDTH = "trigger_width";
    private static final String KEY_TRIGGER_TOP = "trigger_top";
    private static final String KEY_TRIGGER_BOTTOM = "trigger_bottom";

    private SwitchPreference mEnabledPref;
    private SeekBarPreference mTransparencyPref;
    private ListPreference mPositionPref;
    private SwitchPreference mHideLabelsPref;
    private SeekBarPreference mTriggerWidthPref;
    private SeekBarPreference mTriggerTopPref;
    private SeekBarPreference mTriggerBottomPref;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.app_sidebar_settings);
        ContentResolver resolver = getActivity().getContentResolver();

        mEnabledPref = (SwitchPreference) findPreference(KEY_ENABLED);
        mEnabledPref.setChecked((Settings.System.getInt(resolver,
                Settings.System.APP_SIDEBAR_ENABLED, 0) == 1));
        mEnabledPref.setOnPreferenceChangeListener(this);

        mHideLabelsPref = (SwitchPreference) findPreference(KEY_HIDE_LABELS);
        mHideLabelsPref.setChecked((Settings.System.getInt(resolver,
                Settings.System.APP_SIDEBAR_DISABLE_LABELS, 0) == 1));
        mHideLabelsPref.setOnPreferenceChangeListener(this);

        mPositionPref = (ListPreference) findPreference(KEY_POSITION);
        int position = Settings.System.getInt(resolver, Settings.System.APP_SIDEBAR_POSITION, 0);
        mPositionPref.setValue(String.valueOf(position));
        mPositionPref.setSummary(mPositionPref.getEntries()[mPositionPref.findIndexOfValue("" + position)]);
        mPositionPref.setOnPreferenceChangeListener(this);

        mTransparencyPref = (SeekBarPreference) findPreference(KEY_TRANSPARENCY);
        int transparency = Settings.System.getInt(resolver, Settings.System.APP_SIDEBAR_TRANSPARENCY, 0);
        mTransparencyPref.setValue(transparency);
        mTransparencyPref.setOnPreferenceChangeListener(this);

        mTriggerWidthPref = (SeekBarPreference) findPreference(KEY_TRIGGER_WIDTH);
        int width = Settings.System.getInt(resolver, Settings.System.APP_SIDEBAR_TRIGGER_WIDTH, 16);
        mTriggerWidthPref.setValue(width);
        mTriggerWidthPref.setOnPreferenceChangeListener(this);

        mTriggerTopPref = (SeekBarPreference) findPreference(KEY_TRIGGER_TOP);
        int top = Settings.System.getInt(resolver, Settings.System.APP_SIDEBAR_TRIGGER_TOP, 0);
        mTriggerTopPref.setValue(top);
        mTriggerTopPref.setOnPreferenceChangeListener(this);

        mTriggerBottomPref = (SeekBarPreference) findPreference(KEY_TRIGGER_BOTTOM);
        int bottom = Settings.System.getInt(resolver, Settings.System.APP_SIDEBAR_TRIGGER_HEIGHT, 100);
        mTriggerBottomPref.setValue(bottom);
        mTriggerBottomPref.setOnPreferenceChangeListener(this);

        findPreference(KEY_SETUP_ITEMS).setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mTransparencyPref) {
            int transparency = ((Integer)newValue).intValue();
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_TRANSPARENCY, transparency);
            return true;
        } else if (preference == mTriggerWidthPref) {
            int width = ((Integer)newValue).intValue();
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_TRIGGER_WIDTH, width);
            return true;
        } else if (preference == mTriggerTopPref) {
            int top = ((Integer)newValue).intValue();
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_TRIGGER_TOP, top);
            return true;
        } else if (preference == mTriggerBottomPref) {
            int bottom = ((Integer)newValue).intValue();
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_TRIGGER_HEIGHT, bottom);
            return true;
        } else if (preference == mPositionPref) {
            int position = Integer.valueOf((String) newValue);
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_POSITION, position);
            mPositionPref.setSummary(mPositionPref.getEntries()[mPositionPref.findIndexOfValue("" + newValue)]);
            return true;
        } else if (preference == mEnabledPref) {
            boolean value = ((Boolean)newValue).booleanValue();
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_ENABLED,
                    value ? 1 : 0);
            return true;
        } else if (preference == mHideLabelsPref) {
            boolean value = ((Boolean)newValue).booleanValue();
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_DISABLE_LABELS,
                    value ? 1 : 0);
            /* Re-apply position to make sidebar visible if available. */
            int position = Settings.System.getInt(resolver, Settings.System.APP_SIDEBAR_POSITION, 0);
            Settings.System.putInt(resolver,
                    Settings.System.APP_SIDEBAR_POSITION, position);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if(preference.getKey().equals(KEY_SETUP_ITEMS)) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(new ComponentName("com.android.systemui",
                    "com.android.systemui.statusbar.sidebar.SidebarConfigurationActivity"));
            getActivity().startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        Settings.System.putInt(getContentResolver(),
                Settings.System.APP_SIDEBAR_SHOW_TRIGGER, 0);
    }

    @Override
    public void onResume() {
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
        Settings.System.putInt(getContentResolver(),
                Settings.System.APP_SIDEBAR_SHOW_TRIGGER, 1);
    }
}
