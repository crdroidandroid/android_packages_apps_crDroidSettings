/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.statusbar

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.crdroid.settings.utils.DeviceUtils
import org.json.JSONArray

class DynamicBar : SettingsPreferenceFragment() {

    private val resolver: ContentResolver
        get() = requireContext().contentResolver

    private val handler = Handler(Looper.getMainLooper())
    private var settingsObserver: ContentObserver? = null

    private val eventTypeIds = listOf(
        "screen_recording",
        "privacy",
        "audio_recording",
        "media",
        "call",
        "notification",
        "timer",
        "stopwatch",
        "alarm",
        "charging",
        "bluetooth",
        "hotspot",
        "ringer",
        "vpn",
        "clipboard",
        "torch",
        "casting",
        "promoted_ongoing",
        "sports",
        "app_switch",
        "biometric_unlock",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.dynamic_bar)

        if (!DeviceUtils.hasVibrator(requireContext())) {
            findPreference<Preference>(SETTINGS_KEY_HAPTICS)?.let {
                preferenceScreen.removePreference(it)
            }
        }

        if (!hasCenterCutout(requireContext())) {
            findPreference<Preference>(SETTINGS_KEY_CENTER_ISLAND)?.let {
                preferenceScreen.removePreference(it)
            }
        }

        setupKeyguardSubPrefs()
        setupEventToggles()
        updateCompactNotificationVisibility()
        registerObserver()
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsObserver?.let { resolver.unregisterContentObserver(it) }
    }

    private fun setupKeyguardSubPrefs() {
        val keyguardPref = findPreference<SwitchPreferenceCompat>(SETTINGS_KEY_KEYGUARD_ENABLED)
        keyguardPref?.setOnPreferenceChangeListener { _, newValue ->
            updateKeyguardSubPrefsVisibility(newValue as Boolean)
            true
        }
        // Set initial visibility from current setting value
        val keyguardEnabled = Settings.Secure.getIntForUser(
            resolver, SETTINGS_KEY_KEYGUARD_ENABLED, 1, UserHandle.USER_CURRENT
        ) == 1
        updateKeyguardSubPrefsVisibility(keyguardEnabled)
    }

    private fun updateKeyguardSubPrefsVisibility(keyguardEnabled: Boolean) {
        findPreference<Preference>(SETTINGS_KEY_BATTERY_CHIP_MODE)?.isVisible = keyguardEnabled
    }

    private fun setupEventToggles() {
        val disabledEvents = getDisabledEvents()

        for (typeId in eventTypeIds) {
            val pref = findPreference<SwitchPreferenceCompat>("event_$typeId") ?: continue
            pref.isChecked = typeId !in disabledEvents
            pref.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                toggleEvent(typeId, enabled)
                if (typeId == "notification") {
                    updateCompactNotificationVisibility()
                }
                true
            }
        }
    }

    private fun getDisabledEvents(): Set<String> {
        val json = Settings.Secure.getStringForUser(
            resolver, SETTINGS_KEY_EVENTS, UserHandle.USER_CURRENT
        ) ?: return emptySet()

        if (json.isBlank()) return emptySet()

        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun toggleEvent(typeId: String, enabled: Boolean) {
        val current = getDisabledEvents()
        val updated = if (enabled) current - typeId else current + typeId
        val json = if (updated.isEmpty()) "" else JSONArray(updated.toList()).toString()
        Settings.Secure.putStringForUser(
            resolver, SETTINGS_KEY_EVENTS, json, UserHandle.USER_CURRENT
        )
    }

    private fun updateCompactNotificationVisibility() {
        val compactPref = findPreference<Preference>(SETTINGS_KEY_COMPACT_NOTIFICATIONS)
        val notifPref = findPreference<SwitchPreferenceCompat>("event_notification")
        compactPref?.isVisible = notifPref?.isChecked == true
    }

    private fun registerObserver() {
        settingsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                when (uri?.lastPathSegment) {
                    SETTINGS_KEY_EVENTS -> {
                        val disabledEvents = getDisabledEvents()
                        for (typeId in eventTypeIds) {
                            val pref = findPreference<SwitchPreferenceCompat>("event_$typeId")
                            pref?.isChecked = typeId !in disabledEvents
                        }
                        updateCompactNotificationVisibility()
                    }
                    SETTINGS_KEY_KEYGUARD_ENABLED -> {
                        val enabled = Settings.Secure.getIntForUser(
                            resolver, SETTINGS_KEY_KEYGUARD_ENABLED, 1, UserHandle.USER_CURRENT
                        ) == 1
                        updateKeyguardSubPrefsVisibility(enabled)
                    }
                }
            }
        }

        resolver.registerContentObserver(
            Settings.Secure.getUriFor(SETTINGS_KEY_EVENTS),
            false, settingsObserver!!
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(SETTINGS_KEY_KEYGUARD_ENABLED),
            false, settingsObserver!!
        )
    }

    override fun getMetricsCategory(): Int {
        return MetricsProto.MetricsEvent.CRDROID_SETTINGS
    }

    companion object {
        private const val SETTINGS_KEY_ENABLED = Settings.Secure.AX_DYNAMIC_BAR_ENABLED
        private const val SETTINGS_KEY_KEYGUARD_ENABLED =
            Settings.Secure.AX_DYNAMIC_BAR_KEYGUARD_ENABLED
        private const val SETTINGS_KEY_EVENTS = Settings.Secure.AX_DYNAMIC_BAR_EVENTS
        private const val SETTINGS_KEY_COMPACT_NOTIFICATIONS =
            Settings.Secure.AX_DYNAMIC_BAR_COMPACT_NOTIFICATIONS
        private const val SETTINGS_KEY_BATTERY_CHIP_MODE =
            Settings.Secure.AX_DYNAMIC_BAR_KEYGUARD_BATTERY_CHIP_MODE
        private const val SETTINGS_KEY_HAPTICS = Settings.Secure.AX_DYNAMIC_BAR_HAPTICS
        private const val SETTINGS_KEY_CHIP_MORPH = Settings.Secure.AX_DYNAMIC_BAR_CHIP_MORPH
        private const val SETTINGS_KEY_CENTER_ISLAND =
            Settings.Secure.AX_DYNAMIC_BAR_CENTER_ISLAND

        // Whether the camera cutout sits centred on the screen's natural top edge.
        private fun hasCenterCutout(context: Context): Boolean {
            val display = context.display ?: return false
            val cutout = display.cutout ?: return false
            val rotation = display.rotation
            val rect = when (rotation) {
                Surface.ROTATION_90 -> cutout.boundingRectLeft
                Surface.ROTATION_180 -> cutout.boundingRectBottom
                Surface.ROTATION_270 -> cutout.boundingRectRight
                else -> cutout.boundingRectTop
            }
            if (rect.isEmpty) return false
            val bounds = context.getSystemService(WindowManager::class.java)
                ?.maximumWindowMetrics?.bounds ?: return false
            val sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            val edge = if (sideways) bounds.height() else bounds.width()
            val center = if (sideways) rect.exactCenterY() else rect.exactCenterX()
            val size = if (sideways) rect.height() else rect.width()
            return edge > 0 && center / edge in 0.35f..0.65f && size <= edge * 0.4f
        }

        @JvmStatic
        fun reset(context: Context) {
            val resolver = context.contentResolver
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_ENABLED, 0,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_KEYGUARD_ENABLED, 1,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_COMPACT_NOTIFICATIONS, 1,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_BATTERY_CHIP_MODE, 1,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_HAPTICS, 1,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_CHIP_MORPH, 1,
                UserHandle.USER_CURRENT
            )
            Settings.Secure.putIntForUser(
                resolver, SETTINGS_KEY_CENTER_ISLAND, 0,
                UserHandle.USER_CURRENT
            )
        }
    }
}
