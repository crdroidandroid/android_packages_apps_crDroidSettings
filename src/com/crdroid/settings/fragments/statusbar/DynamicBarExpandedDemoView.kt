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
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class DynamicBarExpandedDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var timerProgress = 1f

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPath = Path()
    private val chipRect = RectF()
    private val arcRect = RectF()

    // Cached theme colors resolved once in init
    private val surfaceColor: Int
    private val outlineColor: Int
    private val primaryColor: Int
    private val onPrimaryColor: Int

    init {
        surfaceColor = resolveThemeColor(android.R.attr.colorBackgroundFloating, Color.WHITE)
        outlineColor = resolveThemeColor(android.R.attr.textColorSecondary, Color.LTGRAY)
        primaryColor = resolveThemeColor(android.R.attr.colorAccent, Color.BLUE)
        onPrimaryColor = resolveThemeColor(android.R.attr.textColorPrimaryInverse, Color.WHITE)
    }

    private val timerAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 12000
        interpolator = LinearInterpolator()
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            timerProgress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        timerAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        timerAnimator.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && !timerAnimator.isStarted) timerAnimator.start()
        else if (visibility != VISIBLE) timerAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cardW = w * 0.85f
        val cardH = h * 0.88f
        val cardLeft = (w - cardW) / 2f
        val cardTop = (h - cardH) / 2f
        val cardRadius = dpToPx(28f)

        // Card background
        chipRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, cardRadius, cardRadius, Path.Direction.CW)
        cardPaint.color = surfaceColor
        canvas.drawPath(chipPath, cardPaint)

        strokePaint.color = outlineColor
        strokePaint.alpha = (0.3f * 255).toInt()
        strokePaint.strokeWidth = dpToPx(1f)
        canvas.drawPath(chipPath, strokePaint)

        // Header area
        val headerH = cardH * 0.52f
        val headerRadius = dpToPx(24f)
        val headerPad = dpToPx(10f)
        chipRect.set(cardLeft + headerPad, cardTop + headerPad,
            cardLeft + cardW - headerPad, cardTop + headerPad + headerH)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, headerRadius, headerRadius, Path.Direction.CW)
        cardPaint.color = COLOR_BLUE
        cardPaint.alpha = (0.08f * 255).toInt()
        canvas.drawPath(chipPath, cardPaint)
        cardPaint.alpha = 255

        val headerCy = cardTop + headerPad + headerH / 2f
        val padX = cardLeft + headerPad + dpToPx(12f)

        // Timer ring
        val ringR = dpToPx(18f)
        val ringCx = padX + ringR
        cardPaint.color = COLOR_BLUE
        cardPaint.alpha = (0.1f * 255).toInt()
        canvas.drawCircle(ringCx, headerCy, ringR, cardPaint)
        cardPaint.alpha = 255

        strokePaint.color = COLOR_BLUE
        strokePaint.alpha = 255
        strokePaint.strokeWidth = dpToPx(3f)
        arcRect.set(ringCx - ringR, headerCy - ringR, ringCx + ringR, headerCy + ringR)
        canvas.drawArc(arcRect, -90f, 360f * timerProgress, false, strokePaint)

        // Timer icon inside ring
        val timerIcon = dpToPx(8f)
        strokePaint.strokeWidth = dpToPx(2f)
        canvas.drawLine(ringCx, headerCy - timerIcon * 0.6f, ringCx, headerCy, strokePaint)
        canvas.drawLine(ringCx, headerCy, ringCx + timerIcon * 0.4f, headerCy, strokePaint)

        // "Timer" label
        val textX = padX + ringR * 2 + dpToPx(12f)
        textPaint.color = Color.parseColor("#8E8E93")
        textPaint.textSize = dpToPx(10f)
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Timer", textX, headerCy - dpToPx(10f), textPaint)

        // Time text
        val mins = (timerProgress * 25).toInt()
        val secs = ((timerProgress * 25 * 60) % 60).toInt()
        textPaint.color = COLOR_BLUE
        textPaint.textSize = dpToPx(18f)
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText(String.format("%d:%02d", mins, secs), textX, headerCy + dpToPx(10f), textPaint)

        // "Running" status chip
        val chipW2 = dpToPx(56f)
        val chipH2 = dpToPx(20f)
        val chipLeft2 = cardLeft + cardW - headerPad - dpToPx(12f) - chipW2
        val chipTop2 = headerCy - chipH2 / 2f
        chipRect.set(chipLeft2, chipTop2, chipLeft2 + chipW2, chipTop2 + chipH2)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, chipH2 / 2f, chipH2 / 2f, Path.Direction.CW)
        cardPaint.color = COLOR_BLUE
        cardPaint.alpha = (0.15f * 255).toInt()
        canvas.drawPath(chipPath, cardPaint)
        cardPaint.alpha = 255

        textPaint.color = COLOR_BLUE
        textPaint.textSize = dpToPx(9f)
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val runningW = textPaint.measureText("Running")
        val textY = chipTop2 + chipH2 / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("Running", chipLeft2 + (chipW2 - runningW) / 2f, textY, textPaint)

        // Action buttons
        val btnY = cardTop + headerPad + headerH + dpToPx(10f)
        val btnH = dpToPx(28f)
        val btnGap = dpToPx(8f)
        val btnLeft = cardLeft + headerPad
        val btnW = (cardW - headerPad * 2 - btnGap) / 2f

        // Button 1: +1 min
        chipRect.set(btnLeft, btnY, btnLeft + btnW, btnY + btnH)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, btnH / 2f, btnH / 2f, Path.Direction.CW)
        cardPaint.color = primaryColor
        canvas.drawPath(chipPath, cardPaint)

        textPaint.color = onPrimaryColor
        textPaint.textSize = dpToPx(10f)
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val btn1W = textPaint.measureText("+1 min")
        val btn1Y = btnY + btnH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("+1 min", btnLeft + (btnW - btn1W) / 2f, btn1Y, textPaint)

        // Button 2: Cancel
        val btn2Left = btnLeft + btnW + btnGap
        chipRect.set(btn2Left, btnY, btn2Left + btnW, btnY + btnH)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, btnH / 2f, btnH / 2f, Path.Direction.CW)
        canvas.drawPath(chipPath, cardPaint)

        val btn2W = textPaint.measureText("Cancel")
        canvas.drawText("Cancel", btn2Left + (btnW - btn2W) / 2f, btn1Y, textPaint)
    }

    private fun resolveThemeColor(attr: Int, fallback: Int): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, fallback)
        typedArray.recycle()
        return color
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    companion object {
        private const val COLOR_BLUE = 0xFF0A84FF.toInt()
    }
}
