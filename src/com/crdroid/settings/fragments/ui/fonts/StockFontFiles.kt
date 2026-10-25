/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.ui.fonts

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.math.abs

/**
 * Resolves a font family name to its font file by reading the font XML files on disk.
 *
 * Nothing that comes from the running system can be used for this. The live font table is
 * rebuilt at boot for whichever font was active then, and a custom font replaces the stock
 * family in place, so resolving the name at runtime (Typeface.create, or SystemFonts' parsed
 * config) gives the boot-time font rather than the stock one. The XML files and the font
 * files they list are read-only partitions and always describe the stock fonts.
 *
 * Files read, later ones overriding earlier definitions of the same family:
 *  - /system/etc/fonts.xml
 *  - /system/etc/font_fallback.xml (Android 15+, may use <family-list>)
 *  - /product/etc/fonts_customization.xml (OEM families, e.g. "new-named-family")
 */
internal object StockFontFiles {

    private const val TAG = "StockFontFiles"
    private const val MAX_ALIAS_DEPTH = 4

    private class Source(val xml: String, val fontDir: String)

    private val SOURCES = listOf(
        Source("/system/etc/fonts.xml", "/system/fonts"),
        Source("/system/etc/font_fallback.xml", "/system/fonts"),
        Source("/product/etc/fonts_customization.xml", "/product/fonts"),
    )

    private class Entry(
        val file: File,
        val weight: Int,
        val italic: Boolean,
        val ttcIndex: Int,
        val variationSettings: String,
    )

    private class Parsed(
        val families: Map<String, List<Entry>>,
        val aliases: Map<String, String>,
    )

    @Volatile
    private var parsed: Parsed? = null

    /** The regular upright face of [familyName] loaded from its file, or null. */
    fun typeface(familyName: String): Typeface? = try {
        val config = parsed ?: parseAll().also { parsed = it }
        val name = resolveAlias(config, familyName)
        val entry = config.families[name]
            ?.filter { it.file.isFile }
            ?.minByOrNull { (if (it.italic) 10_000 else 0) + abs(it.weight - FontStyle.FONT_WEIGHT_NORMAL) }
        if (entry == null) {
            Log.w(TAG, "No stock font file for $familyName (resolved to $name)")
            null
        } else {
            build(entry)
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to load stock font for $familyName", t)
        null
    }

    /**
     * Loads the file through Font.Builder, which throws on failure, rather than
     * Typeface.Builder, which quietly returns Typeface.DEFAULT (the boot-time font again).
     */
    private fun build(entry: Entry): Typeface {
        val slant = if (entry.italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT
        val font = Font.Builder(entry.file)
            .setTtcIndex(entry.ttcIndex)
            .setWeight(entry.weight)
            .setSlant(slant)
            .apply { if (entry.variationSettings.isNotEmpty()) setFontVariationSettings(entry.variationSettings) }
            .build()
        return Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build())
            .setStyle(FontStyle(entry.weight, slant))
            .build()
    }

    private fun resolveAlias(config: Parsed, name: String): String {
        var current = name
        repeat(MAX_ALIAS_DEPTH) {
            if (config.families.containsKey(current)) return current
            current = config.aliases[current] ?: return current
        }
        return current
    }

    private fun parseAll(): Parsed {
        val families = LinkedHashMap<String, List<Entry>>()
        val aliases = HashMap<String, String>()
        for (source in SOURCES) {
            val file = File(source.xml)
            if (!file.isFile) continue
            runCatching { parse(file, source.fontDir) }
                .onSuccess { (fileFamilies, fileAliases) ->
                    families.putAll(fileFamilies)
                    aliases.putAll(fileAliases)
                }
                .onFailure { Log.w(TAG, "Failed to parse ${source.xml}", it) }
        }
        return Parsed(families, aliases)
    }

    private fun parse(
        xml: File,
        fontDir: String,
    ): Pair<Map<String, List<Entry>>, Map<String, String>> {
        val families = LinkedHashMap<String, MutableList<Entry>>()
        val aliases = HashMap<String, String>()

        xml.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)

            var listName: String? = null    // enclosing <family-list name>
            var familyName: String? = null  // enclosing <family name>
            var inFont = false
            var weight = FontStyle.FONT_WEIGHT_NORMAL
            var italic = false
            var ttcIndex = 0
            val fileName = StringBuilder()
            val axes = mutableListOf<String>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "family-list" -> listName = parser.getAttributeValue(null, "name")
                        "family" -> familyName = parser.getAttributeValue(null, "name") ?: listName
                        "font" -> {
                            inFont = true
                            weight = parser.getAttributeValue(null, "weight")?.toIntOrNull()
                                ?: FontStyle.FONT_WEIGHT_NORMAL
                            italic = parser.getAttributeValue(null, "style") == "italic"
                            ttcIndex = parser.getAttributeValue(null, "index")?.toIntOrNull() ?: 0
                            fileName.setLength(0)
                            axes.clear()
                        }
                        "axis" -> if (inFont) {
                            val tag = parser.getAttributeValue(null, "tag")
                            val value = parser.getAttributeValue(null, "stylevalue")
                            if (!tag.isNullOrEmpty() && !value.isNullOrEmpty()) axes += "'$tag' $value"
                        }
                        "alias" -> {
                            val name = parser.getAttributeValue(null, "name")
                            val to = parser.getAttributeValue(null, "to")
                            // Weight-specific aliases (e.g. sans-serif-medium) aren't the base face.
                            if (name != null && to != null && parser.getAttributeValue(null, "weight") == null) {
                                aliases[name] = to
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (inFont) fileName.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "font" -> {
                            inFont = false
                            val name = familyName
                            val path = fileName.toString().trim()
                            if (name != null && path.isNotEmpty()) {
                                val file = if (path.startsWith("/")) File(path) else File(fontDir, path)
                                families.getOrPut(name) { mutableListOf() } += Entry(
                                    file = file,
                                    weight = weight,
                                    italic = italic,
                                    ttcIndex = ttcIndex,
                                    variationSettings = axes.joinToString(", "),
                                )
                            }
                        }
                        "family" -> familyName = null
                        "family-list" -> listName = null
                    }
                }
                event = parser.next()
            }
        }
        return families to aliases
    }
}
