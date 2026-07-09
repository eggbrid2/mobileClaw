package com.mobileclaw.agent

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.capabilityEndpoint
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.llm.RoleModelResolver
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.storage.AtomicTextFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RoleWorkspaceSnapshot(
    val roleId: String,
    val rootPath: String,
    val core: String,
    val skills: String,
    val memory: String,
    val model: String,
    val chatProtocol: String,
)

class RoleWorkspaceStore(private val context: Context) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val ioLock = Any()
    private val rootDir: File get() = context.filesDir.resolve("role_workspaces").also { it.mkdirs() }

    fun ensure(role: Role, skills: List<SkillMeta> = emptyList()): RoleWorkspaceSnapshot = synchronized(ioLock) {
        val dir = roleDir(role.id).also { it.mkdirs() }
        val core = File(dir, CORE_MD)
        val skill = File(dir, SKILLS_MD)
        val memory = File(dir, MEMORY_MD)
        val model = File(dir, MODEL_MD)
        val chatProtocol = File(dir, CHAT_PROTOCOL_MD)
        val journal = File(dir, JOURNAL_MD)

        if (!core.exists()) AtomicTextFile.write(core, defaultCore(role))
        if (!skill.exists()) AtomicTextFile.write(skill, defaultSkills(role, skills))
        if (!memory.exists()) AtomicTextFile.write(memory, defaultMemory(role))
        if (!model.exists()) AtomicTextFile.write(model, defaultModel(role))
        if (!chatProtocol.exists()) AtomicTextFile.write(chatProtocol, defaultChatProtocol(role))
        if (!journal.exists()) AtomicTextFile.write(journal, "# ${role.name.ifBlank { role.id }} 工作日志\n\n")
        migrateDefaultRoleSections(role, core, skill, memory, chatProtocol)

        if (skills.isNotEmpty()) {
            refreshSkillIndex(role.id, skills)
        }
        snapshotLocked(role.id)
    }

    fun snapshot(role: Role, skills: List<SkillMeta> = emptyList()): RoleWorkspaceSnapshot =
        ensure(role, skills)

    fun promptBlock(role: Role, skills: List<SkillMeta> = emptyList()): String {
        val snap = snapshot(role, skills)
        return promptBlock(snap, skills)
    }

    fun promptBlock(
        snap: RoleWorkspaceSnapshot,
        skills: List<SkillMeta> = emptyList(),
        includeChatProtocol: Boolean = true,
    ): String {
        val allSkillsNote = if (skills.isNotEmpty()) {
            val grouped = skills
                .filterNot { it.internalTool }
                .groupBy { it.categories.firstOrNull()?.name ?: "OTHER" }
                .toSortedMap()
                .map { (category, metas) ->
                    "$category: " + metas.sortedBy { it.id }.joinToString(", ") { it.id }
                }
                .joinToString("\n")
            "\n## 全量技能索引\n$grouped\n"
        } else ""
        val chatProtocolSection = if (includeChatProtocol) {
            """

### chat_protocol.md
${snap.chatProtocol.take(2200)}
""".trimEnd()
        } else ""
        return """
## Role Workspace
Role id: ${snap.roleId}
Workspace path: ${snap.rootPath}

### core.md
${snap.core.take(1800)}

### memory.md
${snap.memory.take(1800)}

### model.md
${snap.model.take(1400)}
$chatProtocolSection

### skills.md
${snap.skills.take(2200)}
$allSkillsNote
Rules:
- You are operating as this role, not merely speaking in this role's style.
- Read this role workspace before making durable decisions about the role.
- Follow `chat_protocol.md` as the role's working protocol for input understanding, context reading, memory, skills, response, and persistence.
- Use `role_workspace` to read, write, append, or refresh role files when the role learns something durable.
- All installed skills are available for discovery. Choose tools by task need, then read skill details on demand instead of guessing.
""".trimIndent()
    }

    fun read(roleId: String, fileName: String): String? = synchronized(ioLock) {
        AtomicTextFile.readOrNull(resolveFile(roleId, fileName))
    }

    fun write(roleId: String, fileName: String, content: String): String = synchronized(ioLock) {
        val file = resolveFile(roleId, fileName)
        AtomicTextFile.write(file, content)
        file.relativeTo(rootDir).path
    }

    fun append(roleId: String, fileName: String, content: String): String = synchronized(ioLock) {
        val file = resolveFile(roleId, fileName)
        val previous = AtomicTextFile.readOrNull(file).orEmpty()
        val next = buildString {
            append(previous)
            if (previous.isNotBlank() && !previous.endsWith("\n")) append('\n')
            append(content.trimEnd())
            append("\n")
        }
        AtomicTextFile.write(file, next)
        file.relativeTo(rootDir).path
    }

    fun list(roleId: String): List<String> = synchronized(ioLock) {
        roleDir(roleId).also { it.mkdirs() }
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(roleDir(roleId)).path }
            .toList()
            .sorted()
    }

    fun refreshSkillIndex(roleId: String, skills: List<SkillMeta>): String = synchronized(ioLock) {
        val file = File(roleDir(roleId).also { it.mkdirs() }, SKILL_INDEX_MD)
        AtomicTextFile.write(file, renderSkillIndex(skills))
        file.relativeTo(rootDir).path
    }

    fun recordModelConfig(role: Role, snapshot: ConfigSnapshot, source: String = "runtime"): String = synchronized(ioLock) {
        roleDir(role.id).also { it.mkdirs() }
        val modelInfo = roleModelConfigMap(role, snapshot, source)
        val md = renderModelConfigMarkdown(modelInfo)
        AtomicTextFile.write(File(roleDir(role.id), MODEL_MD), md)
        AtomicTextFile.write(File(roleDir(role.id), MODEL_CONFIG_JSON), gson.toJson(modelInfo))
        File(roleDir(role.id), MODEL_MD).relativeTo(rootDir).path
    }

    private fun snapshotLocked(roleId: String): RoleWorkspaceSnapshot =
        RoleWorkspaceSnapshot(
            roleId = roleId,
            rootPath = roleDir(roleId).absolutePath,
            core = AtomicTextFile.readOrNull(File(roleDir(roleId), CORE_MD)).orEmpty(),
            skills = AtomicTextFile.readOrNull(File(roleDir(roleId), SKILLS_MD)).orEmpty(),
            memory = AtomicTextFile.readOrNull(File(roleDir(roleId), MEMORY_MD)).orEmpty(),
            model = AtomicTextFile.readOrNull(File(roleDir(roleId), MODEL_MD)).orEmpty(),
            chatProtocol = AtomicTextFile.readOrNull(File(roleDir(roleId), CHAT_PROTOCOL_MD)).orEmpty(),
        )

    private fun roleDir(roleId: String): File = File(rootDir, sanitize(roleId))

    private fun resolveFile(roleId: String, fileName: String): File {
        val safePath = fileName.trim().ifBlank { CORE_MD }
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
            .ifBlank { CORE_MD }
        return File(roleDir(roleId).also { it.mkdirs() }, safePath)
    }

    private fun defaultCore(role: Role): String = """
# ${role.name.ifBlank { role.id }}

## 定位
${role.description.ifBlank { "这个角色负责在 MobileClaw 中承担一个稳定的 AI 工作身份。" }}

## 执行原则
${role.systemPromptAddendum.ifBlank { "- 先判断任务目标，再选择最小可行能力完成。\n- 不把角色理解成单纯聊天口吻，而是以这个工作身份执行任务。" }}

## 工作方法
${roleCorePlaybook(role)}

## 工作边界
- Role id: ${role.id}
- Preferred task types: ${role.preferredTaskTypes.joinToString(", ").ifBlank { "GENERAL" }}
- Keywords: ${role.keywords.joinToString(", ").ifBlank { "none" }}
- Forced skills: ${role.forcedSkillIds.joinToString(", ").ifBlank { "none" }}
- Model binding: ${roleModelBindingText(role)}
""".trimIndent() + "\n"

    private fun defaultSkills(role: Role, skills: List<SkillMeta>): String = """
# 技能使用说明

## 角色默认技能
${role.forcedSkillIds.joinToString("\n") { "- $it" }.ifBlank { "- 暂无强制技能。根据任务按需发现和使用技能。" }}

## 按需读取原则
- 你可以发现所有已安装技能，但只在任务需要时调用。
- 优先使用最贴近任务目标的技能，不要因为能调用就调用。
- 如果不确定技能用途，先查看 `skill_index.md` 或调用技能/市场相关工具获取详情。
- 角色不被技能白名单限制；聊天、手机控制、文件、页面、联网、MCP、工作区都可以按任务需要组合。
- 创建类任务优先使用持久产物工具；修改类任务必须先确认目标产物，再更新已有产物，不要误创建新产物。

## 技能选择习惯
${roleSkillPlaybook(role)}

## 当前技能索引概览
${renderCompactSkillGroups(skills)}
""".trimIndent() + "\n"

    private fun defaultMemory(role: Role): String = """
# 角色记忆

## 稳定偏好
- 这个角色的长期偏好、禁忌、常用工作方式写在这里。

## 记忆触发
- 用户明确表达长期偏好、个人信息、重要目标、禁忌或工作习惯时，更新这里。
- 角色在执行中形成稳定经验，比如某类任务总要先检查某文件、某模型不适合某类任务，也更新这里。
- 临时状态、一次性结果、短期情绪不要写入长期记忆；写入 journal.md。

## 任务经验
- 完成重要任务后，把可复用经验追加到这里。

## 用户协作习惯
- 记录用户希望这个角色如何工作，而不是记录一次性闲聊。
""".trimIndent() + "\n"

    private fun defaultModel(role: Role): String = """
# 模型与网关配置

## 当前记录
- 尚未记录实际运行配置。
- Role model binding: ${roleModelBindingText(role)}

## 说明
- 这里记录角色最近一次实际使用的模型、网关和能力模型配置。
- API Key 不写入角色目录；角色只记录 gateway id/name 与 masked 状态，后续执行仍从全局安全配置读取密钥。
- 群聊、狼人杀等多角色玩法可以用这里区分每个角色的“脑子”和调用偏好。
""".trimIndent() + "\n"

    private fun defaultChatProtocol(role: Role): String = """
# Chat Execution Protocol

## Runtime Contract
- Role id: ${role.id}
- Protocol version: 1
- This file defines how the role drives MobileClaw Chat Runtime stages.

## Input Understanding
- 先判断用户是在闲聊、追问、修改当前产物，还是要求执行一个动作。
- 短句如“继续”“重试”“不对”“改一下”必须结合最近对话、活动工作区和当前角色任务理解。
- 不把角色理解成语气包；角色是一套工作身份和执行方法。

## Context Reading
- 优先读取 core.md 理解角色定位和边界。
- 读取 memory.md 获取长期偏好、协作习惯和可复用经验。
- 读取 model.md 获取角色最近使用的模型和网关配置画像。
- 需要技能时按需查看 skills.md 和 skill_index.md，不要凭记忆猜测技能能力。
- 工作区和 artifact 上下文只用于解决当前任务，不要覆盖最新用户意图。

## Memory Policy
- 只沉淀稳定偏好、重要事件节点、角色工作习惯和可复用任务经验。
- 一次性闲聊、临时情绪、过期状态不写入长期记忆。
- 如果学到关于用户或角色的重要事实，优先追加 memory.md；如果只是一次任务过程，写入 journal.md。

## Skill Policy
- 所有技能都可以按需发现和读取，但必须先判断任务是否真的需要技能。
- 角色可以跨聊天、文件、页面、手机操作、联网、MCP、系统配置等能力域执行；不要因为角色类型或聊天入口限制技能选择。
- 普通问答可以直接回答；一旦用户目标需要行动，就自主进入工具/agent 流程。
- ${role.forcedSkillIds.joinToString(", ").ifBlank { "当前角色没有强制技能；按任务需要选择。" }}
- ${roleProtocolHints(role)}

## Response Policy
- 普通聊天直接、清楚地回应用户。
- 执行类任务说明做了什么、结果在哪里、还有什么风险或下一步。
- 如果需要写入记忆或角色文件，应在完成任务后沉淀，不要把内部文件操作变成冗长解释。

## Persistence Policy
- 重要任务完成后追加 journal.md，记录时间、目标、结果和可复用经验。
- 角色偏好、工作链路、回复习惯变化时更新 memory.md 或 core.md。
- 模型和网关配置由运行时写入 model.md / model_config.json，不在本文件保存密钥。
""".trimIndent() + "\n"

    private fun migrateDefaultRoleSections(
        role: Role,
        core: File,
        skills: File,
        memory: File,
        chatProtocol: File,
    ) {
        appendSectionIfMissing(core, "## 工作方法", "\n## 工作方法\n${roleCorePlaybook(role)}\n")
        appendSectionIfMissing(
            skills,
            "## 技能选择习惯",
            "\n## 技能选择习惯\n${roleSkillPlaybook(role)}\n",
        )
        appendSectionIfMissing(
            memory,
            "## 记忆触发",
            """

## 记忆触发
- 用户明确表达长期偏好、个人信息、重要目标、禁忌或工作习惯时，更新这里。
- 角色在执行中形成稳定经验，比如某类任务总要先检查某文件、某模型不适合某类任务，也更新这里。
- 临时状态、一次性结果、短期情绪不要写入长期记忆；写入 journal.md。
""".trimEnd() + "\n",
        )
        appendSectionIfMissing(
            chatProtocol,
            "## Role-Specific Runtime Hint",
            "\n## Role-Specific Runtime Hint\n- ${roleProtocolHints(role)}\n",
        )
    }

    private fun appendSectionIfMissing(file: File, marker: String, section: String) {
        val current = AtomicTextFile.readOrNull(file).orEmpty()
        if (current.contains(marker)) return
        AtomicTextFile.write(file, buildString {
            append(current.trimEnd())
            append("\n")
            append(section.trimEnd())
            append("\n")
        })
    }

    private fun roleCorePlaybook(role: Role): String = when (role.id) {
        "coder" -> """
- 先读现有代码和错误上下文，再决定修改点。
- 对编译、运行、测试、日志类任务，优先复现或定位，再做小范围修复。
- 修改后尽量运行最相关的编译或测试命令，并把关键输出反馈给用户。
- 保持用户未要求的改动不动，遇到脏工作区时只处理本任务相关文件。
""".trimIndent()
        "web_agent" -> """
- 先判断信息是否可能过期；过期或高风险信息必须联网确认。
- 收集来源时优先官方、原始资料和可信媒体，避免只给单一来源结论。
- 回答要区分事实、推断和不确定性，并保留用户能继续查看的链接或出处。
""".trimIndent()
        "phone_operator" -> """
- 使用 observe -> act -> verify 循环，先看屏幕再点击。
- 每次只做一个清晰动作，动作后验证界面是否达到预期。
- 遇到权限、弹窗、登录或网络异常时先说明状态，再选择可恢复路径。
""".trimIndent()
        "creator" -> """
- 先判断用户要的是聊天内轻量 UI、持久原生页面、MiniAPP、图片还是文件。
- 新建产物必须有唯一身份；修改产物必须先确认目标产物和需要保留的功能。
- 已展示的 UI 或产物不视为可丢弃草稿，除非用户明确要求删除或替换。
- 产物完成后打开或说明位置，并沉淀可复用的设计/修复经验。
""".trimIndent()
        "skill_admin" -> """
- 先盘点当前技能和市场状态，再决定安装、修复或整理。
- 技能变更后要确认入口、参数、触发说明和可用性。
- 对外部技能/MCP 要区分公开可用、需要 token、移动端不可运行三类情况。
""".trimIndent()
        "vpn_operator" -> """
- 先检查当前配置、订阅、节点和连接状态，再修改。
- 操作 VPN 要尽量保留原有配置，避免覆盖用户已有订阅。
- 诊断时按网络可达性、订阅解析、节点延迟、系统 VPN 状态逐层排查。
""".trimIndent()
        else -> """
- 先理解用户真实目标，再选择聊天回答、工具执行、工作区读写或角色记忆。
- 简单问题直接回答；涉及行动、文件、页面、手机、联网、MCP 时进入执行流程。
- 每次完成后只沉淀真正长期有用的信息。
""".trimIndent()
    }

    private fun roleSkillPlaybook(role: Role): String = when (role.id) {
        "coder" -> """
- 文件/代码：读文件、搜索、修改、运行构建测试。
- 联网：查官方文档、依赖版本、错误原因。
- 工作区：把关键实现计划、架构结论和复盘写入对应工作区。
""".trimIndent()
        "web_agent" -> """
- 搜索/浏览：动态信息先搜索，具体页面再抓取内容。
- 文件：可生成摘要、报告、表格或引用清单。
- MCP：可接入公开 MCP 做结构化检索，但不要假设需要注册的服务可在移动端运行。
""".trimIndent()
        "phone_operator" -> """
- 视觉：see_screen 优先，截图作为补充。
- 操作：tap、scroll、input、back、launch 等动作后必须验证。
- 记忆：常用 app 包名、用户偏好路径可沉淀。
""".trimIndent()
        "creator" -> """
- 原生页面：ui_builder，适合设置页、表单、仪表盘、数据页。
- MiniAPP：app_manager，适合 HTML/JS、游戏、Canvas、Python/SQLite/WebView 运行时。
- 文件/文档：对应文档、表格、PDF、图片技能；不要在聊天里塞原始代码替代产物。
""".trimIndent()
        "skill_admin" -> """
- skill_market/skill 管理：先 list/browse，再 install/update/test。
- role_workspace：把角色可复用的技能习惯写入 skills.md 或 memory.md。
- MCP：记录服务能力、认证要求、移动端可用性和失败原因。
""".trimIndent()
        "vpn_operator" -> """
- vpn_control：开关、订阅、节点选择、延迟测试。
- phone/web/file：必要时配合检查系统状态、导入订阅、读取配置。
- 诊断结论要说明当前层级：配置、节点、系统权限、网络出口。
""".trimIndent()
        else -> """
- 优先使用能直接完成目标的最小技能组合。
- 多工具任务先规划，再按步骤执行和验证。
- 需要长期保留的信息写入工作区，而不是只留在聊天气泡。
""".trimIndent()
    }

    private fun roleProtocolHints(role: Role): String = when (role.id) {
        "creator" -> "创建持久页面/应用时不要先输出 embedded ui block 作为最终结果；使用 ui_builder/app_manager，并保留每次产物展示的唯一实例。"
        "coder" -> "代码任务要把 chat 流程拆成：理解 -> 定位 -> 修改 -> 验证 -> 汇报；不要跳过定位直接改。"
        "phone_operator" -> "手机控制任务要把每一步的屏幕观察和动作结果纳入下一步判断，避免连续盲点。"
        "web_agent" -> "联网任务要把来源可信度纳入回答，不确定时说明缺口。"
        "skill_admin" -> "技能/MCP 任务要先说明移动端可用性、认证要求和失败恢复策略。"
        "vpn_operator" -> "VPN 任务要避免隐式覆盖配置，所有订阅/节点变更都应保留可回退信息。"
        else -> "执行流程应按任务复杂度自适应：简单直接答，复杂任务拆步执行并在关键节点沉淀。"
    }

    private fun roleModelConfigMap(role: Role, snapshot: ConfigSnapshot, source: String): Map<String, Any?> {
        val binding = role.effectiveModelBinding()
        val resolved = RoleModelResolver.resolve(role, snapshot)
        val gateway = if (resolved.localModelId.isNotBlank()) {
            null
        } else {
            snapshot.gateways.firstOrNull { resolved.gatewayId.isNotBlank() && it.id == resolved.gatewayId }
                ?: snapshot.gateways.firstOrNull { resolved.gatewayName.isNotBlank() && it.name.equals(resolved.gatewayName, ignoreCase = true) }
                ?: snapshot.activeGateway
        }
        val effectiveRoleModel = when {
            resolved.localModelId.isNotBlank() -> "local:${resolved.localModelId.removePrefix("local:")}"
            resolved.model.isNotBlank() -> resolved.model
            else -> snapshot.model
        }
        val capabilities = listOf("chat", "image", "video", "embedding").associateWith { type ->
            mapOf(
                "model" to (gateway?.capabilityModel(type) ?: ""),
                "endpoint" to maskEndpoint(gateway?.capabilityEndpoint(type).orEmpty()),
                "enabled" to (gateway?.capabilities.orEmpty().firstOrNull { it.type.equals(type, ignoreCase = true) }?.enabled ?: false),
            )
        }
        return mapOf(
            "updatedAt" to nowText(),
            "source" to source,
            "role" to mapOf(
                "id" to role.id,
                "name" to role.name,
                "modelOverride" to role.modelOverride,
                "modelBinding" to binding?.let {
                    mapOf(
                        "gatewayId" to it.gatewayId,
                        "gatewayName" to it.gatewayName,
                        "model" to it.model,
                        "localModelId" to it.localModelId,
                    )
                },
                "modelBindingSummary" to roleModelBindingText(role),
            ),
            "runtime" to mapOf(
                "effectiveModel" to effectiveRoleModel,
                "chatModel" to snapshot.chatModel,
                "embeddingModel" to snapshot.embeddingModel,
                "localModelEnabled" to snapshot.localModelEnabled,
                "localNativeOnly" to snapshot.localNativeOnly,
                "localToolCallingEnabled" to snapshot.localToolCallingEnabled,
                "localModelId" to snapshot.localModelId,
                "roleInheritsDefault" to resolved.inheritedDefault,
                "roleGatewayId" to resolved.gatewayId,
                "roleGatewayName" to resolved.gatewayName,
                "roleModel" to resolved.model,
                "roleLocalModelId" to resolved.localModelId,
            ),
            "gateway" to mapOf(
                "id" to gateway?.id,
                "name" to gateway?.name,
                "endpoint" to maskEndpoint(gateway?.endpoint.orEmpty()),
                "apiKey" to maskSecret(gateway?.apiKey.orEmpty()),
                "model" to gateway?.model,
                "embeddingModel" to gateway?.embeddingModel,
                "supportsMultimodal" to gateway?.supportsMultimodal,
            ),
            "capabilities" to capabilities,
        )
    }

    private fun renderModelConfigMarkdown(info: Map<String, Any?>): String {
        val runtime = info["runtime"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val gateway = info["gateway"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val role = info["role"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val capabilities = info["capabilities"] as? Map<*, *> ?: emptyMap<String, Any?>()
        fun textOrNone(value: Any?): String =
            value?.toString()?.takeIf { it.isNotBlank() } ?: "none"
        return buildString {
            appendLine("# 模型与网关配置")
            appendLine()
            appendLine("## 最近一次运行")
            appendLine("- Updated at: ${info["updatedAt"]}")
            appendLine("- Source: ${info["source"]}")
            appendLine("- Role: ${role["name"]} (${role["id"]})")
            appendLine("- Role model binding: ${role["modelBindingSummary"] ?: role["modelOverride"] ?: "none"}")
            appendLine()
            appendLine("## Effective Model")
            appendLine("- Effective model: ${runtime["effectiveModel"]}")
            appendLine("- Inherits global default: ${runtime["roleInheritsDefault"]}")
            appendLine("- Role gateway id: ${textOrNone(runtime["roleGatewayId"])}")
            appendLine("- Role gateway name: ${textOrNone(runtime["roleGatewayName"])}")
            appendLine("- Role model: ${textOrNone(runtime["roleModel"])}")
            appendLine("- Role local model id: ${textOrNone(runtime["roleLocalModelId"])}")
            appendLine("- Chat model: ${runtime["chatModel"]}")
            appendLine("- Embedding model: ${runtime["embeddingModel"]}")
            appendLine("- Local enabled: ${runtime["localModelEnabled"]}")
            appendLine("- Local native only: ${runtime["localNativeOnly"]}")
            appendLine("- Local tool calling: ${runtime["localToolCallingEnabled"]}")
            appendLine("- Local model id: ${runtime["localModelId"]}")
            appendLine()
            appendLine("## Gateway")
            appendLine("- Gateway id: ${gateway["id"] ?: "none"}")
            appendLine("- Gateway name: ${gateway["name"] ?: "none"}")
            appendLine("- Endpoint: ${gateway["endpoint"] ?: ""}")
            appendLine("- API key: ${gateway["apiKey"] ?: ""}")
            appendLine("- Base model: ${gateway["model"] ?: ""}")
            appendLine("- Base embedding model: ${gateway["embeddingModel"] ?: ""}")
            appendLine("- Supports multimodal: ${gateway["supportsMultimodal"] ?: false}")
            appendLine()
            appendLine("## Capability Models")
            capabilities.forEach { (type, value) ->
                val item = value as? Map<*, *> ?: return@forEach
                appendLine("- $type: model=${item["model"]}, endpoint=${item["endpoint"]}, enabled=${item["enabled"]}")
            }
            appendLine()
            appendLine("## Usage Notes")
            appendLine("- 这是角色运行时模型画像，用于多角色群聊、狼人杀等玩法区分每个角色的调用配置。")
            appendLine("- 不要把 API Key 明文写入此文件。需要真实密钥时引用全局 gateway id。")
        }
    }

    private fun renderCompactSkillGroups(skills: List<SkillMeta>): String {
        if (skills.isEmpty()) return "- 技能索引将在运行时刷新。"
        return skills
            .filterNot { it.internalTool }
            .groupBy { it.categories.firstOrNull() ?: SkillToolCategory.SYSTEM }
            .toSortedMap(compareBy { it.name })
            .map { (category, metas) ->
                "- ${category.name}: ${metas.sortedBy { it.id }.take(20).joinToString(", ") { it.id }}"
            }
            .joinToString("\n")
    }

    private fun renderSkillIndex(skills: List<SkillMeta>): String {
        if (skills.isEmpty()) return "# Skill Index\n\nNo skills registered yet.\n"
        return buildString {
            appendLine("# Skill Index")
            appendLine()
            skills.filterNot { it.internalTool }
                .sortedWith(compareBy<SkillMeta> { it.categories.firstOrNull()?.name ?: "OTHER" }.thenBy { it.id })
                .forEach { meta ->
                    appendLine("## ${meta.id}")
                    appendLine("- Name: ${meta.nameZh ?: meta.name}")
                    appendLine("- Category: ${meta.categories.joinToString(", ") { it.name }.ifBlank { "OTHER" }}")
                    appendLine("- Level: ${meta.injectionLevel}")
                    appendLine("- Description: ${(meta.descriptionZh ?: meta.description).replace('\n', ' ').take(240)}")
                    if (meta.parameters.isNotEmpty()) {
                        appendLine("- Params: ${meta.parameters.joinToString(", ") { p -> p.name + if (p.required) "*" else "" }}")
                    }
                    appendLine()
                }
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9_\\-]+"), "_").ifBlank { "role" }

    private fun maskSecret(value: String): String =
        when {
            value.isBlank() -> ""
            value.length <= 8 -> "***"
            else -> value.take(4) + "***" + value.takeLast(4)
        }

    private fun maskEndpoint(value: String): String = value.take(220)

    private fun roleModelBindingText(role: Role): String {
        val binding = role.effectiveModelBinding() ?: return "none"
        val normalized = binding.normalized()
        return when {
            normalized.localModelId.isNotBlank() -> "local:${normalized.localModelId.removePrefix("local:")}"
            normalized.gatewayName.isNotBlank() && normalized.model.isNotBlank() -> "${normalized.gatewayName} / ${normalized.model}"
            normalized.gatewayId.isNotBlank() && normalized.model.isNotBlank() -> "${normalized.gatewayId} / ${normalized.model}"
            normalized.gatewayName.isNotBlank() -> "${normalized.gatewayName} / default chat model"
            normalized.gatewayId.isNotBlank() -> "${normalized.gatewayId} / default chat model"
            normalized.model.isNotBlank() -> "default gateway / ${normalized.model}"
            else -> "none"
        }
    }

    private fun nowText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    companion object {
        const val CORE_MD = "core.md"
        const val SKILLS_MD = "skills.md"
        const val MEMORY_MD = "memory.md"
        const val MODEL_MD = "model.md"
        const val CHAT_PROTOCOL_MD = "chat_protocol.md"
        const val MODEL_CONFIG_JSON = "model_config.json"
        const val JOURNAL_MD = "journal.md"
        const val SKILL_INDEX_MD = "skill_index.md"
    }
}
