# MiniAPP Javet Node Runtime 技术方案

> 目标：把 MobileClaw MiniAPP 从“单 HTML 页面”升级为“可源码编辑、可编译、可导入导出、可运行 Vue/TS 等现代前端框架”的项目级应用，并在 Android 内通过 Javet 运行受控 Node 构建器。

本文档记录当前确定的技术路线、ChatAI 需要新增的方法、AI 创建/修改 MiniAPP 的执行流程、Node/Javet 版本方案、MiniAPP 包格式、导入导出策略和原生桥约束。

## 1. 背景

当前 MiniAPP 的底层模型仍是单文件 HTML：

- `MiniApp` 元数据中保存 `htmlPath`。
- `AppManagerSkill` 的 `create/update` 主要接收完整 `html` 字符串。
- `MiniAppStore` 将内容保存为 `{id}.html` 和 `{id}.json`。
- WebView 使用 `loadDataWithBaseURL(file://...)` 注入 HTML 字符串运行。
- Claw 原生桥通过向 HTML 中插入 `<script>` 完成注入。

这套模型适合一次性小页面，但不适合真正的 APP：

- Vue/TS 需要多文件源码、模块系统、类型检查和编译产物。
- Vite/Rollup/esbuild 需要明确的构建目录和依赖管理。
- 后续 AI 修改应用时，需要按文件搜索、读取、patch、编译、验证，而不是重写整段 HTML。
- 导入导出需要同时保存源码、构建产物、后端、数据、manifest 和权限声明。

因此，MiniAPP v2 应定义为项目级应用：

```text
MiniAPP = metadata + source project + build output + backend + data + runtime permissions
```

## 2. 总体技术路线

### 2.1 选型

Android 内运行 Node 选择 Javet：

```kotlin
implementation("com.caoccao.javet:javet-node-android:5.0.9")
```

当前计划锁定：

- Javet: `5.0.9`
- Node.js: `24.17.0`
- Android ABI: `arm64-v8a`, `x86_64`
- MobileClaw 当前 `minSdk = 30`，满足该路线的基础要求。

注意：Javet 解决的是“在 Android 里运行 Node runtime”，不等于可以直接在手机上随便跑完整 npm 生态。MiniAPP 构建必须做成受控构建器。

### 2.2 构建器原则

不把 MiniAPP 构建设计成用户手动执行：

```text
npm install
npm run build
```

而是由 MobileClaw 内部提供受控构建器：

```text
ChatAI
  -> miniapp_project.*
  -> node_runtime.prepare
  -> node_runtime.build_project
  -> MiniAppBuildService
  -> Javet Node Runtime
  -> bundled build-miniapp.mjs
  -> Vite JS API
  -> dist/
```

第一版不开放任意 npm/shell。构建脚本只允许在 MiniAPP 项目目录内读取源码、写入 `dist/`、输出结构化日志。

### 2.3 分层

```text
ChatAI / AgentRuntime
  调用 miniapp_project 和 node_runtime 工具

AppManagerSkill v2
  对外暴露创建、修改、搜索、构建、验证、导入导出动作

MiniAppProjectStore
  管理 MiniAPP v2 项目目录、源码、dist、manifest、后端和数据

MiniAppBuildService
  调 Javet Node 执行受控构建脚本

NodeRuntimeManager
  管理 Javet 初始化、builder 解压、Node 版本、构建队列和日志

MiniAppRuntime / WebView
  通过 WebViewAssetLoader 加载 dist/index.html，并注入 Claw bridge
```

## 3. ChatAI 需要新增的方法

### 3.1 MiniAPP 项目工具组

新增或升级 `app_manager` 为项目级动作，建议内部命名为 `miniapp_project`，外部可以保持兼容。

| 方法 | 责任 | 主要参数 |
| --- | --- | --- |
| `get_guide` | 返回 MiniAPP v2 规范、Claw SDK、项目模板和流程约束 | `framework?` |
| `create_project` | 创建项目级 MiniAPP，写入源码、元数据、可选后端 | `id?`, `title`, `description`, `framework`, `files`, `backend?`, `capabilities?` |
| `get_project` | 返回项目摘要、manifest、入口、文件树、最近构建状态 | `id` |
| `list_files` | 列出源码/配置/dist 文件 | `id`, `scope=src|dist|all`, `glob?` |
| `search_files` | 在项目中搜索文本或正则 | `id`, `query`, `scope?`, `glob?`, `case_sensitive?` |
| `read_file` | 读取单个项目文件 | `id`, `path` |
| `write_file` | 写入单个项目文件 | `id`, `path`, `content` |
| `patch_file` | 对单个文件做最小补丁修改 | `id`, `path`, `patch` 或 `old/new` |
| `delete_file` | 删除项目文件 | `id`, `path` |
| `build` | 编译项目源码到 `dist/` | `id`, `mode=development|production`, `clean?` |
| `validate` | 静态检查 + 隐藏 WebView 加载 dist 验证 | `id`, `strict?` |
| `inspect_logs` | 查看构建日志、运行日志、WebView 日志 | `id`, `kind=build|runtime|all`, `limit?` |
| `open` | 打开 MiniAPP 运行界面 | `id` |
| `export_package` | 导出 MiniAPP v2 包 | `id`, `file_path?`, `include_data?` |
| `import_package` | 导入 MiniAPP v1/v2 包 | `file_path`, `id?`, `overwrite?`, `build_if_needed?` |
| `migrate_v1_to_v2` | 将旧 HTML MiniAPP 包装为 v2 项目 | `id`, `framework=vanilla|vue3-vite-ts` |

第一版可先将这些动作放进现有 `AppManagerSkill`，但技能说明必须从“HTML MiniAPP”改成“项目级 MiniAPP”。

### 3.2 Node 运行时工具组

新增 `node_runtime` 或内部服务动作。该工具组不面向普通用户做任意命令执行，只服务 MiniAPP 构建。

| 方法 | 责任 | 主要参数 |
| --- | --- | --- |
| `status` | 返回 Javet 是否可用、Node 版本、builder 版本、ABI、最近错误 | 无 |
| `prepare` | 初始化 Javet，解压或校验 builder，检查构建依赖 | `force?` |
| `build_project` | 调用受控构建器编译指定 MiniAPP | `id`, `project_dir`, `out_dir`, `mode`, `timeout_ms?` |
| `run_script` | 仅允许执行白名单脚本，如 `build-miniapp.mjs`、`diagnose.mjs` | `script`, `args` |
| `diagnose` | 输出 Node/Vite/Rollup/esbuild 能力诊断 | `verbose?` |
| `clear_cache` | 清理 builder 缓存、构建缓存、临时目录 | `target=builder|project|all` |

限制：

- 不提供任意 `npm install`。
- 不提供任意 `node -e`。
- 不允许访问 MiniAPP 项目根之外的路径。
- 构建日志必须结构化返回给 ChatAI，便于自动修复。

### 3.3 文件操作输出格式

所有项目工具返回结构化 JSON，便于 AgentRuntime 判断下一步：

```json
{
  "artifact_type": "miniapp_project",
  "action": "build",
  "id": "todo_app",
  "ok": false,
  "errors": [
    {
      "kind": "typescript",
      "file": "src/App.vue",
      "line": 42,
      "column": 17,
      "message": "Property 'items' does not exist on type..."
    }
  ],
  "warnings": [],
  "summary": "Build failed with 1 TypeScript error."
}
```

## 4. AI 创建 MiniAPP 的执行流程

创建 MiniAPP 时，AI 必须按照项目级流程执行，而不是一次性写完整 HTML。

### 4.1 标准流程

```text
1. miniapp_project.get_guide
2. node_runtime.status
3. node_runtime.prepare
4. miniapp_project.create_project
5. miniapp_project.build
6. 如果 build 失败：
   6.1 解析错误 file:line:column
   6.2 miniapp_project.read_file
   6.3 miniapp_project.patch_file
   6.4 重新 build
7. miniapp_project.validate
8. 如果 validate 失败：
   8.1 inspect_logs
   8.2 定点 patch
   8.3 build + validate
9. miniapp_project.open
```

### 4.2 创建时的文件骨架

默认框架为 `vue3-vite-ts`：

```text
package.json
vite.config.ts
index.html
src/
  main.ts
  App.vue
  mobileclaw.ts
  style.css
```

复杂项目可以扩展：

```text
src/
  components/
  composables/
  stores/
  views/
  types/
```

第一版不要默认生成 Router/Pinia，除非用户需求天然需要多页面或复杂状态。

### 4.3 创建时的默认要求

AI 生成源码时必须满足：

- 使用 `src/mobileclaw.ts` 封装原生桥。
- 不直接访问 `window.Android`。
- 网络请求使用 `Claw.fetch`。
- 本地数据使用 `Claw.files` 或 `Claw.sql`。
- Python 后端使用 `Claw.python`。
- 重要路径写 `Claw.log.info/warn/error/debug`。
- UI 适配移动端 WebView，避免固定超宽布局。
- 不使用外部 CDN，除非用户明确允许。

## 5. AI 修改 MiniAPP 的执行流程

修改 MiniAPP 时参考 Codex 的代码工作流：先理解项目，再最小修改，再编译验证。

### 5.1 标准流程

```text
1. miniapp_project.get_project
2. miniapp_project.search_files
3. miniapp_project.read_file 读取相关文件
4. 分析改动范围
5. miniapp_project.patch_file 做最小变更
6. miniapp_project.build
7. 编译失败则按错误定点修复
8. miniapp_project.validate
9. inspect_logs
10. open
```

### 5.2 文件搜索策略

AI 不允许直接整包重写。修改前必须做文件定位。

基础读取：

- `miniapp.json`
- `package.json`
- `vite.config.ts`
- `src/main.ts`
- `src/App.vue`
- `src/mobileclaw.ts`

按任务类型继续搜索：

| 任务类型 | 优先搜索 |
| --- | --- |
| UI/布局/样式 | `src/App.vue`, `src/components/*`, `src/style.css`, `class=`, `style`, 关键 UI 文案 |
| 页面/路由 | `src/views/*`, `router`, `route`, `tab`, `page` |
| 状态/数据 | `src/stores/*`, `src/composables/*`, `ref(`, `reactive(`, `computed(` |
| 原生能力 | `Claw.`, `mobileclaw.ts`, `fetch`, `sql`, `python`, `files` |
| 网络问题 | `Claw.fetch`, `fetch(`, `XMLHttpRequest`, URL、headers、解析逻辑 |
| 编译问题 | 报错文件、相关 import、类型定义、`package.json` |

### 5.3 patch 规则

- 优先 `patch_file`，不优先 `write_file`。
- 一次只改相关文件。
- 不删除用户已有功能。
- 不因一个类型错误重写整个组件。
- 不把编译产物 `dist/assets/*.js` 当源码编辑。
- 如果必须改依赖，必须说明原因并重新 build。

### 5.4 编译报错处理

| 错误类型 | 处理方式 |
| --- | --- |
| TypeScript 类型错误 | 读取报错文件和相关类型，定点修复类型或数据结构 |
| Vue template 错误 | 读取 `.vue` 文件，修 template/script 绑定关系 |
| Module not found | 检查 import 路径、大小写、文件是否存在；确实缺依赖再改 `package.json` |
| Vite config 错误 | 读取 `vite.config.ts`，确认 base、plugins、root、outDir |
| Rollup/esbuild Android ABI 错误 | 归类为 builder 环境错误，调用 `node_runtime.diagnose`，不要乱改业务代码 |
| 外部 CDN/网络资源错误 | 改成本地依赖或内置资源 |
| Claw bridge 缺失 | 检查 runtime 注入和 `mobileclaw.ts`，不要直接绕到 `window.Android` |

### 5.5 自动修复上限

为避免无限循环：

- 同一 build 错误最多自动修复 3 次。
- 同一 validate 错误最多自动修复 2 次。
- 如果判断为 builder 环境问题，停止修改业务源码，返回诊断。

## 6. Node/Javet 运行方案

### 6.1 Android 侧新增类

建议新增：

```text
app/src/main/java/com/mobileclaw/node/NodeRuntimeManager.kt
app/src/main/java/com/mobileclaw/node/MiniAppBuildService.kt
app/src/main/java/com/mobileclaw/node/NodeBuildResult.kt
app/src/main/java/com/mobileclaw/node/NodeBuilderAssets.kt
```

职责：

- `NodeRuntimeManager`
  - 初始化 Javet。
  - 返回 Node 版本。
  - 管理 runtime 生命周期。
  - 串行化构建任务。
  - 收集 stdout/stderr/structured diagnostics。

- `MiniAppBuildService`
  - 将 MiniAPP 项目目录传给 builder。
  - 执行 `build-miniapp.mjs`。
  - 解析构建输出。
  - 写入 `dist/`。
  - 返回 `NodeBuildResult`。

- `NodeBuilderAssets`
  - 解压内置 builder。
  - 校验 builder 版本和 hash。
  - 确认 ABI 对应依赖可用。

### 6.2 Builder 目录

首次准备后写入：

```text
filesDir/node_builder/
  manifest.json
  package.json
  node_modules/
  scripts/
    build-miniapp.mjs
    diagnose.mjs
  templates/
    vue3-vite-ts/
    vanilla-ts/
```

构建时不要在项目目录安装依赖。项目目录只保存源码和轻量配置，builder 目录提供统一依赖。

### 6.3 Javet 执行模型

推荐第一版使用单构建队列：

```text
build request
  -> Mutex / single worker
  -> create or reuse Node runtime
  -> execute whitelisted script
  -> collect structured result
  -> release project lock
```

原因：

- 构建是重资源任务，没必要并发。
- 避免多个 Node runtime 抢内存。
- 便于定位 builder 缓存和日志。

### 6.4 不依赖 npm CLI

第一版不要求 Javet 内部可直接运行 `npm`：

- Vite 通过 JS API 调用。
- 依赖由 MobileClaw builder 预置。
- 项目的 `package.json` 更多用于声明和导出，不作为手机端实时安装入口。

后续如果需要扩展依赖，可以做“受控依赖市场”：

```text
allowed dependencies
  -> prebuilt builder pack
  -> signed package
  -> import into node_builder
```

## 7. MiniAPP v2 项目与包格式

### 7.1 本地目录

建议将旧的平铺文件逐步迁移到项目目录：

```text
filesDir/apps/
  {id}/
    miniapp.json
    package.json
    vite.config.ts
    index.html
    src/
    dist/
    backend.py
    data/
    logs/
      build.log
      runtime.log
```

兼容旧版：

```text
filesDir/apps/{id}.json
filesDir/apps/{id}.html
filesDir/apps/{id}_data/
```

### 7.2 miniapp.json

示例：

```json
{
  "schemaVersion": 2,
  "runtimeVersion": "miniapp-v2",
  "id": "todo_app",
  "title": "Todo App",
  "description": "A Vue/TS MiniAPP",
  "icon": "apps",
  "framework": "vue3-vite-ts",
  "entry": "dist/index.html",
  "sourceRoot": "src",
  "distRoot": "dist",
  "hasPython": false,
  "capabilities": ["network", "files", "sqlite", "ai"],
  "build": {
    "builder": "mobileclaw-vite-builder",
    "builderVersion": "1.0.0",
    "node": "24.17.0",
    "command": "vite-build",
    "lastBuildAt": 0,
    "lastBuildOk": false
  }
}
```

### 7.3 导出包

MiniAPP v2 导出为：

```text
manifest.json
miniapp.json
package.json
vite.config.ts
index.html
src/
dist/
backend.py
data/
```

规则：

- 默认导出 `src/` 和 `dist/`。
- 默认不导出 `node_modules/`。
- 可选导出 `data/`。
- 不导出构建缓存。
- 不导出运行日志，除非用户选择诊断包。
- v1 MiniAPP 继续导出 `app.html`。

### 7.4 导入包

导入识别顺序：

1. 如果有 `miniapp.json` 且 `schemaVersion=2`，按 v2 导入。
2. 如果有 `app.html`，按 v1 导入。
3. 如果只有 `src/` 和 `package.json`，按源码项目导入，要求 build。

导入策略：

- 校验 zip 路径，禁止 `../`。
- 检查 `dist/index.html` 是否存在。
- 如果有 `dist/`，可直接运行，但仍要 `validate`。
- 如果没有 `dist/`，必须 `node_runtime.prepare + build`。
- ID 冲突支持：改名、覆盖、取消。
- 导入后记录 history：来源文件、原始 id、是否重建 dist。

### 7.5 v1 兼容与迁移

旧版 MiniAPP 必须继续可打开：

- v1 读取 `{id}.html`。
- v1 导出仍包含 `app.html`。
- v1 可通过 `migrate_v1_to_v2` 包装成 v2。

迁移方式：

```text
旧 app.html
  -> filesDir/apps/{id}/src/legacy.html
  -> filesDir/apps/{id}/index.html
  -> framework = vanilla
  -> dist/index.html
```

不建议自动把任意旧 HTML 改写成 Vue 项目，除非用户明确要求。

## 8. WebView 运行和原生桥

### 8.1 WebView 加载方式

v2 不再用 `loadDataWithBaseURL(file://...)` 加载字符串。

建议改为：

```text
https://appassets.androidplatform.net/miniapps/{id}/dist/index.html
```

使用 `WebViewAssetLoader` 映射本地文件。

好处：

- 支持多文件静态资源。
- 避免宽泛 `file://` 权限。
- 更接近真实 Web origin。
- 便于限制原生桥只对可信 origin 生效。

### 8.2 WebView 安全设置

v2 应尽量关闭：

```kotlin
allowFileAccessFromFileURLs = false
allowUniversalAccessFromFileURLs = false
```

根据实际需要保留：

```kotlin
javaScriptEnabled = true
domStorageEnabled = true
```

### 8.3 Bridge 注入

当前 bridge 是把 JS 字符串插入 HTML。v2 应改成稳定 runtime 文件：

```text
/_claw/bridge.js
```

`dist/index.html` 应在业务 bundle 前加载：

```html
<script src="/_claw/bridge.js"></script>
<script type="module" src="/assets/index-xxxxx.js"></script>
```

原因：

- Vue/Vite 的 module script 可能很早执行。
- `onPageFinished` 后补注入可能晚于业务代码。
- 固定 runtime 文件更容易版本化和调试。

### 8.4 TypeScript SDK

每个项目包含：

```text
src/mobileclaw.ts
```

只暴露类型安全 SDK：

```ts
export const Claw = {
  fetch,
  sql,
  python,
  files,
  ai,
  log,
  toast,
  device,
  clipboard,
  close,
  setTitle,
}
```

业务代码禁止直接访问：

```ts
window.Android
```

### 8.5 Capabilities 权限

`miniapp.json` 声明权限：

```json
{
  "capabilities": ["network", "files", "sqlite", "python", "ai"]
}
```

原生桥按权限判断是否允许调用：

| capability | 允许能力 |
| --- | --- |
| `network` | `Claw.fetch` |
| `files` | `Claw.files` |
| `sqlite` | `Claw.sql` |
| `python` | `Claw.python` |
| `shell` | `Claw.shell` |
| `ai` | `Claw.ai.chat` |
| `clipboard` | `Claw.clipboard` |
| `native_intent` | `openUrl`, `shareText`, `launchApp` |

默认不开放 `shell`。

## 9. 验证体系

### 9.1 构建验证

`miniapp_project.build` 至少检查：

- Node/Javet 可用。
- builder 版本匹配。
- `vite.config.ts` 可解析。
- `src/main.ts` 存在。
- TypeScript/Vue 编译通过。
- `dist/index.html` 生成。
- `dist/assets/*` 资源存在。

### 9.2 静态验证

`miniapp_project.validate` 静态检查：

- 禁止 `window.Android`。
- 禁止原生 `fetch(` 和 `XMLHttpRequest`，除非 bridge polyfill 内部。
- 禁止外部 CDN，除非 manifest 允许。
- 检查 `Claw` 调用是否 `await`。
- 检查 `dist/index.html` 是否加载 `/_claw/bridge.js`。

### 9.3 运行验证

隐藏 WebView 加载：

```text
https://appassets.androidplatform.net/miniapps/{id}/dist/index.html
```

探针检查：

- `window.Claw` 是否存在。
- body 是否可见。
- 页面尺寸是否合理。
- DOM 是否挂载。
- 控件或视觉内容是否存在。
- console error / unhandled rejection。
- runtime logs 是否有 error。

## 10. 实施阶段

### 阶段 1：Javet 可行性验证

目标：确认 Android 内 Node 可运行。

任务：

- 添加 `javet-node-android` 依赖。
- 新增 `NodeRuntimeManager`。
- 实现 `node_runtime.status`。
- 实现 `node_runtime.prepare`。
- 跑 hello-world Node script。
- 输出 Node 版本、ABI、运行日志。

完成标准：

- 真机可返回 Node 版本。
- 多次调用不崩溃。
- 出错时能返回结构化错误。

### 阶段 2：受控 Vite Builder

目标：在 Android 内将一个固定 Vue/TS 模板编译成 `dist/`。

任务：

- 准备 `node_builder` 资产。
- 新增 `build-miniapp.mjs`。
- 新增 `MiniAppBuildService`。
- 构建 `vue3-vite-ts` 示例项目。
- 解析 Vite/TS 错误为结构化 JSON。

完成标准：

- `src/App.vue` 可编译成 `dist/index.html`。
- 编译失败能定位文件行号。

### 阶段 3：MiniAPP v2 存储

目标：让 MiniAPP 支持项目目录。

任务：

- 扩展或新增 `MiniAppProjectStore`。
- 支持 `filesDir/apps/{id}/`。
- 保留 v1 `{id}.html` 兼容。
- 支持源码文件读写、搜索、patch。
- 支持 build logs。

完成标准：

- 旧 MiniAPP 不受影响。
- 新 MiniAPP 可保存多文件项目。

### 阶段 4：ChatAI 工具升级

目标：AI 能按项目流程创建和修改 MiniAPP。

任务：

- 升级 `AppManagerSkill`。
- 增加项目级 actions。
- 更新 `get_guide`。
- 更新 AgentRuntime 对 validate/build 错误的自动修复提示。

完成标准：

- AI 创建项目时会 build/validate/open。
- AI 修改项目时会 search/read/patch/build/validate。

### 阶段 5：WebView v2 Runtime

目标：运行 `dist/` 多文件应用。

任务：

- 接入 `WebViewAssetLoader`。
- 加载 v2 `dist/index.html`。
- 提供 `/_claw/bridge.js`。
- 根据 capabilities 限制桥能力。
- 关闭不必要 file 权限。

完成标准：

- Vue/Vite 产物可在 WebView 正常运行。
- Bridge 在业务代码前可用。
- v1 仍可打开。

### 阶段 6：导入导出

目标：完整迁移 MiniAPP 项目。

任务：

- 导出 v2 包：manifest、miniapp、src、dist、backend、data。
- 导入 v2 包。
- 导入无 dist 的源码包时触发 build。
- 支持 v1 包导入。
- 支持冲突策略。

完成标准：

- 导出的 v2 包在另一台设备导入后可直接运行。
- 缺 dist 时可在设备上重建。

## 11. 风险与处理

| 风险 | 处理 |
| --- | --- |
| Javet 包体积增加 | 只保留 `arm64-v8a` 和 `x86_64`；后续按渠道拆分 |
| Node/Vite 内存占用高 | 单构建队列，限制并发，设置超时 |
| npm 生态 Android 兼容问题 | 不跑任意 npm；预置 builder；必要时走依赖白名单 |
| esbuild/Rollup ABI 不匹配 | `node_runtime.diagnose` 检查；builder 包按 ABI 准备 |
| AI 乱改 dist | 工具层禁止 patch `dist/`，只允许 build 生成 |
| 原生桥安全风险 | WebViewAssetLoader origin + capabilities + 禁止外部不可信内容 |
| 旧 MiniAPP 兼容 | v1/v2 双路径，迁移显式触发 |

## 12. 当前代码落点

| 当前文件 | 后续改动 |
| --- | --- |
| `app/build.gradle.kts` | 添加 Javet 依赖，确认 ABI/packaging |
| `MiniAppStore.kt` | 扩展 v2 项目目录、导入导出、v1 兼容 |
| `MiniAppActivity.kt` | v2 使用 WebViewAssetLoader 加载 dist |
| `AppLauncherPage.kt` | 工作台预览同步支持 v2 runtime |
| `MiniAppPreflightValidator.kt` | 从 HTML 字符串验证升级为项目/dist 验证 |
| `AppManagerSkill.kt` | 增加项目级 actions 和 v2 guide |
| `AppJsBridge.kt` | 增加 capabilities 和 origin 校验 |
| `ClawApplication.kt` | 初始化 NodeRuntimeManager / MiniAppBuildService |

## 13. 第一版验收标准

第一版完成后，至少要满足：

1. Android 真机内能通过 Javet 返回 Node 版本。
2. AI 可以创建一个 `vue3-vite-ts` MiniAPP 项目。
3. 项目可在 Android 内 build 成 `dist/`。
4. 编译失败时 AI 能读取报错文件并修复。
5. WebView 能运行 `dist/index.html`。
6. Claw bridge 在 Vue 应用启动前可用。
7. MiniAPP v2 可以导出和导入。
8. 旧版 HTML MiniAPP 不受影响。
