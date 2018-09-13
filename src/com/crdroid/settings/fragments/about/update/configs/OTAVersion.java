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

import android.content.Context;
import android.os.SystemProperties;

import com.crdroid.settings.fragments.about.update.utils.OTAUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class OTAVersion {

    private static final String UNAME_R = "uname -r";

    public static String getFullLocalVersion(Context context) {
        String source = OTAConfig.getInstance(context).getVersionSource();
        String sourceString = "";
        if (source.equalsIgnoreCase(UNAME_R)) {
            sourceString = OTAUtils.runCommand(UNAME_R);
        } else {
            sourceString = SystemProperties.get(source, "");
        }
        return sourceString;
    }

    public static boolean checkServerVersion(String serverVersion, Context context) {
        String localVersion = getFullLocalVersion(context);
        localVersion = extractVersionFrom(localVersion, context);
        serverVersion = extractVersionFrom(serverVersion, context);

        OTAUtils.logInfo("serverVersion: " + serverVersion);
        OTAUtils.logInfo("localVersion: " + localVersion);

        return compareVersion(serverVersion, localVersion, context);
    }

    public static boolean compareVersion(String serverVersion, String localVersion, Context context) {
        boolean versionIsNew = false;

        if (serverVersion == null || localVersion == null) {
            return false;
        }

        if (serverVersion.isEmpty() || localVersion.isEmpty()) {
            return false;
        }

        final SimpleDateFormat format = OTAConfig.getInstance(context).getFormat();
        if (format == null) {
            try {
                int serverNumber = Integer.parseInt(serverVersion.replaceAll("[\\D]", ""));
                int currentNumber = Integer.parseInt(localVersion.replaceAll("[\\D]", ""));
                versionIsNew = serverNumber > currentNumber;
            } catch (NumberFormatException e) {
                OTAUtils.logError(e);
            }
        } else {
            try {
                Date serverDate = format.parse(serverVersion);
                Date currentDate = format.parse(localVersion);
                versionIsNew = serverDate.after(currentDate);
            } catch (ParseException e) {
                OTAUtils.logError(e);
            }
        }

        return versionIsNew;
    }

    public static String extractVersionFrom(String str, Context context) {
        String version = "";

        if (str != null && !str.isEmpty()) {
            String delimiter = OTAConfig.getInstance(context).getDelimiter();
            int position = OTAConfig.getInstance(context).getPosition();

            if (delimiter.isEmpty()) {
                version = str;
            } else {
                if (delimiter.equals(".")) {
                    delimiter = Pattern.quote(".");
                }
                String[] tokens = str.split(delimiter);
                if (position > -1 && position < tokens.length) {
                    version = tokens[position];
                }
            }
        }

        return version;
    }
}
