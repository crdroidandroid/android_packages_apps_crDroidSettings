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
package com.crdroid.settings.fragments.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.provider.Settings;
import android.support.v7.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;

public class AmbientTicker extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private ListPreference mAmbientTicker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ambient_ticker);
        final ContentResolver resolver = getActivity().getContentResolver();

        mFooterPreferenceMixin.createFooterPreference().setTitle(R.string.ambient_ticker_footer);

        mAmbientTicker = (ListPreference) findPreference("force_ambient_for_media");
        int mode = Settings.System.getIntForUser(resolver,
                Settings.System.FORCE_AMBIENT_FOR_MEDIA, 0, UserHandle.USER_CURRENT);
        mAmbientTicker.setValue(Integer.toString(mode));
        mAmbientTicker.setSummary(mAmbientTicker.getEntry());
        mAmbientTicker.setOnPreferenceChangeListener(this);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mAmbientTicker) {
            int value = Integer.valueOf((String) newValue);
            int index = mAmbientTicker.findIndexOfValue((String) newValue);
            mAmbientTicker.setSummary(
                    mAmbientTicker.getEntries()[index]);
            Settings.System.putIntForUser(resolver, Settings.System.FORCE_AMBIENT_FOR_MEDIA,
                    value, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.FORCE_AMBIENT_FOR_MEDIA, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
