package com.mobileclaw.skill.builtin

import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.ui.InAppWebViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

class WebSearchSkill : Skill {
    override val meta = SkillMeta(
        id = "web_search",
        name = "Web Search",
        description = "Searches the web using DuckDuckGo and returns a summary of results. No API key required.",
        parameters = listOf(
            SkillParam("query", "string", "Search query"),
            SkillParam("max_results", "number", "Max results to return (default 5)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val query = params["query"] as? String ?: return@withContext SkillResult(false, "query is required")
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 5

        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .get().build()

        suspendCancellableCoroutine { cont ->
            httpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val html = response.body?.string() ?: ""
                        val doc = Jsoup.parse(html)
                        val results = doc.select(".result__body").take(maxResults).mapIndexed { i, el ->
                            val title = el.select(".result__title").text()
                            val snippet = el.select(".result__snippet").text()
                            val link = el.select(".result__url").text()
                            "${i + 1}. $title\n   $snippet\n   $link"
                        }
                        val output = if (results.isEmpty()) "No results found for: $query"
                        else results.joinToString("\n\n")
                        cont.resume(SkillResult(success = true, output = output))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }
}

class FetchUrlSkill : Skill {
    override val meta = SkillMeta(
        id = "fetch_url",
        name = "Fetch URL",
        description = "Fetches a URL and returns its main text content as markdown (truncated to 4000 chars).",
        parameters = listOf(
            SkillParam("url", "string", "URL to fetch"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val url = params["url"] as? String ?: return@withContext SkillResult(false, "url is required")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .get().build()

        suspendCancellableCoroutine { cont ->
            httpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val html = response.body?.string() ?: ""
                        val doc = Jsoup.parse(html)
                        doc.select("script, style, nav, footer, header, aside").remove()
                        val text = doc.body()?.text() ?: ""
                        cont.resume(SkillResult(success = true, output = text.take(4000)))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }
}

// ── In-App WebView Skills ─────────────────────────────────────────────────────

class WebBrowseSkill(private val manager: InAppWebViewManager) : Skill {
    override val meta = SkillMeta(
        id = "web_browse",
        name = "Browse URL (In-App WebView)",
        description = "Loads a URL in a hidden background WebView and waits for the page to finish loading. " +
            "After this, use web_content to read the page text or web_js to interact via JavaScript. " +
            "Supports dynamic/JS-rendered sites unlike fetch_url.",
        parameters = listOf(
            SkillParam("url", "string", "Full URL to load (must include https:// or http://)"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val url = params["url"] as? String ?: return SkillResult(false, "url is required")
        return runCatching {
            val statusText = manager.browse(url)
            val screenshot = manager.captureScreenshot()
            SkillResult(true, statusText, imageBase64 = screenshot)
        }.getOrElse { SkillResult(false, "WebView error: ${it.message}") }
    }
}

class WebContentSkill(private val manager: InAppWebViewManager) : Skill {
    override val meta = SkillMeta(
        id = "web_content",
        name = "Read WebView Page Content",
        description = "Extracts visible text from the currently loaded background WebView page (up to 5000 chars). " +
            "Call web_browse first. Use the selector param to focus on a specific page section.",
        parameters = listOf(
            SkillParam("selector", "string", "CSS selector for the target element (default: body)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val selector = params["selector"] as? String ?: "body"
        return runCatching {
            val text = manager.getContent(selector)
            SkillResult(true, "URL: ${manager.currentUrl()}\n\n$text")
        }.getOrElse { SkillResult(false, "Content error: ${it.message}") }
    }
}

class WebJsSkill(private val manager: InAppWebViewManager) : Skill {
    override val meta = SkillMeta(
        id = "web_js",
        name = "Execute JavaScript in WebView",
        description = "Runs JavaScript in the background WebView and returns the result. " +
            "Use for clicking web buttons, filling forms, reading dynamic data, or extracting structured info. " +
            "Call web_browse first.",
        parameters = listOf(
            SkillParam("script", "string", "JavaScript expression to evaluate; its return value is captured"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val script = params["script"] as? String ?: return SkillResult(false, "script is required")
        return runCatching {
            val result = manager.evalJs(script)
            SkillResult(true, "JS result: $result")
        }.getOrElse { SkillResult(false, "JS error: ${it.message}") }
    }
}
