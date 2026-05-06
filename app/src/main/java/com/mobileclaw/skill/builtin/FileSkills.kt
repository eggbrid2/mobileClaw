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
        description = "Lists files previously created by the AI in the created_files directory.",
        descriptionZh = "列出 AI 通过 create_file 创建的文件。",
        parameters = emptyList(),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir("created_files") ?: context.filesDir.resolve("created_files")
        val files = dir.listFiles()?.sortedBy { it.lastModified() }?.reversed() ?: emptyList()
        if (files.isEmpty()) return@withContext SkillResult(true, "No files found in created_files directory.")
        val lines = files.map { f ->
            val size = when {
                f.length() < 1024 -> "${f.length()} B"
                f.length() < 1_048_576 -> "${"%.1f".format(f.length() / 1024.0)} KB"
                else -> "${"%.1f".format(f.length() / 1_048_576.0)} MB"
            }
            "${f.name}  ($size)"
        }
        SkillResult(true, "${files.size} file(s) in created_files:\n" + lines.joinToString("\n"))
    }
}

class CreateHtmlSkill(private val context: Context) : Skill {

    override val meta = SkillMeta(
        id = "create_html",
        name = "Create HTML Page",
        description = "Creates an HTML file and shows it as an in-app web page. " +
            "Use for rich reports, charts (Chart.js, D3), interactive content, or any custom UI. " +
            "The page opens directly in the app without leaving the chat.",
        parameters = listOf(
            SkillParam("title", "string", "Page title shown in the viewer header"),
            SkillParam("html_content", "string", "Complete HTML document including <!DOCTYPE html>, <html>, <head>, and <body>"),
            SkillParam("filename", "string", "Optional filename (e.g., 'report.html'). Default: 'page.html'", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "创建 HTML 页面",
        descriptionZh = "生成 HTML 文件并在内置浏览器中打开。",
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
