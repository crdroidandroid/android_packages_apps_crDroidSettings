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
import android.graphics.fonts.FontFamilyUpdateRequest
import android.graphics.fonts.FontFileUpdateRequest
import android.graphics.fonts.FontFileUtil
import android.graphics.fonts.FontManager
import android.graphics.fonts.FontStyle
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import com.android.internal.statusbar.IStatusBarService
import com.android.internal.util.crdroid.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

/**
 * Installs a user-supplied .ttf / .otf as the system font through [FontManager] and the
 * Google Sans Flex font overlay.
 *
 * Nothing here shows UI. Every blocking call runs on [Dispatchers.IO] and outcomes come back
 * as values, so the caller decides how to report them and the work is safe to run outside a
 * screen's lifecycle.
 */
class ExternalFontInstaller(context: Context) {

    companion object {
        private const val TAG = "ExternalFontInstaller"
        private const val CUSTOM_FONT_FILE = "cust_font.ttf"
        private const val TEMP_PREVIEW_FONT = "preview_font.ttf"
        private const val MAX_FONT_BYTES = 32L * 1024L * 1024L
        private const val OVERLAY_CATEGORY_FONT = "android.theme.customization.font"
        private const val DEFAULT_FONT_PACKAGE = "android"
        const val DEFAULT_FONT_FAMILY = "google-sans-flex"
        private const val DEFAULT_FONT_OVERLAY = "com.android.theme.font.googlesansflex"

        /** FontManager result code used when the request could not even be sent. */
        const val RESULT_EXCEPTION = -1

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
        /** The file is gone or no longer parses as a font. */
        data object Invalid : InstallResult
        /** FontManager rejected the update; [code] is a FontManager.RESULT_* value. */
        data class Failed(val code: Int) : InstallResult
    }

    private val context: Context = context.applicationContext
    private val fontManager: FontManager = this.context.getSystemService(FontManager::class.java)

    // ---- Preview ---------------------------------------------------------------------------

    /**
     * Copies [uri] into the cache and parses it. Returns null when the file can't be read,
     * is larger than 32 MB or isn't a font Android can load.
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

    /** Typeface of the installed custom font, for previews. Null if none is installed. */
    fun loadInstalledTypeface(): Typeface? = runCatching {
        // filesDir is current; cacheDir is where older builds kept the copy.
        listOf(File(context.filesDir, CUSTOM_FONT_FILE), File(context.cacheDir, CUSTOM_FONT_FILE))
            .firstOrNull { it.isFile && it.length() > 0L }
            ?.let { Typeface.createFromFile(it) }
    }.getOrNull()

    // ---- Install ---------------------------------------------------------------------------

    /**
     * Pushes [preview] into the system font table as Google Sans Flex. This does not switch
     * the font overlay yet; call [updateThemeOverlays] afterwards. That write makes the
     * running activity recreate, so record any state that has to survive that first.
     */
    suspend fun install(preview: FontFilePreview): InstallResult = withContext(Dispatchers.IO) {
        val source = preview.file
        val postScriptName = extractPostScriptName(source) ?: return@withContext InstallResult.Invalid

        // Keep a private copy for the picker's preview; the system keeps its own in /data/fonts.
        val target = File(context.filesDir, CUSTOM_FONT_FILE)
        val staged = File(context.filesDir, "$CUSTOM_FONT_FILE.tmp")
        val copied = runCatching {
            source.copyTo(staged, overwrite = true)
            if (target.exists() && !target.delete()) error("Could not replace installed font copy")
            if (!staged.renameTo(target)) error("Could not move installed font copy")
        }.onFailure {
            Log.e(TAG, "Failed to stage font file", it)
            staged.delete()
        }.isSuccess
        if (!copied) return@withContext InstallResult.Failed(RESULT_EXCEPTION)

        val code = applyFontToSystem(target, postScriptName)
        if (code != FontManager.RESULT_SUCCESS) {
            target.delete()
            return@withContext InstallResult.Failed(code)
        }
        File(context.cacheDir, CUSTOM_FONT_FILE).delete()
        cleanupPreviewFont()
        InstallResult.Success(postScriptName)
    }

    /**
     * Points the font overlay category at Google Sans Flex so the replaced family is used.
     * SystemUI's theme controller applies it, which recreates the running activities.
     */
    fun updateThemeOverlays() {
        val resolver = context.contentResolver
        val userId = UserHandle.myUserId()

        val json = runCatching {
            val current = Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                userId
            )
            if (current.isNullOrEmpty()) JSONObject() else JSONObject(current)
        }.getOrElse { JSONObject() }

        runCatching {
            if (json.has(OVERLAY_CATEGORY_FONT)) json.remove(OVERLAY_CATEGORY_FONT)
            json.put(OVERLAY_CATEGORY_FONT, DEFAULT_FONT_OVERLAY)

            Settings.Secure.putStringForUser(
                resolver,
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                json.toString(),
                userId
            )
        }.onFailure { Log.e(TAG, "Failed to persist custom font overlay", it) }
    }

    /** Drops the custom font from the system font table. Blocking: call off the main thread. */
    fun resetFontUpdates() {
        fontManager.clearUpdates()
        ThemeUtils.getInstance(context).setOverlayEnabled(
            OVERLAY_CATEGORY_FONT, DEFAULT_FONT_PACKAGE, DEFAULT_FONT_PACKAGE
        )
        File(context.filesDir, CUSTOM_FONT_FILE).delete()
        File(context.cacheDir, CUSTOM_FONT_FILE).delete()
        cleanupPreviewFont()
    }

    // ---- Internals -------------------------------------------------------------------------

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

    /** Returns a FontManager.RESULT_* code, or [RESULT_EXCEPTION]. */
    private fun applyFontToSystem(fontFile: File, postScriptName: String): Int = runCatching {
        ParcelFileDescriptor.open(fontFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val fontFileUpdateRequest = FontFileUpdateRequest(pfd, ByteArray(0))

            val fontRegular = FontFamilyUpdateRequest.Font.Builder(
                postScriptName,
                FontStyle()
            ).build()

            val familyRegular = FontFamilyUpdateRequest.FontFamily.Builder(
                DEFAULT_FONT_FAMILY,
                listOf(fontRegular)
            ).build()

            val updateRequest = FontFamilyUpdateRequest.Builder()
                .addFontFileUpdateRequest(fontFileUpdateRequest)
                .addFontFamily(familyRegular)
                .build()

            fontManager.updateFontFamily(updateRequest, fontManager.fontConfig.configVersion)
        }
    }.onFailure {
        Log.e(TAG, "Failed to update system font", it)
    }.getOrDefault(RESULT_EXCEPTION).also {
        if (it != FontManager.RESULT_SUCCESS) Log.e(TAG, "updateFontFamily returned $it")
    }

    private fun cleanupPreviewFont() {
        try {
            val previewFile = File(context.cacheDir, TEMP_PREVIEW_FONT)
            if (previewFile.exists()) previewFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup preview font", e)
        }
    }
}
