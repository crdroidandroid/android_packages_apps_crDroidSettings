/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui.fonts

import android.app.Application
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontFileUtil
import android.graphics.fonts.FontManager
import android.graphics.text.TextRunShaper
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

class EmojiPicker : SettingsPreferenceFragment() {

    private lateinit var viewModel: EmojiPickerViewModel

    private val fontFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.inspect(it) } }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[EmojiPickerViewModel::class.java]
        requireActivity().title = getString(R.string.emoji_picker_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = spoofComposeView(requireContext()).apply {
        setContent {
            SettingsTheme {
                EmojiPickerContent(
                    vm = viewModel,
                    onPickFont = { fontFilePicker.launch(FONT_MIME_TYPES) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    private companion object {
        val FONT_MIME_TYPES = arrayOf(
            "font/ttf", "font/sfnt", "application/x-font-ttf", "application/font-sfnt",
            "application/octet-stream",
        )
    }
}

/** A picked emoji font, copied into the cache and checked. */
internal class EmojiFilePreview(
    val file: File,
    val postScriptName: String,
    val displayName: String?,
    val sizeBytes: Long,
    val typeface: Typeface,
)

/**
 * Talks to the custom emoji API of FontManager. The font sits in front of the system emoji
 * fallbacks, so it takes effect in every process started afterwards and needs no reboot.
 */
internal class EmojiRepository(context: Context) {

    private val context: Context = context.applicationContext
    private val fontManager: FontManager? = this.context.getSystemService(FontManager::class.java)

    fun customEmojiName(): String? = runCatching { fontManager?.customEmojiName }
        .onFailure { Log.e(TAG, "getCustomEmojiName failed", it) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }

    fun installedTypeface(): Typeface? =
        File(INSTALLED_EMOJI).takeIf { it.isFile }?.let { fileTypeface(it) }

    /** Null when the file can't be read, is too large, or has no emoji in it. Blocking. */
    fun inspect(uri: Uri): EmojiFilePreview? {
        val file = copyToCache(uri) ?: return null
        val postScriptName = runCatching {
            FileInputStream(file).use { fis ->
                val buffer = fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, fis.channel.size())
                FontFileUtil.getPostScriptName(buffer, 0)
            }
        }.getOrNull()
        val typeface = fileTypeface(file)
        if (postScriptName.isNullOrEmpty() || typeface == null || !hasEmoji(typeface, file)) {
            file.delete()
            return null
        }
        return EmojiFilePreview(file, postScriptName, displayName(uri), file.length(), typeface)
    }

    fun discardPreview() {
        File(context.cacheDir, PREVIEW_FILE).delete()
    }

    /** Returns a FontManager.RESULT_* code or [ExternalFontInstaller.RESULT_EXCEPTION]. Blocking. */
    fun install(preview: EmojiFilePreview): Int {
        val manager = fontManager ?: return ExternalFontInstaller.RESULT_EXCEPTION
        return runCatching {
            ParcelFileDescriptor.open(preview.file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                manager.installCustomEmoji(it)
            }
        }.onFailure { Log.e(TAG, "installCustomEmoji failed", it) }
            .getOrDefault(ExternalFontInstaller.RESULT_EXCEPTION)
            .also { if (it == FontManager.RESULT_SUCCESS) preview.file.delete() }
    }

    fun clear(): Int = runCatching { fontManager?.clearCustomEmoji() }
        .onFailure { Log.e(TAG, "clearCustomEmoji failed", it) }
        .getOrNull() ?: ExternalFontInstaller.RESULT_EXCEPTION

    /** Number of app files written, or a negative FontManager.RESULT_* code. Blocking. */
    fun applyToApps(): Int = runCatching { fontManager?.applyCustomEmojiToApps() }
        .onFailure { Log.e(TAG, "applyCustomEmojiToApps failed", it) }
        .getOrNull() ?: ExternalFontInstaller.RESULT_EXCEPTION

    private fun fileTypeface(file: File): Typeface? = runCatching {
        val family = FontFamily.Builder(Font.Builder(file).build()).build()
        Typeface.CustomFallbackBuilder(family).build()
    }.onFailure { Log.w(TAG, "Can't load $file", it) }.getOrNull()

    /** Paint.hasGlyph would also see the system fallback; the shaper says which font drew it. */
    private fun hasEmoji(typeface: Typeface, file: File): Boolean = runCatching {
        val paint = Paint().apply { this.typeface = typeface }
        val glyphs = TextRunShaper.shapeTextRun(
            PROBE_EMOJI, 0, PROBE_EMOJI.length, 0, PROBE_EMOJI.length, 0f, 0f, false, paint,
        )
        glyphs.glyphCount() > 0 && glyphs.getFont(0).file?.absolutePath == file.absolutePath
    }.getOrDefault(false)

    private fun copyToCache(uri: Uri): File? {
        val target = File(context.cacheDir, PREVIEW_FILE)
        val temp = File(context.cacheDir, "$PREVIEW_FILE.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_EMOJI_BYTES) { "Emoji font exceeds size limit" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null
            check(temp.length() > 0L && temp.renameTo(target))
            target
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $uri", e)
            temp.delete()
            null
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    companion object {
        private const val TAG = "EmojiRepository"
        private const val PREVIEW_FILE = "emoji_preview.ttf"
        private const val PROBE_EMOJI = "😀" // 😀

        /** Where FontManagerService keeps the installed font (UpdatableFontDir). */
        private const val INSTALLED_EMOJI = "/data/fonts/emoji/emoji.ttf"

        /** Same limit as UpdatableFontDir.MAX_CUSTOM_EMOJI_FILE_SIZE. */
        const val MAX_EMOJI_BYTES = 128L * 1024L * 1024L
    }
}

internal sealed interface EmojiBusy {
    data object Reading : EmojiBusy
    data class Installing(val name: String) : EmojiBusy
    data object Removing : EmojiBusy
    data object UpdatingApps : EmojiBusy
}

internal sealed interface EmojiDialog {
    class Install(val preview: EmojiFilePreview) : EmojiDialog
    data object Remove : EmojiDialog
    data object UpdateApps : EmojiDialog
    data class AppsUpdated(val files: Int) : EmojiDialog
    data class Error(val message: String) : EmojiDialog
}

class EmojiPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application
    private val repo = EmojiRepository(application)
    private val tracker = FontRestartTracker(application, RESTART_PREFS)

    internal var loading by mutableStateOf(true)
        private set
    internal var customName by mutableStateOf<String?>(null)
        private set
    internal var customTypeface by mutableStateOf<Typeface?>(null)
        private set
    internal var pending by mutableStateOf<PendingRestart?>(null)
        private set
    internal var busy by mutableStateOf<EmojiBusy?>(null)
        private set
    internal var dialog by mutableStateOf<EmojiDialog?>(null)
        private set

    /** Set when the user chose "restart now"; carried out by the screen, which has an activity. */
    internal var restartRequested by mutableStateOf(false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val (name, typeface, restart) = withContext(Dispatchers.IO) {
                val name = repo.customEmojiName()
                Triple(name, if (name != null) repo.installedTypeface() else null, tracker.pending())
            }
            customName = name
            customTypeface = typeface
            pending = restart
            loading = false
        }
    }

    private fun run(state: EmojiBusy, block: suspend () -> Unit) {
        if (busy != null) return
        busy = state
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = null
                refresh()
            }
        }
    }

    internal fun inspect(uri: Uri) = run(EmojiBusy.Reading) {
        val preview = withContext(Dispatchers.IO) { repo.inspect(uri) }
        if (preview == null) {
            dialog = EmojiDialog.Error(app.getString(R.string.emoji_error_invalid_file))
        } else {
            dialog = EmojiDialog.Install(preview)
        }
    }

    internal fun install(preview: EmojiFilePreview, restartNow: Boolean) {
        dialog = null
        run(EmojiBusy.Installing(preview.postScriptName)) {
            val code = withContext(Dispatchers.IO) { repo.install(preview) }
            finish(code, preview.postScriptName, restartNow)
        }
    }

    internal fun remove(restartNow: Boolean) {
        dialog = null
        run(EmojiBusy.Removing) {
            val code = withContext(Dispatchers.IO) { repo.clear() }
            finish(code, app.getString(R.string.font_default), restartNow)
        }
    }

    private fun finish(code: Int, label: String, restartNow: Boolean) {
        if (code != FontManager.RESULT_SUCCESS) {
            dialog = EmojiDialog.Error(errorMessage(code))
            return
        }
        tracker.markPending(RestartKind.SYSTEMUI, label)
        if (restartNow) restartRequested = true
    }

    internal fun updateApps() {
        dialog = null
        run(EmojiBusy.UpdatingApps) {
            val result = withContext(Dispatchers.IO) { repo.applyToApps() }
            dialog = if (result >= 0) EmojiDialog.AppsUpdated(result)
            else EmojiDialog.Error(errorMessage(result))
        }
    }

    internal fun showDialog(next: EmojiDialog) {
        if (busy == null) dialog = next
    }

    internal fun dismissDialog() {
        if (dialog is EmojiDialog.Install) repo.discardPreview()
        dialog = null
    }

    internal fun restartNow(activityContext: Context) {
        restartRequested = false
        if (pending == null) return
        tracker.restartNow(RestartKind.SYSTEMUI, activityContext)
        pending = null
    }

    private fun errorMessage(code: Int): String = when (code) {
        ExternalFontInstaller.RESULT_EXCEPTION -> app.getString(R.string.emoji_error_generic, code)
        FontManager.RESULT_ERROR_INVALID_FONT_FILE -> app.getString(R.string.emoji_error_invalid_file)
        FontManager.RESULT_ERROR_FONT_NOT_FOUND -> app.getString(R.string.emoji_error_not_installed)
        else -> FontOps.errorMessage(app, code)
    }

    private companion object {
        const val RESTART_PREFS = "emoji_picker_state"
    }
}
