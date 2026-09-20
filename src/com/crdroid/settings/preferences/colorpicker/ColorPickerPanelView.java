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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A single color swatch: rounded Material 3 shape, checkerboard behind translucent colors,
 * hairline outline, and an optional check mark used to mark the active preset.
 */
public class ColorPickerPanelView extends View {

    private static final float DEFAULT_CORNER_DP = 12f;
    private static final float OUTLINE_WIDTH_DP = 1f;
    private static final float CHECK_STROKE_DP = 2f;

    private final Paint mColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCheckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mCheckPath = new Path();

    private AlphaPatternDrawable mAlphaPattern;
    private float mCornerRadius;
    private boolean mCircular;
    private boolean mChecked;

    @ColorInt private int mColor = Color.BLACK;
    @ColorInt private int mBorderColor;

    public ColorPickerPanelView(Context context) {
        this(context, null);
    }

    public ColorPickerPanelView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerPanelView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mCornerRadius = ColorPickerUtils.dp(context, DEFAULT_CORNER_DP);
        mBorderColor = ColorPickerUtils.resolveThemeColor(context,
                android.R.attr.colorControlNormal, 0xFF6E6E6E);

        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(ColorPickerUtils.dp(context, OUTLINE_WIDTH_DP));

        mCheckPaint.setStyle(Paint.Style.STROKE);
        mCheckPaint.setStrokeWidth(ColorPickerUtils.dp(context, CHECK_STROKE_DP));
        mCheckPaint.setStrokeCap(Paint.Cap.ROUND);
        mCheckPaint.setStrokeJoin(Paint.Join.ROUND);

        mAlphaPattern = new AlphaPatternDrawable((int) ColorPickerUtils.dp(context, 4f));
    }

    /** Draws the swatch as a circle (used by the preference widget). */
    public void setCircular(boolean circular) {
        if (mCircular != circular) {
            mCircular = circular;
            updateShape();
            invalidate();
        }
    }

    public void setCornerRadius(float radius) {
        if (!mCircular && mCornerRadius != radius) {
            mCornerRadius = radius;
            updateShape();
            invalidate();
        }
    }

    /** Marks this swatch as the currently selected preset. */
    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            invalidate();
        }
    }

    public boolean isChecked() {
        return mChecked;
    }

    public void setColor(@ColorInt int color) {
        if (mColor != color) {
            mColor = color;
            invalidate();
        }
    }

    @ColorInt
    public int getColor() {
        return mColor;
    }

    public void setBorderColor(@ColorInt int color) {
        if (mBorderColor != color) {
            mBorderColor = color;
            invalidate();
        }
    }

    @ColorInt
    public int getBorderColor() {
        return mBorderColor;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRect.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
        updateShape();
    }

    private void updateShape() {
        if (mRect.isEmpty()) {
            return;
        }
        final float radius = mCircular
                ? Math.min(mRect.width(), mRect.height()) / 2f
                : mCornerRadius;
        mAlphaPattern.setCornerRadius(radius);
        mAlphaPattern.setBounds(Math.round(mRect.left), Math.round(mRect.top),
                Math.round(mRect.right), Math.round(mRect.bottom));

        final float cx = mRect.centerX();
        final float cy = mRect.centerY();
        final float unit = Math.min(mRect.width(), mRect.height()) / 8f;
        mCheckPath.reset();
        mCheckPath.moveTo(cx - unit * 1.6f, cy);
        mCheckPath.lineTo(cx - unit * 0.4f, cy + unit * 1.2f);
        mCheckPath.lineTo(cx + unit * 1.7f, cy - unit * 1.2f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (mRect.isEmpty()) {
            return;
        }
        final float radius = mCircular
                ? Math.min(mRect.width(), mRect.height()) / 2f
                : mCornerRadius;

        if (Color.alpha(mColor) < 255) {
            mAlphaPattern.draw(canvas);
        }

        mColorPaint.setColor(mColor);
        canvas.drawRoundRect(mRect, radius, radius, mColorPaint);

        mOutlinePaint.setColor(mBorderColor);
        mOutlinePaint.setAlpha(0x3D);
        final float inset = mOutlinePaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(mRect.left + inset, mRect.top + inset,
                mRect.right - inset, mRect.bottom - inset, radius, radius, mOutlinePaint);

        if (mChecked) {
            mCheckPaint.setColor(ColorPickerUtils.contrastingColor(mColor));
            canvas.drawPath(mCheckPath, mCheckPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(isClickable());
        info.setChecked(mChecked);
    }
}
