/*
 * Copyright (C) 2024 crDroid Android Project
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
package com.crdroid.settings.fragments.quicksettings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SearchIndexable
public class QsHeaderImageSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String CUSTOM_HEADER_BROWSE = "custom_header_browse";
    private static final String DAYLIGHT_HEADER_PACK = "daylight_header_pack";
    private static final String CUSTOM_HEADER_PROVIDER = "qs_header_provider";
    private static final String STATUS_BAR_CUSTOM_HEADER = "status_bar_custom_header";
    private static final String FILE_HEADER_SELECT = "file_header_select";
    private static final int REQUEST_PICK_IMAGE = 10001;

    private Preference mHeaderBrowse;
    private ListPreference mDaylightHeaderPack;
    private ListPreference mHeaderProvider;
    private String mDaylightHeaderProvider;
    private Preference mFileHeader;
    private String mFileHeaderProvider;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.qs_header_image_settings);

        ContentResolver resolver = getContext().getContentResolver();

        mHeaderBrowse = findPreference(CUSTOM_HEADER_BROWSE);
        mHeaderBrowse.setEnabled(isBrowseHeaderAvailable());

        mDaylightHeaderPack = (ListPreference) findPreference(DAYLIGHT_HEADER_PACK);

        List<String> entries = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        getAvailableHeaderPacks(entries, values);
        mDaylightHeaderPack.setEntries(entries.toArray(new String[entries.size()]));
        mDaylightHeaderPack.setEntryValues(values.toArray(new String[values.size()]));
        updateHeaderProviderSummary();
        mDaylightHeaderPack.setOnPreferenceChangeListener(this);

        mDaylightHeaderProvider = "daylight";
        mFileHeaderProvider = "file";
        String providerName = Settings.System.getString(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER);
        if (providerName == null) {
            providerName = mDaylightHeaderProvider;
        }
        mHeaderBrowse.setEnabled(isBrowseHeaderAvailable() && !providerName.equals(mFileHeaderProvider));

        mHeaderProvider = (ListPreference) findPreference(CUSTOM_HEADER_PROVIDER);
        int valueIndex = mHeaderProvider.findIndexOfValue(providerName);
        mHeaderProvider.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
        mHeaderProvider.setSummary(mHeaderProvider.getEntry());
        mHeaderProvider.setOnPreferenceChangeListener(this);
        mDaylightHeaderPack.setEnabled(providerName.equals(mDaylightHeaderProvider));

        mFileHeader = findPreference(FILE_HEADER_SELECT);
        mFileHeader.setEnabled(providerName.equals(mFileHeaderProvider));
    }

    private void updateHeaderProviderSummary() {
        String settingHeaderPackage = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK);
        int valueIndex = mDaylightHeaderPack.findIndexOfValue(settingHeaderPackage);
        if (valueIndex >= 0) {
            mDaylightHeaderPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
            mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntry());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getContext().getContentResolver();
        switch (preference.getKey()) {
            case DAYLIGHT_HEADER_PACK:
                String dhvalue = (String) newValue;
                Settings.System.putString(resolver,
                        Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, dhvalue);
                int dhvalueIndex = mDaylightHeaderPack.findIndexOfValue(dhvalue);
                mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntries()[dhvalueIndex]);
                return true;

            case CUSTOM_HEADER_PROVIDER:
                String value = (String) newValue;
                Settings.System.putString(resolver,
                        Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, value);
                int valueIndex = mHeaderProvider.findIndexOfValue(value);
                mHeaderProvider.setSummary(mHeaderProvider.getEntries()[valueIndex]);
                mDaylightHeaderPack.setEnabled(value.equals(mDaylightHeaderProvider));
                mHeaderBrowse.setEnabled(!value.equals(mFileHeaderProvider));
                mHeaderBrowse.setTitle(valueIndex == 0 ? R.string.qs_header_browse_title : R.string.qs_header_pick_title);
                mHeaderBrowse.setSummary(valueIndex == 0 ? R.string.qs_header_browse_summary : R.string.qs_header_pick_summary);
                mFileHeader.setEnabled(value.equals(mFileHeaderProvider));
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mFileHeader) {
            try {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_IMAGE);
                return true;
            } catch (Exception e) {
                Toast.makeText(getContext(), R.string.qs_header_needs_gallery, Toast.LENGTH_LONG).show();
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean isBrowseHeaderAvailable() {
        PackageManager pm = getContext().getPackageManager();
        Intent browse = new Intent();
        browse.setClassName("org.omnirom.omnistyle", "org.omnirom.omnistyle.PickHeaderActivity");
        return pm.resolveActivity(browse, 0) != null;
    }

    private void getAvailableHeaderPacks(List<String> entries, List<String> values) {
        Map<String, String> headerMap = new HashMap<>();
        Intent intent = new Intent();
        PackageManager packageManager = getContext().getPackageManager();
        intent.setAction("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(intent, 0)) {
            String packageName = r.activityInfo.packageName;
            String label = r.activityInfo.loadLabel(packageManager).toString();
            if (label == null) {
                label = packageName;
            }
            headerMap.put(label, packageName);
        }
        intent.setAction("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo r : packageManager.queryIntentActivities(intent, 0)) {
            String packageName = r.activityInfo.packageName;
            String label = r.activityInfo.loadLabel(packageManager).toString();
            if (r.activityInfo.name.endsWith(".theme")) {
                continue;
            }
            if (label == null) {
                label = packageName;
            }
            headerMap.put(label, packageName + "/" + r.activityInfo.name);
        }
        List<String> labelList = new ArrayList<>(headerMap.keySet());
        Collections.sort(labelList);
        for (String label : labelList) {
            entries.add(label);
            values.add(headerMap.get(label));
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, 142, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            final Uri imageUri = result.getData();
            if (imageUri != null) {
                Settings.System.putString(getContentResolver(),
                    Settings.System.STATUS_BAR_FILE_HEADER_IMAGE, imageUri.toString());
            }
        }
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
                    sir.xmlResId = R.xml.qs_header_image_settings;
                    result.add(sir);
                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    ArrayList<String> result = new ArrayList<String>();
                    return result;
                }
            };
}
