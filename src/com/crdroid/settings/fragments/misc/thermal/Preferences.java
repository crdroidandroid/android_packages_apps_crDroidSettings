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
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.stream.IntStream;

import static android.content.Context.MODE_PRIVATE;

public class Preferences {
    private static String PREFS_NAME = "ThermalController_Settings";

    public static void setProfileId(Context context, String packageName, int profileId){
        if (TextUtils.isEmpty(packageName) || !IntStream.of(ThermalProfiles.supportedProfiles).anyMatch(x -> x == profileId)){
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(packageName, profileId);
        editor.commit();
    }

    public static int getProfileId(Context context, String packageName){
        if (TextUtils.isEmpty(packageName)){
            return ThermalProfiles.MODE_DEFAULT;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(packageName, ThermalProfiles.getDefaultProfileId(packageName));
    }
}
