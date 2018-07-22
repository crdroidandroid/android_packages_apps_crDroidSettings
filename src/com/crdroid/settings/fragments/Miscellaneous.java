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
package com.crdroid.settings.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v14.preference.SwitchPreference;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.development.DevelopmentSettings;
import com.android.settings.SettingsPreferenceFragment;

import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.crdroid.settings.fragments.misc.AlarmBlocker;
import com.crdroid.settings.fragments.misc.ScreenStateToggles;
import com.crdroid.settings.fragments.misc.SmartPixels;
import com.crdroid.settings.fragments.misc.WakeLockBlocker;
import com.crdroid.settings.fragments.misc.ScreenshotEditPackageListAdapter;
import com.crdroid.settings.fragments.misc.ScreenshotEditPackageListAdapter.PackageItem;
import com.crdroid.settings.R;

public class Miscellaneous extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String TAG = "Miscellaneous";

    private static final String KEY_LOCK_CLOCK = "lock_clock";
    private static final String KEY_LOCK_CLOCK_PACKAGE_NAME = "com.cyanogenmod.lockclock";
    private static final String SHOW_CPU_INFO_KEY = "show_cpu_info";
    private static final String MEDIA_SCANNER_ON_BOOT = "media_scanner_on_boot";
    private static final String SCREENSHOT_EDIT_APP = "screenshot_edit_app";

    private static final int DIALOG_SCREENSHOT_EDIT_APP = 1;

    private SwitchPreference mShowCpuInfo;
    private ListPreference mMSOB;
    private Preference mScreenshotEditAppPref;
    private ScreenshotEditPackageListAdapter mPackageAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.crdroid_settings_misc);

        ContentResolver resolver = mContext.getContentResolver();

        //Screenshot edit app
        mPackageAdapter = new ScreenshotEditPackageListAdapter(getActivity());
        mScreenshotEditAppPref = findPreference(SCREENSHOT_EDIT_APP);
        mScreenshotEditAppPref.setOnPreferenceClickListener(this);

        // mLockClock
        if (!DevelopmentSettings.isPackageInstalled(mContext, KEY_LOCK_CLOCK_PACKAGE_NAME)) {
            getPreferenceScreen().removePreference(findPreference(KEY_LOCK_CLOCK));
        }

        mShowCpuInfo = (SwitchPreference) findPreference(SHOW_CPU_INFO_KEY);
        mShowCpuInfo.setChecked(Settings.Global.getInt(resolver,
                Settings.Global.SHOW_CPU_OVERLAY, 0) == 1);
        mShowCpuInfo.setOnPreferenceChangeListener(this);

        // MediaScanner behavior on boot
        mMSOB = (ListPreference) findPreference(MEDIA_SCANNER_ON_BOOT);
        int mMSOBValue = Settings.System.getIntForUser(resolver,
                Settings.System.MEDIA_SCANNER_ON_BOOT, 0, UserHandle.USER_CURRENT);
        mMSOB.setValue(String.valueOf(mMSOBValue));
        mMSOB.setSummary(mMSOB.getEntry());
        mMSOB.setOnPreferenceChangeListener(this);

       refreshScreenshotEditApp();
    }

    private void refreshScreenshotEditApp() {
        String currentScreenshotEditApp =  Settings.System.getStringForUser(getActivity().getContentResolver(),
                Settings.System.SCREENSHOT_EDIT_USER_APP, UserHandle.USER_CURRENT);
        if (currentScreenshotEditApp != null && currentScreenshotEditApp != "") {
            mScreenshotEditAppPref.setSummary(currentScreenshotEditApp);
        } else {
            mScreenshotEditAppPref.setSummary(R.string.screenshot_edit_app_summary);
        }
}

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DIALOG_SCREENSHOT_EDIT_APP: {
                Dialog dialog;
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                final ListView list = new ListView(getActivity());
                list.setAdapter(mPackageAdapter);
                alertDialog.setTitle(R.string.profile_choose_app);
                alertDialog.setView(list);
                dialog = alertDialog.create();
                list.setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        // Add empty application definition, the user will be able to edit it later
                        PackageItem info = (PackageItem) parent.getItemAtPosition(position);
                        Settings.System.putStringForUser(getActivity().getContentResolver(),
                                Settings.System.SCREENSHOT_EDIT_USER_APP, info.packageName, UserHandle.USER_CURRENT);
                        refreshScreenshotEditApp();
                        dialog.cancel();
                    }
                });
                return dialog;
            }
         }
        return super.onCreateDialog(dialogId);
    }

    private static void writeCpuInfoOptions(Context mContext, boolean value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SHOW_CPU_OVERLAY, value ? 1 : 0);
        Intent service = (new Intent())
                .setClassName("com.android.systemui", "com.android.systemui.CPUInfoService");
        if (value) {
            mContext.startService(service);
        } else {
            mContext.stopService(service);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        Context mContext = getActivity().getApplicationContext();
        if (preference == mShowCpuInfo) {
            writeCpuInfoOptions(mContext, (Boolean) newValue);
            return true;
        } else if (preference == mMSOB) {
            int value = Integer.parseInt(((String) newValue).toString());
            Settings.System.putIntForUser(resolver,
                    Settings.System.MEDIA_SCANNER_ON_BOOT, value, UserHandle.USER_CURRENT);
            mMSOB.setValue(String.valueOf(value));
            mMSOB.setSummary(mMSOB.getEntries()[value]);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        AlarmBlocker.reset(mContext);
        ScreenStateToggles.reset(mContext);
        SmartPixels.reset(mContext);
        WakeLockBlocker.reset(mContext);
        writeCpuInfoOptions(mContext, false);
        Settings.System.putIntForUser(resolver,
                Settings.System.MEDIA_SCANNER_ON_BOOT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.AUTO_USB_MODE_CHOOSER, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREENSHOT_TYPE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.VIBRATION_ON_CHARGE_STATE_CHANGED, 0, UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(resolver,
                Settings.System.SCREENSHOT_EDIT_USER_APP, "", UserHandle.USER_CURRENT);
        Settings.Global.putInt(resolver,
                Settings.Global.TOAST_ICON, 1);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        switch (dialogId) {
            case DIALOG_SCREENSHOT_EDIT_APP:
                return MetricsEvent.CRDROID_SETTINGS;
            default:
                return 0;
        }
     }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        // Don't show the dialog if there are no available editor apps
        if (preference == mScreenshotEditAppPref && mPackageAdapter.getCount() > 0) {
            showDialog(DIALOG_SCREENSHOT_EDIT_APP);
        } else {
            Toast.makeText(getActivity(), getActivity().getString(R.string.screenshot_edit_app_no_editor),
                    Toast.LENGTH_LONG).show();
        }
        return true;
    }

}
