/*
 * Copyright (C) 2016-2019 crDroid Android Project
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
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.notifications.Ticker;

import java.util.List;
import java.util.ArrayList;

public class Notifications extends SettingsPreferenceFragment implements Indexable {

    public static final String TAG = "Notifications";

    private static final String BATTERY_LIGHTS_PREF = "battery_lights";
    private static final String NOTIFICATION_LIGHTS_PREF = "notification_lights";
    private static final String FLASHLIGHT_ON_CALL = "flashlight_on_call";

    private Preference mBatLights;
    private Preference mNotLights;
    private ListPreference mFlashlightOnCall;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.crdroid_settings_notifications);

        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources res = getResources();

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

        mFlashlightOnCall = (ListPreference) findPreference(FLASHLIGHT_ON_CALL);
        if (!Utils.deviceSupportsFlashLight(mContext))
            prefScreen.removePreference(mFlashlightOnCall);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.FLASHLIGHT_ON_CALL, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FORCE_EXPANDED_NOTIFICATIONS, 0, UserHandle.USER_CURRENT);
        Settings.Global.putInt(resolver,
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1);
        Settings.System.putIntForUser(resolver,
                Settings.System.HEADS_UP_NOTIFICATION_SNOOZE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.HEADS_UP_TIMEOUT, 5000, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LESS_BORING_HEADS_UP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD, 30000, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NOTIFICATION_SOUND_VIB_SCREEN_ON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.VIBRATE_ON_CONNECT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.VIBRATE_ON_CALLWAITING, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.VIBRATE_ON_DISCONNECT, 0, UserHandle.USER_CURRENT);
        Ticker.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();
                    SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.crdroid_settings_notifications;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();

                    boolean mBatLightsSupported = res.getInteger(
                            org.lineageos.platform.internal.R.integer.config_deviceLightCapabilities) >= 64;
                    if (!mBatLightsSupported)
                        keys.add(BATTERY_LIGHTS_PREF);

                    boolean mNotLightsSupported = res.getBoolean(
                            com.android.internal.R.bool.config_intrusiveNotificationLed);
                    if (!mNotLightsSupported)
                        keys.add(NOTIFICATION_LIGHTS_PREF);

                    if (!Utils.deviceSupportsFlashLight(context))
                        keys.add(FLASHLIGHT_ON_CALL);

                    return keys;
                }
            };
}
