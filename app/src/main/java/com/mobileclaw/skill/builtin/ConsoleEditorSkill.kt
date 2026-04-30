package com.mobileclaw.skill.builtin

import com.mobileclaw.server.ConsoleServer
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

/**
 * Lets the agent fully customise the LAN console web page — the "千人千面" feature.
 *
 * The console lives at filesDir/console_web/index.html and is served by ConsoleServer on
 * the local network. The agent can read the current page, replace it entirely with a
 * personalised dashboard, inject incremental CSS/JS patches, or reset to the default.
 *
 * Personalisation ideas:
 *   - Match the user's favourite colour scheme
 *   - Add shortcut buttons for their most-used commands
 *   - Embed live data widgets (weather, tasks, stocks) fetched by JS
 *   - Show user's name and avatar in the header
 *   - Switch between chat/command/dashboard layouts
 */
class ConsoleEditorSkill(private val server: ConsoleServer) : Skill {

    override val meta = SkillMeta(
        id = "console_editor",
        name = "Console Editor",
        description = "Read or rewrite the LAN web console page (千人千面). " +
            "Actions: " +
            "read — get the current console HTML; " +
            "write — replace the entire page with new HTML (use for full redesigns); " +
            "patch_css — inject a <style> block into the page head (for incremental theme tweaks); " +
            "patch_js — inject a <script> block into the page body (for adding widgets/features); " +
            "reset — restore the factory-default console; " +
            "get_url — return the LAN URL users should visit. " +
            "Always keep the /api/events SSE connection, /api/send POST, /api/sessions, and /api/messages " +
            "endpoints wired correctly so the console stays functional after edits.",
        parameters = listOf(
            SkillParam(
                "action", "string",
                "One of: read | write | patch_css | patch_js | reset | get_url",
            ),
            SkillParam(
                "html", "string",
                "Complete HTML document for action=write. Must be a full <!DOCTYPE html>…</html> document.",
                required = false,
            ),
            SkillParam(
                "css", "string",
                "Raw CSS rules (no <style> tags) to inject for action=patch_css.",
                required = false,
            ),
            SkillParam(
                "js", "string",
                "Raw JavaScript (no <script> tags) to inject for action=patch_js.",
                required = false,
            ),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        return when (val action = params["action"] as? String) {
            "read" -> {
                val html = server.readHtml()
                SkillResult(true, "控制台当前 HTML（${html.length} 字符）:\n\n$html")
            }

            "write" -> {
                val html = params["html"] as? String
                    ?: return SkillResult(false, "html 参数必填")
                if (!html.contains("<html", ignoreCase = true)) {
                    return SkillResult(false, "html 必须是完整的 HTML 文档（包含 <html> 标签）")
                }
                server.writeHtml(html)
                SkillResult(true, "✅ 控制台页面已更新（${html.length} 字符）。刷新浏览器即可看到新页面。")
            }

            "patch_css" -> {
                val css = params["css"] as? String
                    ?: return SkillResult(false, "css 参数必填")
                val current = server.readHtml()
                val styleBlock = "\n<style>\n/* console_patch */\n$css\n</style>\n"
                val patched = injectBeforeClosingHead(current, styleBlock)
                server.writeHtml(patched)
                SkillResult(true, "✅ CSS 已注入控制台。刷新浏览器生效。")
            }

            "patch_js" -> {
                val js = params["js"] as? String
                    ?: return SkillResult(false, "js 参数必填")
                val current = server.readHtml()
                val scriptBlock = "\n<script>\n/* console_patch */\n$js\n</script>\n"
                val patched = injectBeforeClosingBody(current, scriptBlock)
                server.writeHtml(patched)
                SkillResult(true, "✅ JS 已注入控制台。刷新浏览器生效。")
            }

            "reset" -> {
                server.resetHtml()
                SkillResult(true, "✅ 控制台已恢复默认样式。刷新浏览器生效。")
            }

            "get_url" -> {
                val url = server.getLanUrl()
                SkillResult(true, "控制台 LAN 地址：$url\n\n同一局域网内用浏览器打开即可访问。")
            }

            null, "" -> SkillResult(false, "action 参数必填")

            else -> SkillResult(
                false,
                "未知 action: $action。可用值: read | write | patch_css | patch_js | reset | get_url",
            )
        }
    }

    private fun injectBeforeClosingHead(html: String, block: String): String {
        val idx = html.indexOf("</head>", ignoreCase = true)
        return if (idx >= 0) html.substring(0, idx) + block + html.substring(idx)
        else block + html
    }

    private fun injectBeforeClosingBody(html: String, block: String): String {
        val idx = html.indexOf("</body>", ignoreCase = true)
        return if (idx >= 0) html.substring(0, idx) + block + html.substring(idx)
        else html + block
    }
}
