/*
 * Copyright (C) 2025 AxionOS
 * SPDX-FileCopyrightText: crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.preferences

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontFileUtil
import android.graphics.fonts.FontManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import android.provider.OpenableColumns
import android.util.Log
import com.android.internal.statusbar.IStatusBarService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

/**
 * Installs a user-supplied .ttf / .otf as the system UI font through the custom-font API of
 * [FontManager] ("base: Support custom fonts for system wide").
 *
 * The framework stores the file under /data/fonts with fs-verity, registers it as the
 * "sans-serif" custom family and puts it in front of every UI family (sans-serif*,
 * google-sans*, variable-*, roboto*, source-sans-pro*). So, unlike the previous
 * implementation, no family is replaced and no font overlay has to be forced on. The only
 * overlay rule is that a font overlay whose family isn't a UI family would hide the custom
 * font, so the caller switches the overlay back to Default.
 *
 * Nothing here shows UI. Every blocking call runs on [Dispatchers.IO] and outcomes come back
 * as values, so the caller decides how to report them.
 */
class ExternalFontInstaller(context: Context) {

    companion object {
        private const val TAG = "ExternalFontInstaller"
        private const val CUSTOM_FONT_FILE = "cust_font.ttf"
        private const val TEMP_PREVIEW_FONT = "preview_font.ttf"

        /** Same limit as UpdatableFontDir.MAX_CUSTOM_FONT_FILE_SIZE. */
        const val MAX_FONT_BYTES = 64L * 1024L * 1024L

        /** Family the framework registers the custom font under. */
        const val CUSTOM_FONT_FAMILY = "sans-serif"

        /**
         * Our own code for "the call didn't get through" (exception, missing service). Kept
         * clear of FontManager's RESULT_* values, which use -1..-9 and -10001..-10008.
         */
        const val RESULT_EXCEPTION = -20000

        fun rebootDevice() {
            runCatching {
                val binder = ServiceManager.getService("statusbar")
                val statusBarService = IStatusBarService.Stub.asInterface(binder)
                statusBarService.reboot(false, "system_font_change")
            }.onFailure {
                Log.e(TAG, "Failed to reboot device via statusbar service", it)
            }
        }
    }

    /** A picked font file, copied into the cache and checked, ready to preview or install. */
    class FontFilePreview internal constructor(
        internal val file: File,
        val postScriptName: String,
        val displayName: String?,
        val sizeBytes: Long,
        val typeface: Typeface,
    )

    sealed interface InstallResult {
        data class Success(val postScriptName: String) : InstallResult
        /** The file is gone, too large, or no longer parses as a font. */
        data object Invalid : InstallResult
        /** FontManager rejected the request; [code] is a FontManager.RESULT_* value. */
        data class Failed(val code: Int) : InstallResult
    }

    private val context: Context = context.applicationContext
    private val fontManager: FontManager? = this.context.getSystemService(FontManager::class.java)

    // ---- State -----------------------------------------------------------------------------

    /**
     * PostScript name of the active custom font, straight from FontManagerService. It changes
     * as soon as a font is installed or cleared, before the reboot, so it matches what the
     * next boot will use. Null when none is active or the service can't be asked.
     */
    fun customFontName(): String? = runCatching { fontManager?.customFontName }
        .onFailure { Log.e(TAG, "getCustomFontName failed", it) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }

    // ---- Preview ---------------------------------------------------------------------------

    /**
     * Copies [uri] into the cache and parses it. Returns null when the file can't be read,
     * is larger than [MAX_FONT_BYTES] or isn't a font Android can load.
     */
    suspend fun inspect(uri: Uri): FontFilePreview? = withContext(Dispatchers.IO) {
        val file = copyUriToCache(uri, TEMP_PREVIEW_FONT) ?: return@withContext null
        val postScriptName = extractPostScriptName(file)
        val typeface = postScriptName?.let {
            runCatching { Typeface.createFromFile(file) }
                .onFailure { e -> Log.e(TAG, "Failed to create preview typeface for $it", e) }
                .getOrNull()
        }
        if (postScriptName == null || typeface == null) {
            file.delete()
            return@withContext null
        }
        FontFilePreview(
            file = file,
            postScriptName = postScriptName,
            displayName = queryDisplayName(uri),
            sizeBytes = file.length(),
            typeface = typeface,
        )
    }

    /** Deletes the cached preview copy when the user backs out of installing it. */
    fun discardPreview() = cleanupPreviewFont()

    /**
     * Typeface of the installed custom font, for previews. Uses the private copy kept at
     * install time, and falls back to the system's copy in /data/fonts when that is gone.
     */
    fun loadInstalledTypeface(): Typeface? {
        val local = listOf(File(context.filesDir, CUSTOM_FONT_FILE), File(context.cacheDir, CUSTOM_FONT_FILE))
            .firstOrNull { it.isFile && it.length() > 0L }
        val file = local ?: customFontName()?.let { systemFontFile(it) }
        return file?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
    }

    // ---- Install / clear -------------------------------------------------------------------

    /**
     * Hands [preview] to FontManagerService. On success the font is part of the system font
     * map for processes started from now on; running processes keep their font until they
     * restart, hence the reboot prompt.
     */
    suspend fun install(preview: FontFilePreview): InstallResult = withContext(Dispatchers.IO) {
        val source = preview.file
        if (!source.isFile || source.length() > MAX_FONT_BYTES) return@withContext InstallResult.Invalid
        val postScriptName = extractPostScriptName(source) ?: return@withContext InstallResult.Invalid
        val manager = fontManager ?: return@withContext InstallResult.Failed(RESULT_EXCEPTION)

        val code = runCatching {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                manager.installCustomFont(pfd)
            }
        }.onFailure { Log.e(TAG, "installCustomFont failed", it) }
            .getOrDefault(RESULT_EXCEPTION)
        if (code != FontManager.RESULT_SUCCESS) {
            Log.e(TAG, "installCustomFont returned $code for $postScriptName")
            return@withContext InstallResult.Failed(code)
        }

        // Private copy for the picker's preview; the system keeps its own in /data/fonts.
        runCatching {
            val target = File(context.filesDir, CUSTOM_FONT_FILE)
            val staged = File(context.filesDir, "$CUSTOM_FONT_FILE.tmp")
            source.copyTo(staged, overwrite = true)
            if (!staged.renameTo(target)) {
                staged.delete()
                error("Could not move preview copy")
            }
        }.onFailure { Log.w(TAG, "Could not keep a preview copy of the custom font", it) }
        File(context.cacheDir, CUSTOM_FONT_FILE).delete()
        cleanupPreviewFont()
        InstallResult.Success(postScriptName)
    }

    /**
     * Removes the custom font from the system font configuration. Blocking: call off the main
     * thread. Returns a FontManager.RESULT_* value or [RESULT_EXCEPTION].
     */
    fun clearCustomFont(): Int {
        val code = runCatching { fontManager?.clearCustomFont() ?: RESULT_EXCEPTION }
            .onFailure { Log.e(TAG, "clearCustomFont failed", it) }
            .getOrDefault(RESULT_EXCEPTION)
        if (code == FontManager.RESULT_SUCCESS) {
            File(context.filesDir, CUSTOM_FONT_FILE).delete()
            File(context.cacheDir, CUSTOM_FONT_FILE).delete()
            cleanupPreviewFont()
        }
        return code
    }

    // ---- Internals -------------------------------------------------------------------------

    /**
     * The installed custom font's file, looked up in FontManager.getFontConfig(). The config
     * type (android.text.FontConfig) is walked by reflection: its family layout changed in
     * Android 14 and some getters are hidden.
     */
    private fun systemFontFile(postScriptName: String): File? = runCatching {
        val config = fontManager?.fontConfig ?: return@runCatching null
        val named = (config.call("getNamedFamilyLists") as? List<*>).orEmpty()
            .flatMap { (it.call("getFamilies") as? List<*>).orEmpty() }
        val legacy = (config.call("getFontFamilies") as? List<*>).orEmpty()
        (named + legacy).asSequence()
            .flatMap { (it.call("getFontList") as? List<*>).orEmpty().asSequence() }
            .firstOrNull { it.call("getPostScriptName") == postScriptName }
            ?.call("getFile") as? File
    }.getOrNull()?.takeIf { it.isFile }

    private fun Any?.call(method: String): Any? =
        this?.javaClass?.getMethod(method)?.invoke(this)

    private fun copyUriToCache(uri: Uri, fileName: String): File? {
        val cacheFile = File(context.cacheDir, fileName)
        val tempFile = File(context.cacheDir, "$fileName.tmp")
        return try {
            tempFile.delete()
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    FileOutputStream(tempFile, false).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_FONT_BYTES) {
                                throw IllegalArgumentException("Font file exceeds size limit")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
            }

            if (tempFile.length() <= 0L) {
                tempFile.delete()
                return null
            }
            if (cacheFile.exists() && !cacheFile.delete()) {
                throw IllegalStateException("Could not replace cached font")
            }
            if (!tempFile.renameTo(cacheFile)) {
                throw IllegalStateException("Could not install cached font")
            }
            cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to cache", e)
            tempFile.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun extractPostScriptName(fontFile: File): String? = runCatching {
        if (!fontFile.isFile || fontFile.length() <= 0L || fontFile.length() > MAX_FONT_BYTES) {
            return@runCatching null
        }
        FileInputStream(fontFile).use { fis ->
            val size = fis.channel.size()
            if (size <= 0L || size > MAX_FONT_BYTES) return@use null
            val buffer = fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
            FontFileUtil.getPostScriptName(buffer, 0)
        }
    }.onFailure {
        Log.e(TAG, "Failed to inspect font file", it)
    }.getOrNull()

    private fun cleanupPreviewFont() {
        try {
            val previewFile = File(context.cacheDir, TEMP_PREVIEW_FONT)
            if (previewFile.exists()) previewFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup preview font", e)
        }
    }
}
