/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.statusbar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DynamicBarKeyguardDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var glowAlpha = 0.5f

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(12f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val chipPath = Path()
    private val chipRect = RectF()

    private val glowAnimator = ValueAnimator.ofFloat(0.5f, 1f).apply {
        duration = 800
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            glowAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glowAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && !glowAnimator.isStarted) glowAnimator.start()
        else if (visibility != VISIBLE) glowAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val chipH = dpToPx(36f)

        val timeText = "2:34"
        val timeW = textPaint.measureText(timeText)

        val padS = dpToPx(6f)
        val padE = dpToPx(8f)
        val dotR = dpToPx(5f)
        val gap = dpToPx(6f)
        val btnSize = dpToPx(24f)
        val btnGap = dpToPx(4f)

        val contentW = dotR * 2 + gap + timeW + gap + btnSize * 2 + btnGap
        val chipW = padS + contentW + padE

        val chipLeft = cx - chipW / 2f
        val chipTop = cy - chipH / 2f

        // Chip background
        chipPaint.color = COLOR_RED
        chipRect.set(chipLeft, chipTop, chipLeft + chipW, chipTop + chipH)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, chipH / 2f, chipH / 2f, Path.Direction.CW)
        canvas.drawPath(chipPath, chipPaint)

        var x = chipLeft + padS

        // Blinking recording dot
        dotPaint.alpha = (glowAlpha * 255).toInt()
        canvas.drawCircle(x + dotR, cy, dotR, dotPaint)
        dotPaint.alpha = 255
        x += dotR * 2 + gap

        // Time text
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(timeText, x, textY, textPaint)
        x += timeW + gap

        // Pause button
        btnBgPaint.color = Color.WHITE
        btnBgPaint.alpha = (0.2f * 255).toInt()
        canvas.drawCircle(x + btnSize / 2f, cy, btnSize / 2f, btnBgPaint)

        val pauseBarW = dpToPx(3f)
        val pauseBarH = dpToPx(10f)
        barPaint.color = Color.WHITE
        chipRect.set(
            x + btnSize / 2f - pauseBarW - dpToPx(1f),
            cy - pauseBarH / 2f,
            x + btnSize / 2f - dpToPx(1f),
            cy + pauseBarH / 2f
        )
        canvas.drawRoundRect(chipRect, dpToPx(1f), dpToPx(1f), barPaint)

        chipRect.set(
            x + btnSize / 2f + dpToPx(1f),
            cy - pauseBarH / 2f,
            x + btnSize / 2f + dpToPx(1f) + pauseBarW,
            cy + pauseBarH / 2f
        )
        canvas.drawRoundRect(chipRect, dpToPx(1f), dpToPx(1f), barPaint)

        x += btnSize + btnGap

        // Stop button
        canvas.drawCircle(x + btnSize / 2f, cy, btnSize / 2f, btnBgPaint)

        val stopSize = dpToPx(8f)
        chipRect.set(
            x + btnSize / 2f - stopSize / 2f,
            cy - stopSize / 2f,
            x + btnSize / 2f + stopSize / 2f,
            cy + stopSize / 2f
        )
        canvas.drawRoundRect(chipRect, dpToPx(2f), dpToPx(2f), barPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    companion object {
        private const val COLOR_RED = 0xFFFF453A.toInt()
    }
}
