package com.mobileclaw.skill.executor

import com.mobileclaw.skill.HttpSkillConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillResult
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpSkillExecutor(
    override val meta: SkillMeta,
    private val config: HttpSkillConfig,
) : Skill {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val mapped = config.paramsMapping.entries.associate { (skillKey, apiKey) ->
            apiKey to (params[skillKey]?.toString() ?: "")
        }

        // Substitute {param_name} templates in URL
        val resolvedUrl = params.entries.fold(config.url) { url, (k, v) ->
            url.replace("{$k}", v.toString())
        }

        val request = when (config.method.uppercase()) {
            "GET" -> {
                val url = resolvedUrl.toHttpUrl().newBuilder().apply {
                    mapped.forEach { (k, v) -> if (v.isNotBlank()) addQueryParameter(k, v) }
                }.build()
                Request.Builder().url(url)
                    .apply { config.headers.forEach { (k, v) -> header(k, v) } }
                    .get().build()
            }
            "POST" -> {
                val body = JSONObject(mapped).toString().toRequestBody("application/json".toMediaType())
                Request.Builder().url(resolvedUrl)
                    .apply { config.headers.forEach { (k, v) -> header(k, v) } }
                    .post(body).build()
            }
            else -> throw IllegalArgumentException("Unsupported HTTP method: ${config.method}")
        }

        return suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    val msg = when {
                        e.message?.contains("No address associated", ignoreCase = true) == true ||
                        e.message?.contains("Unable to resolve", ignoreCase = true) == true ->
                            "Network error: cannot resolve host. Check internet connection. (${e.message})"
                        e.message?.contains("timeout", ignoreCase = true) == true ||
                        e.message?.contains("timed out", ignoreCase = true) == true ->
                            "Network error: request timed out. (${e.message})"
                        e.message?.contains("Connection refused", ignoreCase = true) == true ->
                            "Network error: connection refused. (${e.message})"
                        else -> "Network error: ${e.message}"
                    }
                    cont.resume(SkillResult(success = false, output = msg))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resume(SkillResult(success = false, output = "HTTP ${response.code}: $body"))
                        return
                    }

                    // Extract image base64 from JSON response if path is configured
                    val imageBase64 = config.imageResponsePath?.let { path ->
                        runCatching { extractJsonPath(body, path) }.getOrNull()
                            ?.let { b64 ->
                                if (b64.startsWith("data:")) b64
                                else "data:image/png;base64,$b64"
                            }
                    }

                    // Extract text output from JSON response if path is configured
                    val textOutput = config.textResponsePath?.let { path ->
                        runCatching { extractJsonPath(body, path) }.getOrNull()
                    } ?: body

                    val attachment = imageBase64?.let {
                        SkillAttachment.ImageData(it, params["prompt"]?.toString())
                    }

                    cont.resume(SkillResult(
                        success = true,
                        output = textOutput.take(2000),
                        imageBase64 = imageBase64,
                        data = attachment,
                    ))
                }
            })
        }
    }

    /**
     * Extracts a value from a JSON string using dot-notation with bracket indexing.
     * Examples: "data[0].b64_json", "choices[0].text", "result"
     */
    private fun extractJsonPath(json: String, path: String): String {
        val segments = path.split(".")
        var current: Any = JSONObject(json)
        for (segment in segments) {
            val arrayMatch = Regex("(\\w+)\\[(\\d+)]").matchEntire(segment)
            current = if (arrayMatch != null) {
                val key = arrayMatch.groupValues[1]
                val index = arrayMatch.groupValues[2].toInt()
                (current as JSONObject).getJSONArray(key).get(index)
            } else {
                (current as JSONObject).get(segment)
            }
        }
        return current.toString()
    }
}
