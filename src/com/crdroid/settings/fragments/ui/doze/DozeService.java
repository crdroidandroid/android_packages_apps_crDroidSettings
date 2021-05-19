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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class DozeService extends Service {
    private static final String TAG = "crDroidDozeService";
    private static final boolean DEBUG = false;

    private TiltSensor mTiltSensor;
    private PickupSensor mPickupSensor;
    private ProximitySensor mProximitySensor;

    private boolean mTiltSensorAvailable;
    private boolean mPickupSensorAvailable;
    private boolean mProximitySensorAvailable;

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                onDisplayOn();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                onDisplayOff();
            }
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        mTiltSensorAvailable = Utils.getTiltSensor(this);
        mPickupSensorAvailable = Utils.getPickupSensor(this);
        mProximitySensorAvailable = Utils.getProximitySensor(this);

        if (!mTiltSensorAvailable && !mPickupSensorAvailable && !mProximitySensorAvailable) return;

        if (mTiltSensorAvailable) mTiltSensor = new TiltSensor(this);
        if (mPickupSensorAvailable) mPickupSensor = new PickupSensor(this);
        if (mProximitySensorAvailable) mProximitySensor = new ProximitySensor(this);

        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        super.onDestroy();

        if (!mTiltSensorAvailable && !mPickupSensorAvailable && !mProximitySensorAvailable) return;

        this.unregisterReceiver(mScreenStateReceiver);
        if (mTiltSensorAvailable) {
            mTiltSensor.disable();
        }
        if (mPickupSensorAvailable) {
            mPickupSensor.disable();
        }
        if (mProximitySensorAvailable) {
            mProximitySensor.disable();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onDisplayOn() {
        if (DEBUG) Log.d(TAG, "Display on");
        if (mTiltSensorAvailable && Utils.tiltEnabled(this)) {
            mTiltSensor.disable();
        }
        if (mPickupSensorAvailable && (Utils.pickUpEnabled(this) || Utils.pickUpSetToWake(this))) {
            mPickupSensor.disable();
        }
        if (mProximitySensorAvailable && (Utils.handwaveGestureEnabled(this) ||
                Utils.pocketGestureEnabled(this))) {
            mProximitySensor.disable();
        }
    }

    private void onDisplayOff() {
        if (DEBUG) Log.d(TAG, "Display off");
        if (mTiltSensorAvailable && Utils.tiltEnabled(this)) {
            mTiltSensor.enable();
        }
        if (mPickupSensorAvailable && (Utils.pickUpEnabled(this) || Utils.pickUpSetToWake(this))) {
            mPickupSensor.enable();
        }
        if (mProximitySensorAvailable && (Utils.handwaveGestureEnabled(this) ||
                Utils.pocketGestureEnabled(this))) {
            mProximitySensor.enable();
        }
    }
}
