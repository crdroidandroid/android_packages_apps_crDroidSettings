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
package com.crdroid.settings.fragments.about;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;

public class Donate extends SettingsPreferenceFragment {

    public static final String TAG = "Donate";

    private String KEY_DONATE_TEAM = "crdroid_donate_team";
    private String KEY_DONATE_DEV = "crdroid_donate_dev";

    private Preference mDonateTeam;
    private Preference mDonateDev;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.donate);

        mDonateTeam = findPreference(KEY_DONATE_TEAM);
        mDonateDev = findPreference(KEY_DONATE_DEV);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mDonateTeam) {
            launchUrl("https://www.paypal.me/crdroidandroid");
        } else if (preference == mDonateDev) {
            launchUrl("https://www.paypal.me/neobuddy89");
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void launchUrl(String url) {
        Uri uriUrl = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uriUrl);
        getActivity().startActivity(intent);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
