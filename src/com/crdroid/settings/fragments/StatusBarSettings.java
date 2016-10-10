package com.crdroid.settings.fragments;

import com.android.internal.logging.MetricsProto.MetricsEvent;

import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
import com.android.settings.R;

import java.util.Locale;
import android.text.TextUtils;
import android.view.View;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import android.util.Log;

import java.util.List;
import java.util.ArrayList;

import com.crdroid.settings.preferences.ColorPickerPreference;

public class StatusBarSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_CRDROID_LOGO_COLOR = "status_bar_crdroid_logo_color";
    private static final String KEY_CRDROID_LOGO_STYLE = "status_bar_crdroid_logo_style";

    private ColorPickerPreference mCrDroidLogoColor;
    private ListPreference mCrDroidLogoStyle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);
        PreferenceScreen prefSet = getPreferenceScreen();
        final ContentResolver resolver = getActivity().getContentResolver();

        mCrDroidLogoStyle = (ListPreference) findPreference(KEY_CRDROID_LOGO_STYLE);
        int crdroidLogoStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);
        mCrDroidLogoStyle.setValue(String.valueOf(crdroidLogoStyle));
        mCrDroidLogoStyle.setSummary(mCrDroidLogoStyle.getEntry());
        mCrDroidLogoStyle.setOnPreferenceChangeListener(this);

        // CrDroid logo color
        mCrDroidLogoColor =
                (ColorPickerPreference) prefSet.findPreference(KEY_CRDROID_LOGO_COLOR);
        mCrDroidLogoColor.setOnPreferenceChangeListener(this);
        int intColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, 0xffffffff);
        String hexColor = String.format("#%08x", (0xffffffff & intColor));
        mCrDroidLogoColor.setSummary(hexColor);
        mCrDroidLogoColor.setNewPreviewColor(intColor);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mCrDroidLogoColor) {
            String hex = ColorPickerPreference.convertToARGB(
                Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);
            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(resolver,
                Settings.System.STATUS_BAR_CRDROID_LOGO_COLOR, intHex);
            return true;
        } else if (preference == mCrDroidLogoStyle) {
            int crdroidLogoStyle = Integer.valueOf((String) newValue);
            int index = mCrDroidLogoStyle.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(
                resolver, Settings.System.STATUS_BAR_CRDROID_LOGO_STYLE, crdroidLogoStyle,
                UserHandle.USER_CURRENT);
            mCrDroidLogoStyle.setSummary(
                    mCrDroidLogoStyle.getEntries()[index]);
            return true;
        }
        return false;
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

}
