/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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
package com.crdroid.settings.fragments.statusbar;

import android.content.ContentResolver;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.SystemSettingSeekBarPreference;
import com.crdroid.settings.preferences.colorpicker.SystemSettingColorPickerPreference;

public class ClockChipSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ClockChipSettings";

    private static final String KEY_CHIP_STYLE = "statusbar_clock_chip";
    private static final String KEY_GRADIENT_START_COLOR  = "statusbar_clock_chip_gradient_start_color";
    private static final String KEY_GRADIENT_END_COLOR = "statusbar_clock_chip_gradient_end_color";
    private static final String KEY_GRADIENT_ANGLE = "statusbar_clock_chip_gradient_angle";

    private static final int CHIP_STYLE_CUSTOM_GRADIENT = 13;

    private static final int DEFAULT_GRADIENT_START_COLOR = 0xFFFF6B6B;
    private static final int DEFAULT_GRADIENT_END_COLOR = 0xFF4ECDC4;

    private SystemSettingColorPickerPreference mGradientStartColor;
    private SystemSettingColorPickerPreference mGradientEndColor;
    private SystemSettingSeekBarPreference mGradientAngle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.clock_chip_settings);

        final ContentResolver resolver = requireActivity().getContentResolver();

        Preference chipStyle = findPreference(KEY_CHIP_STYLE);
        chipStyle.setOnPreferenceChangeListener(this);

        mGradientStartColor =
                (SystemSettingColorPickerPreference) findPreference(KEY_GRADIENT_START_COLOR);

        mGradientEndColor =
                (SystemSettingColorPickerPreference) findPreference(KEY_GRADIENT_END_COLOR);

        mGradientAngle =
                (SystemSettingSeekBarPreference) findPreference(KEY_GRADIENT_ANGLE);

        int currentStyle = Settings.System.getIntForUser(
                resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP,
                0,
                UserHandle.USER_CURRENT);
        updateGradientPrefsVisibility(currentStyle);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = requireActivity().getContentResolver();
        final String key = preference.getKey();

        if (KEY_CHIP_STYLE.equals(key)) {
            int style = (int) newValue;
            updateGradientPrefsVisibility(style);
            return true;
        }

        return false;
    }

    private void updateGradientPrefsVisibility(int chipStyle) {
        boolean isGradient = (chipStyle == CHIP_STYLE_CUSTOM_GRADIENT);
        if (mGradientStartColor != null) mGradientStartColor.setVisible(isGradient);
        if (mGradientEndColor != null) mGradientEndColor.setVisible(isGradient);
        if (mGradientAngle != null) mGradientAngle.setVisible(isGradient);
    }

    public static void reset(Context context) {
        ContentResolver resolver = context.getContentResolver();

        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_START_COLOR,
                DEFAULT_GRADIENT_START_COLOR, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_END_COLOR,
                DEFAULT_GRADIENT_END_COLOR, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_CHIP_GRADIENT_ANGLE, 0,
                UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
