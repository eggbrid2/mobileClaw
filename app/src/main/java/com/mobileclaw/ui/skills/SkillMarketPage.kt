package com.mobileclaw.ui.skills

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.mcp.McpEndpointConfig
import com.mobileclaw.mcp.McpHttpClient
import com.mobileclaw.skill.HttpSkillConfig
import com.mobileclaw.skill.McpSkillConfig
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillMarket
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillToolTaxonomy
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.mobileclaw.R
import com.mobileclaw.str

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
    showHeader: Boolean = true,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(str(R.string.skill_market_228a7d), "接入 MCP")

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(skillMarketWorkbenchBrush()),
    ) {
        // Top bar
        if (showHeader) Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.58f))
                        .border(0.7.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(20.dp)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = SkillMarketInk,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = str(R.string.skill_market_5917e2),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = SkillMarketInk,
                )
            }
            CompactScrollableTabs(
                items = tabs.mapIndexed { idx, label ->
                    CompactTabItem(label = label, selected = selectedTab == idx) { selectedTab = idx }
                },
                modifier = Modifier.background(Color.Transparent),
            )
        } else {
            CompactScrollableTabs(
                items = tabs.mapIndexed { idx, label ->
                    CompactTabItem(label = label, selected = selectedTab == idx) { selectedTab = idx }
                },
                modifier = Modifier.background(Color.Transparent),
            )
        }

        when (selectedTab) {
            0 -> RecommendedTab(installedIds = installedIds, onInstall = onInstall)
            1 -> PublicMcpTab(installedIds = installedIds, onInstall = onInstall)
        }
    }
}

@Composable
private fun RecommendedTab(
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
) {
    val grouped = remember { SkillMarket.catalog.groupBy { it.category } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel(text = "推荐")
        }
        grouped.forEach { (category, entries) ->
            item {
                Text(
                    text = category,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SkillMarketMuted,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
            items(entries, key = { it.def.meta.id }) { entry ->
                MarketSkillRow(
                    source = category,
                    name = entry.def.meta.nameZh ?: entry.def.meta.name,
                    description = entry.def.meta.descriptionZh ?: entry.def.meta.description,
                    tags = entry.def.meta.tags,
                    stars = null,
                    installed = entry.def.meta.id in installedIds,
                    onInstall = { onInstall(entry.def) },
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PublicMcpTab(
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var endpointInput by remember { mutableStateOf("") }
    var headersInput by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<RemoteSkillEntry>() }

    fun discoverInput(inputOverride: String? = null, headersOverride: String? = null) {
        val input = (inputOverride ?: endpointInput).trim()
        val headers = (headersOverride ?: headersInput).trim()
        if (inputOverride != null) endpointInput = inputOverride
        if (headersOverride != null) headersInput = headersOverride
        if (input.isBlank()) return
        loading = true
        error = null
        results.clear()
        focusManager.clearFocus()
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                discoverPublicMcpTools(input, headers)
            }
            loading = false
            when {
                found == null -> error = "无法连接公开 MCP，请检查 SSE/HTTP 地址、配置 JSON、Headers 或网络"
                found.isEmpty() -> error = "这个 MCP Server 没有返回可安装工具"
                else -> results.addAll(found)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "粘贴公开 MCP 的 SSE/Streamable HTTP 地址，或包含 mcpServers 的配置 JSON。",
                fontSize = 12.sp,
                color = SkillMarketMuted,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            MarketInput(
                value = endpointInput,
                onValueChange = { endpointInput = it },
                placeholder = "https://example.com/sse 或 {\"mcpServers\":{...}}",
                singleLine = false,
                minHeight = 86.dp,
            )
            MarketInput(
                value = headersInput,
                onValueChange = { headersInput = it },
                placeholder = "可选 Headers JSON，例如 {\"Authorization\":\"Bearer ...\"}",
                singleLine = false,
                minHeight = 70.dp,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(SkillMarketAction)
                    .clickable { discoverInput() }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text("从地址发现工具", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SkillMarketInk, modifier = Modifier.size(36.dp))
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, fontSize = 14.sp, color = SkillMarketMuted, modifier = Modifier.padding(horizontal = 32.dp))
            }
            results.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SectionLabel(text = "公开 MCP") }
                items(results, key = { it.id }) { entry ->
                    MarketSkillRow(
                        source = entry.platform,
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
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("等待公开 MCP 地址", fontSize = 14.sp, color = SkillMarketMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("发现后会把每个 MCP tool 安装成手机端技能", fontSize = 12.sp, color = SkillMarketMuted.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun RemoteSearchTab(
    platform: String,
    apiBase: String,
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
) {
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
                found == null -> error = str(R.string.platform_unreachable, platform)
                found.isEmpty() -> error = str(R.string.skill_market_none)
                else -> results.addAll(found)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color.White.copy(alpha = 0.56f))
                .border(0.7.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(27.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = SkillMarketMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 14.sp, color = SkillMarketInk),
                cursorBrush = SolidColor(SkillMarketInk),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(str(R.string.search_skills_hint, platform), fontSize = 14.sp, color = SkillMarketMuted)
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SkillMarketInk)
                        .clickable { doSearch() }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(str(R.string.profile_search), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when {
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SkillMarketInk, modifier = Modifier.size(36.dp))
            }
            error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        error!!,
                        fontSize = 14.sp,
                        color = SkillMarketMuted,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        str(R.string.skill_market_94507e),
                        fontSize = 12.sp,
                        color = SkillMarketMuted.copy(alpha = 0.6f),
                    )
                }
            }
            results.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionLabel(text = "热榜")
                }
                items(results, key = { it.id }) { entry ->
                    MarketSkillRow(
                        source = entry.platform,
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
                    Text(
                        str(R.string.search_skills_placeholder, platform),
                        fontSize = 14.sp,
                        color = SkillMarketMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        str(R.string.skill_market_6a2070),
                        fontSize = 12.sp,
                        color = SkillMarketMuted.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketSkillRow(
    source: String,
    name: String,
    description: String,
    tags: List<String>,
    stars: Int?,
    installed: Boolean,
    busy: Boolean = false,
    actionText: String = str(R.string.skill_market_e655a4),
    onInstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.62f))
            .border(0.7.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = SkillMarketInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = SkillMarketMuted,
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
                    val visibleTags = if (tags.isEmpty()) listOf(source) else tags.take(2)
                    visibleTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(SkillMarketTagBg)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(tag, fontSize = 10.sp, color = SkillMarketTagInk, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (stars != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(stars.toString(), fontSize = 10.sp, color = SkillMarketMuted)
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Action button
        if (installed || busy) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SkillMarketInstalledBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(color = SkillMarketTagInk, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                    } else {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SkillMarketTagInk,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (busy) "发现中" else str(R.string.skill_market_done),
                        fontSize = 11.sp,
                        color = SkillMarketTagInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SkillMarketAction)
                    .clickable { onInstall() }
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(actionText, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MarketInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 54.dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.56f))
            .border(0.7.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        textStyle = TextStyle(fontSize = 13.sp, color = SkillMarketInk, lineHeight = 17.sp),
        cursorBrush = SolidColor(SkillMarketInk),
        singleLine = singleLine,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = SkillMarketMuted.copy(alpha = 0.72f), lineHeight = 17.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = SkillMarketMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text("↗", color = SkillMarketMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private val SkillMarketInk = Color(0xFF191B1B)
private val SkillMarketMuted = Color(0xFF76777B)
private val SkillMarketAction = Color(0xFF101010)
private val SkillMarketTagInk = Color(0xFF4F4D48)
private val SkillMarketTagBg = Color(0xFFF1EFE8).copy(alpha = 0.78f)
private val SkillMarketInstalledBg = Color(0xFFE7F5F2).copy(alpha = 0.72f)

private fun skillMarketWorkbenchBrush(): Brush =
    Brush.verticalGradient(
        listOf(
            Color(0xFFFFFEFA),
            Color(0xFFF7F4EE),
            Color(0xFFF8F6F0),
        ),
    )

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

private suspend fun discoverPublicMcpTools(
    input: String,
    headersJson: String = "",
): List<RemoteSkillEntry>? {
    val config = McpEndpointConfig.parse(input) ?: return null
    val extraHeaders = parseHeaderObject(headersJson) ?: return null
    val headers = config.headers + extraHeaders
    return runCatching {
        val tools = McpHttpClient().listTools(config.endpoint, headers).tools
        tools.map { tool ->
            val idSeed = "${config.endpoint}_${tool.name}"
            val safeId = ("public_mcp_" + idSeed)
                .replace(Regex("[^a-zA-Z0-9_]"), "_")
                .lowercase()
                .takeLast(56)
            val params = tool.inputSchema?.getAsJsonObject("properties")
                ?.entrySet()
                ?.map { (key, value) ->
                    val obj = value.takeIf { it.isJsonObject }?.asJsonObject
                    val type = obj?.get("type")?.asString?.takeIf { it in setOf("string", "number", "boolean", "object", "array") } ?: "string"
                    val desc = obj?.get("description")?.asString ?: "MCP parameter"
                    val required = tool.inputSchema
                        ?.takeIf { it.has("required") && it["required"].isJsonArray }
                        ?.getAsJsonArray("required")
                        ?.any { it.asString == key } == true
                    SkillParam(key, type, desc, required = required)
                }
                .orEmpty()
            val meta = SkillMeta(
                id = safeId,
                name = tool.title ?: tool.name,
                nameZh = tool.title ?: tool.name,
                description = tool.description ?: "Public MCP tool: ${tool.name}",
                descriptionZh = tool.description ?: "公开 MCP 工具：${tool.name}",
                parameters = params,
                type = SkillType.MCP,
                injectionLevel = 2,
                isBuiltin = false,
                tags = listOf("MCP", "公开"),
            )
            val categorizedMeta = meta.copy(categories = SkillToolTaxonomy.categoriesFor(meta).toList())
            RemoteSkillEntry(
                id = safeId,
                name = meta.name,
                nameZh = meta.nameZh,
                description = meta.description,
                descriptionZh = meta.descriptionZh,
                tags = categorizedMeta.tags,
                stars = 0,
                platform = "公开 MCP",
                def = SkillDefinition(
                    meta = categorizedMeta,
                    mcpConfig = McpSkillConfig(
                        endpoint = config.endpoint,
                        tool = tool.name,
                        headers = headers,
                    ),
                ),
            )
        }
    }.getOrNull()
}

private fun parseHeaderObject(json: String): Map<String, String>? {
    if (json.isBlank()) return emptyMap()
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    return obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        .filterKeys { it.isNotBlank() }
        .filterValues { it.isNotBlank() }
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
    val categorizedMeta = meta.copy(categories = SkillToolTaxonomy.categoriesFor(meta).toList())
    return if (httpUrl.isNotBlank()) {
        SkillDefinition(meta = categorizedMeta, httpConfig = HttpSkillConfig(url = httpUrl))
    } else {
        val script = scriptBody.ifBlank {
            "# $name\n# Sourced from $platform\nprint(\"\"\"$description\"\"\")"
        }
        SkillDefinition(meta = categorizedMeta, script = script)
    }
}
