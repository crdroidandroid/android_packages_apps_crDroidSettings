/*
 * Copyright (C) 2024-2026 crDroid Android Project
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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SearchIndexable
public class QsHeaderImageSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "QsHeaderImageSettings";

    private static final String CUSTOM_HEADER_BROWSE = "custom_header_browse";
    private static final String DAYLIGHT_HEADER_PACK = "daylight_header_pack";
    private static final String CUSTOM_HEADER_PROVIDER = "qs_header_provider";
    private static final String FILE_HEADER_SELECT = "file_header_select";
    private static final int REQUEST_PICK_IMAGE = 10001;

    private static final String PROVIDER_DAYLIGHT = "daylight";
    private static final String PROVIDER_FILE = "file";

    private static final String OMNISTYLE_PACKAGE = "org.omnirom.omnistyle";
    private static final String OMNISTYLE_PICK = OMNISTYLE_PACKAGE + ".PickHeaderActivity";
    private static final String OMNISTYLE_BROWSE = OMNISTYLE_PACKAGE + ".BrowseHeaderActivity";
    /** Pack key (package or package/activity) OmniStyle opens on; same format as our values. */
    private static final String EXTRA_HEADER_PACK = "header_pack";

    private static final String QSHEADER_RELATIVE_PATH = "Pictures/QSHeader";
    private static final String QSHEADER_DISPLAY_NAME = "qs_header_image";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private Preference mHeaderBrowse;
    private ListPreference mDaylightHeaderPack;
    private ListPreference mHeaderProvider;
    private Preference mFileHeader;
    private boolean mBrowseAvailable;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.qs_header_image_settings);

        mHeaderBrowse = findPreference(CUSTOM_HEADER_BROWSE);
        mHeaderProvider = (ListPreference) findPreference(CUSTOM_HEADER_PROVIDER);
        mDaylightHeaderPack = (ListPreference) findPreference(DAYLIGHT_HEADER_PACK);
        mFileHeader = findPreference(FILE_HEADER_SELECT);

        mHeaderProvider.setOnPreferenceChangeListener(this);
        mDaylightHeaderPack.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBrowseAvailable = isOmniStyleAvailable();

        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();
        getAvailableHeaderPacks(entries, values);
        mDaylightHeaderPack.setEntries(entries.toArray(new String[0]));
        mDaylightHeaderPack.setEntryValues(values.toArray(new String[0]));
        updateDaylightPackSummary();

        String providerName = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER);
        if (providerName == null || mHeaderProvider.findIndexOfValue(providerName) < 0) {
            providerName = PROVIDER_DAYLIGHT;
        }
        mHeaderProvider.setValue(providerName);
        updateProviderUi(providerName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    private void updateDaylightPackSummary() {
        String settingHeaderPackage = Settings.System.getString(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK);
        int valueIndex = mDaylightHeaderPack.findIndexOfValue(settingHeaderPackage);
        if (valueIndex >= 0) {
            mDaylightHeaderPack.setValueIndex(valueIndex);
            mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntry());
        } else {
            mDaylightHeaderPack.setSummary(null);
        }
    }

    private void updateProviderUi(String provider) {
        final boolean daylight = PROVIDER_DAYLIGHT.equals(provider);
        final boolean file = PROVIDER_FILE.equals(provider);

        mHeaderProvider.setSummary(mHeaderProvider.getEntry());
        mDaylightHeaderPack.setEnabled(daylight);
        mFileHeader.setEnabled(file);

        // Daylight: browse packs read-only (picking would switch the provider to static).
        // Static: pick an image, which OmniStyle applies directly.
        mHeaderBrowse.setEnabled(mBrowseAvailable && !file);
        mHeaderBrowse.setTitle(daylight
                ? R.string.qs_header_browse_title : R.string.qs_header_pick_title);
        mHeaderBrowse.setSummary(daylight
                ? R.string.qs_header_browse_summary : R.string.qs_header_pick_summary);

        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName(OMNISTYLE_PACKAGE, daylight ? OMNISTYLE_BROWSE : OMNISTYLE_PICK);
        final String pack = mDaylightHeaderPack.getValue();
        if (daylight && !TextUtils.isEmpty(pack)) {
            intent.putExtra(EXTRA_HEADER_PACK, pack);
        }
        mHeaderBrowse.setIntent(intent);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getContext().getContentResolver();
        switch (preference.getKey()) {
            case DAYLIGHT_HEADER_PACK: {
                String value = (String) newValue;
                Settings.System.putString(resolver,
                        Settings.System.STATUS_BAR_DAYLIGHT_HEADER_PACK, value);
                int index = mDaylightHeaderPack.findIndexOfValue(value);
                if (index >= 0) {
                    mDaylightHeaderPack.setSummary(mDaylightHeaderPack.getEntries()[index]);
                }
                // Keep the browse intent pointing at the newly chosen pack.
                mDaylightHeaderPack.setValue(value);
                updateProviderUi(mHeaderProvider.getValue());
                return true;
            }
            case CUSTOM_HEADER_PROVIDER: {
                String value = (String) newValue;
                Settings.System.putString(resolver,
                        Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, value);
                mHeaderProvider.setValue(value);
                updateProviderUi(value);
                return true;
            }
            default:
                return false;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mFileHeader) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"image/jpeg", "image/png", "image/gif", "image/webp"});
                startActivityForResult(intent, REQUEST_PICK_IMAGE);
            } catch (Exception e) {
                Toast.makeText(getContext(), R.string.qs_header_needs_gallery,
                        Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean isOmniStyleAvailable() {
        Intent pick = new Intent().setClassName(OMNISTYLE_PACKAGE, OMNISTYLE_PICK);
        return getContext().getPackageManager().resolveActivity(pick, 0) != null;
    }

    private void getAvailableHeaderPacks(List<String> entries, List<String> values) {
        Map<String, String> headerMap = new HashMap<>();
        PackageManager packageManager = getContext().getPackageManager();
        Intent intent = new Intent("org.omnirom.DaylightHeaderPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(intent, 0)) {
            headerMap.put(r.activityInfo.loadLabel(packageManager).toString(),
                    r.activityInfo.packageName);
        }
        intent.setAction("org.omnirom.DaylightHeaderPack1");
        for (ResolveInfo r : packageManager.queryIntentActivities(intent, 0)) {
            if (r.activityInfo.name.endsWith(".theme")) {
                continue;
            }
            headerMap.put(r.activityInfo.loadLabel(packageManager).toString(),
                    r.activityInfo.packageName + "/" + r.activityInfo.name);
        }
        List<String> labelList = new ArrayList<>(headerMap.keySet());
        Collections.sort(labelList, String.CASE_INSENSITIVE_ORDER);
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_PICK_IMAGE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;

        final Uri inUri = data.getData();
        final Context appContext = getContext().getApplicationContext();
        final ContentResolver cr = appContext.getContentResolver();

        mExecutor.execute(() -> {
            final Uri outUri = createHeaderImage(cr, inUri);
            if (outUri != null) {
                // Remove older copies only once the new one exists.
                deleteHeaderImagesExcept(cr, outUri);
                // SystemUI's FileHeaderProvider copies the image when this URI changes.
                Settings.System.putString(cr,
                        Settings.System.STATUS_BAR_FILE_HEADER_IMAGE, outUri.toString());
            } else {
                mMainHandler.post(() -> Toast.makeText(appContext,
                        R.string.qs_header_file_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void deleteHeaderImagesExcept(ContentResolver cr, Uri keep) {
        final String selection =
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.MediaColumns._ID + "!=?";
        final String[] args = new String[] {
                QSHEADER_RELATIVE_PATH + "/",
                QSHEADER_DISPLAY_NAME + "%",
                keep.getLastPathSegment()
        };
        try {
            cr.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, args);
        } catch (Exception e) {
            Log.w(TAG, "Failed to delete old header images", e);
        }
    }

    private static Uri createHeaderImage(ContentResolver cr, Uri inUri) {
        String mime = cr.getType(inUri);
        if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, QSHEADER_DISPLAY_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, QSHEADER_RELATIVE_PATH);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri outUri = null;
        try {
            outUri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (outUri == null) return null;

            try (InputStream in = cr.openInputStream(inUri);
                 OutputStream out = cr.openOutputStream(outUri, "wt")) {
                if (in == null || out == null) {
                    throw new IllegalStateException("Unable to open streams");
                }
                byte[] buf = new byte[16 * 1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(outUri, done, null, null);
            return outUri;
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy header image " + inUri, e);
            if (outUri != null) {
                try { cr.delete(outUri, null, null); } catch (Exception ignored) {}
            }
            return null;
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
                    ArrayList<SearchIndexableResource> result = new ArrayList<>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.qs_header_image_settings;
                    result.add(sir);
                    return result;
                }
            };
}
