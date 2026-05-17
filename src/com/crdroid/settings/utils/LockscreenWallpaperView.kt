/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crdroid.settings.utils

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.ImageView

class LockscreenWallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var currentLockWallpaperDrawable: Drawable? = null

    private val wallpaperChecker = object : Runnable {
        override fun run() {
            updateLockscreenWallpaper()
            handler.postDelayed(this, 2000)
        }
    }

    init {
        updateLockscreenWallpaper()
        handler.postDelayed(wallpaperChecker, 2000)
    }

    private fun updateLockscreenWallpaper() {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val lockDrawable: Drawable? = try {
            wallpaperManager.getDrawable(WallpaperManager.FLAG_LOCK)
                ?: wallpaperManager.drawable
        } catch (e: Exception) {
            wallpaperManager.drawable
        }

        if (lockDrawable != null && lockDrawable != currentLockWallpaperDrawable) {
            currentLockWallpaperDrawable = lockDrawable
            setImageDrawable(lockDrawable)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(wallpaperChecker)
    }
}
