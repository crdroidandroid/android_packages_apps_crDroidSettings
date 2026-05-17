/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */
package com.crdroid.settings.fragments.lockscreen

import android.app.WallpaperManager
import android.content.Context
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.android.internal.logging.nano.MetricsProto
import com.android.internal.util.crdroid.ThemeUtils
import com.android.settings.R
import com.android.settingslib.spa.framework.theme.SettingsTheme

import com.crdroid.settings.utils.SystemUtils

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
                        },
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }

    private fun applyClock(clockStyle: Int) {
        val ctx = requireContext()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockPickerScreen(
    initialClock: Int,
    onApply: (Int) -> Unit,
    onBack: () -> Unit
) {
    var selectedClock by remember { mutableStateOf(initialClock) }
    val clockNames = ClockUtils.getClockNames()
    val clockLayouts = ClockUtils.CLOCK_LAYOUTS
    val context = LocalContext.current
    
    val wallpaperBitmap = remember {
        val wm = WallpaperManager.getInstance(context)
        wm.getDrawable(WallpaperManager.FLAG_LOCK)?.toBitmap(400, 800)?.asImageBitmap()
            ?: wm.getDrawable(WallpaperManager.FLAG_SYSTEM)?.toBitmap(400, 800)?.asImageBitmap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select custom clock") },
                actions = {
                    Button(
                        onClick = { onApply(selectedClock) },
                        enabled = selectedClock != initialClock,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(clockLayouts.size) { index ->
                ClockItem(
                    index = index,
                    name = clockNames[index],
                    layoutRes = clockLayouts[index],
                    isSelected = index == selectedClock,
                    wallpaper = wallpaperBitmap,
                    onClick = { selectedClock = index }
                )
            }
        }
    }
}

@Composable
fun ClockItem(
    index: Int,
    name: String,
    layoutRes: Int,
    isSelected: Boolean,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .height(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            }
            
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        LayoutInflater.from(context).inflate(layoutRes, this, true)
                        post {
                            if (childCount > 0) {
                                val view = getChildAt(0)
                                view.scaleX = 0.45f
                                view.scaleY = 0.45f
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 20.dp)
            )
            
            Text(
                text = name,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
