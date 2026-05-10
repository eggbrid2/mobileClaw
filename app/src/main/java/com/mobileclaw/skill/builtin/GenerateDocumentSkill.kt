package com.mobileclaw.skill.builtin

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.skill.executor.RuntimePipInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Generates PPTX, DOCX, XLSX, PDF, CSV or Markdown files via Python libraries.
 * Pure-Python pip packages (python-pptx, python-docx, openpyxl, xlsxwriter) are
 * auto-installed on first use. Output is saved to filesDir/documents/ and returned
 * as a FileData attachment.
 *
 * Content format per type:
 *   pptx → JSON: {title?, subtitle?, theme?, image_queries?, image_urls?, slides:[...]}
 *   docx → JSON: {title?, subtitle?, theme?, image_queries?, image_urls?, sections:[...]}
 *   xlsx → JSON: {sheets:[{name, headers:[], rows:[[]]}]}  OR  {headers:[], rows:[[]]}
 *   pdf  → JSON: {title?, subtitle?, theme?, image_queries?, image_urls?, sections:[...]}
 *   csv  → plain CSV text
 *   md   → plain Markdown text
 */
class GenerateDocumentSkill(private val context: Context) : Skill {

    override val meta = SkillMeta(
        id = "generate_document",
        name = "Generate Document",
        nameZh = "生成文档",
        description = "Creates business-grade PPTX, DOCX, XLSX, PDF, CSV or Markdown files and returns them as downloadable attachments. This is the required tool for Office documents; do not use create_file, run_python, pandas, python-pptx, openpyxl, xlsxwriter, or ad-hoc Python for PPTX/DOCX/XLSX/PDF generation. " +
            "For PPTX/DOCX/PDF use JSON with title, subtitle, theme, slides or sections. Supports web image search/download via image_queries, direct image_urls, generated executive backgrounds, charts, tables, bullets and commercial layouts. " +
            "Slide/section chart format: {\"type\":\"bar|line|pie|donut\",\"title\":\"...\",\"labels\":[...],\"values\":[...]}. " +
            "Theme format: {\"name\":\"executive\",\"palette\":{\"navy\":\"#13213C\",\"blue\":\"#2563EB\",\"teal\":\"#14B8A6\",\"gold\":\"#F59E0B\"}}. " +
            "pptx format: {\"title\":\"Deck\",\"subtitle\":\"...\",\"image_queries\":[\"modern data center\"],\"slides\":[{\"title\":\"...\",\"bullets\":[...],\"chart\":{...},\"image_query\":\"...\"}]}. " +
            "docx/pdf format: {\"title\":\"Report\",\"sections\":[{\"heading\":\"...\",\"paragraphs\":[...],\"bullets\":[...],\"chart\":{...},\"table\":[[...]]}]}. " +
            "xlsx format: {\"sheets\":[{\"name\":\"Sheet1\",\"headers\":[\"A\",\"B\"],\"rows\":[[1,2]],\"charts\":[{\"type\":\"bar\",\"title\":\"...\"}]}]}. Type aliases are accepted: ppt→pptx, word→docx, excel→xlsx. csv/md: plain text content.",
        descriptionZh = "生成具有商业展示价值的 PPTX/DOCX/XLSX/PDF/CSV/Markdown 文件并作为附件返回。生成办公文档必须使用此 skill，不要用 create_file、run_python、pandas、python-pptx、openpyxl、xlsxwriter 或临时手写 Python 生成 PPTX/DOCX/XLSX/PDF。PPTX/DOCX/PDF 支持联网检索并下载图片、插入图片直链、生成商务背景、图表、表格、项目符号和商业版式。content 使用 JSON，可包含 title、subtitle、theme、image_queries、image_urls、slides 或 sections。chart 格式：{\"type\":\"bar|line|pie|donut\",\"title\":\"...\",\"labels\":[...],\"values\":[...]}。type 支持别名：ppt→pptx、word→docx、excel→xlsx。",
        parameters = listOf(
            SkillParam("type", "string", "Document type: pptx | docx | xlsx | pdf | csv | md. Aliases accepted: ppt | word | excel"),
            SkillParam("filename", "string", "Output filename. Extension is optional, e.g. 'report' or 'report.pptx'"),
            SkillParam("content", "string", "Document content. For pptx/docx/pdf/xlsx pass JSON. For csv/md pass plain text. JSON may include theme, image_queries, image_urls, slides/sections, charts and tables."),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("创作", "文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val type = normalizeType(params["type"] as? String)
            ?: return@withContext SkillResult(false, "type parameter required (pptx/docx/xlsx/pdf/csv/md)")
        val filename = normalizeFilename((params["filename"] as? String)?.trim()?.ifBlank { "document" } ?: "document", type)
        val content = params["content"] as? String ?: ""

        val outputDir = File(context.filesDir, "documents").also { it.mkdirs() }
        val outputFile = File(outputDir, "$filename.$type")

        // Write content to a temp file to avoid any escaping issues in Python scripts
        val contentFile = File(context.cacheDir, "doc_content_${System.currentTimeMillis()}.tmp")
        contentFile.writeText(content)

        try {
            when (type) {
                "csv", "md", "txt" -> {
                    outputFile.writeText(content)
                    val mime = when (type) {
                        "csv" -> "text/csv"
                        "md"  -> "text/markdown"
                        else  -> "text/plain"
                    }
                    SkillResult(true, "${type.uppercase()} 文件已生成：${outputFile.name}",
                        data = SkillAttachment.FileData(outputFile.absolutePath, outputFile.name, mime, outputFile.length()))
                }
                "pptx", "docx", "xlsx", "pdf" -> {
                    if (!Python.isStarted()) Python.start(AndroidPlatform(context))
                    val py = Python.getInstance()
                    val pipDir = RuntimePipInstaller.pipPackagesDir(context).absolutePath
                    RuntimePipInstaller.injectSysPath(py, pipDir)
                    runPythonDoc(py, pipDir, type, contentFile.absolutePath, outputFile)
                }
                else -> SkillResult(false, "Unsupported type: $type. Use: pptx, docx, xlsx, pdf, csv, md")
            }
        } finally {
            contentFile.delete()
        }
    }

    private fun normalizeType(raw: String?): String? = when (raw?.lowercase()?.trim()?.removePrefix(".")?.replace("powerpoint", "ppt")) {
        "ppt", "pptx" -> "pptx"
        "doc", "docx", "word" -> "docx"
        "xls", "xlsx", "excel" -> "xlsx"
        "pdf" -> "pdf"
        "csv" -> "csv"
        "md", "markdown" -> "md"
        "txt", "text" -> "txt"
        else -> null
    }

    private fun normalizeFilename(raw: String, type: String): String {
        val clean = raw.trim().ifBlank { "document" }
        val knownExtensions = listOf(".pptx", ".ppt", ".docx", ".doc", ".xlsx", ".xls", ".pdf", ".csv", ".md", ".markdown", ".txt")
        return knownExtensions.firstOrNull { clean.endsWith(it, ignoreCase = true) }
            ?.let { clean.dropLast(it.length).ifBlank { "document" } }
            ?: clean.removeSuffix(".$type").ifBlank { "document" }
    }

    private fun runPythonDoc(py: Python, pipDir: String, type: String, contentPath: String, outputFile: File): SkillResult {
        val packages = when (type) {
            "pptx" -> listOf("python-pptx", "xlsxwriter")
            "docx" -> listOf("python-docx")
            "xlsx" -> listOf("openpyxl", "xlsxwriter")
            "pdf"  -> emptyList()
            else   -> return SkillResult(false, "Unknown type: $type")
        }
        val installLines = packages.joinToString("\n") { "pip_install('$it')" }
        val script = RuntimePipInstaller.buildPreamble(pipDir) + "\n" + installLines
        return runCatching {
            val globals = py.getBuiltins().callAttr("dict")
            py.getBuiltins().callAttr("exec", script, globals)
            val assetDir = File(context.cacheDir, "office_assets_${System.currentTimeMillis()}").also { it.mkdirs() }
            val module = py.getModule("office_document_builder")
            val buildResult = module.callAttr(
                "build_document",
                type,
                contentPath,
                outputFile.absolutePath,
                assetDir.absolutePath,
            ).toString()
            val mime = when (type) {
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "pdf"  -> "application/pdf"
                else   -> "application/octet-stream"
            }
            val extra = if (buildResult.contains("\"asset_count\": 0")) "" else "（已处理图片/图表资源）"
            SkillResult(true, "${type.uppercase()} 已生成：${outputFile.name}$extra",
                data = SkillAttachment.FileData(outputFile.absolutePath, outputFile.name, mime, outputFile.length()))
        }.getOrElse { e ->
            SkillResult(false, "生成 $type 失败：${e.message?.take(600)}")
        }
    }

}
