/*
 * Copyright (C) 2016-2018 crDroid Android Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.widget.Toast;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.navigation.CarbonGesturesSettings;
import com.crdroid.settings.fragments.navigation.SwipeUpGesturesSettings;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

import java.util.List;
import java.util.ArrayList;

import lineageos.providers.LineageSettings;

public class Navigation extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {

    public static final String TAG = "Navigation";

    private static final String NAVBAR_VISIBILITY = "navbar_visibility";

    private SwitchPreference mNavbarVisibility;

    private boolean mIsNavSwitchingMode = false;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_navigation);

        ContentResolver resolver = getActivity().getContentResolver();
        mHandler = new Handler();

        mNavbarVisibility = (SwitchPreference) findPreference(NAVBAR_VISIBILITY);

        boolean showing = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.FORCE_SHOW_NAVBAR,
                Utils.hasNavbarByDefault(getActivity()) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
        mNavbarVisibility.setChecked(showing);
        mNavbarVisibility.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (preference == mNavbarVisibility) {
            if (mIsNavSwitchingMode) {
                return false;
            }
            mIsNavSwitchingMode = true;
            boolean showing = ((Boolean)newValue);
            LineageSettings.System.putIntForUser(resolver, LineageSettings.System.FORCE_SHOW_NAVBAR,
                    showing ? 1 : 0, UserHandle.USER_CURRENT);
            mNavbarVisibility.setChecked(showing);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mIsNavSwitchingMode = false;
                }
            }, 1500);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.System.putIntForUser(resolver, LineageSettings.System.FORCE_SHOW_NAVBAR,
             Utils.hasNavbarByDefault(mContext) ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, Settings.System.PIXEL_NAV_ANIMATION,
             1, UserHandle.USER_CURRENT);
        CarbonGesturesSettings.reset(mContext);
        SwipeUpGesturesSettings.reset(mContext);
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
                    sir.xmlResId = R.xml.crdroid_settings_navigation;
                    result.add(sir);

                    return result;
                }
            };
}
