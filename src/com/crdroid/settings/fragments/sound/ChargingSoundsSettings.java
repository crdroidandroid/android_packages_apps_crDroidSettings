/*
 * Copyright (C) 2016 The CyanogenMod Project
 *               2017,2019-2020 The LineageOS Project
 *               2021 crDroid Android Project
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

package com.crdroid.settings.fragments.sound;

import android.app.Activity;
import android.database.Cursor;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;

import lineageos.providers.LineageSettings;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class ChargingSoundsSettings extends SettingsPreferenceFragment {

    private static final String KEY_POWER_NOTIFICATIONS_VIBRATE = "power_notifications_vibrate";
    private static final String KEY_CHARGING_SOUNDS_RINGTONE = "charging_sounds_ringtone";
    private static final String KEY_BATTERY_FULLY_CHARGED_SOUND_RINGTONE = "battery_fully_charged_sound_ringtone";
    private static final String KEY_BATTERY_FULLY_CHARGED_VIBRATE = "battery_fully_charged_vibrate";

    // Used for power notification uri string if set to silent
    private static final String RINGTONE_SILENT_URI_STRING = "silent";

    private static final String DEFAULT_POWER_SOUND =
            "/system/product/media/audio/ui/ChargingStarted.ogg";

    // Request code for charging notification ringtone picker
    private static final int REQUEST_CODE_CHARGING_NOTIFICATIONS_RINGTONE = 1;

    // Request code for battery fully charged ringtone picker
    private static final int REQUEST_CODE_BATTERY_FULLY_CHARGED_RINGTONE = 2;

    private Preference mChargingSoundsRingtone;
    private Preference mBatteryFullyChargedSoundRingtone;

    private Uri mDefaultPowerSoundUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.charging_sounds_settings);

        Vibrator vibrator = getActivity().getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            removePreference(KEY_POWER_NOTIFICATIONS_VIBRATE);
            removePreference(KEY_BATTERY_FULLY_CHARGED_VIBRATE);
        }

        mChargingSoundsRingtone = findPreference(KEY_CHARGING_SOUNDS_RINGTONE);

        mBatteryFullyChargedSoundRingtone = findPreference(KEY_BATTERY_FULLY_CHARGED_SOUND_RINGTONE);
        String curBatteryFullyChargedTone = LineageSettings.Global.getString(getContentResolver(),
                LineageSettings.Global.BATTERY_FULLY_CHARGED_RINGTONE);
        if (curBatteryFullyChargedTone == null) {
            updateBatteryFullyChargedRingtone(Settings.System.DEFAULT_NOTIFICATION_URI.toString(), true);
        } else {
            updateBatteryFullyChargedRingtone(curBatteryFullyChargedTone, false);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String curTone = LineageSettings.Global.getString(getContentResolver(),
                LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE);

        // Convert default sound file path to a media uri so that we can
        // set a proper default for the ringtone picker.
        mDefaultPowerSoundUri = audioFileToUri(getContext(), DEFAULT_POWER_SOUND);

        updateChargingRingtone(curTone);
    }

    private Uri audioFileToUri(Context context, String audioFile) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Media._ID },
                MediaStore.Audio.Media.DATA + "=? ",
                new String[] { audioFile }, null);
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
        cursor.close();
        return Uri.withAppendedPath(MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                Integer.toString(id));
    }

    private void updateChargingRingtone(String toneUriString) {
        final String toneTitle;

        if ((toneUriString == null || toneUriString.equals(DEFAULT_POWER_SOUND))
                && mDefaultPowerSoundUri != null) {
            toneUriString = mDefaultPowerSoundUri.toString();
        }

        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            final Ringtone ringtone = RingtoneManager.getRingtone(getActivity(),
                    Uri.parse(toneUriString));
            if (ringtone != null) {
                toneTitle = ringtone.getTitle(getActivity());
            } else {
                // Unlikely to ever happen, but is possible if the ringtone
                // previously chosen is removed during an upgrade
                toneTitle = "";
                toneUriString = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
            }
        } else {
            // Silent
            toneTitle = getString(R.string.charging_sounds_ringtone_silent);
            toneUriString = RINGTONE_SILENT_URI_STRING;
        }

        mChargingSoundsRingtone.setSummary(toneTitle);
        LineageSettings.Global.putString(getContentResolver(),
                LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE, toneUriString);
    }

    private void updateBatteryFullyChargedRingtone(String toneUriString, boolean persist) {
        final String toneName;

        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            final Ringtone ringtone = RingtoneManager.getRingtone(getActivity(),
                    Uri.parse(toneUriString));
            if (ringtone != null) {
                toneName = ringtone.getTitle(getActivity());
            } else {
                // Unlikely to ever happen, but is possible if the ringtone
                // previously chosen is removed during an upgrade
                toneName = "";
                toneUriString = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
                persist = true;
            }
        } else {
            // Silent
            toneName = getString(R.string.battery_fully_charged_sound_ringtone_silent);
            toneUriString = RINGTONE_SILENT_URI_STRING;
        }

        mBatteryFullyChargedSoundRingtone.setSummary(toneName);
        if (persist) {
            LineageSettings.Global.putString(getContentResolver(),
                    LineageSettings.Global.BATTERY_FULLY_CHARGED_RINGTONE, toneUriString);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mChargingSoundsRingtone) {
            launchNotificationSoundPicker(REQUEST_CODE_CHARGING_NOTIFICATIONS_RINGTONE,
                    LineageSettings.Global.getString(getContentResolver(),
                    LineageSettings.Global.POWER_NOTIFICATIONS_RINGTONE));
        } else if (preference == mBatteryFullyChargedSoundRingtone) {
            launchBatteryFullyChargedSoundPicker(REQUEST_CODE_BATTERY_FULLY_CHARGED_RINGTONE,
                    LineageSettings.Global.getString(getContentResolver(),
                    LineageSettings.Global.BATTERY_FULLY_CHARGED_RINGTONE));
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchNotificationSoundPicker(int requestCode, String toneUriString) {
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.charging_sounds_ringtone_title));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, mDefaultPowerSoundUri);
        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            Uri uri = Uri.parse(toneUriString);
            if (uri != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri);
            }
        }
        startActivityForResult(intent, requestCode);
    }

    private void launchBatteryFullyChargedSoundPicker(int requestCode, String toneUriString) {
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.battery_fully_charged_sound_ringtone_title));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                Settings.System.DEFAULT_NOTIFICATION_URI);
        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            Uri uri = Uri.parse(toneUriString);
            if (uri != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri);
            }
        }
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CHARGING_NOTIFICATIONS_RINGTONE
                && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            updateChargingRingtone(uri != null ? uri.toString() : RINGTONE_SILENT_URI_STRING);
        } else if (requestCode == REQUEST_CODE_BATTERY_FULLY_CHARGED_RINGTONE
                && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            updateBatteryFullyChargedRingtone(uri != null ? uri.toString() : null, true);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
