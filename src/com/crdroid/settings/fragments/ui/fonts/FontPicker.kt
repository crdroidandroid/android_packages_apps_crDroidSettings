/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui.fonts

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontManager
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.crdroid.settings.fragments.misc.spoofComposeView
import com.crdroid.settings.preferences.ExternalFontInstaller
import com.crdroid.settings.preferences.ExternalFontInstaller.InstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FontPicker : SettingsPreferenceFragment() {

    private lateinit var viewModel: FontPickerViewModel

    private val fontFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.inspect(it) } }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Survives the activity recreation a font change causes, open dialogs included.
        viewModel = ViewModelProvider(this)[FontPickerViewModel::class.java]
        requireActivity().title = getString(R.string.theme_customization_font_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = spoofComposeView(requireContext()).apply {
        setContent {
            SettingsTheme {
                FontPickerContent(
                    vm = viewModel,
                    onAddFont = { fontFilePicker.launch(FONT_MIME_TYPES) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private val FONT_MIME_TYPES = arrayOf(
            "font/ttf", "font/otf", "font/sfnt", "font/collection",
            "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt",
            "application/vnd.ms-opentype", "application/octet-stream",
        )

        @JvmStatic
        fun summary(context: Context): CharSequence {
            val repo = FontRepository(context)
            val custom = repo.customFontName()
            val applied = repo.appliedPackage()
            val label = custom ?: repo.label(applied)
            val typeface = if (custom != null) {
                ExternalFontInstaller(context).loadInstalledTypeface()
            } else {
                repo.typeface(applied)
            }
            val text = when (FontRestartTracker(context).pending()?.kind) {
                RestartKind.REBOOT -> context.getString(R.string.font_summary_pending_reboot, label)
                RestartKind.SYSTEMUI -> context.getString(R.string.font_summary_pending_systemui, label)
                null -> label
            }
            return SpannableString(text).apply {
                val start = text.indexOf(label)
                if (typeface != null && start >= 0) {
                    setSpan(
                        TypefaceSpan(typeface), start, start + label.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
    }
}

internal sealed interface FontDialog {
    /** Switch to a system font. [fromCustom]: a custom font is active, so a reboot is needed. */
    data class Apply(val option: FontOption, val fromCustom: Boolean) : FontDialog

    class Install(val preview: ExternalFontInstaller.FontFilePreview) : FontDialog

    /** A failed install or switch, explained. Toasts cut these messages short. */
    data class Error(val message: String) : FontDialog
}

class FontPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application
    private val repo = FontRepository(application)
    private val installer = ExternalFontInstaller(application)
    private val tracker = FontRestartTracker(application)

    internal var options by mutableStateOf<List<FontOption>>(emptyList())
        private set
    internal var loading by mutableStateOf(true)
        private set
    internal var appliedPackage by mutableStateOf(FontRepository.DEFAULT_PACKAGE)
        private set
    internal var customName by mutableStateOf<String?>(null)
        private set
    internal var customTypeface by mutableStateOf<Typeface?>(null)
        private set
    internal var pending by mutableStateOf<PendingRestart?>(null)
        private set
    internal var dialog by mutableStateOf<FontDialog?>(null)
        private set

    internal val selectedKey: String get() = if (customName != null) CUSTOM_KEY else appliedPackage

    internal val currentLabel: String
        get() = customName
            ?: options.firstOrNull { it.packageName == appliedPackage }?.label
            ?: app.getString(R.string.font_default)

    internal val currentTypeface: Typeface?
        get() = if (customName != null) customTypeface
        else options.firstOrNull { it.packageName == appliedPackage }?.typeface

    private data class Snapshot(
        val options: List<FontOption>,
        val applied: String,
        val custom: String?,
        val customTypeface: Typeface?,
        val pending: PendingRestart?,
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) {
                val custom = repo.customFontName()
                Snapshot(
                    options = repo.loadOptions(),
                    applied = repo.appliedPackage(),
                    custom = custom,
                    customTypeface = if (custom != null) installer.loadInstalledTypeface() else null,
                    pending = tracker.pending(),
                )
            }
            options = s.options
            appliedPackage = s.applied
            customName = s.custom
            customTypeface = s.customTypeface
            pending = s.pending
            loading = false
        }
    }

    // ---- Selection -------------------------------------------------------------------------

    internal fun onFontClicked(option: FontOption) {
        if (FontOps.busy != null || option.packageName == selectedKey) return
        dialog = FontDialog.Apply(option, fromCustom = customName != null)
    }

    internal fun onRemoveCustom() {
        val default = options.firstOrNull { it.isDefault }
            ?: FontOption(FontRepository.DEFAULT_PACKAGE, app.getString(R.string.font_default), null)
        dialog = FontDialog.Apply(default, fromCustom = true)
    }

    internal fun apply(option: FontOption, fromCustom: Boolean, restartNow: Boolean) {
        dialog = null
        val kind = if (fromCustom) RestartKind.REBOOT else RestartKind.SYSTEMUI
        FontOps.run(FontBusy.Applying(option.packageName)) {
            val previous = tracker.pending()
            // Recorded first: the overlay change below recreates this activity.
            tracker.markPending(kind, option.label)
            val code = withContext(Dispatchers.IO) {
                repo.applyOverlay(option.packageName, fromCustom)
            }
            if (code != FontManager.RESULT_SUCCESS) {
                tracker.restore(previous)
                dialog = FontDialog.Error(
                    if (code == ExternalFontInstaller.RESULT_EXCEPTION) {
                        app.getString(R.string.toast_failed_apply_font)
                    } else {
                        FontOps.errorMessage(app, code)
                    },
                )
                return@run
            }
            if (restartNow) {
                when (tracker.pending()?.kind ?: kind) {
                    RestartKind.REBOOT -> ExternalFontInstaller.rebootDevice()
                    RestartKind.SYSTEMUI -> FontOps.restartRequest = RestartKind.SYSTEMUI
                }
            }
        }
    }

    // ---- Custom font -----------------------------------------------------------------------

    internal fun inspect(uri: Uri) {
        if (FontOps.busy != null) return
        FontOps.run(FontBusy.Reading) {
            val preview = installer.inspect(uri)
            if (preview == null) {
                FontOps.toast(app, R.string.toast_invalid_font_file)
            } else {
                dialog = FontDialog.Install(preview)
            }
        }
    }

    internal fun cancelInstall() {
        dialog = null
        installer.discardPreview()
    }

    internal fun install(preview: ExternalFontInstaller.FontFilePreview, rebootNow: Boolean) {
        dialog = null
        FontOps.run(FontBusy.Installing(preview.postScriptName)) {
            when (val result = installer.install(preview)) {
                is InstallResult.Success -> {
                    repo.setCustomFontName(result.postScriptName)
                    // Recorded before the overlay switch, which recreates this activity.
                    tracker.markPending(RestartKind.REBOOT, result.postScriptName)
                    // The custom font sits in front of the UI families; an overlay pointing the
                    // UI at another family would hide it, so go back to Default.
                    val overlayReset = withContext(Dispatchers.IO) { repo.resetOverlayToDefault() }
                    if (!overlayReset) FontOps.toast(app, R.string.font_overlay_reset_failed)
                    if (rebootNow) ExternalFontInstaller.rebootDevice()
                }
                InstallResult.Invalid -> FontOps.toast(app, R.string.toast_invalid_font_file)
                is InstallResult.Failed -> dialog = FontDialog.Error(
                    if (result.code == ExternalFontInstaller.RESULT_EXCEPTION) {
                        app.getString(R.string.font_install_failed, result.code)
                    } else {
                        FontOps.errorMessage(app, result.code)
                    },
                )
            }
        }
    }

    // ---- Restart ---------------------------------------------------------------------------

    internal fun dismissDialog() {
        if (dialog is FontDialog.Install) cancelInstall() else dialog = null
    }

    internal fun restartNow(activityContext: Context) {
        val kind = pending?.kind ?: return
        tracker.restartNow(kind, activityContext)
        if (kind == RestartKind.SYSTEMUI) pending = null
    }

    /** Runs a SystemUI restart requested by [apply] once a live screen can host it. */
    internal fun consumeRestartRequest(activityContext: Context) {
        val kind = FontOps.restartRequest ?: return
        FontOps.restartRequest = null
        tracker.restartNow(kind, activityContext)
        pending = null
    }

    internal companion object {
        const val CUSTOM_KEY = "__custom_font__"
    }
}
