/*
 * Copyright (C) 2016-2017 crDroid Android Project
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
package com.crdroid.settings.fragments.about.ota.tasks;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import com.crdroid.settings.fragments.about.OTA;
import com.crdroid.settings.fragments.about.ota.configs.AppConfig;
import com.crdroid.settings.fragments.about.ota.configs.LinkConfig;
import com.crdroid.settings.fragments.about.ota.configs.OTAConfig;
import com.crdroid.settings.fragments.about.ota.configs.OTAVersion;
import com.crdroid.settings.fragments.about.ota.dialogs.WaitDialogHandler;
import com.crdroid.settings.fragments.about.ota.tasks.NotificationHelper;
import com.crdroid.settings.fragments.about.ota.utils.OTAUtils;
import com.crdroid.settings.fragments.about.ota.xml.OTADevice;
import com.crdroid.settings.fragments.about.ota.xml.OTAParser;
import com.crdroid.settings.R;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

public class CheckUpdateTask extends AsyncTask<Context, Void, OTADevice> {

    private static CheckUpdateTask mInstance = null;
    private final Handler mHandler = new WaitDialogHandler();
    private Context mContext;
    private boolean mIsBackgroundThread;

    private static final int NOTI_PRIMARY = 1100;
    private NotificationHelper noti;

    private CheckUpdateTask(boolean isBackgroundThread) {
        this.mIsBackgroundThread = isBackgroundThread;
    }

    public static CheckUpdateTask getInstance(boolean isBackgroundThread) {
        if (mInstance == null) {
            mInstance = new CheckUpdateTask(isBackgroundThread);
        }
        return mInstance;
    }

    @Override
    protected OTADevice doInBackground(Context... params) {
        mContext = params[0];

        if (!isConnectivityAvailable(mContext)) {
            return null;
        }

        showWaitDialog();

        OTADevice device = null;
        String deviceName = OTAUtils.getDeviceName(mContext);
        OTAUtils.logInfo("deviceName: " + deviceName);
        if (!deviceName.isEmpty()) {
            try {
                String otaUrl = OTAConfig.getInstance(mContext).getOtaUrl();
                InputStream is = OTAUtils.downloadURL(otaUrl);
                if (is != null) {
                    final String releaseType = OTAConfig.getInstance(mContext).getReleaseType();
                    device = OTAParser.getInstance().parse(is, deviceName, releaseType);
                    is.close();
                }
            } catch (IOException | XmlPullParserException e) {
                OTAUtils.logError(e);
            }
        }

        return device;
    }

    @Override
    protected void onPostExecute(OTADevice device) {
        super.onPostExecute(device);

        if (device == null) {
            showToast(R.string.check_update_failed);
        } else {
            String latestVersion = device.getLatestVersion();
            boolean updateAvailable = OTAVersion.checkServerVersion(latestVersion, mContext);
            if (updateAvailable) {
                showNotification(mContext);
            } else {
                showToast(R.string.no_update_available);
            }
            AppConfig.persistLatestVersion(latestVersion, mContext);
            LinkConfig.persistLinks(device.getLinks(), mContext);
        }

        AppConfig.persistLastCheck(mContext);
        OTA.updatePreferences(mContext);

        hideWaitDialog();

        mInstance = null;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        mInstance = null;
    }

    private void showWaitDialog() {
        if (!mIsBackgroundThread) {
            Message msg = mHandler.obtainMessage(WaitDialogHandler.MSG_SHOW_DIALOG);
            msg.obj = mContext;
            mHandler.sendMessage(msg);
        }
    }

    private void hideWaitDialog() {
        if (!mIsBackgroundThread) {
            Message msg = mHandler.obtainMessage(WaitDialogHandler.MSG_CLOSE_DIALOG);
            mHandler.sendMessage(msg);
        }
    }

    private void showToast(int messageId) {
        if (!mIsBackgroundThread) {
            OTAUtils.toast(messageId, mContext);
        }
    }

    private static boolean isConnectivityAvailable(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    private void showNotification(Context context) {
        Notification.Builder nb = null;
        String title = context.getString(R.string.notification_title);
        String body = context.getString(R.string.notification_message);
        if (noti == null) {
            noti = new NotificationHelper(context);
        }
        nb = noti.getNotification(context, title, body);

        if (nb != null) {
            noti.notify(NOTI_PRIMARY, nb);
        }
    }
}
