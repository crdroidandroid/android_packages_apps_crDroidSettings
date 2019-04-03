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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.misc.AggressiveBattery;
import com.crdroid.settings.fragments.misc.AlarmBlocker;
import com.crdroid.settings.fragments.misc.GamingMode;
import com.crdroid.settings.fragments.misc.ImeSettings;
import com.crdroid.settings.fragments.misc.ScreenStateToggles;
import com.crdroid.settings.fragments.misc.WakeLockBlocker;

import java.util.List;
import java.util.ArrayList;

public class Miscellaneous extends SettingsPreferenceFragment 
        implements Indexable, Preference.OnPreferenceChangeListener {

    public static final String TAG = "Miscellaneous";

    private static final String KEY_LOCK_CLOCK = "lock_clock";
    private static final String KEY_LOCK_CLOCK_PACKAGE_NAME = "com.cyanogenmod.lockclock";
    private static final String SHOW_CPU_INFO_KEY = "show_cpu_info";

    private SwitchPreference mShowCpuInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.crdroid_settings_misc);

        ContentResolver resolver = mContext.getContentResolver();

        // mLockClock
        if (!Utils.isPackageInstalled(mContext, KEY_LOCK_CLOCK_PACKAGE_NAME)) {
            getPreferenceScreen().removePreference(findPreference(KEY_LOCK_CLOCK));
        }

        mShowCpuInfo = (SwitchPreference) findPreference(SHOW_CPU_INFO_KEY);
        mShowCpuInfo.setChecked(Settings.Global.getInt(resolver,
                Settings.Global.SHOW_CPU_OVERLAY, 0) == 1);
        mShowCpuInfo.setOnPreferenceChangeListener(this);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver,
                Settings.Global.TOAST_ICON, 1);
        Settings.System.putIntForUser(resolver,
                Settings.System.MEDIA_SCANNER_ON_BOOT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.POCKET_JUDGE, 0, UserHandle.USER_CURRENT);
        writeCpuInfoOptions(mContext, false);
        AggressiveBattery.reset(mContext);
        AlarmBlocker.reset(mContext);
        GamingMode.reset(mContext);
        ImeSettings.reset(mContext);
        ScreenStateToggles.reset(mContext);
        WakeLockBlocker.reset(mContext);
    }

    private static void writeCpuInfoOptions(Context mContext, boolean value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, value ? 1 : 0);
        Intent service = (new Intent())
                .setClassName("com.android.systemui", "com.android.systemui.CPUInfoService");
        if (value) {
            mContext.startService(service);
        } else {
            mContext.stopService(service);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        Context mContext = getActivity().getApplicationContext();
        if (preference == mShowCpuInfo) {
            writeCpuInfoOptions(mContext, (Boolean) newValue);
            return true;
        }
        return false;
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
                    sir.xmlResId = R.xml.crdroid_settings_misc;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    if (!Utils.isPackageInstalled(context, KEY_LOCK_CLOCK_PACKAGE_NAME)) {
                        keys.add(KEY_LOCK_CLOCK);
                    }

                    return keys;
                }
            };
}
