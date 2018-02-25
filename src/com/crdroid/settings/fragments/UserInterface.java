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
import android.widget.Toast;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.fragments.ui.AnimationControls;
import com.crdroid.settings.fragments.ui.BlurPersonalizations;
import com.crdroid.settings.fragments.ui.CarbonGesturesSettings;
import com.crdroid.settings.fragments.ui.DozeFragment;
import com.crdroid.settings.fragments.ui.FontDialogPreference;
import com.crdroid.settings.R;

import java.util.ArrayList;
import java.util.List;

import lineageos.providers.LineageSettings;

public class UserInterface extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_TOAST_ANIMATION = "toast_animation";
    private static final String KEY_LISTVIEW_ANIMATION = "listview_animation";
    private static final String KEY_LISTVIEW_INTERPOLATOR = "listview_interpolator";
/*
    private static final String POWER_MENU_ANIMATION = "power_menu_animation";
*/
    private static final String PREF_TILE_ANIM_STYLE = "qs_tile_animation_style";
    private static final String PREF_TILE_ANIM_DURATION = "qs_tile_animation_duration";
    private static final String PREF_TILE_ANIM_INTERPOLATOR = "qs_tile_animation_interpolator";
    private static final String SCROLLINGCACHE_PREF = "pref_scrollingcache";
    private static final String SCROLLINGCACHE_PERSIST_PROP = "persist.sys.scrollingcache";
    private static final String SCROLLINGCACHE_DEFAULT = "1";

    private static final String KEY_FONT_PICKER_FRAGMENT_PREF = "custom_font";
    private static final String SUBS_PACKAGE = "projekt.substratum";

    private static final String CATEGORY_SUBSTRATUM = "category_substratum";

    private FontDialogPreference mFontPreference;
    private IFontService mFontService;

    private ListPreference mToastAnimation;
    private ListPreference mListViewAnimation;
    private ListPreference mListViewInterpolator;
/*
    private ListPreference mPowerMenuAnimation;
*/
    private ListPreference mTileAnimationStyle;
    private ListPreference mTileAnimationDuration;
    private ListPreference mTileAnimationInterpolator;
    private ListPreference mScrollingCachePref;

    Toast mToast;

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

        mToastAnimation = (ListPreference) findPreference(KEY_TOAST_ANIMATION);
        int toastanimation = Settings.System.getIntForUser(resolver,
                Settings.System.TOAST_ANIMATION, 1,
                UserHandle.USER_CURRENT);
        mToastAnimation.setValue(String.valueOf(toastanimation));
        mToastAnimation.setSummary(mToastAnimation.getEntry());
        mToastAnimation.setOnPreferenceChangeListener(this);

        mListViewAnimation = (ListPreference) findPreference(KEY_LISTVIEW_ANIMATION);
        int listviewanimation = Settings.System.getIntForUser(resolver,
                Settings.System.LISTVIEW_ANIMATION, 0,
                UserHandle.USER_CURRENT);
        mListViewAnimation.setValue(String.valueOf(listviewanimation));
        mListViewAnimation.setSummary(mListViewAnimation.getEntry());
        mListViewAnimation.setOnPreferenceChangeListener(this);

        mListViewInterpolator = (ListPreference) findPreference(KEY_LISTVIEW_INTERPOLATOR);
        int listviewinterpolator = Settings.System.getIntForUser(resolver,
                Settings.System.LISTVIEW_INTERPOLATOR, 0,
                UserHandle.USER_CURRENT);
        mListViewInterpolator.setValue(String.valueOf(listviewinterpolator));
        mListViewInterpolator.setSummary(mListViewInterpolator.getEntry());
        mListViewInterpolator.setEnabled(listviewanimation > 0);
        mListViewInterpolator.setOnPreferenceChangeListener(this);

/*
        mPowerMenuAnimation = (ListPreference) findPreference(POWER_MENU_ANIMATION);
        int powermenuanimation = Settings.System.getIntForUser(resolver,
                Settings.System.POWER_MENU_ANIMATION, 0,
                UserHandle.USER_CURRENT);
        mPowerMenuAnimation.setValue(String.valueOf(powermenuanimation));
        mPowerMenuAnimation.setSummary(mPowerMenuAnimation.getEntry());
        mPowerMenuAnimation.setOnPreferenceChangeListener(this);
*/

        mTileAnimationStyle = (ListPreference) findPreference(PREF_TILE_ANIM_STYLE);
        int tileAnimationStyle = Settings.System.getIntForUser(resolver,
                Settings.System.ANIM_TILE_STYLE, 0,
                UserHandle.USER_CURRENT);
        mTileAnimationStyle.setValue(String.valueOf(tileAnimationStyle));
        mTileAnimationStyle.setSummary(mTileAnimationStyle.getEntry());
        mTileAnimationStyle.setOnPreferenceChangeListener(this);

        mTileAnimationDuration = (ListPreference) findPreference(PREF_TILE_ANIM_DURATION);
        int tileAnimationDuration = Settings.System.getIntForUser(resolver,
                Settings.System.ANIM_TILE_DURATION, 2000,
                UserHandle.USER_CURRENT);
        mTileAnimationDuration.setValue(String.valueOf(tileAnimationDuration));
        mTileAnimationDuration.setSummary(mTileAnimationDuration.getEntry());
        mTileAnimationDuration.setEnabled(tileAnimationStyle > 0);
        mTileAnimationDuration.setOnPreferenceChangeListener(this);

        mTileAnimationInterpolator = (ListPreference) findPreference(PREF_TILE_ANIM_INTERPOLATOR);
        int tileAnimationInterpolator = Settings.System.getIntForUser(resolver,
                Settings.System.ANIM_TILE_INTERPOLATOR, 0,
                UserHandle.USER_CURRENT);
        mTileAnimationInterpolator.setValue(String.valueOf(tileAnimationInterpolator));
        mTileAnimationInterpolator.setSummary(mTileAnimationInterpolator.getEntry());
        mTileAnimationInterpolator.setEnabled(tileAnimationStyle > 0);
        mTileAnimationInterpolator.setOnPreferenceChangeListener(this);

        mScrollingCachePref = (ListPreference) findPreference(SCROLLINGCACHE_PREF);
        mScrollingCachePref.setValue(SystemProperties.get(SCROLLINGCACHE_PERSIST_PROP,
                SystemProperties.get(SCROLLINGCACHE_PERSIST_PROP, SCROLLINGCACHE_DEFAULT)));
        mScrollingCachePref.setSummary(mScrollingCachePref.getEntry());
        mScrollingCachePref.setOnPreferenceChangeListener(this);

        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }

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

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mToastAnimation) {
            int value = Integer.parseInt((String) newValue);
            int index = mToastAnimation.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.TOAST_ANIMATION, value, UserHandle.USER_CURRENT);
            mToastAnimation.setSummary(mToastAnimation.getEntries()[index]);
            if (mToast != null) {
                mToast.cancel();
            }
            mToast = Toast.makeText(getActivity(), R.string.toast_animation_test,
                    Toast.LENGTH_SHORT);
            mToast.show();
            return true;
        } else if (preference == mListViewAnimation) {
            int value = Integer.parseInt((String) newValue);
            int index = mListViewAnimation.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.LISTVIEW_ANIMATION, value, UserHandle.USER_CURRENT);
            mListViewAnimation.setSummary(mListViewAnimation.getEntries()[index]);
            mListViewInterpolator.setEnabled(value > 0);
            return true;
        } else if (preference == mListViewInterpolator) {
            int value = Integer.parseInt((String) newValue);
            int index = mListViewInterpolator.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.LISTVIEW_INTERPOLATOR, value, UserHandle.USER_CURRENT);
            mListViewInterpolator.setSummary(mListViewInterpolator.getEntries()[index]);
            return true;
/*
        } else if (preference == mPowerMenuAnimation) {
            int value = Integer.parseInt((String) newValue);
            int index = mPowerMenuAnimation.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver,
                    Settings.System.POWER_MENU_ANIMATION, value, UserHandle.USER_CURRENT);
            mPowerMenuAnimation.setSummary(mPowerMenuAnimation.getEntries()[index]);
            return true;
*/
        } else if (preference == mTileAnimationStyle) {
            int value = Integer.valueOf((String) newValue);
            int index = mTileAnimationStyle.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver, Settings.System.ANIM_TILE_STYLE,
                    value, UserHandle.USER_CURRENT);
            mTileAnimationStyle.setSummary(mTileAnimationStyle.getEntries()[index]);
            mTileAnimationDuration.setEnabled(value > 0);
            mTileAnimationInterpolator.setEnabled(value > 0);
            return true;
       } else if (preference == mTileAnimationDuration) {
            int value = Integer.valueOf((String) newValue);
            int index = mTileAnimationDuration.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver, Settings.System.ANIM_TILE_DURATION,
                    value, UserHandle.USER_CURRENT);
            mTileAnimationDuration.setSummary(mTileAnimationDuration.getEntries()[index]);
            return true;
       } else if (preference == mTileAnimationInterpolator) {
            int value = Integer.valueOf((String) newValue);
            int index = mTileAnimationInterpolator.findIndexOfValue((String) newValue);
            Settings.System.putIntForUser(resolver, Settings.System.ANIM_TILE_INTERPOLATOR,
                    value, UserHandle.USER_CURRENT);
            mTileAnimationInterpolator.setSummary(mTileAnimationInterpolator.getEntries()[index]);
            return true;
        } else if (preference == mScrollingCachePref) {
            String value = (String) newValue;
            int index = mScrollingCachePref.findIndexOfValue(value);
            SystemProperties.set(SCROLLINGCACHE_PERSIST_PROP, value);
            mScrollingCachePref.setSummary(mScrollingCachePref.getEntries()[index]);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver,
                Settings.Global.SCREEN_OFF_ANIMATION, 0);
        Settings.System.putIntForUser(resolver,
                Settings.System.TOAST_ANIMATION, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LISTVIEW_ANIMATION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LISTVIEW_INTERPOLATOR, 0, UserHandle.USER_CURRENT);
/*
        Settings.System.putIntForUser(resolver,
                Settings.System.POWER_MENU_ANIMATION, 0, UserHandle.USER_CURRENT);
*/
        Settings.System.putIntForUser(resolver,
                Settings.System.ANIM_TILE_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ANIM_TILE_DURATION, 2000, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ANIM_TILE_INTERPOLATOR, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.FORCE_AUTHORIZE_SUBSTRATUM_PACKAGES, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putStringForUser(resolver,
                LineageSettings.System.LONG_SCREEN_APPS, null, UserHandle.USER_CURRENT);
        SystemProperties.set(SCROLLINGCACHE_PERSIST_PROP, SCROLLINGCACHE_DEFAULT);
        AnimationControls.reset(mContext);
        BlurPersonalizations.reset(mContext);
        CarbonGesturesSettings.reset(mContext);
        DozeFragment.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
