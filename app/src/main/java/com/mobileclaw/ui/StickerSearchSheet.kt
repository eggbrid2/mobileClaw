package com.mobileclaw.ui

import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.ClawApplication
import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.builtin.ChineseBqbStickerRepository
import com.mobileclaw.skill.builtin.StickerFavoritesStore
import com.mobileclaw.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSearchSheet(
    onDismiss: () -> Unit,
    onSelected: (SkillAttachment.FileData) -> Unit,
) {
    val c = LocalClawColors.current
    val app = ClawApplication.instance
    val scope = rememberCoroutineScope()
    val favoritesStore = remember { StickerFavoritesStore(app) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChineseBqbStickerRepository.Entry>>(emptyList()) }
    var favorites by remember { mutableStateOf(favoritesStore.all()) }
    var selectedTab by remember { mutableStateOf(0) }
    var isSearching by remember { mutableStateOf(true) }
    var loadingUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun searchNow(raw: String) {
        scope.launch {
            isSearching = true
            error = null
            val found = runCatching {
                ChineseBqbStickerRepository.search(app, raw.trim(), limit = 40)
            }.onFailure { error = it.message ?: str(R.string.sticker_search_failed) }.getOrDefault(emptyList())
            results = found
            isSearching = false
        }
    }

    LaunchedEffect(Unit) { searchNow("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.sticker_search_title), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(str(R.string.sticker_search_subtitle), color = c.subtext, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(19.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(str(R.string.sticker_favorites), str(R.string.sticker_search_tab)).forEachIndexed { index, label ->
                    val active = selectedTab == index
                    Box(
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) c.text else c.card)
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (active) c.bg else c.subtext, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }

            if (selectedTab == 0) {
                if (favorites.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text(str(R.string.sticker_favorites_empty), color = c.subtext, fontSize = 13.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(favorites, key = { it.path }) { favorite ->
                            FavoriteStickerTile(
                                sticker = favorite,
                                onSend = {
                                    onSelected(favorite.toFileData())
                                    onDismiss()
                                },
                                onUnfavorite = {
                                    favoritesStore.toggle(favorite.toFileData())
                                    favorites = favoritesStore.all()
                                },
                            )
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp)) },
                        placeholder = { Text(str(R.string.sticker_search_hint), color = c.subtext, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.text,
                            unfocusedBorderColor = c.border,
                            focusedTextColor = c.text,
                            unfocusedTextColor = c.text,
                            cursorColor = c.text,
                            focusedContainerColor = c.card,
                            unfocusedContainerColor = c.card,
                        ),
                    )
                    TextButton(
                        onClick = { searchNow(query) },
                        modifier = Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.text),
                    ) {
                        Text(str(R.string.sticker_search_action), color = c.bg, maxLines = 1)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("哈哈", "生气", "摸鱼", "猫", "狗", "谢谢").forEach { hot ->
                        Text(
                            hot,
                            color = c.text,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.cardAlt)
                                .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                                .clickable {
                                    query = hot
                                    searchNow(hot)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                if (isSearching) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.text, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else if (error != null) {
                    Text(error.orEmpty(), color = c.red, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else if (results.isEmpty()) {
                    Text(str(R.string.sticker_search_empty), color = c.subtext, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(results, key = { it.url }) { entry ->
                            StickerResultTile(
                                entry = entry,
                                loading = loadingUrl == entry.url,
                                onClick = {
                                    scope.launch {
                                        loadingUrl = entry.url
                                        val file = runCatching { ChineseBqbStickerRepository.download(app, entry) }
                                            .onFailure { error = it.message ?: str(R.string.sticker_download_failed) }
                                            .getOrNull()
                                        loadingUrl = null
                                        if (file != null) {
                                            onSelected(file)
                                            onDismiss()
                                        }
                                    }
                                },
                                onFavorite = { file ->
                                    favoritesStore.toggle(file)
                                    favorites = favoritesStore.all()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StickerResultTile(
    entry: ChineseBqbStickerRepository.Entry,
    loading: Boolean,
    onClick: () -> Unit,
    onFavorite: (SkillAttachment.FileData) -> Unit,
) {
    val c = LocalClawColors.current
    val app = ClawApplication.instance
    val scope = rememberCoroutineScope()
    var favLoading by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.cardAlt.copy(alpha = 0.5f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = c.text, modifier = Modifier.size(18.dp), strokeWidth = 1.8.dp)
        else StickerEntryThumbnail(entry)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.surface.copy(alpha = 0.86f))
                .border(0.5.dp, c.border, RoundedCornerShape(11.dp))
                .clickable(enabled = !favLoading) {
                    scope.launch {
                        favLoading = true
                        val file = runCatching { ChineseBqbStickerRepository.download(app, entry) }.getOrNull()
                        favLoading = false
                        if (file != null) onFavorite(file)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FavoriteStickerTile(
    sticker: StickerFavoritesStore.FavoriteSticker,
    onSend: () -> Unit,
    onUnfavorite: () -> Unit,
) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.cardAlt.copy(alpha = 0.5f))
            .clickable(onClick = onSend)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        StickerFileThumbnail(sticker.path, sticker.name)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.surface.copy(alpha = 0.86f))
                .border(0.5.dp, c.border, RoundedCornerShape(11.dp))
                .clickable(onClick = onUnfavorite),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StickerEntryThumbnail(entry: ChineseBqbStickerRepository.Entry) {
    val app = ClawApplication.instance
    val bitmap by produceState<Bitmap?>(null, entry.url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = ChineseBqbStickerRepository.download(app, entry)
                BitmapFactory.decodeFile(file.path)
            }.getOrNull()
        }
    }
    StickerBitmapThumb(bitmap = bitmap, contentDescription = entry.name)
}

@Composable
private fun StickerFileThumbnail(path: String, contentDescription: String) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    StickerBitmapThumb(bitmap = bitmap, contentDescription = contentDescription)
}

@Composable
private fun StickerBitmapThumb(bitmap: Bitmap?, contentDescription: String) {
    val c = LocalClawColors.current
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(62.dp)
                .padding(2.dp),
        )
    } else {
        Text("图", color = c.subtext, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

suspend fun stickerFileToDataUri(file: SkillAttachment.FileData): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = File(file.path).readBytes()
        val mime = file.mimeType.ifBlank { "image/jpeg" }
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()
}
