/*
 * Copyright (C) 2017-2019 crDroid Android Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.ui.AmbientTicker;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

public class DozeFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "DozeFragment";

    private static final String KEY_DOZE = "doze_enabled";
    private static final String KEY_DOZE_ALWAYS_ON = "doze_always_on";

    private static final String KEY_DOZE_TRIGGER_TILT = "doze_trigger_tilt";
    private static final String KEY_DOZE_TRIGGER_PICKUP = "doze_trigger_pickup";
    private static final String KEY_DOZE_TRIGGER_HANDWAVE = "doze_trigger_handwave";
    private static final String KEY_DOZE_TRIGGER_POCKET = "doze_trigger_pocket";

    private static final String KEY_DOZE_VIBRATE_TILT = "doze_vibrate_tilt";
    private static final String KEY_DOZE_VIBRATE_PICKUP = "doze_vibrate_pickup";
    private static final String KEY_DOZE_VIBRATE_PROX = "doze_vibrate_prox";

    private SwitchPreference mDozePreference;

    private SwitchPreference mTiltPreference;
    private SwitchPreference mPickUpPreference;
    private SwitchPreference mHandwavePreference;
    private SwitchPreference mPocketPreference;

    private CustomSeekBarPreference mVibrateTilt;
    private CustomSeekBarPreference mVibratePickup;
    private CustomSeekBarPreference mVibrateProximity;

    private SharedPreferences mPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.doze_settings);

        PreferenceCategory proximitySensorCategory =
                (PreferenceCategory) getPreferenceScreen().findPreference(Utils.CATEG_PROX_SENSOR);

        mDozePreference = (SwitchPreference) findPreference(KEY_DOZE);
        mDozePreference.setOnPreferenceChangeListener(this);

        mTiltPreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_TILT);
        mTiltPreference.setOnPreferenceChangeListener(this);

        mPickUpPreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_PICKUP);
        mPickUpPreference.setOnPreferenceChangeListener(this);

        mHandwavePreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_HANDWAVE);
        mHandwavePreference.setOnPreferenceChangeListener(this);

        mPocketPreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_POCKET);
        mPocketPreference.setOnPreferenceChangeListener(this);

        mVibrateTilt = (CustomSeekBarPreference) findPreference(KEY_DOZE_VIBRATE_TILT);
        mVibrateTilt.setOnPreferenceChangeListener(this);

        mVibratePickup = (CustomSeekBarPreference) findPreference(KEY_DOZE_VIBRATE_PICKUP);
        mVibratePickup.setOnPreferenceChangeListener(this);

        mVibrateProximity = (CustomSeekBarPreference) findPreference(KEY_DOZE_VIBRATE_PROX);
        mVibrateProximity.setOnPreferenceChangeListener(this);

        updateState();
        updateVibOptions();

        // Hide proximity sensor related features if the device doesn't support them
        if (!Utils.getProxCheckBeforePulse(getActivity())) {
            getPreferenceScreen().removePreference(proximitySensorCategory);
        }

        // Hides always on toggle if device doesn't support it (based on config_dozeAlwaysOnDisplayAvailable overlay)
        boolean mAlwaysOnAvailable = getResources().getBoolean(com.android.internal.R.bool.config_dozeAlwaysOnDisplayAvailable);
        if (!mAlwaysOnAvailable) {
            getPreferenceScreen().removePreference(findPreference(KEY_DOZE_ALWAYS_ON));
        }
    }

    private void updateVibOptions() {
        Context context = getContext();
        ContentResolver resolver = context.getContentResolver();
        int val;
        boolean enabled; 
        boolean dozeEnabled = Utils.isDozeEnabled(context);

        if (mVibrateTilt != null) {
            enabled = (Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_TRIGGER_TILT, 0, UserHandle.USER_CURRENT) != 0) &&
                    dozeEnabled;
            val = Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_VIBRATE_TILT, 0, UserHandle.USER_CURRENT);
            mVibrateTilt.setEnabled(enabled);
            mVibrateTilt.setValue(val);
            if (enabled && val > 0) {
                mVibrateTilt.setSummary(context.getResources().getString(R.string.enabled));
            } else if (enabled) {
                mVibrateTilt.setSummary(context.getResources().getString(R.string.disabled));
            } else {
                mVibrateTilt.setSummary(context.getResources().getString(R.string.doze_vibrate_summary));
            }
        }

        if (mVibratePickup != null) {
            enabled = (Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_TRIGGER_PICKUP, 0, UserHandle.USER_CURRENT) != 0) &&
                    dozeEnabled;
            val = Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_VIBRATE_PICKUP, 0, UserHandle.USER_CURRENT);
            mVibratePickup.setEnabled(enabled);
            mVibratePickup.setValue(val);
            if (enabled && val > 0) {
                mVibratePickup.setSummary(context.getResources().getString(R.string.enabled));
            } else if (enabled) {
                mVibratePickup.setSummary(context.getResources().getString(R.string.disabled));
            } else {
                mVibratePickup.setSummary(context.getResources().getString(R.string.doze_vibrate_summary));
            }
        }

        if (mVibrateProximity != null) {
            enabled = ((Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_TRIGGER_HANDWAVE, 0, UserHandle.USER_CURRENT) != 0) ||
                    (Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_TRIGGER_POCKET, 0, UserHandle.USER_CURRENT) != 0)) &&
                    dozeEnabled;
            val = Settings.System.getIntForUser(resolver,
                    Settings.System.DOZE_VIBRATE_PROX, 0, UserHandle.USER_CURRENT);
            mVibrateProximity.setEnabled(enabled);
            mVibrateProximity.setValue(val);
            if (enabled && val > 0) {
                mVibrateProximity.setSummary(context.getResources().getString(R.string.enabled));
            } else if (enabled) {
                mVibrateProximity.setSummary(context.getResources().getString(R.string.disabled));
            } else {
                mVibrateProximity.setSummary(context.getResources().getString(R.string.doze_vibrate_summary));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
        updateVibOptions();
    }

    private void updateState() {
        Context context = getContext();

        if (mDozePreference != null) {
            mDozePreference.setChecked(Utils.isDozeEnabled(context));
        }
        if (mTiltPreference != null) {
            mTiltPreference.setChecked(Utils.tiltEnabled(context));
        }
        if (mPickUpPreference != null) {
            mPickUpPreference.setChecked(Utils.pickUpEnabled(context));
        }
        if (mHandwavePreference != null) {
            mHandwavePreference.setChecked(Utils.handwaveGestureEnabled(context));
        }
        if (mPocketPreference != null) {
            mPocketPreference.setChecked(Utils.pocketGestureEnabled(context));
        }

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context context = getContext();
        ContentResolver resolver = context.getContentResolver();

        if (preference == mDozePreference) {
            boolean value = (Boolean) newValue;
            Settings.Secure.putIntForUser(resolver, Settings.Secure.DOZE_ENABLED, 
                 value ? 1 : 0, UserHandle.USER_CURRENT);
            Utils.enableService(value, context);
            return true;
        } else if (preference == mTiltPreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_TRIGGER_TILT, 
                 value ? 1 : 0, UserHandle.USER_CURRENT);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            if (newValue != null)
                sensorWarning(context);
            return true;
        } else if (preference == mPickUpPreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_TRIGGER_PICKUP, 
                 value ? 1 : 0, UserHandle.USER_CURRENT);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            if (newValue != null)
                sensorWarning(context);
            return true;
        } else if (preference == mHandwavePreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_TRIGGER_HANDWAVE, 
                 value ? 1 : 0, UserHandle.USER_CURRENT);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            if (newValue != null)
                sensorWarning(context);
            return true;
        } else if (preference == mPocketPreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_TRIGGER_POCKET, 
                 value ? 1 : 0, UserHandle.USER_CURRENT);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            if (newValue != null)
                sensorWarning(context);
            return true;
        } else if (preference == mVibrateTilt) {
            int val = (Integer) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_VIBRATE_TILT, val, UserHandle.USER_CURRENT);
            updateVibOptions();
            return true;
        } else if (preference == mVibratePickup) {
            int val = (Integer) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_VIBRATE_PICKUP, val, UserHandle.USER_CURRENT);
            updateVibOptions();
            return true;
        } else if (preference == mVibrateProximity) {
            int val = (Integer) newValue;
            Settings.System.putIntForUser(resolver, Settings.System.DOZE_VIBRATE_PROX, val, UserHandle.USER_CURRENT);
            updateVibOptions();
            return true;
        }

        return false;
    }

    private void sensorWarning(Context context) {
        mPreferences = context.getSharedPreferences("dozesettingsfragment", Activity.MODE_PRIVATE);
        if (mPreferences.getBoolean("sensor_warning_shown", false)) {
            return;
        }
        context.getSharedPreferences("dozesettingsfragment", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("sensor_warning_shown", true)
                .commit();

        new AlertDialog.Builder(context)
                .setTitle(getResources().getString(R.string.sensor_warning_title))
                .setMessage(getResources().getString(R.string.sensor_warning_message))
                .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                }).show();
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.DOZE_ENABLED, mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_doze_enabled_by_default) ? 1 : 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.DOZE_ALWAYS_ON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_TRIGGER_TILT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_TRIGGER_PICKUP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_TRIGGER_HANDWAVE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_TRIGGER_POCKET, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_VIBRATE_TILT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_VIBRATE_PICKUP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOZE_VIBRATE_PROX, 0, UserHandle.USER_CURRENT);
        AmbientTicker.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
