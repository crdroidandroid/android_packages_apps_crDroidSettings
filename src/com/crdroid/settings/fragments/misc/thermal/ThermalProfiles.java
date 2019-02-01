/*
 * Copyright (c) 2019 The PixelExperience Project
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

package com.crdroid.settings.fragments.misc.thermal;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.lineageos.internal.util.FileUtils;

public class ThermalProfiles {
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_DIALER = 8;
    public static final int MODE_GAME = 9;
    public static final int MODE_PERFORMANCE = 10;
    public static final int MODE_BROWSER = 11;
    public static final int MODE_CAMERA = 12;
    public static final int MODE_PUBG = 13;
    public static final int MODE_VIDEO = 14;

    public static final int supportedProfiles[] = {MODE_DEFAULT, MODE_DIALER, MODE_GAME,
        MODE_PERFORMANCE, MODE_BROWSER, MODE_CAMERA, MODE_PUBG, MODE_VIDEO};

    private static final String TAG = "ThermalController:ThermalProfiles";

    private static String CONTROL_PATH = "/sys/class/thermal/thermal_message/sconfig";

    public static int getDefaultProfileId(String packageName) {
        switch (packageName) {
            case "com.android.dialer":
            case "com.google.android.dialer":
                return MODE_DIALER;
            case "com.antutu.ABenchMark":
            case "com.futuremark.dmandroid.application":
            case "com.primatelabs.geekbench":
                return MODE_PERFORMANCE;
            case "com.tencent.ig":
            case "com.dts.freefireth":
            case "com.epicgames.fortnite":
            case "com.gameloft.android.ANMP.GloftA8HM":
            case "com.gameloft.android.ANMP.GloftA9HM":
                return MODE_GAME;
            case "com.google.android.youtube":
            case "com.netflix.mediaclient":
            case "com.google.android.videos":
            case "com.amazon.avod.thirdpartyclient":
            case "com.google.android.apps.youtube.kids":
                return MODE_VIDEO;
            case "org.lineageos.jelly":
            case "org.mozilla.firefox":
            case "com.android.chrome":
            case "com.UCMobile.intl":
                return MODE_BROWSER;
            case "org.codeaurora.snapcam":
            case "com.android.camera":
            case "com.android.gallery3d":
            case "com.google.android.apps.photos":
            case "com.google.android.GoogleCamera":
            case "org.lineageos.snap":
                return MODE_CAMERA;
            default:
                return MODE_DEFAULT;
        }
    }

    public static boolean isAllowed(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_allowActivePackageBroadcast);
    }

    public static boolean isAvailable() {
        return FileUtils.isFileWritable(CONTROL_PATH);
    }

    public static void writeProfile(int profileId) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONTROL_PATH))) {
            writer.write(Integer.toString(profileId));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write profile", e);
        }
    }
}
