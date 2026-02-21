/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings.fragments.lockscreen

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.AnimationDrawable
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

class UdfpsAnimation : SettingsPreferenceFragment() {

    private var udfpsPkg = "com.crdroid.udfps.animations"
    private var udfpsRes: Resources? = null

    private val items = ArrayList<AnimItem>(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().setTitle(R.string.udfps_recog_animation_effect_title)
        loadResources()
    }

    private fun loadResources() {
        items.clear()
        val host = activity ?: return

        udfpsRes = try {
            host.packageManager.getResourcesForApplication(udfpsPkg)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        val res = udfpsRes ?: return

        val stylesId = res.getIdentifier("udfps_animation_styles", "array", udfpsPkg)
        val previewsId = res.getIdentifier("udfps_animation_previews", "array", udfpsPkg)
        val titlesId = res.getIdentifier("udfps_animation_titles", "array", udfpsPkg)

        if (stylesId == 0 || previewsId == 0 || titlesId == 0) return

        val styles = res.getStringArray(stylesId)
        val previews = res.getStringArray(previewsId)
        val titles = res.getStringArray(titlesId)

        val count = minOf(styles.size, previews.size, titles.size)
        for (i in 0 until count) {
            items.add(
                AnimItem(
                    index = i,
                    animDrawableName = styles[i],
                    previewDrawableName = previews[i],
                    title = titles[i]
                )
            )
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

        val adapter = UdfpsAnimAdapter(
            contentResolver = requireContext().contentResolver,
            drawableLoader = { name -> getDrawableByName(name) }
        )
        rv.adapter = adapter
        adapter.submitList(items.toList())

        return view
    }

    override fun getMetricsCategory(): Int = MetricsEvent.CRDROID_SETTINGS

    private fun getDrawableByName(drawableName: String): Drawable? {
        val res = udfpsRes ?: return null
        val id = res.getIdentifier(drawableName, "drawable", udfpsPkg)
        if (id == 0) return null
        return try {
            res.getDrawable(id, requireContext().theme)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        @JvmStatic
        fun reset(context: Context) {
            val resolver: ContentResolver = context.contentResolver
            Settings.System.putIntForUser(
                resolver,
                Settings.System.UDFPS_ANIM_STYLE,
                0,
                UserHandle.USER_CURRENT
            )
        }
    }

    private data class AnimItem(
        val index: Int,
        val animDrawableName: String,
        val previewDrawableName: String,
        val title: String
    )

    private class UdfpsAnimAdapter(
        private val contentResolver: ContentResolver,
        private val drawableLoader: (String) -> Drawable?
    ) : ListAdapter<AnimItem, UdfpsAnimAdapter.VH>(DIFF) {

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
                    Settings.System.UDFPS_ANIM_STYLE,
                    0,
                    UserHandle.USER_CURRENT
                )
            }

            holder.image.setImageDrawable(drawableLoader(item.previewDrawableName))
            holder.name.text = item.title

            holder.itemView.isActivated = (position == selectedPos)

            holder.itemView.setOnClickListener {
                val old = selectedPos
                selectedPos = position
                if (old != -1) notifyItemChanged(old)
                notifyItemChanged(selectedPos)

                val bg = drawableLoader(item.animDrawableName)
                holder.image.background = bg
                (bg as? AnimationDrawable)?.let { ad ->
                    ad.isOneShot = true
                    ad.stop()
                    ad.start()
                }

                Settings.System.putIntForUser(
                    contentResolver,
                    Settings.System.UDFPS_ANIM_STYLE,
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
            val DIFF = object : DiffUtil.ItemCallback<AnimItem>() {
                override fun areItemsTheSame(oldItem: AnimItem, newItem: AnimItem): Boolean {
                    return oldItem.index == newItem.index
                }

                override fun areContentsTheSame(oldItem: AnimItem, newItem: AnimItem): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }
}
