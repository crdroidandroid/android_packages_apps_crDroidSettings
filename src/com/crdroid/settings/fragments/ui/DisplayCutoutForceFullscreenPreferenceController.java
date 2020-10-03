/*
 * Copyright (C) 2019 The PixelExperience Project
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

import android.content.Context;
import android.provider.Settings;
import android.os.Handler;
import android.os.UserHandle;

import com.android.settings.core.BasePreferenceController;

import com.android.internal.util.crdroid.cutout.CutoutFullscreenController;

public class DisplayCutoutForceFullscreenPreferenceController extends BasePreferenceController {

    private static final String PREF_KEY = "display_cutout_force_fullscreen_settings";
    private CutoutFullscreenController mCutoutForceFullscreenSettings;

    public DisplayCutoutForceFullscreenPreferenceController(Context context) {
        super(context, PREF_KEY);
        mCutoutForceFullscreenSettings = new CutoutFullscreenController(new Handler(), context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mCutoutForceFullscreenSettings.isSupported() ?
                AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
