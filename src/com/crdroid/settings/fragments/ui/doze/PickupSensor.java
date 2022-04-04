/*
 * Copyright (C) 2017-2021 crDroid Android Project
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

package com.crdroid.settings.fragments.ui.doze;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.settings.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PickupSensor implements SensorEventListener {
    private static final boolean DEBUG = false;
    private static final String TAG = "PickupSensor";

    private SensorManager mSensorManager;
    private Sensor mSensorPickup;
    private Context mContext;
    private TelephonyManager telephonyManager;
    private ExecutorService mExecutorService;
    private PowerManager mPowerManager;
    private WakeLock mWakeLock;

    private boolean mIsCustomPickupSensor;
    private int mMinPulseIntervalMs;
    private int mWakelockTimeoutMs;

    private float[] mGravity;
    private float mAccelLast;
    private float mAccelCurrent;
    private long mEntryTimestamp;

    private Vibrator mVibrator;

    public PickupSensor(Context context) {
        mContext = context;
        final Resources res = context.getResources();
        mSensorManager = mContext.getSystemService(SensorManager.class);
        final String pickup_sensor = res.getString(R.string.pickup_sensor);
        mIsCustomPickupSensor = pickup_sensor != null && !pickup_sensor.isEmpty();
        if (mIsCustomPickupSensor) {
            for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_ALL)) {
                if (pickup_sensor.equals(sensor.getStringType())) {
                    mSensorPickup = sensor;
                    break;
                }
            }
        } else {
            mSensorPickup = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        mMinPulseIntervalMs =
            res.getInteger(R.integer.config_dozePulsePickup_MinPulseIntervalMs);
        mWakelockTimeoutMs =
            res.getInteger(R.integer.config_dozePulsePickup_WakelockTimeoutMs);
        if (DEBUG) {
            Log.d(TAG, "Pickup sensor: " + mSensorPickup.getStringType());
            Log.d(TAG, "MinPulseIntervalMs: " + String.valueOf(mMinPulseIntervalMs));
            Log.d(TAG, "WakelockTimeoutMs: " + String.valueOf(mWakelockTimeoutMs));
        }
        telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mExecutorService = Executors.newSingleThreadExecutor();
        mAccelLast = SensorManager.GRAVITY_EARTH;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }
    }

    private Future<?> submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (DEBUG) Log.d(TAG, "Got sensor event: " + event.values[0]);

        long delta = SystemClock.elapsedRealtime() - mEntryTimestamp;
        if (delta < mMinPulseIntervalMs) {
            return;
        } else {
            mEntryTimestamp = SystemClock.elapsedRealtime();
        }

        try {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mGravity = event.values.clone();

                // Movement detection
                float x = mGravity[0];
                float y = mGravity[1];
                float z = mGravity[2];

                mAccelLast = mAccelCurrent;
                mAccelCurrent = (float) Math.sqrt(x * x + y * y + z * z);
                float accDelta = Math.abs(mAccelCurrent - mAccelLast);
                if (accDelta >= 0.1 && accDelta <= 1.5) {
                    launchWakeOrPulse();
                }
            } else {
                if (event.values[0] == 1) {
                    launchWakeOrPulse();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void launchWakeOrPulse() {
        boolean isRaiseToWake = Utils.isRaiseToWakeEnabled(mContext);
        if (isRaiseToWake) {
            mWakeLock.acquire(mWakelockTimeoutMs);
            mPowerManager.wakeUp(SystemClock.uptimeMillis(),
                PowerManager.WAKE_REASON_GESTURE, TAG);
        } else {
            Utils.launchDozePulse(mContext);
            doHapticFeedback();
        }
    }

    protected boolean isCallActive(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager.getMode() == AudioManager.MODE_IN_CALL) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    protected void enable() {
        if (DEBUG) Log.d(TAG, "Enabling");
        submit(() -> {
            mEntryTimestamp = SystemClock.elapsedRealtime();
            mSensorManager.registerListener(this, mSensorPickup,
                    mIsCustomPickupSensor ? SensorManager.SENSOR_DELAY_NORMAL
                    : SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        });
    }

    protected void disable() {
        if (DEBUG) Log.d(TAG, "Disabling");
        submit(() -> {
            mSensorManager.unregisterListener(this, mSensorPickup);
        });
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
        int val = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.DOZE_GESTURE_VIBRATE, 0, UserHandle.USER_CURRENT);
        if (val > 0) {
            mVibrator.vibrate(VibrationEffect.createOneShot(val,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
