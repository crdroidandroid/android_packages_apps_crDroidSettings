/*
 * Copyright (C) 2016-2017 crDroid Android Project
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
package com.crdroid.settings.fragments.about;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceCategory;

import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.about.ota.configs.AppConfig;
import com.crdroid.settings.fragments.about.ota.configs.LinkConfig;
import com.crdroid.settings.fragments.about.ota.configs.OTAVersion;
import com.crdroid.settings.fragments.about.ota.dialogs.WaitDialogFragment;
import com.crdroid.settings.fragments.about.ota.tasks.CheckUpdateTask;
import com.crdroid.settings.fragments.about.ota.utils.OTAUtils;
import com.crdroid.settings.fragments.about.ota.xml.OTALink;
import com.android.settings.SettingsPreferenceFragment;

import java.util.List;

public class OTA extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener,
        WaitDialogFragment.OTADialogListener,
        LinkConfig.LinkConfigListener {

    private static final String KEY_ROM_INFO = "key_rom_info";
    private static final String KEY_CHECK_UPDATE = "key_check_update";
    private static final String KEY_UPDATE_INTERVAL = "key_update_interval";
    private static final String CATEGORY_LINKS = "category_links";

    private static PreferenceScreen mRomInfo;
    private static PreferenceScreen mCheckUpdate;
    private static ListPreference mUpdateInterval;
    private static PreferenceCategory mLinksCategory;

    private static CheckUpdateTask mTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.ota_settings);

        Context context = getActivity();

        mRomInfo = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_ROM_INFO);
        mCheckUpdate = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_CHECK_UPDATE);

        mUpdateInterval = (ListPreference) getPreferenceScreen().findPreference(KEY_UPDATE_INTERVAL);
        mUpdateInterval.setOnPreferenceChangeListener(this);

        mLinksCategory = (PreferenceCategory) getPreferenceScreen().findPreference(CATEGORY_LINKS);

        updatePreferences(context);
        updateLinks(context, false);
    }

    public static void updatePreferences(Context context) {
        updateRomInfo(context);
        updateLastCheckSummary(context);
        updateIntervalSummary(context);
    }

    private void updateLinks(Context context, boolean force) {
        List<OTALink> links = LinkConfig.getInstance().getLinks(context, force);
        boolean removeCat = true;

        if (mLinksCategory == null)
            return;

        for (OTALink link : links) {
            String id = link.getId();
            PreferenceScreen linkPref = (PreferenceScreen) getPreferenceScreen().findPreference(id);
            if (linkPref == null) {
                linkPref = getPreferenceManager().createPreferenceScreen(context);
                linkPref.setKey(id);
                mLinksCategory.addPreference(linkPref);
            }
            if (linkPref != null) {
                String title = link.getTitle();
                linkPref.setTitle(title.isEmpty() ? id : title);
                linkPref.setSummary(link.getDescription());
                removeCat = false;
            }
        }
        if (removeCat) {
            mLinksCategory.setTitle("");
        } else {
            mLinksCategory.setTitle(context.getResources().getString(R.string.links_category));
        }
    }

    private static void updateRomInfo(Context context) {
        if (mRomInfo == null)
            return;

        String fullLocalVersion = OTAVersion.getFullLocalVersion(context);
        mRomInfo.setTitle(fullLocalVersion);

        String prefix = context.getResources().getString(R.string.latest_version);
        String fullLatestVersion = AppConfig.getFullLatestVersion(context);
        if (fullLatestVersion == null || fullLatestVersion.isEmpty()) {
            fullLatestVersion = context.getResources().getString(R.string.unknown);
            mRomInfo.setSummary(String.format(prefix, fullLatestVersion));
        } else {
            String shortLocalVersion = OTAVersion.extractVersionFrom(fullLocalVersion, context);
            String shortLatestVersion = OTAVersion.extractVersionFrom(fullLatestVersion, context);
            if (!OTAVersion.compareVersion(shortLatestVersion, shortLocalVersion, context)) {
                mRomInfo.setSummary(context.getResources().getString(R.string.system_uptodate));
            } else {
                mRomInfo.setSummary(String.format(prefix, fullLatestVersion));
            }
        }
    }

    private static void updateLastCheckSummary(Context context) {
        if (mCheckUpdate != null) {
            mCheckUpdate.setSummary(AppConfig.getLastCheck(context));
        }
    }

    private static void updateIntervalSummary(Context context) {
        if (mUpdateInterval != null) {
            mUpdateInterval.setValueIndex(AppConfig.getUpdateIntervalIndex(context));
            mUpdateInterval.setSummary(mUpdateInterval.getEntry());
        }
    }

    @Override
     public void onResume() {
        updatePreferences(getActivity());
        updateLinks(getActivity(), false);
        super.onResume();
    }

    @Override
    public void onProgressCancelled() {
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
    }

    @Override
    public void onConfigChange() {
        updateLinks(getActivity(), true);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();

        if (preference == mUpdateInterval)
            return super.onPreferenceTreeClick(preference);

        Context context = getActivity();
        if (preference == mCheckUpdate) {
            mTask = CheckUpdateTask.getInstance(false);
            if (!mTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context);
            }
            updateLinks(context, true);
            return true;
        }

        OTALink link = LinkConfig.getInstance().findLink(key, context);
        if (link != null) {
            OTAUtils.launchUrl(link.getUrl(), context);
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        Context context = getActivity();
        if (preference == mUpdateInterval) {
            AppConfig.persistUpdateIntervalIndex(Integer.valueOf((String) value), context);
            updateIntervalSummary(context);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
     }
}
