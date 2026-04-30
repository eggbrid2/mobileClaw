package com.mobileclaw.skill.builtin

import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.ui.InAppWebViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

private data class SearchEntry(val title: String, val url: String, val snippet: String)

class WebSearchSkill(private val webView: InAppWebViewManager? = null) : Skill {
    override val meta = SkillMeta(
        id = "web_search",
        name = "Web Search",
        nameZh = "网络搜索",
        description = "Searches the web using a real hidden browser (no API key needed). " +
            "Auto mode tries Baidu → Sogou → Bing → DuckDuckGo.",
        descriptionZh = "使用内置隐藏浏览器搜索网络，无需 API，自动选择最佳引擎（百度→搜狗→Bing→DuckDuckGo）。",
        parameters = listOf(
            SkillParam("query", "string", "Search query"),
            SkillParam("max_results", "number", "Max results to return (default 5)", required = false),
            SkillParam("engine", "string", "'auto' (default) | 'baidu' | 'sogou' | 'bing' | 'duckduckgo'", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        tags = listOf("网络"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val query = params["query"] as? String ?: return SkillResult(false, "query is required")
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 5
        val engine = (params["engine"] as? String)?.lowercase() ?: "auto"

        // Try browser-based search first (real WebView, bypasses blocks)
        if (webView != null) {
            val result = runCatching { browserSearch(query, maxResults, engine) }.getOrNull()
            if (result != null && result.success) return result
        }

        // HTTP fallback
        return withContext(Dispatchers.IO) { httpSearch(query, maxResults, engine) }
    }

    // ── Real browser search (hidden WebView) ─────────────────────────────────

    private suspend fun browserSearch(query: String, maxResults: Int, engine: String): SkillResult {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // Engine priority: for "auto" try Baidu then Sogou then Bing
        val candidates = when (engine) {
            "baidu"      -> listOf("baidu")
            "sogou"      -> listOf("sogou")
            "bing"       -> listOf("bing")
            "duckduckgo" -> listOf("duckduckgo")
            else         -> listOf("baidu", "sogou", "bing", "duckduckgo")
        }

        for (eng in candidates) {
            val url = when (eng) {
                "baidu"      -> "https://m.baidu.com/s?word=$encoded&rn=$maxResults"
                "sogou"      -> "https://www.sogou.com/web?query=$encoded&num=$maxResults"
                "bing"       -> "https://cn.bing.com/search?q=$encoded&count=$maxResults"
                else         -> "https://html.duckduckgo.com/html/?q=$encoded"
            }
            val entries = runCatching {
                webView!!.browse(url)
                // Give JS-rendered content a moment to settle
                kotlinx.coroutines.delay(1200)
                extractViaJs(eng, maxResults)
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: continue

            val label = when (eng) {
                "baidu" -> "百度"; "sogou" -> "搜狗"; "bing" -> "Bing"; else -> "DuckDuckGo"
            }
            return buildResult(label, entries, query)
        }
        return SkillResult(false, "Browser search returned no results for: $query")
    }

    private suspend fun extractViaJs(engine: String, maxResults: Int): List<SearchEntry> {
        val script = when (engine) {
            "baidu" -> """
                (function(){
                  var r=[];
                  var els=document.querySelectorAll('article,.c-container,[class*="result"]');
                  for(var i=0;i<els.length&&r.length<$maxResults;i++){
                    var el=els[i];
                    var a=el.querySelector('h3 a,.c-title a,a[class*="title"],a[class*="c-title"]');
                    if(!a||!a.innerText.trim())continue;
                    var href=a.href||'';
                    if(!href.startsWith('http'))continue;
                    var snip=el.querySelector('.c-abstract,.c-summary,[class*="abstract"],[class*="summary"],p');
                    r.push({t:a.innerText.trim(),u:href,s:snip?snip.innerText.trim().substring(0,200):''});
                  }
                  return JSON.stringify(r);
                })();
            """.trimIndent()
            "sogou" -> """
                (function(){
                  var r=[];
                  var els=document.querySelectorAll('div.rb,div.vrwrap,[class*="result"]');
                  for(var i=0;i<els.length&&r.length<$maxResults;i++){
                    var el=els[i];
                    var a=el.querySelector('h3 a,.pt a,.vrTitle a,a[class*="title"]');
                    if(!a||!a.innerText.trim())continue;
                    if(!a.href||!a.href.startsWith('http'))continue;
                    var snip=el.querySelector('.str_info,.ft,p');
                    r.push({t:a.innerText.trim(),u:a.href,s:snip?snip.innerText.trim().substring(0,200):''});
                  }
                  return JSON.stringify(r);
                })();
            """.trimIndent()
            "bing" -> """
                (function(){
                  var r=[];
                  var els=document.querySelectorAll('li.b_algo,div.b_algo');
                  for(var i=0;i<els.length&&r.length<$maxResults;i++){
                    var el=els[i];
                    var a=el.querySelector('h2 a');
                    if(!a||!a.href||!a.href.startsWith('http'))continue;
                    var snip=el.querySelector('.b_caption p,.b_algoSlug,p');
                    r.push({t:a.innerText.trim(),u:a.href,s:snip?snip.innerText.trim().substring(0,200):''});
                  }
                  return JSON.stringify(r);
                })();
            """.trimIndent()
            else -> """
                (function(){
                  var r=[];
                  var els=document.querySelectorAll('.result__body,.result');
                  for(var i=0;i<els.length&&r.length<$maxResults;i++){
                    var el=els[i];
                    var t=el.querySelector('.result__title,.result__a');
                    var a=el.querySelector('.result__url,a');
                    var s=el.querySelector('.result__snippet,p');
                    if(!t||!t.innerText.trim())continue;
                    var url=(a&&a.href)||'';
                    r.push({t:t.innerText.trim(),u:url,s:s?s.innerText.trim().substring(0,200):''});
                  }
                  return JSON.stringify(r);
                })();
            """.trimIndent()
        }

        val raw = webView!!.evalJs(script).trim()
        if (raw == "null" || raw == "\"[]\"" || raw.isBlank()) return emptyList()
        // evalJs wraps string result in quotes and escapes — strip outer quotes and unescape
        val json = if (raw.startsWith("\"")) {
            raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "")
        } else raw
        return parseJsonEntries(json)
    }

    private fun parseJsonEntries(json: String): List<SearchEntry> = runCatching {
        // Manual lightweight JSON array parse — avoids adding a heavy dependency
        val results = mutableListOf<SearchEntry>()
        val objPattern = Regex("""\{[^}]+\}""")
        val tPat = Regex(""""t"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val uPat = Regex(""""u"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val sPat = Regex(""""s"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        for (m in objPattern.findAll(json)) {
            val obj = m.value
            val title = tPat.find(obj)?.groupValues?.get(1)?.unescape() ?: continue
            val url   = uPat.find(obj)?.groupValues?.get(1)?.unescape() ?: continue
            val snip  = sPat.find(obj)?.groupValues?.get(1)?.unescape() ?: ""
            if (title.isBlank() || !url.startsWith("http")) continue
            results += SearchEntry(title, url, snip)
        }
        results
    }.getOrDefault(emptyList())

    private fun String.unescape() = this
        .replace("\\n", " ").replace("\\t", " ")
        .replace("\\u003c", "<").replace("\\u003e", ">")
        .replace("\\u0026", "&").replace("\\\"", "\"")
        .replace("\\\\", "\\")

    // ── HTTP fallback ─────────────────────────────────────────────────────────

    private fun buildResult(engineName: String, entries: List<SearchEntry>, query: String): SkillResult {
        val textSummary = "[$engineName]\n\n" + entries.mapIndexed { i, e ->
            "${i + 1}. ${e.title}\n   ${e.snippet}\n   ${e.url}"
        }.joinToString("\n\n")
        val pages = entries.map { SkillAttachment.WebPage(it.url, it.title, it.snippet) }
        return SkillResult(
            success = true,
            output = textSummary,
            data = SkillAttachment.SearchResults(query = query, engine = engineName, pages = pages),
        )
    }

    private suspend fun httpSearch(query: String, maxResults: Int, engine: String): SkillResult {
        val (engineName, entries) = when (engine) {
            "duckduckgo" -> "DuckDuckGo" to searchDuckDuckGo(query, maxResults)
            "bing"       -> "Bing"       to searchBing(query, maxResults)
            "baidu"      -> "百度"        to searchBaidu(query, maxResults)
            "sogou"      -> "搜狗"        to searchSogou(query, maxResults)
            else -> {
                val baidu = runCatching { searchBaidu(query, maxResults) }.getOrNull()
                if (!baidu.isNullOrEmpty()) return buildResult("百度", baidu, query)
                val sogou = runCatching { searchSogou(query, maxResults) }.getOrNull()
                if (!sogou.isNullOrEmpty()) return buildResult("搜狗", sogou, query)
                val bing = runCatching { searchBing(query, maxResults) }.getOrNull()
                if (!bing.isNullOrEmpty()) return buildResult("Bing", bing, query)
                "DuckDuckGo" to (runCatching { searchDuckDuckGo(query, maxResults) }.getOrElse { emptyList() })
            }
        }
        if (entries.isEmpty()) return SkillResult(true, "No results found for: $query")
        return buildResult(engineName, entries, query)
    }

    private suspend fun searchDuckDuckGo(query: String, maxResults: Int): List<SearchEntry> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetchHtml("https://html.duckduckgo.com/html/?q=$encoded")
        val doc = Jsoup.parse(html)
        return doc.select(".result__body").take(maxResults).map { el ->
            SearchEntry(
                title = el.select(".result__title").text(),
                snippet = el.select(".result__snippet").text(),
                url = el.select(".result__url").text().let { raw ->
                    if (raw.startsWith("http")) raw else "https://$raw"
                },
            )
        }.filter { it.title.isNotBlank() }
    }

    private suspend fun searchBing(query: String, maxResults: Int): List<SearchEntry> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetchHtml(
            "https://www.bing.com/search?q=$encoded&count=$maxResults",
            ua = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
        )
        val doc = Jsoup.parse(html)
        return doc.select("li.b_algo").take(maxResults).map { el ->
            SearchEntry(
                title = el.select("h2 a").text(),
                url = el.select("h2 a").attr("href"),
                snippet = el.select(".b_caption p, .b_algoSlug").text(),
            )
        }.filter { it.title.isNotBlank() && it.url.startsWith("http") }
    }

    private suspend fun searchBaidu(query: String, maxResults: Int): List<SearchEntry> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val mobileUa = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val html = fetchHtml("https://m.baidu.com/s?word=$encoded&rn=$maxResults", ua = mobileUa)
        val doc = Jsoup.parse(html)
        val results = doc.select("article, div[class*=c-result], div[class*=result-op], div.c-container")
            .take(maxResults + 6)
            .mapNotNull { el ->
                val titleEl = el.select("h3 a, .c-title a, a[class*=title], a[class*=c-title]").firstOrNull()
                    ?: return@mapNotNull null
                val title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val rawHref = titleEl.attr("href")
                val url = if (rawHref.startsWith("http")) rawHref else "https://m.baidu.com$rawHref"
                val snippet = el.select(".c-abstract, .c-summary, [class*=abstract], [class*=summary], p").text()
                SearchEntry(title, url, snippet)
            }
            .take(maxResults)
        // Fallback: try desktop selectors if mobile returned nothing
        if (results.isEmpty()) {
            val html2 = fetchHtml("https://www.baidu.com/s?wd=$encoded&rn=$maxResults", ua = mobileUa)
            val doc2 = Jsoup.parse(html2)
            return doc2.select("div[class*=result]").take(maxResults).mapNotNull { el ->
                val a = el.select("h3 a").firstOrNull() ?: return@mapNotNull null
                val title = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val href = a.attr("href").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                SearchEntry(title, href, el.select(".c-abstract, [class*=abs]").text())
            }
        }
        return results
    }

    private suspend fun searchSogou(query: String, maxResults: Int): List<SearchEntry> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetchHtml(
            "https://www.sogou.com/web?query=$encoded&num=$maxResults",
            ua = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        val doc = Jsoup.parse(html)
        return doc.select("div.rb, div.vrwrap, div[class*=result]").take(maxResults).mapNotNull { el ->
            val titleEl = el.select("h3 a, .pt a, .vrTitle a, a[class*=title]").firstOrNull() ?: return@mapNotNull null
            val title = titleEl.text().trim().ifBlank { return@mapNotNull null }
            val url = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val snippet = el.select(".str_info, .ft, .str_time, p").text()
            SearchEntry(title, url, snippet)
        }
    }

    private suspend fun fetchHtml(url: String, ua: String = "Mozilla/5.0 (Android; Mobile)"): String =
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(url).header("User-Agent", ua).get().build()
            httpClient.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string() ?: ""
                    cont.resume(body)
                }
            })
        }
}

class FetchUrlSkill : Skill {
    override val meta = SkillMeta(
        id = "fetch_url",
        name = "Fetch URL",
        nameZh = "抓取网页",
        description = "Fetches a URL and returns its main text content (truncated to 4000 chars). Also produces a WebPage card attachment shown in chat.",
        descriptionZh = "抓取网页并返回可读文本（最多 4000 字），同时在聊天中显示网页卡片。",
        parameters = listOf(
            SkillParam("url", "string", "URL to fetch"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        tags = listOf("网络"),
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
                        val doc = Jsoup.parse(html, url)
                        doc.select("script, style, nav, footer, header, aside").remove()
                        val text = doc.body()?.text() ?: ""
                        val title = doc.title().take(120)
                        val attachment = SkillAttachment.WebPage(url, title, text.take(400))
                        cont.resume(SkillResult(success = true, output = text.take(4000), data = attachment))
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
        tags = listOf("网络"),
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
        tags = listOf("网络"),
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
        tags = listOf("网络"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val script = params["script"] as? String ?: return SkillResult(false, "script is required")
        return runCatching {
            val result = manager.evalJs(script)
            SkillResult(true, "JS result: $result")
        }.getOrElse { SkillResult(false, "JS error: ${it.message}") }
    }
}
