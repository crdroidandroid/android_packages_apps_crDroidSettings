/*
 * Copyright (C) 2026 crDroid Android Project
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

package com.crdroid.settings.preferences.colorpicker;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

final class ColorPickerUtils {

    static final int OPAQUE_MASK = 0xFF000000;

    private ColorPickerUtils() {
    }

    @ColorInt
    static int applyAlphaPolicy(@ColorInt int color, boolean alphaEnabled) {
        return alphaEnabled ? color : (color | OPAQUE_MASK);
    }

    @ColorInt
    static int resolveThemeColor(@NonNull Context context, @AttrRes int attr,
            @ColorInt int fallback) {
        final TypedValue tv = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, tv, true)) {
            return fallback;
        }
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        if (tv.resourceId != 0) {
            try {
                return context.getColor(tv.resourceId);
            } catch (Resources.NotFoundException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    @ColorInt
    static int contrastingColor(@ColorInt int background) {
        final int opaque = background | OPAQUE_MASK;
        return ColorUtils.calculateLuminance(opaque) > 0.5d ? 0xFF000000 : 0xFFFFFFFF;
    }

    static float dp(@NonNull Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
