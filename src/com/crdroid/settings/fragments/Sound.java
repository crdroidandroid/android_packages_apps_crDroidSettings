/*
 * Copyright (C) 2016-2020 crDroid Android Project
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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.sound.VolumePanel;

import java.util.List;
import java.util.ArrayList;

@SearchIndexable
public class Sound extends SettingsPreferenceFragment implements Indexable {

    public static final String TAG = "Sound";

    private static final String KEY_RINGTONE_FOCUS_MODE_V2 = "ringtone_focus_mode_v2";

    private ListPreference mRingtoneFocusMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_sound);

        final Resources res = getResources();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        mRingtoneFocusMode = (ListPreference) findPreference(KEY_RINGTONE_FOCUS_MODE_V2);

        if (!res.getBoolean(com.android.internal.R.bool.config_deviceRingtoneFocusMode)) {
            prefScreen.removePreference(mRingtoneFocusMode);
        }

    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.A2DP_SOURCE_CODEC_PRIORITY, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ADAPTIVE_PLAYBACK_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ADAPTIVE_PLAYBACK_TIMEOUT, 30, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SBC_HD_PRIORITY, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREENSHOT_SOUND, 1, UserHandle.USER_CURRENT);
        Settings.Global.putInt(resolver, Settings.Global.RINGTONE_FOCUS_MODE_V2, 1);
        VolumePanel.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.crdroid_settings_sound;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    final Resources res = context.getResources();

                    if (!res.getBoolean(com.android.internal.R.bool.config_deviceRingtoneFocusMode)) {
                        keys.add(KEY_RINGTONE_FOCUS_MODE_V2);
                    }

                    return keys;
                }

            };
}
