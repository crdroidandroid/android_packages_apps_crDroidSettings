/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings.fragments.lockscreen

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

class UdfpsIconPicker : SettingsPreferenceFragment() {

    private var udfpsPkg = "com.crdroid.udfps.icons"

    private var udfpsCtx: Context? = null
    private var udfpsRes: Resources? = null

    private val items = ArrayList<IconItem>(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().setTitle(R.string.udfps_icon_picker_title)
        loadResources()
    }

    private fun loadResources() {
        items.clear()
        val host = activity ?: return

        try {
            val pkgCtx = host.createPackageContext(udfpsPkg, Context.CONTEXT_IGNORE_SECURITY)
            val res = pkgCtx.resources

            val arrayId = res.getIdentifier("udfps_icons", "array", udfpsPkg)
            if (arrayId == 0) {
                udfpsCtx = null
                udfpsRes = null
                return
            }

            val iconNames = res.getStringArray(arrayId)
            udfpsCtx = pkgCtx
            udfpsRes = res

            iconNames.forEachIndexed { index, name ->
                val id = res.getIdentifier(name, "drawable", udfpsPkg)
                items.add(
                    IconItem(
                        index = index,
                        name = name,
                        drawableId = id
                    )
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            udfpsCtx = null
            udfpsRes = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.item_view, container, false)
        val rv = view.findViewById<RecyclerView>(R.id.recycler_view)

        val spanCount = if (resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) 4 else 3

        rv.layoutManager = GridLayoutManager(requireContext(), spanCount)

        val adapter = UdfpsIconAdapter(
            contentResolver = requireContext().contentResolver,
            iconLoader = { item -> getIconDrawable(item.drawableId) }
        )
        rv.adapter = adapter
        adapter.submitList(items.toList())

        return view
    }

    private fun getIconDrawable(@DrawableRes id: Int): Drawable? {
        val c = udfpsCtx ?: return null
        if (id == 0) return null
        return try {
            c.getDrawable(id)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    override fun getMetricsCategory(): Int = MetricsEvent.CRDROID_SETTINGS

    companion object {
        @JvmStatic
        fun reset(context: Context) {
            val resolver: ContentResolver = context.contentResolver
            Settings.System.putIntForUser(
                resolver,
                Settings.System.UDFPS_ICON,
                0,
                UserHandle.USER_CURRENT
            )
        }
    }

    private data class IconItem(
        val index: Int,
        val name: String,
        @DrawableRes val drawableId: Int
    )

    private class UdfpsIconAdapter(
        private val contentResolver: ContentResolver,
        private val iconLoader: (IconItem) -> Drawable?
    ) : ListAdapter<IconItem, UdfpsIconAdapter.VH>(DIFF) {

        private var selectedPos: Int = -1

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = getItem(position).index.toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_option, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)

            if (selectedPos == -1) {
                selectedPos = Settings.System.getIntForUser(
                    contentResolver,
                    Settings.System.UDFPS_ICON,
                    0,
                    UserHandle.USER_CURRENT
                )
            }

            holder.image.setImageDrawable(iconLoader(item))
            holder.image.setPadding(20, 20, 20, 20)
            holder.name.visibility = View.GONE

            holder.itemView.isActivated = (position == selectedPos)

            holder.itemView.setOnClickListener {
                val old = selectedPos
                selectedPos = position
                if (old != -1) notifyItemChanged(old)
                notifyItemChanged(selectedPos)

                Settings.System.putIntForUser(
                    contentResolver,
                    Settings.System.UDFPS_ICON,
                    position,
                    UserHandle.USER_CURRENT
                )
            }
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.option_label)
            val image: ImageView = itemView.findViewById(R.id.option_thumbnail)
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<IconItem>() {
                override fun areItemsTheSame(oldItem: IconItem, newItem: IconItem): Boolean {
                    return oldItem.index == newItem.index
                }

                override fun areContentsTheSame(oldItem: IconItem, newItem: IconItem): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }
}
