package com.mobileclaw.skill.builtin

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.config.UserConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Generates videos by calling an async video generation API.
 *
 * Supported providers (configured via user_config key "video_api_endpoint"):
 *   - Kling AI  (快手可灵):  https://api.klingai.com
 *   - Any OpenAI-compatible video endpoint
 *
 * Required user_config keys:
 *   video_api_endpoint  — API base URL, e.g. https://api.klingai.com
 *   video_api_key       — API key for the video provider
 *
 * Flow: submit job → poll status → download video → return FileData attachment.
 */
class GenerateVideoSkill(
    private val config: AgentConfig,
    private val context: Context,
    private val userConfig: UserConfig,
) : Skill {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override val meta = SkillMeta(
        id = "generate_video",
        name = "Generate Video",
        nameZh = "生成视频",
        description = "Generates a video from a text prompt using an external async video API (e.g. Kling AI). " +
            "Requires user_config keys: video_api_endpoint (e.g. https://api.klingai.com) and video_api_key. " +
            "Parameters: prompt (required), duration in seconds (optional, default 5), " +
            "aspect_ratio e.g. 16:9 (optional), model e.g. kling-v1 (optional). " +
            "Returns the generated video as a downloadable file attachment.",
        descriptionZh = "通过外部异步视频 API（如快手可灵）生成视频。" +
            "需要在用户配置中设置 video_api_endpoint 和 video_api_key。",
        parameters = listOf(
            SkillParam("prompt", "string", "Text description of the video to generate"),
            SkillParam("duration", "string", "Video duration in seconds (default: 5)", required = false),
            SkillParam("aspect_ratio", "string", "Aspect ratio: 16:9 | 9:16 | 1:1 (default: 16:9)", required = false),
            SkillParam("model", "string", "Model name, e.g. kling-v1 (provider-specific, optional)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("创作"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val prompt = params["prompt"] as? String
            ?: return@withContext SkillResult(false, "prompt parameter is required")
        val duration = (params["duration"] as? String)?.toIntOrNull() ?: 5
        val aspectRatio = (params["aspect_ratio"] as? String) ?: "16:9"
        val model = params["model"] as? String

        // Read video API config from user_config (stored via UserConfigSkill)
        val endpoint = userConfig.get("video_api_endpoint")?.trim()
            ?: return@withContext SkillResult(false,
                "video_api_endpoint not configured. Use: user_config(action=set, key=video_api_endpoint, value=https://api.klingai.com)")
        val apiKey = userConfig.get("video_api_key")?.trim()
            ?: return@withContext SkillResult(false,
                "video_api_key not configured. Use: user_config(action=set, key=video_api_key, value=YOUR_KEY)")

        // Detect provider from endpoint
        val provider = when {
            "klingai" in endpoint -> Provider.KLING
            else                  -> Provider.OPENAI_COMPAT
        }

        val taskId = submitJob(endpoint, apiKey, provider, prompt, duration, aspectRatio, model)
            ?: return@withContext SkillResult(false, "Failed to submit video generation job")

        // Poll for completion (up to 10 minutes, polling every 10s)
        val videoUrl = pollForCompletion(endpoint, apiKey, provider, taskId, maxWaitMs = 600_000L)
            ?: return@withContext SkillResult(false, "Video generation timed out or failed (task_id: $taskId)")

        // Download the video
        val outputFile = downloadVideo(videoUrl, prompt)
            ?: return@withContext SkillResult(true,
                "Video generated but could not download. URL: $videoUrl",
                data = SkillAttachment.WebPage(videoUrl, "Generated Video", prompt.take(120)))

        SkillResult(
            success = true,
            output = "视频已生成：${outputFile.name}",
            data = SkillAttachment.FileData(outputFile.absolutePath, outputFile.name, "video/mp4", outputFile.length()),
        )
    }

    // ── Submission ────────────────────────────────────────────────────────────

    private fun submitJob(
        endpoint: String,
        apiKey: String,
        provider: Provider,
        prompt: String,
        duration: Int,
        aspectRatio: String,
        model: String?,
    ): String? {
        val base = endpoint.trimEnd('/')
        return when (provider) {
            Provider.KLING -> submitKling(base, apiKey, prompt, duration, aspectRatio, model ?: "kling-v1")
            Provider.OPENAI_COMPAT -> submitOpenAiCompat(base, apiKey, prompt, duration, aspectRatio, model)
        }
    }

    private fun submitKling(base: String, apiKey: String, prompt: String, duration: Int, aspectRatio: String, model: String): String? {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("duration", duration)
            addProperty("aspect_ratio", aspectRatio)
        }
        val req = Request.Builder()
            .url("$base/v1/videos/text2video")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                json["data"]?.asJsonObject?.get("task_id")?.asString
                    ?: json["task_id"]?.asString
            }
        }.getOrNull()
    }

    private fun submitOpenAiCompat(base: String, apiKey: String, prompt: String, duration: Int, aspectRatio: String, model: String?): String? {
        val body = JsonObject().apply {
            if (model != null) addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("duration", duration)
            addProperty("aspect_ratio", aspectRatio)
            addProperty("n", 1)
        }
        val req = Request.Builder()
            .url("$base/v1/videos/generations")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                json["id"]?.asString ?: json["task_id"]?.asString
            }
        }.getOrNull()
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private suspend fun pollForCompletion(
        endpoint: String,
        apiKey: String,
        provider: Provider,
        taskId: String,
        maxWaitMs: Long,
    ): String? {
        val base = endpoint.trimEnd('/')
        val pollUrl = when (provider) {
            Provider.KLING       -> "$base/v1/videos/text2video/$taskId"
            Provider.OPENAI_COMPAT -> "$base/v1/videos/generations/$taskId"
        }
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            delay(10_000L)
            val result = runCatching {
                val req = Request.Builder()
                    .url(pollUrl)
                    .header("Authorization", "Bearer $apiKey")
                    .get().build()
                client.newCall(req).execute().use { resp ->
                    val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                    extractVideoUrl(json, provider)
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun extractVideoUrl(json: JsonObject, provider: Provider): String? {
        // Kling: data.task_result.videos[0].url
        val data = json["data"]?.asJsonObject ?: json
        val status = data["task_status"]?.asString ?: data["status"]?.asString ?: ""
        if (status == "failed" || status == "error") return null
        if (status != "succeed" && status != "completed" && status != "success") return null  // still processing

        // Try common response shapes
        return data["task_result"]?.asJsonObject
            ?.get("videos")?.asJsonArray
            ?.firstOrNull()?.asJsonObject
            ?.get("url")?.asString
            ?: data["videos"]?.asJsonArray?.firstOrNull()?.asJsonObject?.get("url")?.asString
            ?: json["url"]?.asString
            ?: data["data"]?.asJsonArray?.firstOrNull()?.asJsonObject?.get("url")?.asString
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private fun downloadVideo(url: String, prompt: String): File? {
        return runCatching {
            val safeFilename = prompt.take(30)
                .replace(Regex("[^\\w\\s-]"), "").trim().replace(" ", "_")
                .ifBlank { "video" }
            val outDir = File(context.filesDir, "videos").also { it.mkdirs() }
            val outFile = File(outDir, "${safeFilename}_${System.currentTimeMillis()}.mp4")
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                resp.body?.bytes()?.let { outFile.writeBytes(it) }
            }
            if (outFile.exists() && outFile.length() > 0) outFile else null
        }.getOrNull()
    }

    private enum class Provider { KLING, OPENAI_COMPAT }
}
