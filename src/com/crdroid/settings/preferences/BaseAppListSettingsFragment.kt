/*
 * SPDX-FileCopyrightText: 2021 AOSP-Krypton Project
 * SPDX-FileCopyrightText: 2022 Nameless-AOSP Project
 * SPDX-FileCopyrightText: 2022, 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.preferences

import android.app.ActivityManager
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import com.google.android.material.appbar.AppBarLayout

abstract class BaseAppListSettingsFragment : Fragment(R.layout.app_list_layout) {

    private lateinit var activityManager: ActivityManager
    private lateinit var packageManager: PackageManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var launcherApps: LauncherApps
    private lateinit var loadingView: View
    private var isLoading = true
    private val handlerThread = HandlerThread(TAG).apply { start() }
    private val bgHandler = Handler(handlerThread.looper)
    private val appBarLayout: AppBarLayout by lazy {
        requireActivity().findViewById(R.id.app_bar)!!
    }
    private var searchText = ""

    protected open fun excludeSystemApps(): Boolean = true
    protected open fun restartPackageOnChange(): Boolean = true
    protected open fun appFilter(info: LauncherActivityInfo): Boolean = true

    @StringRes
    abstract fun getTitleResId(): Int

    /** @return an initial list of packages that should appear as selected. */
    abstract fun getInitialCheckedList(): List<String>

    /**
     * Called when user selects an item.
     *
     * @param list a [List<String>] of selected items.
     */
    abstract fun onListUpdate(packageName: String, isChecked: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().setTitle(getTitleResId())
        activityManager = requireContext().getSystemService(ActivityManager::class.java)!!
        packageManager = requireContext().packageManager
        launcherApps = requireContext().getSystemService(LauncherApps::class.java)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AppListAdapter()
        recyclerView =
            view.findViewById<RecyclerView>(R.id.apps_list)!!.also {
                it.layoutManager = LinearLayoutManager(context)
                it.adapter = adapter
            }
        loadingView = view.findViewById(R.id.apps_loading)!!
        bgHandler.post { refreshList() }
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerThread.quitSafely()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.app_list_menu, menu)
        val searchMenuItem =
            (menu.findItem(R.id.search) as MenuItem).apply {
                setOnActionExpandListener(
                    object : MenuItem.OnActionExpandListener {
                        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                            // To prevent a large space on tool bar.
                            appBarLayout.setExpanded(false /*expanded*/, false /*animate*/)
                            // To prevent user can expand the collapsing tool bar view.
                            ViewCompat.setNestedScrollingEnabled(recyclerView, false)
                            return true
                        }

                        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                            // We keep the collapsed status after user cancel the search function.
                            appBarLayout.setExpanded(false /*expanded*/, false /*animate*/)
                            ViewCompat.setNestedScrollingEnabled(recyclerView, true)
                            return true
                        }
                    }
                )
            }
        (searchMenuItem.actionView as SearchView).apply {
            queryHint = getString(R.string.app_list_search_apps)
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String) = false

                    override fun onQueryTextChange(newText: String): Boolean {
                        searchText = newText
                        refreshList()
                        return true
                    }
                }
            )
        }
    }

    private fun refreshList() {
        if (!::adapter.isInitialized) return
        val initialList = getInitialCheckedList()
        bgHandler.post {
            val list =
                launcherApps
                    .getActivityList(null, Process.myUserHandle())
                    .distinctBy { it.componentName.packageName } // filter out duplicates
                    .filter {
                        (!excludeSystemApps() || !it.applicationInfo!!.isSystemApp()) &&
                            it.label.contains(searchText, ignoreCase = true) &&
                            appFilter(it)
                    }
                    .map { it.toAppInfo() }
                    .sortedWith(
                        compareBy(
                            { it.packageName !in initialList }, // checked items first
                            { it.label.lowercase() } // sort case insensitive
                        )
                    )
            Log.d(TAG, "refreshList: ${list.size} items")
            requireActivity().runOnUiThread {
                if (isLoading) {
                    isLoading = false
                    loadingView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                adapter.submitList(list)
            }
        }
    }

    private fun LauncherActivityInfo.toAppInfo() =
        AppInfo(
            packageName = componentName.packageName,
            label = label.toString(),
            icon = getIcon(0)
        )

    private inner class AppListAdapter : ListAdapter<AppInfo, AppListViewHolder>(itemCallback) {
        private val selectedIndices = mutableSetOf<Int>()
        private var initialList = getInitialCheckedList().toMutableList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppListViewHolder(
                layoutInflater.inflate(R.layout.app_list_item, parent, false)
            )

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
            val appInfo = getItem(position) ?: return
            holder.apply {
                packageName = appInfo.packageName
                label.text = appInfo.label
                icon.setImageDrawable(appInfo.icon)
                itemView.setOnClickListener {
                    if (selectedIndices.contains(position)) {
                        selectedIndices.remove(position)
                        onListUpdate(packageName, false)
                    } else {
                        selectedIndices.add(position)
                        onListUpdate(packageName, true)
                    }
                    if (restartPackageOnChange()) {
                        runCatching { activityManager?.forceStopPackage(packageName) }
                    }
                    notifyItemChanged(position)
                }
                if (initialList.contains(packageName)) {
                    initialList.remove(packageName)
                    selectedIndices.add(position)
                }
                checkBox.isChecked = selectedIndices.contains(position)
            }
        }

        override fun submitList(list: List<AppInfo>?) {
            initialList = getInitialCheckedList().toMutableList()
            selectedIndices.clear()
            super.submitList(list)
        }
    }

    private class AppListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var packageName: String = ""
        val icon: ImageView = itemView.findViewById(R.id.icon)!!
        val label: TextView = itemView.findViewById(R.id.label)!!
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)!!
    }

    private data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable,
    )

    companion object {
        private const val TAG = "BaseAppListSettingsFragment"

        private val itemCallback =
            object : DiffUtil.ItemCallback<AppInfo>() {
                override fun areItemsTheSame(oldInfo: AppInfo, newInfo: AppInfo) =
                    oldInfo.packageName == newInfo.packageName

                override fun areContentsTheSame(oldInfo: AppInfo, newInfo: AppInfo) =
                    oldInfo == newInfo
            }
    }
}
