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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.FontInfo;
import android.content.IFontService;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.SettingsPreferenceFragment;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.ui.Animations;
import com.crdroid.settings.fragments.ui.BlurPersonalizations;
import com.crdroid.settings.fragments.ui.CutoutSettings;
import com.crdroid.settings.fragments.ui.DozeFragment;
import com.crdroid.settings.fragments.ui.FontDialogPreference;
import com.crdroid.settings.fragments.ui.RoundedCorners;
import com.crdroid.settings.fragments.ui.SmartPixels;
import com.crdroid.settings.fragments.ui.ThemeSettings;

import java.util.ArrayList;
import java.util.List;

import lineageos.providers.LineageSettings;

public class UserInterface extends SettingsPreferenceFragment implements Indexable {

    private static final String SMART_PIXELS = "smart_pixels";
    private static final String KEY_FONT_PICKER_FRAGMENT_PREF = "custom_font";
    private static final String SUBS_PACKAGE = "projekt.substratum";
    private static final String DISPLAY_CUTOUT = "cutout_settings";

    private static final String CATEGORY_SUBSTRATUM = "category_substratum";

    private Preference mSmartPixels;
    private FontDialogPreference mFontPreference;
    private IFontService mFontService;
    private Preference mDisplayCutout;
    private PreferenceCategory substratumCategory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_ui);

        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources res = getResources();

        final boolean hasNotch = res.getBoolean(
                org.lineageos.platform.internal.R.bool.config_haveNotch);

        mDisplayCutout = (Preference) prefScreen.findPreference(DISPLAY_CUTOUT);
        if (!hasNotch)
            prefScreen.removePreference(mDisplayCutout);

        mSmartPixels = (Preference) prefScreen.findPreference(SMART_PIXELS);
        boolean mSmartPixelsSupported = res.getBoolean(
                com.android.internal.R.bool.config_supportSmartPixels);
        boolean mBurnInSupported = res.getBoolean(
                com.android.internal.R.bool.config_enableBurnInProtection);
        if (!mSmartPixelsSupported || !mBurnInSupported)
            prefScreen.removePreference(mSmartPixels);

       substratumCategory =
                (PreferenceCategory) prefScreen.findPreference(CATEGORY_SUBSTRATUM);

       mFontPreference =  (FontDialogPreference) findPreference(KEY_FONT_PICKER_FRAGMENT_PREF);
       mFontService = IFontService.Stub.asInterface(
                ServiceManager.getService("fontservice"));
        if (!Utils.isPackageInstalled(getActivity(), SUBS_PACKAGE)) {
            mFontPreference.setSummary(getCurrentFontInfo().fontName.replace("_", " "));
            prefScreen.removePreference(substratumCategory);
        } else {
            mFontPreference.setSummary(getActivity().getString(
                    R.string.disable_fonts_installed_title));
        }
    }

    private FontInfo getCurrentFontInfo() {
        try {
            return mFontService.getFontInfo();
        } catch (RemoteException e) {
            return FontInfo.getDefaultFontInfo();
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.FORCE_AUTHORIZE_SUBSTRATUM_PACKAGES, 0, UserHandle.USER_CURRENT);
        Animations.reset(mContext);
        BlurPersonalizations.reset(mContext);
        CutoutSettings.reset(mContext);
        DozeFragment.reset(mContext);
        RoundedCorners.reset(mContext);
        SmartPixels.reset(mContext);
        ThemeSettings.reset(mContext);
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
                    sir.xmlResId = R.xml.crdroid_settings_ui;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();

                    boolean hasNotch = res.getBoolean(
                            org.lineageos.platform.internal.R.bool.config_haveNotch);
                    if (!hasNotch)
                        keys.add(DISPLAY_CUTOUT);
                    boolean mSmartPixelsSupported = res.getBoolean(
                            com.android.internal.R.bool.config_supportSmartPixels);
                    boolean mBurnInSupported = res.getBoolean(
                            com.android.internal.R.bool.config_enableBurnInProtection);
                    if (!mSmartPixelsSupported || !mBurnInSupported)
                        keys.add(SMART_PIXELS);

                    return keys;
                }
            };
}
