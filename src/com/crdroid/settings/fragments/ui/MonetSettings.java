/*
 * Copyright (C) 2021-2024 crDroid Android Project
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

package com.crdroid.settings.fragments.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

import java.lang.CharSequence;

import lineageos.providers.LineageSettings;

import org.json.JSONException;
import org.json.JSONObject;

@SearchIndexable
public class MonetSettings extends DashboardFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "MonetSettings";
    private static final String OVERLAY_CATEGORY_ACCENT_COLOR =
            "android.theme.customization.accent_color";
    private static final String OVERLAY_CATEGORY_SYSTEM_PALETTE =
            "android.theme.customization.system_palette";
    private static final String OVERLAY_CATEGORY_THEME_STYLE =
            "android.theme.customization.theme_style";
    private static final String OVERLAY_CATEGORY_BG_COLOR =
            "android.theme.customization.bg_color";
    private static final String OVERLAY_COLOR_SOURCE =
            "android.theme.customization.color_source";
    private static final String OVERLAY_COLOR_BOTH =
            "android.theme.customization.color_both";
    private static final String OVERLAY_LUMINANCE_FACTOR =
            "android.theme.customization.luminance_factor";
    private static final String OVERLAY_CHROMA_FACTOR =
            "android.theme.customization.chroma_factor";
    private static final String OVERLAY_TINT_BACKGROUND =
            "android.theme.customization.tint_background";
    private static final String COLOR_SOURCE_PRESET = "preset";
    private static final String COLOR_SOURCE_HOME = "home_wallpaper";
    private static final String COLOR_SOURCE_LOCK = "lock_wallpaper";
    private static final String TIMESTAMP_FIELD = "_applied_timestamp";

    private static final String PREF_THEME_STYLE = "theme_style";
    private static final String PREF_COLOR_SOURCE = "color_source";
    private static final String PREF_ACCENT_COLOR = "accent_color";
    private static final String PREF_ACCENT_BACKGROUND = "accent_background";
    private static final String PREF_BG_COLOR = "bg_color";
    private static final String PREF_LUMINANCE_FACTOR = "luminance_factor";
    private static final String PREF_CHROMA_FACTOR = "chroma_factor";
    private static final String PREF_TINT_BACKGROUND = "tint_background";

    private static final int DEFAULT_COLOR = 0xFF1b6ef3;

    private ListPreference mThemeStylePref;
    private ListPreference mColorSourcePref;
    private ColorPickerPreference mAccentColorPref;
    private SwitchPreferenceCompat mAccentBackgroundPref;
    private ColorPickerPreference mBgColorPref;
    private CustomSeekBarPreference mLuminancePref;
    private CustomSeekBarPreference mChromaPref;
    private SwitchPreferenceCompat mTintBackgroundPref;

    private int mAccentColorValue;
    private int mBgColorValue;

    private SharedPreferences mSharedPreferences;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.monet_engine;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mThemeStylePref = findPreference(PREF_THEME_STYLE);
        mColorSourcePref = findPreference(PREF_COLOR_SOURCE);
        mAccentColorPref = findPreference(PREF_ACCENT_COLOR);
        mAccentBackgroundPref = findPreference(PREF_ACCENT_BACKGROUND);
        mBgColorPref = findPreference(PREF_BG_COLOR);
        mLuminancePref = findPreference(PREF_LUMINANCE_FACTOR);
        mChromaPref = findPreference(PREF_CHROMA_FACTOR);
        mTintBackgroundPref = findPreference(PREF_TINT_BACKGROUND);
        mSharedPreferences = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);

        updatePreferences();

        mThemeStylePref.setOnPreferenceChangeListener(this);
        mColorSourcePref.setOnPreferenceChangeListener(this);
        mAccentColorPref.setOnPreferenceChangeListener(this);
        mAccentBackgroundPref.setOnPreferenceChangeListener(this);
        mBgColorPref.setOnPreferenceChangeListener(this);
        mLuminancePref.setOnPreferenceChangeListener(this);
        mChromaPref.setOnPreferenceChangeListener(this);
        mTintBackgroundPref.setOnPreferenceChangeListener(this);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.BERRY_BLACK_THEME, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    private void updatePreferences() {
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (overlayPackageJson != null && !overlayPackageJson.isEmpty()) {
            try {
                final JSONObject object = new JSONObject(overlayPackageJson);
                final String style = object.optString(OVERLAY_CATEGORY_THEME_STYLE, "TONAL_SPOT");
                final String source = object.optString(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
                String color;
                if (object.has(OVERLAY_CATEGORY_SYSTEM_PALETTE)) {
                    color = object.optString(OVERLAY_CATEGORY_SYSTEM_PALETTE);
                    mAccentColorValue = ColorPickerPreference.convertToColorInt(color);
                } else {
                    mAccentColorValue = mSharedPreferences.getInt(PREF_ACCENT_COLOR, DEFAULT_COLOR);
                    color = ColorPickerPreference.convertToRGB(mAccentColorValue).replace("#", "");
                }
                boolean hasBGColor = object.has(OVERLAY_CATEGORY_BG_COLOR);
                mAccentBackgroundPref.setChecked(mSharedPreferences.getBoolean(PREF_ACCENT_BACKGROUND, hasBGColor));
                if (hasBGColor) {
                    mBgColorValue = object.optInt(OVERLAY_CATEGORY_BG_COLOR);
                } else {
                    mBgColorValue = mSharedPreferences.getInt(PREF_BG_COLOR, DEFAULT_COLOR);
                }
                boolean both;
                if (object.has(OVERLAY_COLOR_BOTH)) {
                    both = object.optInt(OVERLAY_COLOR_BOTH) == 1;
                } else {
                    both = false;
                }
                final boolean tintBG = object.optInt(OVERLAY_TINT_BACKGROUND, 0) == 1;
                final float lumin = (float) object.optDouble(OVERLAY_LUMINANCE_FACTOR, 1d);
                final float chroma = (float) object.optDouble(OVERLAY_CHROMA_FACTOR, 1d);
                // style handling
                boolean styleUpdated = false;
                if (style != null && !style.isEmpty()) {
                    for (CharSequence value : mThemeStylePref.getEntryValues()) {
                        if (value.toString().equals(style)) {
                            styleUpdated = true;
                            break;
                        }
                    }
                    if (styleUpdated) {
                        updateListByValue(mThemeStylePref, style);
                    }
                }
                if (!styleUpdated) {
                    updateListByValue(mThemeStylePref,
                            mThemeStylePref.getEntryValues()[0].toString());
                }
                // color handling
                final String sourceVal = (source == null || source.isEmpty() ||
                        (source.equals(COLOR_SOURCE_HOME) && both)) ? "both" : source;
                updateListByValue(mColorSourcePref, sourceVal);
                updateAccentEnablement(sourceVal);
                // Set preview color irrespective it is enabled
                if (color != null && !color.isEmpty()) {
                    mAccentColorPref.setNewPreviewColor(mAccentColorValue);
                }
                mBgColorPref.setNewPreviewColor(mBgColorValue);
                // etc
                int luminV = 0;
                if (lumin > 1d) luminV = Math.round((lumin - 1f) * 100f);
                else if (lumin < 1d) luminV = -1 * Math.round((1f - lumin) * 100f);
                mLuminancePref.setValue(luminV);
                int chromaV = 0;
                if (chroma > 1d) chromaV = Math.round((chroma - 1f) * 100f);
                else if (chroma < 1d) chromaV = -1 * Math.round((1f - chroma) * 100f);
                mChromaPref.setValue(chromaV);
                mTintBackgroundPref.setChecked(tintBG);
            } catch (JSONException | IllegalArgumentException ignored) {}
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mThemeStylePref) {
            String value = (String) newValue;
            setStyleValue(value);
            updateListByValue(mThemeStylePref, value, false);
            return true;
        } else if (preference == mColorSourcePref) {
            String value = (String) newValue;
            setSourceValue(value);
            updateListByValue(mColorSourcePref, value, false);
            updateAccentEnablement(value);
            return true;
        } else if (preference == mAccentColorPref) {
            mAccentColorValue = (Integer) newValue;
            mSharedPreferences.edit().putInt(PREF_ACCENT_COLOR, mAccentColorValue).apply();
            setColorValue();
            return true;
        } else if (preference == mAccentBackgroundPref) {
            boolean value = (Boolean) newValue;
            mAccentBackgroundPref.setChecked(value);
            mSharedPreferences.edit().putBoolean(PREF_ACCENT_BACKGROUND, value).apply();
            setBgColorValue();
            return true;
        } else if (preference == mBgColorPref) {
            mBgColorValue = (Integer) newValue;
            mSharedPreferences.edit().putInt(PREF_BG_COLOR, mBgColorValue).apply();
            setBgColorValue();
            return true;
        } else if (preference == mLuminancePref) {
            int value = (Integer) newValue;
            setLuminanceValue(value);
            return true;
        } else if (preference == mChromaPref) {
            int value = (Integer) newValue;
            setChromaValue(value);
            return true;
        } else if (preference == mTintBackgroundPref) {
            boolean value = (Boolean) newValue;
            setTintBackgroundValue(value);
            return true;
        }
        return false;
    }

    private void updateListByValue(ListPreference pref, String value) {
        updateListByValue(pref, value, true);
    }

    private void updateListByValue(ListPreference pref, String value, boolean set) {
        if (set) pref.setValue(value);
        final int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
    }

    private void updateAccentEnablement(String source) {
        final boolean shouldEnable = source != null && source.equals(COLOR_SOURCE_PRESET);
        mAccentColorPref.setEnabled(shouldEnable);
        mAccentBackgroundPref.setEnabled(shouldEnable);
        setColorValue();
        setBgColorValue();
    }

    private JSONObject getSettingsJson() throws JSONException {
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        JSONObject object;
        if (overlayPackageJson == null || overlayPackageJson.isEmpty())
            return new JSONObject();
        return new JSONObject(overlayPackageJson);
    }

    private void putSettingsJson(JSONObject object) {
        Settings.Secure.putStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                object.toString(), UserHandle.USER_CURRENT);
    }

    private void setStyleValue(String style) {
        try {
            JSONObject object = getSettingsJson();
            object.putOpt(OVERLAY_CATEGORY_THEME_STYLE, style);
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setSourceValue(String source) {
        try {
            JSONObject object = getSettingsJson();
            if (source.equals("both")) {
                object.putOpt(OVERLAY_COLOR_BOTH, 1);
                object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
            } else {
                object.remove(OVERLAY_COLOR_BOTH);
                object.putOpt(OVERLAY_COLOR_SOURCE, source);
            }
            object.putOpt(TIMESTAMP_FIELD, System.currentTimeMillis());
            if (!source.equals(COLOR_SOURCE_PRESET)) {
                object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
            }
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setColorValue() {
        try {
            JSONObject object = getSettingsJson();

            if (mColorSourcePref.getValue().equals(COLOR_SOURCE_PRESET)) {
                final String rgbColor = ColorPickerPreference.convertToRGB(mAccentColorValue).replace("#", "");
                object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgbColor);
                object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgbColor);
            } else {
                object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
            }
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setBgColorValue() {
        try {
            JSONObject object = getSettingsJson();
            if (mColorSourcePref.getValue().equals(COLOR_SOURCE_PRESET) && mAccentBackgroundPref.isChecked()) {
                object.putOpt(OVERLAY_CATEGORY_BG_COLOR, mBgColorValue);
            } else {
                object.remove(OVERLAY_CATEGORY_BG_COLOR);
            }
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setLuminanceValue(int lumin) {
        try {
            JSONObject object = getSettingsJson();
            if (lumin == 0)
                object.remove(OVERLAY_LUMINANCE_FACTOR);
            else
                object.putOpt(OVERLAY_LUMINANCE_FACTOR, 1d + ((double) lumin / 100d));
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setChromaValue(int chroma) {
        try {
            JSONObject object = getSettingsJson();
            if (chroma == 0)
                object.remove(OVERLAY_CHROMA_FACTOR);
            else
                object.putOpt(OVERLAY_CHROMA_FACTOR, 1d + ((double) chroma / 100d));
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setTintBackgroundValue(boolean tint) {
        try {
            JSONObject object = getSettingsJson();
            if (!tint) object.remove(OVERLAY_TINT_BACKGROUND);
            else object.putOpt(OVERLAY_TINT_BACKGROUND, 1);
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.monet_engine);
}
