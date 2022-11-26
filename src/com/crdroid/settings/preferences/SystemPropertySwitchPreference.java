/*
 * Copyright (C) 2022 crDroid Android Project
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

package com.crdroid.settings.preferences;

import android.content.Context;
import android.os.SystemProperties;
import android.util.AttributeSet;

import com.android.settingslib.development.SystemPropPoker;

import lineageos.preference.SelfRemovingSwitchPreference;

public class SystemPropertySwitchPreference extends SelfRemovingSwitchPreference {

    public SystemPropertySwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SystemPropertySwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SystemPropertySwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected boolean isPersisted() {
        return !SystemProperties.get(getKey(), "").isEmpty();
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        SystemProperties.set(key, Boolean.toString(value));
        SystemPropPoker.getInstance().poke();
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return SystemProperties.getBoolean(key, defaultValue);
    }
}
