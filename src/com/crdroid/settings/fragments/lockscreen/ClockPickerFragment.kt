/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.crdroid.settings.fragments.lockscreen

import android.app.WallpaperManager
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.android.internal.util.crdroid.ThemeUtils
import com.android.settings.R
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.crdroid.settings.utils.SystemUtils
import kotlin.math.roundToInt

class ClockPickerFragment : Fragment() {

    private lateinit var themeUtils: ThemeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeUtils = ThemeUtils(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            isNestedScrollingEnabled = true
            setContent {
                SettingsTheme {
                    val initialClock = remember {
                        Settings.Secure.getIntForUser(
                            context.contentResolver,
                            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
                            0,
                            UserHandle.USER_CURRENT
                        )
                    }
                    ClockPickerScreen(
                        initialClock = initialClock,
                        onApply = { clockStyle ->
                            applyClock(clockStyle)
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    )
                }
            }
        }
    }

    private fun applyClock(clockStyle: Int) {
        val ctx = requireContext()
        val current = Settings.Secure.getIntForUser(
            ctx.contentResolver,
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
            0,
            UserHandle.USER_CURRENT
        )
        if (clockStyle == current) return

        Settings.Secure.putIntForUser(
            ctx.contentResolver,
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_STYLE,
            clockStyle,
            UserHandle.USER_CURRENT
        )
        updateClockOverlays(clockStyle)
        SystemUtils.restartSystemUI(ctx)
    }

    private fun updateClockOverlays(clockStyle: Int) {
        themeUtils.setOverlayEnabled(
            "android.theme.customization.hideclock",
            if (clockStyle != 0) "com.android.systemui.clocks.hideclock" else "android",
            "android"
        )
        themeUtils.setOverlayEnabled(
            "android.theme.customization.smartspace",
            if (clockStyle != 0) "com.android.systemui.hide.smartspace" else "com.android.systemui",
            "com.android.systemui"
        )
    }
}

@Composable
fun ClockPickerScreen(
    initialClock: Int,
    onApply: (Int) -> Unit
) {
    var selectedClock by rememberSaveable { mutableStateOf(initialClock) }
    val clockNames = remember { ClockUtils.getClockNames() }
    val clockLayouts = remember { ClockUtils.CLOCK_LAYOUTS }
    val context = LocalContext.current
    val hostView = LocalView.current

    val wallpaperBitmap: ImageBitmap? = remember {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            (wm.getDrawable(WallpaperManager.FLAG_LOCK)
                ?: wm.getDrawable(WallpaperManager.FLAG_SYSTEM))
                ?.toBitmap(240, 480)
                ?.asImageBitmap()
        }.getOrNull()
    }

    val nestedScrollInterop = remember(hostView) {
        ComposeToViewNestedScrollBridge(hostView)
    }
    DisposableEffect(nestedScrollInterop) {
        onDispose { nestedScrollInterop.stop() }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val hasSelectionChange = selectedClock != initialClock

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = navBarBottom + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollInterop)
        ) {
            items(
                count = clockLayouts.size,
                key = { it }
            ) { index ->
                ClockItem(
                    name = clockNames[index],
                    layoutRes = clockLayouts[index],
                    isSelected = index == selectedClock,
                    wallpaper = wallpaperBitmap,
                    onClick = { selectedClock = index }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onApply(selectedClock) },
            icon = { Icon(Icons.Default.Check, contentDescription = null) },
            text = { Text(stringResource(R.string.apply)) },
            containerColor = if (hasSelectionChange) {
                FloatingActionButtonDefaults.containerColor
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun ClockItem(
    name: String,
    layoutRes: Int,
    isSelected: Boolean,
    wallpaper: ImageBitmap?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (wallpaper != null) {
                    Image(
                        bitmap = wallpaper,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.5f
                    )
                }

                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            clipChildren = false
                            clipToPadding = false
                            isClickable = false
                            isFocusable = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                            LayoutInflater.from(ctx).inflate(layoutRes, this, true)

                            if (childCount > 0) {
                                val child = getChildAt(0)
                                child.scaleX = 0.45f
                                child.scaleY = 0.45f
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            Text(
                text = name,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private class ComposeToViewNestedScrollBridge(
    view: View
) : NestedScrollConnection {

    private val helper = NestedScrollingChildHelper(view).apply {
        isNestedScrollingEnabled = true
    }

    private val consumedScroll = IntArray(2)

    private fun NestedScrollSource.toViewType(): Int =
        if (this == NestedScrollSource.Fling) ViewCompat.TYPE_NON_TOUCH
        else ViewCompat.TYPE_TOUCH

    private fun ensureStarted(type: Int) {
        if (!helper.hasNestedScrollingParent(type)) {
            helper.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type)
        }
    }

    private fun toViewDelta(value: Float): Int = (-value).roundToInt()
    private fun toComposeDelta(value: Int): Float = -value.toFloat()

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val type = source.toViewType()
        ensureStarted(type)
        consumedScroll[0] = 0
        consumedScroll[1] = 0
        helper.dispatchNestedPreScroll(
            toViewDelta(available.x),
            toViewDelta(available.y),
            consumedScroll,
            /* offsetInWindow = */ null,
            type
        )
        return Offset(
            toComposeDelta(consumedScroll[0]),
            toComposeDelta(consumedScroll[1])
        )
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val type = source.toViewType()
        ensureStarted(type)
        consumedScroll[0] = 0
        consumedScroll[1] = 0
        helper.dispatchNestedScroll(
            toViewDelta(consumed.x),
            toViewDelta(consumed.y),
            toViewDelta(available.x),
            toViewDelta(available.y),
            /* offsetInWindow = */ null,
            type,
            consumedScroll
        )
        return Offset(
            toComposeDelta(consumedScroll[0]),
            toComposeDelta(consumedScroll[1])
        )
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val consumed = helper.dispatchNestedPreFling(-available.x, -available.y)
        return if (consumed) available else Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        helper.dispatchNestedFling(-available.x, -available.y, /* consumed = */ true)
        return available
    }

    fun stop() {
        helper.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        helper.stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
    }
}
