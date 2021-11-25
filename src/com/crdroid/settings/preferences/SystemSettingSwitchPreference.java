/*
 * Copyright (C) 2016-2018 crDroid Android Project
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
import android.provider.Settings;
import android.os.UserHandle;
import android.util.AttributeSet;

import lineageos.preference.SelfRemovingSwitchPreference;

public class SystemSettingSwitchPreference extends SelfRemovingSwitchPreference {

    public SystemSettingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SystemSettingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SystemSettingSwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected boolean isPersisted() {
        return Settings.System.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        Settings.System.putIntForUser(getContext().getContentResolver(), key, value ? 1 : 0, UserHandle.USER_CURRENT);
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return Settings.System.getIntForUser(getContext().getContentResolver(),
                key, defaultValue ? 1 : 0, UserHandle.USER_CURRENT) != 0;
    }
}
