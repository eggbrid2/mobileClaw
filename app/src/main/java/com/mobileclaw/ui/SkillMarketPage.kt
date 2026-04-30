package com.mobileclaw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.skill.HttpSkillConfig
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillMarket
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class RemoteSkillEntry(
    val id: String,
    val name: String,
    val nameZh: String?,
    val description: String,
    val descriptionZh: String?,
    val tags: List<String>,
    val stars: Int,
    val platform: String,
    val def: SkillDefinition,
)

@Composable
fun SkillMarketPage(
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("⭐ 推荐", "🌐 ClawHub", "📦 SkillsMP")

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface),
    ) {
        // Top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = c.text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "技能市场",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                )
            }
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = c.surface,
                contentColor = c.accent,
                edgePadding = 8.dp,
                divider = {},
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == idx) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == idx) c.accent else c.subtext,
                            )
                        },
                    )
                }
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        when (selectedTab) {
            0 -> RecommendedTab(installedIds = installedIds, onInstall = onInstall)
            1 -> RemoteSearchTab(
                platform = "ClawHub",
                apiBase = "https://clawhub.ai/api/v1",
                installedIds = installedIds,
                onInstall = onInstall,
            )
            2 -> RemoteSearchTab(
                platform = "SkillsMP",
                apiBase = "https://skillsmp.com/api",
                installedIds = installedIds,
                onInstall = onInstall,
            )
        }
    }
}

@Composable
private fun RecommendedTab(
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
) {
    val c = LocalClawColors.current
    val grouped = remember { SkillMarket.catalog.groupBy { it.category } }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (category, entries) ->
            item {
                Text(
                    text = category,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.subtext,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(entries, key = { it.def.meta.id }) { entry ->
                MarketSkillRow(
                    emoji = entry.emoji,
                    name = entry.def.meta.nameZh ?: entry.def.meta.name,
                    description = entry.def.meta.descriptionZh ?: entry.def.meta.description,
                    tags = entry.def.meta.tags,
                    stars = null,
                    installed = entry.def.meta.id in installedIds,
                    onInstall = { onInstall(entry.def) },
                )
            }
            item { HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun RemoteSearchTab(
    platform: String,
    apiBase: String,
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
) {
    val c = LocalClawColors.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<RemoteSkillEntry>() }
    var searched by remember { mutableStateOf(false) }

    fun doSearch() {
        if (query.isBlank()) return
        loading = true
        error = null
        results.clear()
        searched = true
        focusManager.clearFocus()
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                searchRemotePlatform(platform, apiBase, query.trim())
            }
            loading = false
            when {
                found == null -> error = "无法连接到 $platform，请检查网络"
                found.isEmpty() -> error = "没有找到相关技能"
                else -> results.addAll(found)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.card)
                .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = c.subtext,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 14.sp, color = c.text),
                cursorBrush = SolidColor(c.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜索 $platform 技能...", fontSize = 14.sp, color = c.subtext)
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.accent)
                        .clickable { doSearch() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("搜索", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when {
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(36.dp))
            }
            error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        fontSize = 14.sp,
                        color = c.subtext,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "可通过「创建技能」手动配置",
                        fontSize = 12.sp,
                        color = c.subtext.copy(alpha = 0.6f),
                    )
                }
            }
            results.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { entry ->
                    MarketSkillRow(
                        emoji = "🔌",
                        name = entry.nameZh ?: entry.name,
                        description = entry.descriptionZh ?: entry.description,
                        tags = entry.tags,
                        stars = entry.stars,
                        installed = entry.id in installedIds,
                        onInstall = { onInstall(entry.def) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            !searched -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "搜索 $platform 上的技能",
                        fontSize = 14.sp,
                        color = c.subtext,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持按名称、功能关键词搜索",
                        fontSize = 12.sp,
                        color = c.subtext.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketSkillRow(
    emoji: String,
    name: String,
    description: String,
    tags: List<String>,
    stars: Int?,
    installed: Boolean,
    onInstall: () -> Unit,
) {
    val c = LocalClawColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.cardAlt)
                .border(0.5.dp, c.border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 22.sp)
        }

        Spacer(Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = c.subtext,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (tags.isNotEmpty() || stars != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(tag, fontSize = 10.sp, color = c.accent)
                        }
                    }
                    if (stars != null) {
                        Spacer(Modifier.width(4.dp))
                        Text("⭐ $stars", fontSize = 10.sp, color = c.subtext)
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Action button
        if (installed) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("已安装", fontSize = 11.sp, color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent)
                    .clickable { onInstall() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("安装", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = c.border.copy(alpha = 0.4f),
        thickness = 0.5.dp,
    )
}

private fun searchRemotePlatform(
    platform: String,
    apiBase: String,
    query: String,
): List<RemoteSkillEntry>? {
    return try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = when (platform) {
            "ClawHub"  -> "$apiBase/search?q=$encodedQuery&limit=20"
            "SkillsMP" -> "$apiBase/search?q=$encodedQuery&page_size=20"
            else       -> "$apiBase/search?q=$encodedQuery"
        }
        val conn = URL(searchUrl).openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 8000
            readTimeout    = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MobileClaw/1.0 Android")
        }
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        parseRemoteResults(platform, body)
    } catch (e: Exception) {
        null
    }
}

private fun parseRemoteResults(platform: String, json: String): List<RemoteSkillEntry> {
    val root = JSONObject(json)
    // Try common response wrapper formats
    val arr: JSONArray = when {
        root.has("items")   -> root.getJSONArray("items")
        root.has("results") -> root.getJSONArray("results")
        root.has("data")    -> root.getJSONArray("data")
        root.has("skills")  -> root.getJSONArray("skills")
        else                -> return emptyList()
    }
    val entries = mutableListOf<RemoteSkillEntry>()
    for (i in 0 until arr.length()) {
        val item = arr.getJSONObject(i)
        val id   = item.optString("id").ifBlank { item.optString("slug") }
        if (id.isBlank()) continue
        val name = item.optString("name").ifBlank { item.optString("title") }.ifBlank { id }
        val desc = item.optString("description").ifBlank { item.optString("desc", "") }
        val stars = item.optInt("stars", item.optInt("downloads", 0))
        val tagsArr = item.optJSONArray("tags")
        val tags = if (tagsArr != null) {
            (0 until tagsArr.length()).map { tagsArr.getString(it) }
        } else {
            val tagStr = item.optString("tags", "")
            if (tagStr.isNotBlank()) tagStr.split(",").map { it.trim() } else emptyList()
        }
        // Build a SkillDefinition: try HTTP first, fall back to Python echo
        val httpUrl = item.optString("endpoint", item.optString("url", ""))
        val scriptBody = item.optString("script", "")
        val def = buildRemoteDef(id, name, desc, tags, platform, httpUrl, scriptBody)
        entries += RemoteSkillEntry(
            id = id,
            name = name,
            nameZh = item.optString("name_zh").ifBlank { null },
            description = desc,
            descriptionZh = item.optString("description_zh").ifBlank { null },
            tags = tags,
            stars = stars,
            platform = platform,
            def = def,
        )
    }
    return entries
}

private fun buildRemoteDef(
    id: String,
    name: String,
    description: String,
    tags: List<String>,
    platform: String,
    httpUrl: String,
    scriptBody: String,
): SkillDefinition {
    val safeId = id.replace(Regex("[^a-z0-9_]"), "_").lowercase().take(40)
    val meta = SkillMeta(
        id = safeId,
        name = name,
        description = description,
        tags = tags + listOf(platform),
        type = if (httpUrl.isNotBlank()) SkillType.HTTP else SkillType.PYTHON,
        injectionLevel = 2,
        isBuiltin = false,
    )
    return if (httpUrl.isNotBlank()) {
        SkillDefinition(meta = meta, httpConfig = HttpSkillConfig(url = httpUrl))
    } else {
        val script = scriptBody.ifBlank {
            "# $name\n# Sourced from $platform\nprint(\"\"\"$description\"\"\")"
        }
        SkillDefinition(meta = meta, script = script)
    }
}
