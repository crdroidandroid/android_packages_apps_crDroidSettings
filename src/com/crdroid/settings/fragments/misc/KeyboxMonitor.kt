/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.UserManager
import android.provider.Settings
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Background keybox alerts, on by default.
 */
internal object KeyboxMonitor {

    const val ENABLED_KEY = "spoof_trickystore_keybox_alerts"
    const val ACTION_EXPIRY_DUE = "com.crdroid.settings.action.KEYBOX_EXPIRY_DUE"

    // Must not clash with other JobScheduler ids used by the Settings app.
    internal const val JOB_DAILY = 0x7b0c01
    internal const val JOB_NOW = 0x7b0c02

    private const val CHANNEL_ID = "trickystore_keybox_alerts"
    private const val NOTIFY_EXPIRY = 0x7b0c
    private const val NOTIFY_REVOKED = 0x7b0d

    private const val PREFS = "trickystore_keybox_alerts"
    private const val PREF_KEYBOX_HASH = "keybox_hash"
    private const val PREF_EXPIRY_NOTIFIED = "expiry_notified"
    private const val PREF_REVOKED_NOTIFIED = "revoked_notified"

    /** How long before expiry the expiry alert fires. */
    private val LEAD_TIME_MS = TimeUnit.DAYS.toMillis(1)
    /** An alarm that lands slightly early still counts as due. */
    private val EARLY_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(5)
    private val CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
    /** The expiry-due check waits this long for a network before running without one. */
    private val NOW_DEADLINE_MS = TimeUnit.MINUTES.toMillis(30)

    private val REVOKED_STATES =
        setOf(KeyboxInspector.Status.REVOKED, KeyboxInspector.Status.SUSPENDED)

    private class Keybox(val xml: String, val hash: String, val expiry: Long?)

    fun isEnabled(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, ENABLED_KEY, 1) == 1

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Secure.putInt(context.contentResolver, ENABLED_KEY, if (enabled) 1 else 0)
        sync(context)
    }

    /** When the expiry alert for a keybox expiring at [expiry] is due. */
    fun triggerAt(expiry: Long): Long = expiry - LEAD_TIME_MS

    /** Brings jobs, alarm and notifications in line with the current keybox and switch. */
    fun sync(context: Context) {
        val ctx = context.applicationContext
        val keybox = reconcile(ctx) ?: return
        val expiry = keybox.expiry ?: return
        val now = System.currentTimeMillis()
        // Inside the last day (device was off at the alarm, or alerts were just turned on):
        // run the check now; it posts the expiry alert if still appropriate.
        if (now < expiry && now >= triggerAt(expiry) - EARLY_TOLERANCE_MS &&
            !prefs(ctx).getBoolean(PREF_EXPIRY_NOTIFIED, false)) {
            checkNow(ctx)
        }
    }

    fun markRevokedSeen(context: Context, hash: String) {
        val ctx = context.applicationContext
        if (!isUnlocked(ctx)) return
        val prefs = prefsFor(ctx, hash)
        prefs.edit()
            .putBoolean(PREF_REVOKED_NOTIFIED, true)
            .putBoolean(PREF_EXPIRY_NOTIFIED, true)
            .apply()
        cancelNotification(ctx, NOTIFY_REVOKED)
    }

    @Synchronized
    fun runCheck(context: Context): Boolean {
        val ctx = context.applicationContext
        if (!isUnlocked(ctx)) return false
        val keybox = reconcile(ctx) ?: return true
        val hash = keybox.hash

        val previous = KeyboxInspector.loadReport(ctx, hash)
        val fresh = runCatching { KeyboxInspector.inspect(ctx, keybox.xml) }.getOrNull()
        // A failed network check must not erase a known bad verdict (same rule as the screen).
        val keepPrevious = fresh == null ||
            (fresh.status == KeyboxInspector.Status.UNVERIFIED && previous != null &&
                previous.status != KeyboxInspector.Status.VALID &&
                previous.status != KeyboxInspector.Status.UNVERIFIED)
        val report = if (keepPrevious) previous else fresh!!.also {
            KeyboxInspector.saveReport(ctx, hash, it)
        }

        // Is this keybox still the one installed, and are alerts still on? The check above
        // can take a while, and the user may have deleted or replaced it meanwhile.
        if (currentKeybox(ctx)?.hash != hash || !isEnabled(ctx)) return true

        val prefs = prefsFor(ctx, hash)
        val status = report?.status
        if (status in REVOKED_STATES) {
            if (!prefs.getBoolean(PREF_REVOKED_NOTIFIED, false)) {
                postRevoked(ctx, status!!, report?.reason.orEmpty())
            }
            // An expiry reminder for a revoked keybox would only add noise.
            prefs.edit()
                .putBoolean(PREF_REVOKED_NOTIFIED, true)
                .putBoolean(PREF_EXPIRY_NOTIFIED, true)
                .apply()
            return true
        }

        val expiry = keybox.expiry ?: return true
        val now = System.currentTimeMillis()
        if (status == KeyboxInspector.Status.EXPIRED || now >= expiry) return true
        if (now >= triggerAt(expiry) - EARLY_TOLERANCE_MS &&
            !prefs.getBoolean(PREF_EXPIRY_NOTIFIED, false)) {
            postExpiry(ctx, expiry)
            prefs.edit().putBoolean(PREF_EXPIRY_NOTIFIED, true).apply()
        }
        return true
    }

    /** Asks for a check as soon as possible (used when the expiry alarm fires). */
    fun checkNow(context: Context) {
        val ctx = context.applicationContext
        ctx.getSystemService(JobScheduler::class.java)?.schedule(
            JobInfo.Builder(JOB_NOW, ComponentName(ctx, KeyboxCheckJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(NOW_DEADLINE_MS)
                .build()
        )
    }

    private fun reconcile(ctx: Context): Keybox? {
        val keybox = currentKeybox(ctx)
        if (keybox == null) {
            cancelAll(ctx)
            if (isUnlocked(ctx)) prefs(ctx).edit().clear().apply()
            return null
        }
        if (isUnlocked(ctx)) prefsFor(ctx, keybox.hash)
        if (!isEnabled(ctx)) {
            cancelAll(ctx)
            return null
        }
        scheduleDailyCheck(ctx)
        val expiry = keybox.expiry
        val now = System.currentTimeMillis()
        if (expiry == null || now >= triggerAt(expiry) - EARLY_TOLERANCE_MS) {
            cancelAlarm(ctx)
        } else {
            scheduleAlarm(ctx, triggerAt(expiry))
        }
        return keybox
    }

    private fun currentKeybox(ctx: Context): Keybox? {
        val xml = KeyboxInspector.decode(
            Settings.Secure.getString(ctx.contentResolver, TrickyStore.KEYBOX_KEY)
        ) ?: return null
        val expiry = runCatching { KeyboxInspector.describe(xml)?.earliestExpiry }.getOrNull()
        return Keybox(xml, KeyboxInspector.hashOf(xml), expiry)
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun prefsFor(ctx: Context, hash: String): SharedPreferences {
        val prefs = prefs(ctx)
        if (prefs.getString(PREF_KEYBOX_HASH, null) != hash) {
            // Alerts about the previous keybox no longer apply.
            cancelNotification(ctx, NOTIFY_EXPIRY)
            cancelNotification(ctx, NOTIFY_REVOKED)
            prefs.edit().clear().putString(PREF_KEYBOX_HASH, hash).commit()
        }
        return prefs
    }

    private fun isUnlocked(ctx: Context): Boolean =
        ctx.getSystemService(UserManager::class.java)?.isUserUnlocked != false

    private fun cancelAll(ctx: Context) {
        ctx.getSystemService(JobScheduler::class.java)?.apply {
            cancel(JOB_DAILY)
            cancel(JOB_NOW)
        }
        cancelAlarm(ctx)
        cancelNotification(ctx, NOTIFY_EXPIRY)
        cancelNotification(ctx, NOTIFY_REVOKED)
    }

    private fun scheduleDailyCheck(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        // Re-scheduling would restart the period, so leave a pending job alone.
        if (js.getPendingJob(JOB_DAILY) != null) return
        js.schedule(
            JobInfo.Builder(JOB_DAILY, ComponentName(ctx, KeyboxCheckJobService::class.java))
                .setPeriodic(CHECK_INTERVAL_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
        )
    }

    private fun alarmIntent(ctx: Context, create: Boolean): PendingIntent? {
        val intent = Intent(ctx, KeyboxMonitorReceiver::class.java).setAction(ACTION_EXPIRY_DUE)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }

    private fun scheduleAlarm(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx, create = true) ?: return
        // Settings runs as the system uid, which is exempt from the exact-alarm permission,
        // but fall back to an inexact alarm rather than crash if that ever changes.
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context) {
        val pi = alarmIntent(ctx, create = false) ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    private fun postExpiry(ctx: Context, expiry: Long) = post(
        ctx, NOTIFY_EXPIRY,
        title = ctx.getString(R.string.ts_alert_expiry_title),
        text = ctx.getString(R.string.ts_alert_expiry_text, KeyboxInspector.formatDate(expiry)),
    )

    private fun postRevoked(ctx: Context, status: KeyboxInspector.Status, reason: String) {
        val suspended = status == KeyboxInspector.Status.SUSPENDED
        post(
            ctx, NOTIFY_REVOKED,
            title = ctx.getString(
                if (suspended) R.string.ts_alert_suspended_title else R.string.ts_alert_revoked_title,
            ),
            text = ctx.getString(
                if (suspended) R.string.ts_alert_suspended_text else R.string.ts_alert_revoked_text,
                reason.ifEmpty { "-" },
            ),
        )
    }

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.ts_alerts_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = ctx.getString(R.string.ts_alerts_channel_description)
            }
        )

        val open = SubSettingLauncher(ctx)
            .setDestination(TrickyStore::class.java.name)
            .setTitleRes(R.string.tricky_store_title)
            .setSourceMetricsCategory(MetricsProto.MetricsEvent.CRDROID_SETTINGS)
            .toIntent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val contentIntent = PendingIntent.getActivity(
            ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(id, notification)
    }

    private fun cancelNotification(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}

class KeyboxCheckJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val done = runCatching { KeyboxMonitor.runCheck(applicationContext) }
                .getOrDefault(false)
            // Retry later only if it couldn't run (user still locked, unexpected error).
            jobFinished(params, !done && params.jobId == KeyboxMonitor.JOB_NOW)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

class KeyboxMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> KeyboxMonitor.sync(context)
            KeyboxMonitor.ACTION_EXPIRY_DUE -> KeyboxMonitor.checkNow(context)
        }
    }
}
