/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings

import android.content.Context
import com.android.settings.core.BasePreferenceController

class TopLevelSettingsPreferenceController(
    context: Context,
    preferenceKey: String
) : BasePreferenceController(context, preferenceKey) {

    override fun getAvailabilityStatus(): Int = AVAILABLE
}
