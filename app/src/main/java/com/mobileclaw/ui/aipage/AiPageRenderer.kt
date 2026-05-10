package com.mobileclaw.ui.aipage

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mobileclaw.ui.ClawColors
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.ui.MarkdownText

/** Renders an AI page's component tree, evaluating ${expr} templates from runtime state. */
@Composable
fun AiPageRenderer(layout: JsonObject, runtime: AiPageRuntime, modifier: Modifier = Modifier) {
    val c = LocalClawColors.current
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RenderNode(layout, runtime, c)
    }
}

// ── Color helper ──────────────────────────────────────────────────────────────

@Composable
private fun nodeColor(colorStr: String?, c: ClawColors): Color =
    when (colorStr) {
        "accent"  -> c.accent
        "subtext" -> c.subtext
        "red"     -> c.red
        "green"   -> c.green
        "blue"    -> c.blue
        else      -> colorStr?.takeIf { it.startsWith("#") }?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        } ?: c.text
    }

// ── Expression evaluation shorthand ──────────────────────────────────────────

private fun ev(raw: String?, runtime: AiPageRuntime): String {
    if (raw.isNullOrEmpty()) return ""
    return ExprEval.eval(raw, runtime.state.toMap(), runtime.inputState.toMap())
}

// ── Recursive node renderer ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderNode(node: JsonObject, runtime: AiPageRuntime, c: ClawColors) {
    val type = node["type"]?.asString ?: return
    val gap = node["gap"]?.asInt ?: 8
    val nodePad = node["padding"]?.asInt ?: 0
    val padMod = if (nodePad > 0) Modifier.padding(nodePad.dp) else Modifier

    @Composable
    fun children(arr: JsonArray?) {
        if (arr == null) return
        for (el in arr) runCatching { RenderNode(el.asJsonObject, runtime, c) }
    }

    when (type) {
        // ── Layout ───────────────────────────────────────────────────────────

        "column" -> Column(
            modifier = Modifier.fillMaxWidth().then(padMod),
            verticalArrangement = Arrangement.spacedBy(gap.dp),
        ) { children(node["children"]?.asJsonArray) }

        "row" -> Row(
            modifier = Modifier.fillMaxWidth().then(padMod),
            horizontalArrangement = Arrangement.spacedBy(gap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node["children"]?.asJsonArray?.forEach { el ->
                Box(Modifier.weight(1f)) { runCatching { RenderNode(el.asJsonObject, runtime, c) } }
            }
        }

        "card" -> {
            val title = ev(node["title"]?.asString, runtime)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cardAlt)
                    .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(12.dp).then(padMod),
                verticalArrangement = Arrangement.spacedBy(gap.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.subtext)
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
                }
                children(node["children"]?.asJsonArray)
            }
        }

        // ── Content ───────────────────────────────────────────────────────────

        "text" -> {
            val content = ev(node["content"]?.asString, runtime)
            val size = node["size"]?.asInt ?: 14
            val bold = node["bold"]?.asBoolean ?: false
            val italic = node["italic"]?.asBoolean ?: false
            val color = nodeColor(node["color"]?.asString, c)
            val align = when (node["align"]?.asString) {
                "center" -> TextAlign.Center
                "end"    -> TextAlign.End
                else     -> TextAlign.Start
            }
            Text(
                text = content,
                style = TextStyle(
                    fontSize = size.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    color = color,
                    textAlign = align,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        "markdown" -> {
            val content = ev(node["content"]?.asString, runtime)
            MarkdownText(text = content, color = c.text, fontSize = 14.sp, lineHeight = 21.sp)
        }

        "badge" -> {
            val badgeText = ev(node["text"]?.asString, runtime)
            val badgeColor = nodeColor(node["color"]?.asString, c).let {
                if (it == c.text) c.subtext else it
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(badgeText, fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.Medium)
            }
        }

        "divider" -> HorizontalDivider(color = c.border, thickness = 0.5.dp)

        "spacer" -> Spacer(Modifier.height((node["size"]?.asInt ?: 8).dp))

        "progress" -> {
            val rawVal = ev(node["value"]?.asString, runtime)
            val value = rawVal.toFloatOrNull() ?: node["value"]?.asFloat ?: 0f
            val label = ev(node["label"]?.asString, runtime)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (label.isNotBlank()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 12.sp, color = c.subtext)
                        Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = c.accent)
                    }
                }
                LinearProgressIndicator(
                    progress = { value.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = c.accent,
                    trackColor = c.border,
                )
            }
        }

        "image" -> {
            val src = ev(node["src"]?.asString, runtime)
            val height = (node["height"]?.asInt ?: 200).dp
            if (src.startsWith("data:image/")) {
                val b64 = src.substringAfter(",")
                val bm = runCatching {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
                if (bm != null) {
                    Image(
                        bitmap = bm.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        // ── Input & Actions ────────────────────────────────────────────────────

        "input" -> {
            val key = node["key"]?.asString ?: return
            val placeholder = ev(node["placeholder"]?.asString, runtime)
            val label = ev(node["label"]?.asString, runtime)
            val multiline = node["multiline"]?.asBoolean ?: false
            val current = runtime.inputState.getOrElse(key) { ev(node["default"]?.asString, runtime) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (label.isNotBlank()) Text(label, fontSize = 12.sp, color = c.subtext)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.cardAlt)
                        .border(0.5.dp, c.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = current,
                        onValueChange = { runtime.inputState[key] = it },
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        maxLines = if (multiline) 6 else 1,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (current.isEmpty()) Text(placeholder, color = c.subtext, fontSize = 14.sp)
                            inner()
                        },
                    )
                }
            }
        }

        "select" -> {
            val key = node["key"]?.asString ?: return
            val label = ev(node["label"]?.asString, runtime)
            val options = node["options"]?.asJsonArray?.map { it.asString } ?: emptyList()
            var expanded by remember { mutableStateOf(false) }
            val selected = runtime.inputState.getOrElse(key) { options.firstOrNull() ?: "" }
            if (!runtime.inputState.containsKey(key) && options.isNotEmpty()) {
                runtime.inputState[key] = options.first()
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (label.isNotBlank()) Text(label, fontSize = 12.sp, color = c.subtext)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    Box(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.cardAlt)
                            .border(0.5.dp, c.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(selected, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        }
                    }
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = c.card,
                    ) {
                        options.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, color = c.text, fontSize = 14.sp) },
                                onClick = { runtime.inputState[key] = opt; expanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }
        }

        "switch" -> {
            val key = node["key"]?.asString ?: return
            val label = ev(node["label"]?.asString, runtime)
            val current = runtime.state[key]?.equals("true", ignoreCase = true) ?: false
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
                Switch(
                    checked = current,
                    onCheckedChange = { runtime.state[key] = it.toString() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = c.accent),
                )
            }
        }

        "slider" -> {
            val key = node["key"]?.asString ?: return
            val label = ev(node["label"]?.asString, runtime)
            val min = node["min"]?.asFloat ?: 0f
            val max = node["max"]?.asFloat ?: 100f
            val current = runtime.state[key]?.toFloatOrNull() ?: min
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 12.sp, color = c.subtext)
                    Text(current.toInt().toString(), fontSize = 12.sp, color = c.accent)
                }
                Slider(
                    value = current,
                    onValueChange = { runtime.state[key] = it.toString() },
                    valueRange = min..max,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        "button" -> {
            val label = ev(node["label"]?.asString, runtime)
            val action = node["action"]?.asString ?: ""
            val style = node["style"]?.asString ?: "filled"
            if (label.isBlank()) return

            val (bg, textColor, hasBorder) = when (style) {
                "filled"  -> Triple(c.accent, Color.White, false)
                "outline" -> Triple(Color.Transparent, c.accent, true)
                "text"    -> Triple(Color.Transparent, c.accent, false)
                else      -> Triple(c.accent, Color.White, false)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .then(if (hasBorder) Modifier.border(1.dp, c.accent, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable(enabled = !runtime.isRunning) { runtime.handleAction(action) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        "button_group" -> {
            val buttons = node["buttons"]?.asJsonArray ?: return
            val groupStyle = node["style"]?.asString ?: "outline"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                for (el in buttons) {
                    val b = runCatching { el.asJsonObject }.getOrNull() ?: continue
                    val lbl = ev(b["label"]?.asString, runtime)
                    val action = b["action"]?.asString ?: ""
                    val style = b["style"]?.asString ?: groupStyle
                    val (bg, tc, hasBorder) = when (style) {
                        "filled" -> Triple(c.accent, Color.White, false)
                        "text"   -> Triple(Color.Transparent, c.accent, false)
                        else     -> Triple(Color.Transparent, c.accent, true)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .then(if (hasBorder) Modifier.border(1.dp, c.accent, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable(enabled = !runtime.isRunning) { runtime.handleAction(action) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(lbl, color = tc, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // ── Compound helpers ──────────────────────────────────────────────────

        "metric_grid" -> {
            val items = node["items"]?.asJsonArray ?: return
            val cols = node["cols"]?.asInt ?: 2
            val metricList = (0 until items.size()).mapNotNull { runCatching { items[it].asJsonObject }.getOrNull() }
            metricList.chunked(cols).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    rowItems.forEach { item ->
                        val ic = nodeColor(item["color"]?.asString, c).let { if (it == c.text) c.accent else it }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.cardAlt)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(ev(item["value"]?.asString, runtime), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ic)
                            Text(ev(item["label"]?.asString, runtime), fontSize = 11.sp, color = c.subtext, textAlign = TextAlign.Center)
                        }
                    }
                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        "info_rows" -> {
            val items = node["items"]?.asJsonArray ?: return
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.cardAlt)) {
                val size = items.size()
                items.forEachIndexed { i, el ->
                    val item = runCatching { el.asJsonObject }.getOrNull() ?: return@forEachIndexed
                    val ic = nodeColor(item["color"]?.asString, c)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ev(item["label"]?.asString, runtime), fontSize = 13.sp, color = c.subtext)
                        Text(ev(item["value"]?.asString, runtime), fontSize = 13.sp, color = ic, fontWeight = FontWeight.Medium)
                    }
                    if (i < size - 1) HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }

        "table" -> {
            val headers = node["headers"]?.asJsonArray?.map { it.asString } ?: emptyList()
            val rows = node["rows"]?.asJsonArray ?: return
            val colW = 120.dp
            Column(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.cardAlt),
            ) {
                if (headers.isNotEmpty()) {
                    Row(Modifier.background(c.accent.copy(alpha = 0.12f))) {
                        headers.forEach { h ->
                            Text(ev(h, runtime), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                                modifier = Modifier.width(colW).padding(8.dp))
                        }
                    }
                }
                rows.forEachIndexed { i, row ->
                    val cells = runCatching { row.asJsonArray }.getOrNull() ?: return@forEachIndexed
                    Row(Modifier.background(if (i % 2 == 0) Color.Transparent else c.border.copy(alpha = 0.05f))) {
                        for (cell in cells) {
                            Text(ev(cell.asString, runtime), fontSize = 12.sp, color = c.text,
                                modifier = Modifier.width(colW).padding(8.dp))
                        }
                    }
                }
            }
        }

        "json_view" -> {
            val content = ev(node["content"]?.asString, runtime)
            val maxChars = node["max_chars"]?.asInt ?: 6000
            val pretty = remember(content) {
                runCatching {
                    com.google.gson.GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(com.google.gson.JsonParser.parseString(content))
                }.getOrDefault(content)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.cardAlt)
                    .border(0.5.dp, c.border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Text(
                    pretty.take(maxChars),
                    color = c.text,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }

        "conditional" -> {
            val condRaw = ev(node["condition"]?.asString, runtime)
            val isTrue = condRaw.equals("true", ignoreCase = true) || condRaw == "1" || (condRaw.isNotBlank() && condRaw != "false" && condRaw != "0")
            val branch = if (isTrue) node["children"]?.asJsonArray else node["else_children"]?.asJsonArray
            if (branch != null) children(branch)
        }

        "chart_bar"  -> BarChart(node, runtime, c)
        "chart_line" -> LineChart(node, runtime, c)
    }
}

// ── Charts ────────────────────────────────────────────────────────────────────

@Composable
private fun BarChart(node: JsonObject, runtime: AiPageRuntime, c: ClawColors) {
    val rawData = node["data"]?.asJsonArray?.map { it.asFloat } ?: return
    val labels = node["labels"]?.asJsonArray?.map { it.asString } ?: emptyList()
    val title = ev(node["title"]?.asString, runtime)
    val max = rawData.maxOrNull()?.takeIf { it > 0f } ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotBlank()) Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val n = rawData.size
            if (n == 0) return@Canvas
            val barW = (size.width / (n * 1.5f)).coerceAtMost(size.width / n * 0.7f)
            val spacing = (size.width - barW * n) / (n + 1)
            rawData.forEachIndexed { i, v ->
                val barH = (v / max) * size.height * 0.85f
                val x = spacing + i * (barW + spacing)
                drawRect(c.accent, topLeft = Offset(x, size.height - barH), size = Size(barW, barH))
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                labels.take(8).forEach { l ->
                    Text(l, fontSize = 9.sp, color = c.subtext, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun LineChart(node: JsonObject, runtime: AiPageRuntime, c: ClawColors) {
    val rawData = node["data"]?.asJsonArray?.map { it.asFloat } ?: return
    val labels = node["labels"]?.asJsonArray?.map { it.asString } ?: emptyList()
    val title = ev(node["title"]?.asString, runtime)
    val min = rawData.minOrNull() ?: 0f
    val max = rawData.maxOrNull() ?: 1f
    val range = (max - min).takeIf { it > 0f } ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotBlank()) Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val n = rawData.size
            if (n < 2) return@Canvas
            val stepX = size.width / (n - 1).toFloat()
            val path = Path()
            rawData.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height * 0.9f - ((v - min) / range) * size.height * 0.85f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, c.accent, style = Stroke(width = 2.dp.toPx()))
            rawData.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height * 0.9f - ((v - min) / range) * size.height * 0.85f
                drawCircle(c.accent, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.take(6).forEach { l -> Text(l, fontSize = 9.sp, color = c.subtext) }
            }
        }
    }
}
