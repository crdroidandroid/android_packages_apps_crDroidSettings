/*
 * Copyright (C) 2018 crDroid Android Project
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
package com.crdroid.settings.fragments.ui.style.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.crdroid.settings.R;
import com.crdroid.settings.fragments.ui.style.models.Accent;
import com.crdroid.settings.fragments.ui.style.models.StyleStatus;

import lineageos.style.StyleInterface;

import java.util.ArrayList;
import java.util.List;

public final class AccentUtils {
    private static final String TAG = "AccentUtils";
    private static final String METADATA_COLOR = "lineage_berry_accent_preview";
    private static final String METADATA_SUPPORTED_STYLES = "lineage_berry_accent_supported_styles";
    private static final String METADATA_STYLE_DEFAULT = "dark|light";
    private static final String METADATA_STYLE_DARK = "dark";
    private static final String METADATA_STYLE_LIGHT = "light";
    private static final int DEFAULT_COLOR = Color.BLACK;

    private AccentUtils() {
    }

    public static List<Accent> getAccents(Context context, StyleStatus status) {
        List<Accent> accents = new ArrayList<>();

        StyleInterface styleInterface = StyleInterface.getInstance(context);
        List<String> targets = styleInterface.getTrustedAccents();
        for (String target : targets) {
            // Add default accent
            if (StyleInterface.ACCENT_DEFAULT.equals(target)) {
                accents.add(getDefaultAccent(context));
                continue;
            }

            try {
                Accent accent = getAccent(context, target);
                if (accent != null && isCompatible(status, accent)) {
                    accents.add(accent);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, e.getMessage());
            }
        }

        return accents;
    }

    @Nullable
    public static Accent getAccent(Context context, @Nullable String target)
            throws PackageManager.NameNotFoundException {
        if (TextUtils.isEmpty(target)) {
            return getDefaultAccent(context);
        }

        PackageManager pm = context.getPackageManager();
        ApplicationInfo ai = pm.getApplicationInfo(target, PackageManager.GET_META_DATA);

        String name = ai.loadLabel(pm).toString();
        int color = ai.metaData == null ? DEFAULT_COLOR :
                ai.metaData.getInt(METADATA_COLOR, DEFAULT_COLOR);

        String supportedStyles = ai.metaData == null ? METADATA_STYLE_DEFAULT :
                ai.metaData.getString(METADATA_SUPPORTED_STYLES, METADATA_STYLE_DEFAULT);
        boolean supportsDark = supportedStyles.contains(METADATA_STYLE_DARK);
        boolean supportsLight = supportedStyles.contains(METADATA_STYLE_LIGHT);
        StyleStatus status = (supportsLight && supportsDark) ? StyleStatus.DYNAMIC :
                supportsLight ? StyleStatus.LIGHT_ONLY : StyleStatus.DARK_ONLY;

        return new Accent(name, ai.packageName, color, status);
    }

    @NonNull
    private static Accent getDefaultAccent(Context context) {
        return new Accent(context.getString(R.string.style_accent_default_name),
                StyleInterface.ACCENT_DEFAULT, Color.parseColor("#5e97f6"), StyleStatus.DYNAMIC);
    }

    public static boolean isCompatible(StyleStatus currentStatus, Accent accent) {
        StyleStatus accentStatus = accent.getSupportedStatus();
        return accentStatus == StyleStatus.DYNAMIC || currentStatus == accentStatus;
    }
}
