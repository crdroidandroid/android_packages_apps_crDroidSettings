/*
 * Copyright (C) 2016-2023 crDroid Android Project
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
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.android.internal.util.crdroid.Utils;

import com.crdroid.settings.preferences.CustomSeekBarPreference;

import java.util.List;

@SearchIndexable
public class Notifications extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "Notifications";

    private static final String ALERT_SLIDER_PREF = "alert_slider_notifications";
    private static final String LIGHT_BRIGHTNESS_CATEGORY = "light_brightness";
    private static final String BATTERY_LIGHTS_PREF = "battery_lights";
    private static final String NOTIFICATION_LIGHTS_PREF = "notification_lights";
    private static final String HEADS_UP_TIMEOUT_PREF = "heads_up_timeout";

    private Preference mAlertSlider;
    private Preference mBatLights;
    private Preference mNotLights;

    private CustomSeekBarPreference mHeadsUpTimeOut;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_notifications);

        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Context mContext = getActivity().getApplicationContext();
        final ContentResolver resolver = mContext.getContentResolver();
        final Resources res = mContext.getResources();

        mAlertSlider = (Preference) prefScreen.findPreference(ALERT_SLIDER_PREF);
        boolean mAlertSliderAvailable = res.getBoolean(
                com.android.internal.R.bool.config_hasAlertSlider);
        if (!mAlertSliderAvailable)
            prefScreen.removePreference(mAlertSlider);

        mHeadsUpTimeOut = (CustomSeekBarPreference)
                            prefScreen.findPreference(HEADS_UP_TIMEOUT_PREF);
        mHeadsUpTimeOut.setDefaultValue(getDefaultDecay(mContext));

        mBatLights = (Preference) prefScreen.findPreference(BATTERY_LIGHTS_PREF);
        boolean mBatLightsSupported = res.getInteger(
                org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities) >= 64;
        if (!mBatLightsSupported)
            prefScreen.removePreference(mBatLights);

        mNotLights = (Preference) prefScreen.findPreference(NOTIFICATION_LIGHTS_PREF);
        boolean mNotLightsSupported = res.getBoolean(
                com.android.internal.R.bool.config_intrusiveNotificationLed);
        if (!mNotLightsSupported)
            prefScreen.removePreference(mNotLights);

        if (!mBatLightsSupported && !mNotLightsSupported) {
            final PreferenceCategory lightsCategory =
                    (PreferenceCategory) prefScreen.findPreference(LIGHT_BRIGHTNESS_CATEGORY);
            prefScreen.removePreference(lightsCategory);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    private static int getDefaultDecay(Context context) {
        int defaultHeadsUpTimeOut = 5;
        Resources systemUiResources;
        try {
            systemUiResources = context.getPackageManager().getResourcesForApplication("com.android.systemui");
            defaultHeadsUpTimeOut = systemUiResources.getInteger(systemUiResources.getIdentifier(
                    "com.android.systemui:integer/heads_up_notification_decay", null, null)) / 1000;
        } catch (Exception e) {
        }
        return defaultHeadsUpTimeOut;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver,
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1);
        Settings.System.putIntForUser(resolver,
                Settings.System.LESS_BORING_HEADS_UP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NOTIFICATION_SOUND_VIB_SCREEN_ON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ALERT_SLIDER_NOTIFICATIONS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NOTIFICATION_GUTS_KILL_APP_BUTTON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RETICKER_STATUS, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RETICKER_COLORED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.HEADS_UP_TIMEOUT, getDefaultDecay(mContext), UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_notifications) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();

                    boolean mAlertSliderAvailable = res.getBoolean(
                            com.android.internal.R.bool.config_hasAlertSlider);
                    if (!mAlertSliderAvailable)
                        keys.add(ALERT_SLIDER_PREF);

                    boolean mBatLightsSupported = res.getInteger(
                            org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities) >= 64;
                    if (!mBatLightsSupported)
                        keys.add(BATTERY_LIGHTS_PREF);

                    boolean mNotLightsSupported = res.getBoolean(
                            com.android.internal.R.bool.config_intrusiveNotificationLed);
                    if (!mNotLightsSupported)
                        keys.add(NOTIFICATION_LIGHTS_PREF);

                    return keys;
                }
            };
}
