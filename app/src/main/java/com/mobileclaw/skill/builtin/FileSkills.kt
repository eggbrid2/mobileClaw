package com.mobileclaw.skill.builtin

import android.content.Context
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CreateFileSkill(private val context: Context) : Skill {

    override val meta = SkillMeta(
        id = "create_file",
        name = "Create File",
        description = "Creates a text file and saves it to the device. " +
            "The file appears as a card in the chat that the user can open or share. " +
            "Supports any text format: .txt, .csv, .json, .md, .py, etc.",
        parameters = listOf(
            SkillParam("filename", "string", "Filename with extension, e.g. 'report.txt', 'data.csv', 'script.py'"),
            SkillParam("content", "string", "Text content to write to the file"),
            SkillParam("mime_type", "string", "MIME type, e.g. 'text/plain', 'text/csv', 'application/json'. Default: 'text/plain'", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "创建文件",
        descriptionZh = "在本地存储中创建或覆盖文件。",
        tags = listOf("文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val filename = params["filename"] as? String
            ?: return@withContext SkillResult(false, "filename is required")
        val content = params["content"] as? String
            ?: return@withContext SkillResult(false, "content is required")
        val mimeType = params["mime_type"] as? String ?: inferMimeType(filename)

        runCatching {
            val dir = (context.getExternalFilesDir("created_files")
                ?: context.filesDir.resolve("created_files"))
                .also { it.mkdirs() }
            val file = File(dir, filename)
            file.writeText(content, Charsets.UTF_8)
            SkillResult(
                success = true,
                output = "File created: $filename (${file.length()} bytes)",
                data = SkillAttachment.FileData(file.absolutePath, filename, mimeType, file.length()),
            )
        }.getOrElse { e -> SkillResult(false, "Failed to create file: ${e.message}") }
    }

    private fun inferMimeType(filename: String): String = when (filename.substringAfterLast('.').lowercase()) {
        "csv"  -> "text/csv"
        "json" -> "application/json"
        "html" -> "text/html"
        "md"   -> "text/markdown"
        "xml"  -> "text/xml"
        "py"   -> "text/x-python"
        "js"   -> "text/javascript"
        "kt"   -> "text/plain"
        else   -> "text/plain"
    }
}

class ReadFileSkill(private val context: Context) : Skill {
    override val meta = SkillMeta(
        id = "read_file",
        name = "Read File",
        nameZh = "读取文件",
        description = "Reads a text file from the app's created_files directory. Returns up to 50,000 characters.",
        descriptionZh = "读取 created_files 目录中的文本文件，最多返回 50,000 字符。",
        parameters = listOf(
            SkillParam("filename", "string", "Filename to read, e.g. 'report.txt' or 'data.json'"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val filename = params["filename"] as? String ?: return@withContext SkillResult(false, "filename is required")
        val safeName = File(filename).name  // strip any path component
        val dir = context.getExternalFilesDir("created_files") ?: context.filesDir.resolve("created_files")
        val file = File(dir, safeName)
        when {
            !file.exists() -> {
                val listed = dir.listFiles()?.map { it.name }?.joinToString(", ") ?: "(none)"
                SkillResult(false, "File not found: $safeName. Available files: $listed")
            }
            !file.isFile -> SkillResult(false, "$safeName is not a file")
            else -> runCatching {
                val content = file.readText().take(50_000)
                val suffix = if (file.length() > 50_000) "\n[truncated — file is ${file.length()} bytes]" else ""
                SkillResult(true, content + suffix)
            }.getOrElse { SkillResult(false, "read error: ${it.message}") }
        }
    }
}

class ListFilesSkill(private val context: Context) : Skill {
    override val meta = SkillMeta(
        id = "list_files",
        name = "List Files",
        nameZh = "列出文件",
        description = "Lists files in a directory — shown as tappable file cards in chat. " +
            "directory: 'created_files' (AI-created, default), 'downloads', 'documents', 'pictures', 'music', or an absolute path. " +
            "Returns at most 50 files, no recursion. Do NOT call this in a loop — one call gives you the complete flat listing.",
        descriptionZh = "列出目录中的文件，结果以文件卡片形式展示。directory 可为 'created_files'（默认）、'downloads'、'documents'、'pictures'、'music' 或绝对路径。最多 50 个文件，不递归。",
        parameters = listOf(
            SkillParam("directory", "string", "Which folder to list: 'created_files' (default), 'downloads', 'documents', 'pictures', 'music', or absolute path.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val dirKey = (params["directory"] as? String)?.trim() ?: "created_files"
        val dir: java.io.File = resolveDirectory(dirKey)

        if (!dir.exists()) return@withContext SkillResult(false, "Directory not found: $dirKey")
        if (!dir.isDirectory) return@withContext SkillResult(false, "$dirKey is not a directory")

        val MAX_FILES = 50
        val allFiles = dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        val files = allFiles.take(MAX_FILES)
        if (files.isEmpty()) return@withContext SkillResult(true, "No files found in $dirKey.")

        val entries = files.map { f ->
            SkillAttachment.FileList.FileEntry(
                path = f.absolutePath,
                name = f.name,
                mimeType = inferMimeType(f.name),
                sizeBytes = f.length(),
            )
        }
        val truncated = if (allFiles.size > MAX_FILES) " (showing first $MAX_FILES of ${allFiles.size})" else ""
        SkillResult(
            success = true,
            output = "${files.size} file(s) in $dirKey$truncated",
            data = SkillAttachment.FileList(entries, dirKey),
        )
    }

    private fun resolveDirectory(key: String): java.io.File = when (key) {
        "created_files" -> context.getExternalFilesDir("created_files")
            ?: context.filesDir.resolve("created_files")
        "downloads" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        "documents" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        "pictures" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        "music" -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        else -> java.io.File(key)
    }

    private fun inferMimeType(filename: String): String = when (filename.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png"  -> "image/png"
        "gif"  -> "image/gif"
        "webp" -> "image/webp"
        "mp4"  -> "video/mp4"
        "mp3"  -> "audio/mpeg"
        "pdf"  -> "application/pdf"
        "csv"  -> "text/csv"
        "json" -> "application/json"
        "html" -> "text/html"
        "md"   -> "text/markdown"
        "xml"  -> "text/xml"
        "py"   -> "text/x-python"
        "js"   -> "text/javascript"
        "zip"  -> "application/zip"
        else   -> "text/plain"
    }
}

class CreateHtmlSkill(private val context: Context) : Skill {

    override val meta = SkillMeta(
        id = "create_html",
        name = "Create One-off HTML Preview",
        description = "Creates a temporary HTML preview/report and shows it in chat. " +
            "Use only for one-off rich reports or previews. For persistent pages use ui_builder; for real mini-app programs use app_manager. " +
            "Never return raw HTML in chat when this tool can display it.",
        parameters = listOf(
            SkillParam("title", "string", "Page title shown in the viewer header"),
            SkillParam("html_content", "string", "Complete HTML document including <!DOCTYPE html>, <html>, <head>, and <body>"),
            SkillParam("filename", "string", "Optional filename (e.g., 'report.html'). Default: 'page.html'", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "创建一次性 HTML 预览",
        descriptionZh = "生成一次性 HTML 报告/预览并在聊天中打开。持久页面优先使用 ui_builder，真正的 MiniAPP 程序使用 app_manager，不要直接在聊天里返回 HTML。",
        tags = listOf("文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val title = params["title"] as? String ?: "AI Generated Page"
        val htmlContent = params["html_content"] as? String
            ?: return@withContext SkillResult(false, "html_content is required")
        val filename = params["filename"] as? String ?: "page.html"

        runCatching {
            val dir = (context.getExternalFilesDir("html_pages")
                ?: context.filesDir.resolve("html_pages"))
                .also { it.mkdirs() }
            val file = File(dir, filename)
            file.writeText(htmlContent, Charsets.UTF_8)
            SkillResult(
                success = true,
                output = "HTML page created: \"$title\" — showing in chat",
                data = SkillAttachment.HtmlData(file.absolutePath, title, htmlContent),
            )
        }.getOrElse { e -> SkillResult(false, "Failed to create HTML page: ${e.message}") }
    }
}
