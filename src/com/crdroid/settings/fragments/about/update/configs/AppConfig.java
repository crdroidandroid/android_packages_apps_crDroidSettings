/*
 * Copyright (C) 2016-2018 crDroid Android Project
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
package com.crdroid.settings.fragments.about.update.configs;

import android.app.AlarmManager;
import android.content.Context;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.provider.Settings;
import com.crdroid.settings.R;
import com.crdroid.settings.fragments.about.update.scheduler.OTAListener;
import com.crdroid.settings.fragments.about.update.utils.OTAUtils;

import android.content.ContentResolver;

import java.text.DateFormat;
import java.util.Date;

public final class AppConfig {

    public static final long DEFAULT_INTERVAL_VALUE = AlarmManager.INTERVAL_HALF_DAY;
    public static final int DEFAULT_INTERVAL_INDEX = 4;

    private AppConfig() {
    }

    private static String buildLastCheckSummary(long time, Context context) {
        String prefix = context.getResources().getString(R.string.last_check_summary);
        if (time > 0) {
            final String date = DateFormat.getDateTimeInstance().format(new Date(time));
            return String.format(prefix, date);
        }
        return String.format(prefix, context.getResources().getString(R.string.last_check_never));
    }

    public static String getLastCheck(Context context) {
        return Settings.System.getString(context.getContentResolver(), Settings.System.OTA_LAST_CHECK);
    }

    public static String getFullLatestVersion(Context context) {
        return Settings.System.getString(context.getContentResolver(), Settings.System.OTA_LATEST_VERSION);
    }

    public static void persistLatestVersion(String latestVersion, Context context) {
        Settings.System.putString(context.getContentResolver(), Settings.System.OTA_LATEST_VERSION, latestVersion);
    }

    public static String getMaintainer(Context context) {
        return Settings.System.getString(context.getContentResolver(), Settings.System.OTA_MAINTAINER);
    }

    public static void persistMaintainer(String maintainer, Context context) {
        Settings.System.putString(context.getContentResolver(), Settings.System.OTA_MAINTAINER, maintainer);
    }

    public static void persistLastCheck(Context context) {
        String lastCheck = buildLastCheckSummary(System.currentTimeMillis(), context);
        Settings.System.putString(context.getContentResolver(), Settings.System.OTA_LAST_CHECK, lastCheck);
    }

    public static void persistUpdateIntervalIndex(int intervalIndex, Context context) {
        long intervalValue;
        switch(intervalIndex) {
            case 0:
                intervalValue = 0;
                break;
            case 1:
                intervalValue = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
                break;
            case 2:
                intervalValue = AlarmManager.INTERVAL_HALF_HOUR;
                break;
            case 3:
                intervalValue = AlarmManager.INTERVAL_HOUR;
                break;
            case 4:
                intervalValue = AlarmManager.INTERVAL_HALF_DAY;
                break;
            case 5:
                intervalValue = AlarmManager.INTERVAL_DAY;
                break;
            case 6:
                intervalValue = 60000;
                break;
            default:
                intervalValue = DEFAULT_INTERVAL_VALUE;
                break;
        }

        Settings.System.putString(context.getContentResolver(), Settings.System.OTA_UPDATE_INTERVAL, Long.toString(intervalValue));
        if (intervalValue > 0) {
            WakefulIntentService.cancelAlarms(context);
            WakefulIntentService.scheduleAlarms(new OTAListener(), context, true);
            OTAUtils.toast(R.string.autoupdate_enabled, context);
        } else {
            WakefulIntentService.cancelAlarms(context);
            OTAUtils.toast(R.string.autoupdate_disabled, context);
        }
    }

    public static int getUpdateIntervalIndex(Context context) {
        String val = Settings.System.getString(context.getContentResolver(), Settings.System.OTA_UPDATE_INTERVAL);
        int index = DEFAULT_INTERVAL_INDEX;
        long value;

        if (val == null || val == "" || val.isEmpty()) {
            persistUpdateIntervalIndex(index, context);
            return index;
        }

        try {
            value =  Long.parseLong(val);
        } catch (NumberFormatException nfe) {
            persistUpdateIntervalIndex(index, context);
            return index;
        }

        if (value == AlarmManager.INTERVAL_FIFTEEN_MINUTES) {
            index = 1;
        } else if (value == AlarmManager.INTERVAL_HALF_HOUR) {
            index = 2;
        } else if (value == AlarmManager.INTERVAL_HOUR) {
            index = 3;
        } else if (value == AlarmManager.INTERVAL_HALF_DAY) {
            index = 4;
        } else if (value == AlarmManager.INTERVAL_DAY) {
            index = 5;
        } else if (value == 60000) {
            index = 6;
        } else {
            index = 0;
        }
        return index;
    }

    public static long getUpdateIntervalTime(Context context) {
        String val = Settings.System.getString(context.getContentResolver(), Settings.System.OTA_UPDATE_INTERVAL);
        long time;

        try {
            time = Long.parseLong(val);
        } catch (NumberFormatException nfe) {
            return DEFAULT_INTERVAL_VALUE;
        }

        return time;
    }
}
