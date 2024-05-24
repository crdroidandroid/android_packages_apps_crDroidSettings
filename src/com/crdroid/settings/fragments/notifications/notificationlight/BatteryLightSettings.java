/*
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.notifications.notificationlight;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.internal.logging.nano.MetricsProto;

import org.lineageos.internal.notification.LightsCapabilities;

import lineageos.preference.LineageSystemSettingMainSwitchPreference;
import lineageos.preference.LineageSystemSettingSwitchPreference;
import lineageos.providers.LineageSettings;

import java.util.List;

@SearchIndexable
public class BatteryLightSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "BatteryLightSettings";

    private static final String KEY_BATTERY_LIGHTS = "battery_lights";
    private static final String GENERAL_SECTION = "general_section";
    private static final String COLORS_SECTION = "colors_list";
    private static final String BRIGHTNESS_SECTION = "brightness_section";

    private static final String LOW_COLOR_PREF = "low_color";
    private static final String MEDIUM_COLOR_PREF = "medium_color";
    private static final String FULL_COLOR_PREF = "full_color";
    private static final String REALLY_FULL_COLOR_PREF = "really_full_color";
    private static final String LIGHT_ENABLED_PREF = "battery_light_enabled";
    private static final String LIGHT_FULL_CHARGE_DISABLED_PREF =
            "battery_light_full_charge_disabled";
    private static final String PULSE_ENABLED_PREF = "battery_light_pulse";
    private static final String BRIGHTNESS_PREFERENCE = "battery_light_brightness_level";
    private static final String BRIGHTNESS_ZEN_PREFERENCE = "battery_light_brightness_level_zen";

    private ApplicationLightPreference mLowColorPref;
    private ApplicationLightPreference mMediumColorPref;
    private ApplicationLightPreference mFullColorPref;
    private ApplicationLightPreference mReallyFullColorPref;
    private LineageSystemSettingMainSwitchPreference mLightEnabledPref;
    private LineageSystemSettingSwitchPreference mLightFullChargeDisabledPref;
    private LineageSystemSettingSwitchPreference mPulseEnabledPref;
    private BatteryBrightnessPreference mBatteryBrightnessPref;
    private BatteryBrightnessZenPreference mBatteryBrightnessZenPref;
    private int mDefaultLowColor;
    private int mDefaultMediumColor;
    private int mDefaultFullColor;
    private int mDefaultReallyFullColor;
    private boolean mMultiColorLed;

    private static final int MENU_RESET = Menu.FIRST;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Context context = requireContext();
        final Resources res = getResources();
        final ContentResolver resolver = context.getContentResolver();

        // Collect battery led capabilities.
        mMultiColorLed =
                LightsCapabilities.supports(context, LightsCapabilities.LIGHTS_RGB_BATTERY_LED);
        // liblights supports brightness control
        final boolean halAdjustableBrightness = LightsCapabilities.supports(context,
                LightsCapabilities.LIGHTS_ADJUSTABLE_BATTERY_LED_BRIGHTNESS);
        final boolean pulsatingLed = LightsCapabilities.supports(context,
                LightsCapabilities.LIGHTS_PULSATING_LED);
        final boolean segmentedBatteryLed = LightsCapabilities.supports(context,
                LightsCapabilities.LIGHTS_SEGMENTED_BATTERY_LED);

        addPreferencesFromResource(R.xml.battery_light_settings);
        requireActivity().getActionBar().setTitle(R.string.battery_light_title);

        PreferenceScreen prefSet = getPreferenceScreen();

        PreferenceGroup generalPrefs = prefSet.findPreference(GENERAL_SECTION);

        mLightEnabledPref = prefSet.findPreference(LIGHT_ENABLED_PREF);
        mLightFullChargeDisabledPref = prefSet.findPreference(LIGHT_FULL_CHARGE_DISABLED_PREF);
        mPulseEnabledPref = prefSet.findPreference(PULSE_ENABLED_PREF);
        mBatteryBrightnessPref = prefSet.findPreference(BRIGHTNESS_PREFERENCE);
        mBatteryBrightnessZenPref = prefSet.findPreference(BRIGHTNESS_ZEN_PREFERENCE);

        boolean isLightEnabled = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.BATTERY_LIGHT_ENABLED,
                isBatteryLightEnabled(context) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
        mLightEnabledPref.setChecked(isLightEnabled);

        boolean isLightFullChargeDisabled = LineageSettings.System.getIntForUser(resolver,
                LineageSettings.System.BATTERY_LIGHT_FULL_CHARGE_DISABLED,
                isBatteryLightFullChargeDisabled(context) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
        mLightFullChargeDisabledPref.setChecked(isLightFullChargeDisabled);

        mDefaultLowColor = res.getInteger(
                com.android.internal.R.integer.config_notificationsBatteryLowARGB);
        mDefaultMediumColor = res.getInteger(
                com.android.internal.R.integer.config_notificationsBatteryMediumARGB);
        mDefaultFullColor = res.getInteger(
                com.android.internal.R.integer.config_notificationsBatteryFullARGB);
        mDefaultReallyFullColor = res.getInteger(
                com.android.internal.R.integer.config_notificationsBatteryReallyFullARGB);

        int batteryBrightness = mBatteryBrightnessPref.getBrightnessSetting();

        if (!pulsatingLed || segmentedBatteryLed) {
            generalPrefs.removePreference(mPulseEnabledPref);
        } else {
            boolean isPulseEnabled = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_PULSE,
                    isBatteryLightPulseEnabled(context) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
            mPulseEnabledPref.setChecked(isPulseEnabled);
        }

        if (mMultiColorLed) {
            generalPrefs.removePreference(mLightFullChargeDisabledPref);
            setHasOptionsMenu(true);

            // Low, Medium and full color preferences
            mLowColorPref = prefSet.findPreference(LOW_COLOR_PREF);
            mLowColorPref.setOnPreferenceChangeListener(this);
            mLowColorPref.setDefaultValues(mDefaultLowColor, 0, 0);
            mLowColorPref.setBrightness(batteryBrightness);

            mMediumColorPref = prefSet.findPreference(MEDIUM_COLOR_PREF);
            mMediumColorPref.setOnPreferenceChangeListener(this);
            mMediumColorPref.setDefaultValues(mDefaultMediumColor, 0, 0);
            mMediumColorPref.setBrightness(batteryBrightness);

            mFullColorPref = prefSet.findPreference(FULL_COLOR_PREF);
            mFullColorPref.setOnPreferenceChangeListener(this);
            mFullColorPref.setDefaultValues(mDefaultFullColor, 0, 0);
            mFullColorPref.setBrightness(batteryBrightness);

            mReallyFullColorPref = prefSet.findPreference(REALLY_FULL_COLOR_PREF);
            mReallyFullColorPref.setOnPreferenceChangeListener(this);
            mReallyFullColorPref.setDefaultValues(mDefaultReallyFullColor, 0, 0);
            mReallyFullColorPref.setBrightness(batteryBrightness);

            final BrightnessPreference.OnBrightnessChangedListener brightnessListener =
                    brightness -> {
                mLowColorPref.setBrightness(brightness);
                mMediumColorPref.setBrightness(brightness);
                mFullColorPref.setBrightness(brightness);
                mReallyFullColorPref.setBrightness(brightness);
            };
            mBatteryBrightnessPref.setOnBrightnessChangedListener(brightnessListener);
        } else {
            prefSet.removePreference(prefSet.findPreference(COLORS_SECTION));
            resetColors();
        }

        // Remove battery LED brightness controls if we can't support them.
        if (!mMultiColorLed && !halAdjustableBrightness) {
            prefSet.removePreference(prefSet.findPreference(BRIGHTNESS_SECTION));
        }

        //watch(LineageSettings.System.getUriFor(LineageSettings.System.BATTERY_LIGHT_ENABLED));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshColors();
    }

    private void refreshColors() {
        ContentResolver resolver = requireActivity().getContentResolver();

        if (mLowColorPref != null) {
            int lowColor = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_LOW_COLOR, mDefaultLowColor, UserHandle.USER_CURRENT);
            mLowColorPref.setAllValues(lowColor, 0, 0, false);
        }

        if (mMediumColorPref != null) {
            int mediumColor = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR, mDefaultMediumColor, UserHandle.USER_CURRENT);
            mMediumColorPref.setAllValues(mediumColor, 0, 0, false);
        }

        if (mFullColorPref != null) {
            int fullColor = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_FULL_COLOR, mDefaultFullColor, UserHandle.USER_CURRENT);
            mFullColorPref.setAllValues(fullColor, 0, 0, false);
        }

        if (mReallyFullColorPref != null) {
            int reallyfullColor = LineageSettings.System.getIntForUser(resolver,
                    LineageSettings.System.BATTERY_LIGHT_REALLY_FULL_COLOR, mDefaultReallyFullColor, UserHandle.USER_CURRENT);
            mReallyFullColorPref.setAllValues(reallyfullColor, 0, 0, false);
            updateBrightnessPrefColor(reallyfullColor);
        }
    }

    private void updateBrightnessPrefColor(int color) {
        // If the user has selected no light (ie black) for
        // full charge, use white for the brightness preference.
        if (color == 0) {
            color = 0xFFFFFF;
        }
        mBatteryBrightnessPref.setLedColor(color);
        mBatteryBrightnessZenPref.setLedColor(color);
    }

    /**
     * Updates the default or application specific notification settings.
     *
     * @param key of the specific setting to update
     */
    protected void updateValues(String key, Integer color) {
        ContentResolver resolver = requireActivity().getContentResolver();
        switch (key) {
            case LOW_COLOR_PREF:
                LineageSettings.System.putIntForUser(resolver,
                        LineageSettings.System.BATTERY_LIGHT_LOW_COLOR, color, UserHandle.USER_CURRENT);
                break;
            case MEDIUM_COLOR_PREF:
                LineageSettings.System.putIntForUser(resolver,
                        LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR, color, UserHandle.USER_CURRENT);
                break;
            case FULL_COLOR_PREF:
                LineageSettings.System.putIntForUser(resolver,
                        LineageSettings.System.BATTERY_LIGHT_FULL_COLOR, color, UserHandle.USER_CURRENT);
                break;
            case REALLY_FULL_COLOR_PREF:
                LineageSettings.System.putIntForUser(resolver,
                        LineageSettings.System.BATTERY_LIGHT_REALLY_FULL_COLOR, color, UserHandle.USER_CURRENT);
                updateBrightnessPrefColor(color);
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mMultiColorLed) {
            menu.add(0, MENU_RESET, 0, R.string.reset)
                    .setIcon(R.drawable.ic_settings_backup_restore)
                    .setAlphabeticShortcut('r')
                    .setShowAsActionFlags(
                            MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                resetToDefaults();
                return true;
        }
        return false;
    }

    protected void resetColors() {
        ContentResolver resolver = requireActivity().getContentResolver();

        // Reset to the framework default colors
        LineageSettings.System.putIntForUser(resolver, LineageSettings.System.BATTERY_LIGHT_LOW_COLOR,
                mDefaultLowColor, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver, LineageSettings.System.BATTERY_LIGHT_MEDIUM_COLOR,
                mDefaultMediumColor, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver, LineageSettings.System.BATTERY_LIGHT_FULL_COLOR,
                mDefaultFullColor, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver, LineageSettings.System.BATTERY_LIGHT_REALLY_FULL_COLOR,
                mDefaultReallyFullColor, UserHandle.USER_CURRENT);
        refreshColors();
    }

    private static boolean isBatteryLightEnabled(Context context) {
        try {
            Context con = context.createPackageContext("org.lineageos.lineageparts", 0);
            int id = con.getResources().getIdentifier("def_battery_light_enabled",
                    "bool", "org.lineageos.lineageparts");
            return con.getResources().getBoolean(id);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private static boolean isBatteryLightFullChargeDisabled(Context context) {
        try {
            Context con = context.createPackageContext("org.lineageos.lineageparts", 0);
            int id = con.getResources().getIdentifier("def_battery_light_full_charge_disabled",
                    "bool", "org.lineageos.lineageparts");
            return con.getResources().getBoolean(id);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isBatteryLightPulseEnabled(Context context) {
        try {
            Context con = context.createPackageContext("org.lineageos.lineageparts", 0);
            int id = con.getResources().getIdentifier("def_battery_light_pulse",
                    "bool", "org.lineageos.lineageparts");
            return con.getResources().getBoolean(id);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    protected void resetToDefaults() {
        final Context context = getContext();

        if (mLightEnabledPref != null) mLightEnabledPref.setChecked(isBatteryLightEnabled(context));
        if (mLightFullChargeDisabledPref != null) {
            mLightFullChargeDisabledPref.setChecked(isBatteryLightFullChargeDisabled(context));
        }
        if (mPulseEnabledPref != null) mPulseEnabledPref.setChecked(isBatteryLightPulseEnabled(context));

        resetColors();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ApplicationLightPreference lightPref = (ApplicationLightPreference) preference;
        updateValues(lightPref.getKey(), lightPref.getColor());
        return true;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.battery_light_settings) {

        @Override
        public List<String> getNonIndexableKeys(Context context) {
            final List<String> result = super.getNonIndexableKeys(context);

            if (!LightsCapabilities.supports(context, LightsCapabilities.LIGHTS_BATTERY_LED)) {
                result.add(KEY_BATTERY_LIGHTS);
                result.add(LIGHT_ENABLED_PREF);
                result.add(GENERAL_SECTION);
                result.add(LIGHT_FULL_CHARGE_DISABLED_PREF);
                result.add(COLORS_SECTION);
                result.add(LOW_COLOR_PREF);
                result.add(MEDIUM_COLOR_PREF);
                result.add(FULL_COLOR_PREF);
                result.add(REALLY_FULL_COLOR_PREF);
            } else if (LightsCapabilities.supports(context,
                    LightsCapabilities.LIGHTS_RGB_BATTERY_LED)) {
                result.add(LIGHT_FULL_CHARGE_DISABLED_PREF);
            } else {
                result.add(COLORS_SECTION);
                result.add(LOW_COLOR_PREF);
                result.add(MEDIUM_COLOR_PREF);
                result.add(FULL_COLOR_PREF);
                result.add(REALLY_FULL_COLOR_PREF);
            }
            if (!LightsCapabilities.supports(context,
                    LightsCapabilities.LIGHTS_ADJUSTABLE_BATTERY_LED_BRIGHTNESS)) {
                result.add(BRIGHTNESS_SECTION);
                result.add(BRIGHTNESS_PREFERENCE);
                result.add(BRIGHTNESS_ZEN_PREFERENCE);
            }
            if (!LightsCapabilities.supports(context, LightsCapabilities.LIGHTS_PULSATING_LED) ||
                    LightsCapabilities.supports(context,
                            LightsCapabilities.LIGHTS_SEGMENTED_BATTERY_LED)) {
                result.add(PULSE_ENABLED_PREF);
            }
            return result;
        }
    };
}
