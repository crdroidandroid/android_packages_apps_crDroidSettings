/*
 * Copyright (C) 2016 crDroid Android
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
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.R;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;

public class About extends SettingsPreferenceFragment {

    public static final String TAG = "About";

    private static final String KEY_CRDROID_SHARE = "share";

    Preference mSourceUrl;
    Preference mGoogleUrl;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.crdroid_about);

        mSourceUrl = findPreference("crdroid_source");
        mGoogleUrl = findPreference("crdroid_google_plus");
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mSourceUrl) {
            launchUrl("https://github.com/crdroidandroid");
        } else if (preference == mGoogleUrl) {
            launchUrl("https://plus.google.com/u/0/communities/118297646046960923906");
        } else if (preference.getKey().equals(KEY_CRDROID_SHARE)) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, String.format(
                getActivity().getString(R.string.share_message), Build.MODEL));
        startActivity(Intent.createChooser(intent, getActivity().getString(R.string.share_chooser_title)));
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchUrl(String url) {
        Uri uriUrl = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uriUrl);
        getActivity().startActivity(intent);
    }
}
