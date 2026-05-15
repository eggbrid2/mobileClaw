package com.mobileclaw.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.TaskType
import com.mobileclaw.skill.SkillMeta
import java.util.UUID
import com.mobileclaw.str

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleEditPage(
    initial: Role,
    availableModels: List<String> = emptyList(),
    modelsLoading: Boolean = false,
    allSkills: List<SkillMeta> = emptyList(),
    onSave: (Role) -> Unit,
    onRestore: (() -> Unit)? = null,
    onFetchModels: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val c = LocalClawColors.current

    var name by remember { mutableStateOf(initial.name) }
    var avatar by remember { mutableStateOf(initial.avatar) }
    var description by remember { mutableStateOf(initial.description) }
    var addendum by remember { mutableStateOf(initial.systemPromptAddendum) }
    var schedulerKeywords by remember { mutableStateOf(initial.keywords.joinToString(", ")) }
    var selectedTaskTypes by remember { mutableStateOf(initial.preferredTaskTypes.toSet()) }
    var selectedModel by remember { mutableStateOf(initial.modelOverride ?: "") }
    var selectedSkillIds by remember { mutableStateOf(initial.forcedSkillIds.toSet()) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    val isImageAvatar = avatar.startsWith("content://") || avatar.startsWith("file://")

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cropSourceUri = uri
        }
    }

    LaunchedEffect(Unit) {
        if (availableModels.isEmpty()) onFetchModels()
    }

    // Show crop dialog over the edit page
    if (cropSourceUri != null) {
        CropImageDialog(
            imageUri = cropSourceUri!!,
            onDismiss = { cropSourceUri = null },
            onCropped = { path -> avatar = path; cropSourceUri = null },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        ClawPageHeader(
            title = str(if (initial.isBuiltin) R.string.role_edit_title else R.string.role_create_title),
            onBack = onBack,
            actions = {
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    onSave(
                        Role(
                            id = initial.id,
                            name = name.trim(),
                            description = description.trim(),
                            avatar = avatar.trim().ifBlank { "🤖" },
                            systemPromptAddendum = addendum.trim(),
                            forcedSkillIds = selectedSkillIds.toList(),
                            modelOverride = selectedModel.trim().ifBlank { null },
                            preferredTaskTypes = selectedTaskTypes.toList(),
                            keywords = schedulerKeywords
                                .split(",", "，", "\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct(),
                            isBuiltin = initial.isBuiltin,
                            chatBubbleStyle = initial.chatBubbleStyle,
                        )
                    )
                }) {
                    Text(str(R.string.role_save), color = c.accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Avatar (tappable) ─────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clickable { imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    GradientAvatar(emoji = avatar, size = 80.dp, color = c.accent)
                    // Camera badge overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = c.bg, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (isImageAvatar) {
                    TextButton(onClick = { avatar = "🤖" }) {
                        Text(str(R.string.role_edit_74308d), fontSize = 12.sp, color = c.subtext)
                    }
                } else {
                    OutlinedTextField(
                        value = avatar,
                        onValueChange = { if (it.length <= 2) avatar = it },
                        label = { Text(str(R.string.role_edit_74308d)) },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                    )
                }
            }

            // ── Name ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(str(R.string.role_field_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Description ───────────────────────────────────────────────────
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(str(R.string.role_field_description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Scheduler Metadata ───────────────────────────────────────────
            OutlinedTextField(
                value = schedulerKeywords,
                onValueChange = { schedulerKeywords = it },
                label = { Text(str(R.string.role_field_scheduler_keywords)) },
                placeholder = { Text(str(R.string.role_field_scheduler_keywords_hint), fontSize = 12.sp, color = c.subtext) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
            )

            Text(
                text = str(R.string.role_field_task_types),
                fontSize = 12.sp,
                color = c.subtext,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, c.border, RoundedCornerShape(8.dp)),
            ) {
                TaskType.values().forEachIndexed { index, taskType ->
                    if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                    val selected = taskType in selectedTaskTypes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTaskTypes = if (selected)
                                    selectedTaskTypes - taskType
                                else
                                    selectedTaskTypes + taskType
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                selectedTaskTypes = if (checked)
                                    selectedTaskTypes + taskType
                                else
                                    selectedTaskTypes - taskType
                            },
                        )
                        Text(taskType.name, fontSize = 13.sp, color = c.text)
                    }
                }
            }

            // ── System Prompt Addendum ────────────────────────────────────────
            OutlinedTextField(
                value = addendum,
                onValueChange = { addendum = it },
                label = { Text(str(R.string.role_field_system_prompt)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            // ── Model Dropdown ────────────────────────────────────────────────
            when {
                modelsLoading -> {
                    OutlinedTextField(
                        value = str(R.string.role_edit_loading),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(str(R.string.role_field_model)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                availableModels.isNotEmpty() -> {
                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedModel.ifBlank { str(R.string.role_edit_b11de2) },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(str(R.string.role_field_model)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(str(R.string.role_edit_b11de2)) },
                                onClick = { selectedModel = ""; modelDropdownExpanded = false },
                            )
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model, fontSize = 13.sp) },
                                    onClick = { selectedModel = model; modelDropdownExpanded = false },
                                )
                            }
                        }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it },
                            label = { Text(str(R.string.role_field_model)) },
                            placeholder = { Text(str(R.string.role_edit_2442c5), fontSize = 12.sp, color = c.subtext) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onFetchModels) {
                            Text(str(R.string.role_edit_refresh), color = c.accent, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Force-Inject Skills ───────────────────────────────────────────
            if (allSkills.isNotEmpty()) {
                Text(
                    text = str(R.string.role_field_skills),
                    fontSize = 12.sp,
                    color = c.subtext,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, c.border, RoundedCornerShape(8.dp)),
                ) {
                    allSkills.forEachIndexed { index, skill ->
                        if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
                        val selected = skill.id in selectedSkillIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSkillIds = if (selected)
                                        selectedSkillIds - skill.id
                                    else
                                        selectedSkillIds + skill.id
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedSkillIds = if (checked)
                                        selectedSkillIds + skill.id
                                    else
                                        selectedSkillIds - skill.id
                                },
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(skill.name, fontSize = 13.sp, color = c.text)
                                Text(skill.id, fontSize = 10.sp, color = c.subtext)
                            }
                        }
                    }
                }
            }

            // ── Restore for built-ins ─────────────────────────────────────────
            if (onRestore != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.red.copy(alpha = 0.15f)),
                ) {
                    Text(str(R.string.role_edit_a2ea09), color = c.red, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
