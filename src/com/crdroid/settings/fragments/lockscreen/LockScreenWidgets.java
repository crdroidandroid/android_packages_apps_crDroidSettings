/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 * Copyright (C) 2024-25 Project Infinity X 
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
package com.crdroid.settings.fragments.lockscreen;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;
import com.crdroid.settings.preferences.SystemSettingSwitchPreference;
import com.crdroid.settings.preferences.SystemSettingListPreference;
import com.crdroid.settings.preferences.SystemSettingSeekBarPreference;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.crdroid.settings.utils.SystemUtils;

@SearchIndexable
public class LockScreenWidgets extends SettingsPreferenceFragment {

    public static final String TAG = "LockScreenWidgets";

    private static final String MAIN_WIDGET_1_KEY = "main_custom_widgets1";
    private static final String MAIN_WIDGET_2_KEY = "main_custom_widgets2";
    private static final String EXTRA_WIDGET_1_KEY = "custom_widgets1";
    private static final String EXTRA_WIDGET_2_KEY = "custom_widgets2";
    private static final String EXTRA_WIDGET_3_KEY = "custom_widgets3";
    private static final String EXTRA_WIDGET_4_KEY = "custom_widgets4";
    private static final String KEY_APPLY_CHANGE_BUTTON = "apply_change_button";
    private static final String LOCKSCREEN_WIDGETS_PREVIEW_KEY = "lockscreen_widgets_preview";
    private static final String LOCKSCREEN_WIDGETS_ENABLED_KEY = "lockscreen_widgets_enabled";
    private static final String LOCKSCREEN_WIDGETS_STYLE_KEY = "lockscreen_widgets_style";
    private static final String LOCKSCREEN_WIDGETS_TRANSPARENCY_KEY = "lockscreen_widgets_transparency";

    private static final String LOCKSCREEN_WIDGETS_KEY = "lockscreen_widgets";
    private static final String LOCKSCREEN_WIDGETS_EXTRAS_KEY = "lockscreen_widgets_extras";

    private Button bigWidget1, bigWidget2;
    private Button smallWidget1, smallWidget2, smallWidget3, smallWidget4;
    private ExtendedFloatingActionButton applyChangeButton;
    private LayoutPreference previewPreference;
    private SystemSettingSwitchPreference widgetsEnabledPreference;
    private SystemSettingListPreference widgetsStylePreference;
    private SystemSettingSeekBarPreference widgetsTransparencyPreference;

    private Map<String, String> widgetValuesMap = new HashMap<>();
    private Map<String, String> initialWidgetValuesMap = new HashMap<>();
    private Vibrator vibrator;

    private String[] defaultMainWidgets = {"none", "none"};
    private String[] defaultExtraWidgets = {"none", "none", "none", "none"};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.lock_screen_widgets);

        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);

        previewPreference = findPreference(LOCKSCREEN_WIDGETS_PREVIEW_KEY);
        widgetsEnabledPreference = findPreference(LOCKSCREEN_WIDGETS_ENABLED_KEY);
        widgetsStylePreference = findPreference(LOCKSCREEN_WIDGETS_STYLE_KEY);
        widgetsTransparencyPreference = findPreference(LOCKSCREEN_WIDGETS_TRANSPARENCY_KEY);

        View previewView = previewPreference.findViewById(R.id.lockscreen_widgets_preview);

        bigWidget1 = previewView.findViewById(R.id.big_widget_1);
        bigWidget2 = previewView.findViewById(R.id.big_widget_2);
        smallWidget1 = previewView.findViewById(R.id.small_widget_1);
        smallWidget2 = previewView.findViewById(R.id.small_widget_2);
        smallWidget3 = previewView.findViewById(R.id.small_widget_3);
        smallWidget4 = previewView.findViewById(R.id.small_widget_4);

        LayoutPreference applyButtonPreference = findPreference(KEY_APPLY_CHANGE_BUTTON);
        applyChangeButton = applyButtonPreference.findViewById(R.id.apply_change);

        setupWidgetClickListeners();
        loadInitialPreferences();
        saveInitialPreferences();
        applyChangeButton.setEnabled(false);

        widgetsEnabledPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (Boolean) newValue;
            updatePreviewVisibility(isEnabled);
            updateApplyButtonVisibility();
            SystemUtils.restartSystemUI(getContext());
            return true;
        });

        widgetsStylePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            int styleValue = Integer.parseInt((String) newValue);
            updateTransparencySeekbarState(styleValue);
            updateSmallWidgetsDrawable(styleValue);
            updateApplyButtonVisibility();
            return true;
        });

        widgetsTransparencyPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            updateApplyButtonVisibility();
            return true;
        });

        boolean isWidgetsEnabled = Settings.System.getInt(getActivity().getContentResolver(),
                LOCKSCREEN_WIDGETS_ENABLED_KEY, 0) == 1;
        updatePreviewVisibility(isWidgetsEnabled);

        int styleValue = Integer.parseInt(Settings.System.getString(getActivity().getContentResolver(),
                LOCKSCREEN_WIDGETS_STYLE_KEY));
        updateTransparencySeekbarState(styleValue);
        updateSmallWidgetsDrawable(styleValue);
    }

    private void updatePreviewVisibility(boolean isEnabled) {
        if (previewPreference != null) {
            previewPreference.setVisible(isEnabled);
        }
        applyChangeButton.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
    }

    private void updateApplyButtonVisibility() {
        boolean hasChanges = hasChanges() || widgetsTransparencyPreference.isEnabled() && widgetsTransparencyPreference.getValue() != 100;
        applyChangeButton.setEnabled(hasChanges);
    }

    private void updateTransparencySeekbarState(int styleValue) {
        if (widgetsTransparencyPreference != null) {
            boolean isSeekbarEnabled = styleValue == 2 || styleValue == 3;
            widgetsTransparencyPreference.setEnabled(isSeekbarEnabled);
        }
    }

    private void updateSmallWidgetsDrawable(int styleValue) {
        int drawableResId = styleValue == 1 ? R.drawable.widget_rounded_square_background : R.drawable.widget_rounded_background;
        smallWidget1.setBackgroundResource(drawableResId);
        smallWidget2.setBackgroundResource(drawableResId);
        smallWidget3.setBackgroundResource(drawableResId);
        smallWidget4.setBackgroundResource(drawableResId);
    }

    private void setupWidgetClickListeners() {
        View.OnClickListener clickListener = v -> {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            }
            String widgetKey = (String) v.getTag();
            showWidgetSelectionDialog(widgetKey);
        };

        bigWidget1.setTag(MAIN_WIDGET_1_KEY);
        bigWidget2.setTag(MAIN_WIDGET_2_KEY);
        smallWidget1.setTag(EXTRA_WIDGET_1_KEY);
        smallWidget2.setTag(EXTRA_WIDGET_2_KEY);
        smallWidget3.setTag(EXTRA_WIDGET_3_KEY);
        smallWidget4.setTag(EXTRA_WIDGET_4_KEY);

        bigWidget1.setOnClickListener(clickListener);
        bigWidget2.setOnClickListener(clickListener);
        smallWidget1.setOnClickListener(clickListener);
        smallWidget2.setOnClickListener(clickListener);
        smallWidget3.setOnClickListener(clickListener);
        smallWidget4.setOnClickListener(clickListener);

        applyChangeButton.setOnClickListener(v -> {
            updateWidgetPreferences();
            saveInitialPreferences();
            applyChangeButton.setEnabled(false);
            SystemUtils.restartSystemUI(getContext());
        });
    }

    private void showWidgetSelectionDialog(String widgetKey) {
        new WidgetSelectionDialogFragment(widgetKey)
                .show(getChildFragmentManager(), "WidgetSelectionDialog");
    }

    private void loadInitialPreferences() {
        ContentResolver resolver = getActivity().getContentResolver();
        String mainWidgets = Settings.System.getString(resolver, LOCKSCREEN_WIDGETS_KEY);
        String extraWidgets = Settings.System.getString(resolver, LOCKSCREEN_WIDGETS_EXTRAS_KEY);

        if (!TextUtils.isEmpty(mainWidgets)) {
            List<String> mainWidgetList = Arrays.asList(mainWidgets.split(","));
            widgetValuesMap.put(MAIN_WIDGET_1_KEY, mainWidgetList.size() > 0 ? mainWidgetList.get(0) : defaultMainWidgets[0]);
            widgetValuesMap.put(MAIN_WIDGET_2_KEY, mainWidgetList.size() > 1 ? mainWidgetList.get(1) : defaultMainWidgets[1]);
        } else {
            widgetValuesMap.put(MAIN_WIDGET_1_KEY, defaultMainWidgets[0]);
            widgetValuesMap.put(MAIN_WIDGET_2_KEY, defaultMainWidgets[1]);
        }

        if (!TextUtils.isEmpty(extraWidgets)) {
            List<String> extraWidgetList = Arrays.asList(extraWidgets.split(","));
            widgetValuesMap.put(EXTRA_WIDGET_1_KEY, extraWidgetList.size() > 0 ? extraWidgetList.get(0) : defaultExtraWidgets[0]);
            widgetValuesMap.put(EXTRA_WIDGET_2_KEY, extraWidgetList.size() > 1 ? extraWidgetList.get(1) : defaultExtraWidgets[1]);
            widgetValuesMap.put(EXTRA_WIDGET_3_KEY, extraWidgetList.size() > 2 ? extraWidgetList.get(2) : defaultExtraWidgets[2]);
            widgetValuesMap.put(EXTRA_WIDGET_4_KEY, extraWidgetList.size() > 3 ? extraWidgetList.get(3) : defaultExtraWidgets[3]);
        } else {
            widgetValuesMap.put(EXTRA_WIDGET_1_KEY, defaultExtraWidgets[0]);
            widgetValuesMap.put(EXTRA_WIDGET_2_KEY, defaultExtraWidgets[1]);
            widgetValuesMap.put(EXTRA_WIDGET_3_KEY, defaultExtraWidgets[2]);
            widgetValuesMap.put(EXTRA_WIDGET_4_KEY, defaultExtraWidgets[3]);
        }

        updatePreviewButtons();
    }

    private void updatePreviewButtons() {
        updateWidgetButtonText(bigWidget1, widgetValuesMap.getOrDefault(MAIN_WIDGET_1_KEY, "none"));
        updateWidgetButtonText(bigWidget2, widgetValuesMap.getOrDefault(MAIN_WIDGET_2_KEY, "none"));
        updateWidgetButtonText(smallWidget1, widgetValuesMap.getOrDefault(EXTRA_WIDGET_1_KEY, "none"));
        updateWidgetButtonText(smallWidget2, widgetValuesMap.getOrDefault(EXTRA_WIDGET_2_KEY, "none"));
        updateWidgetButtonText(smallWidget3, widgetValuesMap.getOrDefault(EXTRA_WIDGET_3_KEY, "none"));
        updateWidgetButtonText(smallWidget4, widgetValuesMap.getOrDefault(EXTRA_WIDGET_4_KEY, "none"));
    }

    private void updateWidgetButtonText(Button button, String widgetValue) {
        if (widgetValue.equals("none")) {
            button.setText(R.string.status_bar_date_none);
        } else {
            String[] widgetValues = getResources().getStringArray(R.array.widget_values);
            String[] widgetEntries = getResources().getStringArray(R.array.widget_entries);
            
            for (int i = 0; i < widgetValues.length; i++) {
                if (widgetValues[i].equals(widgetValue)) {
                    button.setText(widgetEntries[i]);
                    return;
                }
            }
            button.setText(widgetValue);
        }
    }

    private void saveInitialPreferences() {
        initialWidgetValuesMap.clear();
        initialWidgetValuesMap.putAll(widgetValuesMap);
    }

    private void updateWidgetPreferences() {
        String[] mainWidgetsList = Arrays.asList(
                widgetValuesMap.get(MAIN_WIDGET_1_KEY),
                widgetValuesMap.get(MAIN_WIDGET_2_KEY)
        ).toArray(new String[0]);;
        String[] extraWidgetsList = Arrays.asList(
                widgetValuesMap.get(EXTRA_WIDGET_1_KEY),
                widgetValuesMap.get(EXTRA_WIDGET_2_KEY),
                widgetValuesMap.get(EXTRA_WIDGET_3_KEY),
                widgetValuesMap.get(EXTRA_WIDGET_4_KEY)
        ).toArray(new String[0]);;
        ContentResolver resolver = getActivity().getContentResolver();
        if (Arrays.equals(defaultMainWidgets, mainWidgetsList)) {
            Settings.System.putString(resolver, LOCKSCREEN_WIDGETS_KEY, null);
        } else {
            String mainWidgets = TextUtils.join(",", mainWidgetsList);
            Settings.System.putString(resolver, LOCKSCREEN_WIDGETS_KEY, mainWidgets);
        }
        if (Arrays.equals(defaultExtraWidgets, extraWidgetsList)) {
            Settings.System.putString(resolver, LOCKSCREEN_WIDGETS_EXTRAS_KEY, null);
        } else {
            String extraWidgets = TextUtils.join(",", extraWidgetsList);
            Settings.System.putString(resolver, LOCKSCREEN_WIDGETS_EXTRAS_KEY, extraWidgets);
        }
    }

    private boolean hasChanges() {
        return !widgetValuesMap.equals(initialWidgetValuesMap);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.lock_screen_widgets);

    public static class WidgetSelectionDialogFragment extends DialogFragment {
        private String widgetKey;

        public WidgetSelectionDialogFragment(String widgetKey) {
            this.widgetKey = widgetKey;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.select_widget)
                    .setItems(R.array.widget_entries, (dialog, which) -> {
                        String selectedWidget = getResources().getStringArray(R.array.widget_values)[which];
                        ((LockScreenWidgets) getParentFragment()).onWidgetSelected(widgetKey, selectedWidget);
                    });
            return builder.create();
        }
    }

    public void onWidgetSelected(String widgetKey, String selectedWidget) {
        widgetValuesMap.put(widgetKey, selectedWidget);
        updatePreviewButtons();
        updateApplyButtonVisibility();
    }
}
