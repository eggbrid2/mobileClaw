package com.mobileclaw.ui.common

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.str
import com.mobileclaw.ui.ClawColors
import com.mobileclaw.ui.ClawSymbolIcon
import com.mobileclaw.ui.LocalClawColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Attachment cards share one visual language so chat and group chat do not drift into different message systems.
@Composable
fun MediaAttachmentCardFrame(
    modifier: Modifier = Modifier,
    maxWidthDp: Dp,
    cornerRadiusDp: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val body = Modifier
        .widthIn(max = maxWidthDp)
        .clip(RoundedCornerShape(cornerRadiusDp))
        .background(Color.Black)
        .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerRadiusDp))

    Box(
        modifier = modifier.then(if (onClick != null) body.clickable { onClick() } else body),
        content = content,
    )
}

@Composable
fun AttachmentMetaChip(
    text: String,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background((if (dark) Color.Black else Color.White).copy(alpha = 0.54f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (dark) Color.White else Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun DocumentAttachmentCard(
    attachment: SkillAttachment.FileData,
    context: Context,
    modifier: Modifier = Modifier,
    c: ClawColors = LocalClawColors.current,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .background(if (c.isDark) Color(0xFF121212) else Color.White)
            .clickable { openFileAttachment(context, attachment) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
                .border(0.5.dp, c.border.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ClawSymbolIcon(
                symbol = mimeTypeSymbol(attachment.mimeType),
                tint = c.text,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                attachment.name,
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                attachment.mimeType.ifBlank { str(R.string.group_label_file) },
                color = c.subtext,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AttachmentMetaChip(
                    text = formatFileSize(attachment.sizeBytes),
                    dark = c.isDark,
                )
                AttachmentMetaChip(
                    text = str(R.string.perm_open),
                    dark = c.isDark,
                )
            }
        }
        ClawSymbolIcon("link", tint = c.subtext, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun ImageFileAttachmentCard(
    attachment: SkillAttachment.FileData,
    context: Context,
    maxThumbWidth: Dp,
    cornerRadiusDp: Dp = 16.dp,
    unavailableText: String = str(R.string.chat_image_unavailable),
) {
    val bitmap by produceState<Bitmap?>(null, attachment.path) {
        value = withContext(Dispatchers.IO) { decodeFileAttachmentBitmap(context, attachment, maxPx = 1200) }
    }
    var showFullscreen by remember { mutableStateOf(false) }

    MediaAttachmentCardFrame(
        maxWidthDp = maxThumbWidth,
        cornerRadiusDp = cornerRadiusDp,
        onClick = { if (bitmap != null) showFullscreen = true else openFileAttachment(context, attachment) },
    ) {
        val image = bitmap
        if (image != null) {
            val ratio = remember(image) {
                (image.width.toFloat() / image.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.8f)
            }
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = attachment.name,
                modifier = Modifier
                    .width(maxThumbWidth)
                    .aspectRatio(ratio),
                contentScale = ContentScale.Fit,
            )
            AttachmentMetaChip(
                text = formatFileSize(attachment.sizeBytes),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.18f)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClawSymbolIcon("image", tint = Color.White.copy(alpha = 0.62f), modifier = Modifier.size(18.dp))
                    Text(unavailableText, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (bitmap != null && showFullscreen) {
        FullscreenImageDialog(bitmap = bitmap!!, onDismiss = { showFullscreen = false })
    }
}
