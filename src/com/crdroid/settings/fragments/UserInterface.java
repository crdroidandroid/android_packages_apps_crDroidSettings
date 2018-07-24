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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.FontInfo;
import android.content.IFontService;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.fragments.ui.Animations;
import com.crdroid.settings.fragments.ui.BlurPersonalizations;
import com.crdroid.settings.fragments.ui.DozeFragment;
import com.crdroid.settings.fragments.ui.FontDialogPreference;
import com.crdroid.settings.fragments.ui.SmartPixels;
import com.crdroid.settings.R;

import java.util.ArrayList;
import java.util.List;

import lineageos.providers.LineageSettings;

public class UserInterface extends SettingsPreferenceFragment {

    private static final String KEY_FONT_PICKER_FRAGMENT_PREF = "custom_font";
    private static final String SUBS_PACKAGE = "projekt.substratum";

    private static final String CATEGORY_SUBSTRATUM = "category_substratum";

    private FontDialogPreference mFontPreference;
    private IFontService mFontService;

    private IntentFilter mIntentFilter;

    private PreferenceCategory substratumCategory;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("com.android.server.ACTION_FONT_CHANGED")) {
                mFontPreference.stopProgress();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        final Context context = getActivity();
        context.registerReceiver(mIntentReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        final Context context = getActivity();
        context.unregisterReceiver(mIntentReceiver);
        mFontPreference.stopProgress();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_ui);
        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

       substratumCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_SUBSTRATUM);

       mFontPreference =  (FontDialogPreference) findPreference(KEY_FONT_PICKER_FRAGMENT_PREF);
       mFontService = IFontService.Stub.asInterface(
                ServiceManager.getService("fontservice"));

        if (!isPackageInstalled(SUBS_PACKAGE, getActivity())) {
            mFontPreference.setSummary(getCurrentFontInfo().fontName.replace("_", " "));
            prefScreen.removePreference(substratumCategory);
        } else {
            mFontPreference.setSummary(getActivity().getString(
                    R.string.disable_fonts_installed_title));
        }

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("com.android.server.ACTION_FONT_CHANGED");
    }

    private FontInfo getCurrentFontInfo() {
        try {
            return mFontService.getFontInfo();
        } catch (RemoteException e) {
            return FontInfo.getDefaultFontInfo();
        }
    }

    private boolean isPackageInstalled(String package_name, Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(package_name, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.FORCE_AUTHORIZE_SUBSTRATUM_PACKAGES, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putStringForUser(resolver,
                LineageSettings.System.LONG_SCREEN_APPS, null, UserHandle.USER_CURRENT);
        Animations.reset(mContext);
        BlurPersonalizations.reset(mContext);
        DozeFragment.reset(mContext);
        SmartPixels.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
