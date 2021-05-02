/*
 * Copyright (C) 2016-2021 crDroid Android Project
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
package com.crdroid.settings.fragments.notifications;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Menu;
import android.widget.EditText;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.CustomSeekBarPreference;
import com.crdroid.settings.preferences.SystemSettingListPreference;

public class Ticker extends SettingsPreferenceFragment {

    private static final String STATUSBAR_TICKER_FOOTER = "statusbar_ticker_footer";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.statusbar_ticker);

        findPreference(STATUSBAR_TICKER_FOOTER).setTitle(R.string.ticker_screen_footer);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_TICKER, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_TICKER_ANIMATION_MODE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_TICKER_TICK_DURATION, 3000, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
