/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.preferences

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import kotlin.math.min

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
        val content = LayoutInflater.from(ctx).inflate(R.layout.item_view, null)

        recyclerView = content.findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)

            val span = if (ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                2
            } else {
                1
            }

            layoutManager = GridLayoutManager(ctx, span)
            adapter = IconAdapter(getCurrentValue())
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
                tintDialogAccent(dlg)
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

    private fun getCurrentValue(): Int {
        return Settings.System.getIntForUser(
            context.contentResolver,
            SETTING_KEY,
            0,
            UserHandle.USER_CURRENT
        )
    }

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

        val name = chipDrawables[styleIndex]
        val id = res.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE)
        if (id == 0) return null

        return try {
            res.getDrawable(id, context.theme)
        } catch (_: Throwable) {
            null
        }
    }

    private fun tintDialogAccent(dlg: AlertDialog) {
        val tv = TypedValue()
        val theme = context.theme
        if (!theme.resolveAttribute(android.R.attr.colorAccent, tv, true)) return

        val accent = if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
        if (accent == 0) return

        val w = dlg.window
        if (w != null) {
            val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
            val titleView = if (titleId != 0) w.decorView.findViewById<TextView>(titleId) else null
            titleView?.setTextColor(accent)
        }

        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
    }

    private inner class IconAdapter(applied: Int) :
        RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

        private var selectedIndex: Int = clampIndex(applied)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.clock_chip_option, parent, false)
            return IconViewHolder(v)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val styleIndex = position

            holder.clock.background = getChipDrawable(styleIndex)
            holder.clock.setPadding(8, 2, 8, 2)
            holder.clock.textAlignment = View.TEXT_ALIGNMENT_CENTER

            if (styleIndex != 0 && styleIndex != 2 && styleIndex != 8) {
                holder.clock.setTextColor(Color.WHITE)
            } else {
                holder.clock.setTextColor(resolveTextColorPrimary(holder.clock.context))
            }

            holder.name.visibility = View.VISIBLE
            holder.name.text = labelForIndex(styleIndex).orEmpty()

            holder.itemView.isActivated = (styleIndex == selectedIndex)

            holder.itemView.setOnClickListener {
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

        private fun clampIndex(idx: Int): Int {
            if (idx < 0) return 0
            if (idx >= chipDrawables.size) return 0
            return idx
        }

        private fun resolveTextColorPrimary(ctx: Context): Int {
            val tv = TypedValue()
            return if (ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
                if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
            } else {
                0xff000000.toInt()
            }
        }

        inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val clock: TextView = itemView.findViewById(R.id.option_clock)
            val name: TextView = itemView.findViewById(R.id.option_label)
        }
    }

    companion object {
        private const val SETTING_KEY = "statusbar_clock_chip"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val DIALOG_MAX_WIDTH_DP = 360
        private const val DIALOG_MAX_WIDTH_DP_LANDSCAPE = 640
    }
}
