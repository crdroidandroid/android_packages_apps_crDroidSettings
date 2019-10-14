/*
 * Copyright (C) 2019 crDroid Android Project
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

package com.crdroid.settings.fragments.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.View;

import androidx.preference.SwitchPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.crdroid.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.android.internal.logging.nano.MetricsProto;

public class ImeSettings extends SettingsPreferenceFragment {

    private static final String TAG = "ImeSettings";
    private static final String IME_SWITCHER_KEY = "status_bar_ime_switcher";

    private SwitchPreference mShowOngoingImeSwitcherForPhones;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.ime_settings);
        ContentResolver resolver = getActivity().getContentResolver();

        mShowOngoingImeSwitcherForPhones = (SwitchPreference) findPreference(IME_SWITCHER_KEY);

        boolean defaultImeSwitcherForPhones = getResources().getBoolean(
                com.android.internal.R.bool.show_ongoing_ime_switcher);
        boolean enabled = Settings.System.getIntForUser(
            resolver, Settings.System.STATUS_BAR_IME_SWITCHER,
            defaultImeSwitcherForPhones ? 1 : 0, UserHandle.USER_CURRENT) == 1;
        mShowOngoingImeSwitcherForPhones.setChecked(enabled);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        boolean defaultImeSwitcherForPhones = mContext.getResources().getBoolean(
                com.android.internal.R.bool.show_ongoing_ime_switcher);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_IME_SWITCHER, defaultImeSwitcherForPhones ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ENABLE_FULLSCREEN_KEYBOARD, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FORMAL_TEXT_INPUT, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
