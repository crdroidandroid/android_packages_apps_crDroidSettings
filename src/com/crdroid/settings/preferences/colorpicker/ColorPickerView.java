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
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * HSV color picker: a saturation/value square with horizontal hue and (optional) alpha bars
 * below it.
 */
public class ColorPickerView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(@ColorInt int color);
    }

    private static final int PANEL_NONE = -1;
    private static final int PANEL_SAT_VAL = 0;
    private static final int PANEL_HUE = 1;
    private static final int PANEL_ALPHA = 2;

    private static final float MAX_SIDE_DP = 320f;
    private static final float PANEL_CORNER_DP = 16f;
    private static final float BAR_HEIGHT_DP = 24f;
    private static final float SPACING_DP = 20f;
    private static final float THUMB_RADIUS_DP = 12f;
    private static final float THUMB_RING_DP = 3f;
    private static final float OUTLINE_WIDTH_DP = 1f;

    private static final String STATE_SUPER = "super";
    private static final String STATE_COLOR = "color";

    private final Paint mSatValPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAlphaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mSatValRect = new RectF();
    private final RectF mHueRect = new RectF();
    private final RectF mAlphaRect = new RectF();

    private final float mPanelCorner;
    private final float mBarHeight;
    private final float mBarCorner;
    private final float mSpacing;
    private final float mThumbRadius;
    private final float mThumbRing;

    private final AlphaPatternDrawable mAlphaPattern;

    private Shader mValShader;
    private Shader mSatShader;
    private Shader mHueShader;
    private float mShaderHue = Float.NaN;

    private float mHue = 0f;
    private float mSat = 1f;
    private float mVal = 1f;
    private int mAlpha = 0xFF;

    private boolean mShowAlphaPanel = false;
    private int mActivePanel = PANEL_NONE;

    @Nullable private OnColorChangedListener mListener;

    public ColorPickerView(Context context) {
        this(context, null);
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPanelCorner = ColorPickerUtils.dp(context, PANEL_CORNER_DP);
        mBarHeight = ColorPickerUtils.dp(context, BAR_HEIGHT_DP);
        mBarCorner = mBarHeight / 2f;
        mSpacing = ColorPickerUtils.dp(context, SPACING_DP);
        mThumbRadius = ColorPickerUtils.dp(context, THUMB_RADIUS_DP);
        mThumbRing = ColorPickerUtils.dp(context, THUMB_RING_DP);

        mAlphaPattern = new AlphaPatternDrawable((int) ColorPickerUtils.dp(context, 4f));
        mAlphaPattern.setCornerRadius(mBarCorner);

        mThumbRingPaint.setColor(Color.WHITE);
        mThumbRingPaint.setStyle(Paint.Style.FILL);

        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(ColorPickerUtils.dp(context, OUTLINE_WIDTH_DP));
        mOutlinePaint.setColor(ColorPickerUtils.resolveThemeColor(context,
                android.R.attr.colorControlNormal, 0xFF6E6E6E));
        mOutlinePaint.setAlpha(0x3D);

        setFocusable(true);
        setClickable(true);
    }

    public void setOnColorChangedListener(@Nullable OnColorChangedListener listener) {
        mListener = listener;
    }

    @ColorInt
    public int getColor() {
        final int alpha = mShowAlphaPanel ? mAlpha : 0xFF;
        return Color.HSVToColor(alpha, new float[]{mHue, mSat, mVal});
    }

    public void setColor(@ColorInt int color) {
        setColor(color, false);
    }

    public void setColor(@ColorInt int color, boolean callback) {
        color = ColorPickerUtils.applyAlphaPolicy(color, mShowAlphaPanel);

        final float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);

        mAlpha = Color.alpha(color);
        mHue = hsv[0];
        mSat = hsv[1];
        mVal = hsv[2];

        updateContentDescription();
        invalidate();

        if (callback && mListener != null) {
            mListener.onColorChanged(getColor());
        }
    }

    public void setAlphaSliderVisible(boolean visible) {
        if (mShowAlphaPanel == visible) {
            return;
        }
        mShowAlphaPanel = visible;
        if (!visible) {
            mAlpha = 0xFF;
        }
        requestLayout();
        invalidate();
    }

    public boolean isAlphaSliderVisible() {
        return mShowAlphaPanel;
    }

    /** Kept for source compatibility with the old dialog layout padding maths. */
    public float getDrawingOffset() {
        return mThumbRadius;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        final float maxSide = ColorPickerUtils.dp(getContext(), MAX_SIDE_DP);
        final float hPad = getPaddingLeft() + getPaddingRight() + mThumbRadius * 2f;
        final float vPad = getPaddingTop() + getPaddingBottom() + mThumbRadius * 2f;
        final float bars = mSpacing + mBarHeight + (mShowAlphaPanel ? mSpacing + mBarHeight : 0f);

        int width = (widthMode == MeasureSpec.UNSPECIFIED)
                ? Math.round(maxSide + hPad) : widthSize;
        float side = Math.min(width - hPad, maxSide);
        float height = side + bars + vPad;

        if (heightMode != MeasureSpec.UNSPECIFIED && height > heightSize) {
            height = heightSize;
            side = Math.max(0f, height - bars - vPad);
            if (widthMode != MeasureSpec.EXACTLY) {
                width = Math.round(side + hPad);
            }
        }
        setMeasuredDimension(width, Math.round(height));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        final float left = getPaddingLeft() + mThumbRadius;
        final float top = getPaddingTop() + mThumbRadius;
        final float right = w - getPaddingRight() - mThumbRadius;
        final float bottom = h - getPaddingBottom() - mThumbRadius;
        final float bars = mSpacing + mBarHeight + (mShowAlphaPanel ? mSpacing + mBarHeight : 0f);

        final float side = Math.max(0f, Math.min(right - left, bottom - top - bars));
        mSatValRect.set(left, top, left + side, top + side);

        float y = mSatValRect.bottom + mSpacing;
        mHueRect.set(left, y, left + side, y + mBarHeight);

        if (mShowAlphaPanel) {
            y = mHueRect.bottom + mSpacing;
            mAlphaRect.set(left, y, left + side, y + mBarHeight);
            mAlphaPattern.setBounds(Math.round(mAlphaRect.left), Math.round(mAlphaRect.top),
                    Math.round(mAlphaRect.right), Math.round(mAlphaRect.bottom));
        } else {
            mAlphaRect.setEmpty();
        }

        mValShader = null;
        mHueShader = null;
        mSatShader = null;
        mShaderHue = Float.NaN;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (mSatValRect.isEmpty()) {
            return;
        }
        drawSatValPanel(canvas);
        drawHueBar(canvas);
        if (mShowAlphaPanel && !mAlphaRect.isEmpty()) {
            drawAlphaBar(canvas);
        }
    }

    private void drawSatValPanel(Canvas canvas) {
        if (mValShader == null) {
            mValShader = new LinearGradient(0f, mSatValRect.top, 0f, mSatValRect.bottom,
                    Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);
            mShaderHue = Float.NaN;
        }
        if (mSatShader == null || mShaderHue != mHue) {
            mShaderHue = mHue;
            mSatShader = new LinearGradient(mSatValRect.left, 0f, mSatValRect.right, 0f,
                    Color.WHITE, Color.HSVToColor(new float[]{mHue, 1f, 1f}), Shader.TileMode.CLAMP);
            mSatValPaint.setShader(
                    new ComposeShader(mValShader, mSatShader, PorterDuff.Mode.MULTIPLY));
        }

        canvas.drawRoundRect(mSatValRect, mPanelCorner, mPanelCorner, mSatValPaint);
        strokeOutline(canvas, mSatValRect, mPanelCorner);

        final float cx = mSatValRect.left + mSat * mSatValRect.width();
        final float cy = mSatValRect.top + (1f - mVal) * mSatValRect.height();
        drawThumb(canvas, cx, cy, Color.HSVToColor(new float[]{mHue, mSat, mVal}));
    }

    private void drawHueBar(Canvas canvas) {
        if (mHueShader == null) {
            mHueShader = new LinearGradient(mHueRect.left, 0f, mHueRect.right, 0f,
                    buildHueColors(), null, Shader.TileMode.CLAMP);
            mHuePaint.setShader(mHueShader);
        }
        canvas.drawRoundRect(mHueRect, mBarCorner, mBarCorner, mHuePaint);
        strokeOutline(canvas, mHueRect, mBarCorner);

        final float cx = mHueRect.left + (mHue / 360f) * mHueRect.width();
        drawThumb(canvas, cx, mHueRect.centerY(), Color.HSVToColor(new float[]{mHue, 1f, 1f}));
    }

    private void drawAlphaBar(Canvas canvas) {
        mAlphaPattern.draw(canvas);

        final float[] hsv = new float[]{mHue, mSat, mVal};
        final int opaque = Color.HSVToColor(0xFF, hsv);
        final int transparent = Color.HSVToColor(0x00, hsv);
        mAlphaPaint.setShader(new LinearGradient(mAlphaRect.left, 0f, mAlphaRect.right, 0f,
                transparent, opaque, Shader.TileMode.CLAMP));

        canvas.drawRoundRect(mAlphaRect, mBarCorner, mBarCorner, mAlphaPaint);
        strokeOutline(canvas, mAlphaRect, mBarCorner);

        final float cx = mAlphaRect.left + (mAlpha / 255f) * mAlphaRect.width();
        drawThumb(canvas, cx, mAlphaRect.centerY(), Color.HSVToColor(mAlpha, hsv));
    }

    private void strokeOutline(Canvas canvas, RectF rect, float radius) {
        final float inset = mOutlinePaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(rect.left + inset, rect.top + inset,
                rect.right - inset, rect.bottom - inset, radius, radius, mOutlinePaint);
    }

    private void drawThumb(Canvas canvas, float cx, float cy, @ColorInt int color) {
        canvas.drawCircle(cx, cy, mThumbRadius, mThumbRingPaint);
        mThumbFillPaint.setColor(color | ColorPickerUtils.OPAQUE_MASK);
        canvas.drawCircle(cx, cy, mThumbRadius - mThumbRing, mThumbFillPaint);
        canvas.drawCircle(cx, cy, mThumbRadius - mOutlinePaint.getStrokeWidth() / 2f, mOutlinePaint);
    }

    private static int[] buildHueColors() {
        final int[] hue = new int[361];
        for (int i = 0; i < hue.length; i++) {
            hue[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
        }
        return hue;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mActivePanel = panelAt(x, y);
                if (mActivePanel == PANEL_NONE) {
                    return false;
                }
                setParentInterceptDisallowed(true);
                update(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mActivePanel == PANEL_NONE) {
                    return false;
                }
                update(x, y);
                return true;

            case MotionEvent.ACTION_UP:
                if (mActivePanel != PANEL_NONE) {
                    update(x, y);
                    performClick();
                }
                mActivePanel = PANEL_NONE;
                setParentInterceptDisallowed(false);
                return true;

            case MotionEvent.ACTION_CANCEL:
                mActivePanel = PANEL_NONE;
                setParentInterceptDisallowed(false);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void setParentInterceptDisallowed(boolean disallow) {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private int panelAt(float x, float y) {
        if (contains(mSatValRect, x, y, mThumbRadius)) {
            return PANEL_SAT_VAL;
        }
        if (contains(mHueRect, x, y, mThumbRadius)) {
            return PANEL_HUE;
        }
        if (mShowAlphaPanel && contains(mAlphaRect, x, y, mThumbRadius)) {
            return PANEL_ALPHA;
        }
        return PANEL_NONE;
    }

    private static boolean contains(RectF rect, float x, float y, float slop) {
        return !rect.isEmpty()
                && x >= rect.left - slop && x <= rect.right + slop
                && y >= rect.top - slop && y <= rect.bottom + slop;
    }

    private void update(float x, float y) {
        switch (mActivePanel) {
            case PANEL_SAT_VAL:
                mSat = clamp((x - mSatValRect.left) / mSatValRect.width(), 0f, 1f);
                mVal = 1f - clamp((y - mSatValRect.top) / mSatValRect.height(), 0f, 1f);
                break;
            case PANEL_HUE:
                mHue = clamp((x - mHueRect.left) / mHueRect.width(), 0f, 1f) * 360f;
                break;
            case PANEL_ALPHA:
                mAlpha = Math.round(clamp((x - mAlphaRect.left) / mAlphaRect.width(), 0f, 1f) * 255f);
                break;
            default:
                return;
        }
        updateContentDescription();
        invalidate();
        if (mListener != null) {
            mListener.onColorChanged(getColor());
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateContentDescription() {
        setContentDescription(String.format(Locale.US, "#%08X", getColor()));
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_SUPER, super.onSaveInstanceState());
        state.putInt(STATE_COLOR, getColor());
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle bundle) {
            super.onRestoreInstanceState(bundle.getParcelable(STATE_SUPER, Parcelable.class));
            setColor(bundle.getInt(STATE_COLOR, Color.BLACK), false);
            return;
        }
        super.onRestoreInstanceState(state);
    }
}
