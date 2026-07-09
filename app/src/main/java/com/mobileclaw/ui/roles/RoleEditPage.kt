package com.mobileclaw.ui.roles

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.mobileclaw.R
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleAvatarDefaults
import com.mobileclaw.agent.RoleModelBinding
import com.mobileclaw.agent.RoleWorkspaceSnapshot
import com.mobileclaw.agent.RoleWorkspaceStore
import com.mobileclaw.agent.effectiveModelBinding
import com.mobileclaw.agent.isRoleImageAvatar
import com.mobileclaw.agent.normalizeRoleAvatar
import com.mobileclaw.agent.TaskType
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.ui.CropImageDialog
import com.mobileclaw.ui.GradientAvatar
import com.mobileclaw.ui.LocalAppLanguage
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.ui.RoleWorkspaceFileUi
import com.mobileclaw.ui.chat.runtime.RoleChatControlPlanCompiler
import com.mobileclaw.ui.chat.runtime.RoleExecutionProtocol
import com.mobileclaw.ui.chat.runtime.RoleExecutionProtocolParser
import com.mobileclaw.ui.chat.runtime.RoleRuntimeProfile
import java.util.UUID
import com.mobileclaw.str

private enum class RoleEditMode {
    QUICK,
    FULL,
}

private enum class RoleExecutionPreference {
    AUTO,
    DIRECT_FIRST,
    AGENT_FIRST,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleEditPage(
    initial: Role,
    workspaceFiles: List<RoleWorkspaceFileUi> = emptyList(),
    configSnapshot: ConfigSnapshot = ConfigSnapshot(),
    availableModels: List<String> = emptyList(),
    modelsLoading: Boolean = false,
    gatewayModels: Map<String, List<String>> = emptyMap(),
    gatewayModelsLoadingIds: Set<String> = emptySet(),
    allSkills: List<SkillMeta> = emptyList(),
    onSave: (Role, String) -> Unit,
    onRestore: (() -> Unit)? = null,
    onFetchModels: () -> Unit = {},
    onFetchGatewayModels: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    val gson = remember { GsonBuilder().setPrettyPrinting().create() }

    var name by remember { mutableStateOf(initial.name) }
    var avatar by remember { mutableStateOf(normalizeRoleAvatar(initial.id, initial.avatar)) }
    var description by remember { mutableStateOf(initial.description) }
    var addendum by remember { mutableStateOf(initial.systemPromptAddendum) }
    var schedulerKeywords by remember { mutableStateOf(initial.keywords.joinToString(", ")) }
    var selectedTaskTypes by remember { mutableStateOf(initial.preferredTaskTypes.toSet()) }
    val initialModelBinding = remember(initial.id, initial.modelBinding, initial.modelOverride) { initial.effectiveModelBinding() }
    var selectedGatewayId by remember(initial.id, initial.modelBinding, initial.modelOverride) { mutableStateOf(initialModelBinding?.gatewayId.orEmpty()) }
    var selectedModel by remember(initial.id, initial.modelBinding, initial.modelOverride) {
        mutableStateOf(
            initialModelBinding?.legacyModelOverride()
                ?: initial.modelOverride
                ?: "",
        )
    }
    var selectedSkillIds by remember { mutableStateOf(initial.forcedSkillIds.toSet()) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }
    var editMode by remember { mutableStateOf(RoleEditMode.QUICK) }
    var rawDefinition by remember(initial.id) { mutableStateOf(gson.toJson(initial)) }
    var fullEditError by remember { mutableStateOf("") }
    var chatControlExpanded by remember { mutableStateOf(true) }
    var rawProtocolExpanded by remember { mutableStateOf(false) }
    var protocolMarkdown by remember(initial.id) { mutableStateOf(defaultEditableChatProtocol(initial)) }
    var protocolLoadedFromWorkspace by remember(initial.id) { mutableStateOf(false) }
    var inputUnderstanding by remember(initial.id) { mutableStateOf("") }
    var contextReading by remember(initial.id) { mutableStateOf("") }
    var memoryPolicy by remember(initial.id) { mutableStateOf("") }
    var skillPolicy by remember(initial.id) { mutableStateOf("") }
    var responsePolicy by remember(initial.id) { mutableStateOf("") }
    var persistencePolicy by remember(initial.id) { mutableStateOf("") }
    var executionPreference by remember(initial.id) { mutableStateOf(RoleExecutionPreference.AUTO) }

    fun loadProtocol(markdown: String) {
        val protocol = RoleExecutionProtocolParser.parse(
            roleId = initial.id,
            markdown = markdown.ifBlank { defaultEditableChatProtocol(initial) },
        )
        protocolMarkdown = protocol.toMarkdown()
        inputUnderstanding = protocol.inputUnderstanding
        contextReading = protocol.contextReading
        memoryPolicy = protocol.memoryPolicy
        skillPolicy = protocol.skillPolicy
        responsePolicy = protocol.responsePolicy
        persistencePolicy = protocol.persistencePolicy
        executionPreference = inferExecutionPreference(protocol)
    }

    fun currentProtocolMarkdown(): String = RoleExecutionProtocol(
        roleId = initial.id,
        version = 1,
        inputUnderstanding = inputUnderstanding,
        contextReading = contextReading,
        memoryPolicy = memoryPolicy,
        skillPolicy = applyExecutionPreferenceToSkillPolicy(skillPolicy, executionPreference),
        responsePolicy = applyExecutionPreferenceToResponsePolicy(responsePolicy, executionPreference),
        persistencePolicy = persistencePolicy,
    ).toMarkdown()

    val isImageAvatar = isRoleImageAvatar(avatar)
    val selectedGateway = configSnapshot.gateways.firstOrNull { it.id == selectedGatewayId }
    fun draftModelBinding(): RoleModelBinding? {
        val model = selectedModel.trim()
        if (model.startsWith("local:")) {
            return RoleModelBinding(localModelId = model.removePrefix("local:"))
        }
        val binding = RoleModelBinding(
            gatewayId = selectedGateway?.id.orEmpty(),
            gatewayName = selectedGateway?.name.orEmpty(),
            model = model,
        ).normalized()
        return binding.takeUnless { it.isEmpty() }
    }
    fun draftRole(): Role = Role(
        id = initial.id,
        name = name.trim(),
        description = description.trim(),
        avatar = normalizeRoleAvatar(initial.id, avatar),
        systemPromptAddendum = addendum.trim(),
        forcedSkillIds = selectedSkillIds.toList(),
        modelBinding = draftModelBinding(),
        modelOverride = draftModelBinding()?.legacyModelOverride(),
        preferredTaskTypes = selectedTaskTypes.toList(),
        keywords = schedulerKeywords
            .split(",", "，", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct(),
        isBuiltin = initial.isBuiltin,
        chatBubbleStyle = initial.chatBubbleStyle,
    )

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            cropSourceUri = uri
        }
    }

    fun saveRole() {
        val roleToSave = if (editMode == RoleEditMode.FULL) {
            val parsed = runCatching { gson.fromJson(rawDefinition, Role::class.java) }.getOrElse {
                fullEditError = if (isZh) "定义不是有效 JSON" else "Definition is not valid JSON"
                return
            }
            val parsedName = runCatching { parsed.name.trim() }.getOrDefault("")
            if (parsedName.isBlank()) {
                fullEditError = if (isZh) "角色名称不能为空" else "Role name cannot be empty"
                return
            }
            parsed.copy(
                id = initial.id,
                name = parsedName,
                avatar = normalizeRoleAvatar(initial.id, runCatching { parsed.avatar }.getOrDefault("")),
                isBuiltin = initial.isBuiltin,
            )
        } else {
            if (name.isBlank()) return
            draftRole()
        }
        fullEditError = ""
        onSave(
            roleToSave,
            if (rawProtocolExpanded) protocolMarkdown else currentProtocolMarkdown(),
        )
    }

    LaunchedEffect(Unit) {
        if (availableModels.isEmpty()) onFetchModels()
    }

    LaunchedEffect(initial.id, workspaceFiles) {
        if (!protocolLoadedFromWorkspace) {
            val workspaceProtocol = workspaceFiles
                .firstOrNull { it.name == RoleWorkspaceStore.CHAT_PROTOCOL_MD }
                ?.content
            loadProtocol(workspaceProtocol.orEmpty())
            if (workspaceProtocol != null) protocolLoadedFromWorkspace = true
        }
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
        RoleEditTopBar(
            title = str(if (initial.name.isBlank() && !initial.isBuiltin) R.string.role_create_title else R.string.role_edit_title),
            onBack = onBack,
            onSave = ::saveRole,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoleEditModeSwitch(
                selected = editMode,
                onSelect = { mode ->
                    if (mode == RoleEditMode.FULL && editMode != RoleEditMode.FULL) {
                        rawDefinition = gson.toJson(draftRole())
                        fullEditError = ""
                    }
                    editMode = mode
                },
            )

            if (editMode == RoleEditMode.FULL) {
                RoleEditSectionTitle(if (isZh) "完整定义" else "Full definition")
                OutlinedTextField(
                    value = rawDefinition,
                    onValueChange = {
                        rawDefinition = it
                        fullEditError = ""
                    },
                    label = { Text(if (isZh) "Role JSON" else "Role JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 18,
                    maxLines = 32,
                    shape = RoundedCornerShape(16.dp),
                    colors = roleEditorTextFieldColors(c),
                )
                if (fullEditError.isNotBlank()) {
                    Text(fullEditError, color = c.red, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Text(
                    text = if (isZh) "保存会保留当前角色 ID，其他字段按定义写入。" else "Save keeps this role ID and writes the rest of the definition.",
                    color = c.subtext,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Text(
                    text = if (isZh) "Chat 控制协议保存在角色工作空间的 chat_protocol.md，保存时会一起写入。" else "Chat control is stored in role workspace chat_protocol.md and is saved together.",
                    color = c.subtext,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                if (onRestore != null) {
                    Button(
                        onClick = onRestore,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = c.red.copy(alpha = 0.15f)),
                    ) {
                        Text(str(R.string.role_edit_a2ea09), color = c.red, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            RoleIdentityEditorPanel(
                avatar = avatar,
                name = name,
                onNameChange = { name = it },
                description = description,
                onDescriptionChange = { description = it },
                isImageAvatar = isImageAvatar,
                onChooseImage = { imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                onResetAvatar = { avatar = RoleAvatarDefaults.forRoleId(initial.id) },
            )

            RoleModelBindingPanel(
                configSnapshot = configSnapshot,
                selectedGatewayId = selectedGatewayId,
                onGatewaySelect = { gateway ->
                    selectedGatewayId = gateway?.id.orEmpty()
                    selectedModel = gateway?.defaultChatModel().orEmpty()
                    gateway?.id?.let(onFetchGatewayModels)
                },
                selectedModel = selectedModel,
                onModelSelect = { selectedModel = it },
                gatewayModels = gatewayModels,
                gatewayModelsLoadingIds = gatewayModelsLoadingIds,
                availableModels = availableModels,
                modelsLoading = modelsLoading,
                onFetchModels = onFetchModels,
                onFetchGatewayModels = onFetchGatewayModels,
            )

            RoleEditSectionCard(str(R.string.role_edit_section_work)) {
                TaskType.values().forEachIndexed { index, taskType ->
                    if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))
                    val selected = taskType in selectedTaskTypes
                    RoleTaskTypeRow(
                        taskType = taskType,
                        selected = selected,
                        onToggle = {
                            selectedTaskTypes = if (selected) selectedTaskTypes - taskType else selectedTaskTypes + taskType
                        },
                    )
                }
            }

            RoleEditSectionCard(str(R.string.role_field_scheduler_keywords)) {
                OutlinedTextField(
                    value = schedulerKeywords,
                    onValueChange = { schedulerKeywords = it },
                    placeholder = { Text(str(R.string.role_field_scheduler_keywords_hint), fontSize = 12.sp, color = c.subtext) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = roleEditorTextFieldColors(c),
                )
            }

            RoleEditSectionTitle(if (isZh) "Chat 控制" else "Chat control")
            RoleChatControlEditor(
                expanded = chatControlExpanded,
                onToggleExpanded = { chatControlExpanded = !chatControlExpanded },
                rawExpanded = rawProtocolExpanded,
                onToggleRaw = {
                    if (!rawProtocolExpanded) {
                        protocolMarkdown = currentProtocolMarkdown()
                    } else {
                        loadProtocol(protocolMarkdown)
                    }
                    rawProtocolExpanded = !rawProtocolExpanded
                },
                protocolMarkdown = protocolMarkdown,
                onProtocolMarkdownChange = { protocolMarkdown = it },
                inputUnderstanding = inputUnderstanding,
                onInputUnderstandingChange = { inputUnderstanding = it },
                contextReading = contextReading,
                onContextReadingChange = { contextReading = it },
                memoryPolicy = memoryPolicy,
                onMemoryPolicyChange = { memoryPolicy = it },
                skillPolicy = skillPolicy,
                onSkillPolicyChange = { skillPolicy = it },
                executionPreference = executionPreference,
                onExecutionPreferenceChange = { executionPreference = it },
                responsePolicy = responsePolicy,
                onResponsePolicyChange = { responsePolicy = it },
                persistencePolicy = persistencePolicy,
                onPersistencePolicyChange = { persistencePolicy = it },
                controlSummary = buildRoleControlPreview(
                    role = draftRole(),
                    markdown = if (rawProtocolExpanded) protocolMarkdown else currentProtocolMarkdown(),
                    skills = allSkills,
                    isZh = isZh,
                ),
            )

            TextButton(onClick = { advancedExpanded = !advancedExpanded }, modifier = Modifier.align(Alignment.Start)) {
                Text(
                    text = if (advancedExpanded) str(R.string.role_edit_advanced_hide) else str(R.string.role_edit_advanced_show),
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (advancedExpanded) {
                RoleEditSectionTitle(str(R.string.role_edit_section_advanced))

                OutlinedTextField(
                    value = addendum,
                    onValueChange = { addendum = it },
                    label = { Text(str(R.string.role_field_system_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    colors = roleEditorTextFieldColors(c),
                )

                if (allSkills.isNotEmpty()) {
                    RoleEditSectionCard(str(R.string.role_field_skills)) {
                        allSkills.forEachIndexed { index, skill ->
                            if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 46.dp))
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
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(if (selected) c.text else c.cardAlt, RoundedCornerShape(12.dp))
                                        .border(0.5.dp, if (selected) c.text else c.border, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = c.bg, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(skill.nameZh ?: skill.name, fontSize = 14.sp, lineHeight = 18.sp, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(skill.id, fontSize = 11.sp, lineHeight = 14.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

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
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoleEditTopBar(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onBack)
                    .padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
                Text(if (isZh) "退出" else "Exit", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.text)
                    .clickable(onClick = onSave)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.bg, modifier = Modifier.size(14.dp))
                Text(str(R.string.role_save), color = c.bg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
    }
}

@Composable
private fun RoleIdentityEditorPanel(
    avatar: String,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    isImageAvatar: Boolean,
    onChooseImage: () -> Unit,
    onResetAvatar: () -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    val displayName = name.trim().ifBlank { str(R.string.role_card_unnamed) }
    val displayDescription = description.trim().ifBlank {
        if (isZh) "描述这个角色负责什么、擅长什么。" else "Describe what this role owns and does well."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (isZh) "角色形象" else "Role identity",
            color = c.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .background(c.cardAlt, RoundedCornerShape(18.dp))
                    .clickable { onChooseImage() },
                contentAlignment = Alignment.Center,
            ) {
                GradientAvatar(
                    avatar = avatar,
                    size = 70.dp,
                    color = c.text,
                    shape = RoundedCornerShape(16.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.text),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = c.bg, modifier = Modifier.size(15.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = displayName,
                    color = c.text,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = displayDescription,
                    color = c.subtext,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleIdentityPillButton(
                        text = if (isZh) "选择图片" else "Choose",
                        dark = false,
                        onClick = onChooseImage,
                        modifier = Modifier.weight(1f),
                    )
                    RoleIdentityPillButton(
                        text = if (isZh) "默认" else "Default",
                        dark = true,
                        enabled = isImageAvatar,
                        onClick = onResetAvatar,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(str(R.string.role_field_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = roleEditorTextFieldColors(c),
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(str(R.string.role_field_description)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
            colors = roleEditorTextFieldColors(c),
        )
    }
}

@Composable
private fun roleEditorTextFieldColors(c: com.mobileclaw.ui.ClawColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = c.text.copy(alpha = 0.55f),
    unfocusedBorderColor = c.border,
    focusedTextColor = c.text,
    unfocusedTextColor = c.text,
    cursorColor = c.text,
    focusedContainerColor = c.card,
    unfocusedContainerColor = c.card,
    focusedLabelColor = c.text,
    unfocusedLabelColor = c.subtext,
)

@Composable
private fun RoleIdentityPillButton(
    text: String,
    dark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    val bg = when {
        !enabled -> c.cardAlt.copy(alpha = 0.55f)
        dark -> c.cardAlt
        else -> c.text
    }
    val fg = when {
        !enabled -> c.subtext.copy(alpha = 0.55f)
        dark -> c.text
        else -> c.bg
    }
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(0.5.dp, if (dark || !enabled) c.border else c.text, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoleEditModeSwitch(
    selected: RoleEditMode,
    onSelect: (RoleEditMode) -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (c.isDark) c.surface else Color(0xFFF1F1EE))
            .border(1.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoleEditMode.values().forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) c.text else Color.Transparent)
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (mode) {
                        RoleEditMode.QUICK -> if (isZh) "快速编辑" else "Quick edit"
                        RoleEditMode.FULL -> if (isZh) "完整定义" else "Full definition"
                    },
                    color = if (active) c.bg else c.subtext,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RoleEditSectionTitle(text: String) {
    val c = LocalClawColors.current
    Text(
        text = text,
        color = c.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun RoleEditSectionCard(
    title: String,
    content: @Composable () -> Unit,
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
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleModelBindingPanel(
    configSnapshot: ConfigSnapshot,
    selectedGatewayId: String,
    onGatewaySelect: (GatewayConfig?) -> Unit,
    selectedModel: String,
    onModelSelect: (String) -> Unit,
    gatewayModels: Map<String, List<String>>,
    gatewayModelsLoadingIds: Set<String>,
    availableModels: List<String>,
    modelsLoading: Boolean,
    onFetchModels: () -> Unit,
    onFetchGatewayModels: (String) -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    val gateways = configSnapshot.gateways
    val selectedGateway = gateways.firstOrNull { it.id == selectedGatewayId }
    val modelGateway = selectedGateway ?: configSnapshot.activeGateway
    val modelGatewayId = modelGateway?.id.orEmpty()
    val fetchedModels = gatewayModels[modelGatewayId].orEmpty()
    val configuredModels = modelGateway?.chatModelOptions().orEmpty()
    val fallbackModels = if (selectedGateway == null && fetchedModels.isEmpty()) availableModels else emptyList()
    val modelOptions = (fetchedModels + configuredModels + listOf(selectedModel) + fallbackModels)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val loadingModels = modelGatewayId in gatewayModelsLoadingIds || (selectedGateway == null && modelsLoading)
    var gatewayDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(modelGatewayId) {
        if (modelGatewayId.isNotBlank() && gatewayModels[modelGatewayId].isNullOrEmpty()) {
            onFetchGatewayModels(modelGatewayId)
        }
    }

    RoleEditSectionCard(if (isZh) "模型调用" else "Model routing") {
        ExposedDropdownMenuBox(
            expanded = gatewayDropdownExpanded,
            onExpandedChange = { gatewayDropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = when {
                    gateways.isEmpty() -> if (isZh) "还没有配置网关" else "No gateway configured"
                    selectedGateway != null -> selectedGateway.name
                    else -> if (isZh) "跟随默认网关" else "Follow default gateway"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text(if (isZh) "网关" else "Gateway") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gatewayDropdownExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(16.dp),
                colors = roleEditorTextFieldColors(c),
            )
            ExposedDropdownMenu(
                expanded = gatewayDropdownExpanded,
                onDismissRequest = { gatewayDropdownExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(if (isZh) "跟随默认网关" else "Follow default gateway", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(configSnapshot.activeGateway?.name.orEmpty().ifBlank { if (isZh) "使用全局当前配置" else "Use the current global config" }, fontSize = 11.sp, color = c.subtext)
                        }
                    },
                    onClick = {
                        onGatewaySelect(null)
                        gatewayDropdownExpanded = false
                    },
                )
                gateways.forEach { gateway ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(gateway.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(gateway.endpoint.hostLabel(), gateway.defaultChatModel().ifBlank { if (isZh) "默认模型" else "Default model" })
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = c.subtext,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = {
                            onGatewaySelect(gateway)
                            gatewayDropdownExpanded = false
                        },
                    )
                }
            }
        }

        if (gateways.isEmpty()) {
            Text(
                text = if (isZh) "请先在 AI 基础配置里添加 OpenAI 兼容网关。" else "Add an OpenAI-compatible gateway in AI basic settings first.",
                color = c.subtext,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            return@RoleEditSectionCard
        }

        when {
            loadingModels -> {
                OutlinedTextField(
                    value = if (isZh) "正在读取模型列表..." else "Loading models...",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(if (isZh) "模型" else "Model") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = roleEditorTextFieldColors(c),
                )
            }
            modelOptions.isNotEmpty() -> {
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedModel.ifBlank {
                            modelGateway?.defaultChatModel().orEmpty().ifBlank { if (isZh) "使用网关默认模型" else "Use gateway default" }
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isZh) "模型" else "Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(16.dp),
                        colors = roleEditorTextFieldColors(c),
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isZh) "使用网关默认模型" else "Use gateway default", fontSize = 13.sp) },
                            onClick = {
                                onModelSelect("")
                                modelDropdownExpanded = false
                            },
                        )
                        modelOptions.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    onModelSelect(model)
                                    modelDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = onModelSelect,
                        label = { Text(if (isZh) "模型 ID" else "Model ID") },
                        placeholder = { Text("gpt-4o", fontSize = 12.sp, color = c.subtext) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = roleEditorTextFieldColors(c),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (modelGatewayId.isNotBlank()) onFetchGatewayModels(modelGatewayId) else onFetchModels()
                        },
                    ) {
                        Text(str(R.string.role_edit_refresh), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val summaryGateway = selectedGateway?.name
            ?: configSnapshot.activeGateway?.name
            ?: if (isZh) "默认网关" else "Default gateway"
        val summaryModel = selectedModel.ifBlank {
            modelGateway?.defaultChatModel().orEmpty().ifBlank { if (isZh) "默认模型" else "Default model" }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.cardAlt)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.accent),
            )
            Text(
                text = "$summaryGateway · $summaryModel",
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun GatewayConfig.defaultChatModel(): String =
    capabilityModel("chat") ?: model

private fun GatewayConfig.chatModelOptions(): List<String> =
    listOfNotNull(capabilityModel("chat"), model.takeIf { it.isNotBlank() }).distinct()

private fun String.hostLabel(): String =
    removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .ifBlank { this }

@Composable
private fun RoleTaskTypeRow(
    taskType: TaskType,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (selected) c.text else c.cardAlt, RoundedCornerShape(12.dp))
                .border(0.5.dp, if (selected) c.text else c.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.bg, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(taskType.roleTaskLabel(), fontSize = 14.sp, lineHeight = 18.sp, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(taskType.roleTaskHint(), fontSize = 11.sp, lineHeight = 14.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoleChatControlEditor(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    rawExpanded: Boolean,
    onToggleRaw: () -> Unit,
    protocolMarkdown: String,
    onProtocolMarkdownChange: (String) -> Unit,
    inputUnderstanding: String,
    onInputUnderstandingChange: (String) -> Unit,
    contextReading: String,
    onContextReadingChange: (String) -> Unit,
    memoryPolicy: String,
    onMemoryPolicyChange: (String) -> Unit,
    skillPolicy: String,
    onSkillPolicyChange: (String) -> Unit,
    executionPreference: RoleExecutionPreference,
    onExecutionPreferenceChange: (RoleExecutionPreference) -> Unit,
    responsePolicy: String,
    onResponsePolicyChange: (String) -> Unit,
    persistencePolicy: String,
    onPersistencePolicyChange: (String) -> Unit,
    controlSummary: List<String>,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isZh) "角色如何驱动 Chat" else "How this role drives chat",
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isZh) "这会直接写入 chat_protocol.md，并参与每次对话执行计划。" else "Saved to chat_protocol.md and compiled into every chat run.",
                    color = c.subtext,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Text(
                text = if (expanded) if (isZh) "收起" else "Hide" else if (isZh) "编辑" else "Edit",
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.cardAlt)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            controlSummary.forEach { line ->
                Text(text = line, color = c.text, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        if (!expanded) return@Column

        TextButton(onClick = onToggleRaw, modifier = Modifier.align(Alignment.Start)) {
            Text(
                text = if (rawExpanded) {
                    if (isZh) "切回表单编辑" else "Use form editor"
                } else {
                    if (isZh) "编辑完整协议 Markdown" else "Edit full protocol Markdown"
                },
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (rawExpanded) {
            OutlinedTextField(
                value = protocolMarkdown,
                onValueChange = onProtocolMarkdownChange,
                label = { Text("chat_protocol.md") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 16,
                maxLines = 28,
                shape = RoundedCornerShape(16.dp),
                colors = roleEditorTextFieldColors(c),
            )
        } else {
            RoleExecutionPreferenceSelector(
                selected = executionPreference,
                onSelect = onExecutionPreferenceChange,
            )
            RoleProtocolField(
                label = if (isZh) "意图识别" else "Input understanding",
                value = inputUnderstanding,
                onValueChange = onInputUnderstandingChange,
                minLines = 3,
            )
            RoleProtocolField(
                label = if (isZh) "上下文读取" else "Context reading",
                value = contextReading,
                onValueChange = onContextReadingChange,
                minLines = 3,
            )
            RoleProtocolField(
                label = if (isZh) "记忆策略" else "Memory policy",
                value = memoryPolicy,
                onValueChange = onMemoryPolicyChange,
                minLines = 3,
            )
            RoleProtocolField(
                label = if (isZh) "工具/技能选择" else "Tool and skill policy",
                value = skillPolicy,
                onValueChange = onSkillPolicyChange,
                minLines = 4,
            )
            RoleProtocolField(
                label = if (isZh) "回复方式" else "Response policy",
                value = responsePolicy,
                onValueChange = onResponsePolicyChange,
                minLines = 3,
            )
            RoleProtocolField(
                label = if (isZh) "沉淀与持久化" else "Persistence policy",
                value = persistencePolicy,
                onValueChange = onPersistencePolicyChange,
                minLines = 3,
            )
        }
    }
}

@Composable
private fun RoleProtocolField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int,
) {
    val c = LocalClawColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        maxLines = (minLines + 3).coerceAtLeast(6),
        shape = RoundedCornerShape(16.dp),
        colors = roleEditorTextFieldColors(c),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = c.text),
    )
}

@Composable
private fun RoleExecutionPreferenceSelector(
    selected: RoleExecutionPreference,
    onSelect: (RoleExecutionPreference) -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isZh) "执行倾向" else "Execution preference",
            color = c.subtext,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(if (c.isDark) c.bg else Color(0xFFF1F1EE))
                .border(1.dp, c.border, RoundedCornerShape(999.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoleExecutionPreference.values().forEach { preference ->
                val active = selected == preference
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) c.text else Color.Transparent)
                        .clickable { onSelect(preference) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = preference.label(isZh),
                        color = if (active) c.bg else c.subtext,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun RoleExecutionPreference.label(isZh: Boolean): String = when (this) {
    RoleExecutionPreference.AUTO -> if (isZh) "自动" else "Auto"
    RoleExecutionPreference.DIRECT_FIRST -> if (isZh) "直接优先" else "Direct"
    RoleExecutionPreference.AGENT_FIRST -> if (isZh) "执行优先" else "Agent"
}

@Composable
private fun TaskType.roleTaskLabel(): String = when (this) {
    TaskType.PHONE_CONTROL -> str(R.string.role_task_phone)
    TaskType.WEB_RESEARCH -> str(R.string.role_task_web)
    TaskType.FILE_CREATE -> str(R.string.role_task_file)
    TaskType.APP_BUILD -> str(R.string.role_task_app)
    TaskType.IMAGE_GENERATION -> str(R.string.role_task_image)
    TaskType.VPN_CONTROL -> str(R.string.role_task_vpn)
    TaskType.SKILL_MANAGEMENT -> str(R.string.role_task_skill)
    TaskType.CODE_EXECUTION -> str(R.string.role_task_code)
    TaskType.CHAT,
    TaskType.GENERAL -> str(R.string.role_task_chat)
}

@Composable
private fun TaskType.roleTaskHint(): String = when (this) {
    TaskType.PHONE_CONTROL -> str(R.string.role_task_phone_hint)
    TaskType.WEB_RESEARCH -> str(R.string.role_task_web_hint)
    TaskType.FILE_CREATE -> str(R.string.role_task_file_hint)
    TaskType.APP_BUILD -> str(R.string.role_task_app_hint)
    TaskType.IMAGE_GENERATION -> str(R.string.role_task_image_hint)
    TaskType.VPN_CONTROL -> str(R.string.role_task_vpn_hint)
    TaskType.SKILL_MANAGEMENT -> str(R.string.role_task_skill_hint)
    TaskType.CODE_EXECUTION -> str(R.string.role_task_code_hint)
    TaskType.CHAT,
    TaskType.GENERAL -> str(R.string.role_task_chat_hint)
}

private fun defaultEditableChatProtocol(role: Role): String = """
# Chat Execution Protocol

## Runtime Contract
- Role id: ${role.id}
- Protocol version: 1

## Input Understanding
- 先判断用户真实目标：闲聊、追问、修改当前产物、还是执行动作。
- “继续”“重试”“不对”“改一下”等短句必须结合最近对话和当前工作区理解。
- 不把角色当成语气包；角色是一套工作身份、执行方法和记忆策略。

## Context Reading
- 优先读取 core.md、memory.md、model.md 和 chat_protocol.md。
- 需要技能时读取 skills.md / skill_index.md，再按任务选择工具。
- 最近对话用于理解当前指令，不覆盖最新用户意图。

## Memory Policy
- 只沉淀稳定偏好、重要事件节点、角色工作习惯和可复用经验。
- 临时情绪、一次性状态和过期信息不写入长期记忆。

## Skill Policy
- 所有技能都可以按需发现和读取；先判断任务是否真的需要工具。
- 普通问答直接回答；需要行动时进入工具/agent 流程。
- ${role.forcedSkillIds.joinToString(", ").ifBlank { "当前角色没有强制技能；按任务需要选择。" }}

## Response Policy
- 少罗列能力清单，优先回答用户当前目标。
- 执行类任务说明做了什么、结果在哪里、风险和下一步。

## Persistence Policy
- 重要任务完成后追加 journal.md。
- 角色偏好、工作链路、回复习惯变化时更新 memory.md 或 core.md。
""".trimIndent() + "\n"

private fun inferExecutionPreference(protocol: RoleExecutionProtocol): RoleExecutionPreference {
    val text = listOf(protocol.skillPolicy, protocol.responsePolicy).joinToString("\n").lowercase()
    return when {
        listOf("优先 agent", "优先工具", "进入 agent", "执行类任务进入 agent", "需要行动时进入 agent", "agent first", "prefer agent").any { text.contains(it) } ->
            RoleExecutionPreference.AGENT_FIRST
        listOf("直接回答优先", "普通问答直接回答", "简单问题直接回答", "闲聊直接回答", "direct chat first", "prefer direct").any { text.contains(it) } ->
            RoleExecutionPreference.DIRECT_FIRST
        else -> RoleExecutionPreference.AUTO
    }
}

private fun applyExecutionPreferenceToSkillPolicy(
    value: String,
    preference: RoleExecutionPreference,
): String = replaceManagedPreferenceLine(
    value = value,
    marker = "Execution preference",
    next = when (preference) {
        RoleExecutionPreference.AUTO -> ""
        RoleExecutionPreference.DIRECT_FIRST -> "- Execution preference: 普通问答直接回答；需要行动时进入 agent。"
        RoleExecutionPreference.AGENT_FIRST -> "- Execution preference: 优先 agent；执行类任务进入 agent；需要行动时进入工具。"
    },
)

private fun applyExecutionPreferenceToResponsePolicy(
    value: String,
    preference: RoleExecutionPreference,
): String = replaceManagedPreferenceLine(
    value = value,
    marker = "Response preference",
    next = when (preference) {
        RoleExecutionPreference.AUTO -> ""
        RoleExecutionPreference.DIRECT_FIRST -> "- Response preference: 直接回答优先，少展示内部过程。"
        RoleExecutionPreference.AGENT_FIRST -> "- Response preference: 执行优先，完成后汇报结果和关键过程。"
    },
)

private fun replaceManagedPreferenceLine(value: String, marker: String, next: String): String {
    val kept = value
        .lines()
        .filterNot { it.contains(marker, ignoreCase = true) }
        .joinToString("\n")
        .trimEnd()
    return listOf(kept, next)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()
}

private fun buildRoleControlPreview(
    role: Role,
    markdown: String,
    skills: List<SkillMeta>,
    isZh: Boolean,
): List<String> {
    val protocol = RoleExecutionProtocolParser.parse(role.id, markdown)
    val profile = RoleRuntimeProfile(
        role = role,
        workspace = RoleWorkspaceSnapshot(
            roleId = role.id,
            rootPath = "",
            core = "",
            skills = "",
            memory = "",
            model = "",
            chatProtocol = markdown,
        ),
        protocol = protocol,
        skills = skills,
        workspacePrompt = "",
        compiledPrompt = "",
    )
    val plan = RoleChatControlPlanCompiler.compile(profile)
    val readFiles = plan.contextPolicy.readRoleFiles.joinToString(", ").ifBlank { "none" }
    val preferredTools = plan.toolPolicy.preferredToolIds.joinToString(", ").ifBlank {
        if (isZh) "按任务选择" else "task-based"
    }
    val modeHint = plan.executionModeHint?.name ?: if (isZh) "自动" else "auto"
    return if (isZh) {
        listOf(
            "执行倾向：$modeHint",
            "意图理解：短句 ${plan.intentPolicy.shortFollowUpMode}；产物 ${plan.intentPolicy.artifactReferenceMode}",
            "回复方式：${plan.responsePolicy.style}；UI ${if (plan.responsePolicy.allowUiBlocks) "允许" else "默认关闭"}",
            "会读取：$readFiles",
            "最近对话：${if (plan.contextPolicy.includeRecentMessages) "纳入" else "按需/默认不强制"}；用户记忆：${if (plan.contextPolicy.includeUserMemory) "纳入" else "不读取"}",
            "技能选择：$preferredTools；MCP：${if (plan.toolPolicy.allowMcp) "允许" else "禁用"}",
            "记忆写入：角色 ${if (plan.persistencePolicy.allowRoleMemoryWrite) "允许" else "不主动"} / 用户 ${if (plan.persistencePolicy.allowUserMemoryWrite) "允许" else "禁用"}",
            "过程展示：工具 ${if (plan.visibilityPolicy.showTimelineForToolCalls) "展示" else "低打扰"} / 记忆 ${if (plan.visibilityPolicy.showTimelineForMemoryWrites) "展示" else "隐藏"}",
        )
    } else {
        listOf(
            "Execution hint: $modeHint",
            "Intent: short ${plan.intentPolicy.shortFollowUpMode}; artifact ${plan.intentPolicy.artifactReferenceMode}",
            "Response: ${plan.responsePolicy.style}; UI ${if (plan.responsePolicy.allowUiBlocks) "allowed" else "off by default"}",
            "Reads: $readFiles",
            "Recent messages: ${if (plan.contextPolicy.includeRecentMessages) "included" else "not forced"}; user memory: ${if (plan.contextPolicy.includeUserMemory) "included" else "off"}",
            "Tools: $preferredTools; MCP: ${if (plan.toolPolicy.allowMcp) "allowed" else "disabled"}",
            "Memory writes: role ${if (plan.persistencePolicy.allowRoleMemoryWrite) "allowed" else "passive"} / user ${if (plan.persistencePolicy.allowUserMemoryWrite) "allowed" else "disabled"}",
            "Timeline: tools ${if (plan.visibilityPolicy.showTimelineForToolCalls) "shown" else "quiet"} / memory ${if (plan.visibilityPolicy.showTimelineForMemoryWrites) "shown" else "hidden"}",
        )
    }
}
