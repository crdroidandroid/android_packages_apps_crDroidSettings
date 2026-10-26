/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui.fonts

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ApkAssets
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.fonts.FontManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.internal.util.crdroid.ThemeUtils
import com.android.internal.util.crdroid.ThemeUtils.FONT_KEY
import com.android.settings.R
import com.crdroid.settings.preferences.ExternalFontInstaller
import com.crdroid.settings.utils.SystemUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

// ---------------------------------------------------------------------------
// Font sources
// ---------------------------------------------------------------------------

internal data class FontOption(
    val packageName: String,
    val label: String,
    val typeface: Typeface?,
) {
    val isDefault: Boolean get() = packageName == FontRepository.DEFAULT_PACKAGE
}

/** Reads and switches the font overlay category. Shared by the picker and its preference. */
internal class FontRepository(context: Context) {

    private val context: Context = context.applicationContext
    private val themeUtils: ThemeUtils = ThemeUtils.getInstance(this.context)
    private val installer = ExternalFontInstaller(this.context)

    fun appliedPackage(): String =
        themeUtils.getOverlayInfos(CATEGORY).firstOrNull { it.isEnabled }?.packageName
            ?: DEFAULT_PACKAGE

    /**
     * PostScript name of the active custom font, null when none is active.
     *
     * FontManagerService is the source of truth. The custom_font_name setting is only a
     * mirror for anything else that reads it, and is corrected here when it disagrees (for
     * example a name left over from the previous implementation, whose font the new backend
     * drops on the first boot).
     */
    fun customFontName(): String? {
        val name = installer.customFontName()
        val mirrored = Settings.Secure.getString(context.contentResolver, CUSTOM_FONT_NAME_KEY)
            ?.takeIf { it.isNotEmpty() }
        if (mirrored != name) setCustomFontName(name)
        return name
    }

    /** Updates the custom_font_name mirror; see [customFontName]. */
    fun setCustomFontName(name: String?) {
        Settings.Secure.putString(context.contentResolver, CUSTOM_FONT_NAME_KEY, name.orEmpty())
    }

    /** Default first, then the overlays by label. Blocking: resolves overlay resources. */
    fun loadOptions(): List<FontOption> {
        val options = themeUtils.getOverlayPackagesForCategory(CATEGORY, DEFAULT_PACKAGE)
            .distinct()
            .map { FontOption(it, label(it), typeface(it)) }
        val (default, rest) = options.partition { it.isDefault }
        return default + rest.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun label(pkg: String): String {
        if (pkg == DEFAULT_PACKAGE) return context.getString(R.string.font_default)
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun typeface(pkg: String): Typeface? = try {
        if (pkg == DEFAULT_PACKAGE) {
            stockTypeface()
        } else {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier(BODY_FONT_RES, "string", pkg)
            if (id == 0) null else Typeface.create(res.getString(id), Typeface.NORMAL)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Typeface load failed for pkg: $pkg", e)
        null
    }

    /**
     * Switches to [newPackage]. With [fromCustom] the custom font is first cleared from the
     * system font configuration. Blocking. Returns FontManager.RESULT_SUCCESS or the code of
     * the step that failed ([ExternalFontInstaller.RESULT_EXCEPTION] for the overlay).
     */
    fun applyOverlay(newPackage: String, fromCustom: Boolean): Int {
        if (fromCustom) {
            val code = installer.clearCustomFont()
            if (code != FontManager.RESULT_SUCCESS) return code
            setCustomFontName(null)
        }
        return if (setOverlay(newPackage)) FontManager.RESULT_SUCCESS
        else ExternalFontInstaller.RESULT_EXCEPTION
    }

    /**
     * Turns font overlays off, so the UI families (which the custom font is placed in front
     * of) are what the system uses. Blocking. Returns false if it failed.
     */
    fun resetOverlayToDefault(): Boolean =
        appliedPackage() == DEFAULT_PACKAGE || setOverlay(DEFAULT_PACKAGE)

    /** Same ThemeUtils calls, in the same order, as the previous picker. */
    private fun setOverlay(newPackage: String): Boolean = try {
        val oldPackage = appliedPackage()
        themeUtils.setOverlayEnabled(CATEGORY, oldPackage, oldPackage)
        themeUtils.setOverlayEnabled(CATEGORY, newPackage, DEFAULT_PACKAGE)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to apply font overlay $newPackage", e)
        false
    }

    companion object {
        private const val TAG = "FontRepository"
        private const val BODY_FONT_RES = "config_bodyFontFamily"
        private const val FRAMEWORK_RES_APK = "/system/framework/framework-res.apk"
        private const val FALLBACK_BODY_FONT = "google-sans-flex"

        @Volatile
        private var stockBodyFont: String? = null

        @Volatile
        private var stockFace: Typeface? = null

        /**
         * The real stock font for "Default", read once per process.
         *
         * The family name can't be resolved through the running system: the font table is
         * rebuilt at boot for the font that was active then, and an installed custom font is
         * placed in front of every UI family, so Typeface.create() returns the boot-time or
         * custom font. [StockFontFiles] reads the font XML files on disk
         * instead and loads the stock font file directly. The family name is the fallback.
         */
        private fun stockTypeface(): Typeface? {
            stockFace?.let { return it }
            val family = stockBodyFontFamily() ?: return null
            val face = StockFontFiles.typeface(family)
                ?: runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrNull()
                    .also { Log.w(TAG, "Stock font file for $family not found, using family name") }
            return face?.also { stockFace = it }
        }

        /**
         * config_bodyFontFamily as framework-res.apk itself defines it.
         *
         * Resources.getSystem() and the "android" package's resources both have the enabled
         * font overlay applied, so reading it there gave "Default" the family of whatever
         * font was active. Here framework-res is loaded into its own AssetManager, with no
         * overlays, so the value is the real stock family. It doesn't change at runtime, so
         * it's read once per process.
         */
        private fun stockBodyFontFamily(): String? {
            stockBodyFont?.let { return it }
            val family = try {
                AssetManager.Builder()
                    .addApkAssets(ApkAssets.loadFromPath(FRAMEWORK_RES_APK))
                    .build()
                    .use { assets ->
                        val system = Resources.getSystem()
                        @Suppress("DEPRECATION")
                        val res = Resources(assets, system.displayMetrics, system.configuration)
                        val id = res.getIdentifier(BODY_FONT_RES, "string", DEFAULT_PACKAGE)
                        if (id == 0) null else res.getString(id)
                    }
            } catch (t: Throwable) {
                Log.e(TAG, "Could not read stock $BODY_FONT_RES from $FRAMEWORK_RES_APK", t)
                null
            }
            // Last resort: the platform's default UI family.
            return (family?.takeIf { it.isNotBlank() } ?: FALLBACK_BODY_FONT)
                .also { stockBodyFont = it }
        }

        const val CATEGORY = FONT_KEY
        const val DEFAULT_PACKAGE = "android"
        const val CUSTOM_FONT_NAME_KEY = "custom_font_name"
    }
}

// ---------------------------------------------------------------------------
// Pending restart
// ---------------------------------------------------------------------------

internal enum class RestartKind { SYSTEMUI, REBOOT }

internal data class PendingRestart(val kind: RestartKind, val fontLabel: String)

/**
 * Remembers that a font change still needs a SystemUI restart or a reboot.
 *
 * Applying a font recreates the Settings activity (the overlay changes its assets), which
 * is what used to dismiss the old restart AlertDialog a moment after it appeared. The
 * request is therefore written here *before* the change is applied, and every screen reads
 * it back, so the prompt survives the recreation, leaving the screen, or the process dying.
 * It clears itself once the device has rebooted or SystemUI has restarted.
 */
internal class FontRestartTracker(context: Context) {

    private val context: Context = context.applicationContext
    private val prefs: SharedPreferences =
        this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pending(): PendingRestart? {
        val kind = prefs.getString(KEY_KIND, null)
            ?.let { runCatching { RestartKind.valueOf(it) }.getOrNull() }
            ?: return null
        val rebooted = prefs.getInt(KEY_BOOT_COUNT, -1) != bootCount() ||
            SystemClock.elapsedRealtime() < prefs.getLong(KEY_MARKED_AT, 0L)
        if (rebooted) {
            clear()
            return null
        }
        if (kind == RestartKind.SYSTEMUI) {
            val markedPid = prefs.getInt(KEY_SYSTEMUI_PID, 0)
            val currentPid = systemUiPid()
            if (markedPid > 0 && currentPid > 0 && markedPid != currentPid) {
                clear()
                return null
            }
        }
        return PendingRestart(kind, prefs.getString(KEY_LABEL, null).orEmpty())
    }

    /** A reboot already pending is never downgraded to a SystemUI restart. */
    fun markPending(kind: RestartKind, fontLabel: String) {
        val merged = if (pending()?.kind == RestartKind.REBOOT) RestartKind.REBOOT else kind
        prefs.edit()
            .putString(KEY_KIND, merged.name)
            .putString(KEY_LABEL, fontLabel)
            .putInt(KEY_BOOT_COUNT, bootCount())
            .putLong(KEY_MARKED_AT, SystemClock.elapsedRealtime())
            .putInt(KEY_SYSTEMUI_PID, systemUiPid())
            .commit() // synchronous: an activity recreation or a reboot follows right away
    }

    /** Puts back what [pending] returned before a change that then failed. */
    fun restore(previous: PendingRestart?) {
        if (previous == null) clear() else markPending(previous.kind, previous.fontLabel)
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    /** [activityContext] is passed to SystemUtils, which may show its own progress UI. */
    fun restartNow(kind: RestartKind, activityContext: Context) {
        when (kind) {
            RestartKind.REBOOT -> ExternalFontInstaller.rebootDevice()
            RestartKind.SYSTEMUI -> {
                clear()
                SystemUtils.restartSystemUI(activityContext)
            }
        }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun systemUiPid(): Int = runCatching {
        context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
            ?.firstOrNull { it.processName == SYSTEMUI_PROCESS }?.pid ?: 0
    }.getOrDefault(0)

    companion object {
        private const val PREFS = "font_picker_state"
        private const val KEY_KIND = "pending_restart_kind"
        private const val KEY_LABEL = "pending_restart_label"
        private const val KEY_BOOT_COUNT = "pending_restart_boot_count"
        private const val KEY_MARKED_AT = "pending_restart_marked_at"
        private const val KEY_SYSTEMUI_PID = "pending_restart_systemui_pid"
        private const val SYSTEMUI_PROCESS = "com.android.systemui"
    }
}

// ---------------------------------------------------------------------------
// Long-running operations
// ---------------------------------------------------------------------------

internal sealed interface FontBusy {
    data object Reading : FontBusy
    data class Applying(val packageName: String) : FontBusy
    data class Installing(val name: String) : FontBusy
}

/**
 * Applying or installing a font recreates the activity partway through, so the work runs
 * in a process-wide scope instead of the fragment's or view model's. Screens observe
 * [busy] and [generation] (bumped when an operation ends) as Compose state.
 */
internal object FontOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var busy by mutableStateOf<FontBusy?>(null)
        private set

    var generation by mutableIntStateOf(0)
        private set

    /**
     * A SystemUI restart the user asked for. Consumed by whichever picker screen is alive,
     * because SystemUtils needs an activity context and the one that asked is gone by then.
     */
    var restartRequest by mutableStateOf<RestartKind?>(null)

    fun run(state: FontBusy, block: suspend () -> Unit) {
        if (busy != null) return
        busy = state
        scope.launch {
            try {
                block()
            } finally {
                busy = null
                generation++
            }
        }
    }

    /** User-facing explanation of a FontManager.RESULT_* code. */
    fun errorMessage(context: Context, code: Int): String = when (code) {
        FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED ->
            context.getString(R.string.font_error_updater_disabled)
        FontManager.RESULT_ERROR_INVALID_FONT_FILE ->
            context.getString(R.string.font_error_invalid_file)
        FontManager.RESULT_ERROR_INVALID_FONT_NAME ->
            context.getString(R.string.font_error_invalid_name)
        FontManager.RESULT_ERROR_DOWNGRADING ->
            context.getString(R.string.font_error_downgrading)
        FontManager.RESULT_ERROR_VERIFICATION_FAILURE ->
            context.getString(R.string.font_error_verification)
        FontManager.RESULT_ERROR_FAILED_TO_WRITE_FONT_FILE,
        FontManager.RESULT_ERROR_FAILED_UPDATE_CONFIG ->
            context.getString(R.string.font_error_storage)
        else -> context.getString(R.string.font_install_failed, code)
    }

    fun toast(context: Context, resId: Int, vararg args: Any) {
        Toast.makeText(context, context.getString(resId, *args), Toast.LENGTH_SHORT).show()
    }
}
