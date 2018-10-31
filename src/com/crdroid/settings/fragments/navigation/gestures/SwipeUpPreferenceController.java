/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.gestures;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.SwitchPreference;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.android.internal.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.widget.VideoPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnCreate;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;
import com.android.settingslib.core.lifecycle.events.OnSaveInstanceState;

public class SwipeUpPreferenceController extends BasePreferenceController
        implements PreferenceControllerMixin, Preference.OnPreferenceChangeListener,
        LifecycleObserver, OnResume, OnPause, OnCreate, OnSaveInstanceState {

    private final int ON = 1;
    private final int OFF = 0;

    private static final String ACTION_QUICKSTEP = "android.intent.action.QUICKSTEP_SERVICE";
    private static final String PREF_KEY_VIDEO = "gesture_swipe_up_video";
    @VisibleForTesting
    static final String KEY_VIDEO_PAUSED = "key_video_paused";

    private static final String PREF_SWIPE_UP = "gesture_swipe_up";
    private static final String PREF_FULL_MODE = "full_gesture_mode";
    private static final String PREF_FULL_MODE_DT2S = "full_gesture_mode_dt2s";

    private VideoPreference mVideoPreference;
    @VisibleForTesting
    boolean mVideoPaused;

    private SwitchPreference mSwipeUpPreference;
    private SwitchPreference mFullGestureModePreference;
    private SwitchPreference mFullGestureModeDt2sPreference;

    public SwipeUpPreferenceController(Context context, String key) {
        super(context, key);
    }

    static boolean isGestureAvailable(Context context) {
        if (!context.getResources().getBoolean(R.bool.config_swipe_up_gesture_setting_available)) {
            return false;
        }

        final ComponentName recentsComponentName = ComponentName.unflattenFromString(
                context.getString(R.string.config_recentsComponentName));
        final Intent quickStepIntent = new Intent(ACTION_QUICKSTEP)
                .setPackage(recentsComponentName.getPackageName());
        if (context.getPackageManager().resolveService(quickStepIntent,
                PackageManager.MATCH_SYSTEM_ONLY) == null) {
            return false;
        }
        return true;
    }

    @Override
    public int getAvailabilityStatus() {
        return isGestureAvailable(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    /*@Override
    public boolean isSliceable() {
        return TextUtils.equals(getPreferenceKey(), "gesture_swipe_up");
    }*/

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (isAvailable()) {
            mVideoPreference = (VideoPreference) screen.findPreference(PREF_KEY_VIDEO);

            mSwipeUpPreference = (SwitchPreference) screen.findPreference(PREF_SWIPE_UP);
            mFullGestureModePreference = (SwitchPreference) screen.findPreference(PREF_FULL_MODE);
            mFullGestureModeDt2sPreference = (SwitchPreference) screen.findPreference(PREF_FULL_MODE_DT2S);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mVideoPaused = savedInstanceState.getBoolean(KEY_VIDEO_PAUSED, false);
        }
    }
     @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_VIDEO_PAUSED, mVideoPaused);
    }

    @Override
    public void onPause() {
        if (mVideoPreference != null) {
            mVideoPaused = mVideoPreference.isVideoPaused();
            mVideoPreference.onViewInvisible();
        }
    }
     @Override
    public void onResume() {
        if (mVideoPreference != null) {
            mVideoPreference.onViewVisible(mVideoPaused);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference != null && preference instanceof SwitchPreference) {
            SwitchPreference pref = (SwitchPreference) preference;
            if (TextUtils.equals(pref.getKey(), PREF_SWIPE_UP)) {
                boolean enabled = Settings.Secure.getInt(
                        mContext.getContentResolver(),
                        Settings.Secure.SWIPE_UP_TO_SWITCH_APPS_ENABLED, defaultSwipeUpValue()) == ON;
                pref.setChecked(enabled);
            } else if (TextUtils.equals(pref.getKey(), PREF_FULL_MODE)) {
                pref.setChecked(fullGestureModeEnabled());
                pref.setEnabled(swipeUpenabled());
            } else if (TextUtils.equals(pref.getKey(), PREF_FULL_MODE_DT2S)) {
                boolean enabled = Settings.System.getInt(
                        mContext.getContentResolver(),
                        Settings.System.FULL_GESTURE_NAVBAR_DT2S, OFF) == ON;
                pref.setChecked(enabled);
                pref.setEnabled(swipeUpenabled() && fullGestureModeEnabled());
            }
        }
    }

    private boolean swipeUpenabled() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.SWIPE_UP_TO_SWITCH_APPS_ENABLED, defaultSwipeUpValue()) == ON;
    }

    private int defaultSwipeUpValue() {
        return mContext.getResources()
                .getBoolean(R.bool.config_swipe_up_gesture_default) ? ON : OFF;
    }

    private boolean fullGestureModeEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FULL_GESTURE_NAVBAR, OFF) == ON;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SwitchPreference pref = (SwitchPreference) preference;
        if (TextUtils.equals(pref.getKey(), PREF_SWIPE_UP)) {
            boolean enabled = ((Boolean) newValue).booleanValue();
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.SWIPE_UP_TO_SWITCH_APPS_ENABLED, enabled ? ON : OFF);
            pref.setChecked(enabled);
            if (mFullGestureModePreference != null) {
                mFullGestureModePreference.setEnabled(enabled);
            }
            if (mFullGestureModeDt2sPreference != null) {
                mFullGestureModeDt2sPreference.setEnabled(enabled);
            }
        } else if (TextUtils.equals(pref.getKey(), PREF_FULL_MODE)) {
            boolean enabled = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.FULL_GESTURE_NAVBAR, enabled ? ON : OFF);
            pref.setChecked(enabled);
            if (mFullGestureModeDt2sPreference != null) {
                mFullGestureModeDt2sPreference.setEnabled(enabled);
            }
        } else if (TextUtils.equals(pref.getKey(), PREF_FULL_MODE_DT2S)) {
            boolean enabled = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.FULL_GESTURE_NAVBAR_DT2S, enabled ? ON : OFF);
            pref.setChecked(enabled);
        }

         return true;
    }
}
