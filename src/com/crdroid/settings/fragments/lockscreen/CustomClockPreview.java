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

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.ThemeUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;

import com.crdroid.settings.utils.SystemUtils;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class CustomClockPreview extends SettingsPreferenceFragment {

    private static final String TAG = "CustomClockPreview";
    private static final String PREF_FIRST_TIME = "first_time_clock_face_access";

    private static final String KEY_CLOCK_PREVIEW = "clock_preview";

    private ViewPager viewPager;
    private ClockPagerAdapter pagerAdapter;
    private ExtendedFloatingActionButton applyFab;
    private View highlightGuide;
    private TextView clockNameTextView;

    private int mClockPosition = 0;

    private ThemeUtils mThemeUtils;
    private Handler mHandler = new Handler();

    private static final int[] CLOCK_LAYOUTS = {
            R.layout.keyguard_clock_default,
            R.layout.keyguard_clock_oos, // 1
            R.layout.keyguard_clock_ios, // 2
            R.layout.keyguard_clock_simple, // 3
            R.layout.keyguard_clock_miui, // 4
            R.layout.keyguard_clock_ide,  // 5
            R.layout.keyguard_clock_moto, // 6
            R.layout.keyguard_clock_stylish, // 7
            R.layout.keyguard_clock_stylish2, //8
            R.layout.keyguard_clock_stylish3, // 9
            R.layout.keyguard_clock_stylish4, // 10
            R.layout.keyguard_clock_stylish5, // 11
            R.layout.keyguard_clock_stylish6, // 12
            R.layout.keyguard_clock_stylish7, // 13
            R.layout.keyguard_clock_stylish8, // 14
            R.layout.keyguard_clock_stylish9, // 15
            R.layout.keyguard_clock_stylish10, // 16
            R.layout.keyguard_clock_word, // 17
            R.layout.keyguard_clock_life, // 18
            R.layout.keyguard_clock_a9, // 19
            R.layout.keyguard_clock_nos1, // 20
            R.layout.keyguard_clock_nos2, // 21
            R.layout.keyguard_clock_num, // 22
            R.layout.keyguard_clock_accent, // 23
            R.layout.keyguard_clock_analog, // 24
            R.layout.keyguard_clock_block, // 25
            R.layout.keyguard_clock_bubble, // 26
            R.layout.keyguard_clock_label, // 27
            R.layout.keyguard_clock_taden, // 28
            R.layout.keyguard_clock_mont, // 29
            R.layout.keyguard_clock_encode, // 30
            R.layout.keyguard_clock_nos3 // 31
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(getString(R.string.lockscreen_custom_clock_style_title));
        mThemeUtils = new ThemeUtils(getActivity());

        addPreferencesFromResource(R.xml.lockscreen_clock_preview_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClockPreview();
    }

    private void setupClockPreview() {
        LayoutPreference clockPreviewPref = findPreference(KEY_CLOCK_PREVIEW);
        if (clockPreviewPref == null) return;

        clockNameTextView = clockPreviewPref.findViewById(R.id.clock_name);
        viewPager = clockPreviewPref.findViewById(R.id.view_pager);
        applyFab = clockPreviewPref.findViewById(R.id.apply_extended_fab);
        highlightGuide = clockPreviewPref.findViewById(R.id.highlight_guide);

        pagerAdapter = new ClockPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.setClipChildren(true);
        viewPager.setClipToPadding(true);

        mClockPosition = Settings.Secure.getIntForUser(
                getContext().getContentResolver(), "clock_style", 0, UserHandle.USER_CURRENT);
        if (mClockPosition < 0 || mClockPosition >= CLOCK_LAYOUTS.length) {
            mClockPosition = 0;
            Settings.Secure.putIntForUser(
                    getContext().getContentResolver(), "clock_style", 0, UserHandle.USER_CURRENT);
        }
        viewPager.setCurrentItem(mClockPosition);

        applyFab.setOnClickListener(v -> {
            Context ctx = getContext();
            int currentClock = Settings.Secure.getIntForUser(
                    ctx.getContentResolver(), "clock_style", 0, UserHandle.USER_CURRENT);
            if (mClockPosition != currentClock) {
                Settings.Secure.putIntForUser(
                        ctx.getContentResolver(), "clock_style", mClockPosition, UserHandle.USER_CURRENT);
                Settings.Secure.putIntForUser(
                        ctx.getContentResolver(), "lock_screen_custom_clock_face", 0, UserHandle.USER_CURRENT);
                updateClockOverlays(mClockPosition);
                SystemUtils.restartSystemUI(ctx);
            }
        });

        if (isFirstTime()) {
            highlightGuide.setVisibility(View.VISIBLE);
            highlightGuide.setOnClickListener(v -> {
                highlightGuide.setVisibility(View.GONE);
                disableHighlight();
            });
        } else {
            highlightGuide.setVisibility(View.GONE);
        }

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {}
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override
            public void onPageSelected(int position) {
                mClockPosition = position;
                if (viewPager != null) {
                    viewPager.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }
                updateClockName(position);
            }
        });

        updateClockName(mClockPosition);
    }

    private void updateClockName(int position) {
        String[] clockNames = {
            "Default Clock",
            "OnePlus Clock",
            "IOS Clock",
            "Simple Clock",
            "MIUI Clock",
            "IDE Clock",
            "Moto Clock",
            "Stylish Clock",
            "Stylish Clock 2",
            "Stylish Clock 3",
            "Stylish Clock 4",
            "Stylish Clock 5",
            "Stylish Clock 6",
            "Stylish Clock 7",
            "Stylish Clock 8",
            "Stylish Clock 9",
            "Stylish Clock 10",
            "Text Clock",
            "LifeStyle Clock",
            "Android 9 Vibe",
            "NothingOS 1 Clock",
            "NothingOS 2 Clock",
            "Stacked Clock",
            "X Factor",
            "Simple Analog",
            "Block",
            "Bubble",
            "Label Clock",
            "Taden Clock",
            "Mont Clock",
            "Encode Clock",
            "NOS Clock 3",
        };
        if (clockNameTextView != null && position >= 0 && position < clockNames.length) {
            clockNameTextView.setText(clockNames[position]);
        }
    }

    private void updateClockOverlays(int clockStyle) {
        mThemeUtils.setOverlayEnabled(
                "android.theme.customization.hideclock",
                clockStyle != 0 ? "com.android.systemui.clocks.hideclock" : "android",
                "android");
        mThemeUtils.setOverlayEnabled(
                "android.theme.customization.smartspace",
                clockStyle != 0 ? "com.android.systemui.hide.smartspace" : "com.android.systemui",
                "com.android.systemui");
    }

    private boolean isFirstTime() {
        return Settings.System.getIntForUser(
                getContext().getContentResolver(), PREF_FIRST_TIME, 1, UserHandle.USER_CURRENT) != 0;
    }

    private void disableHighlight() {
        Settings.System.putIntForUser(
                getContext().getContentResolver(), PREF_FIRST_TIME, 0, UserHandle.USER_CURRENT);
    }

    private class ClockPagerAdapter extends PagerAdapter {
        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View layout = inflater.inflate(CLOCK_LAYOUTS[position], container, false);
            container.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return CLOCK_LAYOUTS.length;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateClockName(mClockPosition);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateClockName(mClockPosition);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS;
    }
}
