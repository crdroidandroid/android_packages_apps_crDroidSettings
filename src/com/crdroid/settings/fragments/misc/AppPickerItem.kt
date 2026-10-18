/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.crdroid.settings.fragments.misc

import android.graphics.drawable.Drawable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One app in a picker list: icon, label (with a SYS badge for system apps), package name and
 * a switch. The whole row toggles. [extraContent] is drawn under the text, e.g. the
 * TrickyStore mode selector. Pass [shape] from [SpoofShapes.forIndex] so a list reads as one
 * segmented card.
 */
@Composable
fun AppPickerItem(
    packageName: String,
    label: String,
    icon: Drawable?,
    isSystem: Boolean,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
    shape: Shape = SpoofShapes.single,
    extraContent: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = { onToggle(!checked) },
        enabled = enabled,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpoofDimens.RowMinHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconOrPlaceholder(
                    packageName = packageName,
                    icon = icon,
                    sizeDp = APP_ICON_SIZE_CARD,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isSystem) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SystemAppBadge()
                        }
                    }
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                CheckCloseSwitch(
                    checked = checked,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                )
            }
            extraContent?.invoke()
        }
    }
}
