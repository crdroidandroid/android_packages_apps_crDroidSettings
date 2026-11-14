/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.crdroid.settings.fragments.misc.PifFetcher.PifChannel
import com.crdroid.settings.fragments.misc.PifFetcher.PifFetchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Periodic auto-refresh (every 6, 12 or 24 hours, 24 by default) of a fingerprint that was
 * fetched online.
 */
internal object PifAutoRefresh {
    private const val TAG = "PifAutoRefresh"

    const val ENABLED_KEY = "spoof_pif_auto_refresh"
    /** JSON {channel, device, model} of the last online fetch; absent otherwise. */
    const val SOURCE_KEY = "spoof_pif_auto_source"

    /** Hours between refresh checks. Unset means [DEFAULT_REFRESH_INTERVAL_HOURS]. */
    const val REFRESH_INTERVAL_KEY = "spoof_pif_auto_refresh_interval_hours"

    /** Refresh intervals offered on the Play Integrity Fix screen, in display order. */
    val REFRESH_INTERVAL_OPTIONS = listOf(6, 12, 24)
    private const val DEFAULT_REFRESH_INTERVAL_HOURS = 24

    // Must not clash with other JobScheduler ids used by the Settings app.
    private const val JOB_ID = 0x7b0c03

    private const val CHANNEL_ID = "pif_auto_refresh"
    private const val NOTIFY_ID = 0x7b0e

    private const val PREFS = "pif_auto_refresh"
    private const val PREF_LAST_CHECKED = "last_checked"
    private const val PREF_LAST_UPDATED = "last_updated"

    data class Source(val channel: PifChannel, val device: String, val model: String)

    fun isEnabled(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, ENABLED_KEY, 1) == 1

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Secure.putInt(context.contentResolver, ENABLED_KEY, if (enabled) 1 else 0)
        sync(context)
    }

    fun refreshIntervalHours(context: Context): Int =
        Settings.Secure.getInt(
            context.contentResolver, REFRESH_INTERVAL_KEY, DEFAULT_REFRESH_INTERVAL_HOURS,
        ).takeIf { it in REFRESH_INTERVAL_OPTIONS } ?: DEFAULT_REFRESH_INTERVAL_HOURS

    fun setRefreshIntervalHours(context: Context, hours: Int) {
        if (hours !in REFRESH_INTERVAL_OPTIONS) return
        Settings.Secure.putInt(context.contentResolver, REFRESH_INTERVAL_KEY, hours)
        sync(context)
    }

    fun readSource(context: Context): Source? = runCatching {
        val raw = Settings.Secure.getString(context.contentResolver, SOURCE_KEY)
        if (raw.isNullOrBlank()) return null
        val json = JSONObject(raw)
        Source(
            channel = PifChannel.valueOf(json.getString("channel")),
            device = json.getString("device").ifEmpty { return null },
            model = json.optString("model"),
        )
    }.getOrNull()

    fun setSource(context: Context, channel: PifChannel, device: PifFetcher.PifDevice) {
        val json = JSONObject()
            .put("channel", channel.name)
            .put("device", device.device)
            .put("model", device.model)
        Settings.Secure.putString(context.contentResolver, SOURCE_KEY, json.toString())
        sync(context)
    }

    fun clearSource(context: Context) {
        Settings.Secure.putString(context.contentResolver, SOURCE_KEY, null)
        sync(context)
    }

    fun lastChecked(context: Context): Long? =
        if (!isUnlocked(context)) null
        else prefs(context).getLong(PREF_LAST_CHECKED, 0L).takeIf { it > 0 }

    fun sync(context: Context) {
        val ctx = context.applicationContext
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (readSource(ctx) == null || !isEnabled(ctx)) {
            js.cancel(JOB_ID)
            return
        }
        val interval = TimeUnit.HOURS.toMillis(refreshIntervalHours(ctx).toLong())
        if (js.getPendingJob(JOB_ID)?.intervalMillis == interval) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, PifRefreshJobService::class.java))
                .setPeriodic(interval)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
        )
    }

    @Synchronized
    fun runRefresh(context: Context): Boolean {
        val ctx = context.applicationContext
        if (!isUnlocked(ctx)) return false
        val resolver = ctx.contentResolver
        if (!isEnabled(ctx)) return true
        val source = readSource(ctx) ?: return true

        val beforeRaw = Settings.Secure.getString(resolver, PlayIntegrityFix.PIF_CONFIG_KEY)
        val before = beforeRaw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it.trim()) }.getOrNull() }
        if (before == null || before.optString("DEVICE") != source.device) {
            // Deleted or replaced outside the screen: this is no longer the fetched config.
            Log.i(TAG, "Config no longer matches ${source.device}; stopping auto-refresh")
            clearSource(ctx)
            return true
        }

        prefs(ctx).edit().putLong(PREF_LAST_CHECKED, System.currentTimeMillis()).apply()

        val result = runCatching {
            val list = PifFetcher.fetchDevices(source.channel)
            val device = list.devices.firstOrNull { it.device == source.device }
                ?: return@runCatching null
            PifFetcher.buildConfig(device, source.channel, list.apiKey)
        }.onFailure { Log.w(TAG, "Fetch failed", it) }.getOrNull()
        if (result !is PifFetchResult.Success) {
            Log.i(TAG, "No fingerprint for ${source.device} on ${source.channel}: $result")
            return true
        }

        val newFp = result.pifData.optString("FINGERPRINT")
        val oldFp = before.optString("FINGERPRINT")
        if (!PlayIntegrityFix.isValidFingerprint(newFp) || newFp == oldFp) return true
        // Same channel should only move forward; never trade for an older patch.
        val newPatch = result.pifData.optString("SECURITY_PATCH")
        val oldPatch = before.optString("SECURITY_PATCH")
        if (newPatch.isNotEmpty() && oldPatch.isNotEmpty() && newPatch < oldPatch) {
            Log.i(TAG, "Skipping $newFp: patch $newPatch is older than $oldPatch")
            return true
        }

        // The user may have changed things while we were on the network.
        if (Settings.Secure.getString(resolver, PlayIntegrityFix.PIF_CONFIG_KEY) != beforeRaw ||
            readSource(ctx) != source || !isEnabled(ctx)) {
            return true
        }

        val merged = PlayIntegrityFix.mergeFetchedConfig(result.pifData, beforeRaw)
        if (!Settings.Secure.putString(resolver, PlayIntegrityFix.PIF_CONFIG_KEY, merged)) {
            Log.w(TAG, "Failed to write the refreshed config")
            return true
        }
        // Clear Play Store data only once the new fingerprint is really in place.
        val applied = runCatching {
            JSONObject(Settings.Secure.getString(resolver, PlayIntegrityFix.PIF_CONFIG_KEY))
                .optString("FINGERPRINT") == newFp
        }.getOrDefault(false)
        if (!applied) {
            Log.w(TAG, "Refreshed config did not stick; leaving Play Store data alone")
            return true
        }

        Log.i(TAG, "Applied $newFp")
        try {
            ctx.packageManager.clearApplicationUserData(VENDING_PACKAGE, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear Play Store data", e)
        }
        prefs(ctx).edit().putLong(PREF_LAST_UPDATED, System.currentTimeMillis()).apply()
        postUpdated(ctx, result.model, newPatch)
        return true
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isUnlocked(ctx: Context): Boolean =
        ctx.getSystemService(UserManager::class.java)?.isUserUnlocked != false

    private fun postUpdated(ctx: Context, model: String, patch: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.pif_auto_refresh_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ctx.getString(R.string.pif_auto_refresh_channel_description)
            }
        )
        val open = SubSettingLauncher(ctx)
            .setDestination(PlayIntegrityFix::class.java.name)
            .setTitleRes(R.string.play_integrity_fix_title)
            .setSourceMetricsCategory(MetricsProto.MetricsEvent.CRDROID_SETTINGS)
            .toIntent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = PendingIntent.getActivity(
            ctx, NOTIFY_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = ctx.getString(
            R.string.pif_auto_refresh_notification_text, model, patch.ifEmpty { "-" },
        )
        nm.notify(
            NOTIFY_ID,
            Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(ctx.getString(R.string.pif_auto_refresh_notification_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class PifRefreshJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            runCatching { PifAutoRefresh.runRefresh(applicationContext) }
            // Periodic: a miss is simply retried at the next slot.
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
