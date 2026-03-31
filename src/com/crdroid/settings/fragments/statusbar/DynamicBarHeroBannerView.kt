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
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

class DynamicBarHeroBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class ChipType { RECORDING, MEDIA, TIMER }

    private data class ChipDemo(val label: String, val accent: Int, val type: ChipType)

    private val chips = listOf(
        ChipDemo("0:42", COLOR_RED, ChipType.RECORDING),
        ChipDemo("", COLOR_PURPLE, ChipType.MEDIA),
        ChipDemo("5:23", COLOR_BLUE, ChipType.TIMER),
    )

    // Animation values
    private var dotAlpha = 1f
    private var chipCycleProgress = 0f  // 0..3
    private var wavePhase = 0f
    private var hourglassRotation = 0f

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dpToPx(1.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val chipPath = Path()
    private val chipRect = RectF()

    // Animators
    private val blinkAnimator = ValueAnimator.ofFloat(1f, 0.15f).apply {
        duration = 550
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            dotAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    private val cycleAnimator = ValueAnimator.ofFloat(0f, 2.99f).apply {
        duration = 6000
        interpolator = LinearInterpolator()
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            chipCycleProgress = it.animatedValue as Float
            invalidate()
        }
    }

    private val waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 900
        interpolator = LinearInterpolator()
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            wavePhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val hourglassAnimator = ValueAnimator.ofFloat(0f, 180f).apply {
        duration = 2000
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            hourglassRotation = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnimations() else stopAnimations()
    }

    private fun startAnimations() {
        if (!blinkAnimator.isStarted) blinkAnimator.start()
        if (!cycleAnimator.isStarted) cycleAnimator.start()
        if (!waveAnimator.isStarted) waveAnimator.start()
        if (!hourglassAnimator.isStarted) hourglassAnimator.start()
    }

    private fun stopAnimations() {
        blinkAnimator.cancel()
        cycleAnimator.cancel()
        waveAnimator.cancel()
        hourglassAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val chipH = dpToPx(24f)
        val chipPadH = dpToPx(10f)
        val iconSize = dpToPx(16f)
        val gap = dpToPx(4f)

        val activeIndex = chipCycleProgress.toInt().coerceIn(0, 2)
        val activeChip = chips[activeIndex]

        val waveBarCount = 4
        val waveBarW = dpToPx(2f)
        val waveBarGap = dpToPx(2f)
        val waveW = waveBarCount * (waveBarW + waveBarGap) - waveBarGap

        // Measure chip width
        val chipW = when (activeChip.type) {
            ChipType.RECORDING -> {
                val tw = textPaint.measureText(activeChip.label)
                chipPadH + dpToPx(8f) + gap + tw + chipPadH
            }
            ChipType.MEDIA -> {
                chipPadH + iconSize + gap + waveW + chipPadH
            }
            ChipType.TIMER -> {
                val tw = textPaint.measureText(activeChip.label)
                chipPadH + iconSize + gap + tw + chipPadH
            }
        }

        val chipLeft = cx - chipW / 2f
        val chipTop = cy - chipH / 2f

        // Draw chip background
        chipPaint.color = activeChip.accent
        chipRect.set(chipLeft, chipTop, chipLeft + chipW, chipTop + chipH)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, chipH / 2f, chipH / 2f, Path.Direction.CW)
        canvas.drawPath(chipPath, chipPaint)

        // Draw chip contents
        var contentX = chipLeft + chipPadH

        when (activeChip.type) {
            ChipType.RECORDING -> {
                dotPaint.alpha = (dotAlpha * 255).toInt()
                canvas.drawCircle(contentX + dpToPx(4f), cy, dpToPx(4f), dotPaint)
                dotPaint.alpha = 255
                contentX += dpToPx(8f) + gap
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(activeChip.label, contentX, textY, textPaint)
            }
            ChipType.MEDIA -> {
                // Icon circle
                dotPaint.alpha = (0.3f * 255).toInt()
                canvas.drawCircle(contentX + iconSize / 2f, cy, iconSize / 2f, dotPaint)
                dotPaint.alpha = 255
                contentX += iconSize + gap
                // Wave bars
                val maxBarH = dpToPx(10f)
                for (i in 0 until waveBarCount) {
                    val phase = wavePhase + i * 1.2f
                    val h = maxBarH * (0.22f + 0.56f * ((sin(phase) + 1f) / 2f))
                    val bx = contentX + i * (waveBarW + waveBarGap)
                    barPaint.alpha = (0.85f * 255).toInt()
                    chipRect.set(bx, cy - h / 2f, bx + waveBarW, cy + h / 2f)
                    canvas.drawRoundRect(chipRect, waveBarW / 2f, waveBarW / 2f, barPaint)
                }
                barPaint.alpha = 255
            }
            ChipType.TIMER -> {
                // Hourglass
                drawHourglass(canvas, contentX + iconSize / 2f, cy, iconSize * 0.35f, hourglassRotation)
                contentX += iconSize + gap
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(activeChip.label, contentX, textY, textPaint)
            }
        }

        // Draw indicator dots
        val indY = chipTop + chipH + dpToPx(10f)
        for (i in chips.indices) {
            val dotX = cx + (i - 1) * dpToPx(10f)
            val isCurrent = i == activeIndex
            indicatorPaint.color = if (isCurrent) activeChip.accent else Color.argb(51, 255, 255, 255)
            val r = if (isCurrent) dpToPx(3f) else dpToPx(2f)
            canvas.drawCircle(dotX, indY, r, indicatorPaint)
        }
    }

    private fun drawHourglass(canvas: Canvas, cx: Float, cy: Float, r: Float, rotation: Float) {
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        linePaint.color = Color.WHITE
        val top = cy - r
        val bot = cy + r
        val left = cx - r * 0.6f
        val right = cx + r * 0.6f
        canvas.drawLine(left, top, right, top, linePaint)
        canvas.drawLine(left, bot, right, bot, linePaint)
        canvas.drawLine(left, top, cx, cy, linePaint)
        canvas.drawLine(right, top, cx, cy, linePaint)
        canvas.drawLine(cx, cy, left, bot, linePaint)
        canvas.drawLine(cx, cy, right, bot, linePaint)
        canvas.restore()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    companion object {
        private const val COLOR_RED = 0xFFFF453A.toInt()
        private const val COLOR_PURPLE = 0xFFBF5AF2.toInt()
        private const val COLOR_BLUE = 0xFF0A84FF.toInt()
    }
}
