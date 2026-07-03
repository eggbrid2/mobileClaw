package com.mobileclaw.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.memory.MemoryFact
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.ClawPageHeader
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.ui.common.openFileAttachment
import com.mobileclaw.str
import com.mobileclaw.workspace.WorkspaceInspectorSnapshot
import java.io.File
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspacePage(
    snapshot: WorkspaceInspectorSnapshot?,
    facts: List<MemoryFact>,
    areas: List<WorkspaceAreaUi>,
    openArea: WorkspaceAreaUi?,
    openAreaRoots: List<String>,
    openAreaCurrentPath: String,
    openAreaEntries: List<WorkspaceFileEntryUi>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenArea: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNavigateFolderUp: () -> Unit,
    onCloseArea: () -> Unit,
    onPromoteFact: (String) -> Unit,
    onDeleteFact: (String) -> Unit,
) {
    val c = LocalClawColors.current
    if (openArea != null) {
        BackHandler { if (openAreaCurrentPath.isNotBlank()) onNavigateFolderUp() else onCloseArea() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .navigationBarsPadding(),
    ) {
        ClawPageHeader(
            title = openArea?.title ?: str(R.string.workspace_title),
            onBack = if (openArea != null) {
                { if (openAreaCurrentPath.isNotBlank()) onNavigateFolderUp() else onCloseArea() }
            } else {
                onBack
            },
        ) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = str(R.string.workspace_refresh))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (openArea != null) {
                WorkspaceAreaDetail(
                    area = openArea,
                    roots = openAreaRoots,
                    currentPath = openAreaCurrentPath,
                    entries = openAreaEntries,
                    onOpenFolder = onOpenFolder,
                    onNavigateUp = onNavigateFolderUp,
                )
                Spacer(Modifier.height(8.dp))
                return@Column
            }

            WorkspaceOverview(areas = areas, onOpenArea = onOpenArea)

            if (snapshot == null) {
                WorkspaceSectionCard(title = str(R.string.workspace_current_task)) {
                    WorkspaceEmptyLine(str(R.string.workspace_empty))
                }
                Spacer(Modifier.height(8.dp))
                return@Column
            }

            val currentSnapshot = snapshot

            WorkspaceSectionCard(title = str(R.string.workspace_current_task)) {
                WorkspaceKeyValue(str(R.string.workspace_name), currentSnapshot.manifest.title)
                WorkspaceKeyValue(str(R.string.workspace_goal), currentSnapshot.manifest.goal)
                WorkspaceKeyValue(str(R.string.workspace_scope), currentSnapshot.manifest.scope.ifBlank { str(R.string.workspace_scope_session) })
                WorkspaceKeyValue(str(R.string.workspace_status), currentSnapshot.manifest.status)
                currentSnapshot.execution?.taskType?.takeIf { it.isNotBlank() }?.let {
                    WorkspaceKeyValue(str(R.string.workspace_task_type), it)
                }
                currentSnapshot.execution?.checkpointLabel?.takeIf { it.isNotBlank() }?.let {
                    WorkspaceKeyValue(str(R.string.workspace_checkpoint), it)
                }
                currentSnapshot.execution?.checkpointSummary?.takeIf { it.isNotBlank() }?.let {
                    WorkspaceMultilineValue(str(R.string.workspace_summary), it)
                }
            }

            WorkspaceSectionCard(title = str(R.string.workspace_artifacts)) {
                if (currentSnapshot.recentArtifacts.isEmpty()) {
                    WorkspaceEmptyLine(str(R.string.workspace_no_artifacts))
                } else {
                    currentSnapshot.recentArtifacts.forEachIndexed { index, artifact ->
                        if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = listOf(artifact.artifactType, artifact.title.ifBlank { artifact.artifactId })
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            WorkspaceMetaLine(
                                listOfNotNull(
                                    artifact.action.takeIf { it.isNotBlank() },
                                    artifact.goal.takeIf { it.isNotBlank() }?.take(72),
                                    formatTime(artifact.timestamp),
                                ).joinToString("  ")
                            )
                            artifact.lastDiffSummary.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, color = c.subtext, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }

            WorkspaceSectionCard(title = str(R.string.workspace_checkpoints)) {
                if (currentSnapshot.recentCheckpoints.isEmpty()) {
                    WorkspaceEmptyLine(str(R.string.workspace_no_checkpoints))
                } else {
                    currentSnapshot.recentCheckpoints.forEachIndexed { index, checkpoint ->
                        if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = checkpoint.label,
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            WorkspaceMetaLine(
                                listOfNotNull(
                                    checkpoint.taskType.takeIf { it.isNotBlank() },
                                    formatTime(checkpoint.timestamp),
                                ).joinToString("  ")
                            )
                            checkpoint.summary.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, color = c.subtext, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }

            WorkspaceSectionCard(title = str(R.string.workspace_events)) {
                if (currentSnapshot.recentEvents.isEmpty()) {
                    WorkspaceEmptyLine(str(R.string.workspace_no_events))
                } else {
                    currentSnapshot.recentEvents.forEachIndexed { index, event ->
                        if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = event.title.ifBlank { event.category },
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            WorkspaceMetaLine(
                                listOf(eventCategoryLabel(event.category), event.source, formatTime(event.timestamp)).joinToString("  ")
                            )
                            Text(
                                text = event.summary,
                                color = c.subtext,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            WorkspaceSectionCard(title = str(R.string.workspace_notes)) {
                if (currentSnapshot.recentNotes.isEmpty()) {
                    WorkspaceEmptyLine(str(R.string.workspace_no_notes))
                } else {
                    currentSnapshot.recentNotes.forEachIndexed { index, (name, content) ->
                        if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = name.removeSuffix(".md"),
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = content.take(500),
                                color = c.subtext,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            WorkspaceSectionCard(title = str(R.string.workspace_active_memory)) {
                if (facts.isEmpty()) {
                    WorkspaceEmptyLine(str(R.string.workspace_no_active_memory))
                } else {
                    facts.forEachIndexed { index, fact ->
                        if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = fact.key.substringAfterLast('.'),
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = fact.value,
                                color = c.subtext,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconTextAction(
                                    icon = Icons.Outlined.ArrowUpward,
                                    text = str(R.string.workspace_promote),
                                    onClick = { onPromoteFact(fact.key) },
                                )
                                IconTextAction(
                                    icon = Icons.Outlined.Delete,
                                    text = str(R.string.workspace_delete),
                                    onClick = { onDeleteFact(fact.key) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WorkspaceOverview(
    areas: List<WorkspaceAreaUi>,
    onOpenArea: (String) -> Unit,
) {
    val c = LocalClawColors.current
    WorkspaceSectionCard(title = "总工作空间") {
        if (areas.isEmpty()) {
            WorkspaceEmptyLine("暂无区域数据")
        } else {
            areas.forEachIndexed { index, area ->
                if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArea(area.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(c.cardAlt, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            color = c.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = area.title,
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = area.countLabel,
                                color = c.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = area.description,
                            color = c.subtext,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = area.statusLabel,
                            color = c.subtext,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceAreaDetail(
    area: WorkspaceAreaUi,
    roots: List<String>,
    currentPath: String,
    entries: List<WorkspaceFileEntryUi>,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val c = LocalClawColors.current
    val context = LocalContext.current
    val locationLabel = currentPath.ifBlank { "区域根目录" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(c.cardAlt, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = area.title,
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = area.description,
                    color = c.subtext,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.cardAlt, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("当前位置", color = c.subtext, fontSize = 11.sp, maxLines = 1)
            Text(
                text = locationLabel,
                color = c.text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(area.countLabel, color = c.subtext, fontSize = 11.sp, maxLines = 1)
            Text("·", color = c.subtext, fontSize = 11.sp)
            Text(area.statusLabel, color = c.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    WorkspaceFileList(
        title = if (currentPath.isBlank()) "根目录" else "文件",
        entries = entries,
        showUp = currentPath.isNotBlank(),
        onNavigateUp = onNavigateUp,
        onEntryClick = { entry ->
            if (entry.isDirectory) {
                onOpenFolder(entry.absolutePath)
            } else {
                openWorkspaceFile(context, entry)
            }
        },
    )
}

@Composable
private fun WorkspaceFileList(
    title: String,
    entries: List<WorkspaceFileEntryUi>,
    showUp: Boolean,
    onNavigateUp: () -> Unit,
    onEntryClick: (WorkspaceFileEntryUi) -> Unit,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${entries.size} 项", color = c.subtext, fontSize = 11.sp)
        }
        if (showUp) {
            WorkspaceFileRow(
                name = "..",
                meta = "上一级目录",
                isDirectory = true,
                onClick = onNavigateUp,
            )
            HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
        }
        if (entries.isEmpty()) {
            Text(
                text = "这个目录目前没有可展示的文件。",
                color = c.subtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            )
        } else {
            entries.forEachIndexed { index, entry ->
                WorkspaceFileRow(
                    name = entry.path,
                    meta = if (entry.isDirectory) {
                        "${entry.sizeLabel} · ${entry.updatedLabel}"
                    } else {
                        "${entry.sizeLabel} · ${entry.updatedLabel} · 外部打开"
                    },
                    isDirectory = entry.isDirectory,
                    onClick = {
                        onEntryClick(entry)
                    },
                )
                if (index < entries.lastIndex) {
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceFileRow(
    name: String,
    meta: String,
    isDirectory: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isDirectory) Icons.Outlined.FolderOpen else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = c.text,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                color = c.text,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = c.subtext,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun openWorkspaceFile(context: android.content.Context, entry: WorkspaceFileEntryUi) {
    val file = File(entry.absolutePath)
    openFileAttachment(
        context = context,
        attachment = SkillAttachment.FileData(
            path = entry.absolutePath,
            name = file.name.ifBlank { entry.path },
            mimeType = URLConnection.guessContentTypeFromName(file.name).orEmpty(),
            sizeBytes = file.length(),
        ),
    )
}

@Composable
private fun WorkspaceSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = c.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        content()
    }
}

@Composable
private fun WorkspaceKeyValue(label: String, value: String) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = c.subtext,
            fontSize = 12.sp,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            color = c.text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WorkspaceMultilineValue(label: String, value: String) {
    val c = LocalClawColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = c.subtext, fontSize = 12.sp)
        Text(text = value, color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun WorkspaceMetaLine(text: String) {
    val c = LocalClawColors.current
    Text(text = text, color = c.subtext, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun WorkspaceEmptyLine(text: String) {
    val c = LocalClawColors.current
    Text(text = text, color = c.subtext, fontSize = 12.sp)
}

@Composable
private fun IconTextAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(c.cardAlt, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = text, tint = c.subtext, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            color = c.subtext,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun eventCategoryLabel(category: String): String = when (category) {
    "progress",
    "task_plan",
    "task_started",
    "direct_chat_started",
    "plan_created",
    "tool_call",
    "skill_observation",
    "reflection",
    "review_completed",
    "continuation_checkpoint",
    "deterministic_phone_launch",
    "deterministic_artifact_patch",
    "artifact_observation",
    "file_observation",
    "code_observation" -> "推进"
    "reminder",
    "phone_control_guard",
    "repeated_perception_guard" -> "提醒"
    "repair",
    "draft_repair",
    "validation_repair",
    "runtime_log_repair" -> "修复"
    "completed",
    "task_complete",
    "task_completed",
    "direct_chat_completed" -> "完成"
    "blocked",
    "task_error" -> "阻塞"
    else -> category
}
