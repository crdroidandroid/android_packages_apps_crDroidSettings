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
package com.crdroid.settings.fragments.ui.style.models;

import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

public class Accent {
    @NonNull
    private final String name;
    @NonNull
    private final String packageName;
    @ColorInt
    private final int color;
    private StyleStatus supportedStatus;

    public Accent(@NonNull String name, @NonNull String packageName, @ColorInt int color,
            StyleStatus supportedStatus) {
        this.name = name;
        this.packageName = packageName;
        this.color = color;
        this.supportedStatus = supportedStatus;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    @ColorInt
    public int getColor() {
        return color;
    }

    public StyleStatus getSupportedStatus() {
        return supportedStatus;
    }
}
