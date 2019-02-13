/*
 * Copyright (C) 2018-2019 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.fragments.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.ui.AccentPicker;

import java.util.List;
import java.util.ArrayList;

public class ThemeSettings extends SettingsPreferenceFragment implements Indexable,
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "ThemeSettings";

    private String KEY_ACCENT_PICKER = "berry_accent_picker";
    private String KEY_THEME_OVERRIDE = "berry_theme_override";
    private String KEY_DARK_STYLE = "berry_dark_style";
    private String KEY_NOTIFICATION_STYLE = "berry_notification_style";

    private Preference mAccentPicker;
    private ListPreference mThemeOverride;
    private ListPreference mDarkStyle;
    private ListPreference mNotiStyle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme_settings);

        ContentResolver resolver = getActivity().getContentResolver();

        mAccentPicker = findPreference(KEY_ACCENT_PICKER);

        int themeOverride = Settings.System.getIntForUser(resolver,
                Settings.System.BERRY_THEME_OVERRIDE, 0, UserHandle.USER_CURRENT);
        mThemeOverride = (ListPreference) findPreference(KEY_THEME_OVERRIDE);
        mThemeOverride.setValue(String.valueOf(themeOverride));
        mThemeOverride.setSummary(mThemeOverride.getEntry());
        mThemeOverride.setOnPreferenceChangeListener(this);

        int darkStyle = Settings.System.getIntForUser(resolver,
                Settings.System.BERRY_DARK_STYLE, 0, UserHandle.USER_CURRENT);
        mDarkStyle = (ListPreference) findPreference(KEY_DARK_STYLE);
        mDarkStyle.setValue(String.valueOf(darkStyle));
        mDarkStyle.setSummary(mDarkStyle.getEntry());
        mDarkStyle.setEnabled(themeOverride != 2);
        mDarkStyle.setOnPreferenceChangeListener(this);

        int notiStyle = Settings.System.getIntForUser(resolver,
                Settings.System.BERRY_NOTIFICATION_STYLE, 0, UserHandle.USER_CURRENT);
        mNotiStyle = (ListPreference) findPreference(KEY_NOTIFICATION_STYLE);
        mNotiStyle.setValue(String.valueOf(notiStyle));
        mNotiStyle.setSummary(mNotiStyle.getEntry());
        mNotiStyle.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mThemeOverride) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_THEME_OVERRIDE, value, UserHandle.USER_CURRENT);
            mThemeOverride.setValue(String.valueOf(value));
            mThemeOverride.setSummary(mThemeOverride.getEntry());
            mDarkStyle.setEnabled(value != 2);
            return true;
        } else if (preference == mDarkStyle) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_DARK_STYLE, value, UserHandle.USER_CURRENT);
            mDarkStyle.setValue(String.valueOf(value));
            mDarkStyle.setSummary(mDarkStyle.getEntry());
            return true;
        } if (preference == mNotiStyle) {
            int value = Integer.parseInt((String) newValue);
            mNotiStyle.setValue(String.valueOf(value));
            mNotiStyle.setSummary(mNotiStyle.getEntry());
            Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_NOTIFICATION_STYLE, value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mAccentPicker) {
            AccentPicker.show(this);
        }

        return super.onPreferenceTreeClick(preference);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_ACCENT_PICKER, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_THEME_OVERRIDE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_DARK_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_NOTIFICATION_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_QS_HEADER_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_QS_TILE_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BERRY_SWITCH_STYLE, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.theme_settings;
                    result.add(sir);

                    return result;
                }
            };
}
