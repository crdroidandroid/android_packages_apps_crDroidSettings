/*
 * Copyright (C) 2021 AOSP-Krypton Project
 *           (C) 2022 Nameless-AOSP Project
 *           (C) 2022 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.fragments

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView

import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.android.internal.util.custom.HideDeveloperStatusUtils

import com.android.settings.R

import com.google.android.material.appbar.AppBarLayout

class HideDeveloperStatusSettings: Fragment(R.layout.hide_developer_status_layout) {

    private lateinit var activityManager: ActivityManager
    private lateinit var packageManager: PackageManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var packageList: List<PackageInfo>
    private lateinit var userManager: UserManager
    private lateinit var userInfos: List<UserInfo>

    private var appBarLayout: AppBarLayout? = null
    private var searchText = ""
    private var customFilter: ((PackageInfo) -> Boolean)? = null
    private var comparator: ((PackageInfo, PackageInfo) -> Int)? = null
    private var hideDeveloperStatusUtils: HideDeveloperStatusUtils = HideDeveloperStatusUtils()
    private var showSystem = false
    private var optionsMenu: Menu? = null

    override fun onStart() {
        super.onStart()
        updateOptionsMenu()
        val host = getActivity()
        if (host != null) {
            host.invalidateOptionsMenu();
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().setTitle(getTitle())
        appBarLayout = requireActivity().findViewById(R.id.app_bar)
        activityManager = requireContext().getSystemService(ActivityManager::class.java) as ActivityManager
        packageManager = requireContext().packageManager
        packageList = packageManager.getInstalledPackages(PackageManager.MATCH_ANY_USER)
        userManager = UserManager.get(requireContext())
        userInfos = userManager.getUsers()
        for (info in userInfos) {
            hideDeveloperStatusUtils.setApps(requireContext(), info.id)
        }
    }

    private fun getTitle(): Int {
        return R.string.hide_developer_status_title
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AppListAdapter()
        recyclerView = view.findViewById<RecyclerView>(R.id.apps_list).also {
            it!!.layoutManager = LinearLayoutManager(context)
            it!!.adapter = adapter
        } as RecyclerView
        refreshList()
    }

    /**
     * @return an initial list of packages that should appear as selected.
     */
    private fun getInitialCheckedList(): List<String> {
        val flattenedString = Settings.Secure.getString(
            requireContext().contentResolver, getKey()
        )
        return flattenedString?.takeIf {
            it.isNotBlank()
        }?.split(",")?.toList() ?: emptyList()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val activity = getActivity()
        if (activity == null) {
            return;
        }
        optionsMenu = menu;
        inflater.inflate(R.menu.hide_developer_status_menu, menu)

        menu.findItem(R.id.show_system).setVisible(showSystem)
        menu.findItem(R.id.hide_system).setVisible(!showSystem)

        val searchMenuItem = menu.findItem(R.id.search) as MenuItem
        searchMenuItem.setOnActionExpandListener(object: MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                // To prevent a large space on tool bar.
                appBarLayout!!.setExpanded(false /*expanded*/, false /*animate*/)
                // To prevent user can expand the collapsing tool bar view.
                ViewCompat.setNestedScrollingEnabled(recyclerView, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                // We keep the collapsed status after user cancel the search function.
                appBarLayout!!.setExpanded(false /*expanded*/, false /*animate*/)
                ViewCompat.setNestedScrollingEnabled(recyclerView, true)
                return true
            }
        })
        val searchView = searchMenuItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_apps)
        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false

            override fun onQueryTextChange(newText: String): Boolean {
                searchText = newText
                refreshList()
                return true
            }
        })

        updateOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var i = item.getItemId()
        if (i == R.id.show_system || i == R.id.hide_system) {
            showSystem = !showSystem;
            refreshList();
        }
        updateOptionsMenu()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        updateOptionsMenu()
    }

    override fun onDestroyOptionsMenu() {
        optionsMenu = null;
    }

    private fun updateOptionsMenu() {
        if (optionsMenu == null) {
            return;
        }

        var menu = optionsMenu as Menu

        menu.findItem(R.id.show_system).setVisible(!showSystem)
        menu.findItem(R.id.hide_system).setVisible(showSystem)
    }

    /**
     * Called when user selects an item.
     *
     * @param list a [List<String>] of selected items.
     */
    private fun onListUpdate(packageName: String, isChecked: Boolean) {
        if (packageName.isBlank()) return
        for (info in userInfos) {
            if (isChecked) {
                hideDeveloperStatusUtils.addApp(requireContext(), packageName, info.id)
            } else {
                hideDeveloperStatusUtils.removeApp(requireContext(), packageName, info.id)
            }
        }
        try {
            activityManager.forceStopPackage(packageName)
        } catch (ignored: Exception) {
        }
    }

    private fun getKey(): String {
        return "hide_developer_status"
    }

    private fun refreshList() {
        var list = packageList.filter {
            if (!showSystem) {
                !it.applicationInfo!!.isSystemApp()
                && !resources.getStringArray(
                        R.array.hide_developer_status_hidden_apps)
                            .asList().contains(it.applicationInfo!!.packageName)
                && !it.applicationInfo!!.packageName.contains("android.settings")
            } else {
                !resources.getStringArray(
                    R.array.hide_developer_status_hidden_apps)
                        .asList().contains(it.applicationInfo!!.packageName)
                && !it.applicationInfo!!.packageName.contains("android.settings")
                && !it.applicationInfo!!.isResourceOverlay()
            }
        }.filter {
            getLabel(it).contains(searchText, true)
        }
        list = customFilter?.let { customFilter ->
            list.filter {
                customFilter(it)
            }
        } ?: list
        list = comparator?.let {
            list.sortedWith(it)
        } ?: list.sortedWith { a, b ->
            getLabel(a).compareTo(getLabel(b))
        }
        if (::adapter.isInitialized) adapter.submitList(list.map { appInfoFromPackageInfo(it) })
    }

    private fun appInfoFromPackageInfo(packageInfo: PackageInfo) =
        AppInfo(
            packageInfo.packageName,
            getLabel(packageInfo),
            packageInfo.applicationInfo!!.loadIcon(packageManager),
        )

    private fun getLabel(packageInfo: PackageInfo) =
        packageInfo.applicationInfo!!.loadLabel(packageManager).toString()

    private inner class AppListAdapter: ListAdapter<AppInfo, AppListViewHolder>(itemCallback) {
        private val selectedIndices = mutableSetOf<Int>()
        private var initialList = getInitialCheckedList().toMutableList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppListViewHolder(layoutInflater.inflate(
                R.layout.hide_developer_status_list_item, parent, false))

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
            getItem(position).let {
                holder.label!!.text = it.label
                holder.packageName!!.text = it.packageName
                holder.icon!!.setImageDrawable(it.icon)
                holder.itemView!!.setOnClickListener {
                    if (selectedIndices.contains(position)) {
                        selectedIndices.remove(position)
                        onListUpdate(holder.packageName!!.text.toString(), false)
                    } else {
                        selectedIndices.add(position)
                        onListUpdate(holder.packageName!!.text.toString(), true)
                    }
                    notifyItemChanged(position)
                }
                if (initialList.contains(it.packageName)) {
                    initialList.remove(it.packageName)
                    selectedIndices.add(position)
                }
                holder.checkBox!!.isChecked = selectedIndices.contains(position)
            }
        }

        override fun submitList(list: List<AppInfo>?) {
            initialList = getInitialCheckedList().toMutableList()
            selectedIndices.clear()
            super.submitList(list)
        }
    }

    private class AppListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val icon: ImageView? = itemView.findViewById(R.id.icon)
        val label: TextView? = itemView.findViewById(R.id.label)
        val packageName: TextView? = itemView.findViewById(R.id.packageName)
        val checkBox: CheckBox? = itemView.findViewById(R.id.checkBox)
    }

    private data class AppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable,
    )

    companion object {
        private val itemCallback = object: DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldInfo: AppInfo, newInfo: AppInfo) =
                oldInfo.packageName == newInfo.packageName

            override fun areContentsTheSame(oldInfo: AppInfo, newInfo: AppInfo) =
                oldInfo == newInfo
        }
    }
}
