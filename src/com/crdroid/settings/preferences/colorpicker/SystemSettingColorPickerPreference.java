/*
 * Copyright (C) 2020 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.preferences.colorpicker;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.provider.Settings;

import com.crdroid.settings.preferences.SystemSettingsStore;

public class SystemSettingColorPickerPreference extends ColorPickerPreference {

    public SystemSettingColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    public SystemSettingColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }

    public SystemSettingColorPickerPreference(Context context) {
        super(context, null);
        setPreferenceDataStore(new SystemSettingsStore(context.getContentResolver()));
    }
}
