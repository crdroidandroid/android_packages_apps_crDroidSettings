/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.preferences

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.util.Log
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
import com.android.internal.util.crdroid.ThemeUtils
import com.android.settings.R
import com.crdroid.settings.utils.SystemUtils
import java.util.concurrent.Executors
import kotlin.math.min

class LockClockFontsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private var dialog: AlertDialog? = null
    private var recyclerView: RecyclerView? = null

    private val themeUtils = ThemeUtils(context)
    private val pkgs: List<String> = themeUtils.getOverlayPackagesForCategory(CATEGORY, "android")

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
        val content = LayoutInflater.from(ctx).inflate(R.layout.selector_item_view, null)

        recyclerView = content.findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)

            val isLandscape = ctx.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            val span = if (isLandscape) 2 else 1
            layoutManager = GridLayoutManager(ctx, span)
            adapter = FontsAdapter(ctx, pkgs, themeUtils)

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

    private fun getAppliedPkg(): String {
        return themeUtils.getOverlayInfos(CATEGORY)
            .firstOrNull { it.isEnabled }?.packageName ?: "android"
    }

    private fun updateSummary() {
        val applied = getAppliedPkg()
        summary = if (applied == "android") {
            "Default"
        } else {
            getLabelSafe(context, applied)
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

    private inner class FontsAdapter(
        private val ctx: Context,
        private val pkgs: List<String>,
        private val themeUtils: ThemeUtils
    ) : RecyclerView.Adapter<FontsAdapter.FontsViewHolder>() {

        private var selectedPkg: String = getApplied(themeUtils)
        private val appliedPkg: String = selectedPkg

        private val overlayExecutor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontsViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.clock_font_option, parent, false)
            return FontsViewHolder(v)
        }

        override fun onBindViewHolder(holder: FontsViewHolder, position: Int) {
            val pkg = pkgs[position]
            val label = getLabelSafe(ctx, pkg)
            val tf = getTypefaceSafe(ctx, pkg)

            holder.clock.setPadding(8, 2, 8, 2)
            holder.clock.textAlignment = View.TEXT_ALIGNMENT_CENTER
            if (tf != null) holder.clock.typeface = tf
            holder.clock.setTextColor(resolveTextColorPrimary(holder.clock.context))

            holder.name.visibility = View.VISIBLE
            holder.name.text = if (pkg == "android") "Default" else label

            holder.itemView.isActivated = (pkg == selectedPkg)

            holder.itemView.setOnClickListener {
                val pkg = pkgs[position]
                if (pkg == selectedPkg) return@setOnClickListener

                val old = selectedPkg
                val pending = pkg

                selectedPkg = pending
                notifyItemChanged(pkgs.indexOf(old).takeIf { it >= 0 } ?: 0)
                notifyItemChanged(position)

                dialog?.dismiss()

                showSystemUiRestartDialogWithAction(ctx,
                    onConfirm = {
                        applyOverlayInBackground(old, pending) {
                            SystemUtils.restartSystemUI(ctx)
                        }
                    },
                    onLater = {
                        mainHandler.post {
                            themeUtils.setOverlayEnabled(CATEGORY, old, old)
                            themeUtils.setOverlayEnabled(CATEGORY, pending, "android")
                        }
                    }
                )
            }
        }

        override fun getItemCount(): Int = pkgs.size

        private fun applyOverlayInBackground(
            oldPkg: String,
            newPkg: String,
            onDone: () -> Unit
        ) {
            overlayExecutor.execute {
                try {
                    themeUtils.setOverlayEnabled(CATEGORY, oldPkg, oldPkg)
                    themeUtils.setOverlayEnabled(CATEGORY, newPkg, "android")
                } finally {
                    mainHandler.post { onDone() }
                }
            }
        }

        private fun showSystemUiRestartDialogWithAction(
            ctx: Context,
            onConfirm: () -> Unit,
            onLater: () -> Unit
        ) {
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.systemui_restart_title)
                .setMessage(R.string.systemui_restart_message)
                .setPositiveButton(R.string.systemui_restart_yes) { _, _ ->
                    onConfirm()
                }
                .setNegativeButton(R.string.systemui_restart_not_now) { _, _ ->
                    onLater()
                }
                .show()
        }

        private fun resolveTextColorPrimary(ctx: Context): Int {
            val tv = TypedValue()
            return if (ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
                if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
            } else {
                0xff000000.toInt()
            }
        }

        inner class FontsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val clock: TextView = itemView.findViewById(R.id.option_clock)
            val name: TextView = itemView.findViewById(R.id.option_label)
        }

        private fun getTypefaceSafe(context: Context, pkg: String): Typeface? {
            return try {
                val pm = context.packageManager
                val res = if (pkg == "android") Resources.getSystem() else pm.getResourcesForApplication(pkg)
                val id = res.getIdentifier("config_clockFontFamily", "string", pkg)
                if (id == 0) return null
                Typeface.create(res.getString(id), Typeface.NORMAL)
            } catch (e: Exception) {
                Log.e(TAG, "Typeface load failed for pkg: $pkg", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "LockClockFontsPreference"
        private const val CATEGORY = "android.theme.customization.lockscreen_clock_font"
        private const val DIALOG_MAX_WIDTH_DP = 360
        private const val DIALOG_MAX_WIDTH_DP_LANDSCAPE = 640

        private fun getApplied(themeUtils: ThemeUtils): String {
            return themeUtils.getOverlayInfos(CATEGORY)
                .firstOrNull { it.isEnabled }?.packageName ?: "android"
        }

        private fun getLabelSafe(context: Context, pkg: String): String {
            return try {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }
}
