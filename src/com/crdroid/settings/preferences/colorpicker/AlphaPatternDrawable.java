/*
 * Copyright (C) 2010 Daniel Nilsson
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

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

/**
 * Chessboard pattern drawn behind translucent colors.
 */
public class AlphaPatternDrawable extends Drawable {

    private static final int COLOR_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_DARK = 0xFFCBCBCB;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mLocalMatrix = new Matrix();
    private final Shader mShader;

    private float mCornerRadius = 0f;

    public AlphaPatternDrawable(@Px int cellSize) {
        mShader = createTileShader(Math.max(1, cellSize));
        mPaint.setShader(mShader);
    }

    /** Rounds the pattern so it can be clipped to a Material shape (pass size/2f for a circle). */
    public void setCornerRadius(float radius) {
        if (mCornerRadius != radius) {
            mCornerRadius = radius;
            invalidateSelf();
        }
    }

    private static Shader createTileShader(int cell) {
        final Bitmap tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(tile);
        canvas.drawColor(COLOR_LIGHT);

        final Paint dark = new Paint();
        dark.setColor(COLOR_DARK);
        canvas.drawRect(0, 0, cell, cell, dark);
        canvas.drawRect(cell, cell, cell * 2f, cell * 2f, dark);

        return new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        // Anchor the tile to the drawable, not to the canvas origin.
        mLocalMatrix.setTranslate(bounds.left, bounds.top);
        mShader.setLocalMatrix(mLocalMatrix);

        if (mCornerRadius > 0f) {
            canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    mCornerRadius, mCornerRadius, mPaint);
        } else {
            canvas.drawRect(bounds, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mPaint.getAlpha() != alpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return mPaint.getAlpha();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return mPaint.getAlpha() == 255 && mCornerRadius == 0f
                ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
    }
}
