/*
 * Copyright (C) 2016-2021 crDroid Android Project
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

package com.crdroid.settings.fragments.buttons;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.preference.SwitchPreference;
import androidx.preference.Preference;

import com.android.internal.widget.LockPatternUtils;

import org.lineageos.internal.util.PowerMenuConstants;

import java.util.ArrayList;
import java.util.List;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.SettingsPreferenceFragment;

import com.android.settings.R;

import lineageos.app.LineageGlobalActions;
import lineageos.providers.LineageSettings;

import static org.lineageos.internal.util.PowerMenuConstants.*;

public class PowerMenuActions extends SettingsPreferenceFragment {
    final static String TAG = "PowerMenuActions";

    private SwitchPreference mScreenshotPref;
    private SwitchPreference mAirplanePref;
    private SwitchPreference mUsersPref;
    private SwitchPreference mLockDownPref;
    private SwitchPreference mEmergencyPref;

    private LineageGlobalActions mLineageGlobalActions;

    Context mContext;
    private LockPatternUtils mLockPatternUtils;
    private UserManager mUserManager;
    private List<String> mLocalUserConfig = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.power_menu_settings);
        mContext = getActivity().getApplicationContext();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mUserManager = UserManager.get(mContext);
        mLineageGlobalActions = LineageGlobalActions.getInstance(mContext);

        for (String action : PowerMenuConstants.getAllActions()) {
            if (action.equals(GLOBAL_ACTION_KEY_SCREENSHOT)) {
                mScreenshotPref = (SwitchPreference) findPreference(GLOBAL_ACTION_KEY_SCREENSHOT);
            } else if (action.equals(GLOBAL_ACTION_KEY_AIRPLANE)) {
                mAirplanePref = (SwitchPreference) findPreference(GLOBAL_ACTION_KEY_AIRPLANE);
            } else if (action.equals(GLOBAL_ACTION_KEY_USERS)) {
                mUsersPref = (SwitchPreference) findPreference(GLOBAL_ACTION_KEY_USERS);
            } else if (action.equals(GLOBAL_ACTION_KEY_LOCKDOWN)) {
                mLockDownPref = (SwitchPreference) findPreference(GLOBAL_ACTION_KEY_LOCKDOWN);
            } else if (action.equals(GLOBAL_ACTION_KEY_EMERGENCY)) {
                mEmergencyPref = (SwitchPreference) findPreference(GLOBAL_ACTION_KEY_EMERGENCY);
            }
        }

        mLocalUserConfig = mLineageGlobalActions.getLocalUserConfig();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mScreenshotPref != null) {
            mScreenshotPref.setChecked(mLineageGlobalActions.userConfigContains(
                    GLOBAL_ACTION_KEY_SCREENSHOT));
        }

        if (mAirplanePref != null) {
            mAirplanePref.setChecked(mLineageGlobalActions.userConfigContains(
                    GLOBAL_ACTION_KEY_AIRPLANE));
        }

        if (mUsersPref != null) {
            if (!UserHandle.MU_ENABLED || !UserManager.supportsMultipleUsers()) {
                getPreferenceScreen().removePreference(findPreference(GLOBAL_ACTION_KEY_USERS));
                mUsersPref = null;
            } else {
                List<UserInfo> users = mUserManager.getUsers();
                boolean enabled = (users.size() > 1);
                mUsersPref.setChecked(mLineageGlobalActions.userConfigContains(
                        GLOBAL_ACTION_KEY_USERS) && enabled);
                mUsersPref.setEnabled(enabled);
            }
        }

        if (mEmergencyPref != null) {
            mEmergencyPref.setChecked(mLineageGlobalActions.userConfigContains(
                    GLOBAL_ACTION_KEY_EMERGENCY));
        }

        updatePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        boolean value;

        if (preference == mScreenshotPref) {
            value = mScreenshotPref.isChecked();
            mLineageGlobalActions.updateUserConfig(value, GLOBAL_ACTION_KEY_SCREENSHOT);

        } else if (preference == mAirplanePref) {
            value = mAirplanePref.isChecked();
            mLineageGlobalActions.updateUserConfig(value, GLOBAL_ACTION_KEY_AIRPLANE);

        } else if (preference == mUsersPref) {
            value = mUsersPref.isChecked();
            mLineageGlobalActions.updateUserConfig(value, GLOBAL_ACTION_KEY_USERS);

        } else if (preference == mLockDownPref) {
            value = mLockDownPref.isChecked();
            mLineageGlobalActions.updateUserConfig(value, GLOBAL_ACTION_KEY_LOCKDOWN);
            Settings.Secure.putIntForUser(getContentResolver(),
                    Settings.Secure.LOCKDOWN_IN_POWER_MENU, value ? 1 : 0, UserHandle.USER_CURRENT);

        } else if (preference == mEmergencyPref) {
            value = mEmergencyPref.isChecked();
            mLineageGlobalActions.updateUserConfig(value, GLOBAL_ACTION_KEY_EMERGENCY);

        } else {
            return super.onPreferenceTreeClick(preference);
        }
        return true;
    }

    private void updatePreferences() {
        boolean isKeyguardSecure = mLockPatternUtils.isSecure(UserHandle.myUserId());
        boolean lockdown = Settings.Secure.getIntForUser(
                getContentResolver(), Settings.Secure.LOCKDOWN_IN_POWER_MENU, 0,
                UserHandle.USER_CURRENT) == 1;
        if (mLockDownPref != null) {
            mLockDownPref.setEnabled(isKeyguardSecure);
            if (isKeyguardSecure) {
                mLockDownPref.setChecked(lockdown);
                mLockDownPref.setSummary(null);
            } else {
                mLockDownPref.setChecked(false);
                mLockDownPref.setSummary(R.string.power_menu_lockdown_unavailable);
            }
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.ADVANCED_REBOOT, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.ADVANCED_REBOOT_SECURED, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.NOTIFICATION_BG_ALPHA, 255, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
