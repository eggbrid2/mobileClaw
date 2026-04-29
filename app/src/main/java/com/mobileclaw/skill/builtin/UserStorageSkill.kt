package com.mobileclaw.skill.builtin

import com.mobileclaw.config.UserStorageManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserStorageSkill(private val storage: UserStorageManager) : Skill {

    override val meta = SkillMeta(
        id = "user_storage",
        name = "User Storage",
        description = "Manage files and folders in the user's external storage (Downloads, Documents, Pictures, etc.). " +
            "Actions: list_dirs (well-known directories), list (list files in a path), read (read file content), " +
            "write (write/create a file), delete (delete file or folder), copy, move, search, create_dir. " +
            "Requires the user to have granted 'All Files Access' permission.",
        parameters = listOf(
            SkillParam("action", "string", "One of: list_dirs | list | read | write | delete | copy | move | search | create_dir"),
            SkillParam("path", "string", "Absolute file or directory path. Required for: list, read, write, delete, create_dir", required = false),
            SkillParam("content", "string", "Text content to write. Required for: write", required = false),
            SkillParam("destination", "string", "Destination path. Required for: copy, move", required = false),
            SkillParam("query", "string", "Filename search query. Required for: search", required = false),
            SkillParam("search_root", "string", "Root directory for search. Optional for: search (defaults to Downloads)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!storage.hasAllFilesAccess()) {
            return SkillResult(
                false,
                "❌ 未获得文件访问权限。请让用户前往「设置 → 特殊权限 → 所有文件访问」授权本应用，" +
                    "或在应用设置页面点击「授权文件访问」按钮。",
            )
        }

        val action = params["action"] as? String ?: return SkillResult(false, "action 参数必填")

        return when (action) {
            "list_dirs" -> {
                val dirs = storage.wellKnownDirs()
                val sb = StringBuilder("📁 可用的存储目录：\n\n")
                dirs.forEach { sb.append("${it.emoji} ${it.name}\n   路径: ${it.path}\n\n") }
                SkillResult(true, sb.toString().trimEnd())
            }

            "list" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                storage.listDir(path).fold(
                    onSuccess = { entries ->
                        if (entries.isEmpty()) return SkillResult(true, "目录为空: $path")
                        val sb = StringBuilder("📂 $path (${entries.size} 项)\n\n")
                        entries.forEach { e ->
                            val icon = if (e.isDirectory) "📁" else "📄"
                            val size = if (e.isDirectory) "目录" else formatSize(e.sizeBytes)
                            val date = sdf.format(Date(e.lastModified))
                            sb.append("$icon ${e.name}\n   $size · $date\n   ${e.path}\n\n")
                        }
                        SkillResult(true, sb.toString().trimEnd())
                    },
                    onFailure = { SkillResult(false, it.message ?: "列目录失败") },
                )
            }

            "read" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                storage.readFile(path).fold(
                    onSuccess = { content -> SkillResult(true, content) },
                    onFailure = { SkillResult(false, it.message ?: "读取文件失败") },
                )
            }

            "write" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                val content = params["content"] as? String ?: return SkillResult(false, "content 参数必填")
                storage.writeFile(path, content).fold(
                    onSuccess = { SkillResult(true, "✅ 文件已写入: $path") },
                    onFailure = { SkillResult(false, it.message ?: "写入文件失败") },
                )
            }

            "delete" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                storage.deleteFile(path).fold(
                    onSuccess = { deleted -> SkillResult(deleted, if (deleted) "✅ 已删除: $path" else "文件不存在: $path") },
                    onFailure = { SkillResult(false, it.message ?: "删除失败") },
                )
            }

            "copy" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                val dest = params["destination"] as? String ?: return SkillResult(false, "destination 参数必填")
                storage.copyFile(path, dest).fold(
                    onSuccess = { SkillResult(true, "✅ 已复制: $path → $dest") },
                    onFailure = { SkillResult(false, it.message ?: "复制失败") },
                )
            }

            "move" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                val dest = params["destination"] as? String ?: return SkillResult(false, "destination 参数必填")
                storage.moveFile(path, dest).fold(
                    onSuccess = { SkillResult(true, "✅ 已移动: $path → $dest") },
                    onFailure = { SkillResult(false, it.message ?: "移动失败") },
                )
            }

            "search" -> {
                val query = params["query"] as? String ?: return SkillResult(false, "query 参数必填")
                val root = params["search_root"] as? String
                    ?: storage.wellKnownDirs().firstOrNull { it.name.contains("Download") }?.path
                    ?: android.os.Environment.getExternalStorageDirectory().absolutePath
                storage.searchFiles(root, query).fold(
                    onSuccess = { entries ->
                        if (entries.isEmpty()) return SkillResult(true, "未找到匹配 \"$query\" 的文件（搜索范围: $root）")
                        val sb = StringBuilder("🔍 \"$query\" 的搜索结果（${entries.size} 项，来自 $root）\n\n")
                        entries.forEach { e ->
                            val icon = if (e.isDirectory) "📁" else "📄"
                            sb.append("$icon ${e.name}\n   ${e.path}\n\n")
                        }
                        SkillResult(true, sb.toString().trimEnd())
                    },
                    onFailure = { SkillResult(false, it.message ?: "搜索失败") },
                )
            }

            "create_dir" -> {
                val path = params["path"] as? String ?: return SkillResult(false, "path 参数必填")
                storage.createDir(path).fold(
                    onSuccess = { SkillResult(true, "✅ 目录已创建: $path") },
                    onFailure = { SkillResult(false, it.message ?: "创建目录失败") },
                )
            }

            else -> SkillResult(false, "未知 action: $action。可用值: list_dirs | list | read | write | delete | copy | move | search | create_dir")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
