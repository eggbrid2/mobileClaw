package com.mobileclaw.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import com.mobileclaw.app.MiniApp
import com.mobileclaw.memory.db.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.mobileclaw.str

sealed class LauncherItem {
    data class Feature(val emoji: String, val label: String, val page: AppPage) : LauncherItem()
    data class App(val id: String, val icon: String, val title: String) : LauncherItem()
}

private val DEFAULT_FEATURES = listOf(
    LauncherItem.Feature("💬", str(R.string.home_859362),    AppPage.CHAT),
    LauncherItem.Feature("🧬", str(R.string.home_c478b1),    AppPage.PROFILE),
    LauncherItem.Feature("👥", str(R.string.home_df9abd),    AppPage.GROUPS),
    LauncherItem.Feature("🎭", str(R.string.drawer_roles),    AppPage.ROLES),
    LauncherItem.Feature("📱", str(R.string.drawer_apps),    AppPage.APPS),
    LauncherItem.Feature("🛠️", str(R.string.drawer_skills),   AppPage.SKILLS),
    LauncherItem.Feature("🏪", str(R.string.home_552cac),   AppPage.SKILL_MARKET),
    LauncherItem.Feature("📄", str(R.string.home_2d20d5), AppPage.AI_PAGES),
    LauncherItem.Feature("⚙️", str(R.string.drawer_settings),   AppPage.SETTINGS),
    LauncherItem.Feature("🖥️", str(R.string.drawer_console), AppPage.CONSOLE),
    LauncherItem.Feature("👤", str(R.string.group_chat_1fd02a),    AppPage.USER_CONFIG),
    LauncherItem.Feature("🔒", "VPN",    AppPage.VPN),
)

private val DOCK_FEATURES = listOf(
    LauncherItem.Feature("💬", str(R.string.home_859362), AppPage.CHAT),
    LauncherItem.Feature("👥", str(R.string.home_df9abd), AppPage.GROUPS),
    LauncherItem.Feature("🎭", str(R.string.drawer_roles), AppPage.ROLES),
    LauncherItem.Feature("🛠️", str(R.string.drawer_skills), AppPage.SKILLS),
)

private val ICON_GRADIENTS = mapOf(
    AppPage.CHAT        to (Color(0xFF34AAFF) to Color(0xFF0055CC)),
    AppPage.PROFILE     to (Color(0xFFBD5FE8) to Color(0xFF7928CA)),
    AppPage.GROUPS      to (Color(0xFF4CD964) to Color(0xFF1A8C32)),
    AppPage.ROLES       to (Color(0xFFFF6B6B) to Color(0xFFCC1010)),
    AppPage.APPS        to (Color(0xFF5AC8FA) to Color(0xFF0080AA)),
    AppPage.SKILLS      to (Color(0xFFFFB240) to Color(0xFFD96000)),
    AppPage.SETTINGS    to (Color(0xFF98989D) to Color(0xFF3A3A3C)),
    AppPage.CONSOLE     to (Color(0xFF6E6AC8) to Color(0xFF3A3790)),
    AppPage.USER_CONFIG    to (Color(0xFFFF375F) to Color(0xFFCC0030)),
    AppPage.SKILL_MARKET   to (Color(0xFF00C896) to Color(0xFF007A5A)),
    AppPage.AI_PAGES       to (Color(0xFFFF9F40) to Color(0xFFE05C00)),
    AppPage.VPN            to (Color(0xFF34D399) to Color(0xFF059669)),
)

private val APP_ICON_PALETTE = listOf(
    Color(0xFF34AAFF) to Color(0xFF0055CC),
    Color(0xFFBD5FE8) to Color(0xFF7928CA),
    Color(0xFF4CD964) to Color(0xFF1A8C32),
    Color(0xFFFF6B6B) to Color(0xFFCC1010),
    Color(0xFF5AC8FA) to Color(0xFF0080AA),
    Color(0xFFFFB240) to Color(0xFFD96000),
)

private fun launcherId(item: LauncherItem) = when (item) {
    is LauncherItem.Feature -> item.page.name
    is LauncherItem.App -> item.id
}

private fun loadOrder(
    prefs: android.content.SharedPreferences,
    allItems: List<LauncherItem>,
): List<LauncherItem> {
    val saved = prefs.getString("icon_order", null) ?: return allItems
    val ids = saved.split(",")
    val byId = allItems.associateBy { launcherId(it) }
    val ordered = ids.mapNotNull { byId[it] }
    val newItems = allItems.filter { item -> ids.none { it == launcherId(item) } }
    return ordered + newItems
}

private fun saveOrder(prefs: android.content.SharedPreferences, items: List<LauncherItem>) {
    prefs.edit().putString("icon_order", items.joinToString(",") { launcherId(it) }).apply()
}

@Composable
fun HomePage(
    currentRole: Role,
    sessions: List<SessionEntity>,
    miniApps: List<MiniApp>,
    darkTheme: Boolean = false,
    onNavigate: (AppPage) -> Unit,
    onOpenApp: (String) -> Unit,
    onDeleteApp: (String) -> Unit,
    onSelectSession: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE) }

    var bgUri by remember { mutableStateOf(prefs.getString("bg_uri", null)) }
    var bgBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val bgPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            bgUri = uri.toString()
            prefs.edit().putString("bg_uri", uri.toString()).apply()
        }
    }

    LaunchedEffect(bgUri, darkTheme) {
        withContext(Dispatchers.IO) {
            bgBitmap = if (bgUri != null) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(bgUri))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            } else {
                val resId = if (darkTheme) R.mipmap.bg_desk_night else R.mipmap.bg_desk_light
                runCatching { BitmapFactory.decodeResource(context.resources, resId) }.getOrNull()
            }
        }
    }

    val baseItems: List<LauncherItem> = remember(miniApps) {
        DEFAULT_FEATURES + miniApps.map { LauncherItem.App(it.id, it.icon, it.title) }
    }

    var items by remember(miniApps) {
        mutableStateOf(loadOrder(prefs, baseItems))
    }

    var isEditMode by remember { mutableStateOf(false) }
    var draggingIdx by remember { mutableIntStateOf(-1) }
    var dragRootX by remember { mutableFloatStateOf(0f) }
    var dragRootY by remember { mutableFloatStateOf(0f) }

    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var pendingPickerLaunch by remember { mutableStateOf(false) }
    LaunchedEffect(pendingPickerLaunch) {
        if (pendingPickerLaunch) {
            bgPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            pendingPickerLaunch = false
        }
    }

    val itemsRef = rememberUpdatedState(items)

    BackHandler(isEditMode) { isEditMode = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxCoords = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val coords = boxCoords ?: return@detectDragGesturesAfterLongPress
                        val rootOffset = coords.localToRoot(offset)
                        val hitEntry = itemBounds.entries.firstOrNull { (_, rect) ->
                            rect.contains(rootOffset)
                        }
                        if (hitEntry != null) {
                            draggingIdx = hitEntry.key
                            dragRootX = rootOffset.x
                            dragRootY = rootOffset.y
                            isEditMode = true
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggingIdx >= 0) {
                            dragRootX += dragAmount.x
                            dragRootY += dragAmount.y
                            val currentPos = Offset(dragRootX, dragRootY)
                            val nearestIdx = itemBounds.entries
                                .minByOrNull { (_, rect) ->
                                    (rect.center - currentPos).getDistance()
                                }?.key ?: return@detectDragGesturesAfterLongPress
                            val cur = itemsRef.value
                            if (nearestIdx != draggingIdx &&
                                nearestIdx < cur.size &&
                                draggingIdx < cur.size
                            ) {
                                val mutable = cur.toMutableList()
                                val dragged = mutable.removeAt(draggingIdx)
                                mutable.add(nearestIdx, dragged)
                                items = mutable
                                draggingIdx = nearestIdx
                            }
                        }
                    },
                    onDragEnd = {
                        if (draggingIdx < 0) {
                            // Long press on blank area → show customization menu
                            isEditMode = true
                        } else {
                            saveOrder(prefs, itemsRef.value)
                            draggingIdx = -1
                        }
                    },
                    onDragCancel = {
                        draggingIdx = -1
                    },
                )
            },
    ) {
        // Background
        val bm = bgBitmap
        if (bm != null) {
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A)))
        }

        // Main icon grid + dock
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))
                items.chunked(5).forEachIndexed { rowIdx, row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { colIdx, item ->
                            val idx = rowIdx * 5 + colIdx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .onGloballyPositioned { coords ->
                                        itemBounds[idx] = coords.boundsInRoot()
                                    }
                                    .alpha(if (draggingIdx == idx) 0f else 1f),
                            ) {
                                LauncherIconView(
                                    item = item,
                                    isEditMode = isEditMode,
                                    onTap = {
                                        when (item) {
                                            is LauncherItem.Feature -> onNavigate(item.page)
                                            is LauncherItem.App -> onOpenApp(item.id)
                                        }
                                    },
                                    onDelete = {
                                        if (item is LauncherItem.App) {
                                            onDeleteApp(item.id)
                                            items = items.filter { it != item }
                                        }
                                    },
                                )
                            }
                        }
                        repeat(5 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            BottomDock(onNavigate = onNavigate)
        }

        // Floating drag ghost
        if (draggingIdx >= 0 && draggingIdx < items.size) {
            val boxPos = boxCoords?.positionInRoot() ?: Offset.Zero
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (dragRootX - boxPos.x - 26.dp.toPx()).roundToInt(),
                        (dragRootY - boxPos.y - 40.dp.toPx()).roundToInt(),
                    )
                },
            ) {
                LauncherIconView(
                    item = items[draggingIdx],
                    isEditMode = false,
                    onTap = {},
                    onDelete = {},
                )
            }
        }

        // Dim overlay — fades in when edit mode is active
        AnimatedVisibility(
            visible = isEditMode,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        }

        // "完成" button — slides down from top right
        AnimatedVisibility(
            visible = isEditMode,
            enter = slideInVertically { -it } + fadeIn(tween(220)),
            exit = slideOutVertically { -it } + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(end = 16.dp, top = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF007AFF))
                    .clickable { isEditMode = false }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text(
                    text = str(R.string.app_launcher_done),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }

        // Customization sheet — slides up from bottom
        AnimatedVisibility(
            visible = isEditMode,
            enter = slideInVertically { it } + fadeIn(tween(250)),
            exit = slideOutVertically { it } + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            DesktopEditMenu(
                onWallpaper = {
                    isEditMode = false
                    pendingPickerLaunch = true
                },
            )
        }
    }
}

@Composable
private fun LauncherIconView(
    item: LauncherItem,
    isEditMode: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val emoji = when (item) {
        is LauncherItem.Feature -> item.emoji
        is LauncherItem.App -> item.icon
    }
    val label = when (item) {
        is LauncherItem.Feature -> item.label
        is LauncherItem.App -> item.title
    }
    val isApp = item is LauncherItem.App

    val iconBrush = remember(item) {
        when (item) {
            is LauncherItem.Feature -> {
                val (c1, c2) = ICON_GRADIENTS[item.page] ?: (Color(0xFF607D8B) to Color(0xFF37474F))
                Brush.verticalGradient(listOf(c1, c2))
            }
            is LauncherItem.App -> {
                val (c1, c2) = APP_ICON_PALETTE[kotlin.math.abs(item.id.hashCode()) % APP_ICON_PALETTE.size]
                Brush.verticalGradient(listOf(c1, c2))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush = iconBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 28.sp)
            }
            if (isEditMode && isApp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.8f),
                    offset = Offset.Zero,
                    blurRadius = 4f,
                ),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 64.dp),
        )
    }
}

@Composable
private fun BottomDock(onNavigate: (AppPage) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DOCK_FEATURES.forEach { item ->
                val dockBrush = ICON_GRADIENTS[item.page]?.let { (c1, c2) ->
                    Brush.verticalGradient(listOf(c1, c2))
                } ?: Brush.verticalGradient(listOf(Color(0xFF607D8B), Color(0xFF37474F)))
                Column(
                    modifier = Modifier
                        .width(70.dp)
                        .clickable { onNavigate(item.page) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(brush = dockBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.emoji, fontSize = 30.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset.Zero,
                                blurRadius = 4f,
                            ),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopEditMenu(onWallpaper: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EditMenuItem(icon = Icons.Outlined.Image, label = stringResource(R.string.home_8e9219), onClick = onWallpaper)
            EditMenuItem(icon = Icons.Outlined.Tune, label = stringResource(R.string.home_bb7a93), onClick = {})
            EditMenuItem(icon = Icons.Outlined.GridView, label = stringResource(R.string.home_2066b9), onClick = {})
            EditMenuItem(icon = Icons.Outlined.Settings, label = stringResource(R.string.home_5ea7aa), onClick = {})
        }
    }
}

@Composable
private fun EditMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset.Zero, 3f),
            ),
        )
    }
}
