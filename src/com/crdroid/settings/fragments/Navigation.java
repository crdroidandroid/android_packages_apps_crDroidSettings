/*
 * Copyright (C) 2016-2019 crDroid Android Project
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
import com.android.internal.utils.Config;
import com.android.internal.utils.Config.ButtonConfig;
import com.android.internal.utils.ActionConstants;
import com.android.internal.utils.ActionUtils;
import com.android.internal.util.crdroid.Utils;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.navigation.CarbonGesturesSettings;
import com.crdroid.settings.fragments.navigation.PieSettings;
import com.crdroid.settings.fragments.navigation.StockNavBarSettings;
import com.crdroid.settings.fragments.navigation.SwipeUpGesturesSettings;
import com.crdroid.settings.fragments.navigation.smartnav.FlingSettings;
import com.crdroid.settings.fragments.navigation.smartnav.PulseSettings;
import com.crdroid.settings.fragments.navigation.smartnav.SmartbarSettings;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

import java.util.List;
import java.util.ArrayList;

import lineageos.providers.LineageSettings;

public class Navigation extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Indexable {

    public static final String TAG = "Navigation";

    private static final String NAVBAR_VISIBILITY = "navbar_visibility";
    private static final String KEY_NAVBAR_MODE = "navigation_bar_mode";
    private static final String KEY_NAVIGATION_HEIGHT_PORT = "navbar_height_portrait";
    private static final String KEY_NAVIGATION_HEIGHT_LAND = "navbar_height_landscape";
    private static final String KEY_NAVIGATION_WIDTH = "navbar_width";
    private static final String KEY_CATEGORY_NAVIGATION_INTERFACE = "category_navbar_interface";
    private static final String KEY_CATEGORY_NAVIGATION_GENERAL = "category_navbar_general";
    private static final String KEY_STOCK_NAVBAR_SETTINGS = "stock_settings";
    private static final String KEY_SMARTBAR_SETTINGS = "smartbar_settings";
    private static final String KEY_FLING_NAVBAR_SETTINGS = "fling_settings";

    private SwitchPreference mNavbarVisibility;
    private ListPreference mNavbarMode;
    private CustomSeekBarPreference mBarHeightPort;
    private CustomSeekBarPreference mBarHeightLand;
    private CustomSeekBarPreference mBarWidth;

    private Preference mStockSettings;
    private Preference mSmartbarSettings;
    private Preference mFlingSettings;

    private PreferenceCategory mNavInterface;
    private PreferenceCategory mNavGeneral;

    private boolean mIsNavSwitchingMode = false;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_navigation);

        ContentResolver resolver = getActivity().getContentResolver();
        mHandler = new Handler();

        mNavInterface = (PreferenceCategory) findPreference(KEY_CATEGORY_NAVIGATION_INTERFACE);
        mNavGeneral = (PreferenceCategory) findPreference(KEY_CATEGORY_NAVIGATION_GENERAL);

        mNavbarVisibility = (SwitchPreference) findPreference(NAVBAR_VISIBILITY);

        boolean showing = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.FORCE_SHOW_NAVBAR,
                Utils.hasNavbarByDefault(getActivity()) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
        mNavbarVisibility.setChecked(showing);
        mNavbarVisibility.setOnPreferenceChangeListener(this);

        int mode = Settings.Secure.getIntForUser(resolver, Settings.Secure.NAVIGATION_BAR_MODE,
                    0, UserHandle.USER_CURRENT);

        mStockSettings = (Preference) findPreference(KEY_STOCK_NAVBAR_SETTINGS);
        mFlingSettings = (Preference) findPreference(KEY_FLING_NAVBAR_SETTINGS);
        mSmartbarSettings = (Preference) findPreference(KEY_SMARTBAR_SETTINGS);
        mNavbarMode = (ListPreference) findPreference(KEY_NAVBAR_MODE);

        updateBarModeSettings(mode);
        mNavbarMode.setOnPreferenceChangeListener(this);

        int size = Settings.Secure.getIntForUser(getContentResolver(),
                Settings.Secure.NAVIGATION_BAR_HEIGHT, 100, UserHandle.USER_CURRENT);
        mBarHeightPort = (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_HEIGHT_PORT);
        mBarHeightPort.setValue(size);
        mBarHeightPort.setOnPreferenceChangeListener(this);

        final boolean canMove = ActionUtils.navigationBarCanMove();
        if (canMove) {
            mNavInterface.removePreference(findPreference(KEY_NAVIGATION_HEIGHT_LAND));
            size = Settings.Secure.getIntForUser(getContentResolver(),
                    Settings.Secure.NAVIGATION_BAR_WIDTH, 100, UserHandle.USER_CURRENT);
            mBarWidth = (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_WIDTH);
            mBarWidth.setValue(size);
            mBarWidth.setOnPreferenceChangeListener(this);
        } else {
            mNavInterface.removePreference(findPreference(KEY_NAVIGATION_WIDTH));
            size = Settings.Secure.getIntForUser(getContentResolver(),
                    Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, 100, UserHandle.USER_CURRENT);
            mBarHeightLand = (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_HEIGHT_LAND);
            mBarHeightLand.setValue(size);
            mBarHeightLand.setOnPreferenceChangeListener(this);
        }
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
        } else if (preference == mNavbarMode) {
            int mode = Integer.parseInt((String) newValue);
            updateBarModeSettings(mode);
            return true;
        } else if (preference == mBarHeightPort) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_HEIGHT, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBarHeightLand) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, val, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mBarWidth) {
            int val = (Integer) newValue;
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_WIDTH, val, UserHandle.USER_CURRENT);
            return true;
        }

        return false;
    }

    private void updateBarModeSettings(int mode) {
        switch (mode) {
            case 0:
                mStockSettings.setEnabled(true);
                mStockSettings.setSelectable(true);
                mSmartbarSettings.setEnabled(false);
                mSmartbarSettings.setSelectable(false);
                mFlingSettings.setEnabled(false);
                mFlingSettings.setSelectable(false);
                break;
            case 1:
                mStockSettings.setEnabled(false);
                mStockSettings.setSelectable(false);
                mSmartbarSettings.setEnabled(true);
                mSmartbarSettings.setSelectable(true);
                mFlingSettings.setEnabled(false);
                mFlingSettings.setSelectable(false);
                break;
            case 2:
                mStockSettings.setEnabled(false);
                mStockSettings.setSelectable(false);
                mSmartbarSettings.setEnabled(false);
                mSmartbarSettings.setSelectable(false);
                mFlingSettings.setEnabled(true);
                mFlingSettings.setSelectable(true);
                break;
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.System.putIntForUser(resolver, LineageSettings.System.FORCE_SHOW_NAVBAR,
             Utils.hasNavbarByDefault(mContext) ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_MODE, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_HEIGHT, 100, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, 100, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_WIDTH, 100, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.ONE_HANDED_MODE_UI, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
            Settings.System.FULL_GESTURE_NAVBAR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
            Settings.System.FULL_GESTURE_NAVBAR_DT2S, 0, UserHandle.USER_CURRENT);
        CarbonGesturesSettings.reset(mContext);
        FlingSettings.reset(mContext);
        SmartbarSettings.reset(mContext);
        StockNavBarSettings.reset(mContext);
        SwipeUpGesturesSettings.reset(mContext);
        PieSettings.reset(mContext);
        PulseSettings.reset(mContext);
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
