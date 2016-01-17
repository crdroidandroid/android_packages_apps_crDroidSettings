/*
 * Copyright (C) 2016-2021 crDroid Android Project
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

package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.buttons.PowerMenuSettings;
import com.crdroid.settings.preferences.LineageSystemSettingSeekBarPreference;
import com.crdroid.settings.utils.DeviceUtils;

import java.util.List;

import lineageos.providers.LineageSettings;

@SearchIndexable
public class Buttons extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "Buttons";

    private static final String KEY_POWER_MENU = "power_menu";
    private static final String KEY_TORCH_LONG_PRESS_POWER_GESTURE =
            "torch_long_press_power_gesture";
    private static final String KEY_TORCH_LONG_PRESS_POWER_TIMEOUT =
            "torch_long_press_power_timeout";

    private static final String CATEGORY_POWER = "power_key";

    private SwitchPreference mTorchLongPressPowerGesture;
    private LineageSystemSettingSeekBarPreference mTorchLongPressPowerTimeout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_button);

        final PreferenceScreen prefScreen = getPreferenceScreen();

        // Long press power while display is off to activate torchlight
        mTorchLongPressPowerGesture = findPreference(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
        mTorchLongPressPowerTimeout = findPreference(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);

        final boolean hasPowerKey = DeviceUtils.hasPowerKey();

        final PreferenceCategory powerCategory = prefScreen.findPreference(CATEGORY_POWER);

        if (hasPowerKey) {
            if (!DeviceUtils.deviceSupportsFlashLight(getActivity())) {
                powerCategory.removePreference(mTorchLongPressPowerGesture);
                powerCategory.removePreference(mTorchLongPressPowerTimeout);
            }
        } else {
            prefScreen.removePreference(powerCategory);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.TORCH_LONG_PRESS_POWER_GESTURE, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.TORCH_LONG_PRESS_POWER_TIMEOUT, 0, UserHandle.USER_CURRENT);
        PowerMenuSettings.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_button) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    if (!DeviceUtils.hasPowerKey()) {
                        keys.add(KEY_POWER_MENU);
                        keys.add(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                        keys.add(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                    } else {
                        if (!DeviceUtils.deviceSupportsFlashLight(context)) {
                            keys.add(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                            keys.add(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                        }
                    }

                    return keys;
                }
            };
}
