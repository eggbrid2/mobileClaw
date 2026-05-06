package com.mobileclaw.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.max
import kotlin.math.roundToInt

/** Renders a JSON UI DSL block embedded in chat markdown (```ui fences). */
@Composable
fun DynamicUiRenderer(json: String, onAction: (String) -> Unit) {
    val root = remember(json) {
        runCatching { JsonParser.parseString(json.trim()).asJsonObject }.getOrNull()
    } ?: return

    val inputState = remember(json) { mutableStateMapOf<String, String>() }
    val c = LocalClawColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        RenderNode(root, onAction, inputState)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun resolveAction(action: String, inputState: Map<String, String>): String =
    action.replace(Regex("\\{(\\w+)\\}")) { inputState[it.groupValues[1]] ?: "" }

@Composable
private fun nodeColor(node: JsonObject, key: String, c: ClawColors): Color =
    when (node[key]?.asString) {
        "accent"  -> c.accent
        "subtext" -> c.subtext
        "red"     -> c.red
        "green"   -> c.green
        "blue"    -> c.blue
        else      -> node[key]?.asString?.takeIf { it.startsWith("#") }?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        } ?: c.text
    }

// ── Recursive node renderer ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderNode(
    node: JsonObject,
    onAction: (String) -> Unit,
    inputState: SnapshotStateMap<String, String>,
) {
    val c = LocalClawColors.current
    val context = LocalContext.current
    val type = node["type"]?.asString ?: return
    val gap = node["gap"]?.asInt ?: 8
    val nodePad = node["padding"]?.asInt ?: 0
    val padMod = if (nodePad > 0) Modifier.padding(nodePad.dp) else Modifier

    @Composable
    fun children(arr: JsonArray?) {
        if (arr == null) return
        for (el in arr) {
            runCatching { RenderNode(el.asJsonObject, onAction, inputState) }
        }
    }

    when (type) {
        "column" -> Column(
            modifier = Modifier.fillMaxWidth().then(padMod),
            verticalArrangement = Arrangement.spacedBy(gap.dp),
        ) { children(node["children"]?.asJsonArray) }

        "row" -> Row(
            modifier = Modifier.fillMaxWidth().then(padMod),
            horizontalArrangement = Arrangement.spacedBy(gap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { children(node["children"]?.asJsonArray) }

        "card" -> {
            val title = node["title"]?.asString
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.cardAlt)
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
                    .padding(10.dp).then(padMod),
                verticalArrangement = Arrangement.spacedBy(gap.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.subtext)
                }
                children(node["children"]?.asJsonArray)
            }
        }

        "text" -> {
            val content = node["content"]?.asString ?: return
            val size = node["size"]?.asFloat ?: 14f
            val bold = node["bold"]?.asBoolean ?: false
            val italic = node["italic"]?.asBoolean ?: false
            val textColor = if (node["color"] != null) nodeColor(node, "color", c) else c.text
            val align = when (node["align"]?.asString) {
                "center" -> TextAlign.Center
                "end", "right" -> TextAlign.End
                else -> TextAlign.Start
            }
            Text(
                text = content,
                fontSize = size.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                color = textColor,
                textAlign = align,
                modifier = Modifier.fillMaxWidth().then(padMod),
            )
        }

        "button" -> {
            val label = node["label"]?.asString ?: return
            val action = node["action"]?.asString ?: return
            val style = node["style"]?.asString ?: "filled"
            val handleClick = {
                val payload = resolveAction(action, inputState)
                when {
                    action.startsWith("copy:") -> {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("text", payload.removePrefix("copy:")))
                    }
                    else -> onAction(payload.removePrefix("send:").removePrefix("submit:"))
                }
            }
            when (style) {
                "outline" -> OutlinedButton(onClick = handleClick, modifier = padMod) { Text(label) }
                "text"    -> TextButton(onClick = handleClick, modifier = padMod) { Text(label, color = c.accent) }
                else      -> Button(
                    onClick = handleClick,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    modifier = padMod,
                ) { Text(label) }
            }
        }

        "input" -> {
            val key = node["key"]?.asString ?: return
            val placeholder = node["label"]?.asString ?: node["placeholder"]?.asString ?: ""
            val multiline = node["multiline"]?.asBoolean ?: false
            val value = inputState[key] ?: ""
            OutlinedTextField(
                value = value,
                onValueChange = { inputState[key] = it },
                label = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth().then(padMod),
                singleLine = !multiline,
                minLines = if (multiline) 2 else 1,
                maxLines = if (multiline) 4 else 1,
            )
        }

        "select" -> {
            val key = node["key"]?.asString ?: return
            val options = node["options"]?.asJsonArray?.map { it.asString } ?: return
            var expanded by remember { mutableStateOf(false) }
            val selected = inputState.getOrPut(key) { options.firstOrNull() ?: "" }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).then(padMod),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    for (opt in options) {
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = { inputState[key] = opt; expanded = false },
                        )
                    }
                }
            }
        }

        "image" -> {
            val src = node["src"]?.asString ?: return
            val heightDp = node["height"]?.asInt ?: 200
            val bmp = remember(src) {
                runCatching {
                    if (src.startsWith("data:")) {
                        val b64 = src.substringAfter(",")
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                }.getOrNull()
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(heightDp.dp)
                        .clip(RoundedCornerShape(6.dp)).then(padMod),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(heightDp.dp)
                        .clip(RoundedCornerShape(6.dp)).background(c.cardAlt).then(padMod),
                    contentAlignment = Alignment.Center,
                ) { Text(src.take(60), fontSize = 10.sp, color = c.subtext) }
            }
        }

        "divider" -> Box(
            modifier = Modifier.fillMaxWidth().height(0.5.dp).background(c.border).then(padMod),
        )

        "spacer" -> Spacer(Modifier.height((node["size"]?.asInt ?: 8).dp))

        "progress" -> {
            val value = (node["value"]?.asFloat ?: 0f).coerceIn(0f, 1f)
            val label = node["label"]?.asString
            Column(modifier = Modifier.fillMaxWidth().then(padMod), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (label != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 12.sp, color = c.subtext)
                        Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, color = c.accent)
                    }
                }
                LinearProgressIndicator(
                    progress = { value },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.accent,
                    trackColor = c.border,
                )
            }
        }

        "badge" -> {
            val text = node["text"]?.asString ?: return
            val bgColor = if (node["color"] != null) nodeColor(node, "color", c) else c.accent
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor.copy(alpha = 0.15f))
                    .border(1.dp, bgColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(padMod),
            ) { Text(text, fontSize = 11.sp, color = bgColor, fontWeight = FontWeight.Medium) }
        }

        "table" -> {
            val headers = node["headers"]?.asJsonArray?.map { it.asString } ?: emptyList()
            val rows = node["rows"]?.asJsonArray?.map { row ->
                row.asJsonArray.map { it.asString }
            } ?: emptyList()
            Column(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, c.border, RoundedCornerShape(6.dp))
                    .then(padMod),
            ) {
                if (headers.isNotEmpty()) {
                    Row(modifier = Modifier.background(c.cardAlt).height(IntrinsicSize.Min)) {
                        for ((i, h) in headers.withIndex()) {
                            if (i > 0) Box(Modifier.width(1.dp).background(c.border))
                            Text(
                                h,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).width(100.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.subtext,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                }
                for ((ri, row) in rows.withIndex()) {
                    if (ri > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.border))
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        for ((ci, cell) in row.withIndex()) {
                            if (ci > 0) Box(Modifier.width(0.5.dp).background(c.border))
                            Text(
                                cell,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).width(100.dp),
                                fontSize = 12.sp, color = c.text,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        "chart_bar" -> ChartBar(node, c)
        "chart_line" -> ChartLine(node, c)
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

@Composable
private fun ChartBar(node: JsonObject, c: ClawColors) {
    val data = node["data"]?.asJsonArray?.map { it.asFloat } ?: return
    val labels = node["labels"]?.asJsonArray?.map { it.asString } ?: List(data.size) { "" }
    val title = node["title"]?.asString
    val maxVal = data.maxOrNull()?.takeIf { it > 0f } ?: 1f

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.subtext)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val labelHeightPx = 20f
            val chartH = size.height - labelHeightPx
            val slotW = size.width / data.size
            val barW = slotW * 0.55f
            val textPaint = Paint().apply {
                color = android.graphics.Color.argb(160, 128, 128, 128)
                textSize = 9.dp.toPx()
                textAlign = Paint.Align.CENTER
            }

            for ((i, v) in data.withIndex()) {
                val x = slotW * i + slotW / 2f - barW / 2f
                val barH = (v / maxVal) * chartH
                drawRect(
                    color = c.accent.copy(alpha = 0.8f),
                    topLeft = Offset(x, chartH - barH),
                    size = Size(barW, barH),
                )
                val label = labels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: continue
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, x + barW / 2f, size.height, textPaint)
                }
            }
        }
    }
}

// ── Line chart ────────────────────────────────────────────────────────────────

@Composable
private fun ChartLine(node: JsonObject, c: ClawColors) {
    val data = node["data"]?.asJsonArray?.map { it.asFloat } ?: return
    val labels = node["labels"]?.asJsonArray?.map { it.asString } ?: List(data.size) { "" }
    val title = node["title"]?.asString
    val maxVal = data.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val minVal = data.minOrNull() ?: 0f
    val range = max(maxVal - minVal, 0.001f)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.subtext)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val labelHeightPx = 20f
            val chartH = size.height - labelHeightPx
            val step = if (data.size > 1) size.width / (data.size - 1) else size.width
            val textPaint = Paint().apply {
                color = android.graphics.Color.argb(160, 128, 128, 128)
                textSize = 9.dp.toPx()
                textAlign = Paint.Align.CENTER
            }

            val linePath = Path()
            for ((i, v) in data.withIndex()) {
                val x = step * i
                val y = chartH - ((v - minVal) / range) * chartH
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(step * (data.size - 1), chartH)
                lineTo(0f, chartH)
                close()
            }
            drawPath(fillPath, color = c.accent.copy(alpha = 0.12f))
            drawPath(linePath, color = c.accent, style = Stroke(width = 2.dp.toPx()))

            for ((i, v) in data.withIndex()) {
                val x = step * i
                val y = chartH - ((v - minVal) / range) * chartH
                drawCircle(c.accent, radius = 3.dp.toPx(), center = Offset(x, y))
                val label = labels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: continue
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, x, size.height, textPaint)
                }
            }
        }
    }
}
