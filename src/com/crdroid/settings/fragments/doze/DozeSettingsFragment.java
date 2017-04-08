/*
 * Copyright (C) 2017 crDroid Android Project
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

package com.crdroid.settings.fragments.doze;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
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

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.SeekBarPreference;

public class DozeSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "DozeSettingsFragment";

    private static final String KEY_DOZE = "doze_enabled";
    private static final String KEY_DOZE_FADE_IN_PICKUP = "doze_fade_in_pickup";
    private static final String KEY_DOZE_FADE_IN_DOUBLETAP = "doze_fade_in_doubletap";
    private static final String KEY_DOZE_PULSE_VISIBLE = "doze_pulse_visible";
    private static final String KEY_DOZE_PULSE_OUT = "doze_pulse_out";
    private static final String KEY_DOZE_BRIGHTNESS_LEVEL = "doze_brightness_level";

    private static final String KEY_DOZE_TRIGGER_TILT = "doze_trigger_tilt";
    private static final String KEY_DOZE_TRIGGER_PICKUP = "doze_trigger_pickup";
    private static final String KEY_DOZE_TRIGGER_HANDWAVE = "doze_trigger_handwave";
    private static final String KEY_DOZE_TRIGGER_POCKET = "doze_trigger_pocket";

    private static final String KEY_DOZE_VIBRATE_TILT = "doze_vibrate_tilt";
    private static final String KEY_DOZE_VIBRATE_PICKUP = "doze_vibrate_pickup";
    private static final String KEY_DOZE_VIBRATE_PROX = "doze_vibrate_prox";

    private SwitchPreference mDozePreference;
    private ListPreference mDozeFadeInPickup;
    private ListPreference mDozeFadeInDoubleTap;
    private ListPreference mDozePulseVisible;
    private ListPreference mDozePulseOut;

    private SwitchPreference mTiltPreference;
    private SwitchPreference mPickUpPreference;
    private SwitchPreference mHandwavePreference;
    private SwitchPreference mPocketPreference;

    private DozeBrightnessDialog mDozeBrightnessDialog;
    private Preference mDozeBrightness;

    private SeekBarPreference mVibrateTilt;
    private SeekBarPreference mVibratePickup;
    private SeekBarPreference mVibrateProximity;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.doze_settings);

        mDozePreference = (SwitchPreference) findPreference(KEY_DOZE);
        mDozePreference.setOnPreferenceChangeListener(this);

        mDozeFadeInPickup = (ListPreference) findPreference(KEY_DOZE_FADE_IN_PICKUP);
        mDozeFadeInPickup.setOnPreferenceChangeListener(this);

        mDozeFadeInDoubleTap = (ListPreference) findPreference(KEY_DOZE_FADE_IN_DOUBLETAP);
        mDozeFadeInDoubleTap.setOnPreferenceChangeListener(this);

        mDozePulseVisible = (ListPreference) findPreference(KEY_DOZE_PULSE_VISIBLE);
        mDozePulseVisible.setOnPreferenceChangeListener(this);

        mDozePulseOut = (ListPreference) findPreference(KEY_DOZE_PULSE_OUT);
        mDozePulseOut.setOnPreferenceChangeListener(this);

        mDozeBrightness = (Preference) findPreference(KEY_DOZE_BRIGHTNESS_LEVEL);

        mTiltPreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_TILT);
        mTiltPreference.setOnPreferenceChangeListener(this);

        mPickUpPreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_PICKUP);
        mPickUpPreference.setOnPreferenceChangeListener(this);

        mHandwavePreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_HANDWAVE);
        mHandwavePreference.setOnPreferenceChangeListener(this);

        mPocketPreference = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_POCKET);
        mPocketPreference.setOnPreferenceChangeListener(this);

        mVibrateTilt = (SeekBarPreference) findPreference(KEY_DOZE_VIBRATE_TILT);
        mVibrateTilt.setOnPreferenceChangeListener(this);

        mVibratePickup = (SeekBarPreference) findPreference(KEY_DOZE_VIBRATE_PICKUP);
        mVibratePickup.setOnPreferenceChangeListener(this);

        mVibrateProximity = (SeekBarPreference) findPreference(KEY_DOZE_VIBRATE_PROX);
        mVibrateProximity.setOnPreferenceChangeListener(this);

        updateState();
        updateDozeOptions();
        updateVibOptions();
    }

    private void updateDozeOptions() {
        ContentResolver resolver = getContext().getContentResolver();

        if (mDozeFadeInPickup != null) {
            final int statusDozePulseIn = Settings.System.getInt(resolver,
                    Settings.System.DOZE_FADE_IN_PICKUP, 500);
            mDozeFadeInPickup.setValue(String.valueOf(statusDozePulseIn));
            int index = mDozeFadeInPickup.findIndexOfValue(String.valueOf(statusDozePulseIn));
            if (index != -1) {
                mDozeFadeInPickup.setSummary(mDozeFadeInPickup.getEntries()[index]);
            }
        }
        if (mDozeFadeInDoubleTap != null) {
            final int statusDozePulseIn = Settings.System.getInt(resolver,
                    Settings.System.DOZE_FADE_IN_DOUBLETAP, 500);
            mDozeFadeInDoubleTap.setValue(String.valueOf(statusDozePulseIn));
            int index = mDozeFadeInDoubleTap.findIndexOfValue(String.valueOf(statusDozePulseIn));
            if (index != -1) {
                mDozeFadeInDoubleTap.setSummary(mDozeFadeInDoubleTap.getEntries()[index]);
            }
        }
        if (mDozePulseVisible != null) {
            final int statusDozePulseVisible = Settings.System.getInt(resolver,
                    Settings.System.DOZE_PULSE_DURATION_VISIBLE, 3000);
            mDozePulseVisible.setValue(String.valueOf(statusDozePulseVisible));
            int index = mDozePulseVisible.findIndexOfValue(String.valueOf(statusDozePulseVisible));
            if (index != -1) {
                mDozePulseVisible.setSummary(mDozePulseVisible.getEntries()[index]);
            }
        }
        if (mDozePulseOut != null) {
            final int statusDozePulseOut = Settings.System.getInt(resolver,
                    Settings.System.DOZE_PULSE_DURATION_OUT, 500);
            mDozePulseOut.setValue(String.valueOf(statusDozePulseOut));
            int index = mDozePulseOut.findIndexOfValue(String.valueOf(statusDozePulseOut));
            if (index != -1) {
               mDozePulseOut.setSummary(mDozePulseOut.getEntries()[index]);
            }
        }
    }

    private void updateVibOptions() {
        Context context = getContext();
        ContentResolver resolver = context.getContentResolver();
        int val;
        boolean enabled; 
        boolean dozeEnabled = Utils.isDozeEnabled(context);

        if (mVibrateTilt != null) {
            enabled = (Settings.System.getInt(resolver,
                    Settings.System.DOZE_TRIGGER_TILT, 0) != 0) &&
                    dozeEnabled;
            val = Settings.System.getInt(resolver,
                    Settings.System.DOZE_VIBRATE_TILT, 0);
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
            enabled = (Settings.System.getInt(resolver,
                    Settings.System.DOZE_TRIGGER_PICKUP, 0) != 0) &&
                    dozeEnabled;
            val = Settings.System.getInt(resolver,
                    Settings.System.DOZE_VIBRATE_PICKUP, 0);
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
            enabled = ((Settings.System.getInt(resolver,
                    Settings.System.DOZE_TRIGGER_HANDWAVE, 0) != 0) ||
                    (Settings.System.getInt(resolver,
                    Settings.System.DOZE_TRIGGER_POCKET, 0) != 0)) &&
                    dozeEnabled;
            val = Settings.System.getInt(resolver,
                    Settings.System.DOZE_VIBRATE_PROX, 0);
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
        updateDozeOptions();
        updateVibOptions();
    }

    @Override
    public void onPause() {
        super.onPause();
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
    public boolean onPreferenceTreeClick(Preference preference) {
       if (preference == mDozeBrightness) {
            showDozeBrightnessDialog();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context context = getContext();
        ContentResolver resolver = context.getContentResolver();

        if (preference == mDozePreference) {
            boolean value = (Boolean) newValue;
            Settings.Secure.putInt(resolver, Settings.Secure.DOZE_ENABLED, 
                 value ? 1 : 0);
            Utils.enableService(value, context);
            return true;
        } else if (preference == mDozeFadeInPickup) {
            int dozePulseIn = Integer.parseInt((String) newValue);
            int index = mDozeFadeInPickup.findIndexOfValue((String) newValue);
            mDozeFadeInPickup.setSummary(mDozeFadeInPickup.getEntries()[index]);
            Settings.System.putInt(resolver,
                    Settings.System.DOZE_FADE_IN_PICKUP, dozePulseIn);
            return true;
        } else if (preference == mDozeFadeInDoubleTap) {
            int dozePulseIn = Integer.parseInt((String) newValue);
            int index = mDozeFadeInDoubleTap.findIndexOfValue((String) newValue);
            mDozeFadeInDoubleTap.setSummary(mDozeFadeInDoubleTap.getEntries()[index]);
            Settings.System.putInt(resolver,
                    Settings.System.DOZE_FADE_IN_DOUBLETAP, dozePulseIn);
            return true;
        } else if (preference == mDozePulseVisible) {
            int dozePulseVisible = Integer.parseInt((String) newValue);
            int index = mDozePulseVisible.findIndexOfValue((String) newValue);
            mDozePulseVisible.setSummary(mDozePulseVisible.getEntries()[index]);
            Settings.System.putInt(resolver,
                    Settings.System.DOZE_PULSE_DURATION_VISIBLE, dozePulseVisible);
            return true;
        } else if (preference == mDozePulseOut) {
            int dozePulseOut = Integer.parseInt((String) newValue);
            int index = mDozePulseOut.findIndexOfValue((String) newValue);
            mDozePulseOut.setSummary(mDozePulseOut.getEntries()[index]);
            Settings.System.putInt(resolver,
                    Settings.System.DOZE_PULSE_DURATION_OUT, dozePulseOut);
            return true;
        } else if (preference == mTiltPreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_TRIGGER_TILT, 
                 value ? 1 : 0);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            return true;
        } else if (preference == mPickUpPreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_TRIGGER_PICKUP, 
                 value ? 1 : 0);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            return true;
        } else if (preference == mHandwavePreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_TRIGGER_HANDWAVE, 
                 value ? 1 : 0);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            return true;
        } else if (preference == mPocketPreference) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_TRIGGER_POCKET, 
                 value ? 1 : 0);
            updateVibOptions();
            Utils.enableService(Utils.isDozeEnabled(context), context);
            return true;
        } else if (preference == mVibrateTilt) {
            int val = (Integer) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_VIBRATE_TILT, val);
            updateVibOptions();
            return true;
        } else if (preference == mVibratePickup) {
            int val = (Integer) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_VIBRATE_PICKUP, val);
            updateVibOptions();
            return true;
        } else if (preference == mVibrateProximity) {
            int val = (Integer) newValue;
            Settings.System.putInt(resolver, Settings.System.DOZE_VIBRATE_PROX, val);
            updateVibOptions();
            return true;
        }

        return false;
    }

    private void showDozeBrightnessDialog() {
        if (mDozeBrightnessDialog != null && mDozeBrightnessDialog.isShowing()) {
            return;
        }

        mDozeBrightnessDialog = new DozeBrightnessDialog(getContext());
        mDozeBrightnessDialog.show();
    }

    private class DozeBrightnessDialog extends AlertDialog implements DialogInterface.OnClickListener {
        private SeekBar mBacklightBar;
        private EditText mBacklightInput;
        private int mCurrentBrightness;
        private int mMaxBrightness;

        public DozeBrightnessDialog(Context context) {
            super(context);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            final View v = getLayoutInflater().inflate(R.layout.dialog_doze_brightness, null);

            mBacklightBar = (SeekBar) v.findViewById(R.id.doze_seek);
            mBacklightInput = (EditText) v.findViewById(R.id.doze_input);

            setTitle(R.string.doze_brightness_level_title);
            setCancelable(true);
            setView(v);

            final int dozeBrightnessConfig = getResources().getInteger(
                    com.android.internal.R.integer.config_screenBrightnessDoze);
            mCurrentBrightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.DOZE_SCREEN_BRIGHTNESS, dozeBrightnessConfig);

            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mMaxBrightness = pm.getMaximumScreenBrightnessSetting();
            mBacklightBar.setMax(mMaxBrightness);
            mBacklightBar.setProgress(mCurrentBrightness);
            mBacklightInput.setText(String.valueOf(mCurrentBrightness));

            initListeners();

            setButton(DialogInterface.BUTTON_POSITIVE, getResources().getString(R.string.okay), this);
            setButton(DialogInterface.BUTTON_NEGATIVE, getResources().getString(R.string.cancel), this);

            super.onCreate(savedInstanceState);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                try {
                    int newBacklight = Integer.valueOf(mBacklightInput.getText().toString());
                    Settings.System.putInt(getContext().getContentResolver(),
                            Settings.System.DOZE_SCREEN_BRIGHTNESS, newBacklight);
                } catch (NumberFormatException e) {
                    Log.d(TAG, "NumberFormatException " + e);
                }
            }
        }

        private void initListeners() {
            mBacklightBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (seekBar.getProgress() > 0) {
                        mBacklightInput.setText(String.valueOf(seekBar.getProgress()));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            mBacklightInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
                @Override
                public void afterTextChanged(Editable s) {
                    boolean ok = false;
                    try {
                        int minValue = 1;
                        int maxValue = mMaxBrightness;
                        int newBrightness = Integer.valueOf(s.toString());

                        if (newBrightness >= minValue && newBrightness <= maxValue) {
                            ok = true;
                            mBacklightBar.setProgress(newBrightness);
                        }
                    } catch (NumberFormatException e) {
                        //ignored, ok is false ayway
                    }

                    Button okButton = mDozeBrightnessDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (okButton != null) {
                        okButton.setEnabled(ok);
                    }
                }
            });
        }
    }
}
