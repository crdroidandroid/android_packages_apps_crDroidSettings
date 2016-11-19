package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.graphics.Color;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.view.View;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.crdroid.settings.preferences.SeekBarPreference;
import com.crdroid.settings.preferences.ColorPickerPreference;

public class BlurPersonalizations extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    //Switch Preferences
    private SwitchPreference mExpand;
    private SwitchPreference mNotiTrans;
    private SwitchPreference mQuickSett;
    private SwitchPreference mRecentsSett;

    //Transluency,Radius and Scale
    private SeekBarPreference mScale;
    private SeekBarPreference mRadius;
    private SeekBarPreference mRecentsRadius;
    private SeekBarPreference mRecentsScale;
    private SeekBarPreference mQuickSettPerc;
    private SeekBarPreference mNotSettPerc;

    //Colors
    private ColorPickerPreference mDarkBlurColor;
    private ColorPickerPreference mLightBlurColor;
    private ColorPickerPreference mMixedBlurColor;

    public static int BLUR_LIGHT_COLOR_PREFERENCE_DEFAULT = Color.DKGRAY;
    public static int BLUR_MIXED_COLOR_PREFERENCE_DEFAULT = Color.GRAY;
    public static int BLUR_DARK_COLOR_PREFERENCE_DEFAULT = Color.LTGRAY;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.blur);
        PreferenceScreen prefSet = getPreferenceScreen();

        ContentResolver resolver = getActivity().getContentResolver();

        //Some help here
        int intLightColor;
        int intDarkColor;
        int intMixedColor;
        String hexLightColor;
        String hexDarkColor;
        String hexMixedColor;

        mExpand = (SwitchPreference) prefSet.findPreference("blurred_status_bar_expanded_enabled_pref");
        mExpand.setChecked((Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY, 0) == 1));

        mScale = (SeekBarPreference) findPreference("statusbar_blur_scale");
        mScale.setValue(Settings.System.getInt(resolver, Settings.System.BLUR_SCALE_PREFERENCE_KEY, 10));
        mScale.setOnPreferenceChangeListener(this);

        mRadius = (SeekBarPreference) findPreference("statusbar_blur_radius");
        mRadius.setValue(Settings.System.getInt(resolver, Settings.System.BLUR_RADIUS_PREFERENCE_KEY, 5));
        mRadius.setOnPreferenceChangeListener(this);

        /*mNotiTrans = (SwitchPreference) prefSet.findPreference("translucent_notifications_pref");
        mNotiTrans.setChecked((Settings.System.getInt(resolver,
                Settings.System.TRANSLUCENT_NOTIFICATIONS_PREFERENCE_KEY, 0) == 1));*/

        mQuickSett = (SwitchPreference) prefSet.findPreference("translucent_quick_settings_pref");
        mQuickSett.setChecked((Settings.System.getInt(resolver,
                Settings.System.TRANSLUCENT_QUICK_SETTINGS_PREFERENCE_KEY, 0) == 1));

        mQuickSettPerc = (SeekBarPreference) findPreference("quick_settings_transluency");
        mQuickSettPerc.setValue(Settings.System.getInt(resolver, Settings.System.TRANSLUCENT_QUICK_SETTINGS_PERCENTAGE_PREFERENCE_KEY, 60));
        mQuickSettPerc.setOnPreferenceChangeListener(this);

        /*mNotSettPerc = (SeekBarPreference) findPreference("notifications_transluency");
        mNotSettPerc.setValue(Settings.System.getInt(resolver, Settings.System.TRANSLUCENT_NOTIFICATIONS_PERCENTAGE_PREFERENCE_KEY, 60));
        mNotSettPerc.setOnPreferenceChangeListener(this);*/

        mRecentsSett = (SwitchPreference) prefSet.findPreference("blurred_recent_app_enabled_pref");
        mRecentsSett.setChecked((Settings.System.getInt(resolver,
                Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY, 0) == 1));

        mRecentsScale = (SeekBarPreference) findPreference("recents_blur_scale");
        mRecentsScale.setValue(Settings.System.getInt(resolver, Settings.System.RECENT_APPS_SCALE_PREFERENCE_KEY, 6));
        mRecentsScale.setOnPreferenceChangeListener(this);

        mRecentsRadius = (SeekBarPreference) findPreference("recents_blur_radius");
        mRecentsRadius.setValue(Settings.System.getInt(resolver, Settings.System.RECENT_APPS_RADIUS_PREFERENCE_KEY, 3));
        mRecentsRadius.setOnPreferenceChangeListener(this);

        mLightBlurColor = (ColorPickerPreference) findPreference("blur_light_color");
        intLightColor = Settings.System.getInt(resolver, Settings.System.BLUR_LIGHT_COLOR_PREFERENCE_KEY, BLUR_LIGHT_COLOR_PREFERENCE_DEFAULT);
        hexLightColor = String.format("#%08x", (0xffffffff & intLightColor));
        mLightBlurColor.setSummary(hexLightColor);
        mLightBlurColor.setNewPreviewColor(intLightColor);
        mLightBlurColor.setOnPreferenceChangeListener(this);

        mDarkBlurColor = (ColorPickerPreference) findPreference("blur_dark_color");
        intDarkColor = Settings.System.getInt(resolver, Settings.System.BLUR_DARK_COLOR_PREFERENCE_KEY, BLUR_DARK_COLOR_PREFERENCE_DEFAULT);
        hexDarkColor = String.format("#%08x", (0xffffffff & intDarkColor));
        mDarkBlurColor.setSummary(hexDarkColor);
        mDarkBlurColor.setNewPreviewColor(intDarkColor);
        mDarkBlurColor.setOnPreferenceChangeListener(this);

        mMixedBlurColor = (ColorPickerPreference) findPreference("blur_mixed_color");
        intMixedColor = Settings.System.getInt(resolver, Settings.System.BLUR_MIXED_COLOR_PREFERENCE_KEY, BLUR_MIXED_COLOR_PREFERENCE_DEFAULT);
        hexMixedColor = String.format("#%08x", (0xffffffff & intMixedColor));
        mMixedBlurColor.setSummary(hexMixedColor);
        mMixedBlurColor.setNewPreviewColor(intMixedColor);
        mMixedBlurColor.setOnPreferenceChangeListener(this);
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mScale) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putInt(
                resolver, Settings.System.BLUR_SCALE_PREFERENCE_KEY, value);
            return true;
        } else if (preference == mRadius) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putInt(
                resolver, Settings.System.BLUR_RADIUS_PREFERENCE_KEY, value);
            return true;
        } else if (preference == mQuickSettPerc) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putInt(
                resolver, Settings.System.TRANSLUCENT_QUICK_SETTINGS_PERCENTAGE_PREFERENCE_KEY, value);
            return true;
        /*} else if (preference == mNotSettPerc) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putInt(
                resolver, Settings.System.TRANSLUCENT_NOTIFICATIONS_PERCENTAGE_PREFERENCE_KEY, value);
            return true;
        }*/
        } else if (preference == mRecentsScale) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putInt(
                resolver, Settings.System.RECENT_APPS_SCALE_PREFERENCE_KEY, value);
            return true;
        } else if(preference == mRecentsRadius) {
            int value = ((Integer)newValue).intValue();
            Settings.System.putInt(
                resolver, Settings.System.RECENT_APPS_RADIUS_PREFERENCE_KEY, value);
            return true;
        } else if (preference == mLightBlurColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.BLUR_LIGHT_COLOR_PREFERENCE_KEY, intHex);
            return true;
        } else if (preference == mDarkBlurColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.BLUR_DARK_COLOR_PREFERENCE_KEY, intHex);
            return true;
        } else if (preference == mMixedBlurColor) {
            String hex = ColorPickerPreference.convertToARGB(
                    Integer.parseInt(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.BLUR_MIXED_COLOR_PREFERENCE_KEY, intHex);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if  (preference == mExpand) {
            boolean enabled = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.STATUS_BAR_EXPANDED_ENABLED_PREFERENCE_KEY, enabled ? 1:0);
        } else if (preference == mNotiTrans) {
            boolean enabled = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.TRANSLUCENT_NOTIFICATIONS_PREFERENCE_KEY, enabled ? 1:0);
        } else if (preference == mQuickSett) {
            boolean enabled = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.TRANSLUCENT_QUICK_SETTINGS_PREFERENCE_KEY, enabled ? 1:0);
        } else if (preference == mRecentsSett) {
            boolean enabled = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.RECENT_APPS_ENABLED_PREFERENCE_KEY, enabled ? 1:0);
        }
        return super.onPreferenceTreeClick(preference);
    }
}
