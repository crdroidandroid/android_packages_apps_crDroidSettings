/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Pixel fingerprint sources for Play Integrity Fix, moved out of the fragment unchanged:
 *
 *  - [PifChannel.LATEST_RELEASE]: developer.android.com beta OTA list, fingerprint and patch
 *    level read from the OTA metadata.
 *  - [PifChannel.CANARY]: the beta download pages for the device table, then the Android Flash
 *    Tool API for the newest canary build, patch level from the Pixel bulletin.
 *
 * Every call does network I/O and must run off the main thread.
 */
internal object PifFetcher {
    private const val TAG = "PifFetcher"

    private const val GOOGLE_URL = "https://developer.android.com"
    private const val FLASH_URL = "https://flash.android.com"
    private const val FLASH_API = "https://content-flashstation-pa.googleapis.com/v1/builds"
    private const val PIXEL_BULLETIN_URL = "https://source.android.com/docs/security/bulletin/pixel"

    private val VERSIONS_REGEX =
        Regex("""(?:https://developer\.android\.com)?/about/versions/(\d+)""")

    enum class PifChannel { LATEST_RELEASE, CANARY }

    data class PifDevice(
        val product: String,
        val device: String,
        val model: String,
        val otaUrl: String,
    )

    sealed class PifFetchResult {
        data class Success(val model: String, val pifData: JSONObject) : PifFetchResult()
        data class Error(val message: String) : PifFetchResult()
    }

    /** Devices offered by [channel] and, for Canary, the Flash Tool API key. */
    data class DeviceList(val devices: List<PifDevice>, val apiKey: String?)

    val DEVICE_MODEL_MAP = mapOf(
        "oriole" to "Pixel 6",
        "raven" to "Pixel 6 Pro",
        "bluejay" to "Pixel 6a",
        "panther" to "Pixel 7",
        "cheetah" to "Pixel 7 Pro",
        "lynx" to "Pixel 7a",
        "shiba" to "Pixel 8",
        "tangorpro" to "Pixel Tablet",
        "felix" to "Pixel Fold",
        "husky" to "Pixel 8 Pro",
        "akita" to "Pixel 8a",
        "tokay" to "Pixel 9",
        "caiman" to "Pixel 9 Pro",
        "komodo" to "Pixel 9 Pro XL",
        "comet" to "Pixel 9 Pro Fold",
        "tegu" to "Pixel 9a",
        "frankel" to "Pixel 10",
        "blazer" to "Pixel 10 Pro",
        "mustang" to "Pixel 10 Pro XL",
        "rango" to "Pixel 10 Pro Fold",
        "stallion" to "Pixel 10a",
        "yogi" to "Pixel 11 Pro Fold",
        "kodiak" to "Pixel 11 Pro XL",
        "grizzly" to "Pixel 11 Pro",
        "cubs" to "Pixel 11",
        "formosan" to "Pixel 11a",
    )

    /** SDK level each device launched with. Unknown/new devices fall back to the newest. */
    fun initialSdkIntFor(device: String): String = when (device) {
        "oriole", "raven" -> "31"
        "bluejay" -> "32"
        "panther", "cheetah", "lynx", "felix", "tangorpro" -> "33"
        "shiba", "husky", "akita", "tokay", "caiman", "komodo", "comet" -> "34"
        "tegu", "stallion" -> "35"
        "frankel", "blazer", "mustang", "rango" -> "36"
        "cubs", "grizzly", "kodiak", "yogi" -> "37"
        else -> "37"
    }

    fun fetchDevices(channel: PifChannel): DeviceList = when (channel) {
        PifChannel.LATEST_RELEASE -> DeviceList(fetchAvailableDevices(), null)
        PifChannel.CANARY -> fetchAvailableCanaryDevices().let { DeviceList(it.first, it.second) }
    }

    fun buildConfig(device: PifDevice, channel: PifChannel, apiKey: String?): PifFetchResult =
        when (channel) {
            PifChannel.LATEST_RELEASE -> buildPifFromDevice(device)
            PifChannel.CANARY -> buildCanaryPifFromDevice(device, apiKey ?: "")
        }

    // ---- Latest release (beta OTA) ---------------------------------------------------------

    private fun fetchPartialUrl(url: String, maxBytes: Int): String {
        val conn = URL(url).openConnection()
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.getInputStream().use { input ->
            val buf = ByteArray(512)
            val sb = StringBuilder()
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buf)
                if (read == -1) break
                sb.append(String(buf, 0, read, StandardCharsets.ISO_8859_1))
                total += read
            }
            return sb.toString()
        }
    }

    private fun fetchKnownVersions(): List<Int> {
        val versionsHtml = URL("$GOOGLE_URL/about/versions").readText(StandardCharsets.UTF_8)
        return VERSIONS_REGEX.findAll(versionsHtml)
            .map { it.groupValues[1].toInt() }.toSet().sortedDescending()
    }

    /** Scrapes the developer site for every device with a beta OTA. */
    private fun fetchAvailableDevices(): List<PifDevice> {
        val knownVersions = fetchKnownVersions()

        val maxVersion = knownVersions.firstOrNull() ?: return emptyList()
        val versions = listOf(maxVersion + 1) + knownVersions

        for (version in versions) {
            try {
                val downloadUrl = "$GOOGLE_URL/about/versions/$version/download-ota"
                val otaHtml = URL(downloadUrl).readText(StandardCharsets.UTF_8)
                val otaList = Regex("""href="(https://dl\.google\.com/[^"]*ota/([^/"]+_beta)[^"]*?)"""")
                    .findAll(otaHtml).map { it.groupValues[1] to it.groupValues[2] }.toList()
                if (otaList.isEmpty()) continue

                val devices = mutableListOf<PifDevice>()
                val seen = mutableSetOf<String>()
                for ((otaUrl, product) in otaList) {
                    val device = product.replace("_beta", "")
                    if (device in seen) continue
                    seen.add(device)
                    val model = DEVICE_MODEL_MAP[device] ?: device
                    devices.add(PifDevice(product, device, model, otaUrl))
                }
                if (devices.isNotEmpty()) return devices
            } catch (_: Exception) { continue }
        }
        return emptyList()
    }

    /** Reads the OTA metadata of [pifDevice] and builds the config from it. */
    private fun buildPifFromDevice(pifDevice: PifDevice): PifFetchResult {
        try {
            val partial = fetchPartialUrl(pifDevice.otaUrl, 4096)

            val fingerprint = Regex("""post-build=(.*)""").find(partial)?.groupValues?.get(1)?.trim()
                ?: return PifFetchResult.Error("Could not extract fingerprint")
            val securityPatch = Regex("""security-patch-level=(.*)""").find(partial)?.groupValues?.get(1)?.trim()
                ?: return PifFetchResult.Error("Could not extract security patch")

            val fpParts = fingerprint.split("/")
            val release = fpParts.getOrNull(2)?.substringAfter(":", "") ?: ""
            val buildId = fpParts.getOrNull(3) ?: ""

            val pifJson = JSONObject().apply {
                put("TYPE", "user")
                put("TAGS", "release-keys")
                put("ID", buildId)
                put("BRAND", "google")
                put("DEVICE", pifDevice.device)
                put("FINGERPRINT", fingerprint)
                put("MANUFACTURER", "Google")
                put("MODEL", pifDevice.model)
                put("PRODUCT", pifDevice.product)
                put("RELEASE", release)
                put("SECURITY_PATCH", securityPatch)
                put("DEVICE_INITIAL_SDK_INT", initialSdkIntFor(pifDevice.device))
            }
            return PifFetchResult.Success(pifDevice.model, pifJson)
        } catch (e: Exception) {
            return PifFetchResult.Error("Failed: ${e.message}")
        }
    }

    // ---- Canary (Flash Tool) ---------------------------------------------------------------

    private fun fetchAvailableCanaryDevices(): Pair<List<PifDevice>, String?> {
        try {
            val knownVersions = fetchKnownVersions()

            val maxVersion = knownVersions.firstOrNull() ?: return emptyList<PifDevice>() to null
            val versions = listOf(maxVersion + 1) + knownVersions

            for (version in versions) {
                try {
                    val latestHtml = URL("$GOOGLE_URL/about/versions/$version")
                        .readText(StandardCharsets.UTF_8)

                    // Beta download pages: factory images (".../download") and OTAs
                    // (".../download-ota"), either for the base release or a QPR.
                    val pages = Regex(
                        """href="(/about/versions/$version(?:/qpr(\d+))?/download(?:-ota)?)""""
                    ).findAll(latestHtml)
                        .map { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }
                        .toList()
                    if (pages.isEmpty()) continue

                    // Newest QPR (or the base release when there is none), then the
                    // longer of its FI / OTA device tables, as autopif does.
                    val newestQpr = pages.maxOf { it.second }
                    val devices = pages.filter { it.second == newestQpr }
                        .map { it.first }
                        .distinct()
                        .mapNotNull { path ->
                            try {
                                parseDeviceTable(URL("$GOOGLE_URL$path").readText(StandardCharsets.UTF_8))
                            } catch (e: Exception) {
                                Log.d(TAG, "Failed to read $path: ${e.message}")
                                null
                            }
                        }
                        .maxByOrNull { it.size }
                        .orEmpty()

                    if (devices.isEmpty()) continue

                    val flashHtml = URL(FLASH_URL).readText(StandardCharsets.UTF_8)
                    return devices to extractFlashApiKey(flashHtml)
                } catch (_: Exception) { continue }
            }

            return emptyList<PifDevice>() to null
        } catch (e: Exception) {
            Log.e(TAG, "Canary device fetch failed", e)
            return emptyList<PifDevice>() to null
        }
    }

    private val DEVICE_ROW_REGEX = Regex(
        """<tr id="([^"]+)">\s*<td[^>]*>([^<]+)</td>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val API_KEY_REGEX = Regex("""AIza[0-9A-Za-z_-]{35}""")

    /** Parses a beta download page's device table into codename/model pairs. */
    private fun parseDeviceTable(html: String): List<PifDevice> {
        val devices = mutableListOf<PifDevice>()
        val seen = mutableSetOf<String>()
        DEVICE_ROW_REGEX.findAll(html).forEach { match ->
            val device = match.groupValues[1]
            if (!seen.add(device)) return@forEach
            val model = match.groupValues[2].trim()
                .ifEmpty { DEVICE_MODEL_MAP[device] ?: device }
            devices.add(PifDevice(product = "${device}_beta", device = device, model = model, otaUrl = ""))
        }
        return devices
    }

    /**
     * The Flash Tool key lives in the page's data-client-config attribute (HTML-escaped
     * JSON). Look there first so another Google key on the page is never picked up,
     * and only fall back to scanning the whole page.
     */
    private fun extractFlashApiKey(html: String): String? {
        val clientConfig = Regex("""data-client-config="([^"]*)"""").find(html)?.groupValues?.get(1)
        return clientConfig?.let { API_KEY_REGEX.find(it)?.value }
            ?: API_KEY_REGEX.find(html)?.value
    }

    private fun buildCanaryPifFromDevice(pifDevice: PifDevice, apiKey: String): PifFetchResult {
        try {
            if (apiKey.isEmpty()) return PifFetchResult.Error("Flash Tool API key unavailable")

            val buildsUrl = "$FLASH_API?product=${pifDevice.product}&key=$apiKey"
            val buildsConn = URL(buildsUrl).openConnection().apply {
                setRequestProperty("Referer", FLASH_URL)
                setRequestProperty("X-Goog-Api-Key", apiKey)
                connectTimeout = 15000
                readTimeout = 15000
            }
            val buildsJson = buildsConn.getInputStream().use {
                it.readBytes().toString(StandardCharsets.UTF_8)
            }

            val root = JSONObject(buildsJson)
            val buildsArray = root.optJSONArray("flashstationBuild")
                ?: return PifFetchResult.Error("No flashstationBuild array in Flash Tool response")

            var id: String? = null
            var incremental: String? = null
            var canaryId: String? = null

            for (i in buildsArray.length() - 1 downTo 0) {
                val b = buildsArray.optJSONObject(i) ?: continue
                val meta = b.optJSONObject("previewMetadata") ?: continue
                if (!meta.optBoolean("canary")) continue

                val rc = b.optString("releaseCandidateName")
                val bid = b.optString("buildId")
                if (rc.isEmpty() || bid.isEmpty()) continue

                id = rc
                incremental = bid
                canaryId = meta.optString("id").takeIf { it.contains("canary-") }
                break
            }

            if (id == null || incremental == null) {
                return PifFetchResult.Error("No canary build found for ${pifDevice.product}")
            }

            val fingerprint =
                "google/${pifDevice.product}/${pifDevice.device}:CANARY/$id/$incremental:user/release-keys"

            val canaryMonth = canaryId?.let {
                Regex("""canary-(\d{4})(\d{2})""").find(it)?.let { m ->
                    "${m.groupValues[1]}-${m.groupValues[2]}"
                }
            } ?: return PifFetchResult.Error("Failed to derive canary month id")

            val securityPatch = try {
                val bulletinHtml = URL(PIXEL_BULLETIN_URL).readText(StandardCharsets.UTF_8)
                Regex("""<td>($canaryMonth-\d{2})</td>""").find(bulletinHtml)?.groupValues?.get(1)
                    ?: "$canaryMonth-05"
            } catch (e: Exception) {
                Log.d(TAG, "Bulletin fetch failed, using estimated patch: ${e.message}")
                "$canaryMonth-05"
            }

            val pifJson = JSONObject().apply {
                put("TYPE", "user")
                put("TAGS", "release-keys")
                put("ID", id)
                put("BRAND", "google")
                put("DEVICE", pifDevice.device)
                put("FINGERPRINT", fingerprint)
                put("MANUFACTURER", "Google")
                put("MODEL", pifDevice.model)
                put("PRODUCT", pifDevice.product)
                put("RELEASE", "CANARY")
                put("SECURITY_PATCH", securityPatch)
                put("DEVICE_INITIAL_SDK_INT", initialSdkIntFor(pifDevice.device))
            }

            return PifFetchResult.Success(pifDevice.model, pifJson)
        } catch (e: Exception) {
            return PifFetchResult.Error("Failed: ${e.message}")
        }
    }
}
