/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * Keybox validation and status checks for the TrickyStore screen. Runs entirely in Settings:
 *
 *  - [validateForImport] applies the acceptance rules of the framework
 *    (TrickyStoreService.isValidKeyboxXml + KeyBoxManager.parseKeybox), so a keybox that would
 *    silently never load is refused at import time.
 *  - [describe] reads the key algorithms and certificate expiry locally, no network.
 *  - [inspect] checks every certificate serial against Google's attestation status list,
 *    verifies each certificate chain up to a Google attestation root and checks leaf expiry.
 *
 * State (last report, cached roots, serials seen revoked) lives in this app's own
 * SharedPreferences rather than Settings.Secure, so nothing extra lands in the secure table
 * the framework watches.
 */
internal object KeyboxInspector {
    private const val TAG = "KeyboxInspector"

    private const val PREFS = "spoofing_keybox_state"
    private const val PREF_HASH = "report_hash"
    private const val PREF_STATUS = "report_status"
    private const val PREF_REASON = "report_reason"
    private const val PREF_CHECKED_AT = "report_checked_at"
    private const val PREF_ROOTS = "roots_json"
    private const val PREF_ROOTS_AT = "roots_cached_at"
    private const val PREF_BAD_SERIALS = "bad_serials" // "serial|STATUS|timestamp"

    private const val STATUS_URL = "https://android.googleapis.com/attestation/status?encrypted=0"
    private const val ROOTS_URL = "https://android.googleapis.com/attestation/root"

    private val ROOTS_TTL_MS = TimeUnit.DAYS.toMillis(30)
    private val OFFLINE_TRUST_MS = TimeUnit.DAYS.toMillis(7)
    private val EXPIRY_WARN_MS = TimeUnit.DAYS.toMillis(14)

    /** Interval for the automatic re-check when the TrickyStore screen is resumed. */
    val RECHECK_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(24)

    /**
     * SHA-256 of the DER of Google's hardware attestation roots, only used when neither a live
     * nor a cached copy of the root list is available.
     */
    private val FALLBACK_ROOT_SHA256 = setOf(
        // RSA-4096, 2019 issuance
        "1EF1A04B8BA58AB94589AC498C8982A783F24EA7307E0159A0C3A73B377D87CC",
        // RSA-4096, 2022 reissue
        "CEDB1CB6DC896AE5EC797348BCE9286753C2B38EE71CE0FBE34A9A1248800DFC",
        // EC P-384 "Key Attestation CA1", 2025
        "6D9DB4CE6C5C0B293166D08986E05774A8776CEB525D9E4329520DE12BA4BCC0",
    )

    enum class Status {
        /** Every chain verifies to a Google root and no serial is on the status list. */
        VALID,

        /** As VALID, but a leaf certificate expires within two weeks. */
        EXPIRING_SOON,

        /** A serial is listed as REVOKED. */
        REVOKED,

        /** A serial is listed as SUSPENDED. */
        SUSPENDED,

        /** A leaf certificate is past its notAfter date. */
        EXPIRED,

        /** A certificate is not signed by the next one, or the root is not self-signed. */
        CHAIN_INVALID,

        /** The chain verifies, but its root is not a known Google attestation root. */
        UNTRUSTED_ROOT,

        /** Structure is fine, but the status list (or the root list) could not be reached. */
        UNVERIFIED,
    }

    data class Report(
        val status: Status,
        /** Revocation reason, failing check, or an expiry date, depending on [status]. */
        val reason: String,
        val checkedAt: Long,
    )

    data class KeyInfo(
        val algorithm: String,
        val chainLength: Int,
        val leafNotAfter: Long?,
    )

    data class Summary(val keys: List<KeyInfo>) {
        val algorithms: List<String> get() = keys.map { it.algorithm }.distinct()
        val earliestExpiry: Long? get() = keys.mapNotNull { it.leafNotAfter }.minOrNull()
    }

    // ---- Decoding ---------------------------------------------------------------------------

    /** Keybox payload as stored in Settings.Secure: raw XML or base64 of it. */
    fun decode(payload: String?): String? {
        if (payload.isNullOrBlank()) return null
        val trimmed = payload.trim()
        if (trimmed.startsWith("<") || trimmed.startsWith("\uFEFF<")) return trimmed
        return try {
            val xml = String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (xml.startsWith("<") || xml.startsWith("\uFEFF<")) xml else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Short stable id of a keybox, used to tie a stored report to the keybox it describes. */
    fun hashOf(xml: String): String =
        sha256Hex(xml.trim().toByteArray(Charsets.UTF_8)).take(24)

    // ---- Import validation ------------------------------------------------------------------

    /**
     * Returns null when the framework would load [xml], otherwise a short reason. Mirrors
     * TrickyStoreService.isValidKeyboxXml (literal key-block and identifier checks on the
     * decoded text) and KeyBoxManager.parseKeybox (well-formed document with at least one
     * complete key; NumberOfKeyboxes is advisory there, so a mismatch is not an error).
     */
    fun validateForImport(xml: String): String? {
        if (!xml.contains("<Key algorithm=\"ecdsa\">") && !xml.contains("<Key algorithm=\"rsa\">")) {
            return "no ECDSA or RSA key block"
        }
        if (!xml.contains("<serial>") && !xml.contains("DeviceID")) {
            return "no serial or DeviceID"
        }
        val keys = try {
            parseKeys(sanitize(xml))
        } catch (e: Exception) {
            return "not a well-formed keybox XML"
        }
        if (keys.isEmpty()) return "no complete key (private key + certificate)"
        val usable = keys.count { key ->
            key.certPems.isNotEmpty() && key.certPems.all { parseCertificate(it) != null }
        }
        if (usable == 0) return "certificates could not be parsed"
        return null
    }

    // ---- Local description ------------------------------------------------------------------

    fun describe(xml: String): Summary? = try {
        val keys = parseKeys(sanitize(xml)).map { key ->
            val certs = key.certPems.mapNotNull { parseCertificate(it) }
            KeyInfo(
                algorithm = normaliseAlgorithm(key.algorithm),
                chainLength = certs.size,
                leafNotAfter = certs.firstOrNull()?.notAfter?.time,
            )
        }
        if (keys.isEmpty()) null else Summary(keys)
    } catch (_: Exception) {
        null
    }

    // ---- Status check (network, call off the main thread) ----------------------------------

    fun inspect(context: Context, xml: String): Report {
        val now = System.currentTimeMillis()
        val chains = try {
            parseKeys(sanitize(xml))
                .map { key -> key.certPems.mapNotNull { parseCertificate(it) } }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        if (chains.isEmpty()) return Report(Status.CHAIN_INVALID, "NO_CERTIFICATES", now)

        val prefs = prefs(context)
        val serials = chains.flatten()
            .flatMap { listOf(it.serialNumber.toString(16), it.serialNumber.toString()) }
            .distinct()

        // 1. Revocation list. A revoked or suspended keybox is reported as such whatever
        //    else is wrong with it, since that is what decides STRONG integrity.
        var offline = false
        val statusList = fetchJson(STATUS_URL)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (statusList != null) {
            val entries = statusList.optJSONObject("entries")
            if (entries != null) {
                for (serial in serials) {
                    val entry = entries.optJSONObject(serial) ?: continue
                    val listed = entry.optString("status").uppercase()
                    val reason = entry.optString("reason").uppercase()
                    val status = when (listed) {
                        "REVOKED" -> Status.REVOKED
                        "SUSPENDED" -> Status.SUSPENDED
                        else -> null
                    } ?: continue
                    rememberBadSerial(prefs, serial, status, now)
                    return Report(status, reason, now)
                }
            }
            forgetBadSerials(prefs, serials)
        } else {
            offline = true
            // Downgrade-only fallback: a serial seen listed within the last week is still
            // reported, a clean result is never asserted without a live answer.
            cachedBadSerial(prefs, serials, now)?.let { status ->
                return Report(status, "CACHED_OFFLINE", now)
            }
        }

        // 2. Expiry of the leaf certificates.
        val earliestLeaf = chains.minOf { it.first().notAfter.time }
        if (earliestLeaf < now) return Report(Status.EXPIRED, formatDate(earliestLeaf), now)

        // 3. Chain signatures and root.
        val anchors = trustAnchors(prefs, now)
        var rootUnverifiable = false
        for (chain in chains) {
            when (val result = verifyChain(chain, anchors)) {
                ChainResult.Ok -> Unit
                ChainResult.RootUnknown -> rootUnverifiable = true
                ChainResult.UntrustedRoot -> return Report(Status.UNTRUSTED_ROOT, "", now)
                is ChainResult.Broken -> return Report(Status.CHAIN_INVALID, result.reason, now)
            }
        }

        if (earliestLeaf - now < EXPIRY_WARN_MS) {
            return Report(Status.EXPIRING_SOON, formatDate(earliestLeaf), now)
        }
        if (offline || rootUnverifiable) {
            return Report(Status.UNVERIFIED, if (offline) "OFFLINE" else "ROOTS_UNAVAILABLE", now)
        }
        return Report(Status.VALID, "", now)
    }

    // ---- Stored report ----------------------------------------------------------------------

    fun loadReport(context: Context, hash: String): Report? {
        val prefs = prefs(context)
        if (prefs.getString(PREF_HASH, null) != hash) return null
        val status = prefs.getString(PREF_STATUS, null)
            ?.let { runCatching { Status.valueOf(it) }.getOrNull() } ?: return null
        return Report(
            status = status,
            reason = prefs.getString(PREF_REASON, "") ?: "",
            checkedAt = prefs.getLong(PREF_CHECKED_AT, 0L),
        )
    }

    fun saveReport(context: Context, hash: String, report: Report) {
        prefs(context).edit()
            .putString(PREF_HASH, hash)
            .putString(PREF_STATUS, report.status.name)
            .putString(PREF_REASON, report.reason)
            .putLong(PREF_CHECKED_AT, report.checkedAt)
            .apply()
    }

    fun clearReport(context: Context) {
        prefs(context).edit()
            .remove(PREF_HASH)
            .remove(PREF_STATUS)
            .remove(PREF_REASON)
            .remove(PREF_CHECKED_AT)
            .apply()
    }

    // ---- Internals --------------------------------------------------------------------------

    private class ParsedKey(val algorithm: String, val certPems: List<String>)

    private sealed interface ChainResult {
        object Ok : ChainResult
        object RootUnknown : ChainResult
        object UntrustedRoot : ChainResult
        class Broken(val reason: String) : ChainResult
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** KeyBoxManager.sanitizeXml(): trim, strip a leading BOM and XML comments. */
    private fun sanitize(content: String): String {
        var s = content.trim()
        for (bom in listOf("\uFEFF", "\uFFFE", "\u0000\uFEFF")) {
            if (s.startsWith(bom)) s = s.substring(bom.length)
        }
        return s.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    /**
     * Complete keys only (algorithm, private key and at least one certificate), as counted by
     * KeyBoxManager. Throws when the document is cut off before its root element closes;
     * trailing garbage after a closed root is tolerated like the framework does.
     */
    private fun parseKeys(xml: String): List<ParsedKey> {
        val keys = mutableListOf<ParsedKey>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var depth = 0
        var rootClosed = false
        var inKey = false
        var algorithm: String? = null
        var hasPrivateKey = false
        val certs = mutableListOf<String>()
        var text: StringBuilder? = null
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "Key" -> {
                                inKey = true
                                algorithm = parser.getAttributeValue(null, "algorithm")
                                hasPrivateKey = false
                                certs.clear()
                            }
                            "PrivateKey", "Certificate" -> if (inKey) text = StringBuilder()
                        }
                    }
                    XmlPullParser.TEXT -> text?.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "PrivateKey" -> {
                                if (text?.isNotBlank() == true) hasPrivateKey = true
                                text = null
                            }
                            "Certificate" -> {
                                text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                                    ?.let { certs += it }
                                text = null
                            }
                            "Key" -> {
                                inKey = false
                                val alg = algorithm
                                if (alg != null && hasPrivateKey && certs.isNotEmpty()) {
                                    keys += ParsedKey(alg, certs.toList())
                                }
                            }
                        }
                        if (--depth == 0) rootClosed = true
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            if (!rootClosed || keys.isEmpty()) throw e
        }
        return keys
    }

    private fun parseCertificate(pem: String): X509Certificate? = try {
        val body = pem
            .replace(Regex("-----BEGIN [^-]+-----"), "")
            .replace(Regex("-----END [^-]+-----"), "")
            .replace(Regex("\\s"), "")
        val der = Base64.decode(body, Base64.DEFAULT)
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    } catch (_: Exception) {
        null
    }

    private fun normaliseAlgorithm(algorithm: String): String = when (algorithm.lowercase()) {
        "ec", "ecdsa" -> "ECDSA"
        "rsa" -> "RSA"
        else -> algorithm.uppercase()
    }

    /**
     * Each certificate must be signed by the next, the last one must be self-signed and be a
     * Google attestation root. Live roots are matched by public key: Google reissued the RSA
     * root under a new certificate with the same key, and chains to either must pass.
     */
    private fun verifyChain(chain: List<X509Certificate>, anchors: Set<X509Certificate>): ChainResult {
        if (chain.size < 2) return ChainResult.Broken("CHAIN_TOO_SHORT")
        for (i in 0 until chain.size - 1) {
            try {
                chain[i].verify(chain[i + 1].publicKey)
            } catch (_: Exception) {
                return ChainResult.Broken("BAD_SIGNATURE_AT_$i")
            }
        }
        val root = chain.last()
        try {
            root.verify(root.publicKey)
        } catch (_: Exception) {
            return ChainResult.Broken("ROOT_NOT_SELF_SIGNED")
        }
        if (anchors.isNotEmpty()) {
            val rootKey = sha256Hex(root.publicKey.encoded)
            return if (anchors.any { sha256Hex(it.publicKey.encoded) == rootKey }) ChainResult.Ok
            else ChainResult.UntrustedRoot
        }
        return if (sha256Hex(root.encoded) in FALLBACK_ROOT_SHA256) ChainResult.Ok
        else ChainResult.RootUnknown
    }

    /** Fresh cache, else a live fetch, else a stale cache, else empty. */
    private fun trustAnchors(prefs: SharedPreferences, now: Long): Set<X509Certificate> {
        val cached = prefs.getString(PREF_ROOTS, null)
        val cachedAt = prefs.getLong(PREF_ROOTS_AT, 0L)
        if (cached != null && cachedAt in 1..now && now - cachedAt <= ROOTS_TTL_MS) {
            parseRoots(cached)?.let { return it }
        }
        fetchJson(ROOTS_URL)?.let { raw ->
            parseRoots(raw)?.let { roots ->
                prefs.edit().putString(PREF_ROOTS, raw).putLong(PREF_ROOTS_AT, now).apply()
                return roots
            }
        }
        return cached?.let { parseRoots(it) } ?: emptySet()
    }

    private fun parseRoots(raw: String): Set<X509Certificate>? = try {
        val array = JSONArray(raw)
        val factory = CertificateFactory.getInstance("X.509")
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                factory.generateCertificate(
                    ByteArrayInputStream(array.getString(i).toByteArray(Charsets.UTF_8)),
                ) as X509Certificate
            }.getOrNull()
        }.toSet().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    /**
     * GET with cache-busting: the attestation endpoints sit behind a CDN that can serve a
     * stale 200, so a query parameter and no-cache headers force a fresh answer.
     */
    private fun fetchJson(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val busted = url + (if ('?' in url) "&" else "?") + "_=${System.nanoTime()}"
            conn = (URL(busted).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept-Encoding", "identity")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                Log.w(TAG, "GET $url failed: HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $url failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun rememberBadSerial(prefs: SharedPreferences, serial: String, status: Status, now: Long) {
        val entries = prefs.getStringSet(PREF_BAD_SERIALS, emptySet()).orEmpty()
            .filterNot { it.substringBefore('|') == serial }
            .toMutableSet()
        entries += "$serial|${status.name}|$now"
        prefs.edit().putStringSet(PREF_BAD_SERIALS, entries).apply()
    }

    private fun forgetBadSerials(prefs: SharedPreferences, serials: List<String>) {
        val entries = prefs.getStringSet(PREF_BAD_SERIALS, emptySet()).orEmpty()
        val kept = entries.filterNot { it.substringBefore('|') in serials }.toSet()
        if (kept.size != entries.size) prefs.edit().putStringSet(PREF_BAD_SERIALS, kept).apply()
    }

    private fun cachedBadSerial(prefs: SharedPreferences, serials: List<String>, now: Long): Status? =
        prefs.getStringSet(PREF_BAD_SERIALS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size != 3 || parts[0] !in serials) return@mapNotNull null
                val at = parts[2].toLongOrNull() ?: return@mapNotNull null
                if (now - at > OFFLINE_TRUST_MS) return@mapNotNull null
                runCatching { Status.valueOf(parts[1]) }.getOrNull()
            }
            .minByOrNull { it.ordinal } // REVOKED before SUSPENDED

    private fun sha256Hex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02X".format(it) }

    fun formatDate(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(millis))
}
