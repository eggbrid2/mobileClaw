package com.mobileclaw.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Standard avatar for the minimal black-white AI style.
 * Image avatars still render as-is; emoji avatars sit on a quiet monochrome surface.
 */
@Composable
fun GradientAvatar(
    emoji: String,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    fontSize: TextUnit = (size.value * 0.46f).sp,
) {
    val isImageUri = emoji.startsWith("content://") || emoji.startsWith("file://") ||
        emoji.startsWith("data:") || emoji.startsWith("/")
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = emoji) {
        if (isImageUri) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        emoji.startsWith("data:") -> {
                            val base64Data = emoji.substringAfter(",")
                            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                        emoji.startsWith("/") -> {
                            // Bare file system path — decode directly
                            BitmapFactory.decodeFile(emoji)?.asImageBitmap()
                        }
                        else -> {
                            context.contentResolver.openInputStream(Uri.parse(emoji))?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                        }
                    }
                }.getOrNull()
            }
        }
    }
    val c = LocalClawColors.current
    val surface = if (c.isDark) c.cardAlt else c.card
    val ring = if (color == c.accent) c.accent.copy(alpha = 0.42f) else c.border
    val gradient = Brush.radialGradient(
        colors = listOf(surface, c.cardAlt),
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(brush = gradient)
            .border(1.dp, ring, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (isImageUri) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Text(emoji, fontSize = fontSize)
        }
    }
}

/**
 * Rounded-rectangle variant of GradientAvatar, for use in action bars / chips.
 */
@Composable
fun GradientAvatarRounded(
    emoji: String,
    size: Dp,
    color: Color,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = (size.value * 0.46f).sp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    GradientAvatar(emoji = emoji, size = size, color = color, modifier = modifier, shape = shape, fontSize = fontSize)
}

/**
 * Standard page header bar used across all secondary pages.
 * Fills behind the status bar with the surface color, then pads content below it.
 * Use as the first element in the page Column.
 */
@Composable
fun ClawPageHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalClawColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (onBack != null) 4.dp else 16.dp,
                    end = 8.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = c.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = title,
                color = c.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 4.dp else 0.dp),
            )
            actions()
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
    }
}

@Composable
fun ClawPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalClawColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (c.isDark) Color.White else Color(0xFF101010),
            contentColor = if (c.isDark) Color(0xFF101010) else Color.White,
            disabledContainerColor = c.cardAlt,
            disabledContentColor = c.subtext,
        ),
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ClawSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalClawColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(23.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = c.surface,
            contentColor = c.text,
            disabledContentColor = c.subtext,
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp, brush = Brush.linearGradient(listOf(c.border, c.border))),
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ClawListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalClawColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
            .then(if (onClick != null) Modifier.background(c.surface) else Modifier)
            .then(if (onClick != null) Modifier.border(0.5.dp, c.border, RoundedCornerShape(16.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = c.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = c.subtext,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Text(
                trailingText,
                color = c.subtext,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.subtext.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
