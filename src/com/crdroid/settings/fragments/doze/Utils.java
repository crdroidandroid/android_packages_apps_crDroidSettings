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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import static android.provider.Settings.Secure.DOZE_ENABLED;

public final class Utils {

    public static final Uri DOZE_ENABLED_URI = Settings.Secure.getUriFor(DOZE_ENABLED);
    private static final String TAG = "DozeUtils";
    private static final boolean DEBUG = false;
    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";

    protected static void startService(Context context) {
        if (DEBUG) Log.d(TAG, "Starting service");
        context.startService(new Intent(context, DozeService.class));
    }

    protected static void stopService(Context context) {
        if (DEBUG) Log.d(TAG, "Stopping service");
        context.stopService(new Intent(context, DozeService.class));
    }

    protected static boolean isDozeEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                DOZE_ENABLED, context.getResources().getBoolean(
                com.android.internal.R.bool.config_doze_enabled_by_default) ? 1 : 0) != 0;
    }

    protected static void enableService(boolean enable, Context context) {
        if (enable) {
            startService(context);
        } else {
            stopService(context);
        }
    }

    protected static void launchDozePulse(Context context) {
        if (DEBUG) Log.d(TAG, "Launch doze pulse");
        context.sendBroadcastAsUser(new Intent(DOZE_INTENT),
                new UserHandle(UserHandle.USER_CURRENT));
    }

    protected static boolean tiltEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.DOZE_TRIGGER_TILT, 0) == 1;
    }

    protected static boolean pickUpEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.DOZE_TRIGGER_PICKUP, 0) == 1;
    }

    protected static boolean handwaveGestureEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.DOZE_TRIGGER_HANDWAVE, 0) == 1;
    }

    protected static boolean pocketGestureEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.DOZE_TRIGGER_POCKET, 0) == 1;
    }

    protected static boolean sensorsEnabled(Context context) {
        return tiltEnabled(context) || pickUpEnabled(context) || handwaveGestureEnabled(context)
                || pocketGestureEnabled(context);
    }
}
