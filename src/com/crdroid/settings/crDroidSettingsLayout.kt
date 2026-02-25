/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

import com.crdroid.settings.fragments.*

import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class crDroidSettingsLayout : SettingsPreferenceFragment() {

    private var pager: ViewPager2? = null
    private var tabs: TabLayout? = null
    private var mediator: TabLayoutMediator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().setTitle(R.string.crdroid_settings_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.crdroid_settings, container, false)

        tabs = root.findViewById(R.id.tabs)
        pager = root.findViewById(R.id.pager)

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = TAB_TITLES.size

            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> StatusBar()
                1 -> QuickSettings()
                2 -> LockScreen()
                3 -> Buttons()
                4 -> UserInterface()
                5 -> Notifications()
                6 -> Sound()
                7 -> Miscellaneous()
                8 -> About()
                else -> StatusBar()
            }
        }

        pager!!.adapter = adapter
        pager!!.offscreenPageLimit = TAB_TITLES.size

        mediator = TabLayoutMediator(tabs!!, pager!!) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.also { it.attach() }

        return root
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        pager = null
        tabs = null
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset_settings_title)
            .setIcon(R.drawable.ic_reset)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_RESET) {
            showResetAllDialog(requireContext())
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showResetAllDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(R.string.ok) { _, _ -> resetAll(context) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resetAll(context: Context) {
        lifecycleScope.launch(Dispatchers.Default) {
            Buttons.reset(context)
            LockScreen.reset(context)
            Miscellaneous.reset(context)
            Navigation.reset(context)
            Notifications.reset(context)
            QuickSettings.reset(context)
            Sound.reset(context)
            StatusBar.reset(context)
            UserInterface.reset(context)

            withContext(Dispatchers.Main) {
                requireActivity().finish()
                startActivity(requireActivity().intent)
            }
        }
    }

    override fun getMetricsCategory(): Int =
        MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private const val MENU_RESET = Menu.FIRST

        private val TAB_TITLES = intArrayOf(
            R.string.statusbar_title,
            R.string.quicksettings_title,
            R.string.lockscreen_title,
            R.string.button_title,
            R.string.ui_title,
            R.string.notifications_title,
            R.string.sound_title,
            R.string.misc_title,
            R.string.about_crdroid
        )
    }
}
