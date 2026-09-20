/*
 * Copyright (C) 2011 Sergey Margaritov
 * Copyright (C) 2013 Slimroms
 * Copyright (C) 2015 The TeamEos Project
 * Copyright (C) 2020-2026 crDroid Android Project
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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import java.util.Locale;

/**
 * Preference that opens a {@link ColorPickerDialog} and stores the result as an ARGB int.
 */
public class ColorPickerPreference extends Preference
        implements ColorPickerDialog.OnColorChangedListener {

    private static final String SETTINGS_NS =
            "http://schemas.android.com/apk/res/com.android.settings";

    /** Used only when nothing usable is declared in XML. Opaque on purpose. */
    @ColorInt private static final int FALLBACK_COLOR = Color.BLACK;

    @ColorInt private int mDefaultValue = FALLBACK_COLOR;
    @ColorInt private int mCurrentValue = FALLBACK_COLOR;
    private boolean mValueSet;

    private boolean mAlphaSliderEnabled;
    private boolean mShowReset = true;
    private boolean mShowPreview = true;
    private boolean mDividerAbove;
    private boolean mDividerBelow;
    private boolean mAutoSummary = true;

    @Nullable private ImageView mPreviewView;
    @Nullable private View mResetView;
    @Nullable private ColorPickerDialog mDialog;

    public ColorPickerPreference(Context context) {
        this(context, null);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_material_settings);
        setWidgetLayoutResource(R.layout.preference_widget_color_swatch);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        // "alphaSlider" has historically been declared without a namespace; accept both.
        mAlphaSliderEnabled = attrs.getAttributeBooleanValue(null, "alphaSlider", false)
                || attrs.getAttributeBooleanValue(SETTINGS_NS, "alphaSlider", false);
        mShowReset = attrs.getAttributeBooleanValue(SETTINGS_NS, "showReset", true);
        mShowPreview = attrs.getAttributeBooleanValue(SETTINGS_NS, "showPreview", true);
        mDividerAbove = attrs.getAttributeBooleanValue(SETTINGS_NS, "dividerAbove", false);
        mDividerBelow = attrs.getAttributeBooleanValue(SETTINGS_NS, "dividerBelow", false);
    }

    @Override
    protected Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        final TypedValue tv = a.peekValue(index);
        if (tv == null) {
            return FALLBACK_COLOR;
        }
        if (tv.type == TypedValue.TYPE_STRING && tv.string != null) {
            // android:defaultValue="#FF8000" / "0xFFFF8000" written as a plain string.
            try {
                return convertToColorInt(tv.string.toString());
            } catch (IllegalArgumentException e) {
                return FALLBACK_COLOR;
            }
        }
        // Handles TYPE_INT_COLOR_* and @color/... references. getInt() did not.
        return a.getColor(index, a.getInt(index, FALLBACK_COLOR));
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        mDefaultValue = applyAlphaPolicy(defaultValue instanceof Integer
                ? (Integer) defaultValue : FALLBACK_COLOR);
        setColorInternal(getPersistedInt(mDefaultValue), false);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        if (!mValueSet) {
            setColorInternal(getPersistedInt(mDefaultValue), false);
        }
    }

    @ColorInt
    private int applyAlphaPolicy(@ColorInt int color) {
        return ColorPickerUtils.applyAlphaPolicy(color, mAlphaSliderEnabled);
    }

    @ColorInt
    public int getColor() {
        return mCurrentValue;
    }

    public void setColor(@ColorInt int color) {
        color = applyAlphaPolicy(color);
        if (color == mCurrentValue && mValueSet) {
            return;
        }
        if (!callChangeListener(color)) {
            return;
        }
        setColorInternal(color, true);
    }

    private void setColorInternal(@ColorInt int color, boolean persist) {
        color = applyAlphaPolicy(color);
        final boolean changed = color != mCurrentValue || !mValueSet;
        mCurrentValue = color;
        mValueSet = true;

        if (persist) {
            persistInt(color);
        }
        if (mAutoSummary) {
            setSummary(convertToARGB(color));
        }
        updatePreview();
        if (changed) {
            notifyChanged();
        }
    }

    @Override
    public void onColorChanged(@ColorInt int color) {
        setColor(color);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(mDividerAbove);
        holder.setDividerAllowedBelow(mDividerBelow);

        mPreviewView = (ImageView) holder.findViewById(R.id.color_preview);
        mResetView = holder.findViewById(R.id.color_reset);

        if (mPreviewView != null) {
            mPreviewView.setVisibility(mShowPreview && isEnabled() ? View.VISIBLE : View.GONE);
        }
        if (mResetView != null) {
            mResetView.setVisibility(mShowReset && isEnabled() ? View.VISIBLE : View.GONE);
            mResetView.setEnabled(isEnabled());
            mResetView.setOnClickListener(v -> setColor(mDefaultValue));
        }
        updatePreview();
    }

    private void updatePreview() {
        if (mPreviewView == null || !mShowPreview) {
            return;
        }
        final int size = (int) getContext().getResources()
                .getDimension(R.dimen.oval_notification_size);
        mPreviewView.setImageDrawable(createSwatch(size, mCurrentValue));
        mPreviewView.setContentDescription(convertToARGB(mCurrentValue));
    }

    private Drawable createSwatch(int size, @ColorInt int color) {
        final AlphaPatternDrawable checker = new AlphaPatternDrawable(
                (int) ColorPickerUtils.dp(getContext(), 3f));
        checker.setCornerRadius(size / 2f);

        final GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        fill.setColor(color);
        fill.setSize(size, size);

        final GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setSize(size, size);
        final int outline = ColorPickerUtils.resolveThemeColor(getContext(),
                android.R.attr.colorControlNormal, 0xFF6E6E6E);
        ring.setStroke((int) ColorPickerUtils.dp(getContext(), 1f),
                (outline & 0x00FFFFFF) | 0x3D000000);

        final LayerDrawable layers = new LayerDrawable(new Drawable[]{checker, fill, ring});
        layers.setBounds(0, 0, size, size);
        return layers;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        notifyChanged();
    }

    @Override
    protected void onClick() {
        showDialog(null);
    }

    public void showDialog(@Nullable Bundle state) {
        if (!isEnabled() || (mDialog != null && mDialog.isShowing())) {
            return;
        }
        mDialog = new ColorPickerDialog(getContext(), mCurrentValue, mAlphaSliderEnabled);
        mDialog.setDefaultColor(mDefaultValue);
        mDialog.setOnColorChangedListener(this);
        mDialog.restoreState(state);
        mDialog.show();
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (mDialog == null || !mDialog.isShowing()) {
            return superState;
        }
        final SavedState state = new SavedState(superState);
        state.dialogBundle = mDialog.saveState();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(@Nullable Parcelable state) {
        if (!(state instanceof SavedState saved)) {
            super.onRestoreInstanceState(state);
            return;
        }
        super.onRestoreInstanceState(saved.getSuperState());
        showDialog(saved.dialogBundle);
    }

    private static class SavedState extends BaseSavedState {
        @Nullable Bundle dialogBundle;

        SavedState(Parcel source) {
            super(source);
            dialogBundle = source.readBundle(getClass().getClassLoader());
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeBundle(dialogBundle);
        }

        public static final Creator<SavedState> CREATOR = new Creator<>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /** For callers that push a color in from outside the dialog. */
    public void setNewPreviewColor(@ColorInt int color) {
        setColor(color);
    }

    public void setDefaultValue(@ColorInt int value) {
        mDefaultValue = applyAlphaPolicy(value);
    }

    public void setAlphaSliderEnabled(boolean enable) {
        mAlphaSliderEnabled = enable;
        if (mValueSet) {
            setColorInternal(mCurrentValue, false);
        }
    }

    public void setAutoSummaryEnabled(boolean enable) {
        mAutoSummary = enable;
    }

    @NonNull
    public static String convertToARGB(@ColorInt int color) {
        return String.format(Locale.US, "#%08X", color);
    }

    @NonNull
    public static String convertToRGB(@ColorInt int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }

    @ColorInt
    public static int convertToColorInt(@NonNull String argb) throws IllegalArgumentException {
        String value = argb.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }

        switch (value.length()) {
            case 3: // RGB
                value = "FF" + expandShorthand(value);
                break;
            case 4: // ARGB
                value = expandShorthand(value);
                break;
            case 6: // RRGGBB - opaque, never alpha 0
                value = "FF" + value;
                break;
            case 8: // AARRGGBB
                break;
            default:
                throw new IllegalArgumentException("Unsupported color string: " + argb);
        }

        try {
            return (int) Long.parseLong(value, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unknown color: " + argb, e);
        }
    }

    private static String expandShorthand(String value) {
        final StringBuilder sb = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            sb.append(value.charAt(i)).append(value.charAt(i));
        }
        return sb.toString();
    }
}
