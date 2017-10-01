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
package com.crdroid.settings.fragments.about.ota.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;

import com.crdroid.settings.fragments.about.ota.configs.OTAConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

public final class OTAUtils {

    private static final String TAG = "crDroidOTA";
    private static final boolean DEBUG = true;

    private OTAUtils() {
    }

    public static void logError(Exception e) {
        if (DEBUG) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void logInfo(String message) {
        if (DEBUG) {
            Log.i(TAG, message);
        }
    }

    public static void toast(int messageId, Context context) {
        if (context != null) {
            Toast.makeText(context, context.getResources().getString(messageId),
                    Toast.LENGTH_LONG).show();
        }
    }

    public static String getDeviceName(Context context) {
        String propName = OTAConfig.getInstance(context).getDeviceSource();
        return SystemProperties.get(propName, "");
    }

    public static String runCommand(String command) {
        try {
            StringBuffer output = new StringBuffer();
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            reader.close();
            p.waitFor();
            return output.toString();
        } catch (InterruptedException | IOException e) {
            logError(e);
        }
        return "";
    }

    public static InputStream downloadURL(String link) throws IOException {
        URL url = new URL(link);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.connect();
        logInfo("downloadStatus: " + conn.getResponseCode());
        return conn.getInputStream();
    }

    public static void launchUrl(String url, Context context) {
        if (!url.isEmpty() && context != null) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }
}
