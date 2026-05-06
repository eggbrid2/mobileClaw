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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ripple
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cardAlt)
                    .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(12.dp).then(padMod),
                verticalArrangement = Arrangement.spacedBy(gap.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.accent,
                        letterSpacing = 0.2.sp,
                    )
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
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
                lineHeight = (size * 1.5f).sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
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
                "outline" -> Box(
                    modifier = Modifier
                        .fillMaxWidth().then(padMod)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, c.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = c.accent),
                            onClick = handleClick,
                        )
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 14.sp, color = c.accent, fontWeight = FontWeight.Medium)
                }
                "text" -> Box(
                    modifier = Modifier
                        .fillMaxWidth().then(padMod)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = c.accent),
                            onClick = handleClick,
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 14.sp, color = c.accent)
                }
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth().then(padMod)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.85f), c.accent))
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White),
                            onClick = handleClick,
                        )
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        "input" -> {
            val key = node["key"]?.asString ?: return
            val placeholder = node["label"]?.asString ?: node["placeholder"]?.asString ?: ""
            val multiline = node["multiline"]?.asBoolean ?: false
            val value = inputState[key] ?: ""
            Column(modifier = Modifier.fillMaxWidth().then(padMod), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (placeholder.isNotBlank()) {
                    Text(placeholder, fontSize = 12.sp, color = c.subtext)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { inputState[key] = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = !multiline,
                    minLines = if (multiline) 2 else 1,
                    maxLines = if (multiline) 5 else 1,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp, lineHeight = 20.sp),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.cardAlt)
                                .border(1.dp, c.border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            if (value.isEmpty()) {
                                Text("输入内容…", fontSize = 14.sp, color = c.subtext.copy(alpha = 0.5f))
                            }
                            inner()
                        }
                    },
                )
            }
        }

        "select" -> {
            val key = node["key"]?.asString ?: return
            val options = node["options"]?.asJsonArray?.map { it.asString } ?: return
            var expanded by remember { mutableStateOf(false) }
            val selected = inputState.getOrPut(key) { options.firstOrNull() ?: "" }
            val label = node["label"]?.asString ?: node["placeholder"]?.asString

            Column(modifier = Modifier.fillMaxWidth().then(padMod), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!label.isNullOrBlank()) {
                    Text(label, fontSize = 12.sp, color = c.subtext)
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selected,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = c.text,
                            unfocusedTextColor = c.text,
                            focusedBorderColor = c.accent,
                            unfocusedBorderColor = c.border,
                            focusedContainerColor = c.cardAlt,
                            unfocusedContainerColor = c.cardAlt,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(c.card),
                    ) {
                        for (opt in options) {
                            DropdownMenuItem(
                                text = { Text(opt, color = c.text, fontSize = 14.sp) },
                                onClick = { inputState[key] = opt; expanded = false },
                            )
                        }
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
                        .clip(RoundedCornerShape(8.dp)).then(padMod),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(heightDp.dp)
                        .clip(RoundedCornerShape(8.dp)).background(c.cardAlt).then(padMod),
                    contentAlignment = Alignment.Center,
                ) { Text("🖼️ ${src.take(50)}", fontSize = 11.sp, color = c.subtext) }
            }
        }

        "divider" -> HorizontalDivider(
            modifier = Modifier.then(padMod),
            color = c.border,
            thickness = 0.5.dp,
        )

        "spacer" -> Spacer(Modifier.height((node["size"]?.asInt ?: 4).dp))

        "progress" -> {
            val value = (node["value"]?.asFloat ?: 0f).coerceIn(0f, 1f)
            val label = node["label"]?.asString
            Column(modifier = Modifier.fillMaxWidth().then(padMod), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (label != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 13.sp, color = c.text)
                        Text("${(value * 100).roundToInt()}%", fontSize = 13.sp, color = c.accent, fontWeight = FontWeight.Medium)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(c.border),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(value).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(c.accent.copy(0.8f), c.accent))),
                    )
                }
            }
        }

        "badge" -> {
            val text = node["text"]?.asString ?: return
            val bgColor = if (node["color"] != null) nodeColor(node, "color", c) else c.accent
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor.copy(alpha = 0.15f))
                    .border(0.5.dp, bgColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .then(padMod),
            ) { Text(text, fontSize = 12.sp, color = bgColor, fontWeight = FontWeight.Medium) }
        }

        "table" -> {
            val headers = node["headers"]?.asJsonArray?.map { it.asString } ?: emptyList()
            val rows = node["rows"]?.asJsonArray?.map { row ->
                row.asJsonArray.map { it.asString }
            } ?: emptyList()
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, c.border, RoundedCornerShape(8.dp))
                    .then(padMod),
            ) {
                if (headers.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().background(c.cardAlt)) {
                        for ((i, h) in headers.withIndex()) {
                            if (i > 0) Box(Modifier.width(0.5.dp).defaultMinSize(minHeight = 1.dp).background(c.border))
                            Text(
                                h,
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 7.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.subtext,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
                }
                for ((ri, row) in rows.withIndex()) {
                    if (ri > 0) HorizontalDivider(color = c.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (ri % 2 == 1) c.cardAlt.copy(alpha = 0.4f) else Color.Transparent),
                    ) {
                        for ((ci, cell) in row.withIndex()) {
                            if (ci > 0) Box(Modifier.width(0.5.dp).defaultMinSize(minHeight = 1.dp).background(c.border.copy(alpha = 0.5f)))
                            Text(
                                cell,
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 12.sp, color = c.text, lineHeight = 17.sp,
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

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val labelH = 22f
            val chartH = size.height - labelH
            val slotW = size.width / data.size
            val barW = slotW * 0.52f
            val textPaint = Paint().apply {
                color = android.graphics.Color.argb(140, 170, 170, 200)
                textSize = 9.dp.toPx()
                textAlign = Paint.Align.CENTER
            }
            val accentArgb = android.graphics.Color.argb(
                (c.accent.alpha * 255).toInt(),
                (c.accent.red * 255).toInt(),
                (c.accent.green * 255).toInt(),
                (c.accent.blue * 255).toInt(),
            )
            val accentDimArgb = android.graphics.Color.argb(
                (c.accent.alpha * 150).toInt(),
                (c.accent.red * 255).toInt(),
                (c.accent.green * 255).toInt(),
                (c.accent.blue * 255).toInt(),
            )
            for ((i, v) in data.withIndex()) {
                val x = slotW * i + (slotW - barW) / 2f
                val barH = (v / maxVal) * chartH
                // Gradient bar via drawIntoCanvas
                drawIntoCanvas { canvas ->
                    val barPaint = Paint().apply {
                        shader = android.graphics.LinearGradient(
                            x, chartH - barH, x, chartH,
                            accentArgb, accentDimArgb,
                            android.graphics.Shader.TileMode.CLAMP,
                        )
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        x, chartH - barH, x + barW, chartH, 4.dp.toPx(), 4.dp.toPx(), barPaint
                    )
                }
                val label = labels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: continue
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, x + barW / 2f, size.height - 3.dp.toPx(), textPaint)
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

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val labelH = 22f
            val chartH = size.height - labelH
            val step = if (data.size > 1) size.width / (data.size - 1) else size.width
            val textPaint = Paint().apply {
                color = android.graphics.Color.argb(140, 170, 170, 200)
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
            drawPath(fillPath, color = c.accent.copy(alpha = 0.1f))
            drawPath(linePath, color = c.accent, style = Stroke(width = 2.dp.toPx()))

            for ((i, v) in data.withIndex()) {
                val x = step * i
                val y = chartH - ((v - minVal) / range) * chartH
                drawCircle(c.cardAlt, radius = 4.5.dp.toPx(), center = Offset(x, y))
                drawCircle(c.accent, radius = 3.dp.toPx(), center = Offset(x, y))
                val label = labels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: continue
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, x, size.height - 3.dp.toPx(), textPaint)
                }
            }
        }
    }
}
