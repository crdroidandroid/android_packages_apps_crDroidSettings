/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.ui.fonts

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.crdroid.settings.fragments.misc.AppPickerSearchField
import com.crdroid.settings.fragments.misc.SpoofDialog
import com.crdroid.settings.fragments.misc.SpoofFooter
import com.crdroid.settings.fragments.misc.SpoofShapes
import com.crdroid.settings.fragments.misc.SpoofingEmptyState
import com.crdroid.settings.fragments.misc.SpoofingLoadingBox
import com.crdroid.settings.fragments.misc.spoofBottomInset
import com.crdroid.settings.fragments.misc.spoofComposeView
import com.crdroid.settings.fragments.misc.toolbarNestedScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Google Fonts browser, opened from the font picker's Online fonts section.
 */
class OnlineFontPicker : SettingsPreferenceFragment() {

    private lateinit var viewModel: OnlineFontPickerViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[OnlineFontPickerViewModel::class.java]
        requireActivity().title = getString(R.string.online_fonts_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = spoofComposeView(requireContext()).apply {
        setContent {
            SettingsTheme {
                OnlineFontPickerContent(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPending()
    }

    override fun getMetricsCategory(): Int = MetricsProto.MetricsEvent.CRDROID_SETTINGS
}

/** The family open in the detail dialog and the style being previewed. */
internal data class OnlineFontDetail(
    val font: OnlineFont,
    val variant: String,
    val file: File? = null,
    val typeface: Typeface? = null,
    val failed: Boolean = false,
) {
    val ready: Boolean get() = file != null && typeface != null
}

class OnlineFontPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application
    private val repo = OnlineFontRepository(application)
    private val tracker = FontRestartTracker(application)

    private var catalog by mutableStateOf<List<OnlineFont>>(emptyList())

    /** Saved styles by family. */
    internal var downloads by mutableStateOf<Map<String, DownloadedFont>>(emptyMap())
        private set

    /**
     * The catalog, plus saved families it doesn't have (offline, or since removed from
     * Google Fonts), so downloads are always listed.
     */
    internal val fonts: List<OnlineFont> by derivedStateOf {
        val listed = catalog.mapTo(HashSet()) { it.family }
        catalog + downloads.values.map { it.font }.filter { it.family !in listed }
            .sortedBy { it.popularity }
    }

    internal val catalogLoaded: Boolean get() = catalog.isNotEmpty()

    /** The list couldn't be fetched, but saved fonts can still be shown. */
    internal val offline: Boolean get() = failed && fonts.isNotEmpty()
    internal var loading by mutableStateOf(true)
        private set
    internal var failed by mutableStateOf(false)
        private set
    internal var query by mutableStateOf("")
    internal var category by mutableStateOf(OnlineFontCategory.ALL)
    internal var sortByName by mutableStateOf(false)
    internal var pending by mutableStateOf<PendingRestart?>(null)
        private set
    internal var detail by mutableStateOf<OnlineFontDetail?>(null)
        private set

    /** Download progress of the style in [detail], 0..1; null when unknown. */
    internal var progress by mutableStateOf<Float?>(null)
        private set
    internal var error by mutableStateOf<String?>(null)
        private set

    internal val visible: List<OnlineFont> by derivedStateOf {
        val q = query.trim().lowercase(Locale.ROOT)
        val api = category.apiName
        val matches = fonts.filter { font ->
            (api == null || font.category == api) &&
                (q.isEmpty() || font.family.lowercase(Locale.ROOT).contains(q))
        }
        val sorted = if (sortByName) matches.sortedBy { it.family.lowercase(Locale.ROOT) } else matches
        // Downloads first, each part keeping the chosen order.
        val (saved, rest) = sorted.partition { it.family in downloads }
        saved + rest
    }

    private var variantJob: Job? = null

    init {
        repo.resetFailures()
        load(reload = false)
        refreshPending()
    }

    internal fun load(reload: Boolean) {
        loading = true
        failed = false
        viewModelScope.launch {
            refreshDownloads()
            // The list fetched last time shows at once...
            if (catalog.isEmpty()) repo.savedFamilies()?.let { catalog = it }
            // ...and is replaced in the background once it is a day old, or on Retry.
            val stale = withContext(Dispatchers.IO) { repo.familiesStale() }
            if (reload || stale || catalog.isEmpty()) {
                if (catalog.isNotEmpty()) loading = false
                runCatching { repo.fetchFamilies() }
                    .onSuccess { fresh ->
                        catalog = fresh
                        // Saved fonts Google has updated since are fetched again quietly.
                        withContext(Dispatchers.IO) { repo.updateDownloads(fresh) }
                        refreshDownloads()
                    }
                    .onFailure {
                        Log.w(TAG, "Google Fonts list unavailable", it)
                        // An older list on screen is still good to use.
                        if (catalog.isEmpty()) failed = true
                    }
            }
            loading = false
        }
    }

    fun refreshPending() {
        viewModelScope.launch {
            pending = withContext(Dispatchers.IO) { tracker.pending() }
        }
    }

    private suspend fun refreshDownloads() {
        downloads = withContext(Dispatchers.IO) { repo.downloads() }.associateBy { it.font.family }
    }

    internal fun isSaved(font: OnlineFont, variant: String): Boolean =
        downloads[font.family]?.variants?.contains(variant) == true

    internal fun cachedPreview(font: OnlineFont): Typeface? = repo.cachedPreview(font)

    internal suspend fun preview(font: OnlineFont): Typeface? = repo.preview(font)

    internal fun open(font: OnlineFont) {
        if (FontOps.busy != null) return
        // A saved style opens first, so a downloaded font shows at once, even offline.
        val saved = downloads[font.family]?.variants.orEmpty()
        val variant = when {
            saved.isEmpty() || font.defaultVariant in saved -> font.defaultVariant
            else -> font.variants.firstOrNull { it in saved } ?: font.defaultVariant
        }
        detail = OnlineFontDetail(font, variant)
        loadVariant()
    }

    internal fun selectVariant(variant: String) {
        val current = detail ?: return
        if (current.variant == variant && !current.failed) return
        detail = OnlineFontDetail(current.font, variant)
        loadVariant()
    }

    internal fun retryVariant() {
        val current = detail ?: return
        detail = OnlineFontDetail(current.font, current.variant)
        loadVariant()
    }

    private fun loadVariant() {
        val target = detail ?: return
        variantJob?.cancel()
        progress = null
        variantJob = viewModelScope.launch {
            val result = try {
                val file = repo.download(target.font, target.variant) { progress = it }
                val typeface = withContext(Dispatchers.IO) { repo.load(file) }
                if (typeface == null) target.copy(failed = true)
                else target.copy(file = file, typeface = typeface)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download of ${target.font.family} ${target.variant} failed", e)
                target.copy(failed = true)
            }
            // Only if the user hasn't moved on to another style or font meanwhile.
            val now = detail
            if (now != null && now.font == target.font && now.variant == target.variant) {
                detail = result
            }
        }
    }

    internal fun dismissDetail() {
        variantJob?.cancel()
        detail = null
    }

    internal fun install(rebootNow: Boolean) {
        val target = detail?.takeIf { it.ready } ?: return
        val file = target.file ?: return
        if (FontOps.busy != null) return
        detail = null
        val name = "${target.font.family} ${variantLabel(app, target.variant)}"
        CustomFontInstall.startFromFile(app, file, name, rebootNow) { error = it }
        if (!isSaved(target.font, target.variant)) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repo.saveDownload(target.font, target.variant, file) }
                refreshDownloads()
            }
        }
    }

    /** "Download, install later": keeps the previewed style on the device. */
    internal fun saveForLater() {
        val target = detail?.takeIf { it.ready } ?: return
        val file = target.file ?: return
        detail = null
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                repo.saveDownload(target.font, target.variant, file)
            }
            refreshDownloads()
            if (saved) {
                FontOps.toast(app, R.string.online_fonts_download_done, styleName(target))
            } else {
                error = app.getString(R.string.font_error_storage)
            }
        }
    }

    internal fun deleteDownload() {
        val target = detail ?: return
        detail = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteDownload(target.font, target.variant) }
            refreshDownloads()
            FontOps.toast(app, R.string.online_fonts_download_deleted, styleName(target))
        }
    }

    private fun styleName(detail: OnlineFontDetail) =
        "${detail.font.family} ${variantLabel(app, detail.variant)}"

    internal fun dismissError() {
        error = null
    }

    internal fun restartNow(activityContext: Context) {
        val kind = pending?.kind ?: return
        tracker.restartNow(kind, activityContext)
        if (kind == RestartKind.SYSTEMUI) pending = null
    }

    override fun onCleared() {
        variantJob?.cancel()
        // Styles opened here stay cached for next time, within the cache's size limit.
        repo.trimCache()
    }

    private companion object {
        const val TAG = "OnlineFontPicker"
    }
}

private val WEIGHT_NAMES = mapOf(
    100 to R.string.font_weight_thin,
    200 to R.string.font_weight_extra_light,
    300 to R.string.font_weight_light,
    400 to R.string.font_weight_regular,
    500 to R.string.font_weight_medium,
    600 to R.string.font_weight_semi_bold,
    700 to R.string.font_weight_bold,
    800 to R.string.font_weight_extra_bold,
    900 to R.string.font_weight_black,
)

/** "Bold", "Light italic", "Italic" (regular italic), as Google Fonts names styles. */
internal fun variantLabel(context: Context, variant: String): String {
    val weight = variant.weight()
    val italic = variant.isItalic()
    if (italic && weight == 400) return context.getString(R.string.font_variant_italic)
    val name = WEIGHT_NAMES[weight]?.let(context::getString) ?: weight.toString()
    return if (italic) context.getString(R.string.font_variant_weight_italic, name) else name
}

@Composable
private fun categoryLabel(category: String): String =
    OnlineFontCategory.entries.firstOrNull { it.apiName == category }
        ?.let { stringResource(it.labelRes) }
        ?: category

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
internal fun OnlineFontPickerContent(vm: OnlineFontPickerViewModel) {
    val context = LocalContext.current
    val busy = FontOps.busy

    // An install finished (possibly one started before the activity was recreated).
    LaunchedEffect(FontOps.generation) { if (FontOps.generation > 0) vm.refreshPending() }

    OnlineFontDialogs(vm)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .toolbarNestedScroll(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp + spoofBottomInset(),
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val pending = vm.pending
        if (pending != null && busy == null) {
            fullWidth("__pending") {
                PendingRestartBanner(pending = pending, onRestart = { vm.restartNow(context) })
            }
        }
        if (busy is FontBusy.Installing || busy is FontBusy.Reading) {
            fullWidth("__busy") {
                BusyBanner(
                    text = if (busy is FontBusy.Installing) {
                        stringResource(R.string.font_installing, busy.name)
                    } else {
                        stringResource(R.string.font_reading)
                    },
                )
            }
        }
        fullWidth("__search") {
            AppPickerSearchField(
                query = vm.query,
                onQueryChange = { vm.query = it },
                placeholder = stringResource(R.string.online_fonts_search),
            )
        }
        fullWidth("__filters") {
            FilterRow(
                category = vm.category,
                sortByName = vm.sortByName,
                onCategory = { vm.category = it },
                onSortByName = { vm.sortByName = it },
            )
        }

        if (vm.offline) {
            fullWidth("__offline") {
                ListErrorCard(
                    text = stringResource(R.string.online_fonts_offline),
                    enabled = !vm.loading,
                    onRetry = { vm.load(reload = true) },
                )
            }
        }

        val fonts = vm.visible
        when {
            vm.loading && vm.fonts.isEmpty() -> fullWidth("__loading") {
                SpoofingLoadingBox(Modifier.padding(vertical = 48.dp))
            }
            vm.fonts.isEmpty() -> fullWidth("__error") {
                ListErrorCard(
                    text = stringResource(R.string.online_fonts_error),
                    enabled = !vm.loading,
                    onRetry = { vm.load(reload = true) },
                )
            }
            fonts.isEmpty() -> fullWidth("__empty") {
                SpoofingEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.online_fonts_empty_title),
                    description = stringResource(R.string.online_fonts_empty_body),
                )
            }
            else -> items(fonts, key = { it.family }) { font ->
                OnlineFontCard(
                    font = font,
                    vm = vm,
                    downloaded = font.family in vm.downloads,
                    enabled = busy == null,
                    onClick = { vm.open(font) },
                )
            }
        }
        // Downloads are shown while the full list is still on its way.
        if (vm.loading && !vm.catalogLoaded && vm.fonts.isNotEmpty()) {
            fullWidth("__loading_more") { SpoofingLoadingBox(Modifier.padding(vertical = 32.dp)) }
        }

        fullWidth("__footer") { SpoofFooter(text = stringResource(R.string.online_fonts_footer)) }
    }
}

@Composable
private fun FilterRow(
    category: OnlineFontCategory,
    sortByName: Boolean,
    onCategory: (OnlineFontCategory) -> Unit,
    onSortByName: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = sortByName,
            onClick = { onSortByName(!sortByName) },
            label = { Text(stringResource(R.string.online_fonts_sort_name)) },
            leadingIcon = {
                Icon(Icons.Rounded.SortByAlpha, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        OnlineFontCategory.entries.forEach { entry ->
            FilterChip(
                selected = entry == category,
                onClick = { onCategory(entry) },
                label = { Text(stringResource(entry.labelRes)) },
            )
        }
    }
}

@Composable
private fun OnlineFontCard(
    font: OnlineFont,
    vm: OnlineFontPickerViewModel,
    downloaded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fetched while the card is on screen; scrolling it away cancels a queued download.
    val typeface by produceState(vm.cachedPreview(font), font) {
        if (value == null) value = vm.preview(font)
    }
    val family = remember(typeface) { typeface?.let { FontFamily(it) } ?: FontFamily.Default }
    val previewAlpha by animateFloatAsState(
        targetValue = if (typeface != null) 1f else 0.38f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "online_font_preview",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(R.string.font_sample_glyphs),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = family,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(previewAlpha),
                )
                if (downloaded) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.online_fonts_downloaded),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = font.family,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = family,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(previewAlpha),
            )
            Text(
                text = listOf(
                    if (downloaded) stringResource(R.string.online_fonts_downloaded)
                    else categoryLabel(font.category),
                    LocalContext.current.resources.getQuantityString(
                        R.plurals.online_fonts_styles, font.variants.size, font.variants.size,
                    ),
                ).filter { it.isNotEmpty() }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ListErrorCard(text: String, enabled: Boolean, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SpoofShapes.single,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.emoji_catalog_retry))
            }
        }
    }
}

@Composable
private fun OnlineFontDialogs(vm: OnlineFontPickerViewModel) {
    vm.detail?.let { detail ->
        OnlineFontDetailDialog(
            detail = detail,
            progress = vm.progress,
            titleTypeface = vm.cachedPreview(detail.font),
            installEnabled = detail.ready && FontOps.busy == null,
            savedVariants = vm.downloads[detail.font.family]?.variants.orEmpty(),
            onVariant = vm::selectVariant,
            onRetry = vm::retryVariant,
            onInstall = vm::install,
            onSaveForLater = vm::saveForLater,
            onDeleteDownload = vm::deleteDownload,
            onDismiss = vm::dismissDetail,
        )
    }
    vm.error?.let { message ->
        SpoofDialog(
            onDismissRequest = vm::dismissError,
            title = stringResource(R.string.font_error_title),
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = vm::dismissError) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
private fun OnlineFontDetailDialog(
    detail: OnlineFontDetail,
    progress: Float?,
    titleTypeface: Typeface?,
    installEnabled: Boolean,
    savedVariants: Set<String>,
    onVariant: (String) -> Unit,
    onRetry: () -> Unit,
    onInstall: (rebootNow: Boolean) -> Unit,
    onSaveForLater: () -> Unit,
    onDeleteDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val saved = detail.variant in savedVariants
    val context = LocalContext.current
    val font = detail.font
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = font.family,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = remember(titleTypeface) {
                        titleTypeface?.let { FontFamily(it) } ?: FontFamily.Default
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOf(
                    categoryLabel(font.category),
                    font.designers.joinToString(", "),
                ).filter { it.isNotEmpty() }.joinToString("  ·  ")
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (font.variants.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        font.variants.forEach { variant ->
                            FilterChip(
                                selected = variant == detail.variant,
                                onClick = { onVariant(variant) },
                                label = { Text(remember(variant) { variantLabel(context, variant) }) },
                                // Saved styles are ticked, like downloaded fonts in the list.
                                leadingIcon = if (variant in savedVariants) {
                                    {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = stringResource(R.string.online_fonts_downloaded),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val typeface = detail.typeface
                    when {
                        typeface != null -> FontSpecimen(remember(typeface) { FontFamily(typeface) })
                        detail.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.online_fonts_download_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.emoji_catalog_retry))
                            }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator(Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (progress != null) {
                                    stringResource(
                                        R.string.online_fonts_downloading_progress,
                                        (progress * 100).toInt(),
                                    )
                                } else {
                                    stringResource(R.string.online_fonts_downloading)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.font_install_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onInstall(true) },
                    enabled = installEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SpoofShapes.pill,
                ) { Text(stringResource(R.string.font_install_reboot_now)) }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onInstall(false) },
                    enabled = installEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SpoofShapes.pill,
                ) { Text(stringResource(R.string.font_install_reboot_later)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = if (saved) onDeleteDownload else onSaveForLater,
                    // Deleting needs nothing loaded (but not mid-install); saving needs the
                    // style downloaded.
                    enabled = if (saved) FontOps.busy == null else installEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SpoofShapes.pill,
                ) {
                    Text(
                        stringResource(
                            if (saved) R.string.online_fonts_delete_download
                            else R.string.online_fonts_download_only,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}
