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
import kotlin.math.sin

class DynamicBarChipSwipeDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class ChipType { RECORDING, MEDIA, TIMER }
    private data class DemoChip(val text: String, val accent: Int, val type: ChipType)

    private val demoChips = listOf(
        DemoChip("0:42", COLOR_RED, ChipType.RECORDING),
        DemoChip("", COLOR_PURPLE, ChipType.MEDIA),
        DemoChip("5:23", COLOR_BLUE, ChipType.TIMER),
    )

    private var swipePhase = 0f // 0..3

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dpToPx(2f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dpToPx(1.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val chipPath = Path()
    private val chipRect = RectF()

    private val swipeAnimator = ValueAnimator.ofFloat(0f, 3f).apply {
        duration = 4500
        interpolator = LinearInterpolator()
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            swipePhase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        swipeAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        swipeAnimator.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && !swipeAnimator.isStarted) swipeAnimator.start()
        else if (visibility != VISIBLE) swipeAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val chipH = dpToPx(24f)
        val iconSize = dpToPx(16f)
        val gap = dpToPx(4f)
        val padH = dpToPx(10f)

        val phase = swipePhase.toInt().coerceIn(0, 2)
        val frac = swipePhase - swipePhase.toInt()
        val current = demoChips[phase]
        val next = demoChips[(phase + 1) % 3]

        val slideOffset = frac * width * 0.4f
        drawSwipeChip(canvas, current, cx - slideOffset, cy, chipH, padH, iconSize, gap, 1f - frac, 1f)
        drawSwipeChip(canvas, next, cx + width * 0.4f - slideOffset, cy, chipH, padH, iconSize, gap, frac, 0.92f + 0.08f * frac)

        // Swipe arrow indicator
        val arrowAlpha = if (frac < 0.3f) frac / 0.3f else if (frac > 0.7f) (1f - frac) / 0.3f else 1f
        val arrowX = cx + chipH
        val arrowSize = dpToPx(6f)
        arrowPaint.alpha = (arrowAlpha * 0.4f * 255).toInt()
        canvas.drawLine(arrowX, cy - arrowSize, arrowX + arrowSize, cy, arrowPaint)
        canvas.drawLine(arrowX, cy + arrowSize, arrowX + arrowSize, cy, arrowPaint)
        arrowPaint.alpha = 255
    }

    private fun drawSwipeChip(
        canvas: Canvas, chip: DemoChip, centerX: Float, cy: Float,
        chipH: Float, padH: Float, iconSize: Float, gap: Float,
        alpha: Float, scale: Float
    ) {
        val waveBarCount = 4
        val waveBarW = dpToPx(2f)
        val waveBarGap = dpToPx(2f)
        val waveW = waveBarCount * (waveBarW + waveBarGap) - waveBarGap

        textPaint.alpha = (alpha * 255).toInt()

        val w = when (chip.type) {
            ChipType.RECORDING -> {
                val tw = textPaint.measureText(chip.text)
                padH + dpToPx(8f) + gap + tw + padH
            }
            ChipType.MEDIA -> padH + iconSize + gap + waveW + padH
            ChipType.TIMER -> {
                val tw = textPaint.measureText(chip.text)
                padH + iconSize + gap + tw + padH
            }
        }

        val h = chipH * scale
        val left = centerX - w / 2f
        val top = cy - h / 2f

        chipPaint.color = chip.accent
        chipPaint.alpha = (alpha * 255).toInt()
        chipRect.set(left, top, left + w, top + h)
        chipPath.reset()
        chipPath.addRoundRect(chipRect, h / 2f, h / 2f, Path.Direction.CW)
        canvas.drawPath(chipPath, chipPaint)

        var x = left + padH
        when (chip.type) {
            ChipType.RECORDING -> {
                dotPaint.alpha = (alpha * 0.8f * 255).toInt()
                canvas.drawCircle(x + dpToPx(4f), cy, dpToPx(4f) * scale, dotPaint)
                x += dpToPx(8f) + gap
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(chip.text, x, textY, textPaint)
            }
            ChipType.MEDIA -> {
                dotPaint.alpha = (alpha * 0.3f * 255).toInt()
                canvas.drawCircle(x + iconSize / 2f, cy, iconSize / 2f * scale, dotPaint)
                x += iconSize + gap
                val maxBarH = dpToPx(10f) * scale
                for (i in 0 until waveBarCount) {
                    val barH = maxBarH * (0.3f + 0.5f * if (i % 2 == 0) 0.8f else 0.4f)
                    val bx = x + i * (waveBarW + waveBarGap)
                    barPaint.alpha = (alpha * 0.85f * 255).toInt()
                    chipRect.set(bx, cy - barH / 2f, bx + waveBarW, cy + barH / 2f)
                    canvas.drawRoundRect(chipRect, waveBarW / 2f, waveBarW / 2f, barPaint)
                }
            }
            ChipType.TIMER -> {
                linePaint.alpha = (alpha * 255).toInt()
                drawHourglass(canvas, x + iconSize / 2f, cy, iconSize * 0.35f * scale, 0f)
                linePaint.alpha = 255
                x += iconSize + gap
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(chip.text, x, textY, textPaint)
            }
        }

        // Reset alphas
        textPaint.alpha = 255
        dotPaint.alpha = 255
        barPaint.alpha = 255
        chipPaint.alpha = 255
    }

    private fun drawHourglass(canvas: Canvas, cx: Float, cy: Float, r: Float, rotation: Float) {
        canvas.save()
        canvas.rotate(rotation, cx, cy)
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
