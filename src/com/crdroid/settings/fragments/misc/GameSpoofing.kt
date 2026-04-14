/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.misc

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GameSpoofing : SettingsPreferenceFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enabled = false
    private var gameConfigs = mutableListOf<GameConfig>()

    data class GameConfig(
        val packageName: String,
        val appName: String,
        val props: Map<String, String>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.game_spoofing)

        findPreference<SwitchPreferenceCompat>("gs_enabled")?.setOnPreferenceChangeListener { _, newValue ->
            enabled = newValue as Boolean
            saveConfig()
            true
        }

        findPreference<Preference>("gs_add_game")?.setOnPreferenceClickListener {
            showAddGameDialog()
            true
        }

        findPreference<Preference>("gs_reload")?.setOnPreferenceClickListener {
            loadConfig()
            true
        }

        File(CONFIG_PATH).also { if (!it.exists()) it.mkdirs() }
        loadConfig()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun loadConfig() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { readGamePropsConfig() }
            enabled = result.first
            // Resolve app names from PackageManager
            val pm = requireContext().packageManager
            gameConfigs = result.second.map { game ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    game.packageName
                }
                game.copy(appName = label)
            }.toMutableList()

            findPreference<SwitchPreferenceCompat>("gs_enabled")?.isChecked = enabled
            populateGameList()
        }
    }

    private fun populateGameList() {
        val category = findPreference<PreferenceCategory>("gs_games_category") ?: return
        category.removeAll()

        if (gameConfigs.isEmpty()) {
            category.addPreference(Preference(requireContext()).apply {
                title = getString(R.string.gs_no_apps)
                summary = getString(R.string.gs_no_apps_summary)
                isSelectable = false
            })
            return
        }

        for (game in gameConfigs) {
            val propsText = game.props.entries.joinToString(", ") { "${it.key}=${it.value}" }
            category.addPreference(Preference(requireContext()).apply {
                title = game.appName
                summary = "${game.packageName}\n$propsText"
                setOnPreferenceClickListener {
                    showGameOptionsDialog(game)
                    true
                }
            })
        }
    }

    private fun showGameOptionsDialog(game: GameConfig) {
        val options = arrayOf(
            getString(R.string.gs_edit_props),
            getString(R.string.gs_change_profile),
            getString(R.string.gs_remove),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(game.appName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditPropsDialog(game)
                    1 -> showProfileSelector(game)
                    2 -> showDeleteGameDialog(game)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddGameDialog() {
        scope.launch {
            val progress = AlertDialog.Builder(requireContext())
                .setMessage(R.string.gs_loading_apps)
                .setCancelable(false)
                .show()

            try {
                val (labels, packages) = withContext(Dispatchers.IO) { getInstalledApps() }
                progress.dismiss()

                val configured = gameConfigs.map { it.packageName }.toSet()
                val availableIdx = packages.indices.filter { packages[it] !in configured }
                val availableLabels = availableIdx.map { labels[it] }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.gs_select_app)
                    .setItems(availableLabels) { _, which ->
                        val idx = availableIdx[which]
                        showProfileSelector(
                            GameConfig(packages[idx], labels[idx], emptyMap())
                        )
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (e: Exception) {
                progress.dismiss()
                toast(getString(R.string.gs_failed, e.message ?: ""))
            }
        }
    }

    private fun showProfileSelector(game: GameConfig) {
        val profileNames = PRESET_PROFILES.map { it.first }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.gs_select_profile)
            .setItems(profileNames) { _, which ->
                val props = PRESET_PROFILES[which].second
                val newGame = game.copy(props = props)

                val existing = gameConfigs.indexOfFirst { it.packageName == newGame.packageName }
                if (existing >= 0) {
                    gameConfigs[existing] = newGame
                } else {
                    gameConfigs.add(newGame)
                }
                saveConfig()
                populateGameList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditPropsDialog(game: GameConfig) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        val keyFields = mutableListOf<Pair<EditText, EditText>>()
        for ((key, value) in game.props) {
            val keyField = EditText(ctx).apply { setText(key); hint = getString(R.string.gs_key) }
            val valueField = EditText(ctx).apply { setText(value); hint = getString(R.string.gs_value) }
            container.addView(keyField)
            container.addView(valueField)
            keyFields.add(keyField to valueField)
        }
        if (keyFields.isEmpty()) {
            val keyField = EditText(ctx).apply { hint = "MODEL" }
            val valueField = EditText(ctx).apply { hint = getString(R.string.gs_value) }
            container.addView(keyField)
            container.addView(valueField)
            keyFields.add(keyField to valueField)
        }

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.gs_edit_app, game.appName))
            .setView(container)
            .setPositiveButton(R.string.gs_save) { _, _ ->
                val newProps = mutableMapOf<String, String>()
                for ((kf, vf) in keyFields) {
                    val k = kf.text.toString().trim()
                    val v = vf.text.toString().trim()
                    if (k.isNotEmpty()) newProps[k] = v
                }
                val idx = gameConfigs.indexOfFirst { it.packageName == game.packageName }
                if (idx >= 0) {
                    gameConfigs[idx] = game.copy(props = newProps)
                    saveConfig()
                    populateGameList()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteGameDialog(game: GameConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.gs_remove_title)
            .setMessage(getString(R.string.gs_remove_message, game.appName))
            .setPositiveButton(R.string.gs_remove) { _, _ ->
                gameConfigs.removeAll { it.packageName == game.packageName }
                saveConfig()
                populateGameList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveConfig() {
        scope.launch {
            withContext(Dispatchers.IO) { writeGamePropsConfig(enabled, gameConfigs) }
            toast(getString(R.string.gs_config_saved))
        }
    }

    private fun getInstalledApps(): Pair<Array<String>, Array<String>> {
        val pm = requireContext().packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        return apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray() to
            apps.map { it.packageName }.toTypedArray()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS

    companion object {
        private const val TAG = "GameSpoofing"
        private const val CONFIG_PATH = "/data/system/gameprops"
        private const val CONFIG_FILE = "gameprops.json"

        private val PRESET_PROFILES = listOf(
            "ROG Phone 8 Pro" to mapOf("MODEL" to "ASUS_AI2401_A", "MANUFACTURER" to "asus"),
            "Galaxy S24 Ultra" to mapOf("MODEL" to "SM-S928B", "MANUFACTURER" to "samsung"),
            "Xiaomi 13 Pro" to mapOf("MODEL" to "2210132C", "MANUFACTURER" to "Xiaomi"),
            "OnePlus 9 Pro" to mapOf("MODEL" to "LE2101", "MANUFACTURER" to "OnePlus"),
            "Black Shark 4" to mapOf("MODEL" to "2SM-X706B", "MANUFACTURER" to "blackshark"),
            "Lenovo Y700" to mapOf("MODEL" to "Lenovo TB-9707F", "MANUFACTURER" to "Lenovo"),
        )

        private fun readGamePropsConfig(): Pair<Boolean, List<GameConfig>> {
            val file = File(CONFIG_PATH, CONFIG_FILE)
            if (!file.exists()) return false to emptyList()
            return try {
                val json = JSONObject(file.readText())
                val isEnabled = json.optBoolean("enabled", false)
                val games = mutableListOf<GameConfig>()
                val gamesObj = json.optJSONObject("games")
                gamesObj?.keys()?.forEach { pkg ->
                    val propsObj = gamesObj.getJSONObject(pkg)
                    val props = mutableMapOf<String, String>()
                    propsObj.keys().forEach { k -> props[k] = propsObj.getString(k) }
                    games.add(GameConfig(pkg, pkg, props))
                }
                isEnabled to games
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load config", e)
                false to emptyList()
            }
        }

        private fun writeGamePropsConfig(isEnabled: Boolean, games: List<GameConfig>) {
            try {
                val json = JSONObject()
                json.put("enabled", isEnabled)
                val gamesObj = JSONObject()
                games.forEach { game ->
                    val propsObj = JSONObject()
                    game.props.forEach { (k, v) -> propsObj.put(k, v) }
                    gamesObj.put(game.packageName, propsObj)
                }
                json.put("games", gamesObj)
                val file = File(CONFIG_PATH, CONFIG_FILE)
                file.writeText(json.toString(2))
                file.setReadable(true, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save config", e)
            }
        }
    }
}
