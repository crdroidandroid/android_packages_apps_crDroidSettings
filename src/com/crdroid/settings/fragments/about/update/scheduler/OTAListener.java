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
package com.crdroid.settings.fragments.about.update.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.crdroid.settings.fragments.about.update.configs.AppConfig;
import com.crdroid.settings.fragments.about.update.utils.OTAUtils;

public class OTAListener implements WakefulIntentService.AlarmListener {

    @Override
    public void scheduleAlarms(AlarmManager alarmManager, PendingIntent pendingIntent, Context context) {
        long mIntervalValue = AppConfig.getUpdateIntervalTime(context);
        if (mIntervalValue > 0) {
            OTAUtils.logInfo("crDroidOTA is scheduled for every: " + mIntervalValue + " ms");
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60000, mIntervalValue, pendingIntent);
        } else {
            OTAUtils.logInfo("crDroidOTA is disabled");
        }
    }

    @Override
    public void sendWakefulWork(Context context) {
        ConnectivityManager connMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMan != null) {
            NetworkInfo netInfo = connMan.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                Intent backgroundIntent = new Intent(context, OTAService.class);
                WakefulIntentService.sendWakefulWork(context, backgroundIntent);
            }
        }
    }

    @Override
    public long getMaxAge(Context context) {
        return (AppConfig.getUpdateIntervalTime(context) * 2);
    }
}
