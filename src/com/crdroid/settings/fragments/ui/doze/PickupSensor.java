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

package com.crdroid.settings.fragments.ui.doze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PickupSensor implements SensorEventListener {
    private static final boolean DEBUG = false;
    private static final String TAG = "TiltSensor";

    private static final int BATCH_LATENCY_IN_MS = 100;
    private static final int MIN_PULSE_INTERVAL_MS = 2500;

    private SensorManager mSensorManager;
    private Sensor mSensorPickup;
    private Context mContext;
    private TelephonyManager telephonyManager;
    private ExecutorService mExecutorService;

    private float[] mGravity;
    private float mAccelLast;
    private float mAccelCurrent;
    private long mEntryTimestamp;

    private Vibrator mVibrator;

    public PickupSensor(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mSensorPickup = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
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
        if (delta < MIN_PULSE_INTERVAL_MS) {
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
                    Utils.launchDozePulse(mContext);
                    doHapticFeedback();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            mSensorManager.registerListener(this, mSensorPickup,
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
            mEntryTimestamp = SystemClock.elapsedRealtime();
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
            mVibrator.vibrate(val);
        }
    }
}
