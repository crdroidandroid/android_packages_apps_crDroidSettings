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
import android.support.v7.preference.ListPreference;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.widget.Toast;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.utils.du.ActionConstants;
import com.android.internal.utils.du.Config;
import com.android.internal.utils.du.DUActionUtils;
import com.android.internal.utils.du.Config.ButtonConfig;

import com.crdroid.settings.fragments.navbar.CarbonGesturesSettings;
import com.crdroid.settings.fragments.navbar.EdgeGestureSettings;
import com.crdroid.settings.fragments.navbar.Fling;
import com.crdroid.settings.fragments.navbar.Pulse;
import com.crdroid.settings.fragments.navbar.Smartbar;
import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.R;

public class NavBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "NavBar";

    private static final String NAVBAR_VISIBILITY = "navbar_visibility";
    private static final String KEY_NAVBAR_MODE = "navbar_mode";
    private static final String KEY_STOCK_NAVBAR_SETTINGS = "stock_settings";
    private static final String KEY_FLING_NAVBAR_SETTINGS = "fling_settings";
    private static final String KEY_CATEGORY_NAVIGATION_INTERFACE = "category_navbar_interface";
    private static final String KEY_CATEGORY_NAVIGATION_GENERAL = "category_navbar_general";
    private static final String KEY_NAVIGATION_BAR_LEFT = "navigation_bar_left";
    private static final String KEY_SMARTBAR_SETTINGS = "smartbar_settings";
    private static final String KEY_NAVIGATION_HEIGHT_PORT = "navbar_height_portrait";
    private static final String KEY_NAVIGATION_HEIGHT_LAND = "navbar_height_landscape";
    private static final String KEY_NAVIGATION_WIDTH = "navbar_width";
    private static final String KEY_PULSE_SETTINGS = "pulse_settings";
    private static final String NAVBAR_DYNAMIC = "navbar_dynamic";

    private SwitchPreference mNavbarVisibility;
    private ListPreference mNavbarMode;
    private Preference mFlingSettings;
    private PreferenceCategory mNavInterface;
    private PreferenceCategory mNavGeneral;
    private Preference mSmartbarSettings;
    private Preference mStockSettings;
    private CustomSeekBarPreference mBarHeightPort;
    private CustomSeekBarPreference mBarHeightLand;
    private CustomSeekBarPreference mBarWidth;
    private Preference mPulseSettings;
    private SwitchPreference mNavbarDynamic;

    private boolean mIsNavSwitchingMode = false;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_navigation);

        ContentResolver resolver = getActivity().getContentResolver();

        mNavInterface = (PreferenceCategory) findPreference(KEY_CATEGORY_NAVIGATION_INTERFACE);
        mNavGeneral = (PreferenceCategory) findPreference(KEY_CATEGORY_NAVIGATION_GENERAL);
        mNavbarVisibility = (SwitchPreference) findPreference(NAVBAR_VISIBILITY);
        mNavbarMode = (ListPreference) findPreference(KEY_NAVBAR_MODE);
        mStockSettings = (Preference) findPreference(KEY_STOCK_NAVBAR_SETTINGS);
        mFlingSettings = (Preference) findPreference(KEY_FLING_NAVBAR_SETTINGS);
        mSmartbarSettings = (Preference) findPreference(KEY_SMARTBAR_SETTINGS);
        mPulseSettings = (Preference) findPreference(KEY_PULSE_SETTINGS);
        mNavbarDynamic = (SwitchPreference) findPreference(NAVBAR_DYNAMIC);

        boolean showing = Settings.Secure.getInt(resolver,
                Settings.Secure.NAVIGATION_BAR_VISIBLE,
                DUActionUtils.hasNavbarByDefault(getActivity()) ? 1 : 0) != 0;
        updateBarVisibleAndUpdatePrefs(showing);
        mNavbarVisibility.setOnPreferenceChangeListener(this);

        int mode = Settings.Secure.getInt(resolver, Settings.Secure.NAVIGATION_BAR_MODE,
                1);

        updateBarModeSettings(mode);
        mNavbarMode.setOnPreferenceChangeListener(this);

        int size = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.NAVIGATION_BAR_HEIGHT, 80, UserHandle.USER_CURRENT);
        mBarHeightPort = (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_HEIGHT_PORT);
        mBarHeightPort.setValue(size);
        mBarHeightPort.setOnPreferenceChangeListener(this);

        final boolean canMove = DUActionUtils.navigationBarCanMove();
        if (canMove) {
            mNavInterface.removePreference(findPreference(KEY_NAVIGATION_HEIGHT_LAND));
            size = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_WIDTH, 80, UserHandle.USER_CURRENT);
            mBarWidth = (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_WIDTH);
            mBarWidth.setValue(size);
            mBarWidth.setOnPreferenceChangeListener(this);
        } else {
            mNavInterface.removePreference(findPreference(KEY_NAVIGATION_WIDTH));
            size = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, 80, UserHandle.USER_CURRENT);
            mBarHeightLand = (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_HEIGHT_LAND);
            mBarHeightLand.setValue(size);
            mBarHeightLand.setOnPreferenceChangeListener(this);
        }

        mHandler = new Handler();

        boolean isDynamic = Settings.System.getIntForUser(resolver,
                Settings.System.NAVBAR_DYNAMIC, 0, UserHandle.USER_CURRENT) == 1;
        mNavbarDynamic.setChecked(isDynamic);
        mNavbarDynamic.setOnPreferenceChangeListener(this);
    }

    private void updateBarModeSettings(int mode) {
        mNavbarMode.setValue(String.valueOf(mode));
        switch (mode) {
            case 0:
                mStockSettings.setEnabled(true);
                mStockSettings.setSelectable(true);
                mSmartbarSettings.setEnabled(false);
                mSmartbarSettings.setSelectable(false);
                mFlingSettings.setEnabled(false);
                mFlingSettings.setSelectable(false);
                mPulseSettings.setEnabled(false);
                mPulseSettings.setSelectable(false);
                break;
            case 1:
                mStockSettings.setEnabled(false);
                mStockSettings.setSelectable(false);
                mSmartbarSettings.setEnabled(true);
                mSmartbarSettings.setSelectable(true);
                mFlingSettings.setEnabled(false);
                mFlingSettings.setSelectable(false);
                mPulseSettings.setEnabled(true);
                mPulseSettings.setSelectable(true);
                break;
            case 2:
                mStockSettings.setEnabled(false);
                mStockSettings.setSelectable(false);
                mSmartbarSettings.setEnabled(false);
                mSmartbarSettings.setSelectable(false);
                mFlingSettings.setEnabled(true);
                mFlingSettings.setSelectable(true);
                mPulseSettings.setEnabled(true);
                mPulseSettings.setSelectable(true);
                break;
        }
    }

    private void updateBarVisibleAndUpdatePrefs(boolean showing) {
        mNavbarVisibility.setChecked(showing);
        mNavInterface.setEnabled(mNavbarVisibility.isChecked());
        mNavGeneral.setEnabled(mNavbarVisibility.isChecked());
        mNavbarDynamic.setEnabled(mNavbarVisibility.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (preference == mNavbarMode) {
            int mode = Integer.parseInt((String) newValue);
            Settings.Secure.putIntForUser(resolver,
                    Settings.Secure.NAVIGATION_BAR_MODE, mode, UserHandle.USER_CURRENT);
            updateBarModeSettings(mode);
            return true;
        } else if (preference == mNavbarVisibility) {
            if (mIsNavSwitchingMode) {
                return false;
            }
            mIsNavSwitchingMode = true;
            boolean showing = ((Boolean)newValue);
            Settings.Secure.putIntForUser(resolver, Settings.Secure.NAVIGATION_BAR_VISIBLE,
                    showing ? 1 : 0, UserHandle.USER_CURRENT);
            updateBarVisibleAndUpdatePrefs(showing);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mIsNavSwitchingMode = false;
                }
            }, 1500);
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
        } else if (preference.equals(mNavbarDynamic)) {
            boolean isDynamic = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.NAVBAR_DYNAMIC,
                    isDynamic ? 1 : 0, UserHandle.USER_CURRENT);
            Toast.makeText(getActivity(), R.string.restart_app_required,
                    Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver, Settings.Secure.NAVIGATION_BAR_VISIBLE,
             DUActionUtils.hasNavbarByDefault(mContext) ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_MODE, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_HEIGHT, 80, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_HEIGHT_LANDSCAPE, 80, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
            Settings.Secure.NAVIGATION_BAR_WIDTH, 80, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
            Settings.System.NAVBAR_DYNAMIC, 0, UserHandle.USER_CURRENT);
        CarbonGesturesSettings.reset(mContext);
        EdgeGestureSettings.reset(mContext);
        Fling.reset(mContext);
        Pulse.reset(mContext);
        Smartbar.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
