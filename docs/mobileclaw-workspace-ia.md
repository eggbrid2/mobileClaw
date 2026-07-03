# MobileClaw 总工作空间信息架构

> 目标：定义 MobileClaw 的“总工作空间”概念，作为本机数据根目录、上下文沉淀边界、产物归档边界和导入导出边界，并为后续按区域查看、清理、导入、导出和迁移做准备。

本文讨论的重点不是“页面上有哪些功能入口”，而是：

- MobileClaw 在本机到底沉淀了哪些数据。
- 这些数据应该归属到哪些工作空间区域。
- 每个区域内部应该如何继续分层。
- 每个区域能否被 AI 读取、写入、归档和迁移。
- 每个区域导入导出时如何处理版本、冲突和敏感信息。

## 1. 总工作空间定义

MobileClaw 总工作空间不是某一个页面，也不是某一次任务的临时 workspace，更不是某个角色的档案页。

它应该表示：

> MobileClaw 在本机沉淀出来的全部可归档、可查看、可迁移数据的统一工作空间根。

总工作空间需要解决三个问题：

1. 定义数据边界：什么属于 MobileClaw 工作空间，什么只是运行缓存或外部服务状态。
2. 定义区域归属：角色、用户记忆、工作产物、技能、MCP、群聊等分别放在哪里。
3. 定义迁移规则：哪些内容可以导出，哪些内容需要脱敏，哪些内容导入后必须重新验证。

因此，总工作空间更接近一个本机目录结构和数据 manifest，而不是单纯的 UI 导航。

### 1.1 工作空间根目录

建议后续把总工作空间抽象成一个稳定的逻辑根：

```text
mobileclaw_workspace/
  manifest.json
  roles/
  user_memory/
  work/
  sessions/
  groups/
  skills/
  mcp/
  models/
  media/
  system/
  tasks/
  play/
  backups/
```

这个目录不一定一开始就真实存在于 `filesDir` 下，也可以由现有数据库、DataStore 和文件目录映射出来。

但导入导出时，需要把它序列化成这个稳定结构。

### 1.2 工作空间区域

“区域”不是页面分类，而是数据归属单元。

每个区域至少需要定义：

- 存什么
- 不存什么
- 当前来源在哪里
- 未来导出路径是什么
- 是否允许 AI 读
- 是否允许 AI 写
- 是否默认导出
- 是否包含敏感信息
- 导入时如何合并

### 1.3 工作空间与页面的关系

首页菜单里的“总工作空间”只是一个查看和管理入口。

真正的工作空间应该在系统内部稳定存在：

- 聊天页可以向工作空间写入会话和产物。
- 角色页可以向工作空间写入角色档案。
- 技能市场可以向工作空间写入技能定义。
- MCP 连接页可以向工作空间写入远程工具连接。
- 群聊页可以向工作空间写入群配置和群沉淀。
- 导入导出页可以按区域读取工作空间内容。

也就是说，页面是入口，工作空间是底层数据组织方式。

## 2. 概念边界

### 2.1 总工作空间

总工作空间是全局数据地图。

它包含：

- AI 角色
- 用户记忆
- 工作产物
- 会话记录
- 群聊区域
- 技能库
- MCP 连接
- 模型与网关
- 媒体资产
- 系统配置
- 任务队列
- 玩法空间
- 备份与迁移

### 2.2 任务工作空间

任务工作空间是一次任务、会话或群聊角色执行过程的运行现场。

当前对应：

- `WorkspaceStore`
- `WorkspaceRuntimeCoordinator`
- `WorkspaceRuntimeRecorder`
- `filesDir/workspaces/ws_xxxxxxxx/`

它记录：

- 任务目标
- 当前 scope
- checkpoint
- event
- artifact state
- notes
- run summary
- working set

它不应该承担角色长期记忆或用户长期画像。

### 2.3 角色档案

角色档案是某个 AI 角色的长期身份与运行画像。

当前对应：

- `RoleManager`
- `RoleWorkspaceStore`
- `filesDir/role_workspaces/{roleId}/`

它记录：

- `core.md`
- `skills.md`
- `memory.md`
- `model.md`
- `journal.md`
- `skill_index.md`
- `model_config.json`

它不应该被称为“任务工作空间”，否则会和 `WorkspaceStore` 混淆。

### 2.4 角色空间

角色空间是角色的视觉化展示和游戏化表达。

当前对应：

- `AgentTownStore`
- `filesDir/agent_town/`

它记录：

- 角色房间
- 角色肖像
- sprite pack
- room pins
- toolbox 展示
- furniture
- artifact 陈列

它不是总工作空间，也不是任务工作空间。

## 3. 工作空间一级区域规划

### 3.1 AI 角色

用于管理所有 AI 角色和角色长期档案。

AI 角色不是单纯“人设”，而是 MobileClaw 中一个可被调度、可带技能、可绑定模型、可沉淀长期记忆的 AI 工作单元。

这一块应该回答：

- 当前有哪些角色？
- 每个角色负责什么？
- 每个角色有什么长期记忆？
- 每个角色默认使用哪些技能和模型？
- 每个角色在群聊中扮演什么身份？
- 角色能否被导出、复制、迁移？

包含：

- 内置角色
- 用户自定义角色
- 角色头像和肖像
- 角色描述
- 角色 prompt
- 角色技能偏好
- 角色模型配置
- 角色长期记忆
- 角色工作日志
- 角色气泡样式
- 角色群聊身份配置

#### 3.1.1 区域定位

AI 角色区域是“角色资产”的总入口。

角色资产包括：

- 角色身份
- 角色能力倾向
- 角色模型画像
- 角色长期记忆
- 角色在群聊里的表达方式
- 角色可迁移配置

它不应该承载：

- 某一次任务的完整执行过程
- 某一局游戏的完整状态
- 用户自己的全局记忆
- 全局技能库

这些内容分别归入：

- 任务工作空间
- 玩法空间
- 我的记忆
- 技能库

#### 3.1.2 用户能看到什么

用户应该能看到：

- 角色列表
- 当前默认角色
- 角色名称
- 角色头像/肖像
- 角色简介
- 角色擅长任务
- 角色绑定模型
- 角色固定技能
- 角色长期记忆摘要
- 角色最近工作日志
- 角色在群聊中的身份摘要
- 是否为内置角色
- 是否为用户自定义角色
- 是否可编辑
- 是否可导出

用户不应该默认看到：

- 完整 system prompt 的技术细节
- 脱敏前 API Key
- 内部调度评分
- 过长的 skill schema
- 临时任务 checkpoint

这些可以放到高级视图或调试视图。

#### 3.1.3 AI 能读写什么

AI 可以读取：

- 角色基本信息
- 角色长期档案
- 角色技能偏好
- 角色模型配置
- 角色记忆
- 角色工作日志
- 角色在群聊中的身份要求

AI 可以写入：

- 角色长期记忆
- 角色工作日志
- 角色技能使用经验
- 角色模型使用画像
- 角色群聊身份补充
- 角色自我说明

AI 不应随意写入：

- 内置角色核心定义
- 用户未确认的角色删除
- API Key
- 全局模型网关配置
- 用户自己的全局记忆

#### 3.1.4 二级内容划分

建议 AI 角色区域下再分这些二级内容：

##### A. 角色列表

展示所有角色。

字段：

- role id
- name
- description
- avatar
- isBuiltin
- isActive
- preferredTaskTypes
- forcedSkillIds count
- modelOverride
- updatedAt

操作：

- 查看详情
- 设为当前角色
- 编辑
- 复制内置角色
- 删除自定义角色
- 导出角色

##### B. 角色档案

展示角色长期身份。

字段：

- `core.md`
- 角色定位
- 执行原则
- 工作边界
- 系统提示词摘要
- 角色关键词

操作：

- 查看
- 高级编辑
- 恢复默认

##### C. 角色记忆

展示角色自己的长期记忆，不等于用户全局记忆。

字段：

- `memory.md`
- 角色偏好
- 角色任务经验
- 角色与用户协作习惯
- 角色失败经验
- 角色常用判断规则

操作：

- 查看
- 追加
- 清理
- 导出

##### D. 角色技能

展示角色如何使用技能。

字段：

- `skills.md`
- `skill_index.md`
- forcedSkillIds
- preferredTaskTypes
- skill 使用经验
- 角色常用技能
- 禁用/少用技能建议

操作：

- 查看技能索引
- 绑定固定技能
- 解绑固定技能
- 查看技能使用说明

##### E. 角色模型

展示角色最近使用的模型和模型偏好。

字段：

- `model.md`
- `model_config.json`
- modelOverride
- effectiveModel
- gateway id
- gateway name
- chat model
- embedding model
- image/video model
- local model enabled
- multimodal support

安全要求：

- API Key 只能脱敏展示
- token 不进入角色导出包，除非用户明确选择并二次确认

##### F. 角色工作日志

展示角色长期工作轨迹摘要。

字段：

- `journal.md`
- 最近任务摘要
- 成功经验
- 失败经验
- 自我修正
- 用户反馈

操作：

- 查看
- 追加
- 清理
- 导出

##### G. 角色群聊身份

展示角色在群聊中的表达身份。

字段：

- 群内昵称
- 群内身份设定
- 发言风格
- 气泡样式
- 群内关系
- 群内沉淀
- 群聊玩法身份引用

注意：

- 群聊身份的“全局模板”属于角色区域。
- 某个具体群里的成员关系属于群聊区域。
- 某一局狼人杀身份牌属于玩法空间。

#### 3.1.5 数据来源

现有数据来源：

- `RoleManager`
- `RoleWorkspaceStore`
- `Role`
- `ChatBubbleStyle`
- `role_workspaces/{roleId}/`
- 部分 `AgentTownStore`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 角色基础定义 | `RoleManager`, `Role` |
| 内置角色 | `Role.BUILTINS` |
| 自定义角色 | `filesDir/roles/{roleId}.json` |
| 角色档案 | `RoleWorkspaceStore` |
| 角色长期文件 | `filesDir/role_workspaces/{roleId}/` |
| 角色肖像 | `AgentTownStore.spritePacks` / role portrait pack |
| 气泡样式 | `Role.chatBubbleStyle` |
| 模型偏好 | `Role.modelOverride` |
| 最近模型画像 | `role_workspaces/{roleId}/model.md` |
| 技能偏好 | `Role.forcedSkillIds`, `Role.preferredTaskTypes` |

#### 3.1.6 文件路径

当前相关路径：

```text
filesDir/roles/{roleId}.json
filesDir/role_workspaces/{roleId}/core.md
filesDir/role_workspaces/{roleId}/skills.md
filesDir/role_workspaces/{roleId}/memory.md
filesDir/role_workspaces/{roleId}/model.md
filesDir/role_workspaces/{roleId}/journal.md
filesDir/role_workspaces/{roleId}/skill_index.md
filesDir/role_workspaces/{roleId}/model_config.json
filesDir/agent_town/town.json
filesDir/agent_town/assets/
```

建议后续统一 manifest：

```text
filesDir/role_workspaces/{roleId}/role_manifest.json
```

建议 manifest 字段：

```json
{
  "schemaVersion": 1,
  "roleId": "coder",
  "name": "代码专家",
  "type": "builtin|custom",
  "createdAt": 0,
  "updatedAt": 0,
  "files": [
    "core.md",
    "skills.md",
    "memory.md",
    "model.md",
    "journal.md"
  ],
  "linkedAssets": [],
  "linkedSkills": [],
  "exportPolicy": {
    "includeModelSecrets": false,
    "includePortrait": true,
    "includeJournal": true
  }
}
```

#### 3.1.7 数据库表

当前角色核心定义主要在文件系统，不在数据库。

关联数据库内容：

- session message 里可能记录 sender role
- group message 里可能记录 sender role
- semantic memory 里可能记录用户对角色的偏好

后续如果角色增长很复杂，可以考虑新增：

- `role_memories`
- `role_events`
- `role_group_profiles`

但当前阶段优先使用 Markdown + JSON 文件。

#### 3.1.8 导出范围

角色导出包建议包含：

- `role.json`
- `core.md`
- `skills.md`
- `memory.md`
- `model.md`
- `journal.md`
- `skill_index.md`
- `model_config.json`
- portrait 资源
- bubble style
- linked skill references

默认不包含：

- API Key
- MCP token
- 控制台 token
- 临时任务 workspace
- 群聊完整消息
- 玩法局状态

可选包含：

- 角色肖像
- 角色工作日志
- 角色关联技能完整定义
- 角色在群聊中的身份模板

#### 3.1.9 导入策略

导入角色时需要处理：

- role id 冲突
- 内置角色覆盖
- 自定义角色覆盖
- 技能缺失
- 模型缺失
- 肖像资源缺失
- schema version 不一致

建议导入选项：

1. 创建副本
2. 覆盖已有自定义角色
3. 只导入档案，不导入模型配置
4. 只导入基础定义，不导入记忆
5. 只导入记忆到现有角色

内置角色导入规则：

- 默认不覆盖内置角色。
- 如果导入包 role id 与内置角色相同，默认创建副本。
- 高级模式可允许覆盖内置角色的用户侧扩展档案，但不修改 `Role.BUILTINS` 源定义。

#### 3.1.10 敏感信息

可能敏感：

- 角色记忆中包含用户私密信息
- 模型配置中包含 endpoint
- 模型配置中可能关联 API Key
- 角色日志可能包含任务内容
- 群聊身份可能包含关系信息

处理原则：

- API Key 永不默认明文导出。
- endpoint 可导出，但需要提示。
- memory.md 默认可导出，但需要隐私提示。
- journal.md 默认可导出，但允许用户取消。
- model_config.json 中 token 必须脱敏。

#### 3.1.11 冲突处理

导入时可能冲突：

- role id 已存在
- name 重复
- avatar 文件重名
- model id 不存在
- forced skill 不存在
- linked skill 版本不一致

建议处理方式：

- role id 冲突：默认创建 `roleId_copy`
- name 重复：允许重复，但 UI 提示
- avatar 冲突：复制为新文件
- model 缺失：保留配置但标记 unavailable
- skill 缺失：保留引用并标记 missing
- skill 版本不一致：提示用户选择使用本地版本或导入版本

#### 3.1.12 UI 展示建议

建议 UI 名称：

- AI 角色

一级入口卡片展示：

- 角色数量
- 当前默认角色
- 最近活跃角色
- 自定义角色数量
- 缺失资源/缺失技能提醒

角色列表展示：

- 头像
- 名称
- 简介
- 擅长任务
- 当前状态
- 是否内置

角色详情展示：

- 黑白身份面板
- 工作身份
- 执行模式
- 模型画像
- 角色档案入口
- 技能入口
- 记忆入口
- 导出入口

角色档案页展示：

- core.md
- memory.md
- skills.md
- model.md
- journal.md

注意：

- UI 中建议叫“角色档案”，不要叫“角色工作空间”。
- “工作空间”保留给总工作空间和任务工作空间。

#### 3.1.13 搜索与排序

搜索字段：

- role id
- name
- description
- keywords
- preferredTaskTypes
- forcedSkillIds
- memory.md 摘要

排序字段：

- 当前角色优先
- 最近使用时间
- 内置角色优先
- 自定义角色优先
- 名称
- 创建时间
- 更新时间

#### 3.1.14 空状态

空状态文案建议：

```text
还没有自定义角色。
你可以复制一个内置角色，再为它添加记忆、技能和模型偏好。
```

如果角色档案为空：

```text
这个角色还没有形成长期档案。
后续任务中，它会逐步沉淀自己的记忆和工作经验。
```

#### 3.1.15 后续待定问题

- 角色记忆是否允许用户直接编辑？
- 角色工作日志是否默认导出？
- 内置角色的用户扩展档案如何恢复默认？
- 角色引用技能时，是强绑定技能版本还是只绑定 skill id？
- 角色模型配置是只记录最近一次，还是保留历史？
- 群聊身份模板放在角色区域，还是群聊区域引用后再覆盖？
- 狼人杀角色身份是从角色档案派生，还是在玩法空间单独生成？

导出建议：

- 支持单角色导出
- 支持全部角色导出
- API Key 不随角色模型配置明文导出
- 角色引用的技能可以只导出引用，也可以选择包含技能包

### 3.2 我的记忆

用于管理用户自己的长期沉淀，不属于任何单个角色。

我的记忆不是聊天记录的另一个入口，也不是角色自己的 `memory.md`。

它应该回答：

- 用户是谁？
- 用户长期偏好是什么？
- 用户明确要求 AI 必须遵守什么？
- 用户对工具、模型、UI、写作、代码等有什么稳定习惯？
- 哪些记忆来自用户手动配置，哪些来自聊天中提炼？
- 哪些记忆可以被 AI 使用，哪些已经被用户禁用？
- 哪些记忆需要导出、迁移或清理？

包含：

- 用户画像
- 用户偏好
- 用户长期记忆
- 用户常用习惯
- 用户身份信息
- 用户对 AI 行为的要求
- 会话中提炼出来的 facts
- 显式用户配置

#### 3.2.1 区域定位

我的记忆区域是“用户本人长期画像”的总入口。

它面向的是用户，而不是某个 AI 角色。

它承载：

- 用户基本画像
- 用户明确配置
- 用户长期偏好
- 用户对 AI 行为的长期要求
- 用户纠错和失败经验
- 用户常用项目、工具、App、设备习惯
- 可被注入上下文的长期事实

它不应该承载：

- 角色自己的长期记忆
- 某一次任务的临时状态
- 某个群里的群关系沉淀
- 某一局玩法的状态
- 原始聊天消息全文
- API Key、token、password 等敏感凭据

这些内容分别归入：

- AI 角色
- 任务工作空间
- 群聊区域
- 玩法空间
- 会话记录
- 模型与网关 / 系统配置

#### 3.2.2 用户能看到什么

用户应该能看到：

- 记忆列表
- 记忆分类
- 记忆 key
- 记忆内容
- 置信度
- 来源
- 来源引用
- 创建时间
- 更新时间
- 最近使用时间
- 使用次数
- 是否置顶
- 是否启用
- 是否来自用户显式配置
- 是否来自聊天提炼
- 是否来自任务总结
- 是否属于临时 session scope

用户不应该默认看到：

- 被系统判定为敏感的 token/key/secret
- 原始 embedding
- 内部 prompt 注入细节
- 过长的原始聊天记录
- 被删除或禁用记忆的完整恢复内容

高级视图可以展示：

- 记忆命名空间
- source/sourceRef
- scope
- type
- 命中和注入原因
- 当前任务下会被注入哪些记忆

#### 3.2.3 AI 能读写什么

AI 可以读取：

- 已启用的用户画像
- 已启用的用户偏好
- 已启用的长期规则
- 已启用的失败教训
- 与当前任务相关的 app/project/tool/model 事实
- 用户显式配置中非敏感内容

AI 可以写入：

- 用户明确说“记住”的事实
- 用户明确纠正 AI 行为后的规则
- 用户稳定偏好
- 任务完成后的非敏感经验摘要
- scoped session 记忆
- 经多次确认后从 scoped memory 提升为全局记忆的事实

AI 不应随意写入：

- 用户身份敏感信息
- 联系方式、地址、账号等高敏信息
- API Key、token、password、credential
- 与用户表达相反的推断
- 未经确认的长期规则
- 角色自己的 `memory.md`
- 群聊共享记忆

AI 写入记忆时需要遵守：

- 用户明确表达优先于模型推断。
- 新的用户纠正优先于旧偏好。
- scoped 记忆默认不提升为全局记忆。
- 敏感 key 不进入 prompt 注入。
- 被禁用记忆不参与上下文构建。

#### 3.2.4 二级内容划分

建议我的记忆区域下再分这些二级内容：

##### A. 用户画像

描述用户相对稳定的身份信息。

字段：

- `profile.name`
- `profile.location`
- `profile.profession`
- `profile.note`
- `user.*`
- confidence
- source
- updatedAt
- enabled
- pinned

操作：

- 查看
- 编辑
- 删除
- 启用/禁用
- 置顶
- 导出

注意：

- 画像不是实名档案，应该允许用户随时删除。
- 不应该强制采集用户身份。
- 地址、联系方式等高敏内容需要额外提示。

##### B. 用户偏好

描述用户长期稳定的偏好。

字段：

- `preference.*`
- `profile.preferred_*`
- `profile.preferences`
- `profile.dislikes`
- `task.default_lang`
- `task.tone`
- confidence
- source
- useCount
- lastUsedAt

操作：

- 查看
- 编辑
- 删除
- 启用/禁用
- 置顶
- 导出

典型内容：

- 回复语言
- 回复风格
- UI 风格偏好
- 文档格式偏好
- 代码风格偏好
- 搜索/研究偏好
- 图片生成偏好

##### C. 行为规则

描述用户对 AI 行为的硬性要求。

字段：

- `rule.*`
- `tool.policy.*`
- `agent.behavior.*`
- confidence
- source
- sourceRef
- pinned
- enabled

操作：

- 查看
- 编辑
- 删除
- 启用/禁用
- 置顶
- 查看生效范围

典型内容：

- 不要自动切角色
- 不要在普通聊天中主动生成页面
- 图片理解不要默认联网搜索
- 某类任务必须先确认条件
- 某类输出必须使用中文

注意：

- 规则的优先级高于普通偏好。
- 规则应该尽量来自用户明确表达，而不是 AI 猜测。
- 多条规则冲突时，最新用户纠正优先。

##### D. 纠错与失败经验

记录用户指出的问题和系统失败后的经验。

字段：

- `correction.*`
- `failure.*`
- `lesson.*`
- source
- sourceRef
- updatedAt
- useCount
- enabled

操作：

- 查看
- 删除
- 启用/禁用
- 标记已解决
- 导出

典型内容：

- 用户指出 AI 经常误判任务类型
- 某类技能调用失败后的经验
- 某个模型不适合某类任务
- 某个页面生成流程需要避开的坑

注意：

- 失败经验不是永久惩罚。
- 后续版本修复后，应允许用户或系统标记为过期。

##### E. App / 项目 / 工具事实

描述用户常用项目、App、工具、模型、网关等非敏感事实。

字段：

- `project.*`
- `app.*`
- `skill.*`
- `model.*`
- `vpn.*`
- scope
- source
- updatedAt
- enabled

操作：

- 查看
- 编辑
- 删除
- 启用/禁用
- 导出

注意：

- 如果涉及模型 token、网关密钥、VPN 凭据，不应放在我的记忆明文里。
- 我的记忆只记录可用于个性化判断的事实摘要。
- 真实凭据归入模型与网关或系统配置，并按敏感配置处理。

##### F. 任务作用域记忆

记录某一次任务或 session 内的临时记忆。

字段：

- `session.{scopeId}.*`
- scope = `session:{scopeId}`
- sourceRef
- task goal
- task summary
- task type
- task state
- task status
- updatedAt

操作：

- 查看
- 删除
- 提升为全局记忆
- 关联任务工作空间
- 导出时选择是否包含

注意：

- scoped 记忆默认只在对应任务/会话中生效。
- 只有稳定、可复用、非敏感内容才可以提升为全局记忆。
- 任务现场的完整事件和产物仍归入任务工作空间或工作产物。

##### G. 显式用户配置

展示用户在设置页中主动填写的长期配置。

字段：

- `UserConfig.entries_v2`
- key
- value
- description
- mirrored memory key
- updatedAt

操作：

- 查看
- 编辑
- 删除
- 导出

注意：

- `UserConfig` 是显式配置，比聊天中提炼的记忆更可信。
- 可映射到 `profile.*` 的配置会通过 `MemoryWriter.syncUserConfig(...)` 同步到语义记忆。
- 敏感配置不要镜像到可注入 prompt 的记忆层。

#### 3.2.5 数据来源

现有数据来源：

- `SemanticMemory`
- `MemoryWriter`
- `UserConfig`
- `UserProfileSkill`
- `profile.*`
- `preference.*`
- `rule.*`
- `tool.policy.*`
- `agent.behavior.*`
- `correction.*`
- `failure.*`
- `lesson.*`
- `session.*`
- `project.*`
- `app.*`
- `skill.*`
- `model.*`
- `vpn.*`
- `ProfilePage`
- `ProfileUiState`
- `MemoryContextBuilder`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 用户长期事实 | `semantic_facts` |
| 用户显式配置 | DataStore `user_config.entries_v2` |
| 用户配置镜像 | `MemoryWriter.syncUserConfig(...)` |
| 聊天中明确记忆 | `MemoryWriter.recordExplicitUserText(...)` |
| scoped 会话记忆 | `MemoryWriter.recordScopedUserText(...)` |
| 任务状态记忆 | `MemoryWriter.recordTaskSnapshot(...)`, `updateTaskState(...)` |
| prompt 注入选择 | `MemoryContextBuilder` |
| 记忆管理 UI | `ProfilePage`, `MemoryBrowserCard` |
| 记忆列表状态 | `ProfileUiState.semanticFacts` |

#### 3.2.6 文件路径

当前我的记忆主要不在独立 Markdown 文件里，而在数据库和 DataStore 中：

```text
Room database: semantic_facts
DataStore: user_config
```

后续为了总工作空间导入导出，建议生成可读导出结构：

```text
workspace_export/memory/profile.md
workspace_export/memory/preferences.md
workspace_export/memory/rules.md
workspace_export/memory/lessons.md
workspace_export/memory/app_facts.md
workspace_export/memory/session_memory.json
workspace_export/memory/user_config.json
workspace_export/memory/memory_manifest.json
```

建议 manifest 字段：

```json
{
  "schemaVersion": 1,
  "area": "user_memory",
  "createdAt": 0,
  "entryCount": 0,
  "includesDisabled": false,
  "includesScopedSessionMemory": false,
  "files": [
    "profile.md",
    "preferences.md",
    "rules.md",
    "lessons.md",
    "app_facts.md",
    "user_config.json"
  ],
  "exportPolicy": {
    "includeDisabled": false,
    "includeScopedSessionMemory": false,
    "includeSensitiveKeys": false
  }
}
```

#### 3.2.7 数据库表

当前核心表：

- `semantic_facts`

字段：

- `key`
- `value`
- `confidence`
- `type`
- `scope`
- `source`
- `sourceRef`
- `createdAt`
- `updatedAt`
- `lastUsedAt`
- `useCount`
- `pinned`
- `enabled`

关联表：

- `conversations`
- `sessions`
- `session_messages`
- `group_messages`
- `episodes`

注意：

- `conversations` 和 `session_messages` 是消息历史，不直接等于我的记忆。
- `semantic_facts` 中 `session.*` 是任务/会话作用域记忆，仍可在我的记忆中查看，但不默认视为全局用户记忆。
- `episodes` 更像任务经验库，后续可以决定归入我的记忆、任务队列或工作产物的交叉索引。

#### 3.2.8 导出范围

我的记忆导出包建议包含：

- 用户画像
- 用户偏好
- 行为规则
- 纠错与失败经验
- App / 项目 / 工具事实
- 显式用户配置
- 置顶状态
- 启用状态
- source/sourceRef
- createdAt/updatedAt

默认不包含：

- API Key
- token
- password
- credential
- 原始 embedding
- 原始聊天全文
- 群聊完整消息
- 角色 `memory.md`
- 任务 workspace 事件全文

可选包含：

- disabled 记忆
- scoped session 记忆
- 记忆使用统计
- 记忆来源引用
- 与会话/任务的弱引用 id

#### 3.2.9 导入策略

导入我的记忆时需要处理：

- key 冲突
- 新旧记忆矛盾
- disabled 状态冲突
- pinned 状态冲突
- source/sourceRef 缺失
- schema version 不一致
- 敏感 key 被导入

建议导入选项：

1. 只预览，不写入
2. 新增不存在的记忆
3. 用导入包覆盖本地同 key 记忆
4. 保留本地，导入为副本 key
5. 只导入启用记忆
6. 连同禁用记忆一起导入
7. 只导入画像和偏好
8. 不导入规则和工具策略

冲突合并规则：

- pinned 本地记忆默认优先。
- updatedAt 更新的一方默认优先，但需要显示差异。
- 规则类记忆冲突时必须让用户确认。
- 显式用户配置优先于聊天提炼记忆。
- 敏感 key 默认拒绝导入，除非高级模式二次确认。

#### 3.2.10 敏感信息

可能敏感：

- 用户姓名
- 用户位置
- 职业身份
- 常用项目
- 使用习惯
- 与工作相关的事实
- 模型/网关名称
- VPN 或 App 使用痕迹
- 行为规则中包含的私人要求

绝对不应默认导出：

- API Key
- token
- secret
- password
- credential
- apikey
- api_key

处理原则：

- 导出前给出隐私提示。
- 默认排除敏感 key。
- 默认不导出原始聊天全文。
- 支持按分类勾选导出。
- 导入前展示预览。
- 用户可以一键删除某类记忆。
- AI 使用记忆时只读取 enabled 且非敏感的内容。

#### 3.2.11 冲突处理

导入时可能冲突：

- 同 key 不同 value
- 同 value 不同 key
- 本地禁用、导入启用
- 本地置顶、导入未置顶
- 本地 source 为显式配置，导入 source 为聊天提炼
- 规则互相矛盾
- 旧的失败经验已经不再适用

建议处理方式：

- 同 key 冲突：展示本地和导入值，默认保留本地。
- 同 value 不同 key：允许合并为一个 key。
- 启用状态冲突：本地禁用优先，除非用户确认恢复。
- pinned 冲突：本地 pinned 优先。
- source 冲突：显式配置优先。
- 规则冲突：必须用户确认。
- 失败经验过期：允许导入但标记为待确认。

#### 3.2.12 UI 展示建议

建议 UI 名称：

- 我的记忆

一级入口卡片展示：

- 记忆总数
- 已启用数量
- 已置顶数量
- 用户画像数量
- 偏好数量
- 规则数量
- 最近更新
- 隐私风险提示

记忆列表展示：

- 分类 tab
- 搜索框
- 记忆 key
- 记忆内容摘要
- 来源标签
- 置信度
- 启用开关
- 置顶按钮
- 最近使用

记忆详情展示：

- 完整内容
- 分类
- scope
- source/sourceRef
- 创建和更新时间
- 使用统计
- 当前是否会注入上下文
- 编辑入口
- 删除入口

建议分类：

- 画像
- 偏好
- 规则
- 纠错
- 项目/App
- 任务作用域
- 用户配置

注意：

- 不要把用户记忆做成“聊天记录回放”。
- 不要把角色记忆混到这里。
- 不要默认暴露敏感 key。
- 可以提供“本次对话会用到哪些记忆”的调试视图。

#### 3.2.13 搜索与排序

搜索字段：

- key
- value
- type
- scope
- source
- sourceRef

排序字段：

- pinned 优先
- enabled 优先
- 最近更新
- 最近使用
- 使用次数
- confidence
- 分类
- key

筛选条件：

- 启用/禁用
- 是否置顶
- 记忆类型
- 作用域
- 来源
- 是否可导出
- 是否疑似敏感

#### 3.2.14 空状态

空状态文案建议：

```text
还没有形成长期记忆。
你可以直接告诉我“记住……”，也可以在用户配置里写下长期偏好。
```

如果只有禁用记忆：

```text
当前没有启用的记忆。
被禁用的记忆不会参与 AI 的上下文判断。
```

如果搜索无结果：

```text
没有找到匹配的记忆。
可以换一个关键词，或查看全部分类。
```

#### 3.2.15 后续待定问题

- 是否需要把我的记忆也生成 Markdown 镜像？
- 用户配置和语义记忆的编辑入口是否合并？
- scoped session 记忆默认展示在我的记忆里，还是只从任务工作空间进入？
- 失败经验是否需要过期时间？
- 用户能否设置“永不自动提炼记忆”？
- 群聊中用户说的话是否默认进入我的记忆？
- 角色对用户的观察应写入角色记忆，还是我的记忆？
- 我的记忆导入时，规则类记忆是否必须逐条确认？
- 是否提供“AI 为什么使用了这条记忆”的解释入口？
- 是否需要记忆变更审计日志？

导出建议：

- 支持全部我的记忆导出
- 支持按分类导出
- 支持只导出 enabled 记忆
- 支持导出前敏感扫描
- 支持导入前预览和冲突处理
- 默认不导出敏感 key 和原始聊天全文

### 3.3 工作产物

用于管理 AI 为用户实际生成或维护的结果。

工作产物不是“所有文件”的杂物箱，而是 MobileClaw 代表用户创建、修改、维护过的可交付成果集合。

它应该回答：

- AI 给用户生成了哪些东西？
- 这些东西现在在哪里？
- 哪些产物可以继续编辑？
- 哪些产物属于同一个任务工作空间？
- 哪些产物引用了用户上传素材？
- 哪些产物可以导出、分享、迁移？
- 哪些产物只是缓存或临时预览，不应该默认迁移？

包含：

- AI Native Pages
- MiniAPP
- 生成文件
- 生成文档
- HTML 产物
- 图片、视频、图标
- 代码执行产物
- 任务 workspace
- artifact states
- 用户上传或输入的工作素材

#### 3.3.1 区域定位

工作产物区域是“AI 交付物”的总入口。

它面向结果，而不是过程。

它承载：

- 可打开的页面
- 可运行的 MiniAPP
- 可分享的文件
- 可查看的文档
- 可复用的 HTML 报告
- 可下载的图片/视频/图标
- 可继续编辑的 artifact
- 与任务 workspace 的引用关系
- 与会话消息的引用关系

它不应该承载：

- 普通聊天消息全文
- 用户长期记忆
- 角色长期档案
- 群成员关系和群身份
- 模型 API Key
- MCP token
- 纯缓存文件
- 未交付给用户的内部中间文件

这些内容分别归入：

- 会话记录
- 我的记忆
- AI 角色
- 群聊区域
- 模型与网关
- MCP 连接
- 系统配置 / 缓存清理

#### 3.3.2 用户能看到什么

用户应该能看到：

- 产物列表
- 产物类型
- 标题
- 简介
- 缩略预览
- 文件路径或内部 id
- 文件大小
- 创建时间
- 更新时间
- 最近打开时间
- 创建来源
- 关联会话
- 关联任务 workspace
- 关联角色
- 是否可继续编辑
- 是否可导出
- 是否缺失文件
- 是否含外部资源

用户不应该默认看到：

- 内部 patch 原始 JSON
- 过长的 tool observation
- 运行期临时缓存
- WebView bridge 注入脚本细节
- 本地绝对路径中的敏感目录细节
- 外部服务 token

高级视图可以展示：

- artifact id
- artifact state
- history
- linked workspace id
- source skill
- validation result
- runtime logs
- dependency files

#### 3.3.3 AI 能读写什么

AI 可以读取：

- 用户选中的产物内容
- 产物元数据
- 产物历史摘要
- 关联任务 workspace 的 artifact state
- 关联会话中的用户修改要求
- 产物运行日志
- 用户上传或输入的素材引用

AI 可以写入：

- 新产物
- 产物内容更新
- 产物元数据更新
- artifact state
- 产物历史记录
- 验证结果
- 任务 workspace 中的产物链接
- 产物运行日志

AI 不应随意写入：

- 用户外部存储中的任意文件
- 非当前产物的历史版本
- 角色档案
- 用户记忆
- 模型或网关密钥
- 产物关联外的系统配置

AI 修改产物时需要遵守：

- 如果用户说“继续/改一下/优化下”，优先修改当前相关产物。
- 同一 artifact 的后续修改应保留历史摘要。
- 能用专门产物工具创建时，不应只在聊天里返回原始代码。
- 产物本体和任务 workspace 记录都要更新引用。
- 对外部存储写入需要尊重 Android 权限和用户意图。

#### 3.3.4 二级内容划分

建议工作产物区域下再分这些二级内容：

##### A. AI Native Pages

由 `ui_builder` 创建或更新的 Android 原生 UI 页面。

字段：

- page id
- title
- description
- createdAt
- updatedAt
- page definition
- spec
- history
- linked workspace
- source role

操作：

- 打开
- 继续编辑
- 复制
- 删除
- 导出
- 查看历史
- 查看关联任务

注意：

- AI Native Page 是持久页面，不是一次性 HTML 预览。
- 页面结构应导出为 JSON 定义。
- 页面关联的媒体资源需要一并处理。

##### B. MiniAPP

由 `app_manager` 创建或更新的可运行 HTML/JS/Python 小应用。

字段：

- app id
- title
- description
- icon
- htmlPath
- hasPython
- backend.py
- app.log
- spec
- history
- createdAt
- updatedAt
- linked workspace

操作：

- 打开运行
- 继续编辑
- 查看代码
- 查看日志
- 清空日志
- 更新图标
- 复制
- 删除
- 导出

注意：

- MiniAPP 是程序，不等于静态报告。
- 需要导出 HTML、metadata、data 目录、Python 后端、图标等。
- 运行日志默认可不导出，除非用户勾选。

##### C. 生成文件

由 `create_file` 或文件类技能创建的文本/数据/代码文件。

字段：

- filename
- path
- mimeType
- sizeBytes
- createdAt
- updatedAt
- source skill
- linked message
- linked workspace

操作：

- 打开
- 分享
- 复制路径
- 重命名
- 删除
- 继续编辑
- 导出

典型类型：

- `.txt`
- `.md`
- `.csv`
- `.json`
- `.py`
- `.js`
- `.html`
- `.xml`

注意：

- app 私有目录和外部 files 目录都要统一索引。
- 用户外部存储中的文件不能在导入时强行覆盖。

##### D. 生成文档

由 `generate_document` 创建的 Office/PDF 类文档。

字段：

- filename
- path
- mimeType
- sizeBytes
- document type
- source skill
- generatedAt
- linked workspace

操作：

- 打开
- 分享
- 重新生成
- 导出
- 删除

典型类型：

- `.docx`
- `.pptx`
- `.xlsx`
- `.pdf`

注意：

- 文档本体应归入工作产物。
- 如果后续支持文档模板，模板归入技能库或系统配置，生成结果仍归入工作产物。

##### E. HTML 预览与报告

由 `create_html` 创建的一次性 HTML 页面或报告。

字段：

- title
- filename
- path
- html content
- createdAt
- linked message
- linked workspace

操作：

- 打开
- 分享
- 另存为 MiniAPP
- 删除
- 导出

注意：

- 一次性 HTML 不等于 MiniAPP。
- 如果用户后续要求继续做成工具，应迁移或复制为 MiniAPP。

##### F. 媒体产物

由图片、视频、图标、贴纸等生成技能创建的媒体文件。

字段：

- media id
- filename
- path
- mimeType
- sizeBytes
- width/height
- duration
- prompt
- provider
- source skill
- linked workspace

操作：

- 预览
- 分享
- 保存到相册
- 删除
- 导出
- 作为角色头像/肖像使用
- 作为页面或 MiniAPP 资源使用

注意：

- 工作产物负责“生成结果”。
- 媒体资产区域负责更广义的头像、素材、缓存和资源库。
- 同一文件可以在两个区域中以不同视图出现，但导出时需要去重。

##### G. 任务 Workspace 关联

展示产物背后的任务现场引用。

字段：

- workspace id
- title
- goal
- scope
- status
- linkedArtifacts
- latest artifact state
- checkpoints
- events
- runs
- workingSet

操作：

- 查看关联任务
- 继续该任务
- 打开最近 checkpoint
- 查看产物变更摘要
- 导出任务包

注意：

- 任务 workspace 是过程记录。
- 工作产物是结果入口。
- 一个产物可以被多个 workspace 引用。
- 一个 workspace 可以包含多个产物。

##### H. 用户素材与附件引用

管理用户上传或输入后被产物使用的素材。

字段：

- input id
- path
- mimeType
- sizeBytes
- source message
- linked artifact
- linked workspace
- createdAt

操作：

- 查看
- 复制
- 删除引用
- 导出

注意：

- 用户素材不一定是 AI 生成产物。
- 如果素材被产物引用，导出产物时需要提示是否一并带上。
- 如果素材只是聊天附件，主入口应在会话记录或媒体资产。

#### 3.3.5 数据来源

现有数据来源：

- `WorkspaceStore`
- `AiPageStore`
- `MiniAppStore`
- `TaskReplayStore`
- `TaskRecipeStore`
- `GenerateDocumentSkill`
- `CreateFileSkill`
- `UserStorageManager`
- `CreateHtmlSkill`
- `GenerateImageSkill`
- `GenerateVideoSkill`
- `GenerateIconSkill`
- `VideoGenerationTaskEntity`
- `SkillAttachment`
- `WorkspaceRuntimeRecorder`
- `WorkspaceManagerSkill`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 任务 workspace | `WorkspaceStore` |
| workspace manifest | `filesDir/workspaces/{workspaceId}/manifest.json` |
| artifact link | `WorkspaceManifest.linkedArtifacts` |
| artifact state | `filesDir/workspaces/{workspaceId}/artifacts/*.json` |
| AI Native Page | `AiPageStore`, `filesDir/ai_pages/{id}.json` |
| MiniAPP | `MiniAppStore`, `filesDir/apps/{id}.json`, `{id}.html`, `{id}_data/` |
| 生成文档 | `GenerateDocumentSkill`, `filesDir/documents/` |
| 生成文件 | `CreateFileSkill`, `created_files/` |
| HTML 预览 | `CreateHtmlSkill`, `html_pages/` |
| 视频产物 | `VideoGenerationTaskEntity`, `filesDir/videos/` |
| 聊天图片输入 | `filesDir/chat_images/`, `workspace_image_inputs/` |
| 文件卡片/附件 | `SkillAttachment.FileData`, `HtmlData`, `ImageData`, `FileList` |
| 任务回放 | `TaskReplayStore`, `filesDir/task_replays/` |
| 任务配方 | `TaskRecipeStore`, `filesDir/task_recipes/` |

#### 3.3.6 文件路径

当前相关路径：

```text
filesDir/workspaces/{workspaceId}/manifest.json
filesDir/workspaces/{workspaceId}/notes/
filesDir/workspaces/{workspaceId}/scratch/
filesDir/workspaces/{workspaceId}/outputs/
filesDir/workspaces/{workspaceId}/artifacts/
filesDir/workspaces/{workspaceId}/runs/
filesDir/workspaces/{workspaceId}/cache/
filesDir/workspaces/{workspaceId}/checkpoints/
filesDir/workspaces/{workspaceId}/indexes/
filesDir/workspaces/{workspaceId}/events/

filesDir/ai_pages/{pageId}.json
filesDir/apps/{appId}.json
filesDir/apps/{appId}.html
filesDir/apps/{appId}_data/
filesDir/documents/
filesDir/videos/
filesDir/chat_images/
filesDir/workspace_image_inputs/
filesDir/task_replays/
filesDir/task_recipes/

externalFilesDir/created_files/
externalFilesDir/html_pages/
```

后续为了总工作空间导入导出，建议生成统一产物索引：

```text
workspace_export/artifacts/artifact_manifest.json
workspace_export/artifacts/ai_pages/
workspace_export/artifacts/miniapps/
workspace_export/artifacts/files/
workspace_export/artifacts/documents/
workspace_export/artifacts/html_pages/
workspace_export/artifacts/media/
workspace_export/artifacts/workspace_links/
workspace_export/artifacts/user_inputs/
```

建议 manifest 字段：

```json
{
  "schemaVersion": 1,
  "area": "artifacts",
  "createdAt": 0,
  "artifactCount": 0,
  "files": [],
  "artifacts": [
    {
      "type": "miniapp",
      "id": "app_xxx",
      "title": "Example",
      "sourceSkill": "app_manager",
      "createdAt": 0,
      "updatedAt": 0,
      "entryFile": "miniapps/app_xxx/app.html",
      "metadataFile": "miniapps/app_xxx/manifest.json",
      "linkedWorkspaceIds": ["ws_xxxxxxxx"],
      "linkedSessionIds": [],
      "dependencies": []
    }
  ],
  "exportPolicy": {
    "includeWorkspaceProcess": true,
    "includeRuntimeLogs": false,
    "includeUserInputs": "referenced_only",
    "includeCaches": false
  }
}
```

#### 3.3.7 数据库表

当前工作产物主要在文件系统中。

关联数据库内容：

- `session_messages`
- `group_messages`
- `video_generation_tasks`
- `episodes`

说明：

- `session_messages` 和 `group_messages` 可能保存产物附件引用。
- `video_generation_tasks` 保存视频生成任务和产物路径。
- `episodes` 可作为任务经验索引，但不是产物本体。

后续如果产物列表复杂，建议新增：

- `artifacts`
- `artifact_versions`
- `artifact_links`
- `artifact_dependencies`

建议 `artifacts` 字段：

- id
- type
- title
- description
- primaryPath
- mimeType
- sizeBytes
- sourceSkill
- sourceRoleId
- createdAt
- updatedAt
- lastOpenedAt
- editable
- exportable
- deleted

#### 3.3.8 导出范围

工作产物导出包建议包含：

- 产物 manifest
- 产物元数据
- 产物本体文件
- 产物历史摘要
- artifact state
- workspace link
- 必要依赖资源
- 被引用的用户素材

默认不包含：

- 运行缓存
- WebView 临时缓存
- app.log
- 过长 tool observation
- API Key
- token
- 外部服务任务密钥
- 用户未引用的全部相册/文件
- 群聊完整消息
- 原始会话全文

可选包含：

- 任务 workspace 过程记录
- MiniAPP runtime logs
- 用户输入素材
- HTML 预览源码
- 历史版本
- 任务回放和任务配方

导出粒度：

- 单个产物导出
- 按类型导出
- 按任务 workspace 导出
- 按会话导出关联产物
- 全部工作产物导出

#### 3.3.9 导入策略

导入工作产物时需要处理：

- artifact id 冲突
- 文件名冲突
- 目录不存在
- 引用的 workspace 不存在
- 引用的会话不存在
- 引用的角色不存在
- 引用的技能不存在
- 绝对路径失效
- 外部文件权限不可用
- schema version 不一致

建议导入选项：

1. 创建副本
2. 覆盖已有同 id 产物
3. 只导入产物本体，不导入任务过程
4. 连同任务 workspace 一起导入
5. 只导入可打开产物，不导入缺失依赖产物
6. 保留缺失引用并标记 unavailable
7. 将外部绝对路径重写为 app 私有路径
8. 导入后重新验证 MiniAPP / AI Page

路径重写规则：

- 不保留导入包中的原始绝对路径作为主路径。
- 导入后使用本机新路径。
- 原始路径可作为 `originalPath` 记录。
- 相对依赖路径随产物包一起重建。
- 外部存储写入必须经用户确认。

#### 3.3.10 敏感信息

可能敏感：

- 文档内容
- 用户上传素材
- 生成文件中的业务数据
- MiniAPP 源码
- HTML 报告内容
- 运行日志
- 绝对路径
- 任务目标和摘要

处理原则：

- 导出前提示产物内容可能包含隐私。
- 默认不导出运行日志。
- 默认不导出缓存。
- 绝对路径导出时可脱敏或改为相对路径。
- 如果产物包含外部服务返回结果，需要标记 provider。
- 如果产物中包含 token/key，导出前应进行敏感扫描。

#### 3.3.11 冲突处理

导入时可能冲突：

- artifact id 已存在
- app/page/file 名称重复
- 同名文件内容不同
- workspace link 指向不存在的 workspace
- 产物依赖文件缺失
- MiniAPP 数据目录冲突
- AI Page schema 不兼容
- HTML 引用本地资源路径失效

建议处理方式：

- artifact id 冲突：默认创建新 id。
- 文件名冲突：默认追加后缀。
- 内容相同：去重并复用。
- workspace 缺失：保留弱引用并标记 missing。
- 依赖缺失：允许导入但标记 incomplete。
- schema 不兼容：进入只读模式并提示升级。
- MiniAPP 冲突：导入为副本，保留原 app。

#### 3.3.12 UI 展示建议

建议 UI 名称：

- 工作产物

一级入口卡片展示：

- 产物总数
- 最近产物
- MiniAPP 数量
- AI Native Page 数量
- 文件/文档数量
- 媒体数量
- 缺失文件数量
- 可继续编辑数量

产物列表展示：

- 类型图标
- 标题
- 摘要
- 缩略图
- 更新时间
- 文件大小
- 来源技能
- 关联 workspace
- 快捷操作

产物详情展示：

- 预览
- 基本信息
- 打开/运行入口
- 继续编辑入口
- 文件位置
- 关联会话
- 关联任务 workspace
- 历史记录
- 依赖资源
- 导出入口

建议分类：

- 全部
- MiniAPP
- AI 页面
- 文档
- 文件
- HTML
- 图片/视频
- 任务产物
- 用户素材

注意：

- 不要把工作产物页面做成文件管理器的复制品。
- 重点是“AI 做过什么”和“能否继续维护”。
- 缺失文件、缺失依赖、不可运行状态要明显提示。
- 对可以继续编辑的产物，应优先给出继续按钮。

#### 3.3.13 搜索与排序

搜索字段：

- title
- description
- artifact id
- filename
- mimeType
- source skill
- source role
- workspace title
- task goal

排序字段：

- 最近更新
- 最近打开
- 创建时间
- 产物类型
- 文件大小
- 可编辑优先
- 缺失依赖靠后

筛选条件：

- 类型
- 来源技能
- 关联角色
- 关联 workspace
- 是否可编辑
- 是否可导出
- 是否缺失文件
- 是否包含用户素材

#### 3.3.14 空状态

空状态文案建议：

```text
还没有工作产物。
当 AI 为你生成页面、MiniAPP、文档、文件或媒体时，它们会出现在这里。
```

如果只有缓存或临时文件：

```text
当前没有可管理的工作产物。
临时缓存不会默认显示在这里。
```

如果搜索无结果：

```text
没有找到匹配的产物。
可以换个关键词，或按类型查看。
```

#### 3.3.15 后续待定问题

- 是否需要建立统一 `artifacts` 数据库索引？
- AI Native Page 和 MiniAPP 是否都统一实现 artifact version？
- 一次性 HTML 是否允许升级为 MiniAPP？
- 产物删除是进入回收站，还是直接删除文件？
- workspace 中的 `outputs/` 是否需要成为标准产物目录？
- 用户外部文件被 AI 修改后，是否纳入工作产物索引？
- 产物导出时是否默认包含创建它的任务 workspace？
- 产物导入后是否自动重新验证可运行性？
- 媒体产物和媒体资产区域如何去重？
- 任务回放和任务配方归工作产物，还是任务队列？

导出建议：

- 支持按产物类型导出
- 支持按任务 workspace 导出
- 支持单个产物导出
- 支持导出前依赖扫描
- 支持路径重写
- 支持附件迁移
- 默认不导出缓存和运行日志

### 3.4 会话记录

用于管理普通聊天、单聊 session 和消息历史。

会话记录是用户和 AI 的对话时间线，不是用户长期记忆，也不是工作产物本体。

它应该回答：

- 用户和 AI 什么时候聊过什么？
- 某次对话使用了哪个角色？
- 哪些消息带有附件、文件、网页、搜索结果或操作卡片？
- 某次对话触发了哪些技能和运行日志？
- 某次对话关联了哪些任务 workspace 和工作产物？
- 哪些内容可以导出为聊天记录？
- 哪些内容已经被提炼成记忆或产物引用？

包含：

- 单聊 session
- session messages
- 当前会话角色
- 附件引用
- 运行日志
- 会话关联任务工作空间
- 会话中的用户输入素材
- 会话中的 AI 回复产物引用

#### 3.4.1 区域定位

会话记录区域是“单聊消息历史”的总入口。

它承载：

- 单聊 session 列表
- 每个 session 的消息时间线
- 消息文本
- 消息附件
- AI 执行日志
- 当前会话绑定角色
- AI 回复的发送角色信息
- 会话中产生的文件/页面/搜索结果卡片
- 会话和任务 workspace 的弱引用
- 会话和工作产物的弱引用

它不应该承载：

- 用户长期画像
- 角色长期档案
- 工作产物本体文件
- 群聊成员身份
- 群内长期关系
- 玩法局状态
- 模型密钥
- MCP token

这些内容分别归入：

- 我的记忆
- AI 角色
- 工作产物
- 群聊区域
- 玩法空间
- 模型与网关
- MCP 连接

#### 3.4.2 用户能看到什么

用户应该能看到：

- 会话列表
- 会话标题
- 会话当前角色
- 创建时间
- 更新时间
- 消息数量
- 最近一条消息摘要
- 消息时间线
- 用户消息
- AI 消息
- AI 消息的发送角色
- 附件卡片
- 文件卡片
- HTML 卡片
- 网页/搜索结果卡片
- 操作卡片
- 运行日志摘要
- 关联产物入口
- 关联 workspace 入口

用户不应该默认看到：

- 完整内部 prompt
- 过长 tool observation 原文
- 被剥离的图片 base64 大字段
- 调试级别的中间 JSON
- token/key/secret
- 已删除会话的残留索引

高级视图可以展示：

- message id
- session id
- logLinesJson
- attachmentsJson
- senderRoleId
- workspace scope
- source skill ids
- 任务类型推断

#### 3.4.3 AI 能读写什么

AI 可以读取：

- 当前会话最近消息
- 用户明确要求继续的历史消息
- 消息附件摘要
- 运行日志摘要
- 关联产物摘要
- 关联 workspace 摘要
- 当前会话角色
- AI 消息发送角色

AI 可以写入：

- 用户消息
- AI 回复消息
- 消息附件
- 运行日志
- 会话标题
- 会话角色
- 会话更新时间
- 与产物和 workspace 的引用信息

AI 不应随意写入：

- 历史消息原文
- 已归档会话
- 用户长期记忆
- 角色长期档案
- 产物本体文件
- 群聊消息

AI 使用会话历史时需要遵守：

- “继续/重试/改一下/不是这个”应优先参考当前会话最近上下文。
- 会话历史只是上下文，不等于长期记忆。
- 只有明确或稳定的信息才应提炼进我的记忆。
- 产物后续修改应通过产物引用找到工作产物，而不是只读聊天文本。
- 运行日志用于解释和恢复任务，不应默认完整注入普通聊天。

#### 3.4.4 二级内容划分

建议会话记录区域下再分这些二级内容：

##### A. 会话列表

展示所有单聊 session。

字段：

- session id
- title
- roleId
- createdAt
- updatedAt
- message count
- latest message summary
- linked workspace count
- linked artifact count

操作：

- 打开
- 重命名
- 切换角色
- 删除
- 导出
- 查看关联产物

注意：

- `roleId` 是会话默认角色，不等于每条 AI 消息的真实发送角色。
- 历史消息需要保留当时的发送角色快照。

##### B. 消息时间线

展示某个 session 下的消息。

字段：

- message id
- sessionId
- role
- text
- createdAt
- senderRoleId
- senderRoleName
- senderRoleAvatar
- imageBase64
- attachmentsJson
- logLinesJson

操作：

- 查看
- 复制
- 重新发送
- 从这里继续
- 删除整段会话
- 导出消息

注意：

- 当前表未提供单条消息删除能力，后续如要支持，需要补 DAO。
- `imageBase64` 只适合小图或历史兼容，大图应落盘为附件路径。

##### C. 附件与卡片

展示消息中携带的结构化附件。

字段：

- type
- path
- name
- mimeType
- sizeBytes
- title
- url
- excerpt
- query
- engine
- pages
- localPath

附件类型：

- `ImageData`
- `FileData`
- `HtmlData`
- `WebPage`
- `SearchResults`
- `ActionCard`
- `FileList`

操作：

- 打开
- 分享
- 保存
- 复制链接
- 定位产物
- 导出附件

注意：

- 附件引用不等于产物本体。
- 文件和 HTML 如果是 AI 创建的，应在工作产物区域也能找到。
- 网页和搜索结果是外部引用，导入导出时不保证可离线恢复。

##### D. 运行日志

展示 AI 执行过程中的用户可见日志。

字段：

- entryId
- type
- text
- skillId
- details
- startedAt
- finishedAt
- isRunning

操作：

- 展开查看
- 复制
- 导出
- 定位技能
- 定位错误

注意：

- 保存时已经剥离日志里的图片 base64。
- 日志是恢复任务和解释行为的依据。
- 日志不是系统内部 prompt，也不是完整 agent trace。

##### E. 会话角色

记录会话和消息中的角色信息。

字段：

- session.roleId
- message.senderRoleId
- message.senderRoleName
- message.senderRoleAvatar
- current role snapshot

操作：

- 查看当时发送角色
- 跳转角色详情
- 按角色过滤消息
- 修复缺失角色引用

注意：

- 会话 `roleId` 是会话级默认角色。
- AI 回复的 senderRole 是消息级快照。
- 如果角色后来改名，历史消息仍应保留当时的展示快照。

##### F. 会话关联产物

展示会话中产生或引用的工作产物。

字段：

- artifact type
- artifact id
- title
- source message id
- attachment path
- linked workspace id
- createdAt

操作：

- 打开产物
- 继续编辑
- 导出产物
- 从消息定位产物

注意：

- 产物本体归工作产物。
- 会话记录只保留消息中的引用和上下文。
- 导出会话时可选择是否连同产物一起导出。

##### G. 会话关联 Workspace

展示会话触发的任务工作空间。

字段：

- workspace id
- title
- goal
- scope
- linked artifacts
- latest checkpoint
- latest event
- status

操作：

- 打开 workspace
- 查看 checkpoint
- 继续任务
- 导出任务过程

注意：

- workspace 不是会话本体。
- 会话导出默认只导出 workspace 引用。
- 如果用户选择“完整任务包”，才导出 workspace 过程记录。

##### H. 轻量 Conversation Memory

记录用于画像提取和近期上下文的轻量消息窗口。

字段：

- conversation id
- role
- content
- taskId
- source
- createdAt

操作：

- 查看摘要
- 清理
- 导出可选

注意：

- `conversations` 不是完整会话记录。
- 它用于 recent context、profile extraction、VLM observation 等轻量用途。
- 它会截断文本，不应作为用户完整聊天归档。

#### 3.4.5 数据来源

现有数据来源：

- `SessionEntity`
- `SessionMessageEntity`
- `ConversationMemory`
- `WorkspaceRuntimeCoordinator`
- `SessionDao`
- `SessionMessageDao`
- `ConversationEntity`
- `SkillAttachment`
- `ChatMessage`
- `LogLine`
- `WorkspaceStore`
- `WorkspaceRuntimeRecorder`
- `TaskRouter`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 单聊 session | `sessions` |
| 单聊消息 | `session_messages` |
| 会话默认角色 | `SessionEntity.roleId` |
| 消息发送角色快照 | `SessionMessageEntity.senderRoleId/name/avatar` |
| 消息附件 | `SessionMessageEntity.attachmentsJson` |
| 消息运行日志 | `SessionMessageEntity.logLinesJson` |
| 历史图片字段 | `SessionMessageEntity.imageBase64` |
| 轻量上下文 | `conversations` |
| 任务 workspace 关联 | `WorkspaceRuntimeCoordinator`, `WorkspaceStore` |
| 产物引用 | `SkillAttachment`, `WorkspaceArtifactLink` |
| 续写/任务类型推断 | `TaskRouter` |

#### 3.4.6 文件路径

当前会话核心数据主要在 Room 数据库中：

```text
Room database: sessions
Room database: session_messages
Room database: conversations
```

会话附件可能引用这些路径：

```text
filesDir/chat_images/
filesDir/workspace_image_inputs/
filesDir/documents/
filesDir/videos/
filesDir/apps/
filesDir/ai_pages/
externalFilesDir/created_files/
externalFilesDir/html_pages/
```

后续为了总工作空间导入导出，建议生成可读导出结构：

```text
workspace_export/sessions/session_manifest.json
workspace_export/sessions/{sessionId}/session.json
workspace_export/sessions/{sessionId}/messages.json
workspace_export/sessions/{sessionId}/messages.md
workspace_export/sessions/{sessionId}/attachments/
workspace_export/sessions/{sessionId}/workspace_links.json
workspace_export/sessions/{sessionId}/artifact_links.json
workspace_export/sessions/conversation_memory.json
```

建议 manifest 字段：

```json
{
  "schemaVersion": 1,
  "area": "sessions",
  "createdAt": 0,
  "sessionCount": 0,
  "messageCount": 0,
  "sessions": [
    {
      "id": "session_xxx",
      "title": "Example",
      "roleId": "general",
      "createdAt": 0,
      "updatedAt": 0,
      "messageFile": "session_xxx/messages.json",
      "markdownFile": "session_xxx/messages.md",
      "linkedWorkspaceIds": [],
      "linkedArtifactIds": []
    }
  ],
  "exportPolicy": {
    "includeAttachments": true,
    "includeRunLogs": true,
    "includeConversationMemory": false,
    "includeLinkedArtifacts": "references_only",
    "includeLinkedWorkspaces": "references_only"
  }
}
```

#### 3.4.7 数据库表

当前核心表：

- `sessions`
- `session_messages`
- `conversations`

`sessions` 字段：

- id
- title
- roleId
- createdAt
- updatedAt

`session_messages` 字段：

- id
- sessionId
- role
- text
- logLinesJson
- attachmentsJson
- imageBase64
- senderRoleId
- senderRoleName
- senderRoleAvatar
- createdAt

`conversations` 字段：

- id
- role
- content
- embedding
- taskId
- source
- createdAt

后续如果会话关联复杂，可以考虑新增：

- `session_artifact_links`
- `session_workspace_links`
- `session_message_reactions`
- `session_exports`

但当前阶段优先从消息附件和 workspace link 反查。

#### 3.4.8 导出范围

会话导出包建议包含：

- session 元数据
- message 列表
- 消息文本
- 消息角色
- 发送角色快照
- 附件引用
- 可选附件文件
- 运行日志摘要
- workspace 引用
- artifact 引用

默认不包含：

- 角色完整档案
- 用户长期记忆
- 工作产物本体
- 任务 workspace 完整过程
- 群聊消息
- 群成员身份
- 玩法局状态
- token/key/secret

可选包含：

- 附件文件
- 运行日志全文
- 关联工作产物本体
- 关联任务 workspace
- conversation memory
- Markdown 聊天记录

导出粒度：

- 单个会话导出
- 多个会话导出
- 按角色导出
- 按时间范围导出
- 按是否含产物导出

#### 3.4.9 导入策略

导入会话时需要处理：

- session id 冲突
- message id 冲突
- roleId 不存在
- senderRoleId 不存在
- 附件文件缺失
- 产物引用缺失
- workspace 引用缺失
- 绝对路径失效
- schema version 不一致

建议导入选项：

1. 创建新会话
2. 覆盖已有同 id 会话
3. 合并到已有会话
4. 只导入消息文本
5. 连同附件一起导入
6. 只保留产物/workspace 引用
7. 连同关联产物一起导入
8. 连同关联 workspace 一起导入

导入规则：

- message id 默认重新生成。
- session id 冲突默认创建副本。
- 角色缺失时保留历史 senderRoleName/avatar。
- 附件路径导入后重写为本机路径。
- 产物缺失时保留弱引用并标记 missing。
- workspace 缺失时保留弱引用并标记 missing。

#### 3.4.10 敏感信息

可能敏感：

- 用户消息全文
- AI 回复全文
- 上传图片
- 生成文件附件
- 搜索结果
- 网页摘要
- 运行日志
- 本地文件路径
- 任务目标
- 角色发送记录

处理原则：

- 导出前给出隐私提示。
- 默认不导出 token/key/secret。
- 默认不展开大型图片 base64。
- 附件导出需要用户确认。
- 绝对路径可脱敏或改为相对路径。
- 运行日志可单独开关。
- conversation memory 默认不随完整会话导出，除非用户选择调试数据。

#### 3.4.11 冲突处理

导入时可能冲突：

- session id 已存在
- 会话标题重复
- 消息时间戳重叠
- 角色引用缺失
- 附件同名但内容不同
- 产物引用指向已有不同产物
- workspace 引用指向已有不同 workspace

建议处理方式：

- session id 冲突：默认创建新 id。
- 标题重复：允许重复，但追加导入标记。
- 消息 id 冲突：重新生成。
- 角色缺失：保留快照并标记 role missing。
- 附件冲突：按 hash 去重，否则追加后缀。
- 产物冲突：保留引用并提示用户选择是否关联本地产物。
- workspace 冲突：保留弱引用，不自动覆盖。

#### 3.4.12 UI 展示建议

建议 UI 名称：

- 会话记录

一级入口卡片展示：

- 会话总数
- 消息总数
- 最近会话
- 含附件会话数量
- 含产物会话数量
- 含任务 workspace 会话数量
- 可清理旧会话数量

会话列表展示：

- 标题
- 默认角色头像
- 最近消息摘要
- 更新时间
- 消息数量
- 附件标记
- 产物标记
- workspace 标记

会话详情展示：

- 消息时间线
- 发送角色
- 附件卡片
- 运行日志折叠区
- 关联产物入口
- 关联 workspace 入口
- 导出入口
- 删除入口

建议分类：

- 全部
- 最近
- 按角色
- 含附件
- 含产物
- 含任务
- 已归档

注意：

- 不要把会话记录做成工作产物列表。
- 不要把群聊混进单聊会话。
- 对“继续这个任务”应跳转到相关 workspace 或产物。
- 对“继续这个对话”应恢复 session。

#### 3.4.13 搜索与排序

搜索字段：

- session title
- message text
- senderRoleName
- attachment name
- webpage title
- search query
- skillId

排序字段：

- 最近更新
- 创建时间
- 消息数量
- 附件数量
- 产物数量
- 默认角色

筛选条件：

- 角色
- 时间范围
- 是否含附件
- 是否含运行日志
- 是否含产物
- 是否含 workspace
- 是否含图片
- 是否含网页搜索

#### 3.4.14 空状态

空状态文案建议：

```text
还没有会话记录。
你和 AI 的单聊消息会保存在这里，方便之后继续对话或回看产物来源。
```

如果搜索无结果：

```text
没有找到匹配的会话。
可以换个关键词，或按角色和时间筛选。
```

如果会话引用缺失：

```text
这条会话里有一些附件或产物引用已经不可用。
你仍然可以查看消息文本。
```

#### 3.4.15 后续待定问题

- 是否需要支持单条消息删除？
- 会话是否需要归档状态？
- `conversations` 是否要在 UI 中暴露，还是只作为调试数据？
- 会话和 workspace 的关联是否需要显式表？
- 会话和 artifact 的关联是否需要显式表？
- 历史消息 senderRole 是否应保留完整角色快照？
- 大图片是否彻底移出 `imageBase64`，统一走文件附件？
- 导出 Markdown 时如何呈现附件和运行日志？
- 会话清理是否同步清理孤儿附件？
- 群聊消息是否要在统一搜索中出现，但详情仍跳到群聊区域？

导出建议：

- 支持按会话导出
- 支持按角色/时间范围导出
- 支持 Markdown 和 JSON 双格式
- 支持附件可选迁移
- 支持运行日志开关
- 支持关联产物/任务 workspace 仅引用或完整打包
- 不在这里导出群聊成员身份、群玩法和群关系

### 3.5 群聊区域

用于管理当前已经存在的群聊工作区，包括群配置、成员角色、群消息、附件、群内调度状态和群聊历史备份。

当前阶段群聊区域先不设计完整玩法系统，也不提前承载狼人杀等未来玩法。

它更像是：

> 一个由用户和多个 AI 角色共同参与的多人对话工作区。

它应该回答：

- 当前有哪些群？
- 每个群有哪些 AI 角色成员？
- 群里发生过哪些消息？
- 哪些角色正在回复或执行？
- 群消息里有哪些附件和产物引用？
- 群消息是否有数据库记录和备份记录？
- 当前群是否绑定过任务 workspace？
- 这个群以后能否挂载玩法，但当前不展开玩法状态？

包含：

- 群聊列表
- 群聊基础信息
- 群成员配置
- 每个群成员绑定的角色
- 群聊消息
- 群消息附件
- 群消息发送者快照
- 群聊预览
- 群聊分页历史
- 群聊备份历史
- 群内角色调度
- 正在输入/正在工作状态
- 待回复队列
- 群聊关联任务 workspace
- 群聊关联产物引用

#### 3.5.1 区域定位

群聊区域是“多人 AI 对话工作区”的总入口。

它承载：

- 群配置
- 群成员 role id 列表
- 用户作为隐式成员
- 群消息
- 消息发送者快照
- 消息附件
- 群列表预览
- 群历史分页
- 群历史备份
- 群 turn 调度状态
- 群执行结果
- 群和 workspace 的绑定关系

它不应该在当前阶段承载：

- 玩法规则
- 玩法局状态
- 狼人杀身份牌
- 群内复杂关系图谱
- 群长期共同记忆系统
- 角色私有记忆
- 角色档案完整内容
- 普通单聊 session

这些内容分别归入：

- 玩法空间
- AI 角色
- 我的记忆
- 会话记录
- 工作产物

#### 3.5.2 用户能看到什么

用户应该能看到：

- 群聊列表
- 群名称
- 群 emoji
- 成员角色
- 最近消息预览
- 未读数量
- 当前打开的群
- 群消息列表
- 消息发送者名称
- 消息发送者头像
- 消息附件
- 群内正在输入的角色
- 群内正在工作的角色
- 待回复消息
- 是否正在运行
- 历史是否还有更多

用户不应该默认看到：

- 完整角色 system prompt
- 群调度内部评分
- LLM 原始 tool schema
- 群 turn 队列内部细节
- 未来玩法的未完成状态
- 角色私有记忆

高级视图可以展示：

- group id
- memberRoleIds
- group message id
- attachmentsJson
- backup jsonl 状态
- pending queue 摘要
- workspace binding
- 最近任务类型推断

#### 3.5.3 AI 能读写什么

AI 可以读取：

- 当前群配置
- 群成员角色列表
- 群消息上下文
- 群消息附件摘要
- 群内最近调度状态
- 当前群关联的 workspace 摘要
- 角色自己的档案摘要
- 用户长期记忆中与当前任务相关的部分

AI 可以写入：

- 群消息
- 群消息附件
- 群消息备份
- 群预览
- 群更新时间
- 群 turn 调度状态
- 群关联 workspace 的任务记录

AI 不应随意写入：

- 群成员列表
- 群配置删除
- 角色档案
- 用户长期记忆
- 玩法局状态
- 群以外的单聊 session

AI 在群聊中需要遵守：

- 用户作为隐式成员始终存在。
- 群成员由 `memberRoleIds` 决定。
- 每条 AI 消息要保存 sender 快照。
- 群聊调度只决定谁发言，不应改变角色长期身份。
- 群聊中产生的文件/页面/媒体仍归工作产物。
- 当前阶段不把玩法逻辑写入群聊核心数据。

#### 3.5.4 二级内容划分

建议群聊区域下再分这些二级内容：

##### A. 群聊列表

展示所有群聊。

字段：

- group id
- name
- emoji
- memberRoleIds
- createdAt
- updatedAt
- latest preview
- unreadCount
- message count

操作：

- 打开
- 新建
- 重命名
- 编辑成员
- 删除
- 导出

注意：

- 当前群配置主要是轻量 JSON。
- 用户是隐式成员，不需要写入 `memberRoleIds`。

##### B. 群成员

展示群内 AI 角色成员。

字段：

- role id
- role name
- role avatar
- role bubble style
- forcedSkillIds
- preferredTaskTypes
- member order

操作：

- 添加角色
- 移除角色
- 调整顺序
- 跳转角色详情

注意：

- 成员身份来自角色档案的当前定义。
- 群消息需要保留发送时的 senderName 和 senderAvatar 快照。
- 群里暂不单独维护复杂成员身份档案。

##### C. 群消息

展示群内消息时间线。

字段：

- message id
- groupId
- senderId
- senderName
- senderAvatar
- text
- attachmentsJson
- createdAt

操作：

- 查看
- 复制
- 继续讨论
- 打开附件
- 加载更早消息
- 导出消息

注意：

- 群消息是独立于单聊 session 的消息体系。
- 群消息不进入 `session_messages`。
- 群消息可在统一搜索中出现，但详情应回到群聊区域。

##### D. 群附件

展示群消息中的结构化附件。

字段：

- type
- path
- name
- mimeType
- sizeBytes
- title
- url
- excerpt
- query
- engine
- pages

附件类型：

- `ImageData`
- `FileData`
- `HtmlData`
- `WebPage`
- `SearchResults`
- `ActionCard`
- `FileList`

操作：

- 打开
- 分享
- 保存
- 定位工作产物
- 导出

注意：

- 附件引用不等于产物本体。
- AI 生成的附件如果是文件/页面/媒体，应同时能在工作产物中找到。

##### E. 群历史备份

展示群消息的备份记录。

字段：

- groupId
- jsonl path
- backup message count
- latest backup time
- compact status

操作：

- 查看备份状态
- 恢复最近消息
- 导出备份
- 清理备份

注意：

- 当前备份路径为 `filesDir/group_history/{groupId}.jsonl`。
- 备份主要用于防止数据库缺失或分页异常。
- 备份不是第二套群聊真相源，优先以数据库为准。

##### F. 群调度状态

展示群内角色如何被调度发言。

字段：

- isRunning
- typingAgents
- workingAgents
- pendingMessages
- pending turn roles
- trigger text
- chainDepth
- longTask
- requireResponse

操作：

- 查看当前状态
- 停止运行
- 继续触发
- 清空待回复队列

注意：

- 群调度状态是运行态，不一定需要完整导出。
- 重要的消息结果会落入 `group_messages`。
- 调度内部队列不应成为长期群关系或玩法状态。

##### G. 群 Workspace 绑定

展示群聊和任务 workspace 的绑定关系。

字段：

- groupId
- workspace id
- title
- goal
- scope
- linked artifacts
- latest checkpoint
- latest event

操作：

- 打开 workspace
- 继续任务
- 查看产物
- 导出 workspace 引用

注意：

- 群 workspace 记录任务现场。
- 群聊区域只保存群和 workspace 的关联入口。
- workspace 本体仍归任务工作空间/工作产物相关区域。

##### H. 玩法预留引用

当前只保留未来扩展入口。

字段：

- plannedPlayMode
- linkedPlaySpaceId
- enabled

当前阶段规则：

- 不设计玩法数据结构。
- 不保存狼人杀身份牌。
- 不保存局内回合状态。
- 不导出玩法局数据。
- 只在后续玩法空间设计完成后再接入。

#### 3.5.5 数据来源

现有数据来源：

- `GroupManager`
- `GroupMessageEntity`
- `GroupConversationStore`
- `GroupHistoryStore`
- `GroupTurnScheduler`
- `GroupTurnExecutor`
- `GroupPrompting`
- `Role`
- `ChatBubbleStyle`
- `WorkspaceRuntimeCoordinator.ensureGroupBinding(...)`
- `Group`
- `GroupMessage`
- `GroupUiState`
- `GroupMessageDao`
- `SkillAttachment`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 群配置 | `GroupManager`, `filesDir/groups/{groupId}.json` |
| 群成员 | `Group.memberRoleIds` |
| 群消息 | `group_messages` |
| 群消息附件 | `GroupMessageEntity.attachmentsJson` |
| 群消息发送者快照 | `senderId`, `senderName`, `senderAvatar` |
| 群预览 | `GroupConversationStore.loadPreviews(...)` |
| 群分页历史 | `GroupConversationStore.openGroup(...)`, `loadOlder(...)` |
| 群历史备份 | `GroupHistoryStore`, `filesDir/group_history/{groupId}.jsonl` |
| 群 turn 调度 | `GroupTurnScheduler` |
| 群 turn 执行 | `GroupTurnExecutor` |
| 群 UI 运行态 | `GroupUiState` |
| 群 workspace 绑定 | `WorkspaceRuntimeCoordinator.ensureGroupBinding(...)` |

#### 3.5.6 文件路径

当前相关路径：

```text
filesDir/groups/{groupId}.json
filesDir/group_history/{groupId}.jsonl
```

群消息核心数据在 Room 数据库中：

```text
Room database: group_messages
```

群消息附件可能引用：

```text
filesDir/chat_images/
filesDir/workspace_image_inputs/
filesDir/documents/
filesDir/videos/
filesDir/apps/
filesDir/ai_pages/
externalFilesDir/created_files/
externalFilesDir/html_pages/
```

后续为了总工作空间导入导出，建议生成可读导出结构：

```text
workspace_export/groups/group_manifest.json
workspace_export/groups/{groupId}/group.json
workspace_export/groups/{groupId}/messages.json
workspace_export/groups/{groupId}/messages.md
workspace_export/groups/{groupId}/attachments/
workspace_export/groups/{groupId}/history_backup.jsonl
workspace_export/groups/{groupId}/workspace_links.json
workspace_export/groups/{groupId}/artifact_links.json
```

建议 manifest 字段：

```json
{
  "schemaVersion": 1,
  "area": "groups",
  "createdAt": 0,
  "groupCount": 0,
  "messageCount": 0,
  "groups": [
    {
      "id": "group_xxx",
      "name": "产品讨论组",
      "emoji": "group",
      "memberRoleIds": ["planner", "coder"],
      "createdAt": 0,
      "updatedAt": 0,
      "messageFile": "group_xxx/messages.json",
      "markdownFile": "group_xxx/messages.md",
      "backupFile": "group_xxx/history_backup.jsonl",
      "linkedWorkspaceIds": [],
      "linkedArtifactIds": []
    }
  ],
  "exportPolicy": {
    "includeMessages": true,
    "includeAttachments": true,
    "includeHistoryBackup": false,
    "includeLinkedArtifacts": "references_only",
    "includeLinkedWorkspaces": "references_only",
    "includeRoleArchives": false,
    "includePlaySpace": false
  }
}
```

#### 3.5.7 数据库表

当前核心表：

- `group_messages`

字段：

- id
- groupId
- senderId
- senderName
- senderAvatar
- text
- attachmentsJson
- createdAt

群配置不在数据库表中，而是在文件系统：

- `filesDir/groups/{groupId}.json`

后续如果群功能变复杂，可以考虑新增：

- `groups`
- `group_members`
- `group_workspace_links`
- `group_artifact_links`

但当前阶段不急着迁移，优先把现有文件 + 消息表纳入总工作空间。

#### 3.5.8 导出范围

群聊导出包建议包含：

- 群配置
- 群成员 role id 引用
- 群消息
- 消息发送者快照
- 附件引用
- 可选附件文件
- workspace 引用
- artifact 引用

默认不包含：

- 角色完整档案
- 用户长期记忆
- 角色私有记忆
- 玩法规则
- 玩法局状态
- 群调度运行队列
- 群历史备份 jsonl
- 工作产物本体
- 任务 workspace 完整过程
- token/key/secret

可选包含：

- 附件文件
- 群历史备份
- 关联工作产物本体
- 关联任务 workspace
- 角色档案副本
- Markdown 群聊记录

导出粒度：

- 单个群导出
- 多个群导出
- 按时间范围导出群消息
- 只导出群配置
- 导出群配置 + 消息
- 导出群配置 + 消息 + 附件

#### 3.5.9 导入策略

导入群聊时需要处理：

- group id 冲突
- 群名称重复
- 成员 role id 不存在
- message id 冲突
- 附件文件缺失
- workspace 引用缺失
- artifact 引用缺失
- 绝对路径失效
- schema version 不一致

建议导入选项：

1. 创建新群
2. 覆盖已有同 id 群
3. 只导入群配置
4. 导入群配置和消息
5. 连同附件一起导入
6. 缺失角色只保留 role id
7. 缺失角色时导入角色档案副本
8. 只保留 workspace/artifact 引用

导入规则：

- group id 冲突默认创建副本。
- message id 默认重新生成。
- 成员角色缺失时保留 role id 并标记 missing。
- 历史 senderName/senderAvatar 必须保留。
- 附件路径导入后重写为本机路径。
- workspace 和 artifact 缺失时保留弱引用。
- 不导入玩法状态。

#### 3.5.10 敏感信息

可能敏感：

- 群消息全文
- 群内附件
- 群内生成文件
- 搜索结果
- 网页摘要
- 角色发言风格
- 群成员组合
- 本地文件路径
- 任务目标

处理原则：

- 导出前给出隐私提示。
- 附件导出需要用户确认。
- 绝对路径可脱敏或改为相对路径。
- 默认不导出角色完整档案。
- 默认不导出历史备份 jsonl。
- 默认不导出玩法相关数据。
- 群消息中出现 token/key/secret 时需要敏感扫描。

#### 3.5.11 冲突处理

导入时可能冲突：

- group id 已存在
- 群名称重复
- memberRoleIds 中角色缺失
- 消息时间戳重叠
- 附件同名但内容不同
- 备份历史和数据库消息重复
- workspace 引用指向不存在内容
- artifact 引用指向不存在内容

建议处理方式：

- group id 冲突：默认创建新 id。
- 群名称重复：允许重复，但追加导入标记。
- 角色缺失：保留 role id，UI 提示补齐。
- 消息 id 冲突：重新生成。
- 消息重复：按 groupId/senderId/createdAt/text 去重。
- 附件冲突：按 hash 去重，否则追加后缀。
- 备份重复：默认不导入备份，除非用户选择。
- workspace/artifact 缺失：保留弱引用并标记 missing。

#### 3.5.12 UI 展示建议

建议 UI 名称：

- 群聊区域

一级入口卡片展示：

- 群数量
- 最近活跃群
- 总消息数
- 含附件群数量
- 正在运行群数量
- 缺失角色引用数量

群列表展示：

- 群 emoji
- 群名称
- 成员头像
- 最近消息预览
- 更新时间
- 未读数量

群详情展示：

- 群消息时间线
- 成员列表
- 正在输入/工作状态
- 待回复队列
- 附件卡片
- 关联 workspace 入口
- 关联产物入口
- 导出入口

建议分类：

- 全部
- 最近
- 含附件
- 含任务
- 缺失成员

注意：

- 当前阶段不要在 UI 中暴露复杂玩法配置。
- 群聊详情应优先服务“多人对话继续进行”。
- 群中产物入口应跳到工作产物。
- 群中角色入口应跳到角色详情。

#### 3.5.13 搜索与排序

搜索字段：

- group name
- member role name
- message text
- senderName
- attachment name
- webpage title
- search query

排序字段：

- 最近更新
- 创建时间
- 消息数量
- 成员数量
- 附件数量
- 未读数量

筛选条件：

- 成员角色
- 时间范围
- 是否含附件
- 是否含 workspace
- 是否含产物
- 是否缺失角色

#### 3.5.14 空状态

空状态文案建议：

```text
还没有群聊。
你可以创建一个群，把多个 AI 角色放在一起讨论同一个问题。
```

如果群里没有消息：

```text
这个群还没有消息。
发一条消息后，群内角色会按当前调度策略参与讨论。
```

如果群成员缺失：

```text
这个群里有角色引用已经不可用。
你可以重新添加角色，或保留历史消息继续查看。
```

#### 3.5.15 后续待定问题

- 群配置是否需要迁移到 Room 表？
- 是否需要显式 `group_members` 表？
- 是否需要显式 `group_workspace_links` 表？
- 是否需要显式 `group_artifact_links` 表？
- 群历史备份 jsonl 是否应该默认导出？
- 群消息是否支持单条删除？
- 群内是否需要长期共享记忆？
- 群内成员是否需要独立身份档案？
- 群聊和玩法空间的连接点如何设计？
- 狼人杀等玩法是否应以独立玩法空间挂载到群，而不是写入群核心数据？

导出建议：

- 支持按群导出
- 支持只导出群配置
- 支持导出群消息历史
- 支持导出群成员角色引用
- 支持附件可选迁移
- 支持 Markdown 和 JSON 双格式
- 群聊中引用的角色可选择“仅引用”或“连同角色档案一起导出”
- 当前阶段不导出玩法数据
- 当前阶段不设计群玩法状态

### 3.6 技能库

用于管理 MobileClaw 的能力库。

技能库是 AI 可调用能力的总入口，不是 MCP 连接管理页，也不是工作产物列表。

它应该回答：

- 当前有哪些技能可用？
- 哪些技能是内置的？
- 哪些技能是用户安装或 AI 创建的？
- 哪些技能会默认注入给模型？
- 哪些技能只在任务需要时出现？
- 每个技能属于哪个能力分类？
- 用户对技能写了哪些备注？
- 哪些角色固定偏好或强制使用某些技能？
- 技能能否导出、导入、迁移或删除？

包含：

- 内置 skills
- 用户安装的 skills
- 动态创建的 skills
- skill 市场记录
- skill 注释
- skill injection level
- skill 分类
- skill 使用说明
- skill 运行器配置
- skill 推荐列表

#### 3.6.1 区域定位

技能库区域是“MobileClaw 能力资产”的总入口。

它承载：

- 内置原生技能
- 用户安装技能
- AI 动态创建技能
- 技能市场条目
- 技能元数据
- 技能参数 schema
- 技能分类
- 技能标签
- 技能注入等级
- 技能备注
- 技能等级覆盖
- 技能运行器配置
- 角色与技能的引用关系

它不应该承载：

- MCP endpoint 连接状态
- MCP token
- 模型 API Key
- 技能运行产生的文件产物
- 技能调用日志全文
- 角色长期记忆
- 工作产物本体

这些内容分别归入：

- MCP 连接
- 模型与网关
- 工作产物
- 会话记录 / 任务工作空间
- AI 角色

#### 3.6.2 用户能看到什么

用户应该能看到：

- 技能列表
- 技能名称
- 中文名称
- 描述
- 中文描述
- 技能 id
- 技能类型
- 是否内置
- 是否用户安装
- 是否内部工具
- 注入等级
- 有效注入等级
- 分类
- 标签
- 参数列表
- 版本
- minApiLevel
- 用户备注
- 是否可删除
- 是否可导出
- 是否来自市场
- 是否来自 MCP

用户不应该默认看到：

- HTTP headers 中的 token
- MCP headers
- 过长的 executor config
- Python 脚本全文
- 内置技能实现代码
- 内部工具列表

高级视图可以展示：

- 完整 `SkillMeta`
- `SkillDefinition`
- `httpConfig`
- `mcpConfig`
- `script`
- taxonomy 推断结果
- level override 来源
- 注册状态
- 校验错误

#### 3.6.3 AI 能读写什么

AI 可以读取：

- 可见技能元数据
- 技能参数 schema
- 技能分类
- 技能注入等级
- 用户技能备注
- 动态技能定义
- 市场技能目录
- 角色固定技能引用

AI 可以写入：

- 动态技能定义
- 技能备注
- 动态技能注入等级
- 技能等级覆盖
- 市场技能安装结果
- MCP 工具转化出的动态技能

AI 不应随意写入：

- 内置技能代码
- shell/native 动态技能
- 未经用户确认的高风险技能
- token/key/secret 明文配置
- 角色 forcedSkillIds
- 全局模型网关配置

AI 管理技能时需要遵守：

- 动态技能默认 `injectionLevel=2`，用户确认后再提升。
- AI 创建技能只允许 HTTP/Python，当前 `MetaSkill` 禁止 Shell/Native。
- MCP 工具可以被安装成动态 MCP 技能，但连接配置归 MCP 连接区域。
- 内置技能不删除，只允许配置或隐藏策略。
- 技能运行结果不归技能库，归会话记录、任务 workspace 或工作产物。

#### 3.6.4 二级内容划分

建议技能库区域下再分这些二级内容：

##### A. 技能总表

展示当前注册的所有用户可见技能。

字段：

- id
- name
- nameZh
- description
- descriptionZh
- type
- injectionLevel
- effectiveInjectionLevel
- isBuiltin
- internalTool
- version
- minApiLevel
- categories
- tags
- parameter count

操作：

- 查看详情
- 调整注入等级
- 添加备注
- 删除动态技能
- 导出动态技能
- 跳转关联角色

注意：

- `internalTool=true` 的技能默认不在普通用户列表中展示。
- `SkillRegistry.userVisibleWithEffectiveLevel()` 应作为 UI 主列表来源。

##### B. 内置技能

展示随 App 一起发布的原生技能。

字段：

- id
- SkillMeta
- categories
- injectionLevel
- version
- minApiLevel
- source module

操作：

- 查看
- 添加备注
- 设置等级覆盖
- 恢复默认等级

注意：

- 内置技能不导出实现代码。
- 内置技能不允许删除。
- 导出时只导出用户配置和等级覆盖。

##### C. 动态技能

展示存储在本机的用户安装/AI 创建技能。

字段：

- `SkillDefinition`
- meta
- script
- httpConfig
- mcpConfig
- file path
- createdAt
- updatedAt

操作：

- 查看
- 编辑
- 删除
- 提升
- 降级
- 导出完整定义
- 重新加载

类型：

- HTTP
- PYTHON
- MCP

注意：

- 动态技能存储在 `filesDir/skills/{skillId}.json`。
- `SkillLoader` 不允许动态加载 NATIVE。
- `MetaSkill` 不允许生成 SHELL/NATIVE。
- `SkillLoader` 当前也禁止动态 SHELL。

##### D. 技能市场

展示可一键安装的技能目录。

字段：

- category
- entry title
- market entry emoji/icon
- SkillDefinition
- install status
- source

操作：

- 浏览推荐
- 按分类查看
- 安装
- 查看详情
- 标记已安装

注意：

- 市场目录本身是内置 catalog。
- 安装后会落为动态技能。
- 市场技能默认 `injectionLevel=2`。
- 推荐列表应该不依赖搜索才能看到。

##### E. 技能备注

展示用户为技能写的说明、经验和注意事项。

字段：

- skill id
- note
- updatedAt

操作：

- 查看
- 编辑
- 删除
- 导出

注意：

- 当前备注存在 DataStore `skill_notes`。
- 备注是用户可见说明，不等于角色技能经验。
- 角色对技能的使用经验应该归角色档案。

##### F. 注入等级

管理技能进入模型上下文的策略。

字段：

- default injectionLevel
- override injectionLevel
- effective injectionLevel
- source

等级含义：

- `0`: always
- `1`: by task type
- `2`: on-demand

操作：

- 设置等级
- 恢复默认
- 筛选等级
- 导出覆盖配置

注意：

- 动态技能提升/降级会修改技能定义本体。
- 内置技能等级覆盖存在 `SkillLevelStore`。
- UI 应展示默认等级和实际生效等级。

##### G. 技能分类与标签

展示技能所属能力域。

分类：

- CHAT
- MEMORY
- SKILL
- SELF_EVOLUTION
- ARTIFACT
- PHONE
- WEB
- MEDIA
- VPN
- CODE
- SYSTEM

字段：

- explicit categories
- inferred categories
- tags
- primary category

操作：

- 按分类查看
- 按标签搜索
- 修正动态技能分类

注意：

- `SkillToolTaxonomy` 会为缺失分类的技能做推断。
- 动态技能应尽量写明 categories，减少误路由。

##### H. 角色技能引用

展示角色如何引用技能。

字段：

- role id
- role name
- forcedSkillIds
- preferredTaskTypes
- skill id
- missing status

操作：

- 查看引用角色
- 跳转角色详情
- 解绑缺失技能
- 导出角色时仅引用或连同技能导出

注意：

- 角色技能偏好本体属于 AI 角色区域。
- 技能库只提供反向索引和缺失检查。

##### I. MCP 派生技能

展示由 MCP 工具安装成的动态技能。

字段：

- skill id
- type = MCP
- endpoint
- tool
- headers
- defaultArguments
- source MCP

操作：

- 查看
- 删除
- 导出非敏感定义
- 跳转 MCP 连接

注意：

- MCP 派生技能属于技能库的可调用能力。
- MCP endpoint、连接状态、token 管理归 MCP 连接区域。
- 导出时 headers 中敏感值默认脱敏。

#### 3.6.5 数据来源

现有数据来源：

- `SkillRegistry`
- `SkillLoader`
- `SkillMarket`
- `SkillNotesStore`
- `SkillLevelStore`
- `MetaSkill`
- `QuickSkillSkill`
- `SkillMarketSkill`
- `SkillNotesSkill`
- `SkillMeta`
- `SkillDefinition`
- `SkillToolTaxonomy`
- `SkillToolCategory`
- `TaskToolPolicy`
- `Role.forcedSkillIds`
- `Role.preferredTaskTypes`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 注册技能 | `SkillRegistry` |
| 用户可见技能 | `SkillRegistry.userVisibleWithEffectiveLevel()` |
| 动态技能文件 | `SkillLoader`, `filesDir/skills/{skillId}.json` |
| 动态技能定义 | `SkillDefinition` |
| 技能元数据 | `SkillMeta` |
| 技能市场 | `SkillMarket.catalog` |
| 技能备注 | `SkillNotesStore`, DataStore `skill_notes` |
| 技能等级覆盖 | `SkillLevelStore`, DataStore `skill_levels` |
| AI 创建技能 | `MetaSkill` / `create_skill` |
| 快速技能 | `QuickSkillSkill` / `quick_skill` |
| 市场安装 | `SkillMarketSkill` / `skill_market` |
| 技能分类 | `SkillToolTaxonomy` |
| 任务选工具 | `TaskToolPolicy` |
| 角色固定技能 | `Role.forcedSkillIds` |

#### 3.6.6 文件路径

当前相关路径：

```text
filesDir/skills/{skillId}.json
DataStore: skill_notes
DataStore: skill_levels
```

动态技能 JSON 结构：

```json
{
  "meta": {
    "id": "weather_cn",
    "name": "Weather Query",
    "description": "Gets current weather...",
    "type": "http",
    "injectionLevel": 2,
    "isBuiltin": false,
    "version": "1.0.0",
    "tags": ["生活"],
    "categories": ["WEB"]
  },
  "httpConfig": {
    "url": "https://example.com",
    "method": "GET",
    "headers": {}
  }
}
```

后续为了总工作空间导入导出，建议生成可读导出结构：

```text
workspace_export/skills/skill_manifest.json
workspace_export/skills/dynamic/{skillId}.json
workspace_export/skills/config/skill_notes.json
workspace_export/skills/config/skill_levels.json
workspace_export/skills/config/market_installed.json
workspace_export/skills/index/by_category.json
workspace_export/skills/index/by_role.json
```

建议 manifest 字段：

```json
{
  "schemaVersion": 1,
  "area": "skills",
  "createdAt": 0,
  "skillCount": 0,
  "dynamicSkillCount": 0,
  "builtinConfigCount": 0,
  "skills": [
    {
      "id": "weather_cn",
      "type": "http",
      "isBuiltin": false,
      "version": "1.0.0",
      "definitionFile": "dynamic/weather_cn.json",
      "categories": ["WEB"],
      "exportPolicy": {
        "includeDefinition": true,
        "includeSecrets": false
      }
    }
  ],
  "exportPolicy": {
    "includeBuiltinDefinitions": false,
    "includeDynamicDefinitions": true,
    "includeNotes": true,
    "includeLevelOverrides": true,
    "includeSecrets": false
  }
}
```

#### 3.6.7 数据库表

当前技能库核心数据不在 Room 数据库中。

当前存储：

- 动态技能：文件系统
- 技能备注：DataStore
- 技能等级覆盖：DataStore
- 内置技能：代码注册
- 市场目录：代码 catalog

后续如果技能市场和使用统计复杂，可以考虑新增：

- `skills`
- `skill_installs`
- `skill_usage_events`
- `skill_market_entries`
- `skill_role_links`

但当前阶段优先沿用文件 + DataStore。

#### 3.6.8 导出范围

技能库导出包建议包含：

- 动态技能完整定义
- 技能备注
- 技能等级覆盖
- 市场安装记录
- 技能分类索引
- 角色引用索引

默认不包含：

- 内置技能实现代码
- internalTool 技能
- token/key/secret
- MCP headers 明文
- HTTP headers 敏感值
- 技能运行日志
- 技能运行产物
- pip_packages

可选包含：

- 内置技能用户配置
- 动态 Python 脚本
- 动态 HTTP 配置
- 动态 MCP skill 定义
- 市场 catalog 快照
- 技能使用统计

导出粒度：

- 单个动态技能导出
- 全部动态技能导出
- 技能配置导出
- 技能库完整导出
- 随角色一起导出被引用技能

#### 3.6.9 导入策略

导入技能时需要处理：

- skill id 冲突
- 内置技能同 id
- 动态技能同 id
- type 不支持
- minApiLevel 不兼容
- Python 脚本不可运行
- HTTP endpoint 不可达
- MCP endpoint 不可达
- headers 中包含敏感信息
- categories 缺失
- schema version 不一致

建议导入选项：

1. 创建副本
2. 覆盖已有动态技能
3. 只导入备注和等级覆盖
4. 只导入动态技能定义
5. 不导入敏感 headers
6. 导入后设为 level 2
7. 导入后立即测试
8. 跳过不兼容技能

导入规则：

- 与内置技能 id 冲突：默认拒绝覆盖，创建副本。
- 与动态技能 id 冲突：默认创建副本。
- 动态技能导入后默认 `injectionLevel=2`，除非用户确认。
- minApiLevel 高于当前设备时导入为 disabled/unavailable。
- 缺失 categories 时重新走 taxonomy 推断。
- 敏感 headers 默认不导入明文。
- MCP 派生技能导入后需要重新验证连接。

#### 3.6.10 敏感信息

可能敏感：

- HTTP headers
- MCP headers
- endpoint
- Python 脚本内容
- 用户技能备注
- 技能参数默认值
- 角色技能偏好

绝对不应默认导出：

- API Key
- token
- secret
- password
- credential
- Authorization header
- Cookie

处理原则：

- 导出前敏感扫描。
- headers 默认脱敏。
- endpoint 可导出，但需要提示。
- Python 脚本可导出，但提示可能包含私有逻辑。
- 内置技能代码不导出。
- 动态技能导入后默认低注入等级。

#### 3.6.11 冲突处理

导入时可能冲突：

- skill id 已存在
- 名称重复
- 市场技能和本地技能版本不同
- 等级覆盖冲突
- 备注冲突
- 分类冲突
- 角色引用缺失技能
- MCP 派生技能 endpoint 不可用

建议处理方式：

- skill id 冲突：默认创建 `{skillId}_copy`。
- 内置 id 冲突：不覆盖内置技能。
- 名称重复：允许重复，但 UI 提示。
- 版本不同：展示本地/导入版本。
- 等级覆盖冲突：本地设置优先。
- 备注冲突：保留本地，可追加导入备注。
- 分类冲突：保留导入显式分类，并允许用户修正。
- 角色引用缺失：标记 missing，提示安装或解绑。

#### 3.6.12 UI 展示建议

建议 UI 名称：

- 技能库

一级入口卡片展示：

- 技能总数
- 内置技能数量
- 动态技能数量
- 市场可安装数量
- 已安装市场技能数量
- MCP 派生技能数量
- Level 0/1/2 数量
- 缺失引用数量

技能列表展示：

- 技能图标
- 名称
- 描述
- 分类
- 标签
- 类型
- 注入等级
- 来源
- 是否可删除
- 是否有备注

技能详情展示：

- 基本信息
- 参数 schema
- 分类和标签
- 注入等级设置
- 用户备注
- 动态定义
- 关联角色
- 导出入口
- 删除入口

建议分类：

- 推荐
- 全部
- 聊天表达
- 记忆
- 技能管理
- 自我进化
- 产物
- 手机操作
- 网页
- 媒体
- VPN
- 代码
- 系统
- MCP 派生

注意：

- 技能市场不能只靠搜索，应展示推荐列表和分类列表。
- 不要把 MCP 连接状态塞进技能详情主体，只提供跳转。
- 对高风险技能要明显展示来源和注入等级。
- Level 0 技能数量要控制，避免上下文噪声。

#### 3.6.13 搜索与排序

搜索字段：

- id
- name
- nameZh
- description
- descriptionZh
- tags
- categories
- type
- note

排序字段：

- 推荐优先
- 最近安装
- 注入等级
- 分类
- 内置/动态
- 名称
- 版本

筛选条件：

- 类型
- 分类
- 注入等级
- 是否内置
- 是否动态
- 是否市场安装
- 是否 MCP 派生
- 是否有备注
- 是否可删除
- 是否缺失配置

#### 3.6.14 空状态

空状态文案建议：

```text
还没有安装自定义技能。
你可以从推荐技能里安装，也可以让 AI 创建一个新技能。
```

如果市场为空：

```text
暂时没有推荐技能。
你仍然可以通过创建技能或接入 MCP 来扩展能力。
```

如果搜索无结果：

```text
没有找到匹配的技能。
可以换个关键词，或按分类浏览推荐技能。
```

#### 3.6.15 后续待定问题

- 是否需要为技能建立统一安装来源字段？
- 是否需要记录技能创建时间/更新时间？
- 是否需要技能使用统计？
- 是否需要技能健康检查？
- 是否需要技能权限等级？
- 是否需要区分“市场推荐”和“本地已安装市场技能”？
- 是否需要把 `SkillMarket` catalog 从代码迁到可更新配置？
- 是否需要动态技能编辑器？
- 是否需要给 MCP 派生技能单独的来源索引？
- 是否需要角色级技能授权，而不是全局可见？

导出建议：

- 内置技能不导出代码，只导出用户配置
- 用户自定义技能可导出完整定义
- 动态技能导入后默认 level 2
- 需要标记技能来源和兼容版本
- headers 和 token 默认脱敏
- 角色导出时可选择只带 skill id 或连同动态技能定义

### 3.7 MCP 连接

用于管理外部 MCP 工具协议和远程能力接入。

MCP 连接区域不是技能库本身，也不是某个模型的能力描述。

它是 MobileClaw 工作空间中专门保存“外部工具协议连接关系”的区域。

这一块应该回答：

- 当前接入了哪些 MCP endpoint？
- 每个 MCP endpoint 是否可用？
- 每个 endpoint 暴露了哪些 tool？
- 哪些 tool 已经被安装成 MobileClaw 动态技能？
- 连接需要哪些 header 或认证信息？
- 导入导出时如何处理 endpoint、headers、token 和 tool schema？
- 移动端能不能真正调用这个 MCP？

包含：

- MCP 连接配置
- 已接入 MCP 工具
- MCP endpoint
- MCP tool schema
- MCP executor 状态
- 用户手动添加的 MCP
- AI 自主接入的 MCP
- MCP 可用性检测结果
- MCP 错误日志

#### 3.7.1 区域定位

MCP 连接区域是“远程工具连接注册表”。

它保存的是：

- MCP server endpoint
- 连接名称
- 连接类型
- headers 配置
- tool discovery 结果
- tool schema 快照
- tool 安装映射
- 最近连接状态
- 最近错误摘要
- 是否允许 AI 使用
- 是否允许被角色引用

它不应该保存：

- MCP 工具执行产物
- 工作文件
- 聊天消息全文
- 模型 API Key
- 角色长期记忆
- 技能市场推荐 catalog 的全部内容

这些分别归入：

- 工作产物
- 会话记录
- 模型与网关
- 角色档案
- 技能库

MCP 连接区域和技能库的关系：

- MCP 连接区域负责“能不能连上外部 server”。
- 技能库负责“哪些 MCP tool 被包装成可调用 skill”。
- 一个 MCP endpoint 可以暴露多个 tool。
- 一个 MCP tool 可以被安装成一个动态 MCP skill。
- 删除连接时，需要提示它会影响哪些动态 MCP skill。

#### 3.7.2 用户能看到什么

用户应该能看到：

- MCP 连接列表
- 连接名称
- endpoint 地址
- 连接协议
- 是否启用
- 是否公开 endpoint
- 是否需要认证
- headers 是否已配置
- 最近检测时间
- 最近连接状态
- 工具数量
- 已安装为技能的数量
- 最近错误摘要
- 是否可在移动端调用

用户不应该默认看到：

- header 明文 token
- Authorization 明文
- Cookie 明文
- 过长的 JSON-RPC 原始报文
- 完整错误堆栈

高级视图可以展示：

- tool schema
- raw capability
- initialize response 摘要
- tools/list 原始摘要
- 最近一次 tools/call 参数和结果摘要

#### 3.7.3 AI 能读写什么

AI 可以读取：

- 已启用 MCP 连接
- endpoint 名称
- endpoint 可用性
- tool 列表
- tool schema
- 已安装 MCP skill 映射
- 最近失败原因摘要

AI 可以写入：

- 新的 MCP 连接草稿
- 连接备注
- tool discovery 快照
- 工具安装建议
- MCP tool 到动态 skill 的映射
- 非敏感的连接标签
- 最近连接检测结果

AI 不应自动写入：

- Authorization token
- Cookie
- 私有 header 明文
- 用户未确认的 endpoint 删除
- 用户未确认的公网 endpoint 调用
- 用户未确认的高风险工具调用默认参数

AI 自主接入 MCP 时，需要遵守：

- 只能优先尝试公开 HTTP/SSE/Streamable HTTP endpoint。
- 如果需要登录、注册、token 或 OAuth，应停在“需要用户授权”的状态。
- 不默认假设 Android 设备可以运行本地 stdio MCP server。
- 不把某个平台的 MCP 市场作为内置依赖。
- 发现工具后先展示 tool schema，再让用户决定是否安装为技能。

#### 3.7.4 二级内容划分

建议 MCP 连接区域下再分这些二级内容：

##### A. MCP 连接列表

展示所有已保存连接。

字段：

- connection id
- name
- endpoint
- transport
- enabled
- authRequired
- hasHeaders
- toolCount
- installedSkillCount
- lastCheckedAt
- lastStatus
- lastError
- createdBy
- updatedAt

操作：

- 查看详情
- 检测连接
- 发现工具
- 启用/禁用
- 编辑
- 删除
- 导出

##### B. Endpoint 配置

保存连接入口。

字段：

- url
- transport
- timeout
- protocolVersion
- clientName
- clientVersion
- initializationOptions
- copiedConfigSource

支持输入：

- 直接粘贴 endpoint URL
- 粘贴 `mcpServers` JSON
- 粘贴包含 `url`
- 粘贴包含 `endpoint`
- 粘贴包含 `sseUrl`
- 粘贴包含 `sse_url`

注意：

- 移动端优先支持远程 HTTP/SSE/Streamable HTTP。
- 本地 `stdio` MCP 不应该作为 Android 可用能力默认展示。
- 如果导入包里包含 `command` / `args`，应标记为“桌面端配置，不可直接在手机运行”。

##### C. Header / 认证配置

保存调用 MCP endpoint 时需要的 headers。

字段：

- header name
- secretRef
- maskedValue
- required
- updatedAt
- source

操作：

- 添加 header
- 编辑 header
- 删除 header
- 测试认证
- 导出时脱敏

敏感 header 示例：

- `Authorization`
- `Cookie`
- `X-API-Key`
- `X-Auth-Token`

建议：

- 明文 token 不写入可读 Markdown。
- 导出时只保留 header 名称和是否已配置。
- 导入后提示用户重新填写 token。

##### D. 工具发现结果

保存 `tools/list` 的结果快照。

字段：

- tool name
- title
- description
- inputSchema
- outputSchema
- annotations
- discoveredAt
- discoveryStatus
- schemaHash

操作：

- 查看 schema
- 重新发现
- 安装为技能
- 更新已安装技能
- 标记不可用

注意：

- discovery 结果是快照，不等于 server 永远稳定。
- 导入后需要重新 `tools/list`。
- schemaHash 变化时，需要提示用户更新动态技能。

##### E. 已安装 MCP 技能映射

展示 MCP tool 与 MobileClaw 动态 skill 的绑定关系。

字段：

- skill id
- skill name
- connection id
- endpoint
- tool name
- defaultArguments
- skill level
- enabled
- role references
- updatedAt

操作：

- 跳转技能详情
- 重新绑定连接
- 更新 schema
- 禁用技能
- 删除动态技能

注意：

- 这里展示映射关系。
- 技能的使用笔记、注入等级和分类仍归技能库。
- 连接不可用时，动态 MCP skill 应显示为不可调用。

##### F. 连接检测和错误日志

保存最近连接状态。

字段：

- check id
- connection id
- status
- latencyMs
- stage
- errorCode
- errorMessage
- checkedAt

stage 示例：

- parse config
- initialize
- initialized notification
- tools/list
- tools/call

常见错误：

- endpoint 不可达
- 网络超时
- TLS 失败
- 401/403
- token 缺失
- JSON-RPC 返回错误
- server 不支持 tools/list
- tool schema 无法解析

注意：

- 错误日志不保存明文 token。
- 只保留最近若干条。
- 导出时默认只导出摘要，不导出请求头。

##### G. 公开 MCP 推荐入口

保存少量可直接尝试的公开远程 MCP 示例或推荐来源。

字段：

- name
- description
- endpoint
- transport
- category
- authRequired
- mobileSupported
- source
- updatedAt

注意：

- 推荐入口不能依赖某一个必须注册的平台。
- 如果 endpoint 需要 token，应清楚标记“需要用户授权”。
- 推荐列表应可为空，不影响用户手动添加公开 MCP。
- 推荐列表属于 MCP 连接区域，不等于技能市场。

##### H. 移动端可用性说明

记录某个连接是否适合在手机端调用。

字段：

- mobileCallable
- reason
- requiresLocalProcess
- requiresDesktopRuntime
- requiresBrowserLogin
- requiresToken
- networkRequirement

判断规则：

- 远程 HTTP/SSE endpoint：网络和认证正常时可用。
- 本地 stdio server：Android 端默认不可直接运行。
- 需要桌面命令的 MCP：导入后只作为不可用配置保留。
- 需要网页登录授权的 MCP：只有存在移动端授权流程时才可用。
- 需要内网/VPN 的 MCP：需要系统配置或网络状态满足。

#### 3.7.5 数据来源

当前数据来源：

| 内容 | 当前来源 |
|---|---|
| MCP endpoint 解析 | `McpHttpClient.parseEndpointConfig(...)` |
| MCP 连接会话 | `McpSession` |
| MCP endpoint 配置 | `McpEndpointConfig` |
| initialize / tools/list / tools/call | `McpHttpClient` |
| 手动连接技能 | `McpClientSkill` |
| 发现并安装 MCP 工具 | `McpConnectSkill` |
| 动态 MCP skill 执行 | `McpSkillExecutor` |
| 动态 MCP skill 配置 | `SkillLoader.McpSkillConfig` |
| 动态技能定义 | `SkillDefinition`, `SkillType.MCP` |
| 技能市场 MCP 输入 UI | `SkillMarketPage` |

当前 MCP 能力更多分散在技能系统中，还缺一个独立的 MCP connection registry。

#### 3.7.6 文件路径

当前已安装 MCP 动态技能可以复用动态技能文件：

```text
filesDir/skills/{skillId}.json
```

后续建议 MCP 连接区域独立成目录：

```text
filesDir/mcp/
  connections/
    {connectionId}.json
  discovery/
    {connectionId}.json
  logs/
    {connectionId}.jsonl
  recommendations/
    catalog.json
```

导出结构建议：

```text
workspace_export/mcp/
  manifest.json
  connections/
    {connectionId}.json
  discovery/
    {connectionId}.json
  installed_skills.json
  health_logs.json
```

`connections/{connectionId}.json` 示例：

```json
{
  "schemaVersion": 1,
  "connectionId": "mcp_xxxxxxxx",
  "name": "Public Search MCP",
  "endpoint": "https://example.com/mcp",
  "transport": "streamable_http",
  "enabled": true,
  "auth": {
    "required": false,
    "headers": []
  },
  "mobile": {
    "callable": true,
    "reason": "remote_http"
  },
  "lastStatus": "ok",
  "lastCheckedAt": 0
}
```

敏感 header 示例：

```json
{
  "name": "Authorization",
  "configured": true,
  "exported": false
}
```

#### 3.7.7 数据库表

当前没有明确的 MCP 连接表。

短期可以继续使用：

- 动态 skill JSON
- DataStore 配置
- 文件型 registry

后续如果 MCP 连接需要可搜索、可统计、可恢复，建议增加：

- `mcp_connections`
- `mcp_tools`
- `mcp_tool_installations`
- `mcp_health_logs`

建议字段：

`mcp_connections`：

- `id`
- `name`
- `endpoint`
- `transport`
- `enabled`
- `authRequired`
- `headersConfigured`
- `mobileCallable`
- `lastStatus`
- `lastError`
- `lastCheckedAt`
- `createdAt`
- `updatedAt`

`mcp_tools`：

- `id`
- `connectionId`
- `name`
- `description`
- `inputSchemaJson`
- `schemaHash`
- `discoveredAt`
- `enabled`

`mcp_tool_installations`：

- `id`
- `connectionId`
- `toolName`
- `skillId`
- `enabled`
- `createdAt`
- `updatedAt`

`mcp_health_logs`：

- `id`
- `connectionId`
- `stage`
- `status`
- `latencyMs`
- `errorCode`
- `errorMessage`
- `createdAt`

#### 3.7.8 导出范围

MCP 连接导出包建议包含：

- 连接名称
- endpoint
- transport
- 是否启用
- 是否需要认证
- header 名称
- mobile callable 判断
- tool discovery 快照
- 已安装动态 MCP skill 映射
- 最近健康状态摘要
- createdAt / updatedAt

默认不包含：

- Authorization 明文
- Cookie 明文
- API Key 明文
- 私有 header value
- 完整请求日志
- 完整响应日志

可选包含：

- tool schema 快照
- 最近错误摘要
- 推荐来源
- disabled 连接
- 已禁用工具

导出时需要提示：

```text
MCP 连接中的认证信息不会明文导出。导入到新设备后，需要重新填写 token 或 header，并重新检测连接。
```

#### 3.7.9 导入策略

导入 MCP 连接时需要处理：

- connection id 冲突
- endpoint 重复
- headers 缺失
- token 缺失
- tool schema 过期
- 动态 MCP skill 已存在
- endpoint 不可达
- 移动端不可调用

建议导入选项：

1. 只预览，不写入
2. 导入连接但不启用
3. 导入连接并立即检测
4. 导入连接和动态 MCP skill 映射
5. 跳过需要 token 的连接
6. 导入需要 token 的连接，但标记为待配置
7. endpoint 相同则合并
8. endpoint 相同但名称不同则创建副本

导入后必须执行：

- 解析 endpoint
- 检查 mobile callable
- 检查 headers 是否完整
- 重新 initialize
- 重新 tools/list
- 对比 tool schemaHash
- 更新动态 MCP skill 可用状态

#### 3.7.10 敏感信息

可能敏感：

- endpoint URL
- 内网地址
- query 参数
- header 名称
- header value
- token
- Cookie
- 用户自建 MCP server 地址
- 工具调用参数中的私有数据

处理原则：

- token 不进入 Markdown。
- token 不默认导出。
- header value 只保存到安全配置层或加密存储。
- 日志不记录完整 Authorization。
- URL query 中疑似 token 的字段需要脱敏。
- 导入后认证缺失时，连接状态为“待配置”。
- AI 不应主动读取明文 token。

#### 3.7.11 冲突处理

导入时可能冲突：

- 同 endpoint 已存在
- 同 connection id 已存在
- 同 MCP tool 已安装为不同 skill
- 本地 skill level 和导入 skill level 不一致
- tool schemaHash 不一致
- 本地连接已禁用，导入连接启用
- 本地连接有 token，导入连接无 token

建议处理方式：

- 同 endpoint：默认合并为同一连接。
- 同 connection id：若 endpoint 不同，则创建新 id。
- schemaHash 不一致：保留本地 skill，提示可更新 schema。
- 启用状态冲突：本地状态优先。
- token 冲突：永远不被导入包覆盖。
- skill id 冲突：创建副本或重新绑定。

#### 3.7.12 UI 展示建议

建议 UI 名称：

- MCP 连接

一级入口卡片展示：

- 连接总数
- 可用连接数
- 需要配置认证数
- 工具总数
- 已安装为技能数
- 最近检测时间

连接列表展示：

- 名称
- endpoint 摘要
- 状态点
- 工具数量
- 是否需要认证
- 是否移动端可用
- 最近错误摘要

连接详情展示：

- endpoint
- transport
- headers 配置状态
- tool 列表
- 已安装技能
- 最近检测记录
- 导出策略

移动端交互建议：

- 添加 MCP 时优先一个输入框：粘贴 URL 或 JSON。
- 粘贴后自动识别 endpoint。
- 自动显示“可手机调用 / 需要桌面运行 / 需要认证”。
- 工具发现结果使用列表，不做复杂表格。
- 每个 tool 有“查看 schema”和“安装为技能”。
- 认证配置用“添加 header”表单，不要求用户理解完整 JSON。

空状态不要只给搜索框。

应该给：

- 添加公开 MCP endpoint
- 粘贴 MCP 配置
- 查看推荐公开 MCP
- 从技能市场安装 MCP 派生技能

#### 3.7.13 搜索与排序

支持搜索：

- 连接名称
- endpoint
- tool name
- tool description
- dynamic skill name
- category

支持过滤：

- 全部
- 可用
- 不可用
- 需要认证
- 移动端可用
- 已安装技能
- 未安装工具

排序：

- 最近检测
- 最近添加
- 工具数量
- 已安装技能数量
- 名称
- 可用状态

#### 3.7.14 空状态

首次进入：

```text
还没有 MCP 连接。
你可以粘贴公开 MCP endpoint 或 mcpServers 配置，让 MobileClaw 发现外部工具。
```

没有可用连接：

```text
当前 MCP 都不可用。
请检查网络、endpoint、认证 header 或移动端支持状态。
```

需要认证：

```text
这个 MCP 需要认证。
请在手机上补充必要 header，认证信息不会明文导出。
```

桌面配置不可用：

```text
这个 MCP 使用本地命令启动，手机端无法直接运行。
你可以保留配置，或改用远程 HTTP/SSE endpoint。
```

#### 3.7.15 后续待定问题

- 是否需要 MCP connection registry 独立文件？
- 是否需要把动态 MCP skill 和 connection 建立强引用？
- 是否需要内置少量公开 MCP 推荐？
- 是否允许 AI 自动尝试公开 MCP endpoint？
- 是否需要 MCP 调用权限等级？
- 是否需要对 MCP tool 做风险分类？
- 是否需要保存 tools/call 的结果摘要？
- 是否需要支持 OAuth 移动端授权流程？
- 是否需要支持远程 MCP 的订阅更新？
- 是否需要对 MCP endpoint 做可达性定时检测？

导出建议：

- endpoint 可导出
- tool schema 可导出
- 动态 MCP skill 映射可导出
- token 默认不明文导出
- headers value 默认不明文导出
- 导入后必须重新验证连接状态
- 本地 stdio MCP 配置导入后标记为手机不可直接运行

### 3.8 模型与网关

用于管理云模型、本地模型和多角色模型画像。

模型与网关区域不是单个角色的配置页，也不是普通设置项。

它是 MobileClaw 工作空间中专门保存“模型调用入口、能力分配、本地模型资产引用、角色模型画像”的区域。

这一块应该回答：

- 当前有哪些云模型网关？
- 默认使用哪个网关？
- chat / image / video / embedding 分别走哪个模型？
- 本地模型是否启用？
- 本地模型文件安装在哪里？
- 每个角色最近使用的模型配置是什么？
- 导出时如何处理 API Key？
- 后续群聊和狼人杀如何让不同角色使用不同模型？

包含：

- 云模型网关
- 本地模型
- 每个角色使用的模型
- 多模态能力
- embedding 模型
- image/video 模型
- local tool calling
- 模型调用画像
- 模型可用性检测

#### 3.8.1 区域定位

模型与网关区域是“模型调用配置和模型资源引用”的总入口。

它保存的是：

- 云网关列表
- 当前 active gateway
- 每个网关的 endpoint
- 每个网关的默认模型
- 每类能力的模型 override
- 每类能力的 endpoint override
- 本地模型开关
- 本地模型选择
- 本地模型文件 manifest
- local native only 状态
- local tool calling 状态
- 角色模型画像引用
- 最近可用性检测结果

它不应该保存：

- 聊天消息全文
- 模型调用完整请求体
- 模型完整响应体
- 角色长期记忆
- 任务产物正文
- 原始密钥导出明文

这些分别归入：

- 会话记录
- 工作产物
- 角色档案
- 任务工作空间
- 系统敏感配置

模型与角色的关系：

- 全局网关配置归模型与网关区域。
- 角色可以有 `modelOverride`。
- 角色档案里的 `model.md` 和 `model_config.json` 只保存角色最近使用的模型画像。
- 角色导出时不应携带明文 API Key。
- 后续群聊和狼人杀可以根据角色 id 读取角色模型画像，再引用全局 gateway id。

#### 3.8.2 用户能看到什么

用户应该能看到：

- 网关列表
- 当前默认网关
- 网关名称
- endpoint 摘要
- 默认 chat 模型
- embedding 模型
- image 模型
- video 模型
- 是否支持多模态
- API Key 是否已配置
- 每类能力是否启用
- 本地模型是否启用
- 当前本地模型
- 本地模型安装状态
- 本地模型体积
- 本地模型最低内存要求
- local tool calling 是否启用
- 最近检测状态
- 哪些角色引用了模型 override

用户不应该默认看到：

- API Key 明文
- 完整 Authorization header
- 模型调用原始 payload
- 模型调用原始响应
- 内部 retry 日志

高级视图可以展示：

- gateway id
- capability 配置
- 模型列表拉取结果
- 最近一次检测错误
- 本地模型文件路径
- 本地模型 manifest

#### 3.8.3 AI 能读写什么

AI 可以读取：

- 可用网关名称
- 当前默认模型
- capability model
- 本地模型是否可用
- 当前角色的 modelOverride
- 角色最近模型画像
- 模型可用性摘要

AI 可以写入：

- 角色模型画像
- 模型使用经验
- 非敏感的网关备注
- 模型能力标签
- 失败经验摘要
- 建议的模型分配草稿

AI 不应自动写入：

- API Key
- 用户未确认的新 endpoint
- 用户未确认的默认网关切换
- 用户未确认的本地模型删除
- 用户未确认的高成本模型选择

AI 给角色分配模型时，需要遵守：

- 优先引用已有 gateway id。
- 角色档案只记录模型偏好和最近画像，不复制密钥。
- 如果模型缺失，应提示用户配置网关或选择本地模型。
- 如果本地模型不支持 tool calling，应降低 agent 执行预期。
- 群聊/狼人杀中不同角色可拥有不同模型画像，但实际密钥仍来自全局网关。

#### 3.8.4 二级内容划分

建议模型与网关区域下再分这些二级内容：

##### A. 网关列表

展示所有云模型网关。

字段：

- gateway id
- name
- endpoint
- model
- embeddingModel
- supportsMultimodal
- capability count
- active
- configured
- lastCheckedAt
- lastStatus
- updatedAt

操作：

- 查看详情
- 设为默认
- 编辑
- 检测
- 删除
- 导出

##### B. 网关基础配置

保存 OpenAI-compatible 或其他云模型 endpoint。

字段：

- id
- name
- endpoint
- apiKey configured
- model
- embeddingModel
- supportsMultimodal

注意：

- `apiKey` 是敏感配置。
- endpoint 可导出，但 URL 中疑似 token 的 query 需要脱敏。
- 网关名称和模型名可作为角色画像引用。

##### C. 能力配置

保存 chat / image / video / embedding 等能力分流。

字段：

- type
- enabled
- model
- endpoint
- apiKey configured

当前对应：

- `GatewayCapabilityConfig.type`
- `GatewayCapabilityConfig.model`
- `GatewayCapabilityConfig.enabled`
- `GatewayCapabilityConfig.endpoint`
- `GatewayCapabilityConfig.apiKey`

能力类型建议：

- chat
- embedding
- image
- video
- vision
- audio
- rerank

注意：

- 如果 capability endpoint 为空，则继承 gateway endpoint。
- 如果 capability apiKey 为空，则继承 gateway apiKey。
- 导出时可以导出能力结构，但不导出密钥明文。

##### D. 本地模型

保存本地模型安装状态和文件引用。

字段：

- model id
- name
- family
- fileName
- sizeBytes
- minRamGb
- recommendedRamGb
- supportsText
- supportsVision
- supportsAudio
- supportsChatRuntime
- runtimeNote
- installed
- path
- downloadSources
- manifest

当前对应：

- `LocalModelManager`
- `LocalModelInfo`
- `LocalModelSource`
- `filesDir/models/{modelId}/{fileName}`
- `filesDir/models/{modelId}/manifest.json`

操作：

- 下载
- 导入
- 删除
- 设为当前本地模型
- 查看文件状态
- 查看最低内存要求

注意：

- 本地模型文件体积很大，默认不进入工作空间导出包。
- 可以只导出 manifest 和引用。
- 如果用户明确选择“包含本地模型文件”，需要体积提示。

##### E. 本地运行策略

保存当前是否使用本地模型。

字段：

- localModelEnabled
- localModelId
- localNativeOnly
- localToolCallingEnabled

当前对应：

- `ConfigSnapshot.localModelEnabled`
- `ConfigSnapshot.localModelId`
- `ConfigSnapshot.localNativeOnly`
- `ConfigSnapshot.localToolCallingEnabled`

注意：

- `localNativeOnly = true` 时，应尽量不调用云模型。
- `localToolCallingEnabled = false` 时，本地模型只适合输出内容，不适合 agent 工具执行。
- 这能解释“本地模型只能输出内容，不能执行 agent”的情况：模型输出能力不等于 tool calling / agent runtime 可用。

##### F. 角色模型画像

展示角色最近使用模型和调用配置。

字段：

- role id
- role name
- role modelOverride
- effectiveModel
- chatModel
- embeddingModel
- localModelEnabled
- localNativeOnly
- localToolCallingEnabled
- localModelId
- gateway id
- gateway name
- masked endpoint
- masked apiKey
- capability models
- updatedAt

当前对应：

- `role_workspaces/{roleId}/model.md`
- `role_workspaces/{roleId}/model_config.json`
- `RoleWorkspaceStore.recordModelConfig(...)`

注意：

- 角色模型画像是运行快照，不是密钥来源。
- 角色导出可以包含 `model.md` 和脱敏后的 `model_config.json`。
- 导入角色后，如果本地没有对应 gateway id，需要提示用户重新绑定。

##### G. 模型可用性检测

保存最近检测结果。

字段：

- check id
- gateway id
- capability type
- model
- endpoint
- status
- latencyMs
- errorCode
- errorMessage
- checkedAt

检测内容：

- endpoint 是否可达
- API Key 是否有效
- 模型是否存在
- capability 是否可调用
- embedding 是否可用
- image/video 是否需要独立 endpoint
- 本地模型文件是否完整
- 本地模型内存是否满足建议

##### H. 模型使用画像

保存非敏感使用经验。

字段：

- model
- gateway id
- task type
- role id
- successCount
- failureCount
- avgLatency
- lastUsedAt
- notes

用途：

- 为角色推荐模型
- 为群聊分配模型
- 记录某模型不适合某类任务
- 后续做狼人杀时，为不同角色设置推理强度和成本策略

注意：

- 不保存完整 prompt 和 response。
- 只保存统计摘要和用户可理解的经验。

#### 3.8.5 数据来源

现有数据来源：

- `AgentConfig`
- `GatewayConfig`
- `GatewayCapabilityConfig`
- `ConfigSnapshot`
- `LocalModelManager`
- `LocalModelInfo`
- `LocalModelSource`
- `role_workspaces/{roleId}/model.md`
- `role_workspaces/{roleId}/model_config.json`
- `Role.modelOverride`
- `RoleWorkspaceStore.recordModelConfig(...)`
- `OpenAiGateway`
- `LocalGemmaGateway`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 多网关列表 | DataStore `agent_config.gateways_json` |
| 当前默认网关 | DataStore `agent_config.active_gateway_id` |
| 网关配置结构 | `GatewayConfig` |
| capability 分流 | `GatewayCapabilityConfig` |
| 本地模型开关 | `ConfigSnapshot.localModelEnabled` |
| 本地模型 id | `ConfigSnapshot.localModelId` |
| 只使用本地 | `ConfigSnapshot.localNativeOnly` |
| 本地工具调用开关 | `ConfigSnapshot.localToolCallingEnabled` |
| 本地模型列表 | `LocalModelManager.models` |
| 本地模型文件 | `filesDir/models/{modelId}/` |
| 角色模型 override | `Role.modelOverride` |
| 角色模型画像 | `role_workspaces/{roleId}/model.md`, `model_config.json` |
| 云模型调用 | `OpenAiGateway` |
| 本地模型调用 | `LocalGemmaGateway` |

#### 3.8.6 文件路径

当前全局网关配置保存在 DataStore：

```text
DataStore: agent_config
  gateways_json
  active_gateway_id
  local_model_enabled
  local_model_id
  local_native_only
  local_tool_calling_enabled
```

当前本地模型保存在：

```text
filesDir/models/{modelId}/{fileName}
filesDir/models/{modelId}/manifest.json
```

当前角色模型画像保存在：

```text
filesDir/role_workspaces/{roleId}/model.md
filesDir/role_workspaces/{roleId}/model_config.json
```

后续建议模型与网关区域导出结构：

```text
workspace_export/models/
  manifest.json
  gateways.json
  capabilities.json
  local_models.json
  role_model_profiles.json
  health_logs.json
```

如果用户明确导出本地模型文件：

```text
workspace_export/models/local_files/
  {modelId}/
    manifest.json
    {fileName}
```

`gateways.json` 示例：

```json
{
  "schemaVersion": 1,
  "activeGatewayId": "gw_xxxxxxxx",
  "gateways": [
    {
      "id": "gw_xxxxxxxx",
      "name": "OpenAI Compatible",
      "endpoint": "https://api.example.com/v1",
      "apiKey": {
        "configured": true,
        "exported": false
      },
      "model": "gpt-4o",
      "embeddingModel": "text-embedding-3-small",
      "supportsMultimodal": true,
      "capabilities": []
    }
  ]
}
```

#### 3.8.7 数据库表

当前模型与网关主要不在 Room 表中。

当前存储：

- DataStore `agent_config`
- `filesDir/models/`
- `filesDir/role_workspaces/{roleId}/model_config.json`

后续如果需要查询、统计、迁移和群聊玩法调度，建议增加：

- `model_gateways`
- `model_capabilities`
- `local_models`
- `role_model_profiles`
- `model_health_logs`
- `model_usage_stats`

建议字段：

`model_gateways`：

- `id`
- `name`
- `endpoint`
- `apiKeyConfigured`
- `model`
- `embeddingModel`
- `supportsMultimodal`
- `active`
- `createdAt`
- `updatedAt`

`model_capabilities`：

- `id`
- `gatewayId`
- `type`
- `model`
- `enabled`
- `endpoint`
- `apiKeyConfigured`

`local_models`：

- `id`
- `name`
- `family`
- `fileName`
- `sizeBytes`
- `installed`
- `path`
- `sha256Prefix`
- `supportsChatRuntime`
- `supportsVision`
- `updatedAt`

`role_model_profiles`：

- `id`
- `roleId`
- `gatewayId`
- `effectiveModel`
- `chatModel`
- `embeddingModel`
- `localModelId`
- `localNativeOnly`
- `localToolCallingEnabled`
- `updatedAt`

`model_health_logs`：

- `id`
- `targetId`
- `targetType`
- `capability`
- `status`
- `latencyMs`
- `errorMessage`
- `createdAt`

#### 3.8.8 导出范围

模型与网关导出包建议包含：

- 网关名称
- gateway id
- endpoint
- 默认模型
- embedding 模型
- capability 配置
- active gateway id
- 本地模型开关
- 本地模型 id
- 本地模型 manifest
- 角色模型画像
- 最近健康状态摘要

默认不包含：

- API Key 明文
- capability apiKey 明文
- 完整模型调用日志
- 本地模型大文件
- 下载 token

可选包含：

- 本地模型文件
- 模型使用统计
- 失败经验摘要
- disabled gateway
- 历史角色模型画像

导出提示：

```text
模型网关会导出 endpoint、模型名和能力分配，但不会导出 API Key。导入到新设备后，需要重新填写密钥并检测可用性。
```

如果包含本地模型文件：

```text
本地模型文件体积较大，导出会显著增加备份大小。请确认目标设备有足够存储和内存。
```

#### 3.8.9 导入策略

导入模型与网关时需要处理：

- gateway id 冲突
- endpoint 重复
- API Key 缺失
- active gateway 不存在
- capability 模型不存在
- 本地模型文件缺失
- 本地模型文件不完整
- 角色引用的 gateway 不存在
- 角色 modelOverride 在当前设备不可用

建议导入选项：

1. 只预览，不写入
2. 导入网关结构但不启用
3. endpoint 相同则合并
4. endpoint 相同但名称不同则创建副本
5. 导入角色模型画像
6. 跳过缺少密钥的网关
7. 导入缺少密钥的网关，但标记为待配置
8. 只导入本地模型 manifest，不导入模型文件
9. 连同本地模型文件一起导入

导入后必须执行：

- 检查 active gateway
- 检查 API Key 配置状态
- 检测 chat capability
- 检测 embedding capability
- 检测 image/video capability
- 扫描 `filesDir/models/`
- 校验本地模型 manifest
- 修复角色模型画像引用

#### 3.8.10 敏感信息

可能敏感：

- API Key
- endpoint
- 私有网关地址
- 内网地址
- 模型供应商信息
- capability 独立密钥
- 下载 token
- 本地模型文件路径

处理原则：

- API Key 不进入 Markdown。
- API Key 不默认导出。
- `model_config.json` 只保存 mask 后的 key。
- endpoint 默认可导出，但内网地址需要提示。
- 本地路径导出时应只作为参考，不要求目标设备路径一致。
- AI 只能读取“是否已配置”，不能读取明文密钥。

#### 3.8.11 冲突处理

导入时可能冲突：

- 同 gateway id 不同 endpoint
- 同 endpoint 不同名称
- 本地已有 active gateway
- 导入 active gateway 缺失 API Key
- 同角色已有不同 modelOverride
- 本地模型 id 相同但文件 hash 不一致
- capability 类型相同但模型不同

建议处理方式：

- 同 gateway id 不同 endpoint：创建新 id。
- 同 endpoint：默认合并。
- active gateway 冲突：保留本地 active gateway。
- API Key 冲突：本地密钥永远优先，导入包不覆盖。
- 角色 modelOverride 冲突：保留本地，导入值作为候选。
- 本地模型 hash 不一致：保留本地文件，导入文件另存或跳过。
- capability 冲突：显示差异，默认保留本地。

#### 3.8.12 UI 展示建议

建议 UI 名称：

- 模型与网关

一级入口卡片展示：

- 网关数量
- 当前默认模型
- 本地模型状态
- 已安装本地模型数
- 需要补密钥数量
- 最近检测时间

列表展示：

- 网关列表
- 本地模型列表
- 角色模型画像列表
- 检测日志

网关详情展示：

- 名称
- endpoint 摘要
- 默认模型
- API Key 状态
- chat/image/video/embedding 能力
- 检测按钮
- 导出策略

本地模型详情展示：

- 模型名
- family
- 文件体积
- 安装状态
- 最低内存
- 支持能力
- 下载/导入/删除

角色模型画像展示：

- 角色
- effective model
- gateway name
- local 状态
- 最近更新时间
- 跳转角色档案

#### 3.8.13 搜索与排序

支持搜索：

- 网关名称
- endpoint
- model
- capability type
- 本地模型名称
- role name

支持过滤：

- 全部
- 云网关
- 本地模型
- 需要补密钥
- 可用
- 不可用
- 支持多模态
- 被角色引用

排序：

- 默认网关优先
- 最近更新
- 最近检测
- 名称
- 模型数量
- 角色引用数量
- 本地模型体积

#### 3.8.14 空状态

没有网关：

```text
还没有配置模型网关。
你可以添加 OpenAI-compatible endpoint，或启用本地模型。
```

缺少 API Key：

```text
这个网关缺少 API Key。
导入的工作空间不会包含明文密钥，请重新填写后检测。
```

没有本地模型：

```text
还没有安装本地模型。
你可以下载推荐模型，或从手机文件中导入模型文件。
```

本地模型不可执行 agent：

```text
当前本地模型只能输出文本，工具调用未启用或运行时不支持。
需要执行 agent 时，请启用支持 tool calling 的模型或切换到云网关。
```

#### 3.8.15 后续待定问题

- 是否需要把 gateway 从 DataStore 迁到独立工作空间文件？
- 是否需要为每个角色保存默认 gateway id？
- 是否需要角色级 capability override？
- 是否需要模型成本统计？
- 是否需要模型延迟统计？
- 是否需要模型调用失败自动降级？
- 是否需要群聊中按角色分配不同模型？
- 是否需要狼人杀中给角色配置不同推理强度？
- 是否需要本地模型 tool calling 能力检测？
- 是否需要 API Key 加密存储和导入后重新授权流程？

导出建议：

- 网关结构可导出
- capability 结构可导出
- 角色模型画像可导出
- API Key 默认脱敏
- 本地模型 manifest 默认导出
- 本地模型文件默认不导出，除非用户明确选择
- 导入后必须重新验证网关和本地模型状态

### 3.9 媒体资产

用于管理图片、视频、头像、表情包和角色视觉资源。

媒体资产区域不是所有文件的集合。

它是 MobileClaw 工作空间中专门保存“可展示、可复用、可被角色/会话/群聊引用的视觉和音视频资源”的区域。

这一块应该回答：

- 当前有哪些角色头像和肖像？
- AI Town / 角色空间使用了哪些 sprite pack？
- 聊天和群聊里沉淀了哪些图片、视频、表情包附件？
- AI 生成过哪些图片、图标、视频？
- 哪些媒体只是缓存，哪些应该随工作空间导出？
- 媒体和工作产物、会话记录、角色档案之间如何建立引用？

包含：

- 角色头像
- 角色 portrait
- sprite packs
- 生成图片
- 生成视频
- sticker / 表情包
- 上传图片
- HTML/media attachments
- 缓存媒体

#### 3.9.1 区域定位

媒体资产区域是“媒体文件和媒体引用索引”。

它保存的是：

- 图片文件
- 视频文件
- 角色头像引用
- 角色肖像 pack
- 角色动画 sprite pack
- AI Town 房间资产
- 表情包缓存和收藏
- 生成图标
- 生成图片
- 生成视频文件
- 聊天/群聊附件中的媒体引用
- 媒体来源和归属关系

它不应该保存：

- 普通文档正文
- HTML 页面源码
- MiniAPP 项目代码
- 大段聊天文本
- 模型调用密钥
- 临时截图的长期备份

这些分别归入：

- 工作产物
- 会话记录
- 模型与网关
- 任务工作空间
- 系统缓存

媒体资产和工作产物的关系：

- 图片、视频、图标、表情包属于媒体资产。
- DOCX/PDF/PPTX/HTML/代码文件属于工作产物。
- 如果工作产物里引用了图片，图片本体可以归媒体资产，产物只保存相对引用。
- 如果图片只是某个文档内部嵌入资源，可跟随工作产物导出，不一定进入全局媒体资产列表。

#### 3.9.2 用户能看到什么

用户应该能看到：

- 媒体总数
- 图片数量
- 视频数量
- 表情包数量
- 角色视觉资源数量
- 生成图标数量
- 最近新增媒体
- 媒体来源
- 文件大小
- 文件类型
- 关联角色
- 关联会话
- 关联群聊
- 是否收藏
- 是否缓存
- 是否默认导出

用户不应该默认看到：

- 临时截图队列
- base64 原文
- 外部上传服务 token
- 生成接口 raw response
- 已过期的临时 URL

高级视图可以展示：

- 绝对文件路径
- mime type
- hash
- 宽高
- 引用计数
- 生成 prompt 摘要
- 关联 task id

#### 3.9.3 AI 能读写什么

AI 可以读取：

- 媒体列表摘要
- 媒体类型
- 媒体用途
- 媒体路径或可访问 URI
- 关联角色/会话/群聊
- 生成 prompt 摘要
- 是否可复用

AI 可以写入：

- 新生成图片
- 新生成图标
- 新生成视频引用
- 表情包收藏状态建议
- 媒体标签
- 媒体归属关系
- 媒体说明
- 角色肖像和 sprite pack 引用

AI 不应自动写入：

- 用户相册公共目录
- 用户未确认的媒体删除
- 用户未确认的角色头像替换
- 用户未确认的大文件导出
- 私密图片的公开上传

AI 使用媒体时，需要遵守：

- 只复用用户明确上传或 AI 生成并保存在工作空间里的媒体。
- 聊天里的临时图片如果要长期复用，应先沉淀为媒体资产。
- 截屏类观察图片不应默认进入长期媒体资产。
- 群聊表情包可以归媒体资产，但群聊消息仍归群聊区域。

#### 3.9.4 二级内容划分

建议媒体资产区域下再分这些二级内容：

##### A. 媒体资产索引

展示所有可管理媒体。

字段：

- media id
- type
- name
- mimeType
- path
- sizeBytes
- width
- height
- durationMs
- source
- ownerArea
- ownerId
- createdAt
- updatedAt
- favorite
- cache
- exportPolicy

操作：

- 预览
- 重命名
- 添加标签
- 收藏
- 查看引用
- 删除
- 导出

##### B. 角色视觉资源

管理角色头像、肖像和 sprite pack。

字段：

- role id
- avatar
- portraitSpritePack
- characterSpritePack
- sprite pack id
- imagePath
- frameWidth
- frameHeight
- columns
- rows
- kind
- palette
- notes
- updatedAt

当前对应：

- `Role.avatar`
- `AgentTownStore`
- `AgentRoom.characterSpritePack`
- `AgentRoom.portraitSpritePack`
- `AgentSpritePack.imagePath`
- `filesDir/agent_town/assets/sprites/`
- `filesDir/agent_town/assets/composed/`
- `filesDir/agent_town/assets/previews/`

注意：

- 静态肖像和动态 sprite sheet 需要分开。
- 角色详情页使用 portrait。
- AI Town 房间动画使用 character sprite。
- 导出角色时可以选择连同视觉资源一起导出。

##### C. AI Town / 房间资产

管理角色空间中的视觉资源。

字段：

- town asset id
- room id
- role id
- asset pack
- furniture
- wall pin visual
- showcase artifact thumbnail
- imagePath
- layer
- updatedAt

当前对应：

- `AgentTownStore`
- `filesDir/agent_town/town.json`
- `filesDir/agent_town/assets/`

注意：

- AI Town 功能当前可以在 UI 上隐藏，但底层角色视觉资产仍可能被角色详情页使用。
- 房间状态本体归角色空间/玩法空间讨论，媒体区域只管理可复用的图像资源。

##### D. 生成图片和图标

管理 AI 生成的图片和 App/角色图标。

字段：

- media id
- prompt
- provider
- model
- localPath
- dataUri cached
- target
- appliedToRole
- appliedToApp
- createdAt

当前对应：

- `GenerateImageSkill`
- `GenerateIconSkill`
- `SkillAttachment.ImageData`
- `filesDir/icons/`
- 动态图片附件

注意：

- 当前 `ImageData` 可能只携带 base64，后续应尽量落盘并建立媒体索引。
- 生成图标如果应用到角色或 App，需要记录引用关系。
- base64 不适合作为长期工作空间存储格式。

##### E. 生成视频

管理 AI 生成的视频文件和远程任务结果。

字段：

- task id
- prompt
- provider
- endpoint
- model
- status
- videoUrl
- filePath
- errorMessage
- createdAt
- updatedAt

当前对应：

- `GenerateVideoSkill`
- `VideoGenerationTaskManager`
- `VideoGenerationTaskEntity`
- `filesDir/videos/`

注意：

- 视频任务状态本身也会出现在任务队列。
- 视频文件归媒体资产。
- `apiKey` 不应该随视频任务明文导出。
- submit/poll raw response 默认不作为媒体资产导出。

##### F. 表情包和贴纸

管理聊天和群聊可复用表情包。

字段：

- sticker id
- path
- name
- mimeType
- sizeBytes
- source
- category
- favorite
- createdAt

当前对应：

- `ChineseBqbStickerSkill`
- `ChineseBqbStickerRepository`
- `StickerFavoritesStore`
- `filesDir/stickers/chinese_bqb/index.json`
- `filesDir/stickers/chinese_bqb/images/`

注意：

- 表情包缓存可清理。
- 收藏表情应可导出。
- 非收藏缓存默认不导出。

##### G. 聊天和群聊媒体附件

管理消息中出现的媒体引用。

字段：

- message id
- session id
- group id
- attachment type
- path
- name
- mimeType
- sizeBytes
- imageBase64 present
- createdAt

当前对应：

- `SessionMessageEntity.attachmentsJson`
- `SessionMessageEntity.imageBase64`
- `GroupMessageEntity.attachmentsJson`
- `SkillAttachment.FileData`
- `SkillAttachment.ImageData`

注意：

- 会话归属和消息文本归会话记录/群聊区域。
- 媒体区域只保存媒体文件和引用索引。
- 如果附件路径指向外部 content URI，导出前需要复制或提示不可迁移。

##### H. 上传和外链媒体

管理为了调用视频接口或外部服务而生成的临时公网 URL。

字段：

- sourcePath
- uploadedUrl
- provider
- expiresAt
- createdAt
- ownerTaskId

当前对应：

- `CloudinaryImageUploader`
- video image-to-video 参考图上传流程

注意：

- 临时 URL 默认不导出。
- 如果 URL 可能过期，只保存来源路径和说明。
- 上传凭据归模型与网关或系统配置，不归媒体资产。

#### 3.9.5 数据来源

现有数据来源：

- `AgentTownStore` assets
- `GenerateImageSkill`
- `GenerateVideoSkill`
- `GenerateIconSkill`
- `ChineseBqbStickerSkill`
- 聊天附件
- `SkillAttachment.ImageData`
- `SkillAttachment.FileData`
- `SessionMessageEntity.attachmentsJson`
- `SessionMessageEntity.imageBase64`
- `GroupMessageEntity.attachmentsJson`
- `VideoGenerationTaskEntity`
- `StickerFavoritesStore`
- `ChineseBqbStickerRepository`
- `CloudinaryImageUploader`

详细来源：

| 内容 | 当前来源 |
|---|---|
| 角色头像 | `Role.avatar` |
| 角色肖像 / sprite pack | `AgentTownStore`, `AgentSpritePack` |
| AI Town 资产 | `filesDir/agent_town/assets/` |
| 生成图片 | `GenerateImageSkill`, `SkillAttachment.ImageData` |
| 生成图标 | `GenerateIconSkill`, `filesDir/icons/` |
| 生成视频 | `GenerateVideoSkill`, `filesDir/videos/` |
| 视频任务 | `video_generation_tasks` |
| 表情包缓存 | `filesDir/stickers/chinese_bqb/` |
| 表情包收藏 | `StickerFavoritesStore` |
| 聊天附件 | `session_messages.attachmentsJson` |
| 群聊附件 | `group_messages.attachmentsJson` |
| 用户上传图片 | `SkillAttachment.FileData`, content URI |

#### 3.9.6 文件路径

当前相关路径：

```text
filesDir/agent_town/town.json
filesDir/agent_town/assets/
filesDir/agent_town/assets/sprites/
filesDir/agent_town/assets/composed/
filesDir/agent_town/assets/previews/
filesDir/icons/
filesDir/stickers/
filesDir/stickers/chinese_bqb/index.json
filesDir/stickers/chinese_bqb/images/
filesDir/chat_images/
filesDir/videos/
filesDir/documents/
filesDir/created_files/
```

注意：

- `documents/` 和 `created_files/` 中的非媒体文件归工作产物。
- `videos/` 归媒体资产，同时视频任务记录归任务队列交叉引用。
- `chat_images/` 更像附件缓存，需要根据引用决定是否导出。

后续建议媒体资产区域导出结构：

```text
workspace_export/media/
  manifest.json
  index.json
  images/
  videos/
  icons/
  stickers/
  role_visuals/
  town_assets/
  references.json
```

`index.json` 示例：

```json
{
  "schemaVersion": 1,
  "items": [
    {
      "id": "media_xxxxxxxx",
      "type": "image",
      "name": "role_portrait.png",
      "mimeType": "image/png",
      "relativePath": "role_visuals/role_portrait.png",
      "source": "role_portrait",
      "ownerArea": "roles",
      "ownerId": "role_designer",
      "sizeBytes": 0,
      "exported": true
    }
  ]
}
```

#### 3.9.7 数据库表

当前没有统一媒体资产表。

当前存储分散在：

- `session_messages`
- `group_messages`
- `video_generation_tasks`
- `agent_town/town.json`
- sticker favorite 配置
- 文件目录

后续建议增加：

- `media_assets`
- `media_references`
- `media_tags`
- `media_generation_records`

建议字段：

`media_assets`：

- `id`
- `type`
- `name`
- `mimeType`
- `path`
- `sizeBytes`
- `width`
- `height`
- `durationMs`
- `hash`
- `source`
- `cache`
- `favorite`
- `createdAt`
- `updatedAt`

`media_references`：

- `id`
- `mediaId`
- `ownerArea`
- `ownerId`
- `ownerType`
- `roleId`
- `sessionId`
- `groupId`
- `messageId`
- `createdAt`

`media_generation_records`：

- `id`
- `mediaId`
- `skillId`
- `provider`
- `model`
- `promptSummary`
- `taskId`
- `createdAt`

#### 3.9.8 导出范围

媒体资产导出包建议包含：

- 媒体索引
- 角色头像和肖像
- 角色 sprite pack
- 已引用的 AI Town 媒体
- 收藏表情包
- 生成图片
- 生成图标
- 已下载生成视频
- 会话/群聊中被引用且本地可访问的媒体附件
- 媒体引用关系

默认不包含：

- 临时截图
- 未收藏表情包缓存
- 过期外链
- base64 原文冗余副本
- 上传 token
- video task apiKey
- submitResponseRaw
- pollResponseRaw

可选包含：

- 全部表情包缓存
- 未引用媒体
- 大视频文件
- AI Town preview HTML
- 生成 prompt 详情

#### 3.9.9 导入策略

导入媒体资产时需要处理：

- 文件重名
- hash 重复
- 引用的角色不存在
- 引用的会话不存在
- 引用的群聊不存在
- content URI 无法恢复
- 外部 URL 过期
- 大文件空间不足

建议导入选项：

1. 只预览，不写入
2. 只导入被引用媒体
3. 导入全部媒体
4. 跳过缓存媒体
5. 跳过大视频
6. 角色视觉资源随角色导入
7. 表情包收藏随表情包导入
8. 重名文件保留本地并创建副本

导入后需要执行：

- 重建媒体索引
- 重写相对路径
- 修复角色头像和 sprite pack 引用
- 修复会话/群聊 attachment 引用
- 扫描缺失文件
- 标记不可恢复外链

#### 3.9.10 敏感信息

可能敏感：

- 用户上传图片
- 用户相册路径
- 截图内容
- 群聊附件
- 私密生成图片
- 视频生成 prompt
- 外部上传 URL
- 文件路径中的用户名

处理原则：

- 导出前按类型展示体积和隐私提示。
- 截图默认不作为长期媒体资产导出。
- 用户上传图片默认跟随会话导出选项。
- 私密媒体需要支持单项排除。
- 绝对路径导出时应改成相对路径。
- AI 不应自动把私密媒体上传到公网。

#### 3.9.11 冲突处理

导入时可能冲突：

- 同 media id 不同 hash
- 同 hash 不同路径
- 同角色已有头像
- 同 sprite pack id 不同图片
- 收藏表情包重复
- 视频 task id 重复
- 附件引用找不到目标消息

建议处理方式：

- 同 hash：复用已有文件，新增引用。
- 同 media id 不同 hash：创建新 id。
- 角色头像冲突：保留本地，导入项作为候选。
- sprite pack 冲突：按 id 创建副本或提示覆盖。
- 表情包重复：合并收藏状态。
- 视频 task id 重复：保留本地任务，导入视频文件作为媒体资产。
- 附件目标缺失：保留媒体但标记为孤立资产。

#### 3.9.12 UI 展示建议

建议 UI 名称：

- 媒体资产

一级入口卡片展示：

- 图片数量
- 视频数量
- 角色视觉资源数量
- 表情包收藏数量
- 总体积
- 最近新增

列表展示：

- 缩略图
- 名称
- 类型
- 来源
- 关联对象
- 文件大小
- 最近时间

详情展示：

- 预览
- 路径
- mime type
- 大小
- 来源
- 引用关系
- 导出策略
- 删除/收藏

分类建议：

- 全部
- 角色视觉
- 图片
- 视频
- 图标
- 表情包
- 聊天附件
- 群聊附件
- 缓存

移动端交互建议：

- 默认使用网格缩略图。
- 大文件列表显示体积。
- 删除前显示引用关系。
- 导出前显示总大小。
- 对私密图片给明显排除入口。

#### 3.9.13 搜索与排序

支持搜索：

- 文件名
- prompt 摘要
- 角色名
- 会话标题
- 群聊名称
- 标签
- mime type

支持过滤：

- 图片
- 视频
- 表情包
- 角色视觉
- 收藏
- 缓存
- 未引用
- 大文件

排序：

- 最近新增
- 最近引用
- 文件大小
- 类型
- 来源
- 角色关联

#### 3.9.14 空状态

没有媒体：

```text
还没有媒体资产。
生成图片、视频、角色头像或发送表情后，会在这里沉淀可复用资源。
```

只有缓存：

```text
当前只有临时缓存。
你可以收藏表情包或保存生成图片，让它们进入可迁移的媒体资产。
```

导出空间不足：

```text
媒体文件较大，当前导出可能占用较多空间。
可以只导出索引和角色视觉资源，跳过大视频。
```

#### 3.9.15 后续待定问题

- 是否需要统一 `media_assets` 表？
- 是否需要把 `SkillAttachment.ImageData` 自动落盘？
- 是否需要所有媒体都生成缩略图？
- 是否需要媒体引用计数？
- 是否需要私密媒体标记？
- 是否需要相册导入后的长期拷贝？
- 是否需要按角色导出视觉资源包？
- 是否需要清理孤立媒体？
- 是否需要大视频压缩或仅导出外链？

导出建议：

- 支持按资源类型导出
- 角色视觉资源默认随角色导出
- 收藏表情默认可导出
- 缓存类媒体默认不导出
- 大视频默认提示确认
- 临时截图默认不导出
- 导入后重建媒体索引和引用关系

### 3.10 系统配置

用于管理 MobileClaw 自身运行所需的配置和状态。

系统配置区域不是用户记忆，也不是模型网关配置。

它是 MobileClaw 工作空间中专门保存“App 运行设置、权限诊断、系统能力状态、调试入口和基础设施配置”的区域。

这一块应该回答：

- 当前 App 的基础运行配置是什么？
- 哪些权限已经授权，哪些需要用户手动开启？
- 手机控制、无障碍、虚拟显示是否可用？
- 控制台和局域网服务如何配置？
- VPN 配置和运行状态如何归档？
- 缓存清理策略如何展示？
- 哪些系统状态不能跨设备恢复？
- 哪些 token 和私有配置不能导出？

包含：

- App 设置
- 主题设置
- 语言设置
- 权限状态摘要
- 无障碍配置
- 虚拟显示配置
- 控制台配置
- VPN 配置
- 调试配置
- 运行状态缓存

#### 3.10.1 区域定位

系统配置区域是“MobileClaw 自身运行环境”的总入口。

它保存的是：

- App 基础设置
- UI 设置
- 语言设置
- 权限诊断摘要
- 手机控制能力状态
- 无障碍服务状态
- 虚拟显示状态
- 控制台服务配置
- 局域网访问配置
- VPN 订阅和代理选择摘要
- 缓存分类和清理策略
- 调试开关
- 用户显式配置中的系统类 key

它不应该保存：

- 模型 API Key
- MCP token
- 聊天消息
- 角色记忆
- 工作产物正文
- 媒体文件本体
- Android 系统授权本身

这些分别归入：

- 模型与网关
- MCP 连接
- 会话记录
- 角色档案
- 工作产物
- 媒体资产
- 设备系统设置

系统配置和导入导出的关系：

- 可迁移的是配置值和诊断摘要。
- 不可迁移的是系统授权结果。
- 导入后需要重新检查权限、VPN 授权、无障碍状态、悬浮窗状态、通知状态等。

#### 3.10.2 用户能看到什么

用户应该能看到：

- 当前语言
- 当前主题
- UI 风格
- 权限总览
- 无障碍是否开启
- 悬浮窗是否可用
- 通知权限是否可用
- 电池后台限制状态
- 虚拟显示状态
- 控制台服务状态
- 控制台局域网地址
- VPN 状态
- VPN 订阅数量
- 当前代理
- 缓存分类和大小
- 调试功能是否启用

用户不应该默认看到：

- console token 明文
- Codex bridge token 明文
- VPN 订阅原始私密链接
- 完整 Clash/Mihomo 配置
- 内部调试日志全文
- Android 系统私有路径细节

高级视图可以展示：

- DataStore key 摘要
- 权限诊断详情
- 控制台端口
- 本地服务状态
- 缓存路径
- VPN 配置摘要
- debug page 参数

#### 3.10.3 AI 能读写什么

AI 可以读取：

- 权限诊断摘要
- 系统能力可用性
- 非敏感 App 设置
- 缓存分类摘要
- VPN 当前状态摘要
- 控制台是否开启
- 用户显式配置中允许暴露的系统配置

AI 可以写入：

- 非敏感用户配置
- 调试备注
- 缓存清理建议
- 权限修复步骤说明
- 系统诊断摘要

AI 不应自动写入：

- console token
- Codex bridge token
- VPN 订阅 URL
- 私有 endpoint token
- Android 权限授权结果
- 用户未确认的缓存清理
- 用户未确认的 VPN 开关

AI 操作系统配置时，需要遵守：

- 涉及权限跳转时，需要说明用户需要在系统页确认。
- 涉及 VPN start/stop 时，需要确认当前任务确实需要。
- 涉及缓存清理时，需要提示会删除哪些区域。
- 涉及 token 或密钥时，只能提示重新填写，不能读取明文后复述。

#### 3.10.4 二级内容划分

建议系统配置区域下再分这些二级内容：

##### A. App 基础设置

保存基础偏好。

字段：

- language
- darkTheme
- accentColor
- uiStyle
- response language
- updatedAt

当前对应：

- `AgentConfig`
- `ConfigSnapshot.language`
- `ConfigSnapshot.darkTheme`
- `ConfigSnapshot.accentColor`
- `ConfigSnapshot.uiStyle`

注意：

- 模型网关相关字段已经归 `3.8 模型与网关`。
- 用户画像类配置归 `3.2 我的记忆`。

##### B. 用户显式配置

保存用户通过配置页主动填写的 key-value。

字段：

- key
- value
- description
- sensitive
- exposedToMemory
- updatedAt

当前对应：

- `UserConfig`
- DataStore `user_config.entries_v2`
- `ConfigEntry`

注意：

- 非敏感用户配置可以同步到我的记忆。
- 包含 key/token/secret/password/credential 的配置不能注入 prompt。
- 系统配置区域只展示配置本身，我的记忆展示可用于个性化的摘要。

##### C. 权限诊断

保存权限检查结果摘要。

字段：

- permission id
- title
- granted
- required
- action
- lastCheckedAt
- deviceHint

当前对应：

- `PermissionManager`
- `PermissionDiagnosis`
- `PermissionItem`

权限类型示例：

- 无障碍服务
- 悬浮窗
- 通知
- 后台保活
- 自启动
- VPN 授权
- 文件访问

注意：

- Android 权限状态不能通过工作空间导入直接恢复。
- 导出时只保存诊断摘要和修复建议。
- 导入后必须重新扫描。

##### D. 手机控制与虚拟显示

保存手机控制能力状态。

字段：

- accessibilityEnabled
- overlayEnabled
- virtualDisplayActive
- screenshotAvailable
- coordinateSpace
- lastDiagnosis

当前对应：

- `VirtualDisplayManager`
- `ScreenshotController`
- `PhoneScreenState`
- `PermissionManager`

注意：

- 截图内容不属于系统配置长期导出。
- 虚拟显示运行状态是当前设备状态，不可迁移。
- 只导出能力说明和诊断结果。

##### E. 控制台和局域网服务

保存 MobileClaw 内置控制台服务配置。

字段：

- enabled
- lanUrl
- port
- tokenConfigured
- lastStartedAt
- allowedNetwork

当前对应：

- `ConsoleServer`
- `console_server_token`
- `filesDir/console_web/`

注意：

- console token 绝不明文导出。
- LAN URL 是当前网络状态，不保证跨设备可用。
- 控制台 Web 文件如为 AI 生成页面，归工作产物或系统资源，不混入普通用户记忆。

##### F. 桌面桥接配置

保存手机连接桌面 Codex bridge 的配置摘要。

字段：

- endpoint
- tokenConfigured
- cwd
- provider
- status
- updatedAt

当前对应：

- `CodexDesktopSkill`
- `codex_desktop_endpoint`
- `codex_desktop_token`
- `codex_desktop_cwd`
- `codex_desktop_provider`

注意：

- 这是系统级外部桥接配置，不是 MCP。
- token 默认不导出。
- endpoint 可导出但需要提示局域网地址可能变化。

##### G. VPN 配置和状态

保存 VPN 订阅、代理选择和运行状态摘要。

字段：

- subscription id
- name
- urlConfigured
- proxy count
- selected proxy
- status
- latency
- updatedAt

当前对应：

- `VpnManager`
- `VpnSubscription`
- `VpnControlSkill`
- `ClawVpnService`
- `MihomoConfigBuilder`
- `MihomoProcess`
- VPN database

注意：

- VPN 授权不能跨设备恢复。
- 订阅 URL 可能敏感，默认脱敏。
- Mihomo runtime config 属于运行缓存，不默认导出。
- 当前连接状态只是设备状态，导入后需要重新启动。

##### H. 缓存与清理

展示可清理数据分类。

字段：

- category id
- title
- sizeBytes
- pathCount
- paths
- clearable
- lastScannedAt

当前对应：

- `CacheCleaner`
- `CacheCategory`

当前缓存分类：

- temp
- vpn
- stickers
- chat_images
- documents
- videos
- html
- created_files
- python_packages
- task_replays

注意：

- 有些分类同时也是工作空间区域的物理目录。
- 清理前需要显示会影响哪些区域。
- `documents`、`videos`、`created_files` 不应被粗暴当作无价值缓存。
- 后续需要把“缓存清理”和“工作空间清理”区分开。

##### I. 调试与运行状态

保存调试入口和运行状态摘要。

字段：

- debugEnabled
- activeWorkflow
- routerDebug
- lastError
- appVersion
- buildType
- updatedAt

当前对应：

- `MainActivity` debug intent
- `TaskRouter.debugReason`
- `AgentRuntime` event/log
- active workflow state

注意：

- 调试日志可能包含隐私和密钥，默认不导出。
- 只导出摘要和用户明确选择的诊断包。

#### 3.10.5 数据来源

现有数据来源：

- `AgentConfig`
- `PermissionManager`
- `ConsoleServer`
- `VirtualDisplayManager`
- `VpnControlSkill`
- `UserConfig`
- `CacheCleaner`
- `ConfigSnapshot`
- `VpnManager`
- `ClawVpnService`
- `MihomoConfigBuilder`
- `CodexDesktopSkill`
- `TaskRouter`
- `AgentRuntime`

详细来源：

| 内容 | 当前来源 |
|---|---|
| App 基础设置 | DataStore `agent_config` |
| 用户显式配置 | DataStore `user_config.entries_v2` |
| 权限诊断 | `PermissionManager` |
| 虚拟显示 | `VirtualDisplayManager` |
| 截图/坐标状态 | `ScreenshotController`, `PhoneScreenState` |
| 控制台服务 | `ConsoleServer` |
| console token | `UserConfig.console_server_token` |
| 桌面桥接 | `CodexDesktopSkill`, `UserConfig` |
| VPN 状态 | `VpnManager`, `ClawVpnService` |
| VPN 配置 | VPN database, `MihomoConfigBuilder` |
| 缓存分类 | `CacheCleaner` |
| 调试状态 | `TaskRouter`, `AgentRuntime`, `MainActivity` |

#### 3.10.6 文件路径

当前相关路径：

```text
DataStore: agent_config
DataStore: user_config
filesDir/console_web/
filesDir/stickers/
filesDir/chat_images/
filesDir/documents/
filesDir/videos/
filesDir/html_pages/
filesDir/created_files/
filesDir/pip_packages/
filesDir/task_replays/
filesDir/task_recipes/
cacheDir/
externalCacheDir/
```

VPN 运行时可能涉及：

```text
cacheDir/mihomo-latency-*
filesDir/mihomo-runtime-*.yml
```

后续建议系统配置区域导出结构：

```text
workspace_export/system/
  manifest.json
  app_settings.json
  user_config.json
  permission_diagnosis.json
  console.json
  desktop_bridge.json
  vpn.json
  cache_summary.json
  diagnostics.json
```

#### 3.10.7 数据库表

当前系统配置主要不在统一 Room 表中。

当前存储：

- DataStore `agent_config`
- DataStore `user_config`
- VPN 相关数据库表
- 文件系统缓存路径

后续可考虑：

- `system_diagnostics`
- `permission_snapshots`
- `cache_snapshots`
- `external_bridge_configs`

但系统配置不一定需要全部入库，DataStore + 导出 manifest 也可以满足。

#### 3.10.8 导出范围

系统配置导出包建议包含：

- 语言
- 主题
- UI 风格
- 非敏感用户配置
- 权限诊断摘要
- 控制台配置摘要
- 桌面桥 endpoint 摘要
- VPN 订阅摘要
- 缓存分类摘要
- 调试摘要

默认不包含：

- console token
- codex desktop token
- VPN 订阅 URL 明文
- Mihomo 原始配置
- Android 权限授权状态的可恢复值
- 调试日志全文
- cacheDir 内容

可选包含：

- 诊断包
- 缓存路径摘要
- VPN 订阅配置脱敏版本
- 桌面桥配置脱敏版本

导出提示：

```text
系统配置会导出 App 设置和诊断摘要，但不会导出 token、VPN 私密链接或 Android 权限授权。导入后需要重新检查权限并补充敏感配置。
```

#### 3.10.9 导入策略

导入系统配置时需要处理：

- 设备权限不可恢复
- token 缺失
- 局域网 endpoint 变化
- VPN 订阅 URL 缺失
- 系统版本差异
- App 版本差异
- 缓存路径不可用

建议导入选项：

1. 只预览，不写入
2. 导入基础 App 设置
3. 导入非敏感用户配置
4. 跳过所有 token
5. 桌面桥配置导入为待配置
6. VPN 配置导入为待配置
7. 只导入诊断摘要，不覆盖本机设置

导入后必须执行：

- 重新检查权限
- 重新检查控制台服务
- 重新检查桌面桥 endpoint
- 重新检查 VPN 授权
- 重新扫描缓存
- 标记缺失 token 的配置

#### 3.10.10 敏感信息

可能敏感：

- console token
- codex desktop token
- VPN 订阅 URL
- VPN raw yaml
- 内网 IP
- 局域网服务地址
- 用户配置中的 token/key/password
- 调试日志
- 文件路径中的用户名

处理原则：

- token 不进入 Markdown。
- token 不默认导出。
- VPN raw config 不默认导出。
- 权限诊断只记录状态，不记录无关设备信息。
- 用户配置导出前按 key 过滤敏感项。
- AI 只能读取脱敏摘要。

#### 3.10.11 冲突处理

导入时可能冲突：

- 本地语言和导入语言不同
- 本地主题和导入主题不同
- 同 key 用户配置不同
- 本地已有 console token
- 桌面桥 endpoint 不同
- VPN 订阅同名不同 URL
- 权限状态和导入摘要不同

建议处理方式：

- 基础 UI 设置：用户可选择覆盖。
- 用户配置冲突：本地优先，导入为候选。
- token：永远不被导入覆盖。
- 桌面桥 endpoint：本地优先，导入值作为候选。
- VPN 订阅：同名冲突时创建副本或提示合并。
- 权限状态：忽略导入摘要，以当前设备重新检查为准。

#### 3.10.12 UI 展示建议

建议 UI 名称：

- 系统配置

一级入口卡片展示：

- 权限健康度
- 控制台状态
- VPN 状态
- 缓存总量
- 需要补配置数量

列表展示：

- 基础设置
- 用户配置
- 权限诊断
- 手机控制
- 控制台
- 桌面桥接
- VPN
- 缓存清理
- 调试诊断

系统配置详情展示：

- 状态摘要
- 是否可迁移
- 是否包含敏感信息
- 最近检查时间
- 修复入口

移动端交互建议：

- 权限项用状态列表，不用复杂说明页。
- 每个权限提供一个系统跳转按钮。
- token 只显示“已配置/未配置”。
- 缓存清理显示影响区域。
- 导入后给出“需要重新授权”的 checklist。

#### 3.10.13 搜索与排序

支持搜索：

- 配置 key
- 权限名称
- 服务名称
- VPN 订阅名
- 缓存分类
- 诊断错误

支持过滤：

- 需要处理
- 已配置
- 缺少 token
- 需要权限
- 可导出
- 不可迁移
- 缓存

排序：

- 风险优先
- 需要处理优先
- 最近更新
- 分类
- 名称

#### 3.10.14 空状态

权限都正常：

```text
系统状态正常。
MobileClaw 已具备当前任务所需的运行权限。
```

导入后缺少授权：

```text
工作空间已导入，但系统权限不能自动恢复。
请按清单重新开启无障碍、悬浮窗、VPN 或后台运行权限。
```

缺少 token：

```text
部分服务需要重新填写 token。
为保护隐私，导出包不会包含明文密钥。
```

#### 3.10.15 后续待定问题

- 是否需要统一的 system manifest？
- 是否需要区分“系统设置”和“用户显式配置”两个入口？
- 是否需要把敏感配置迁移到 Android Keystore？
- 是否需要系统诊断包导出？
- 是否需要权限修复向导？
- 是否需要导入后自动生成权限 checklist？
- 是否需要缓存清理前做引用检查？
- 是否需要将 VPN 订阅归系统配置还是单独区域？

导出建议：

- 可导出非敏感配置
- 权限状态只作为提示，不可跨设备直接恢复
- 调试 token 默认不导出
- VPN 私密订阅默认不导出
- 导入后必须重新诊断系统状态
- 缓存目录默认不随系统配置导出

### 3.11 任务队列

用于管理长任务、后台任务和可恢复任务。

任务队列区域不是任务工作空间本体。

它是 MobileClaw 工作空间中专门保存“任务调度状态、后台任务、可恢复任务、任务复盘和自动重试入口”的区域。

这一块应该回答：

- 当前有哪些正在运行或等待继续的任务？
- 哪些任务可以恢复？
- 哪些任务失败后可以重试？
- 视频生成任务处于什么状态？
- 哪些任务有 replay 或 recipe？
- 哪些任务关联了具体任务工作空间？
- 导出时如何处理正在运行的任务？

包含：

- 待执行任务
- 自动任务
- 定时任务
- 视频生成任务
- 长任务状态
- 失败重试
- 后台队列
- active workflow

#### 3.11.1 区域定位

任务队列区域是“任务调度和恢复索引”。

它保存的是：

- pending agent task
- active workflow
- 正在运行的 session task
- 等待用户授权的任务
- 等待角色切换确认的任务
- MiniAPP 自动修复任务
- 视频生成任务
- 群聊待处理 turn
- task replay
- task recipe
- 任务失败和重试摘要
- 任务与 workspace id 的关联

它不应该保存：

- 某次任务的完整工作文件
- 完整会话消息
- 生成媒体文件本体
- 角色长期记忆
- 玩法局内完整状态

这些分别归入：

- 任务工作空间
- 会话记录
- 媒体资产
- 角色档案
- 玩法空间

任务队列和任务工作空间的关系：

- 任务队列回答“有什么任务要跑、能不能恢复、状态是什么”。
- 任务工作空间回答“这个任务运行过程中产生了什么现场记录和文件”。
- 一个任务队列项可以关联一个 `workspaceId`。
- 一个 `workspaceId` 可以被会话、群聊、角色运行或玩法局引用。

#### 3.11.2 用户能看到什么

用户应该能看到：

- 正在运行任务
- 待继续任务
- 失败任务
- 可重试任务
- 视频生成任务
- 自动修复任务
- 任务类型
- 任务目标摘要
- 关联会话
- 关联群聊
- 关联角色
- 关联工作空间
- 最近更新时间
- 失败原因摘要
- 是否可导出

用户不应该默认看到：

- 完整 prompt
- 完整模型响应
- API Key
- 原始 submit/poll response
- 内部调度堆栈

高级视图可以展示：

- task id
- workspace id
- route debug reason
- retry count
- last event
- replay id
- recipe id

#### 3.11.3 AI 能读写什么

AI 可以读取：

- 当前 active workflow 摘要
- 可恢复任务列表
- 任务类型
- 失败原因摘要
- 关联 workspace
- task recipe
- task replay 摘要

AI 可以写入：

- 任务状态摘要
- 重试建议
- replay
- recipe
- 失败经验
- 任务 workspace 关联
- 非敏感的调度备注

AI 不应自动写入：

- 用户未确认的后台任务启动
- 用户未确认的失败任务重试
- 用户未确认的任务删除
- 外部服务 API Key
- 正在运行任务的强制停止

AI 继续任务时，需要遵守：

- 只有用户明确说“继续、重试、修改、接着做”时，才恢复 active workflow。
- 普通聊天不应误触发旧任务。
- 恢复任务前应读取最新 task summary 和 workspace checkpoint。
- 失败任务重试需要改变策略或说明沿用策略的原因。

#### 3.11.4 二级内容划分

建议任务队列区域下再分这些二级内容：

##### A. 当前运行任务

展示正在运行的任务。

字段：

- task id
- session id
- group id
- role id
- task type
- goal summary
- workspace id
- startedAt
- updatedAt
- status
- currentStep

操作：

- 查看
- 停止
- 打开工作空间
- 查看日志摘要

##### B. Active Workflow

保存会话级可继续任务。

字段：

- session id
- originalGoal
- taskType
- updatedAt
- ttl
- latestArtifact
- workspace id

当前对应：

- `MainViewModel.activeWorkflows`
- `ActiveWorkflow`
- `TaskRouter.shouldContinueActiveWorkflow(...)`

注意：

- active workflow 是短期恢复上下文。
- 不应永久作为长期任务队列保存。
- 导出时可以保存摘要，但导入后默认不自动恢复运行。

##### C. 等待确认任务

保存需要用户授权或确认后才能继续的任务。

字段：

- pending id
- type
- originalGoal
- reason
- createdAt
- expiresAt

当前对应：

- `pendingAccessibilityTaskGoal`
- `pendingRoleSwitchTaskGoal`
- `pendingConfirmedRoutes`

注意：

- 权限类确认不能跨设备恢复。
- 导入后只能作为历史摘要。

##### D. 视频生成任务

保存视频生成外部任务状态。

字段：

- taskId
- prompt
- provider
- endpoint
- model
- status
- videoUrl
- filePath
- errorMessage
- createdAt
- updatedAt

当前对应：

- `VideoGenerationTaskEntity`
- `VideoGenerationTaskManager`
- `GenerateVideoSkill`
- `video_generation_tasks`

注意：

- 视频任务记录归任务队列。
- 下载后的视频文件归媒体资产。
- `apiKey` 不默认导出。
- 正在运行的视频任务导入后需要重新 poll 或标记为不可恢复。

##### E. MiniAPP 自动修复任务

保存 MiniAPP 预检失败后的自动修复状态。

字段：

- app id
- session id
- previewStatus
- attempt
- originalGoal
- updatedAt

当前对应：

- `pendingMiniAppAutoRepairs`
- `MiniAppPreflightValidator`
- `MiniAppValidationOverlayManager`

注意：

- 自动修复任务通常只在当前设备当前会话内有效。
- 导出时保存摘要和关联 app，不保存为可自动执行队列。

##### F. 群聊待处理 turn

保存群聊多角色调度中的待执行消息。

字段：

- group id
- queued message
- role id
- priority
- createdAt

当前对应：

- `GroupTurnScheduler`
- `GroupTurnQueue`
- `GroupState.pendingMessages`

注意：

- 群聊消息和成员关系归群聊区域。
- 待执行 turn 是运行状态，默认不作为可恢复跨设备任务。

##### G. Task Replay

保存任务执行复盘。

字段：

- replay id
- goal
- taskType
- steps
- finalStatus
- createdAt

当前对应：

- `TaskReplayStore`
- `filesDir/task_replays/`

用途：

- 复盘任务过程
- 创建 recipe
- 调试失败路径
- 迁移可复用任务经验

##### H. Task Recipe

保存从 replay 提炼出来的可复用任务模板。

字段：

- recipe id
- title
- goal
- taskType
- steps
- createdAt
- updatedAt

当前对应：

- `TaskRecipeStore`
- `TaskRecipeSkill`
- `filesDir/task_recipes/`

注意：

- recipe 更像可复用自动化模板。
- 可以导出。
- 导入后需要检查相关技能、权限和模型是否存在。

##### I. 任务工作空间关联

保存任务队列项与 `WorkspaceStore` 的关联。

字段：

- task id
- workspace id
- session id
- group id
- role id
- artifact count
- checkpoint count
- updatedAt

当前对应：

- `WorkspaceStore`
- `WorkspaceRuntimeCoordinator`
- `WorkspaceRuntimeRecorder`
- `filesDir/workspaces/ws_xxxxxxxx/`

注意：

- 队列只保存引用。
- workspace 内部文件仍归任务工作空间/工作产物章节。

#### 3.11.5 数据来源

现有数据来源：

- `VideoGenerationTaskEntity`
- pending task
- task replay
- active workflow
- task runtime state
- `TaskReplayStore`
- `TaskRecipeStore`
- `TaskRecipeSkill`
- `WorkspaceStore`
- `WorkspaceRuntimeCoordinator`
- `WorkspaceRuntimeRecorder`
- `MainViewModel.activeWorkflows`
- `pendingAccessibilityTaskGoal`
- `pendingRoleSwitchTaskGoal`
- `pendingConfirmedRoutes`
- `pendingMiniAppAutoRepairs`
- `GroupTurnScheduler`
- `pendingAgentTask`

详细来源：

| 内容 | 当前来源 |
|---|---|
| pending agent task | `ClawApplication.pendingAgentTask` |
| active workflow | `MainViewModel.activeWorkflows` |
| 权限等待任务 | `pendingAccessibilityTaskGoal` |
| 角色切换等待任务 | `pendingRoleSwitchTaskGoal` |
| 已确认 route | `pendingConfirmedRoutes` |
| MiniAPP 修复任务 | `pendingMiniAppAutoRepairs` |
| 视频任务 | `video_generation_tasks` |
| task replay | `filesDir/task_replays/` |
| task recipe | `filesDir/task_recipes/` |
| 任务工作空间 | `filesDir/workspaces/ws_xxxxxxxx/` |
| 群聊待处理 turn | `GroupTurnScheduler`, `GroupState.pendingMessages` |

#### 3.11.6 文件路径

当前相关路径：

```text
filesDir/task_replays/
filesDir/task_recipes/
filesDir/workspaces/ws_xxxxxxxx/
filesDir/videos/
```

Room 表：

```text
video_generation_tasks
```

后续建议任务队列区域导出结构：

```text
workspace_export/tasks/
  manifest.json
  queue.json
  active_workflows.json
  video_tasks.json
  replays/
  recipes/
  workspace_links.json
```

#### 3.11.7 数据库表

当前明确任务表：

- `video_generation_tasks`

当前运行态主要在内存：

- `activeWorkflows`
- `pendingConfirmedRoutes`
- `pendingMiniAppAutoRepairs`
- `GroupTurnScheduler`

后续如果要支持真正可恢复任务队列，建议增加：

- `task_queue_items`
- `task_attempts`
- `task_workspace_links`
- `task_replay_index`
- `task_recipe_index`

建议字段：

`task_queue_items`：

- `id`
- `type`
- `status`
- `goalSummary`
- `sessionId`
- `groupId`
- `roleId`
- `workspaceId`
- `retryCount`
- `lastError`
- `createdAt`
- `updatedAt`

`task_attempts`：

- `id`
- `taskId`
- `attempt`
- `status`
- `startedAt`
- `finishedAt`
- `errorSummary`

`task_workspace_links`：

- `id`
- `taskId`
- `workspaceId`
- `linkType`
- `createdAt`

#### 3.11.8 导出范围

任务队列导出包建议包含：

- 已完成任务摘要
- 可恢复任务摘要
- 失败任务摘要
- 视频任务记录
- task replay
- task recipe
- workspace link
- retry count
- createdAt / updatedAt

默认不包含：

- 正在运行中的内存状态
- API Key
- 完整 raw response
- 临时 pending confirmation
- 群聊运行中队列
- 大视频文件本体

可选包含：

- 正在运行任务的停止前快照
- 失败诊断详情
- replay 详细步骤
- recipe 模板
- 关联 workspace 文件

导出提示：

```text
正在运行的任务需要先停止或生成快照。导出包会保存任务摘要和恢复线索，但不会在新设备上自动继续执行。
```

#### 3.11.9 导入策略

导入任务队列时需要处理：

- 任务 id 冲突
- workspace id 缺失
- 关联会话缺失
- 关联角色缺失
- 关联技能缺失
- 视频任务 provider 不可用
- 外部 task id 已过期
- replay/recipe 版本不兼容

建议导入选项：

1. 只导入历史摘要
2. 导入 replay
3. 导入 recipe
4. 导入视频任务记录但不自动刷新
5. 跳过正在运行任务
6. 导入 workspace link
7. 缺失依赖时标记为不可恢复

导入后需要执行：

- 检查角色引用
- 检查技能引用
- 检查 workspace 引用
- 检查视频 provider
- 检查权限需求
- 重建 replay/recipe 索引

#### 3.11.10 敏感信息

可能敏感：

- 任务目标
- prompt 摘要
- 文件路径
- 视频 prompt
- 外部 provider endpoint
- task raw response
- apiKey
- 用户操作意图

处理原则：

- API Key 不导出。
- raw response 默认不导出。
- 任务目标可导出但需要用户预览。
- 与权限、VPN、手机控制相关的任务需要标注敏感。
- 导入后任务不会自动执行。

#### 3.11.11 冲突处理

导入时可能冲突：

- 同 task id 不同内容
- 同 replay id 不同步骤
- 同 recipe id 不同模板
- workspace id 已存在
- 视频 task id 重复
- 本地已有 active workflow

建议处理方式：

- task id 冲突：创建新 id。
- replay id 冲突：按 hash 去重。
- recipe id 冲突：同名创建副本。
- workspace id 冲突：保留本地，导入 workspace 重新分配 id。
- active workflow：不从导入包恢复为 active。
- 视频 task id 重复：保留本地状态，导入记录作为历史。

#### 3.11.12 UI 展示建议

建议 UI 名称：

- 任务队列

一级入口卡片展示：

- 运行中任务数
- 待继续任务数
- 失败任务数
- 视频任务数
- recipe 数量
- 最近更新时间

列表展示：

- 任务标题
- 任务类型
- 状态
- 关联角色
- 关联会话/群聊
- 最近时间
- 操作按钮

详情展示：

- 目标摘要
- 状态时间线
- 失败原因
- workspace 链接
- replay/recipe 链接
- 重试入口

移动端交互建议：

- 运行中任务提供停止按钮。
- 失败任务提供重试按钮，但重试前展示原因。
- recipe 以模板列表展示。
- 视频任务显示状态和刷新按钮。
- 不把内部队列细节直接暴露给普通用户。

#### 3.11.13 搜索与排序

支持搜索：

- 任务目标
- task type
- role name
- session title
- group name
- recipe title
- error summary

支持过滤：

- 运行中
- 待继续
- 失败
- 已完成
- 视频
- replay
- recipe
- 有 workspace
- 缺失依赖

排序：

- 最近更新
- 状态优先
- 失败优先
- 类型
- 角色
- 创建时间

#### 3.11.14 空状态

没有任务：

```text
当前没有后台任务。
当 MobileClaw 执行长任务、生成视频或创建可复用任务模板时，会在这里显示。
```

有失败任务：

```text
有任务执行失败。
你可以查看失败原因，打开关联工作空间，或让 AI 调整策略后重试。
```

导入后不可恢复：

```text
部分任务来自其他设备，不能直接继续执行。
你仍然可以查看任务摘要、复盘和关联产物。
```

#### 3.11.15 后续待定问题

- 是否需要真正持久化 `task_queue_items`？
- 是否需要跨 App 重启恢复 active workflow？
- 是否需要任务暂停/恢复协议？
- 是否需要任务依赖图？
- 是否需要任务优先级？
- 是否需要自动重试策略？
- 是否需要将 replay 和 recipe 移到技能市场或任务模板市场？
- 是否需要按角色统计任务执行历史？

导出建议：

- 已完成任务可导出
- replay 和 recipe 可导出
- 正在运行任务导出前需要先停止或快照
- 外部服务任务需要记录 provider 和 task id
- API Key 和 raw response 默认不导出
- 导入后不自动执行任务

### 3.12 玩法空间

用于承载狼人杀、AI Town、桌游、剧情模拟等可扩展玩法的规则、模板和运行时状态。

玩法空间不保存群聊本体。群聊区域负责群和成员关系，玩法空间负责“某个玩法如何运行”。

包含：

- 玩法模板
- 玩法规则
- 游戏局配置
- 群聊挂载关系
- 玩家角色
- 身份牌
- 回合状态
- 发言记录
- 投票记录
- 角色私有信息
- 裁判/主持人状态
- 胜负状态
- 玩法事件日志
- 玩法结算记录
- AI Town 相关展示状态

现有数据来源：

- `GroupManager`
- `GroupMessageEntity`
- `AgentTownStore`
- 未来狼人杀 runtime

建议 UI 名称：

- 玩法空间

导出建议：

- 每一局游戏应可独立导出
- 需要区分公开信息和角色私有信息
- 可支持玩法模板导入导出
- 群聊基础信息不在这里完整导出，只记录关联 group id 和必要快照

### 3.13 备份与迁移

用于承载总工作空间的导入导出操作。

包含：

- 全量导出
- 分区导出
- 角色包导出
- 技能包导出
- 工作产物导出
- 导入预检
- 冲突处理
- 版本迁移
- 敏感信息脱敏
- 只导出本地可迁移内容

建议 UI 名称：

- 备份与迁移

导出建议：

- 需要 manifest
- 需要 schema version
- 需要 app version
- 需要导出时间
- 需要敏感信息策略

## 4. 建议的总工作空间一级导航

建议一级区域顺序：

1. AI 角色
2. 我的记忆
3. 工作产物
4. 会话记录
5. 群聊区域
6. 技能库
7. MCP 连接
8. 模型与网关
9. 媒体资产
10. 系统配置
11. 任务队列
12. 玩法空间
13. 备份与迁移

## 5. 每个区域后续需要补充的字段

后续扩充每个区域时，建议统一补充这些字段：

- 区域名称
- 区域说明
- 用户能看到什么
- AI 能读写什么
- 数据来源
- 文件路径
- 数据库表
- 是否可导出
- 是否默认导出
- 是否包含敏感信息
- 是否可导入覆盖
- 是否支持增量导入
- 是否支持单项删除
- 是否支持跨设备迁移
- 冲突处理策略
- 版本迁移策略
- UI 展示方式
- 搜索字段
- 排序字段
- 空状态文案
- 风险提示

## 6. 导入导出初步原则

### 6.1 导出原则

- 默认不导出明文密钥。
- 默认不导出临时缓存。
- 大文件需要用户确认。
- 支持按区域导出。
- 支持全量导出。
- 导出包必须包含 manifest。
- 导出包必须包含 schema version。

### 6.2 导入原则

- 导入前必须预检。
- 导入前展示包含的区域。
- 导入前提示敏感配置缺失。
- 支持跳过冲突项。
- 支持覆盖冲突项。
- 支持创建副本。
- 导入后需要重建索引。

### 6.3 敏感信息原则

默认脱敏：

- API Key
- MCP token
- 控制台 token
- 私有 endpoint 中的认证信息
- 用户显式标记为私密的记忆

默认可导出：

- 角色定义
- 非私密用户偏好
- 技能定义
- 工作产物
- 会话文本
- 非敏感系统设置

## 7. 当前需要注意的问题

### 7.1 “工作空间”命名冲突

当前代码里已经存在：

- `WorkspaceStore`：任务工作空间
- `RoleWorkspaceStore`：角色档案
- `AgentTownStore`：角色展示空间

后续 UI 中建议：

- 总入口叫：总工作空间
- 任务现场叫：任务工作空间
- 角色长期上下文叫：角色档案
- 角色视觉展示叫：角色空间

### 7.2 角色区域不应吞掉任务工作空间

角色可以拥有长期档案，但某次任务的过程应该属于任务工作空间。

例如狼人杀：

- 角色档案：角色长期性格、模型、技能、记忆。
- 群聊区域：这群人是谁、成员关系如何、群内沉淀了什么。
- 玩法空间：某一局游戏规则、回合和状态。
- 任务工作空间：某个角色在某一局中的推理、发言、行动记录。

### 7.3 导入导出需要先有区域 manifest

如果没有统一 manifest，后续导入导出会变成路径复制，难以处理版本和冲突。

建议后续定义：

```json
{
  "schemaVersion": 1,
  "app": "mobileClaw",
  "exportedAt": "...",
  "sections": [
    {
      "id": "roles",
      "name": "AI 角色",
      "itemCount": 0,
      "containsSecrets": false
    }
  ]
}
```

## 8. 工作空间功能落地架构

### 8.1 核心判断

MobileClaw 不应该把“总工作空间”做成一个新的孤立数据系统。

更合理的做法是：

> 在现有 chat、角色、技能、MCP、任务 workspace、群聊、产物和设置之上，新增一层统一的工作空间索引、区域适配器和导入导出协议。

也就是说：

- 不推翻 `WorkspaceStore`。
- 不把所有数据搬到一个新目录。
- 不让 chat 直接理解所有区域细节。
- 不让每个页面各自实现导入导出。
- 通过统一 `WorkspaceAreaProvider` 把现有数据源映射到总工作空间。

### 8.2 总体架构

建议架构分为 5 层：

```text
UI 层
  WorkspaceHomePage
  WorkspaceAreaPage
  WorkspaceItemDetailPage
  WorkspaceExportImportPage

应用服务层
  WorkspaceRegistry
  WorkspaceQueryService
  WorkspaceWriteService
  WorkspaceExportService
  WorkspaceImportService
  WorkspacePrivacyPolicy

区域适配层
  RolesAreaProvider
  UserMemoryAreaProvider
  WorkArtifactsAreaProvider
  SessionsAreaProvider
  GroupsAreaProvider
  SkillsAreaProvider
  McpAreaProvider
  ModelsAreaProvider
  MediaAreaProvider
  SystemAreaProvider
  TasksAreaProvider
  PlayAreaProvider

现有领域层
  RoleManager / RoleWorkspaceStore
  SemanticMemory / MemoryWriter / UserConfig
  WorkspaceStore / WorkspaceRuntimeRecorder
  SessionMessageDao / GroupMessageDao
  SkillRegistry / SkillLoader / SkillMarket
  McpHttpClient / McpSkillExecutor
  AgentConfig / LocalModelManager
  AgentTownStore / media files
  PermissionManager / ConsoleServer / VpnManager
  TaskReplayStore / TaskRecipeStore / VideoGenerationTaskDao

存储层
  Room
  DataStore
  filesDir
  cacheDir
  external app files
```

关键点：

- 总工作空间层只做“统一索引、统一查看、统一导入导出、统一隐私策略”。
- 各区域数据仍由原本 owner 维护。
- 区域适配器负责把原始数据转换成统一工作空间条目。
- 需要写入时，通过区域 owner 的现有 API 写入，避免绕开业务规则。

### 8.3 核心概念模型

建议先定义 4 个核心模型：

#### A. WorkspaceArea

表示一个工作空间区域。

字段：

- `id`
- `name`
- `description`
- `itemCount`
- `updatedAt`
- `sizeBytes`
- `containsSecrets`
- `defaultExport`
- `supportsImport`
- `supportsExport`
- `health`

区域 id 建议：

```text
roles
user_memory
work
sessions
groups
skills
mcp
models
media
system
tasks
play
backup
```

#### B. WorkspaceItem

表示区域下可查看、可导出、可删除或可跳转的最小条目。

字段：

- `id`
- `areaId`
- `type`
- `title`
- `subtitle`
- `summary`
- `source`
- `sourceRef`
- `createdAt`
- `updatedAt`
- `sizeBytes`
- `tags`
- `linkedRefs`
- `sensitiveLevel`
- `exportPolicy`
- `status`

示例：

- 一个角色是一个 item。
- 一个 session 是一个 item。
- 一个技能是一个 item。
- 一个 MCP 连接是一个 item。
- 一个媒体文件是一个 item。
- 一个 task replay 是一个 item。

#### C. WorkspaceReference

表示区域之间的引用关系。

字段：

- `fromArea`
- `fromId`
- `toArea`
- `toId`
- `relation`
- `createdAt`

典型关系：

- session -> workspace
- session -> artifacts
- group -> roles
- role -> skills
- role -> model profile
- skill -> mcp connection
- video task -> media file
- artifact -> media asset
- play round -> group

#### D. WorkspaceManifest

表示导出包和全局工作空间快照。

字段：

- `schemaVersion`
- `app`
- `appVersion`
- `exportedAt`
- `deviceSummary`
- `areas`
- `privacyPolicy`
- `files`
- `warnings`

注意：

- 当前代码已有 `WorkspaceManifest`，但它是任务工作空间 manifest。
- 总工作空间 manifest 建议命名为 `MobileWorkspaceManifest` 或 `WorkspaceExportManifest`，避免和任务 workspace 混淆。

### 8.4 AreaProvider 接口

每个区域实现统一接口。

建议接口：

```kotlin
interface WorkspaceAreaProvider {
    val areaId: String
    suspend fun areaSummary(): WorkspaceArea
    suspend fun listItems(query: WorkspaceQuery): List<WorkspaceItem>
    suspend fun getItem(itemId: String): WorkspaceItemDetail?
    suspend fun references(itemId: String): List<WorkspaceReference>
    suspend fun estimateExport(request: WorkspaceExportRequest): WorkspaceExportEstimate
    suspend fun export(request: WorkspaceExportRequest, writer: WorkspaceExportWriter): WorkspaceAreaExportResult
    suspend fun preflightImport(reader: WorkspaceImportReader): WorkspaceImportPreflight
    suspend fun import(request: WorkspaceImportRequest): WorkspaceImportResult
}
```

第一版不需要所有 provider 都支持写入和导入。

MVP 可以先支持：

- `areaSummary`
- `listItems`
- `getItem`
- `estimateExport`
- `export`

导入可以第二阶段做。

### 8.5 Chat 如何接入工作空间

Chat 是工作空间最重要的写入口。

建议分 4 类 chat 写入：

#### A. 普通聊天

普通聊天默认写入：

- 会话记录区域
- 用户记忆区域，只有明确记忆或稳定偏好才写入

普通聊天不一定创建任务工作空间。

判断原则：

- 只是问答、解释、闲聊：不创建 `WorkspaceStore`。
- 产生文件、页面、媒体、代码、长任务：创建或复用任务工作空间。
- 用户说“继续、重试、改一下”：优先恢复当前 session workspace。

#### B. Agent 任务聊天

当 chat 被路由到 agent 执行时：

1. `TaskRouter` 判断 task type。
2. `WorkspaceRuntimeCoordinator.ensureSessionBinding(...)` 创建或复用任务 workspace。
3. `AgentRuntime` 执行技能。
4. `WorkspaceRuntimeRecorder` 记录 checkpoint/event/artifact state。
5. Chat message 保存用户和 AI 可见结果。
6. 工作产物、媒体资产、任务队列分别建立引用。

现有代码已经具备 2、4 的基础。

需要补齐的是：

- session message -> workspace id 的稳定引用
- workspace -> session id 的反向索引
- artifact/media/task -> workspace 的统一引用索引

#### C. 产物型聊天

当 chat 生成产物时：

- 文件归工作产物区域。
- 图片/视频归媒体资产区域。
- HTML/MiniAPP/AI Native Page 归工作产物或 App 产物。
- 任务过程归任务工作空间。
- 当前会话只保存消息和附件引用。

不要把产物只挂在聊天消息里。

聊天消息应该像“时间线”，工作空间应该像“文件柜和索引”。

#### D. 群聊

群聊写入：

- 群聊区域：群、成员、消息、附件引用、角色发言。
- 角色区域：角色身份和长期档案。
- 任务工作空间：某个角色在群聊中执行某次任务的现场。
- 玩法空间：后续狼人杀等玩法局状态。

群聊中每个角色执行任务时，建议 scope：

```text
group:{groupId}:role:{roleId}
```

当前 `WorkspaceRuntimeCoordinator.ensureGroupBinding(...)` 已经接近这个模型。

### 8.6 其他功能如何接入工作空间

#### 角色

角色页负责编辑角色。

工作空间负责：

- 展示所有角色资产
- 展示角色档案文件
- 展示角色技能引用
- 展示角色模型画像
- 导出角色包
- 导入角色包

角色档案仍由 `RoleWorkspaceStore` 写入。

#### 技能

技能市场负责发现和安装技能。

工作空间负责：

- 列出内置/动态/市场/MCP 派生技能
- 管理技能笔记和注入等级
- 导出动态技能定义
- 导入动态技能定义
- 展示角色对技能的引用

#### MCP

MCP 页面负责连接和发现工具。

工作空间负责：

- 管理 MCP 连接注册表
- 导出 endpoint 和 tool schema
- 脱敏 headers
- 展示 MCP tool 到 dynamic skill 的映射

#### 模型

设置页负责配置模型。

工作空间负责：

- 展示网关结构
- 展示本地模型 manifest
- 展示角色模型画像
- 导出脱敏配置
- 导入后提示重新填写 API Key

#### 媒体

聊天、群聊、角色、AI Town 都可能产生媒体。

工作空间负责：

- 建立媒体索引
- 展示引用关系
- 控制大文件导出
- 清理孤立媒体

#### 系统配置

设置页负责操作。

工作空间负责：

- 展示可迁移配置
- 展示不可迁移权限状态
- 导入后生成权限 checklist

### 8.7 推荐新增模块

建议新增包：

```text
app/src/main/java/com/mobileclaw/workspace/global/
  WorkspaceArea.kt
  WorkspaceItem.kt
  WorkspaceReference.kt
  WorkspaceAreaProvider.kt
  WorkspaceRegistry.kt
  WorkspaceQueryService.kt
  WorkspaceExportService.kt
  WorkspaceImportService.kt
  WorkspacePrivacyPolicy.kt
```

区域 provider：

```text
app/src/main/java/com/mobileclaw/workspace/global/provider/
  RolesAreaProvider.kt
  UserMemoryAreaProvider.kt
  WorkArtifactsAreaProvider.kt
  SessionsAreaProvider.kt
  GroupsAreaProvider.kt
  SkillsAreaProvider.kt
  McpAreaProvider.kt
  ModelsAreaProvider.kt
  MediaAreaProvider.kt
  SystemAreaProvider.kt
  TasksAreaProvider.kt
  PlayAreaProvider.kt
```

UI：

```text
app/src/main/java/com/mobileclaw/ui/workspace/global/
  WorkspaceHomePage.kt
  WorkspaceAreaPage.kt
  WorkspaceItemDetailPage.kt
  WorkspaceExportPage.kt
  WorkspaceImportPage.kt
```

命名注意：

- 现有 `com.mobileclaw.workspace.WorkspaceStore` 保留为任务工作空间。
- 新增全局工作空间可以放在 `workspace.global`，避免误解。
- UI 上可以叫“总工作空间”，代码上可以叫 `MobileWorkspace` 或 `GlobalWorkspace`。

### 8.8 推荐数据存储策略

第一版不要急着新建大量 Room 表。

建议采用：

1. 现有 owner 继续保存原始数据。
2. 总工作空间通过 provider 动态读取。
3. 对跨区域引用和导出 manifest 新增少量索引。

MVP 可新增一个索引文件：

```text
filesDir/mobile_workspace/
  manifest.json
  references.json
  area_cache.json
  export_history/
```

`references.json` 保存跨区域引用：

```json
{
  "schemaVersion": 1,
  "refs": [
    {
      "fromArea": "sessions",
      "fromId": "session_xxx",
      "toArea": "work",
      "toId": "ws_xxxxxxxx",
      "relation": "uses_workspace"
    }
  ]
}
```

后续如果引用越来越多，再迁到 Room：

- `workspace_items`
- `workspace_references`
- `workspace_export_history`
- `workspace_import_jobs`

### 8.9 UI 架构

首页菜单进入“总工作空间”。

首页展示：

- 总体积
- 区域数量
- 最近更新
- 隐私风险
- 一键导出
- 导入入口
- 区域列表

区域页展示：

- 区域摘要
- 搜索
- 筛选
- 条目列表
- 批量导出
- 清理建议

详情页展示：

- 条目详情
- 来源
- 引用关系
- 文件路径
- 敏感信息状态
- 导出策略
- 跳转原功能页

关键体验：

- 工作空间页不替代原功能页。
- 工作空间页提供“看清楚、找得到、导得出、清得掉”。
- 真正编辑复杂内容时跳回角色页、技能页、设置页、聊天页。

### 8.10 导入导出架构

导出流程：

1. 用户选择全量或区域。
2. `WorkspaceExportService` 调用各 `AreaProvider.estimateExport(...)`。
3. 展示体积、敏感信息、缺失项、不可迁移项。
4. 用户确认。
5. 各 provider 写入临时导出目录。
6. 生成 `workspace_manifest.json`。
7. 打包为 zip。

导入流程：

1. 用户选择导入包。
2. 读取 `workspace_manifest.json`。
3. 各 provider 执行 `preflightImport(...)`。
4. 展示冲突、缺失密钥、版本差异、不可恢复权限。
5. 用户选择策略。
6. 分区域导入。
7. 重建引用索引。
8. 生成导入报告。

导出包结构：

```text
mobileclaw_workspace_export.zip
  workspace_manifest.json
  roles/
  user_memory/
  work/
  sessions/
  groups/
  skills/
  mcp/
  models/
  media/
  system/
  tasks/
  play/
```

### 8.11 隐私和权限架构

需要统一 `WorkspacePrivacyPolicy`。

它负责判断：

- 哪些 key 是敏感 key
- 哪些文件默认不导出
- 哪些路径需要脱敏
- 哪些区域导出前需要二次确认
- 哪些内容 AI 不能读取明文

敏感规则：

- key 包含 `key/token/secret/password/credential/api_key`
- header 包含 `Authorization/Cookie/X-API-Key`
- URL query 中包含 `token/key/secret`
- VPN raw config
- console token
- Codex bridge token
- 模型 API Key
- MCP headers value

### 8.12 MVP 实施顺序

建议分 4 步做。

#### 第一步：总工作空间只读入口

目标：

- 首页菜单进入总工作空间。
- 展示 13 个区域。
- 每个区域显示数量、大小、最近更新时间、是否有敏感信息。

实现：

- 新增 `WorkspaceAreaProvider`。
- 先实现 `areaSummary()`。
- UI 只做总览。

#### 第二步：区域列表和详情

目标：

- 点进区域可以看条目。
- 能从条目跳回原功能页。

优先实现：

- AI 角色
- 会话记录
- 工作产物
- 技能库
- MCP 连接
- 模型与网关
- 任务队列

#### 第三步：导出

目标：

- 支持按区域导出。
- 支持全量导出。
- 支持 manifest。
- 支持敏感信息脱敏。

先导出：

- 角色
- 用户记忆
- 技能
- MCP 连接
- 模型配置脱敏版
- 工作产物
- 任务 replay/recipe

#### 第四步：导入和冲突处理

目标：

- 导入预检。
- 显示冲突。
- 支持跳过/合并/创建副本。
- 导入后重建索引。

优先支持：

- 角色包
- 动态技能
- MCP 连接脱敏包
- 任务 recipe
- 工作产物

### 8.13 与现有 Chat 的最小改动点

为了让 chat 真正和工作空间连起来，建议先补这些最小改动：

1. `SessionMessageEntity` 增加可选 `workspaceId` 或建立 session-workspace 引用索引。
2. `WorkspaceRuntimeCoordinator` 创建 workspace 后写入全局 `WorkspaceReference`。
3. `WorkspaceRuntimeRecorder` 记录 artifact 时，同时写入 artifact-workspace 引用。
4. `SkillAttachment.FileData` / `ImageData` 生成时尽量落盘并写入媒体/产物索引。
5. `TaskRouter` 恢复任务时优先读取 workspace execution context，而不是只看最近聊天。
6. 聊天附件卡片增加“查看所在工作空间”入口。
7. 工作空间详情页增加“回到这段聊天”入口。

这样用户会感受到：

- 聊天不是一次性流水。
- 每次真正做事都会沉淀到工作空间。
- 继续任务时 AI 能接上现场。
- 导出时能带走角色、记忆、产物、技能和任务线索。

### 8.14 架构边界

不要在第一版做：

- 把所有数据迁到一个新数据库。
- 把所有文件强行复制到 `mobile_workspace/`。
- 做复杂同步协议。
- 做跨设备实时同步。
- 做玩法空间完整设计。
- 让 AI 自动导出所有隐私数据。

第一版应该做到：

- 看得见。
- 找得到。
- 链得上。
- 导得出。
- 敏感信息不乱跑。

## 9. 下一步

下一步可以继续做：

1. 给每个一级区域补二级内容。
2. 定义每个区域的数据来源和路径。
3. 定义导出 manifest。
4. 定义导入预检模型。
5. 再基于这份信息架构重做总工作空间 UI。
