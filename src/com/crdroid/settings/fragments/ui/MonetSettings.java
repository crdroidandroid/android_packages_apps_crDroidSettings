/*
 * Copyright (C) 2021-2023 crDroid Android Project
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

package com.crdroid.settings.fragments.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.List;

import lineageos.providers.LineageSettings;

public class MonetSettings extends SettingsPreferenceFragment {

    final static String TAG = "MonetSettings";

    private static final String PREF_CHROMA_FACTOR ="monet_engine_chroma_factor";
    private static final String PREF_LUMINANCE_FACTOR ="monet_engine_luminance_factor";
    private static final String PREF_TINT_BACKGROUND ="monet_engine_tint_background";
    private static final String PREF_CUSTOM_COLOR ="monet_engine_custom_color";
    private static final String PREF_COLOR_OVERRIDE ="monet_engine_color_override";
    private static final String PREF_CUSTOM_BGCOLOR ="monet_engine_custom_bgcolor";
    private static final String PREF_BGCOLOR_OVERRIDE ="monet_engine_bgcolor_override";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.monet_engine);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                PREF_CHROMA_FACTOR, 100, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                PREF_LUMINANCE_FACTOR, 100, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                PREF_TINT_BACKGROUND, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                PREF_CUSTOM_COLOR, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                PREF_COLOR_OVERRIDE, 0xFF1b6ef3, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                PREF_CUSTOM_BGCOLOR, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                PREF_BGCOLOR_OVERRIDE, 0xFF1b6ef3, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.BERRY_BLACK_THEME, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
