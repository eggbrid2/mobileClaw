package com.mobileclaw.ui.common

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Chat and group chat share the same in-app video preview path so playback, saving, and fallback behavior stay aligned.
@Composable
fun VideoAttachmentCard(
    attachment: SkillAttachment.FileData,
    maxWidthDp: androidx.compose.ui.unit.Dp,
    cornerRadiusDp: androidx.compose.ui.unit.Dp = 16.dp,
    onOpenExternally: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val thumbnail by produceState<android.graphics.Bitmap?>(null, attachment.path) {
        value = withContext(Dispatchers.IO) { decodeVideoAttachmentFrame(context, attachment, maxPx = 1200) }
    }
    var showPlayer by remember { mutableStateOf(false) }
    val ratio = remember(thumbnail) {
        thumbnail?.let {
            (it.width.toFloat() / it.height.coerceAtLeast(1).toFloat()).coerceIn(0.8f, 1.78f)
        } ?: 16f / 9f
    }

    MediaAttachmentCardFrame(
        maxWidthDp = maxWidthDp,
        cornerRadiusDp = cornerRadiusDp,
        onClick = { showPlayer = true },
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = attachment.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = str(R.string.chat_video_unavailable),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.52f))
                .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.chat_video_play),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        AttachmentMetaChip(
            text = formatFileSize(attachment.sizeBytes),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        )
    }

    if (showPlayer) {
        FullscreenVideoDialog(
            attachment = attachment,
            onDismiss = { showPlayer = false },
            onOpenExternally = onOpenExternally,
        )
    }
}

@Composable
fun FullscreenVideoDialog(
    attachment: SkillAttachment.FileData,
    onDismiss: () -> Unit,
    onOpenExternally: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uri = remember(attachment.path) { resolveFileAttachmentUri(context, attachment) }
    val player = remember(uri) {
        uri?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center,
        ) {
            if (player != null) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            this.player = player
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                )
            } else {
                Text(
                    text = str(R.string.chat_video_unavailable),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PreviewDialogButton(str(R.string.chat_save_video)) { saveVideoAttachmentToGallery(context, attachment) }
                if (onOpenExternally != null) {
                    PreviewDialogButton(str(R.string.chat_open_external)) { onOpenExternally() }
                }
                PreviewDialogButton(str(R.string.btn_close), onDismiss)
            }
        }
    }
}

private fun saveVideoAttachmentToGallery(context: Context, attachment: SkillAttachment.FileData) {
    val uri = resolveFileAttachmentUri(context, attachment) ?: return
    val extension = attachment.name.substringAfterLast('.', "mp4")
    val filename = "MobileClaw_${System.currentTimeMillis()}.$extension"
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, attachment.mimeType.ifBlank { "video/mp4" })
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MobileClaw")
            }
            val destination = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("destination_uri_null")
            context.contentResolver.openInputStream(uri)?.use { input ->
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    input.copyTo(output)
                }
            } ?: error("source_stream_null")
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val file = java.io.File(dir, filename)
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(attachment.mimeType), null)
        }
        Toast.makeText(context, str(R.string.chat_video_saved), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, str(R.string.save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
    }
}
