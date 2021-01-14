/*
 * Copyright (C) 2016-2021 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.quicksettings.CustomHeader;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

import java.util.List;
import java.util.ArrayList;

import lineageos.providers.LineageSettings;

@SearchIndexable
public class QuickSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "QuickSettings";

    private static final String KEY_COL_PORTRAIT = "qs_columns_portrait";
    private static final String KEY_ROW_PORTRAIT = "qs_rows_portrait";
    private static final String KEY_COL_LANDSCAPE = "qs_columns_landscape";
    private static final String KEY_ROW_LANDSCAPE = "qs_rows_landscape";

    CustomSeekBarPreference mColPortrait;
    CustomSeekBarPreference mRowPortrait;
    CustomSeekBarPreference mColLandscape;
    CustomSeekBarPreference mRowLandscape;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_quicksettings);

        mColPortrait = (CustomSeekBarPreference) findPreference(KEY_COL_PORTRAIT);
        mRowPortrait = (CustomSeekBarPreference) findPreference(KEY_ROW_PORTRAIT);
        mColLandscape = (CustomSeekBarPreference) findPreference(KEY_COL_LANDSCAPE);
        mRowLandscape = (CustomSeekBarPreference) findPreference(KEY_ROW_LANDSCAPE);

        Resources res = null;
        Context ctx = getContext();

        try {
            res = ctx.getPackageManager().getResourcesForApplication("com.android.systemui");
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        int col_portrait = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_columns_portrait", null, null));
        int row_portrait = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_rows_portrait", null, null));
        int col_landscape = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_columns_landscape", null, null));
        int row_landscape = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_rows_landscape", null, null));

        mColPortrait.setDefaultValue(col_portrait);
        mRowPortrait.setDefaultValue(row_portrait);
        mColLandscape.setDefaultValue(col_landscape);
        mRowLandscape.setDefaultValue(row_landscape);

        int mColPortraitVal = Settings.System.getIntForUser(ctx.getContentResolver(),
                Settings.System.QS_COLUMNS_PORTRAIT, col_portrait, UserHandle.USER_CURRENT);
        int mRowPortraitVal = Settings.System.getIntForUser(ctx.getContentResolver(),
                Settings.System.QS_ROWS_PORTRAIT, row_portrait, UserHandle.USER_CURRENT);
        int mColLandscapeVal = Settings.System.getIntForUser(ctx.getContentResolver(),
                Settings.System.QS_COLUMNS_LANDSCAPE, col_landscape, UserHandle.USER_CURRENT);
        int mRowLandscapeVal = Settings.System.getIntForUser(ctx.getContentResolver(),
                Settings.System.QS_ROWS_LANDSCAPE, row_landscape, UserHandle.USER_CURRENT);

        mColPortrait.setValue(mColPortraitVal);
        mRowPortrait.setValue(mRowPortraitVal);
        mColLandscape.setValue(mColLandscapeVal);
        mRowLandscape.setValue(mRowLandscapeVal);        
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Resources res = null;

        try {
            res = mContext.getPackageManager().getResourcesForApplication("com.android.systemui");
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        int col_portrait = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_columns_portrait", null, null));
        int row_portrait = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_rows_portrait", null, null));
        int col_landscape = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_columns_landscape", null, null));
        int row_landscape = res.getInteger(res.getIdentifier(
                "com.android.systemui:integer/config_qs_rows_landscape", null, null));

        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_COLUMNS_LANDSCAPE, col_landscape, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_COLUMNS_PORTRAIT, col_portrait, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_BATTERY_LOCATION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_BATTERY_STYLE, -1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SHOW_BATTERY_PERCENT, 2, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SHOW_BATTERY_ESTIMATE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SHOW_QS_CLOCK, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_ROWS_LANDSCAPE, row_landscape, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_ROWS_PORTRAIT, row_portrait, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_TITLE_VISIBILITY, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QUICK_SETTINGS_VIBRATE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_PANEL_BG_ALPHA, 255, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SB_BG_GRADIENT, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_SB_BG_ALPHA, 255, UserHandle.USER_CURRENT);

        CustomHeader.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_quicksettings);
}
