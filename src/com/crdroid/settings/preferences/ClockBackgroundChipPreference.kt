/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.preferences

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import com.crdroid.settings.fragments.statusbar.ClockChipSettings
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ClockBackgroundChipPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private var dialog: AlertDialog? = null
    private var recyclerView: RecyclerView? = null

    private val pm: PackageManager = context.packageManager
    private val sysUiRes: Resources? = try {
        pm.getResourcesForApplication(SYSTEMUI_PACKAGE)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private val chipDrawables: Array<String> =
        context.resources.getStringArray(R.array.statusbar_clock_chip_drawables)

    private val chipLabels: Array<String> = run {
        val labels = context.resources.getStringArray(R.array.statusbar_clock_chip_labels)
        if (labels.size == chipDrawables.size) labels else chipDrawables
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary()
    }

    override fun onDetached() {
        dialog?.dismiss()
        dialog = null
        recyclerView = null
        super.onDetached()
    }

    override fun onClick() {
        val ctx = context
        val content = LayoutInflater.from(ctx).inflate(R.layout.picker_dialog, null)

        recyclerView = content.findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)

            val isLandscape = ctx.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            val span = if (isLandscape) 2 else 1
            layoutManager = GridLayoutManager(ctx, span)
            adapter = IconAdapter(getCurrentValue())

            post {
                val fraction = if (isLandscape) 0.75f else 0.6f
                val maxHeight = (ctx.resources.displayMetrics.heightPixels * fraction).toInt()
                layoutParams.height = maxHeight
                requestLayout()
            }
        }

        dialog = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dlg ->
                dlg.setOnDismissListener {
                    dialog = null
                    recyclerView = null
                }
                dlg.show()
                applyDialogWidth(dlg)
            }
    }

    private fun applyDialogWidth(dlg: AlertDialog) {
        val w = dlg.window ?: return
        val ctx = context

        val wm = ctx.getSystemService(WindowManager::class.java)

        val boundsWidthPx = try {
            wm.currentWindowMetrics.bounds.width()
        } catch (_: Throwable) {
            ctx.resources.displayMetrics.widthPixels
        }

        val density = ctx.resources.displayMetrics.density

        val maxDp = if (ctx.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE) {
            DIALOG_MAX_WIDTH_DP_LANDSCAPE
        } else {
            DIALOG_MAX_WIDTH_DP
        }
        val maxWidthPx = (maxDp * density + 0.5f).toInt()

        val targetPx = (boundsWidthPx * 0.90f).toInt()
        w.setLayout(min(maxWidthPx, targetPx), WindowManager.LayoutParams.WRAP_CONTENT)
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun getCurrentValue(): Int = readInt(SETTING_KEY, 0)

    private fun readInt(key: String, def: Int): Int =
        Settings.System.getIntForUser(context.contentResolver, key, def, UserHandle.USER_CURRENT)

    private fun updateSummary() {
        val label = labelForIndex(getCurrentValue()).orEmpty()
        summary = label
    }

    private fun labelForIndex(index: Int): String? {
        if (index < 0 || index >= chipLabels.size) return null
        return chipLabels[index]
    }

    private fun getChipDrawable(styleIndex: Int): Drawable? {
        val res = sysUiRes ?: return null
        if (styleIndex <= 0 || styleIndex >= chipDrawables.size) return null
        if (styleIndex == ClockChipSettings.CHIP_STYLE_CUSTOM_GRADIENT) return null

        val name = chipDrawables[styleIndex]
        val id = res.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE)
        if (id == 0) return null

        return try {
            res.getDrawable(id, context.theme)
        } catch (_: Throwable) {
            null
        }
    }

    private inner class IconAdapter(applied: Int) :
        RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

        private var selectedIndex: Int = clampIndex(applied)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.clock_option, parent, false)
            return IconViewHolder(v)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val styleIndex = position

            val clock = holder.clock
            // Views are recycled: clear any mask state left by the custom gradient row.
            clock.setMaskText(false)
            clock.setPadding(8, 2, 8, 2)
            clock.textAlignment = View.TEXT_ALIGNMENT_CENTER

            if (styleIndex == ClockChipSettings.CHIP_STYLE_CUSTOM_GRADIENT) {
                bindCustomGradient(clock)
            } else {
                clock.background = getChipDrawable(styleIndex)
                when (styleIndex) {
                    0, 2, 8 -> clock.setTextColor(
                        clock.context.getColorStateList(R.color.picker_option_text)
                    )
                    else -> clock.setTextColor(Color.WHITE)
                }
            }

            holder.name.visibility = View.VISIBLE
            holder.name.text = labelForIndex(styleIndex).orEmpty()

            holder.itemView.isActivated = (styleIndex == selectedIndex)

            holder.itemView.setOnClickListener {
                if (styleIndex == selectedIndex) {
                    dialog?.dismiss()
                    return@setOnClickListener
                }
                if (!this@ClockBackgroundChipPreference.callChangeListener(styleIndex)) {
                    return@setOnClickListener
                }

                val old = selectedIndex
                selectedIndex = styleIndex

                Settings.System.putIntForUser(
                    context.contentResolver,
                    SETTING_KEY,
                    styleIndex,
                    UserHandle.USER_CURRENT
                )

                summary = labelForIndex(styleIndex).orEmpty()

                notifyItemChanged(old)
                notifyItemChanged(selectedIndex)

                dialog?.dismiss()
            }
        }

        override fun getItemCount(): Int = chipDrawables.size

        /** Previews the custom gradient chip with the user's current colours, angle and text style. */
        private fun bindCustomGradient(clock: TextView) {
            val (start, end) = resolveGradientColors(clock.context)
            val angle = readInt(KEY_GRADIENT_ANGLE, ClockChipSettings.DEFAULT_GRADIENT_ANGLE)
            val mask = readInt(KEY_GRADIENT_MASK_TEXT,
                ClockChipSettings.GRADIENT_TEXT_STYLE_NORMAL) ==
                ClockChipSettings.GRADIENT_TEXT_STYLE_MASK

            clock.background = GradientChipPreviewDrawable(start, end, angle.toFloat())
            if (mask) {
                clock.setTextColor(Color.WHITE)
                clock.setMaskText(true)
            } else {
                clock.setTextColor(chipTextColorFor(start, end))
            }
        }

        private fun clampIndex(idx: Int): Int {
            if (idx < 0) return 0
            if (idx >= chipDrawables.size) return 0
            return idx
        }

        inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val clock: TextView = itemView.findViewById(R.id.option_clock)
            val name: TextView = itemView.findViewById(R.id.option_label)
        }
    }

    /** Same colour resolution as SystemUI's ClockChipGradientDrawable.resolveColors(). */
    private fun resolveGradientColors(ctx: Context): Pair<Int, Int> {
        val primary = ctx.getColor(com.android.internal.R.color.materialColorPrimary)
        val secondary = ctx.getColor(com.android.internal.R.color.materialColorSecondary)
        if (readInt(KEY_GRADIENT_COLOR_MODE, ClockChipSettings.COLOR_MODE_AUTO) !=
                ClockChipSettings.COLOR_MODE_CUSTOM) {
            return primary to secondary
        }
        val start = readInt(KEY_GRADIENT_START_COLOR,
            ClockChipSettings.DEFAULT_GRADIENT_START_COLOR)
        val end = readInt(KEY_GRADIENT_END_COLOR, ClockChipSettings.DEFAULT_GRADIENT_END_COLOR)
        return (if (start != 0) start else primary) to (if (end != 0) end else secondary)
    }

    /** Same punch-out as SystemUI's Clock mask mode: text erases the chip in its own layer. */
    private fun TextView.setMaskText(enabled: Boolean) {
        if (enabled) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            paint.xfermode = MASK_XFERMODE
        } else {
            paint.xfermode = null
            setLayerType(View.LAYER_TYPE_NONE, null)
        }
        invalidate()
    }

    /** Mirrors SystemUI's chip text colour choice: white or black, whichever contrasts better. */
    private fun chipTextColorFor(startColor: Int, endColor: Int): Int {
        val mid = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(startColor, endColor, 0.5f), 255)
        val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, mid)
        val blackContrast = ColorUtils.calculateContrast(Color.BLACK, mid)
        return if (whiteContrast >= blackContrast) Color.WHITE else Color.BLACK
    }

    /**
     * Preview copy of SystemUI's ClockChipGradientDrawable: pill shape, linear gradient using the
     * GradientDrawable angle convention (0° = left→right, counter-clockwise).
     */
    private class GradientChipPreviewDrawable(
        private val startColor: Int,
        private val endColor: Int,
        angleDeg: Float,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val angleRad = Math.toRadians((((angleDeg % 360f) + 360f) % 360f).toDouble())

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            rect.set(bounds)
            val w = rect.width()
            val h = rect.height()
            if (w <= 0f || h <= 0f) {
                paint.shader = null
                return
            }
            val dx = cos(angleRad).toFloat()
            val dy = -sin(angleRad).toFloat()
            val half = (abs(w * dx) + abs(h * dy)) / 2f
            val cx = rect.centerX()
            val cy = rect.centerY()
            paint.shader = LinearGradient(
                cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                startColor, endColor, Shader.TileMode.CLAMP,
            )
        }

        override fun draw(canvas: Canvas) {
            if (rect.isEmpty) return
            val radius = min(rect.width(), rect.height()) / 2f
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun getAlpha(): Int = paint.alpha

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val SETTING_KEY = "statusbar_clock_chip"
        private const val KEY_GRADIENT_START_COLOR = "statusbar_clock_chip_gradient_start_color"
        private const val KEY_GRADIENT_END_COLOR = "statusbar_clock_chip_gradient_end_color"
        private const val KEY_GRADIENT_ANGLE = "statusbar_clock_chip_gradient_angle"
        private const val KEY_GRADIENT_MASK_TEXT = "statusbar_clock_chip_gradient_mask_text"
        private const val KEY_GRADIENT_COLOR_MODE = "statusbar_clock_chip_gradient_color_mode"
        private val MASK_XFERMODE = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val DIALOG_MAX_WIDTH_DP = 360
        private const val DIALOG_MAX_WIDTH_DP_LANDSCAPE = 640
    }
}
