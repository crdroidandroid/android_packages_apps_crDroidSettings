/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui.fonts

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
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
import android.os.Process
import android.os.UserHandle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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

internal data class EmojiOption(
    val key: String,
    val label: String,
    val typeface: Typeface? = null,
    val preview: File? = null,
    val sizeBytes: Long = 0L,
    val entry: EmojiCatalog.Entry? = null,
    val downloaded: Boolean = false,
) {
    val isDefault: Boolean get() = key == EmojiRepository.DEFAULT_KEY
}

internal object EmojiCatalog {

    private const val TAG = "EmojiCatalog"
    private const val BASE_URL =
        "https://raw.githubusercontent.com/crdroidandroid/android_vendor_themes/17.0/emoji/"
    private const val INDEX = "emoji.json"
    private const val SUPPORTED_VERSION = 1
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_INDEX_BYTES = 256L * 1024L
    private const val MAX_PREVIEW_BYTES = 2L * 1024L * 1024L
    private val SAFE_NAME = Regex("[A-Za-z0-9._-]+")
    private val SHA256 = Regex("[0-9a-fA-F]{64}")

    class Entry(
        val id: String,
        val name: String,
        val file: String,
        val size: Long,
        val sha256: String,
        val preview: String?,
    )

    class DownloadException(message: String) : IOException(message)
    class ChecksumException(message: String) : IOException(message)

    fun fetch(): List<Entry> {
        val json = JSONObject(String(fetchBytes(INDEX, MAX_INDEX_BYTES), Charsets.UTF_8))
        if (json.optInt("version", 0) > SUPPORTED_VERSION) {
            Log.w(TAG, "Catalog version ${json.optInt("version")} is newer than this build")
        }
        val array = json.optJSONArray("emoji") ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until array.length()).mapNotNull { i ->
            runCatching { parse(array.getJSONObject(i)) }
                .onFailure { Log.w(TAG, "Skipping catalog entry $i", it) }
                .getOrNull()
                ?.takeIf { seen.add(it.id) }
        }
    }

    private fun parse(o: JSONObject): Entry {
        val id = o.getString("id")
        val file = o.getString("file")
        val size = o.getLong("size")
        val sha256 = o.getString("sha256")
        val preview = o.optString("preview").takeIf { it.isNotEmpty() }
        require(SAFE_NAME.matches(id)) { "Bad id $id" }
        require(SAFE_NAME.matches(file) && !file.startsWith(".")) { "Bad file $file" }
        require(preview == null || (SAFE_NAME.matches(preview) && !preview.startsWith(".")))
        require(size in 1..EmojiRepository.MAX_EMOJI_BYTES) { "Bad size $size" }
        require(SHA256.matches(sha256)) { "Bad sha256" }
        return Entry(id, o.optString("name").ifEmpty { id }, file, size, sha256.lowercase(),
            preview)
    }

    fun preview(entry: Entry, dir: File): File? {
        val name = entry.preview ?: return null
        val target = File(dir, name)
        if (target.isFile && target.length() > 0) return target
        return runCatching {
            dir.mkdirs()
            val temp = File(dir, "$name.tmp")
            temp.writeBytes(fetchBytes(name, MAX_PREVIEW_BYTES))
            check(temp.renameTo(target))
            target
        }.onFailure { Log.w(TAG, "Preview $name unavailable", it) }.getOrNull()
    }

    fun download(entry: Entry, target: File, onProgress: (Float) -> Unit) {
        val temp = File(target.parentFile, "${target.name}.part")
        var done = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            open(entry.file).use { conn ->
                conn.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > entry.size) {
                                throw ChecksumException("${entry.file} is larger than listed")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            val percent = (total * 100 / entry.size).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (total != entry.size || hash != entry.sha256) {
                throw ChecksumException("${entry.file} doesn't match the catalog")
            }
            if (!temp.renameTo(target)) throw DownloadException("Can't move ${entry.file}")
            done = true
        } catch (e: ChecksumException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            throw DownloadException("Download of ${entry.file} failed: $e")
        } finally {
            if (!done) temp.delete()
        }
    }

    private fun fetchBytes(name: String, limit: Long): ByteArray = open(name).use { conn ->
        conn.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw DownloadException("$name exceeds $limit bytes")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private class Connection(val connection: HttpURLConnection) : Closeable {
        val inputStream get() = connection.inputStream
        override fun close() = connection.disconnect()
    }

    private fun open(name: String): Connection {
        val conn = URL(BASE_URL + name).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.useCaches = false
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw DownloadException("HTTP $code for $name")
        }
        return Connection(conn)
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

    private val downloads = this.context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)

    fun activeEmojiFile(): File? = runCatching {
        fontManager?.fontConfig?.fontFamilies
            ?.flatMap { it.fontList }
            ?.firstOrNull { it.file.parent == INSTALLED_EMOJI_DIR }
            ?.file
    }.onFailure { Log.e(TAG, "getFontConfig failed", it) }.getOrNull()

    fun downloadedId(file: File): String? =
        downloads.getString(KEY_INSTALLED_ID, null)
            ?.takeIf { downloads.getString(KEY_INSTALLED_PATH, null) == file.path }

    fun typeface(file: File): Typeface? {
        val stamp = "${file.length()}:${file.lastModified()}"
        typefaces[file.path]?.takeIf { it.first == stamp }?.let { return it.second }
        return fileTypeface(file)?.also { typefaces[file.path] = stamp to it }
    }

    fun stockTypeface(): Typeface? =
        File(STOCK_EMOJI).takeIf { it.isFile }?.let { fileTypeface(it) }

    private val downloadDir = File(this.context.filesDir, DOWNLOAD_DIR)

    fun catalog(reload: Boolean = false): List<EmojiOption> {
        val entries = cachedCatalog?.takeUnless { reload } ?: run {
            val previewDir = File(context.cacheDir, PREVIEW_DIR)
            EmojiCatalog.fetch()
                .map { it to EmojiCatalog.preview(it, previewDir) }
                .also {
                    cachedCatalog = it
                    deleteStaleDownloads(it.map { (entry, _) -> entry })
                }
        }
        return entries
            .map { (entry, preview) ->
                val local = localFile(entry)
                EmojiOption(
                    key = entry.id,
                    label = entry.name,
                    typeface = local?.let { typeface(it) },
                    preview = preview,
                    sizeBytes = entry.size,
                    entry = entry,
                    downloaded = local != null,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun download(entry: EmojiCatalog.Entry, onProgress: (Float) -> Unit): Int {
        if (localFile(entry) != null) return FontManager.RESULT_SUCCESS
        downloadDir.mkdirs()
        val file = File(downloadDir, entry.file)
        val hashFile = hashFile(entry)
        hashFile.delete()
        try {
            EmojiCatalog.download(entry, file, onProgress)
        } catch (e: EmojiCatalog.ChecksumException) {
            Log.e(TAG, "Emoji download rejected", e)
            return RESULT_CHECKSUM_MISMATCH
        } catch (e: Exception) {
            Log.e(TAG, "Emoji download failed", e)
            return RESULT_DOWNLOAD_FAILED
        }
        // check() deletes the file if it isn't a usable emoji font.
        check(file, entry.name) ?: return FontManager.RESULT_ERROR_INVALID_FONT_FILE
        return runCatching { hashFile.writeText(entry.sha256) }
            .map { FontManager.RESULT_SUCCESS }
            .onFailure { Log.e(TAG, "Can't record download of ${entry.file}", it); file.delete() }
            .getOrDefault(RESULT_DOWNLOAD_FAILED)
    }

    fun installDownloaded(entry: EmojiCatalog.Entry): Int {
        val file = localFile(entry) ?: return FontManager.RESULT_ERROR_FONT_NOT_FOUND
        val code = installFile(file)
        if (code == FontManager.RESULT_SUCCESS) {
            downloads.edit()
                .putString(KEY_INSTALLED_ID, entry.id)
                .putString(KEY_INSTALLED_PATH, activeEmojiFile()?.path)
                .apply()
        }
        return code
    }

    fun deleteDownload(entry: EmojiCatalog.Entry) {
        File(downloadDir, entry.file).let { typefaces.remove(it.path); it.delete() }
        hashFile(entry).delete()
    }

    private fun localFile(entry: EmojiCatalog.Entry): File? {
        val file = File(downloadDir, entry.file)
        val hash = runCatching { hashFile(entry).readText().trim() }.getOrNull()
        return file.takeIf { it.isFile && it.length() == entry.size && hash == entry.sha256 }
    }

    private fun hashFile(entry: EmojiCatalog.Entry) = File(downloadDir, entry.file + HASH_SUFFIX)

    private fun deleteStaleDownloads(entries: List<EmojiCatalog.Entry>) {
        val keep = entries.flatMap { listOf(it.file, it.file + HASH_SUFFIX) }.toSet()
        downloadDir.listFiles()?.filter { it.name !in keep }?.forEach {
            typefaces.remove(it.path)
            it.delete()
        }
    }

    /** Null when the file can't be read, is too large, or has no emoji in it. Blocking. */
    fun inspect(uri: Uri): EmojiFilePreview? {
        val file = copyToCache(uri) ?: return null
        return check(file, displayName(uri))
    }

    private fun check(file: File, displayName: String?): EmojiFilePreview? {
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
        return EmojiFilePreview(file, postScriptName, displayName, file.length(), typeface)
    }

    fun discardPreview() {
        File(context.cacheDir, PREVIEW_FILE).delete()
    }

    /** Returns a FontManager.RESULT_* code or [ExternalFontInstaller.RESULT_EXCEPTION]. Blocking. */
    fun install(preview: EmojiFilePreview): Int =
        installFile(preview.file).also {
            if (it == FontManager.RESULT_SUCCESS) preview.file.delete()
        }

    private fun installFile(file: File): Int {
        val manager = fontManager ?: return ExternalFontInstaller.RESULT_EXCEPTION
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                manager.installCustomEmoji(it)
            }
        }.onFailure { Log.e(TAG, "installCustomEmoji failed", it) }
            .getOrDefault(ExternalFontInstaller.RESULT_EXCEPTION)
    }

    fun clear(): Int = runCatching { fontManager?.clearCustomEmoji() }
        .onFailure { Log.e(TAG, "clearCustomEmoji failed", it) }
        .getOrNull() ?: ExternalFontInstaller.RESULT_EXCEPTION

    /** Number of app files written, or a negative FontManager.RESULT_* code. Blocking. */
    fun applyToApps(): Int = runCatching { fontManager?.applyCustomEmojiToApps() }
        .onFailure { Log.e(TAG, "applyCustomEmojiToApps failed", it) }
        .getOrNull() ?: ExternalFontInstaller.RESULT_EXCEPTION

    /**
     * Apps like Gboard cache rendered emoji until their process dies. killUid rather than
     * forceStopPackage so apps aren't left stopped and keep their alarms and pushes. Blocking.
     */
    fun killRunningApps() {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val uids = runCatching { am.runningAppProcesses }
            .onFailure { Log.e(TAG, "getRunningAppProcesses failed", it) }
            .getOrNull().orEmpty()
            .filter { Process.isApplicationUid(it.uid) }
            .filterNot { proc -> proc.pkgList.orEmpty().any { isPersistent(it) } }
            .filterNot { GMS_PACKAGE in it.pkgList.orEmpty() }
            .mapTo(HashSet()) { it.uid }
        val service = ActivityManager.getService()
        uids.forEach { uid ->
            runCatching {
                service.killUid(UserHandle.getAppId(uid), UserHandle.getUserId(uid), KILL_REASON)
            }.onFailure { Log.w(TAG, "killUid $uid failed", it) }
        }
    }

    private fun isPersistent(pkg: String): Boolean = runCatching {
        (context.packageManager.getApplicationInfo(pkg, 0).flags and
            ApplicationInfo.FLAG_PERSISTENT) != 0
    }.getOrDefault(false)

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
        private const val KILL_REASON = "system emoji changed"
        private const val PROBE_EMOJI = "😀" // 😀

        private const val GMS_PACKAGE = "com.google.android.gms"

        /** Where FontManagerService keeps the installed font (UpdatableFontDir). */
        private const val INSTALLED_EMOJI_DIR = "/data/fonts/emoji"

        @Volatile
        private var cachedCatalog: List<Pair<EmojiCatalog.Entry, File?>>? = null

        private val typefaces = ConcurrentHashMap<String, Pair<String, Typeface>>()

        private const val PREVIEW_DIR = "emoji_previews"
        private const val DOWNLOAD_DIR = "emoji_downloads"
        private const val HASH_SUFFIX = ".sha256"
        private const val DOWNLOAD_PREFS = "emoji_downloads"
        private const val KEY_INSTALLED_ID = "installed_id"
        private const val KEY_INSTALLED_PATH = "installed_path"
        private const val STOCK_EMOJI = "/system/fonts/NotoColorEmoji.ttf"

        const val DEFAULT_KEY = "__default_emoji__"
        const val CUSTOM_KEY = "__custom_emoji__"

        const val RESULT_DOWNLOAD_FAILED = -20001
        const val RESULT_CHECKSUM_MISMATCH = -20002

        /** Same limit as UpdatableFontDir.MAX_CUSTOM_EMOJI_FILE_SIZE. */
        const val MAX_EMOJI_BYTES = 128L * 1024L * 1024L
    }
}

internal sealed interface EmojiBusy {
    data object Reading : EmojiBusy
    data class Installing(val name: String) : EmojiBusy
    data class Applying(val key: String, val label: String) : EmojiBusy
    data class Downloading(val key: String, val label: String) : EmojiBusy
    data object Removing : EmojiBusy
    data object UpdatingApps : EmojiBusy
}

internal object EmojiOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var busy by mutableStateOf<EmojiBusy?>(null)
        private set

    var generation by mutableIntStateOf(0)
        private set

    var downloadProgress by mutableStateOf<Float?>(null)

    /** "Restart now" chosen in a dialog; carried out by whichever screen is alive. */
    var restartRequested by mutableStateOf(false)

    fun run(state: EmojiBusy, block: suspend () -> Unit) {
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

    /** Background work that needn't block the screen and must outlive it. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

internal sealed interface EmojiDialog {
    data class Apply(val option: EmojiOption) : EmojiDialog
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
    internal var options by mutableStateOf<List<EmojiOption>>(emptyList())
        private set
    internal var selectedKey by mutableStateOf(EmojiRepository.DEFAULT_KEY)
        private set
    internal var customName by mutableStateOf<String?>(null)
        private set
    internal var customTypeface by mutableStateOf<Typeface?>(null)
        private set
    private var activeTypeface by mutableStateOf<Typeface?>(null)
    internal var pending by mutableStateOf<PendingRestart?>(null)
        private set
    internal var dialog by mutableStateOf<EmojiDialog?>(null)
        private set
    internal var catalogFailed by mutableStateOf(false)
        private set

    internal val busy: EmojiBusy? get() = EmojiOps.busy

    internal val currentLabel: String
        get() = customName
            ?: options.firstOrNull { it.key == selectedKey }?.label
            ?: app.getString(R.string.font_default)

    internal val currentTypeface: Typeface?
        get() = if (selectedKey == EmojiRepository.DEFAULT_KEY) {
            options.firstOrNull { it.isDefault }?.typeface
        } else {
            activeTypeface
        }

    private class Snapshot(
        val options: List<EmojiOption>,
        val selectedKey: String,
        val customName: String?,
        val customTypeface: Typeface?,
        val activeTypeface: Typeface?,
        val pending: PendingRestart?,
        val catalogFailed: Boolean,
    )

    init {
        refresh()
    }

    fun refresh(reloadCatalog: Boolean = false) {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) {
                val default = EmojiOption(
                    EmojiRepository.DEFAULT_KEY, app.getString(R.string.font_default),
                    repo.stockTypeface(),
                )
                val catalog = runCatching { repo.catalog(reloadCatalog) }
                    .onFailure { Log.w(TAG, "Emoji catalog unavailable", it) }
                val name = repo.customEmojiName()
                val active = if (name != null) repo.activeEmojiFile() else null
                val downloaded = active?.let { repo.downloadedId(it) }
                val key = when {
                    name == null -> EmojiRepository.DEFAULT_KEY
                    downloaded != null -> downloaded
                    else -> EmojiRepository.CUSTOM_KEY
                }
                val custom = key == EmojiRepository.CUSTOM_KEY
                val activeTypeface = active?.let { repo.typeface(it) }
                val catalogOptions = catalog.getOrDefault(emptyList()).map {
                    if (it.key == key && it.typeface == null) it.copy(typeface = activeTypeface)
                    else it
                }
                Snapshot(
                    options = listOf(default) + catalogOptions,
                    selectedKey = key,
                    customName = if (custom) name else null,
                    customTypeface = if (custom) activeTypeface else null,
                    activeTypeface = activeTypeface,
                    pending = tracker.pending(),
                    catalogFailed = catalog.isFailure,
                )
            }
            options = s.options
            selectedKey = s.selectedKey
            customName = s.customName
            customTypeface = s.customTypeface
            activeTypeface = s.activeTypeface
            pending = s.pending
            catalogFailed = s.catalogFailed
            loading = false
        }
    }

    internal fun onOptionClicked(option: EmojiOption) {
        if (busy != null || option.key == selectedKey) return
        dialog = if (option.isDefault) EmojiDialog.Remove else EmojiDialog.Apply(option)
    }

    internal fun applyCatalog(option: EmojiOption, restartNow: Boolean) {
        val entry = option.entry ?: return
        dialog = null
        change(EmojiBusy.Applying(option.key, option.label), option.label, restartNow) {
            val downloaded = downloadWithProgress(entry)
            if (downloaded != FontManager.RESULT_SUCCESS) downloaded
            else repo.installDownloaded(entry)
        }
    }

    internal fun download(option: EmojiOption) {
        val entry = option.entry ?: return
        dialog = null
        EmojiOps.run(EmojiBusy.Downloading(option.key, option.label)) {
            val code = withContext(Dispatchers.IO) { downloadWithProgress(entry) }
            if (code == FontManager.RESULT_SUCCESS) {
                FontOps.toast(app, R.string.emoji_download_done, option.label)
            } else {
                dialog = EmojiDialog.Error(errorMessage(code))
            }
        }
    }

    internal fun deleteDownload(option: EmojiOption) {
        val entry = option.entry ?: return
        dialog = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteDownload(entry) }
            refresh()
        }
    }

    /** Blocking; publishes progress through [EmojiOps.downloadProgress]. */
    private fun downloadWithProgress(entry: EmojiCatalog.Entry): Int {
        EmojiOps.downloadProgress = 0f
        return try {
            repo.download(entry) { EmojiOps.downloadProgress = it }
        } finally {
            EmojiOps.downloadProgress = null
        }
    }

    internal fun inspect(uri: Uri) = EmojiOps.run(EmojiBusy.Reading) {
        val preview = withContext(Dispatchers.IO) { repo.inspect(uri) }
        dialog = if (preview == null) {
            EmojiDialog.Error(app.getString(R.string.emoji_error_invalid_file))
        } else {
            EmojiDialog.Install(preview)
        }
    }

    internal fun install(preview: EmojiFilePreview, restartNow: Boolean) {
        dialog = null
        change(EmojiBusy.Installing(preview.postScriptName), preview.postScriptName, restartNow) {
            repo.install(preview)
        }
    }

    internal fun remove(restartNow: Boolean) {
        dialog = null
        change(EmojiBusy.Removing, app.getString(R.string.font_default), restartNow) {
            repo.clear()
        }
    }

    private fun change(state: EmojiBusy, label: String, restartNow: Boolean, op: () -> Int) {
        EmojiOps.run(state) {
            val previous = tracker.pending()
            tracker.markPending(RestartKind.SYSTEMUI, label)
            val code = withContext(Dispatchers.IO) { op() }
            if (code != FontManager.RESULT_SUCCESS) {
                tracker.restore(previous)
                dialog = EmojiDialog.Error(errorMessage(code))
                return@run
            }
            if (restartNow) EmojiOps.restartRequested = true
        }
    }

    internal fun updateApps() {
        dialog = null
        EmojiOps.run(EmojiBusy.UpdatingApps) {
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
        if (tracker.pending() == null) return
        tracker.restartNow(RestartKind.SYSTEMUI, activityContext)
        pending = null
        EmojiOps.launch { withContext(Dispatchers.IO) { repo.killRunningApps() } }
    }

    internal fun consumeRestartRequest(activityContext: Context) {
        if (!EmojiOps.restartRequested) return
        EmojiOps.restartRequested = false
        restartNow(activityContext)
    }

    private fun errorMessage(code: Int): String = when (code) {
        ExternalFontInstaller.RESULT_EXCEPTION -> app.getString(R.string.emoji_error_generic, code)
        FontManager.RESULT_ERROR_INVALID_FONT_FILE -> app.getString(R.string.emoji_error_invalid_file)
        FontManager.RESULT_ERROR_FONT_NOT_FOUND -> app.getString(R.string.emoji_error_not_installed)
        EmojiRepository.RESULT_DOWNLOAD_FAILED -> app.getString(R.string.emoji_error_download)
        EmojiRepository.RESULT_CHECKSUM_MISMATCH -> app.getString(R.string.emoji_error_checksum)
        else -> FontOps.errorMessage(app, code)
    }

    private companion object {
        const val TAG = "EmojiPickerViewModel"
        const val RESTART_PREFS = "emoji_picker_state"
    }
}
