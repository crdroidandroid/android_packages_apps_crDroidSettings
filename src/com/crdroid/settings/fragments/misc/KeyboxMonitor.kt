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

    /** Comma-separated lead times in days, e.g. "1,7". Unset means [DEFAULT_LEAD_DAYS]. */
    const val LEAD_DAYS_KEY = "spoof_trickystore_expiry_lead_days"

    /** Lead times offered on the TrickyStore screen, in display order. */
    val LEAD_DAY_OPTIONS = listOf(1, 3, 7, 14)
    private val DEFAULT_LEAD_DAYS = setOf(1)

    /** Hours between background revocation checks. Unset means [DEFAULT_CHECK_INTERVAL_HOURS]. */
    const val CHECK_INTERVAL_KEY = "spoof_trickystore_check_interval_hours"

    /** Check intervals offered on the TrickyStore screen, in display order. */
    val CHECK_INTERVAL_OPTIONS = listOf(6, 12, 24)
    private const val DEFAULT_CHECK_INTERVAL_HOURS = 12

    // Must not clash with other JobScheduler ids used by the Settings app.
    internal const val JOB_PERIODIC = 0x7b0c01
    internal const val JOB_NOW = 0x7b0c02

    private const val CHANNEL_ID = "trickystore_keybox_alerts"
    private const val NOTIFY_EXPIRY = 0x7b0c
    private const val NOTIFY_REVOKED = 0x7b0d

    private const val PREFS = "trickystore_keybox_alerts"
    private const val PREF_KEYBOX_HASH = "keybox_hash"
    private const val PREF_LEADS_DONE = "expiry_leads_done"
    private const val PREF_EXPIRY_MUTED = "expiry_muted"
    private const val PREF_REVOKED_NOTIFIED = "revoked_notified"

    private val DAY_MS = TimeUnit.DAYS.toMillis(1)
    /** An alarm that lands slightly early still counts as due. */
    private val EARLY_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(5)
    /** The expiry-due check waits this long for a network before running without one. */
    private val NOW_DEADLINE_MS = TimeUnit.MINUTES.toMillis(30)

    private class Keybox(val xml: String, val hash: String, val expiry: Long?)

    fun isEnabled(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, ENABLED_KEY, 1) == 1

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Secure.putInt(context.contentResolver, ENABLED_KEY, if (enabled) 1 else 0)
        sync(context)
    }

    /** Hours between background revocation checks: one of [CHECK_INTERVAL_OPTIONS]. */
    fun checkIntervalHours(context: Context): Int =
        Settings.Secure.getInt(
            context.contentResolver, CHECK_INTERVAL_KEY, DEFAULT_CHECK_INTERVAL_HOURS,
        ).takeIf { it in CHECK_INTERVAL_OPTIONS } ?: DEFAULT_CHECK_INTERVAL_HOURS

    /** Stores [hours] (ignored unless one of [CHECK_INTERVAL_OPTIONS]) and re-arms the job. */
    fun setCheckIntervalHours(context: Context, hours: Int) {
        if (hours !in CHECK_INTERVAL_OPTIONS) return
        Settings.Secure.putInt(context.contentResolver, CHECK_INTERVAL_KEY, hours)
        sync(context)
    }

    /** The selected lead times, in days; never empty. */
    fun leadDays(context: Context): Set<Int> {
        val raw = Settings.Secure.getString(context.contentResolver, LEAD_DAYS_KEY)
            ?: return DEFAULT_LEAD_DAYS
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in LEAD_DAY_OPTIONS }
            .toSet()
            .ifEmpty { DEFAULT_LEAD_DAYS }
    }

    /** Stores [days] (ignored when empty: at least one lead time stays selected). */
    fun setLeadDays(context: Context, days: Set<Int>) {
        val ctx = context.applicationContext
        val valid = days.filter { it in LEAD_DAY_OPTIONS }.toSet()
        if (valid.isEmpty()) return
        val added = valid - leadDays(ctx)
        Settings.Secure.putString(
            ctx.contentResolver, LEAD_DAYS_KEY, valid.sorted().joinToString(","),
        )
        val keybox = currentKeybox(ctx)
        val expiry = keybox?.expiry
        if (keybox != null && expiry != null && isUnlocked(ctx)) {
            val now = System.currentTimeMillis()
            val passed = added.filter { now >= triggerAt(expiry, it) - EARLY_TOLERANCE_MS }
            if (passed.isNotEmpty()) markLeadsDone(prefsFor(ctx, keybox.hash), passed)
        }
        sync(ctx)
    }

    /** When the reminder [days] before a keybox expiring at [expiry] is due. */
    fun triggerAt(expiry: Long, days: Int): Long = expiry - days * DAY_MS

    /** The next selected reminder still ahead for [expiry], or null when all have passed. */
    fun nextReminderAt(context: Context, expiry: Long): Long? {
        val now = System.currentTimeMillis()
        return leadDays(context).map { triggerAt(expiry, it) }.filter { it > now }.minOrNull()
    }

    /** Brings jobs, alarm and notifications in line with the current keybox and switch. */
    fun sync(context: Context) {
        val ctx = context.applicationContext
        val keybox = reconcile(ctx) ?: return
        val expiry = keybox.expiry ?: return
        if (!isUnlocked(ctx)) return
        val prefs = prefs(ctx)
        val now = System.currentTimeMillis()
        if (now < expiry && !prefs.getBoolean(PREF_EXPIRY_MUTED, false) &&
            dueLeads(ctx, expiry, now, doneLeads(prefs)).isNotEmpty()) {
            checkNow(ctx)
        }
    }

    fun markRevokedSeen(context: Context, hash: String) {
        val ctx = context.applicationContext
        if (!isUnlocked(ctx)) return
        val prefs = prefsFor(ctx, hash)
        prefs.edit()
            .putBoolean(PREF_REVOKED_NOTIFIED, true)
            .putBoolean(PREF_EXPIRY_MUTED, true)
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
        // Same rule as the screen: a failed network check must not erase a known bad verdict.
        val report = KeyboxInspector.resolve(previous, fresh)
        if (report != null && report === fresh) KeyboxInspector.saveReport(ctx, hash, report)

        // Is this keybox still the one installed, and are alerts still on? The check above
        // can take a while, and the user may have deleted or replaced it meanwhile.
        if (currentKeybox(ctx)?.hash != hash || !isEnabled(ctx)) return true

        val prefs = prefsFor(ctx, hash)
        val status = report?.status
        if (report != null && report.status.isRevoked) {
            if (!prefs.getBoolean(PREF_REVOKED_NOTIFIED, false)) {
                postRevoked(ctx, report.status, report.reason)
            }
            // An expiry reminder for a revoked keybox would only add noise.
            prefs.edit()
                .putBoolean(PREF_REVOKED_NOTIFIED, true)
                .putBoolean(PREF_EXPIRY_MUTED, true)
                .apply()
            return true
        }

        val expiry = keybox.expiry ?: return true
        val now = System.currentTimeMillis()
        if (status == KeyboxInspector.Status.EXPIRED || now >= expiry) return true
        if (prefs.getBoolean(PREF_EXPIRY_MUTED, false)) return true
        val due = dueLeads(ctx, expiry, now, doneLeads(prefs))
        if (due.isNotEmpty()) {
            postExpiry(ctx, expiry, now, due.min())
            markLeadsDone(prefs, due)
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
        schedulePeriodicCheck(ctx)
        val now = System.currentTimeMillis()
        val next = keybox.expiry?.let { expiry ->
            leadDays(ctx).map { triggerAt(expiry, it) }
                .filter { it - EARLY_TOLERANCE_MS > now }
                .minOrNull()
        }
        if (next == null) cancelAlarm(ctx) else scheduleAlarm(ctx, next)
        return keybox
    }

    /** Selected lead times whose moment has come and that were not reminded about yet. */
    private fun dueLeads(ctx: Context, expiry: Long, now: Long, done: Set<Int>): List<Int> =
        leadDays(ctx).filter { it !in done && now >= triggerAt(expiry, it) - EARLY_TOLERANCE_MS }

    private fun doneLeads(prefs: SharedPreferences): Set<Int> =
        prefs.getStringSet(PREF_LEADS_DONE, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.toSet()

    private fun markLeadsDone(prefs: SharedPreferences, leads: Collection<Int>) {
        val done = doneLeads(prefs) + leads
        prefs.edit().putStringSet(PREF_LEADS_DONE, done.map { it.toString() }.toSet()).apply()
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
            cancel(JOB_PERIODIC)
            cancel(JOB_NOW)
        }
        cancelAlarm(ctx)
        cancelNotification(ctx, NOTIFY_EXPIRY)
        cancelNotification(ctx, NOTIFY_REVOKED)
    }

    private fun schedulePeriodicCheck(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val interval = TimeUnit.HOURS.toMillis(checkIntervalHours(ctx).toLong())
        // Re-scheduling restarts the period, so leave a pending job alone unless the user
        // picked a different interval.
        if (js.getPendingJob(JOB_PERIODIC)?.intervalMillis == interval) return
        js.schedule(
            JobInfo.Builder(JOB_PERIODIC, ComponentName(ctx, KeyboxCheckJobService::class.java))
                .setPeriodic(interval)
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

    private fun postExpiry(ctx: Context, expiry: Long, now: Long, lead: Int) {
        val days = ((expiry - now + EARLY_TOLERANCE_MS) / DAY_MS).toInt().coerceIn(1, lead)
        post(
            ctx, NOTIFY_EXPIRY,
            title = ctx.resources.getQuantityString(R.plurals.ts_alert_expiry_title, days, days),
            text = ctx.getString(R.string.ts_alert_expiry_text, KeyboxInspector.formatDate(expiry)),
        )
    }

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
