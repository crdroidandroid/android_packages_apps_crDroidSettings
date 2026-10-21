/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.crdroid.settings.fragments.misc.PifFetcher.PifChannel
import com.crdroid.settings.fragments.misc.PifFetcher.PifDevice
import com.crdroid.settings.fragments.misc.PifFetcher.PifFetchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayIntegrityFix : SettingsPreferenceFragment() {

    private val ui = PifUiState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Devices behind the open [PifDialog.DevicePicker], in the same order as its rows. */
    private var pickerDevices: List<PifDevice> = emptyList()
    private var pickerChannel = PifChannel.LATEST_RELEASE
    private var pickerApiKey: String? = null

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        result.data?.data?.let { uri ->
            try {
                val content = requireContext().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(StandardCharsets.UTF_8)
                } ?: ""
                val normalized = normalizePifPayload(content)
                val fingerprint = JSONObject(normalized).optString("FINGERPRINT")
                require(fingerprint.isEmpty() || isValidFingerprint(fingerprint)) {
                    getString(R.string.pif_invalid_fingerprint)
                }
                writeConfig(normalized)
                toast(getString(R.string.pif_imported_as, PIF_CONFIG_NAME))
                ui.dialog = PifDialog.WipePlayStore
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import config", e)
                toast(getString(R.string.pif_failed, e.message ?: ""))
            }
        }
    }

    private val actions = object : PifActions {
        override fun onFetch() {
            ui.dialog = PifDialog.Channel
        }

        override fun onPickChannel(canary: Boolean) =
            fetchDevices(if (canary) PifChannel.CANARY else PifChannel.LATEST_RELEASE)

        override fun onPickDevice(index: Int) {
            pickerDevices.getOrNull(index)?.let { generateAndSave(it) }
        }

        override fun onImport() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importLauncher.launch(intent)
        }

        override fun onDelete() {
            ui.dialog = PifDialog.Delete
        }

        override fun onConfirmDelete() {
            try {
                Settings.Secure.putString(requireContext().contentResolver, PIF_CONFIG_KEY, null)
                toast(getString(R.string.pif_deleted, PIF_CONFIG_NAME))
            } catch (e: Exception) {
                toast(getString(R.string.pif_failed, e.message ?: ""))
            }
            refreshStatus()
        }

        override fun onResetFlags() {
            ui.dialog = PifDialog.ResetFlags
        }

        override fun onConfirmResetFlags() {
            editConfig { json ->
                FLAG_KEYS.forEach { json.remove(it) }
                json.putDefaultSpoofFlags()
            }
        }

        override fun onFlagChange(key: String, value: Boolean) {
            editConfig { it.put(key, value.toString()) }
        }

        override fun onVerboseLogs() {
            val (entries, values) = verboseArrays()
            val current = ui.config[KEY_VERBOSE_LOGS] ?: DEFAULT_VERBOSE_LOGS
            ui.dialog = PifDialog.VerboseLogs(entries, values.indexOf(current))
        }

        override fun onPickVerbose(index: Int) {
            val value = verboseArrays().second.getOrNull(index) ?: return
            editConfig { it.put(KEY_VERBOSE_LOGS, value) }
        }

        override fun onPhotosChange(enabled: Boolean) {
            Settings.Secure.putInt(
                requireContext().contentResolver, PHOTOS_KEY, if (enabled) 1 else 0)
            ui.photosSpoof = enabled
        }

        override fun onEditValue(key: String, value: String): String? {
            if (value.isEmpty()) return getString(R.string.pif_value_empty)
            if (key in INT_KEYS && value.toIntOrNull() == null) {
                return getString(R.string.pif_value_not_integer)
            }
            if (key == "SECURITY_PATCH" && !DATE_REGEX.matches(value)) {
                return getString(R.string.ts_invalid_patch_date)
            }
            if (key == "FINGERPRINT" && !isValidFingerprint(value)) {
                return getString(R.string.pif_invalid_fingerprint)
            }
            editConfig { json ->
                json.put(key, value)
                // Keep the derived fields in step with the new fingerprint so the screen
                // shows what the framework will actually apply.
                if (key == "FINGERPRINT") fingerprintFields(value).forEach { (k, v) -> json.put(k, v) }
            }
            return null
        }

        override fun onWipePlayStore(wipe: Boolean) {
            if (!wipe) return
            try {
                requireContext().packageManager.clearApplicationUserData(VENDING_PACKAGE, null)
            } catch (_: Exception) {}
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().title = getString(R.string.play_integrity_fix_title)
        refreshStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = spoofComposeView(requireContext()).apply {
        setContent {
            SettingsTheme {
                PlayIntegrityFixContent(state = ui, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    // ---- State -----------------------------------------------------------------------------

    private fun readRawConfig(): String? = Settings.Secure.getStringForUser(
        requireContext().contentResolver, PIF_CONFIG_KEY, UserHandle.USER_CURRENT)

    private fun refreshStatus() {
        val raw = readRawConfig()
        val config = if (raw.isNullOrEmpty()) emptyMap() else readConfigData(raw)
        ui.config = config
        ui.patchAgeDays = patchAgeDays(config["SECURITY_PATCH"])

        val (entries, values) = verboseArrays()
        val verbose = config[KEY_VERBOSE_LOGS] ?: DEFAULT_VERBOSE_LOGS
        ui.verboseLabel = entries.getOrNull(values.indexOf(verbose)) ?: verbose

        val photos = Settings.Secure.getString(requireContext().contentResolver, PHOTOS_KEY)
        // Same rule as the framework: unset means enabled.
        ui.photosSpoof = photos == null || photos == "1" || photos.equals("true", true)
    }

    private fun writeConfig(json: String) {
        Settings.Secure.putString(requireContext().contentResolver, PIF_CONFIG_KEY, json)
        refreshStatus()
    }

    /**
     * Applies [block] to the active config and writes it back. The framework re-reads the
     * config and restarts DroidGuard and the Play Store on its own (AxSpoofManager).
     */
    private fun editConfig(block: (JSONObject) -> Unit) {
        try {
            val raw = readRawConfig()
            if (raw.isNullOrBlank()) {
                toast(getString(R.string.pif_no_config))
                return
            }
            val json = JSONObject(normalizePifPayload(raw))
            block(json)
            writeConfig(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update config", e)
            toast(getString(R.string.pif_failed, e.message ?: ""))
        }
    }

    private fun verboseArrays(): Pair<List<String>, List<String>> =
        resources.getStringArray(R.array.pif_verbose_logs_entries).toList() to
            resources.getStringArray(R.array.pif_verbose_logs_values).toList()

    // ---- Fetch from Google -----------------------------------------------------------------

    private fun fetchDevices(channel: PifChannel) {
        if (ui.fetching) return
        ui.fetching = true
        ui.fetchStatus = getString(R.string.pif_fetching)

        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { PifFetcher.fetchDevices(channel) }
                if (!isAdded) return@launch
                when {
                    list.devices.isEmpty() ->
                        toast(getString(R.string.pif_failed, getString(R.string.pif_no_devices_found)))
                    channel == PifChannel.CANARY && list.apiKey.isNullOrEmpty() ->
                        toast(getString(R.string.pif_failed, getString(R.string.pif_no_api_key)))
                    else -> {
                        pickerDevices = list.devices
                        pickerChannel = channel
                        pickerApiKey = list.apiKey
                        // Preselect the device the active config already spoofs, if offered.
                        val current = list.devices.indexOfFirst { it.device == ui.config["DEVICE"] }
                        ui.dialog = PifDialog.DevicePicker(list.devices.map { it.model }, current)
                    }
                }
            } catch (e: Exception) {
                toast(getString(R.string.pif_failed, e.message ?: ""))
            } finally {
                ui.fetching = false
                ui.fetchStatus = null
            }
        }
    }

    private fun generateAndSave(device: PifDevice) {
        if (ui.fetching) return
        ui.fetching = true
        ui.fetchStatus = getString(R.string.pif_generating)
        val channel = pickerChannel
        val apiKey = pickerApiKey

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    PifFetcher.buildConfig(device, channel, apiKey)
                }
                if (!isAdded) return@launch
                when (result) {
                    is PifFetchResult.Success -> {
                        // User-tuned flags from the previous config win; defaults only
                        // fill in whatever is still missing afterwards.
                        val existing = readRawConfig()
                            ?.let { readConfigData(it) }.orEmpty()
                        FLAG_KEYS.forEach { key ->
                            existing[key]?.let { result.pifData.put(key, it) }
                        }
                        result.pifData.putDefaultSpoofFlags()
                        writeConfig(result.pifData.toString(2))
                        toast(getString(R.string.pif_fetched_model, result.model))
                        ui.dialog = PifDialog.WipePlayStore
                    }
                    is PifFetchResult.Error -> toast(getString(R.string.pif_failed, result.message))
                }
            } catch (e: Exception) {
                toast(getString(R.string.pif_failed, e.message ?: ""))
            } finally {
                ui.fetching = false
                ui.fetchStatus = null
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "PlayIntegrityFix"
        internal const val PIF_CONFIG_KEY = "spoof_pif_config"
        internal const val PIF_CONFIG_NAME = "pif.json"
        private const val PHOTOS_KEY = "spoof_pif_photos"

        internal const val KEY_VENDING_BUILD = "spoofVendingBuild"
        internal const val KEY_VENDING_SDK = "spoofVendingSdk"
        private const val KEY_VERBOSE_LOGS = "verboseLogs"
        private const val DEFAULT_VERBOSE_LOGS = "0"

        private val INT_KEYS = setOf("DEVICE_INITIAL_SDK_INT", "SDK_INT")
        private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

        /** brand/product/device:release/id/incremental:type/tags */
        private val FINGERPRINT_REGEX =
            Regex("""[^/:\s]+/[^/:\s]+/[^/:\s]+:[^/:\s]+/[^/:\s]+/[^/:\s]+:[^/:\s]+/[^/:\s]+""")

        /**
         * Fields PlayIntegritySpoofService.expandFingerprint() overwrites from FINGERPRINT,
         * in fingerprint order.
         */
        internal val FINGERPRINT_DERIVED_KEYS = listOf(
            "BRAND", "PRODUCT", "DEVICE", "RELEASE", "ID", "INCREMENTAL", "TYPE", "TAGS",
        )

        /** Keys carried over when a new config is fetched, and cleared by Reset. */
        private val FLAG_KEYS = listOf(
            "spoofBuild", "spoofProps", "spoofProvider", "spoofSignature",
            KEY_VENDING_BUILD, KEY_VENDING_SDK, "DEBUG", KEY_VERBOSE_LOGS,
        )

        /**
         * Defaults for fetched configs. These must match the defaults that
         * PlayIntegritySpoofService.loadConfigInternal() applies when a key is absent.
         */
        private val DEFAULT_SPOOF_FLAGS = linkedMapOf(
            "spoofBuild" to "true",
            "spoofProps" to "true",
            "spoofProvider" to "false",
            "spoofSignature" to "false",
            KEY_VENDING_BUILD to "true",
            KEY_VENDING_SDK to "false",
            "DEBUG" to "false",
        )

        private fun JSONObject.putDefaultSpoofFlags(): JSONObject = apply {
            DEFAULT_SPOOF_FLAGS.forEach { (key, value) -> if (!has(key)) put(key, value) }
        }

        internal fun isValidFingerprint(value: String) = FINGERPRINT_REGEX.matches(value.trim())

        /** The fields expandFingerprint() would derive from [fingerprint]. */
        private fun fingerprintFields(fingerprint: String): Map<String, String> {
            val parts = fingerprint.split('/', ':')
            return FINGERPRINT_DERIVED_KEYS.zip(parts)
                .filter { (_, v) -> v.isNotEmpty() }
                .toMap()
        }

        private fun patchAgeDays(patch: String?): Long? {
            if (patch.isNullOrEmpty() || !DATE_REGEX.matches(patch.trim())) return null
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                val at = sdf.parse(patch.trim())?.time ?: return null
                TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - at).coerceAtLeast(0)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Reads the config from a JSON string (stored in Settings.Secure).
         * Also handles legacy prop-format strings in case an old value is present.
         */
        private fun readConfigData(content: String): Map<String, String> {
            return try {
                val result = linkedMapOf<String, String>()
                val trimmed = content.trim()
                if (trimmed.startsWith("{")) {
                    val json = JSONObject(trimmed)
                    json.keys().forEach { key -> result[key] = json.optString(key, "") }
                } else {
                    trimmed.lines().forEach { line ->
                        val l = line.trim()
                        if (l.isNotEmpty() && !l.startsWith("#") && !l.startsWith("//")) {
                            val eq = l.indexOf('=')
                            if (eq > 0) {
                                result[l.substring(0, eq).trim()] =
                                    l.substring(eq + 1).substringBefore('#').trim()
                            }
                        }
                    }
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read config", e)
                emptyMap()
            }
        }

        /**
         * Validates an imported PIF payload (JSON or prop-format) and normalises it
         * to a JSON string for Settings.Secure. Throws if the payload is empty or
         * malformed, so a broken file is never stored.
         */
        private fun normalizePifPayload(raw: String): String {
            val trimmed = raw.removePrefix("\uFEFF").trim()
            require(trimmed.isNotEmpty()) { "Config file is empty" }

            val json = if (trimmed.startsWith("{")) {
                JSONObject(trimmed)
            } else {
                JSONObject().apply {
                    trimmed.lines().forEach { line ->
                        val stripped = line.trim()
                        if (stripped.isEmpty() || stripped.startsWith("#") ||
                            stripped.startsWith("//")) return@forEach
                        val eq = stripped.indexOf('=')
                        if (eq > 0) {
                            val key = stripped.substring(0, eq).trim()
                            val value = stripped.substring(eq + 1).trim().substringBefore('#').trim()
                            if (key.isNotEmpty()) put(key, value)
                        }
                    }
                }
            }

            require(json.length() > 0) { "Config file has no valid entries" }
            return json.toString(2)
        }
    }
}
