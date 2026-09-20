/*
 * Copyright (C) 2010 Daniel Nilsson
 * Copyright (C) 2013 Slimroms
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ColorPickerDialog implements ColorPickerView.OnColorChangedListener {

    public interface OnColorChangedListener {
        void onColorChanged(@ColorInt int color);
    }

    private static final String STATE_COLOR = "color_picker_color";
    private static final String STATE_OLD_COLOR = "color_picker_old_color";
    private static final String STATE_ALPHA = "color_picker_alpha_enabled";

    private static final int[] STATIC_PRESETS = {
            0xFFFFFFFF, 0xFF000000, 0xFFF44336, 0xFFFF9800, 0xFFFFEB3B,
            0xFF4CAF50, 0xFF00BCD4, 0xFF2196F3, 0xFF9C27B0, 0xFFE91E63,
    };

    private final Context mContext;
    private final AlertDialog mDialog;
    private final ColorPickerView mColorPicker;
    private final ColorPickerPanelView mOldColor;
    private final ColorPickerPanelView mNewColor;
    private final TextView mHexLabel;
    private final EditText mHex;
    private final TextView mHexError;
    private final HorizontalScrollView mPresetScroller;
    private final LinearLayout mPresetContainer;
    private final List<ColorPickerPanelView> mPresets = new ArrayList<>();
    private final boolean mAlphaEnabled;

    @Nullable private OnColorChangedListener mListener;
    @ColorInt private int mDefaultColor;
    private boolean mUpdatingHex;

    public ColorPickerDialog(@NonNull Context context, @ColorInt int initialColor) {
        this(context, initialColor, false);
    }

    public ColorPickerDialog(@NonNull Context context, @ColorInt int initialColor,
            boolean alphaEnabled) {
        mContext = context;
        mAlphaEnabled = alphaEnabled;
        mDefaultColor = initialColor;

        final View layout = LayoutInflater.from(context)
                .inflate(R.layout.dialog_color_picker, null, false);

        mColorPicker = layout.findViewById(R.id.color_picker_view);
        mOldColor = layout.findViewById(R.id.old_color_panel);
        mNewColor = layout.findViewById(R.id.new_color_panel);
        mHexLabel = layout.findViewById(R.id.hex_label);
        mHex = layout.findViewById(R.id.hex);
        mHexError = layout.findViewById(R.id.hex_error);
        mPresetScroller = layout.findViewById(R.id.preset_scroller);
        mPresetContainer = layout.findViewById(R.id.preset_container);

        clipToBounds(layout);
        setUpPresetScroller();

        mColorPicker.setAlphaSliderVisible(alphaEnabled);
        mColorPicker.setOnColorChangedListener(this);

        final int startColor = ColorPickerUtils.applyAlphaPolicy(initialColor, alphaEnabled);
        mOldColor.setColor(startColor);
        mNewColor.setColor(startColor);
        mColorPicker.setColor(startColor, false);

        setUpHexInput();
        buildPresets();
        updateHexText(startColor);
        updatePresetSelection(startColor);

        mDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.color_picker_title)
                .setView(layout)
                .setPositiveButton(R.string.color_picker_select, (dialog, which) -> {
                    if (mListener != null) {
                        mListener.onColorChanged(getColor());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.color_picker_reset, null)
                .create();

        // Wire the neutral button after show() so it resets the picker without dismissing.
        mDialog.setOnShowListener(d -> {
            final View reset = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (reset != null) {
                reset.setOnClickListener(v -> setColor(mDefaultColor));
            }
            clipCustomPanel();
        });
    }

    /**
     * Forces a view to clip everything it draws to its own rectangle, on the render thread.
     */
    private static void clipToBounds(@Nullable View view) {
        if (view == null) {
            return;
        }
        view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        view.setClipToOutline(true);
        if (view instanceof ViewGroup group) {
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }
    }

    /**
     * Clips the framework's custom-view panel.
     */
    private void clipCustomPanel() {
        final View custom = mDialog.findViewById(android.R.id.custom);
        if (custom == null) {
            return;
        }
        clipToBounds(custom);
        final ViewParent parent = custom.getParent();
        if (parent instanceof View panel) {
            clipToBounds(panel);
        }
    }

    private void setUpPresetScroller() {
        mPresetScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        clipToBounds(mPresetScroller);
        mPresetScroller.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }

    public void setOnColorChangedListener(@Nullable OnColorChangedListener listener) {
        mListener = listener;
    }

    /** Color the Reset button restores. */
    public void setDefaultColor(@ColorInt int color) {
        mDefaultColor = ColorPickerUtils.applyAlphaPolicy(color, mAlphaEnabled);
    }

    @ColorInt
    public int getColor() {
        return mColorPicker.getColor();
    }

    public void setColor(@ColorInt int color) {
        mColorPicker.setColor(ColorPickerUtils.applyAlphaPolicy(color, mAlphaEnabled), true);
    }

    public void setAlphaSliderVisible(boolean visible) {
        mColorPicker.setAlphaSliderVisible(visible);
    }

    public void show() {
        mDialog.show();
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    public boolean isShowing() {
        return mDialog.isShowing();
    }

    @NonNull
    public AlertDialog getDialog() {
        return mDialog;
    }

    @NonNull
    public Bundle saveState() {
        final Bundle state = new Bundle();
        state.putInt(STATE_COLOR, getColor());
        state.putInt(STATE_OLD_COLOR, mOldColor.getColor());
        state.putBoolean(STATE_ALPHA, mAlphaEnabled);
        return state;
    }

    public void restoreState(@Nullable Bundle state) {
        if (state == null) {
            return;
        }
        mOldColor.setColor(state.getInt(STATE_OLD_COLOR, mOldColor.getColor()));
        setColor(state.getInt(STATE_COLOR, getColor()));
    }

    @Override
    public void onColorChanged(@ColorInt int color) {
        mNewColor.setColor(color);
        updateHexText(color);
        updatePresetSelection(color);
    }

    private void setUpHexInput() {
        mHexLabel.setText(mAlphaEnabled
                ? R.string.color_picker_hex_argb : R.string.color_picker_hex_rgb);
        mHex.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(mAlphaEnabled ? 9 : 7),
                (source, start, end, dest, dstart, dend) -> {
                    final StringBuilder out = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        final char c = source.charAt(i);
                        if (c == '#' || Character.digit(c, 16) != -1) {
                            out.append(Character.toUpperCase(c));
                        }
                    }
                    return out.toString();
                }
        });
        mHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mUpdatingHex) {
                    return;
                }
                try {
                    final int color = ColorPickerUtils.applyAlphaPolicy(
                            ColorPickerPreference.convertToColorInt(s.toString()), mAlphaEnabled);
                    setHexError(false);
                    mColorPicker.setColor(color, false);
                    mNewColor.setColor(color);
                    updatePresetSelection(color);
                } catch (IllegalArgumentException e) {
                    setHexError(true);
                }
            }
        });
    }

    /** Inline replacement for TextInputLayout's error state. */
    private void setHexError(boolean error) {
        mHexError.setVisibility(error ? View.VISIBLE : View.GONE);
    }

    private void updateHexText(@ColorInt int color) {
        mUpdatingHex = true;
        try {
            final String text = mAlphaEnabled
                    ? ColorPickerPreference.convertToARGB(color)
                    : ColorPickerPreference.convertToRGB(color);
            mHex.setText(text);
            mHex.setSelection(text.length());
            setHexError(false);
        } finally {
            mUpdatingHex = false;
        }
    }

    private void buildPresets() {
        final int size = (int) ColorPickerUtils.dp(mContext, 40f);
        final int margin = (int) ColorPickerUtils.dp(mContext, 6f);

        for (int color : buildPresetColors()) {
            final ColorPickerPanelView swatch = new ColorPickerPanelView(mContext);
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(margin);
            swatch.setLayoutParams(lp);
            swatch.setCircular(true);
            swatch.setColor(color);
            swatch.setClickable(true);
            swatch.setFocusable(true);
            swatch.setContentDescription(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
            swatch.setOnClickListener(v -> setColor(((ColorPickerPanelView) v).getColor()));
            mPresetContainer.addView(swatch);
            mPresets.add(swatch);
        }
    }

    private int[] buildPresetColors() {
        final List<Integer> colors = new ArrayList<>();
        // Material You dynamic palette first.
        addSystemColor(colors, android.R.color.system_accent1_500);
        addSystemColor(colors, android.R.color.system_accent2_500);
        addSystemColor(colors, android.R.color.system_accent3_500);
        for (int color : STATIC_PRESETS) {
            if (!colors.contains(color)) {
                colors.add(color);
            }
        }
        final int[] out = new int[colors.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = colors.get(i) | ColorPickerUtils.OPAQUE_MASK;
        }
        return out;
    }

    private void addSystemColor(List<Integer> colors, int resId) {
        try {
            final int color = mContext.getColor(resId) | ColorPickerUtils.OPAQUE_MASK;
            if (!colors.contains(color)) {
                colors.add(color);
            }
        } catch (Resources.NotFoundException ignored) {
            // Device without dynamic color resources - static presets are enough.
        }
    }

    private void updatePresetSelection(@ColorInt int color) {
        final int rgb = color & 0x00FFFFFF;
        for (ColorPickerPanelView swatch : mPresets) {
            swatch.setChecked((swatch.getColor() & 0x00FFFFFF) == rgb);
        }
    }
}
