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
 * Pure-Python pip packages (python-pptx, python-docx, openpyxl, reportlab) are
 * auto-installed on first use. Output is saved to filesDir/documents/ and returned
 * as a FileData attachment.
 *
 * Content format per type:
 *   pptx → JSON: [{title, subtitle?}, {title, bullets:[], body?}, ...]
 *   docx → JSON: {title?, sections:[{heading?, paragraphs:[], bullets:[]}]}
 *   xlsx → JSON: {sheets:[{name, headers:[], rows:[[]]}]}  OR  {headers:[], rows:[[]]}
 *   pdf  → JSON: {title?, sections:[{heading?, paragraphs:[], table?:[[]] }]}
 *   csv  → plain CSV text
 *   md   → plain Markdown text
 */
class GenerateDocumentSkill(private val context: Context) : Skill {

    override val meta = SkillMeta(
        id = "generate_document",
        name = "Generate Document",
        nameZh = "生成文档",
        description = "Creates PPTX, DOCX, XLSX, PDF, CSV or Markdown files and returns them as downloadable attachments. " +
            "Content is passed as JSON (for structured types) or plain text (csv/md). " +
            "Required libraries are auto-installed on first use via pip. " +
            "pptx format: [{\"title\":\"Slide 1\",\"bullets\":[\"point 1\",\"point 2\"]}, ...] " +
            "docx format: {\"title\":\"Doc Title\",\"sections\":[{\"heading\":\"H1\",\"paragraphs\":[\"text...\"]}]} " +
            "xlsx format: {\"sheets\":[{\"name\":\"Sheet1\",\"headers\":[\"A\",\"B\"],\"rows\":[[1,2],[3,4]]}]} " +
            "pdf format: same as docx " +
            "csv/md: plain text content",
        descriptionZh = "生成 PPTX/DOCX/XLSX/PDF/CSV/Markdown 文件并作为附件返回。" +
            "Python 库首次使用时自动安装。",
        parameters = listOf(
            SkillParam("type", "string", "Document type: pptx | docx | xlsx | pdf | csv | md"),
            SkillParam("filename", "string", "Output filename without extension, e.g. 'report'"),
            SkillParam("content", "string", "Document content: JSON structure for pptx/docx/xlsx/pdf, plain text for csv/md"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("创作", "文件"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val type = (params["type"] as? String)?.lowercase()?.trim()
            ?: return@withContext SkillResult(false, "type parameter required (pptx/docx/xlsx/pdf/csv/md)")
        val filename = (params["filename"] as? String)?.trim()?.ifBlank { "document" } ?: "document"
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

    private fun runPythonDoc(py: Python, pipDir: String, type: String, contentPath: String, outputFile: File): SkillResult {
        val script = RuntimePipInstaller.buildPreamble(pipDir) + "\n" + when (type) {
            "pptx" -> pptxScript(contentPath, outputFile.absolutePath)
            "docx" -> docxScript(contentPath, outputFile.absolutePath)
            "xlsx" -> xlsxScript(contentPath, outputFile.absolutePath)
            "pdf"  -> pdfScript(contentPath, outputFile.absolutePath)
            else   -> return SkillResult(false, "Unknown type: $type")
        }
        return runCatching {
            val globals = py.getBuiltins().callAttr("dict")
            py.getBuiltins().callAttr("exec", script, globals)
            val mime = when (type) {
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "pdf"  -> "application/pdf"
                else   -> "application/octet-stream"
            }
            SkillResult(true, "${type.uppercase()} 已生成：${outputFile.name}",
                data = SkillAttachment.FileData(outputFile.absolutePath, outputFile.name, mime, outputFile.length()))
        }.getOrElse { e ->
            SkillResult(false, "生成 $type 失败：${e.message?.take(600)}")
        }
    }

    // ── Python script bodies ──────────────────────────────────────────────────

    private fun pptxScript(contentPath: String, outPath: String) = """
pip_install('python-pptx')
import json
from pptx import Presentation
from pptx.util import Inches, Pt

with open('$contentPath', 'r', encoding='utf-8') as f:
    slides_data = json.load(f)

prs = Presentation()
prs.slide_width  = Inches(13.33)
prs.slide_height = Inches(7.5)

for i, sd in enumerate(slides_data if isinstance(slides_data, list) else [slides_data]):
    layout = prs.slide_layouts[0] if i == 0 else prs.slide_layouts[1]
    slide = prs.slides.add_slide(layout)
    title_shape = slide.shapes.title
    if title_shape:
        title_shape.text = str(sd.get('title', ''))
    placeholders = list(slide.placeholders)
    if i == 0:
        if len(placeholders) > 1:
            placeholders[1].text = str(sd.get('subtitle', sd.get('body', '')))
    else:
        content_ph = next((p for p in placeholders if p.placeholder_format.idx == 1), None)
        if content_ph:
            tf = content_ph.text_frame
            tf.clear()
            items = sd.get('bullets', [])
            if not items and sd.get('body'):
                items = [sd['body']]
            for j, item in enumerate(items):
                p = tf.paragraphs[0] if j == 0 else tf.add_paragraph()
                p.text = str(item)
                p.level = int(sd.get('level', 0))

prs.save('$outPath')
""".trimIndent()

    private fun docxScript(contentPath: String, outPath: String) = """
pip_install('python-docx')
import json
from docx import Document
from docx.shared import Pt

with open('$contentPath', 'r', encoding='utf-8') as f:
    data = json.load(f)

doc = Document()
title = data.get('title', '')
if title:
    doc.add_heading(title, level=0)

for section in data.get('sections', []):
    heading = section.get('heading', '')
    if heading:
        doc.add_heading(heading, level=int(section.get('level', 1)))
    for para in section.get('paragraphs', []):
        doc.add_paragraph(str(para))
    for item in section.get('bullets', []):
        doc.add_paragraph(str(item), style='List Bullet')
    for item in section.get('numbered', []):
        doc.add_paragraph(str(item), style='List Number')

doc.save('$outPath')
""".trimIndent()

    private fun xlsxScript(contentPath: String, outPath: String) = """
pip_install('openpyxl')
import json
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

with open('$contentPath', 'r', encoding='utf-8') as f:
    data = json.load(f)

wb = Workbook()
raw = data if isinstance(data, dict) else {}
sheets = raw.get('sheets', None)
if sheets is None:
    sheets = [{'name': 'Sheet1', 'headers': raw.get('headers', []), 'rows': raw.get('rows', [])}]

for i, sheet_data in enumerate(sheets):
    ws = wb.active if i == 0 else wb.create_sheet()
    ws.title = str(sheet_data.get('name', f'Sheet{i+1}'))[:31]
    headers = sheet_data.get('headers', [])
    if headers:
        ws.append([str(h) for h in headers])
        for cell in ws[1]:
            cell.font = Font(bold=True)
            cell.fill = PatternFill('solid', fgColor='4472C4')
            cell.font = Font(bold=True, color='FFFFFF')
    for row in sheet_data.get('rows', []):
        ws.append(list(row) if isinstance(row, (list, tuple)) else list(row.values()))
    for col in ws.columns:
        max_len = max((len(str(cell.value)) for cell in col if cell.value), default=8)
        ws.column_dimensions[get_column_letter(col[0].column)].width = min(max_len + 2, 40)

wb.save('$outPath')
""".trimIndent()

    private fun pdfScript(contentPath: String, outPath: String) = """
pip_install('reportlab')
import json
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, ListFlowable, ListItem

with open('$contentPath', 'r', encoding='utf-8') as f:
    data = json.load(f)

doc = SimpleDocTemplate('$outPath', pagesize=A4, topMargin=2*cm, bottomMargin=2*cm)
styles = getSampleStyleSheet()
story = []

title = data.get('title', '')
if title:
    story.append(Paragraph(str(title), styles['Title']))
    story.append(Spacer(1, 12))

for section in data.get('sections', []):
    heading = section.get('heading', '')
    if heading:
        story.append(Paragraph(str(heading), styles['Heading1']))
    for para in section.get('paragraphs', []):
        story.append(Paragraph(str(para), styles['Normal']))
        story.append(Spacer(1, 6))
    bullets = section.get('bullets', [])
    if bullets:
        items = [ListItem(Paragraph(str(b), styles['Normal'])) for b in bullets]
        story.append(ListFlowable(items, bulletType='bullet'))
        story.append(Spacer(1, 8))
    table_data = section.get('table', [])
    if table_data:
        t = Table([[str(cell) for cell in row] for row in table_data])
        t.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#4472C4')),
            ('TEXTCOLOR',  (0,0), (-1,0), colors.white),
            ('FONTNAME',   (0,0), (-1,0), 'Helvetica-Bold'),
            ('GRID',       (0,0), (-1,-1), 0.5, colors.grey),
            ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#F2F2F2')]),
        ]))
        story.append(t)
        story.append(Spacer(1, 12))

doc.build(story)
""".trimIndent()
}
