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

import android.content.ContentResolver;
import android.preference.PreferenceDataStore;
import android.os.UserHandle;
import android.provider.Settings;

public class SystemSettingsStore extends androidx.preference.PreferenceDataStore
        implements PreferenceDataStore {

    private ContentResolver mContentResolver;

    public SystemSettingsStore(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    public boolean getBoolean(String key, boolean defValue) {
        return Settings.System.getIntForUser(mContentResolver, key, defValue ? 1 : 0, UserHandle.USER_CURRENT) != 0;
    }

    public float getFloat(String key, float defValue) {
        return Settings.System.getFloatForUser(mContentResolver, key, defValue, UserHandle.USER_CURRENT);
    }

    public int getInt(String key, int defValue) {
        return Settings.System.getIntForUser(mContentResolver, key, defValue, UserHandle.USER_CURRENT);
    }

    public long getLong(String key, long defValue) {
        return Settings.System.getLongForUser(mContentResolver, key, defValue, UserHandle.USER_CURRENT);
    }

    public String getString(String key, String defValue) {
        String result = Settings.System.getString(mContentResolver, key);
        return result == null ? defValue : result;
    }

    public void putBoolean(String key, boolean value) {
        putInt(key, value ? 1 : 0);
    }

    public void putFloat(String key, float value) {
        Settings.System.putFloatForUser(mContentResolver, key, value, UserHandle.USER_CURRENT);
    }

    public void putInt(String key, int value) {
        Settings.System.putIntForUser(mContentResolver, key, value, UserHandle.USER_CURRENT);
    }

    public void putLong(String key, long value) {
        Settings.System.putLongForUser(mContentResolver, key, value, UserHandle.USER_CURRENT);
    }

    public void putString(String key, String value) {
        Settings.System.putString(mContentResolver, key, value);
    }
}
