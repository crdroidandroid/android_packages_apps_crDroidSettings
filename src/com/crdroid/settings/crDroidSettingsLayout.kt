/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

import com.crdroid.settings.fragments.*

import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

import kotlin.math.sign
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
            private val cachedPositions = java.util.BitSet(getItemCount())

            init {
                savedInstanceState?.also {
                    childFragmentManager.fragments.forEach { frag ->
                        frag.arguments?.takeIf { it.containsKey(ADAPTER_POSITION_ARG) }?.let {
                            cachedPositions.set(it.getInt(ADAPTER_POSITION_ARG))
                        }
                    }
                }?.takeIf { cachedPositions.cardinality() > 1 }?.let {
                    // After a config change, unbound (offscreen) fragments are flagged for removal
                    // by FragmentManager unless revisited/rebound before the view pager's GC pass
                    childFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                                f.takeIf { it.isRemoving() }
                                    ?.arguments
                                    ?.takeIf { it.containsKey(ADAPTER_POSITION_ARG) }
                                    ?.let { cachedPositions.clear(it.getInt(ADAPTER_POSITION_ARG)) }
                            }
                        },
                        false /*recursive*/
                    )
                }
            }

            fun isItemRangeCached(startPos: Int, endPos: Int): Boolean {
                // Returns true if NO bits are 0 inside the provided range
                if (startPos < endPos) {
                    return cachedPositions.nextClearBit(startPos) > endPos
                }
                return cachedPositions.previousClearBit(startPos) < endPos
            }

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
            }.also {
                cachedPositions.set(position)
                // Use fragment arguments to restore the adapter position after a config change
                it.arguments = (it.arguments ?: Bundle(1)).apply {
                    putInt(ADAPTER_POSITION_ARG, position)
                }
            }

            override fun onAttachedToRecyclerView(rv: RecyclerView) {
                super.onAttachedToRecyclerView(rv)
                rv.setItemViewCacheSize(getItemCount())
            }
        }

        pager!!.adapter = adapter

        mediator = TabLayoutMediator(tabs!!, pager!!) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.also { it.attach() }

        // Extend the tabs layout to support smooth navigation across uncached pages and avoid lags
        tabs!!.clearOnTabSelectedListeners()
        tabs!!.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val newPos = tab.position
                val currPos = pager!!.currentItem.takeIf { it != newPos } ?: return
                val direction = (newPos - currPos).sign
                val offscreenPageLimit = pager!!.offscreenPageLimit.coerceAtLeast(1)
                val maxIndex = adapter.itemCount - 1
                // ViewPager2 does a 3-page pre-jump to nearby items on longs jumps
                val rangeBegin = currPos.coerceIn(newPos - 3, newPos + 3)
                val rangeEnd = (newPos + direction * offscreenPageLimit).coerceIn(0, maxIndex)
                // Use smooth scrolling (default behavior) only if no page prefetching is expected
                // (i.e., if all pages along the scroll path, including offscreen ones, have already
                // been visited and cached); otherwise, "snap" directly to the target page
                if (adapter.isItemRangeCached(rangeBegin, rangeEnd)) {
                    pager!!.setCurrentItem(newPos)
                } else {
                    pager!!.setCurrentItemWithSnapScroll(newPos)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

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

        private const val ADAPTER_POSITION_ARG = "view_pager_adapter_pos"
    }
}

/** Reusable drawable to "snapshot" views. */
private class ViewDrawable(
    resources: android.content.res.Resources,
    val height: Int,
    val width: Int
) : BitmapDrawable(resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)) {
    private val canvas = Canvas(bitmap!!)

    init { setBounds(0, 0, width, height) }

    fun snapshot(view: View) {
        bitmap!!.eraseColor(Color.TRANSPARENT)
        view.draw(canvas)
    }

    fun recycle() {
        bitmap!!.recycle()
    }
}

private val FAST_OUT_SLOW_IN_INTERPOLATOR = FastOutSlowInInterpolator()
/** An optimized version of [ViewPager2.setCurrentItem] that smoothly "snaps" from the old to the
 * new page without triggering intermediate page prefetching, which causes UI stuttering/jank. */
private fun ViewPager2.setCurrentItemWithSnapScroll(position: Int) {
    if (currentItem == position || width == 0 || height == 0) return
    val direction = (position - currentItem).sign
    val fadeInOut = kotlin.math.abs(currentItem - position) > 1 // Enable fade only in long jumps
    val overlayDrawable = (tag as? ViewDrawable).let { oldDrawable ->
        oldDrawable
            ?.takeIf { it.width == width && it.height == height } // Handle screen rotation
            ?: ViewDrawable(resources, height, width).also {
                tag = it
                oldDrawable?.recycle()
            }
    }
    // Preemptively set a temporarily transformer to animate the "snap" effect between pages
    setPageTransformer { view, _ ->
        if (currentItem != position) {
            overlayDrawable.snapshot(view) // Snapshot the old page view before it will disappear
            return@setPageTransformer
        }
        setPageTransformer(null)
        overlay.add(overlayDrawable) // Place the old page on top before the new page will appear
        // Prepare the animation (the old page view/overlay will animate with it)
        view.translationX = direction * view.width.toFloat()
        if (fadeInOut) view.alpha = 0f
        view.animate()
            .translationX(0f)
            .also { if (fadeInOut) it.alpha(1f) }
            .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
            .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR)
            .setUpdateListener { anim ->
                if (currentItem != position) {
                    anim.cancel() // Abort! User swiped to a different page during transition
                } else if (anim.animatedFraction > 0f) {
                    val offset = (view.translationX - direction * view.width).toInt()
                    overlayDrawable.setBounds(offset, 0, offset + view.width, view.height)
                    if (fadeInOut) {
                        overlayDrawable.alpha = ((1f - anim.animatedFraction) * 255).toInt()
                    }
                    invalidate()
                }
            }
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlayDrawable.let {
                        overlay.remove(it)
                        it.setBounds(0, 0, view.width, view.height)
                        it.alpha = 255
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.translationX = 0f
                    view.alpha = 1f
                }
            })
    }
    setCurrentItem(position, /* smoothScroll */ false)
}
