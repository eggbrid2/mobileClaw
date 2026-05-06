package com.mobileclaw.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTransformGestures
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen image crop dialog that produces a square JPEG in cache dir. */
@Composable
fun CropImageDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (filePath: String) -> Unit,
) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = imageUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                CropContent(
                    bitmap = bmp,
                    onConfirm = { left, top, sizePx ->
                        val cropped = Bitmap.createBitmap(bmp, left, top, sizePx, sizePx)
                        val output = if (cropped.width > 512) Bitmap.createScaledBitmap(cropped, 512, 512, true) else cropped
                        val file = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                        file.outputStream().buffered().use { output.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        if (output !== cropped) cropped.recycle()
                        onCropped("file://${file.absolutePath}")
                    },
                    onCancel = onDismiss,
                )
            }
        }
    }
}

@Composable
fun CropContent(
    bitmap: Bitmap,
    onConfirm: (left: Int, top: Int, sizePx: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalClawColors.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWPx = constraints.maxWidth.toFloat()
        val containerHPx = constraints.maxHeight.toFloat()
        val buttonBarPx = with(density) { 80.dp.toPx() }

        val imageAreaHPx = containerHPx - buttonBarPx
        val cropSizePx = min(containerWPx, imageAreaHPx) * 0.90f

        // Minimum scale: image just covers the crop square
        val minScale = max(cropSizePx / bitmap.width, cropSizePx / bitmap.height)

        // userZoom is the extra zoom on top of minScale (1f = no extra zoom)
        var userZoom by remember(bitmap) { mutableFloatStateOf(1f) }
        var offsetX by remember(bitmap) { mutableFloatStateOf(0f) }
        var offsetY by remember(bitmap) { mutableFloatStateOf(0f) }

        // Derive current display size
        val scale = minScale * userZoom
        val dispWPx = bitmap.width * scale
        val dispHPx = bitmap.height * scale
        val maxDragX = max(0f, (dispWPx - cropSizePx) / 2f)
        val maxDragY = max(0f, (dispHPx - cropSizePx) / 2f)

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { imageAreaHPx.toDp() })
                    .clipToBounds()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Clamp zoom: min keeps image covering crop, max 4× extra
                            userZoom = (userZoom * zoom).coerceIn(1f, 4f)

                            val s = minScale * userZoom
                            val dw = bitmap.width * s
                            val dh = bitmap.height * s
                            val mxDrag = max(0f, (dw - cropSizePx) / 2f)
                            val myDrag = max(0f, (dh - cropSizePx) / 2f)
                            offsetX = (offsetX + pan.x).coerceIn(-mxDrag, mxDrag)
                            offsetY = (offsetY + pan.y).coerceIn(-myDrag, myDrag)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(with(density) { dispWPx.toDp() }, with(density) { dispHPx.toDp() })
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
                    contentScale = ContentScale.FillBounds,
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sqLeft = (size.width - cropSizePx) / 2f
                    val sqTop = (size.height - cropSizePx) / 2f
                    val scrim = Color.Black.copy(alpha = 0.52f)

                    drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, sqTop))
                    drawRect(scrim, topLeft = Offset(0f, sqTop + cropSizePx), size = Size(size.width, size.height - sqTop - cropSizePx))
                    drawRect(scrim, topLeft = Offset(0f, sqTop), size = Size(sqLeft, cropSizePx))
                    drawRect(scrim, topLeft = Offset(sqLeft + cropSizePx, sqTop), size = Size(size.width - sqLeft - cropSizePx, cropSizePx))

                    drawRect(Color.White, topLeft = Offset(sqLeft, sqTop), size = Size(cropSizePx, cropSizePx), style = Stroke(width = 2.dp.toPx()))

                    val third = cropSizePx / 3f
                    val gridAlpha = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        drawLine(gridAlpha, Offset(sqLeft + third * i, sqTop), Offset(sqLeft + third * i, sqTop + cropSizePx), strokeWidth = 0.8f)
                        drawLine(gridAlpha, Offset(sqLeft, sqTop + third * i), Offset(sqLeft + cropSizePx, sqTop + third * i), strokeWidth = 0.8f)
                    }

                    val handle = 18.dp.toPx()
                    listOf(
                        Offset(sqLeft, sqTop) to Pair(Offset(sqLeft + handle, sqTop), Offset(sqLeft, sqTop + handle)),
                        Offset(sqLeft + cropSizePx, sqTop) to Pair(Offset(sqLeft + cropSizePx - handle, sqTop), Offset(sqLeft + cropSizePx, sqTop + handle)),
                        Offset(sqLeft, sqTop + cropSizePx) to Pair(Offset(sqLeft + handle, sqTop + cropSizePx), Offset(sqLeft, sqTop + cropSizePx - handle)),
                        Offset(sqLeft + cropSizePx, sqTop + cropSizePx) to Pair(Offset(sqLeft + cropSizePx - handle, sqTop + cropSizePx), Offset(sqLeft + cropSizePx, sqTop + cropSizePx - handle)),
                    ).forEach { (corner, ends) ->
                        drawLine(Color.White, corner, ends.first, strokeWidth = 2.5f)
                        drawLine(Color.White, corner, ends.second, strokeWidth = 2.5f)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { buttonBarPx.toDp() })
                    .padding(horizontal = 28.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text("取消", color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
                }
                Button(
                    onClick = {
                        val s = minScale * userZoom
                        val cropBitmapSize = (cropSizePx / s).roundToInt().coerceAtLeast(1)
                        val bmpCx = bitmap.width / 2 - (offsetX / s).roundToInt()
                        val bmpCy = bitmap.height / 2 - (offsetY / s).roundToInt()
                        val left = (bmpCx - cropBitmapSize / 2).coerceIn(0, (bitmap.width - cropBitmapSize).coerceAtLeast(0))
                        val top = (bmpCy - cropBitmapSize / 2).coerceIn(0, (bitmap.height - cropBitmapSize).coerceAtLeast(0))
                        onConfirm(left, top, cropBitmapSize)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                ) {
                    Text("使用此裁剪", fontSize = 15.sp)
                }
            }
        }
    }
}
