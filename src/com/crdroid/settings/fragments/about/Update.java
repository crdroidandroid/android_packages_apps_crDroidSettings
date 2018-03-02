/*
 * Copyright (C) 2016-2018 crDroid Android Project
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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.about.update.configs.AppConfig;
import com.crdroid.settings.fragments.about.update.configs.LinkConfig;
import com.crdroid.settings.fragments.about.update.configs.OTAVersion;
import com.crdroid.settings.fragments.about.update.dialogs.WaitDialogFragment;
import com.crdroid.settings.fragments.about.update.tasks.CheckUpdateTask;
import com.crdroid.settings.fragments.about.update.utils.OTAUtils;
import com.crdroid.settings.fragments.about.update.xml.OTALink;
import com.android.settings.SettingsPreferenceFragment;

import java.util.List;

public class Update extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener,
        WaitDialogFragment.OTADialogListener,
        LinkConfig.LinkConfigListener {

    private static final String KEY_ROM_INFO = "key_rom_info";
    private static final String KEY_MAINTAINER_INFO = "key_maintainer_info";
    private static final String KEY_CHECK_UPDATE = "key_check_update";
    private static final String KEY_UPDATE_INTERVAL = "key_update_interval";
    private static final String CATEGORY_LINKS = "category_links";

    private static final String DOWNLOAD_TAG = "download";
    private static final String CHANGELOG_TAG = "changelog";
    private static final String GAPPS_TAG = "gapps";
    private static final String FORUM_TAG = "forum";
    private static final String PAYPAL_TAG = "paypal";
    private static final String TELEGRAM_TAG = "telegram";

    private static PreferenceScreen mRomInfo;
    private static PreferenceScreen mMaintainerInfo;
    private static PreferenceScreen mCheckUpdate;
    private static ListPreference mUpdateInterval;
    private static PreferenceCategory mLinksCategory;

    private static PreferenceScreen mDownloadLink;
    private static PreferenceScreen mChangelogLink;
    private static PreferenceScreen mGappsLink;
    private static PreferenceScreen mForumLink;
    private static PreferenceScreen mPayPalLink;
    private static PreferenceScreen mTelegramLink;

    private static CheckUpdateTask mTask;
    private static boolean mShowLinks;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadPreferences(true);
    }

    private void loadPreferences(boolean force) {
        Context context = getActivity().getApplicationContext();

        if (getPreferenceScreen() != null)
            getPreferenceScreen().removeAll();
        addPreferencesFromResource(R.xml.ota_settings);

        mRomInfo = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_ROM_INFO);
        mMaintainerInfo = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_MAINTAINER_INFO);
        mCheckUpdate = (PreferenceScreen) getPreferenceScreen().findPreference(KEY_CHECK_UPDATE);

        mUpdateInterval = (ListPreference) getPreferenceScreen().findPreference(KEY_UPDATE_INTERVAL);
        mUpdateInterval.setOnPreferenceChangeListener(this);

        mLinksCategory = (PreferenceCategory) getPreferenceScreen().findPreference(CATEGORY_LINKS);
        mDownloadLink = (PreferenceScreen) getPreferenceScreen().findPreference(DOWNLOAD_TAG);
        mChangelogLink = (PreferenceScreen) getPreferenceScreen().findPreference(CHANGELOG_TAG);
        mGappsLink = (PreferenceScreen) getPreferenceScreen().findPreference(GAPPS_TAG);
        mForumLink = (PreferenceScreen) getPreferenceScreen().findPreference(FORUM_TAG);
        mPayPalLink = (PreferenceScreen) getPreferenceScreen().findPreference(PAYPAL_TAG);
        mTelegramLink = (PreferenceScreen) getPreferenceScreen().findPreference(TELEGRAM_TAG);

        updatePreferences(context);
        updateLinks(force);
    }

    public static void updatePreferences(Context context) {
        updateRomInfo(context);
        updateMaintainerInfo(context);
        updateLastCheckSummary(context);
        updateIntervalSummary(context);
        updateDownloadLinks(context);
    }

    public static void showLinks(boolean show) {
        mShowLinks = show;
    }

    private static void updateDownloadLinks(Context context) {
        List<OTALink> links = LinkConfig.getInstance().getLinks(context, true);

        if (mLinksCategory == null)
            return;

        mDownloadLink.setVisible(false);
        mChangelogLink.setVisible(false);
        mGappsLink.setVisible(false);
        mForumLink.setVisible(false);
        mPayPalLink.setVisible(false);
        mTelegramLink.setVisible(false);

        for (OTALink link : links) {
            String id = link.getId();

            if (id.equalsIgnoreCase(DOWNLOAD_TAG)) {
                mDownloadLink.setTitle(link.getTitle());
                mDownloadLink.setSummary(link.getDescription());
                mDownloadLink.setVisible(mShowLinks);
            } else if (id.equalsIgnoreCase(CHANGELOG_TAG)) {
                mChangelogLink.setTitle(link.getTitle());
                mChangelogLink.setSummary(link.getDescription());
                mChangelogLink.setVisible(mShowLinks);
            } else if (id.equalsIgnoreCase(GAPPS_TAG)) {
                mGappsLink.setTitle(link.getTitle());
                mGappsLink.setSummary(link.getDescription());
                mGappsLink.setVisible(mShowLinks);
            } else if (id.equalsIgnoreCase(FORUM_TAG)) {
                mForumLink.setTitle(link.getTitle());
                mForumLink.setSummary(link.getDescription());
                mForumLink.setVisible(mShowLinks);
            } else if (id.equalsIgnoreCase(PAYPAL_TAG)) {
                mPayPalLink.setTitle(link.getTitle());
                mPayPalLink.setSummary(link.getDescription());
                mPayPalLink.setVisible(mShowLinks);
            } else if (id.equalsIgnoreCase(TELEGRAM_TAG)) {
                mTelegramLink.setTitle(link.getTitle());
                mTelegramLink.setSummary(link.getDescription());
                mTelegramLink.setVisible(mShowLinks);
            }
        }

        mLinksCategory.setTitle(mShowLinks ? context.getString(R.string.links_category) : "");
    }

    private void updateLinks(boolean force) {
        Context context = getActivity().getApplicationContext();
        List<OTALink> links = LinkConfig.getInstance().getLinks(context, force);
        Drawable drawable = context.getDrawable(R.drawable.ic_web);
        TypedArray ta =
               context.obtainStyledAttributes(new int[]{android.R.attr.colorControlNormal});
        drawable.setTint(ta.getColor(0, 0));

        if (mLinksCategory == null)
            return;

        for (OTALink link : links) {
            String id = link.getId();

            if (id.equalsIgnoreCase(DOWNLOAD_TAG) ||
                    id.equalsIgnoreCase(CHANGELOG_TAG) ||
                    id.equalsIgnoreCase(GAPPS_TAG) ||
                    id.equalsIgnoreCase(FORUM_TAG) ||
                    id.equalsIgnoreCase(PAYPAL_TAG) ||
                    id.equalsIgnoreCase(TELEGRAM_TAG)) {
                continue;
            }
            PreferenceScreen linkPref = (PreferenceScreen) getPreferenceScreen().findPreference(id.toLowerCase());
            if (linkPref == null) {
                linkPref = getPreferenceManager().createPreferenceScreen(context);
                linkPref.setKey(id.toLowerCase());
                linkPref.setIcon(drawable);
                mLinksCategory.addPreference(linkPref);
            }
            if (linkPref != null) {
                String title = link.getTitle();
                linkPref.setTitle(title.isEmpty() ? id : title);
                linkPref.setSummary(link.getDescription());
            }
        }
        mLinksCategory.setTitle(mShowLinks ? context.getString(R.string.links_category) : "");
    }

    private static void updateRomInfo(Context context) {
        if (mRomInfo == null)
            return;

        String fullLocalVersion = OTAVersion.getFullLocalVersion(context);
        mRomInfo.setTitle(fullLocalVersion);

        String prefix = context.getString(R.string.latest_version);
        String fullLatestVersion = AppConfig.getFullLatestVersion(context);
        if (fullLatestVersion == null || fullLatestVersion.isEmpty()) {
            fullLatestVersion = context.getString(R.string.unknown);
            mRomInfo.setSummary(String.format(prefix, fullLatestVersion));
        } else {
            String shortLocalVersion = OTAVersion.extractVersionFrom(fullLocalVersion, context);
            String shortLatestVersion = OTAVersion.extractVersionFrom(fullLatestVersion, context);
            if (!OTAVersion.compareVersion(shortLatestVersion, shortLocalVersion, context)) {
                mRomInfo.setSummary(context.getString(R.string.system_uptodate));
            } else {
                mRomInfo.setSummary(String.format(prefix, fullLatestVersion));
            }
        }
    }

    private static void updateMaintainerInfo(Context context) {
        if (mMaintainerInfo == null)
            return;

        String maintainer = AppConfig.getMaintainer(context);
        if (maintainer == null || maintainer.isEmpty()) {
            maintainer = context.getString(R.string.maintainer_unknown);
        }
        mMaintainerInfo.setSummary(maintainer);
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
        super.onResume();
        loadPreferences(false);
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
        loadPreferences(false);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();

        if (preference == mUpdateInterval)
            return super.onPreferenceTreeClick(preference);

        Context context = getActivity().getApplicationContext();
        if (preference == mCheckUpdate) {
            mTask = CheckUpdateTask.getInstance(false);
            if (!mTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context);
            }
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
        Context context = getActivity().getApplicationContext();
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
