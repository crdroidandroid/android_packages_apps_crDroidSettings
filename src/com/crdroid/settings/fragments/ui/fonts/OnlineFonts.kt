/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui.fonts

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import com.android.settings.R
import com.crdroid.settings.preferences.ExternalFontInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.CancellationException

/**
 * One Google Fonts family. [variants] are "<weight>" or "<weight>i" (italic), e.g. "400",
 * "700i", sorted by weight with upright before italic.
 */
internal data class OnlineFont(
    val family: String,
    val category: String,
    val variants: List<String>,
    val popularity: Int,
    val designers: List<String>,
    /** Scripts the family covers, e.g. "latin", "cyrillic". */
    val subsets: List<String>,
    /** Google's last-modified date for the family; changes when its files are updated. */
    val version: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("family", family)
        .put("category", category)
        .put("variants", JSONArray(variants))
        .put("popularity", popularity)
        .put("designers", JSONArray(designers))
        .put("subsets", JSONArray(subsets))
        .put("version", version)

    /** Regular when there is one, else the upright style closest to it. */
    val defaultVariant: String
        get() = if ("400" in variants) "400"
        else variants.filterNot { it.isItalic() }
            .minByOrNull { kotlin.math.abs(it.weight() - 400) }
            ?: variants.first()

    companion object
}

/** Saved entries are read back from [OnlineFont.toJson]. */
internal fun OnlineFont.Companion.fromJson(o: JSONObject): OnlineFont {
    fun JSONObject.strings(key: String): List<String> =
        optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val variants = o.strings("variants")
    require(variants.isNotEmpty() && variants.all { Regex("[1-9]00i?").matches(it) })
    return OnlineFont(
        family = o.getString("family"),
        category = o.optString("category"),
        variants = variants,
        popularity = o.optInt("popularity", Int.MAX_VALUE),
        designers = o.strings("designers"),
        subsets = o.strings("subsets"),
        version = o.optString("version"),
    )
}

/** File and folder name for a family: letters and digits only. */
internal fun onlineFontFileName(family: String) = family.replace(Regex("[^A-Za-z0-9]"), "_")

internal fun String.isItalic(): Boolean = endsWith("i")

internal fun String.weight(): Int = removeSuffix("i").toIntOrNull() ?: 400

/** Categories as fonts.google.com names them; null means all of them. */
internal enum class OnlineFontCategory(val apiName: String?, val labelRes: Int) {
    ALL(null, R.string.online_fonts_category_all),
    SANS_SERIF("Sans Serif", R.string.online_fonts_category_sans),
    SERIF("Serif", R.string.online_fonts_category_serif),
    DISPLAY("Display", R.string.online_fonts_category_display),
    HANDWRITING("Handwriting", R.string.online_fonts_category_handwriting),
    MONOSPACE("Monospace", R.string.online_fonts_category_mono),
}

/**
 * Lists and downloads Google Fonts without an API key,
 */
internal object GoogleFontsCatalog {

    private const val TAG = "GoogleFontsCatalog"
    private const val METADATA_URL = "https://fonts.google.com/metadata/fonts"
    private const val CSS_URL = "https://fonts.googleapis.com/css"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_METADATA_BYTES = 16L * 1024L * 1024L
    private const val MAX_CSS_BYTES = 64L * 1024L
    private const val MAX_PREVIEW_BYTES = 1L * 1024L * 1024L
    private const val METADATA_FILE = "metadata.json"
    private const val METADATA_MAX_AGE_MS = 24L * 60L * 60L * 1000L

    private val VARIANT = Regex("[1-9]00i?")
    private val SUBSET = Regex("[a-z0-9-]+")
    private val TTF_URL =
        Regex("""url\((https://fonts\.gstatic\.com/[^)\s'"]+)\)\s*format\(['"]truetype['"]\)""")

    class FetchException(message: String) : IOException(message)

    @Volatile
    private var cached: List<OnlineFont>? = null

    /**
     * The family list saved by the last [fetchFamilies], most popular first, or null when
     * there is none. Read from disk once per process. Blocking.
     */
    fun savedFamilies(dir: File): List<OnlineFont>? {
        cached?.let { return it }
        val file = File(dir, METADATA_FILE)
        if (!file.isFile) return null
        return runCatching { parse(file.readText()) }
            .onFailure { Log.w(TAG, "Saved font list unreadable", it); file.delete() }
            .getOrNull()
            ?.also { cached = it }
    }

    /** True when the saved list is missing or more than a day old. */
    fun isStale(dir: File): Boolean {
        val file = File(dir, METADATA_FILE)
        return !file.isFile || System.currentTimeMillis() - file.lastModified() >= METADATA_MAX_AGE_MS
    }

    /** Downloads the list, saves it for [savedFamilies] and returns it. Blocking. */
    fun fetchFamilies(dir: File): List<OnlineFont> {
        val json = String(fetchBytes(METADATA_URL, MAX_METADATA_BYTES), Charsets.UTF_8)
        val fonts = parse(json)
        dir.mkdirs()
        val temp = File(dir, "$METADATA_FILE.tmp")
        temp.writeText(json)
        if (!temp.renameTo(File(dir, METADATA_FILE))) temp.delete()
        cached = fonts
        return fonts
    }

    private fun parse(raw: String): List<OnlineFont> {
        // The endpoint has served its JSON behind an XSSI guard (")]}'") at times.
        val start = raw.indexOf('{')
        if (start < 0) throw FetchException("Font list isn't JSON")
        val list = JSONObject(raw.substring(start)).getJSONArray("familyMetadataList")
        val fonts = ArrayList<OnlineFont>(list.length())
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            runCatching { parseFamily(o) }
                .onFailure { Log.w(TAG, "Skipping font entry $i", it) }
                .getOrNull()
                ?.let(fonts::add)
        }
        // Family names key the grid, so they have to be unique.
        return fonts.distinctBy { it.family }.sortedBy { it.popularity }
    }

    private fun parseFamily(o: JSONObject): OnlineFont? {
        val family = o.getString("family").takeIf { it.isNotBlank() } ?: return null
        // The custom font replaces UI text, so it has to cover Latin. Color fonts (COLR)
        // aren't something the system UI font can be.
        val subsets = o.optJSONArray("subsets")
            ?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?.filter { SUBSET.matches(it) && it != "menu" }
            .orEmpty()
        val latin = "latin" in subsets
        val color = (o.optJSONArray("colorCapabilities")?.length() ?: 0) > 0
        if (!latin || color) return null
        val variants = o.optJSONObject("fonts")?.keys()?.asSequence()
            ?.filter { VARIANT.matches(it) }
            ?.sortedWith(compareBy<String> { it.weight() }.thenBy { it.isItalic() })
            ?.toList()
            .orEmpty()
        if (variants.isEmpty()) return null
        val designers = o.optJSONArray("designers")
            ?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }
            .orEmpty()
        return OnlineFont(
            family = family,
            category = o.optString("category"),
            variants = variants,
            popularity = o.optInt("popularity", Int.MAX_VALUE),
            designers = designers,
            subsets = subsets,
            version = o.optString("lastModified"),
        )
    }

    /**
     * Downloads the [text]-only cut of [font]'s default style to [target], replacing it in
     * one step, so a reader never sees half a file. Blocking.
     */
    fun fetchPreview(font: OnlineFont, text: String, target: File) {
        val url = ttfUrl(cssUrl(font.family, font.defaultVariant, subsets = emptyList(), text = text))
        download(url, target, MAX_PREVIEW_BYTES, onProgress = null)
    }

    /** Same as [fetchPreview] for the full file of one style, reporting progress 0..1. */
    fun fetchVariant(font: OnlineFont, variant: String, target: File, onProgress: ((Float) -> Unit)?) {
        require(variant in font.variants) { "${font.family} has no style $variant" }
        // Without subset= the API serves Latin only; the system font should cover every
        // script the family has.
        val url = ttfUrl(cssUrl(font.family, variant, subsets = font.subsets, text = null))
        download(url, target, ExternalFontInstaller.MAX_FONT_BYTES, onProgress)
    }

    private fun cssUrl(family: String, variant: String, subsets: List<String>, text: String?): String {
        val query = StringBuilder("$CSS_URL?family=")
            .append(encode(family)).append(':').append(variant)
        if (subsets.isNotEmpty()) query.append("&subset=").append(subsets.joinToString(","))
        if (text != null) query.append("&text=").append(encode(text))
        return query.toString()
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun ttfUrl(cssUrl: String): String {
        val css = String(fetchBytes(cssUrl, MAX_CSS_BYTES), Charsets.UTF_8)
        return TTF_URL.find(css)?.groupValues?.get(1)
            ?: throw FetchException("No TrueType file offered for $cssUrl")
    }

    private fun download(url: String, target: File, limit: Long, onProgress: ((Float) -> Unit)?) {
        target.parentFile?.mkdirs()
        // Unique, so a background refresh and a foreground download never share it.
        val temp = File(target.parentFile, "${target.name}.${System.nanoTime()}.part")
        var done = false
        try {
            open(url).use { conn ->
                val length = conn.connection.contentLengthLong
                if (length > limit) throw FetchException("$url is larger than $limit bytes")
                conn.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > limit) throw FetchException("$url is larger than $limit bytes")
                            output.write(buffer, 0, read)
                            if (onProgress != null && length > 0L) {
                                val percent = (total * 100 / length).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                        if (total == 0L) throw FetchException("$url is empty")
                    }
                }
            }
            if (!temp.renameTo(target)) throw FetchException("Can't move ${target.name}")
            done = true
        } finally {
            if (!done) temp.delete()
        }
    }

    private fun fetchBytes(url: String, limit: Long): ByteArray = open(url).use { conn ->
        conn.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw FetchException("$url exceeds $limit bytes")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private class Connection(val connection: HttpURLConnection) : Closeable {
        val inputStream get() = connection.inputStream
        override fun close() = connection.disconnect()
    }

    private fun open(url: String): Connection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw FetchException("HTTP $code for $url")
        }
        return Connection(conn)
    }
}

/** A family with styles saved on the device; [variants] are the saved ones. */
internal data class DownloadedFont(val font: OnlineFont, val variants: Set<String>)

/**
 * Online font list, previews and downloads.
 */
internal class OnlineFontRepository(context: Context) {

    private val context: Context = context.applicationContext
    private val root = File(this.context.cacheDir, ROOT_DIR)
    private val previewDir = File(root, PREVIEW_DIR)
    private val fileDir = File(root, FILE_DIR)
    private val downloadDir = File(this.context.filesDir, ROOT_DIR)

    /** The list from the last fetch, at once; null before the first one. */
    suspend fun savedFamilies(): List<OnlineFont>? = withContext(Dispatchers.IO) {
        GoogleFontsCatalog.savedFamilies(root)
    }

    fun familiesStale(): Boolean = GoogleFontsCatalog.isStale(root)

    suspend fun fetchFamilies(): List<OnlineFont> = withContext(Dispatchers.IO) {
        GoogleFontsCatalog.fetchFamilies(root)
    }

    /** Every saved family and its saved styles. Blocking. */
    fun downloads(): List<DownloadedFont> =
        downloadDir.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            runCatching {
                val font = OnlineFont.fromJson(JSONObject(File(dir, FONT_JSON).readText()))
                val variants = font.variants
                    .filter { File(dir, "$it.ttf").let { f -> f.isFile && f.length() > 0L } }
                    .toSet()
                if (variants.isEmpty()) null else DownloadedFont(font, variants)
            }.onFailure { Log.w(TAG, "Ignoring broken download in $dir", it) }.getOrNull()
        }

    fun downloadedFile(font: OnlineFont, variant: String): File? =
        File(familyDir(font), "$variant.ttf").takeIf { it.isFile && it.length() > 0L }

    /** Keeps [file] (a style of [font]) for later. Blocking. Returns false if it failed. */
    fun saveDownload(font: OnlineFont, variant: String, file: File): Boolean = runCatching {
        val dir = familyDir(font).apply { mkdirs() }
        val target = File(dir, "$variant.ttf")
        if (file.canonicalPath != target.canonicalPath) {
            val temp = File(dir, "$variant.ttf.tmp")
            file.copyTo(temp, overwrite = true)
            check(temp.renameTo(target)) { "Can't move ${target.name}" }
            // Unknown when the copy has none, so it gets refreshed in the background.
            val version = versionOf(file)
            if (version != null) setVersion(target, version) else versionFile(target).delete()
        }
        writeEntry(dir, font)
    }.onFailure { Log.e(TAG, "Couldn't save ${font.family} $variant", it) }.isSuccess

    /** Deletes one saved style, and the family's folder with its last one. Blocking. */
    fun deleteDownload(font: OnlineFont, variant: String) {
        val dir = familyDir(font)
        val file = File(dir, "$variant.ttf")
        file.delete()
        versionFile(file).delete()
        if (dir.listFiles { f -> f.name.endsWith(".ttf") }.isNullOrEmpty()) {
            dir.deleteRecursively()
        }
    }

    /**
     * Brings saved downloads in line with a freshly fetched [catalog]: their entries are
     * rewritten and styles Google has updated since are downloaded again, in the
     * background. Blocking (only the bookkeeping; downloads run on their own).
     */
    fun updateDownloads(catalog: List<OnlineFont>) {
        val byFamily = catalog.associateBy { it.family }
        downloads().forEach { saved ->
            val font = byFamily[saved.font.family] ?: return@forEach
            runCatching { writeEntry(familyDir(font), font) }
            saved.variants.filter { it in font.variants }.forEach { variant ->
                downloadedFile(font, variant)?.let { refreshIfStale(it, font, variant) }
            }
        }
    }

    private fun familyDir(font: OnlineFont) = File(downloadDir, onlineFontFileName(font.family))

    private fun writeEntry(dir: File, font: OnlineFont) {
        val json = File(dir, "$FONT_JSON.tmp")
        json.writeText(font.toJson().toString())
        check(json.renameTo(File(dir, FONT_JSON))) { "Can't write $FONT_JSON" }
    }

    /** Already-loaded preview, for the first frame of a card. */
    fun cachedPreview(font: OnlineFont): Typeface? = previews.get(font.family)

    /**
     * The family name (plus the "Aa" sample) in the font itself: from a saved style, else
     * the cached cut (refreshed in the background when outdated), else a cut downloaded
     * now. Null when none can be had; a failed download isn't retried until the screen is
     * opened again.
     */
    suspend fun preview(font: OnlineFont): Typeface? {
        previews.get(font.family)?.let { return it }
        val local = withContext(Dispatchers.IO) {
            val saved = (listOf(font.defaultVariant) + font.variants)
                .firstNotNullOfOrNull { downloadedFile(font, it) }
            when {
                saved != null -> load(saved)
                previewFile(font).isUsable() -> {
                    val file = previewFile(font)
                    refreshIfStale(file, font, variant = null)
                    file.setLastModified(System.currentTimeMillis())
                    load(file)
                }
                else -> null
            }
        }
        if (local != null) return local.also { previews.put(font.family, it) }
        if (font.family in failedPreviews) return null
        return previewLimit.withPermit {
            withContext(Dispatchers.IO) {
                previews.get(font.family) ?: runCatching {
                    val file = previewFile(font)
                    GoogleFontsCatalog.fetchPreview(font, previewText(font), file)
                    setVersion(file, font.version)
                    load(file) ?: error("Unusable preview for ${font.family}")
                }.onFailure {
                    Log.w(TAG, "Preview of ${font.family} unavailable: $it")
                    failedPreviews += font.family
                }.getOrNull()?.also { previews.put(font.family, it) }
            }
        }
    }

    private fun previewFile(font: OnlineFont) =
        File(previewDir, "${onlineFontFileName(font.family)}.ttf")

    private fun previewText(font: OnlineFont) =
        font.family + context.getString(R.string.font_sample_glyphs)

    /**
     * One style for previewing and installing: the saved copy, else the cached one (both
     * refreshed in the background when outdated), else downloaded now. Throws on failure.
     * Cancelling the calling coroutine stops a download at the next chunk.
     */
    suspend fun download(font: OnlineFont, variant: String, onProgress: (Float) -> Unit): File {
        val job = currentCoroutineContext()[Job]
        return withContext(Dispatchers.IO) {
            downloadedFile(font, variant)?.let {
                refreshIfStale(it, font, variant)
                return@withContext it
            }
            val cached = File(fileDir, "${onlineFontFileName(font.family)}-$variant.ttf")
            if (cached.isUsable()) {
                refreshIfStale(cached, font, variant)
                cached.setLastModified(System.currentTimeMillis())
                return@withContext cached
            }
            fileDir.mkdirs()
            GoogleFontsCatalog.fetchVariant(font, variant, cached) {
                if (job?.isActive == false) throw CancellationException()
                onProgress(it)
            }
            setVersion(cached, font.version)
            cached
        }
    }

    fun load(file: File): Typeface? = runCatching { Typeface.createFromFile(file) }
        .getOrNull()
        ?.takeUnless { it === Typeface.DEFAULT }
        .also { if (it == null) file.delete() }

    /**
     * Downloads [file] again in the background when it predates [font]'s current version.
     * [variant] null means it's the preview cut. The file is replaced in one step, so
     * whoever is using it keeps working. Failures are left for the next time.
     */
    private fun refreshIfStale(file: File, font: OnlineFont, variant: String?) {
        if (font.version.isEmpty() || versionOf(file) == font.version) return
        if (!refreshing.add(file.path)) return
        refreshScope.launch {
            try {
                refreshLimit.withPermit { update(file, font, variant) }
            } catch (e: Exception) {
                Log.w(TAG, "Background update of ${file.name} failed: $e")
            } finally {
                refreshing.remove(file.path)
            }
        }
    }

    private fun update(file: File, font: OnlineFont, variant: String?) {
        if (variant == null) {
            GoogleFontsCatalog.fetchPreview(font, previewText(font), file)
            load(file)?.let { previews.put(font.family, it) }
        } else {
            GoogleFontsCatalog.fetchVariant(font, variant, file, onProgress = null)
        }
        setVersion(file, font.version)
        Log.i(TAG, "Updated ${file.name} to ${font.family} ${font.version}")
    }

    private fun File.isUsable() = isFile && length() > 0L

    private fun versionFile(file: File) = File(file.parentFile, "${file.name}$VERSION_SUFFIX")

    private fun versionOf(file: File): String? =
        runCatching { versionFile(file).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun setVersion(file: File, version: String) {
        if (version.isEmpty()) return
        runCatching {
            val temp = File(file.parentFile, "${file.name}$VERSION_SUFFIX.tmp")
            temp.writeText(version)
            check(temp.renameTo(versionFile(file)))
        }.onFailure { Log.w(TAG, "Couldn't record version of ${file.name}", it) }
    }

    /** Failed previews are tried again once per visit to the screen. */
    fun resetFailures() = failedPreviews.clear()

    /** Keeps the caches within their limits, least recently used first out. Async. */
    fun trimCache() {
        refreshScope.launch {
            trim(fileDir, MAX_STYLE_CACHE_BYTES)
            trim(previewDir, MAX_PREVIEW_CACHE_BYTES)
        }
    }

    private fun trim(dir: File, limit: Long) {
        val now = System.currentTimeMillis()
        val files = dir.listFiles().orEmpty()
        // Leftovers of downloads that never finished.
        files.filter { it.name.endsWith(".part") && now - it.lastModified() > STALE_PART_MS }
            .forEach { it.delete() }
        var total = 0L
        files.filter { it.name.endsWith(".ttf") && it.path !in refreshing }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                total += file.length()
                if (total > limit) {
                    file.delete()
                    versionFile(file).delete()
                }
            }
    }

    companion object {
        private const val TAG = "OnlineFontRepository"
        private const val ROOT_DIR = "online_fonts"
        private const val PREVIEW_DIR = "previews"
        private const val FILE_DIR = "files"
        private const val FONT_JSON = "font.json"
        private const val VERSION_SUFFIX = ".version"
        private const val MAX_STYLE_CACHE_BYTES = 64L * 1024L * 1024L
        private const val MAX_PREVIEW_CACHE_BYTES = 16L * 1024L * 1024L
        private const val STALE_PART_MS = 60L * 60L * 1000L

        /** Previews download in parallel, but not all ~1,800 at once while flinging. */
        private val previewLimit = Semaphore(6)
        private val previews = LruCache<String, Typeface>(300)
        private val failedPreviews: MutableSet<String> = Collections.synchronizedSet(HashSet())

        /** Background updates outlive the screen; one at a time per file, two at once. */
        private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshLimit = Semaphore(2)
        private val refreshing: MutableSet<String> = Collections.synchronizedSet(HashSet())
    }
}
