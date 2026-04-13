/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.statusbar

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.widget.LayoutPreference

class DynamicBarGuide : SettingsPreferenceFragment() {

    private data class GuideStep(val titleRes: Int, val bodyRes: Int, val iconRes: Int)

    private val guideSteps = listOf(
        GuideStep(R.string.dynamic_bar_guide_chip_title, R.string.dynamic_bar_guide_chip, R.drawable.ic_fiber_manual_record),
        GuideStep(R.string.dynamic_bar_guide_swipe_title, R.string.dynamic_bar_guide_swipe, R.drawable.ic_swap_horiz),
        GuideStep(R.string.dynamic_bar_guide_expand_title, R.string.dynamic_bar_guide_expand, R.drawable.ic_touch_app),
        GuideStep(R.string.dynamic_bar_guide_dismiss_title, R.string.dynamic_bar_guide_dismiss, R.drawable.ic_expand_less),
        GuideStep(R.string.dynamic_bar_guide_keyguard_title, R.string.dynamic_bar_guide_keyguard, R.drawable.ic_notifications),
    )

    private val stepViewIds = intArrayOf(
        R.id.guide_step_1,
        R.id.guide_step_2,
        R.id.guide_step_3,
        R.id.guide_step_4,
        R.id.guide_step_5,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.dynamic_bar_guide_prefs)
    }

    override fun onStart() {
        super.onStart()
        populateGuideSteps()
    }

    private fun populateGuideSteps() {
        val layoutPref = findPreference<LayoutPreference>("dynamic_bar_guide_content")
            ?: return

        guideSteps.forEachIndexed { index, step ->
            val stepView = layoutPref.findViewById<android.view.View>(stepViewIds[index])
                ?: return@forEachIndexed
            stepView.findViewById<ImageView>(R.id.step_icon)?.setImageResource(step.iconRes)
            stepView.findViewById<TextView>(R.id.step_number)?.text = "${index + 1}"
            stepView.findViewById<TextView>(R.id.step_title)?.setText(step.titleRes)
            stepView.findViewById<TextView>(R.id.step_body)?.setText(step.bodyRes)
        }
    }

    override fun getMetricsCategory(): Int {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS
    }
}
