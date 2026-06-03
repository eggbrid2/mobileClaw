package com.mobileclaw.skill.builtin

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.BuildConfig
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.config.UserConfig
import com.mobileclaw.config.capabilityApiKey
import com.mobileclaw.config.capabilityEndpoint
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private data class VideoTaskSubmission(
    val taskId: String,
    val rawResponse: String = "",
    val errorMessage: String = "",
    val httpCode: Int? = null,
)

private const val VIDEO_LOG_TAG = "GenerateVideoSkill"

/**
 * Generates videos by calling an async video generation API.
 *
 * Preferred config source: active gateway capability "video".
 * Fallback config source: legacy user_config values such as video_api_endpoint/video_api_key.
 *
 * Supported providers:
 *   - Kling AI  (快手可灵):  https://api.klingai.com
 *   - Agnes APIHub:         https://apihub.agnes-ai.com
 *   - Any OpenAI-compatible video endpoint
 *
 * Flow: submit job → poll status → download video → return FileData attachment.
 */
class GenerateVideoSkill(
    private val config: AgentConfig,
    private val context: Context,
    private val userConfig: UserConfig,
    private val taskManager: VideoGenerationTaskManager,
) : Skill {

    private val client = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val imageUploader = CloudinaryImageUploader(context, userConfig, client)

    override val meta = SkillMeta(
        id = "generate_video",
        name = "Generate Video",
        nameZh = "生成视频",
        description = "Generates a video from a text prompt using an external async video API (e.g. Kling AI / Agnes APIHub). " +
            "Prefer the active gateway video capability; legacy video_api_endpoint/video_api_key is only a fallback. " +
            "Parameters: prompt (required), duration in seconds (optional, default 5), " +
            "aspect_ratio e.g. 16:9 (optional), model (optional), image for image-to-video (optional). " +
            "Returns the generated video as a downloadable file attachment.",
        descriptionZh = "通过外部异步视频 API（如快手可灵、Agnes APIHub）生成视频。" +
            "优先使用当前网关里的 video 能力配置，旧的 video_api_endpoint / video_api_key 仅作为兼容兜底。",
        parameters = listOf(
            SkillParam("prompt", "string", "Text description of the video to generate"),
            SkillParam("gateway_id", "string", "Optional configured gateway id to use for video generation", required = false),
            SkillParam("gateway_name", "string", "Optional configured gateway name to use for video generation", required = false),
            SkillParam("duration", "string", "Video duration in seconds (default: 5)", required = false),
            SkillParam("aspect_ratio", "string", "Aspect ratio: 16:9 | 9:16 | 1:1 (default: 16:9)", required = false),
            SkillParam("model", "string", "Model name, e.g. kling-v1 (provider-specific, optional)", required = false),
            SkillParam("image", "string", "Optional source image URL for image-to-video providers such as Agnes", required = false),
            SkillParam("negative_prompt", "string", "Optional negative prompt (provider-specific)", required = false),
            SkillParam("width", "string", "Optional output width in pixels (provider-specific)", required = false),
            SkillParam("height", "string", "Optional output height in pixels (provider-specific)", required = false),
            SkillParam("num_frames", "string", "Optional frame count (provider-specific)", required = false),
            SkillParam("frame_rate", "string", "Optional frame rate (provider-specific)", required = false),
            SkillParam("seed", "string", "Optional deterministic seed (provider-specific)", required = false),
            SkillParam("num_inference_steps", "string", "Optional inference steps (provider-specific)", required = false),
            SkillParam("mode", "string", "Optional provider mode, e.g. keyframes", required = false),
            SkillParam("first_frame_image", "string", "Optional first/start frame image URL, content URI, local path, or data URI", required = false),
            SkillParam("last_frame_image", "string", "Optional last/end frame image URL, content URI, local path, or data URI", required = false),
            SkillParam("images", "string", "Optional JSON array or comma/newline-separated image URLs for providers supporting multi-image input", required = false),
            SkillParam("extra_body", "string", "Optional raw JSON object merged into the provider request body", required = false),
            SkillParam("wait_for_completion", "string", "Whether to poll until the video is ready. Set false to submit and track asynchronously.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        categories = listOf(SkillToolCategory.MEDIA),
        tags = listOf("创作"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val prompt = params["prompt"] as? String
            ?: return@withContext SkillResult(false, "prompt parameter is required")
        val duration = (params["duration"] as? String)?.toIntOrNull() ?: 5
        val aspectRatio = (params["aspect_ratio"] as? String) ?: "16:9"
        val snapshot = config.snapshot()
        val selectedGateway = selectGateway(
            snapshot = snapshot,
            gatewayId = params["gateway_id"] as? String,
            gatewayName = params["gateway_name"] as? String,
        )
        val gatewayVideoEndpoint = selectedGateway?.capabilityEndpoint("video").orEmpty()
        val gatewayVideoApiKey = selectedGateway?.capabilityApiKey("video").orEmpty()
        val gatewayVideoModel = selectedGateway?.capabilityModel("video")
        val model = (params["model"] as? String)?.takeIf { it.isNotBlank() }
            ?: gatewayVideoModel
            ?: userConfig.get("video_api_model")?.trim()?.takeIf { it.isNotBlank() }
            ?: userConfig.get("agnes_video_model")?.trim()?.takeIf { it.isNotBlank() }
        val firstFrameImage = firstStringParam(
            params,
            "first_frame_image",
            "start_frame_image",
            "first_frame",
            "start_image",
        )
        val lastFrameImage = firstStringParam(
            params,
            "last_frame_image",
            "end_frame_image",
            "last_frame",
            "end_image",
        )
        val explicitImages = parseFlexibleStringList(params["images"] as? String)
        val image = firstFrameImage
            ?: (params["image"] as? String)?.takeIf { it.isNotBlank() }
            ?: userConfig.get("latest_image_local_path")?.trim()?.takeIf {
                explicitImages.isEmpty() && prompt.shouldUseLatestUserImageForVideo()
            }
        val negativePrompt = params["negative_prompt"] as? String
        val width = (params["width"] as? String)?.toIntOrNull()
        val height = (params["height"] as? String)?.toIntOrNull()
        val numFrames = (params["num_frames"] as? String)?.toIntOrNull()
        val frameRate = (params["frame_rate"] as? String)?.toIntOrNull()
        val seed = (params["seed"] as? String)?.toLongOrNull()
        val numInferenceSteps = (params["num_inference_steps"] as? String)?.toIntOrNull()
        val mode = (params["mode"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val waitForCompletion = (params["wait_for_completion"] as? String)
            ?.trim()
            ?.lowercase()
            ?.let { it == "true" || it == "1" || it == "yes" }
            ?: true
        val images = buildList {
            addAll(explicitImages)
            firstFrameImage?.let(::add)
            lastFrameImage?.let(::add)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val extraBody = parseJsonObject(params["extra_body"] as? String)?.deepCopy() ?: JsonObject()
        firstFrameImage?.let {
            extraBody.addProperty("first_frame_image", it)
            extraBody.addProperty("start_frame_image", it)
        }
        lastFrameImage?.let {
            extraBody.addProperty("last_frame_image", it)
            extraBody.addProperty("end_frame_image", it)
        }
        val cloudinaryCloudName = userConfig.get("cloudinary_cloud_name")?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLOUDINARY_CLOUD_NAME
        val cloudinaryApiKey = userConfig.get("cloudinary_api_key")?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLOUDINARY_API_KEY
        val cloudinaryApiSecret = userConfig.get("cloudinary_api_secret")?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLOUDINARY_API_SECRET

        // Read video API config from gateway first, then fall back to legacy user_config.
        val endpoint = gatewayVideoEndpoint.takeIf { it.isNotBlank() }
            ?: userConfig.get("video_api_endpoint")?.trim()?.takeIf { it.isNotBlank() }
            ?: snapshot.endpoint.takeIf { it.isNotBlank() }
            ?: return@withContext SkillResult(false,
                "Video generation is not configured. Add a gateway with video capability, or set legacy video_api_endpoint.")
        val apiKey = gatewayVideoApiKey.takeIf { it.isNotBlank() }
            ?: userConfig.get("video_api_key")?.trim()?.takeIf { it.isNotBlank() }
            ?: snapshot.apiKey.takeIf { it.isNotBlank() }
            ?: return@withContext SkillResult(false,
                "Video generation credentials are unavailable. Add a gateway video capability, or set legacy video_api_key.")

        // Detect provider from endpoint
        val provider = VideoTaskRuntime.detectProvider(endpoint)

        val submission = submitJob(
            endpoint = endpoint,
            apiKey = apiKey,
            provider = provider,
            prompt = prompt,
            duration = duration,
            aspectRatio = aspectRatio,
            model = model,
            image = image,
            negativePrompt = negativePrompt,
            width = width,
            height = height,
            numFrames = numFrames,
            frameRate = frameRate,
            seed = seed,
            numInferenceSteps = numInferenceSteps,
            mode = mode,
            images = images,
            extraBody = extraBody.takeIf { it.size() > 0 },
            cloudinaryCloudName = cloudinaryCloudName,
            cloudinaryApiKey = cloudinaryApiKey,
            cloudinaryApiSecret = cloudinaryApiSecret,
        )
        if (submission == null) {
            return@withContext SkillResult(false, "视频任务提交失败，未收到服务端响应。")
        }
        if (submission.taskId.isBlank()) {
            val failure = buildString {
                append("视频任务提交失败")
                submission.httpCode?.let { append("，HTTP $it") }
                if (submission.errorMessage.isNotBlank()) {
                    append("：")
                    append(submission.errorMessage)
                } else if (submission.rawResponse.isNotBlank()) {
                    append("：")
                    append(submission.rawResponse.take(300))
                }
            }
            taskManager.recordSubmitFailed(
                prompt = prompt,
                provider = provider,
                endpoint = endpoint,
                apiKey = apiKey,
                model = model,
                errorMessage = failure,
                submitResponseRaw = submission.rawResponse,
            )
            return@withContext SkillResult(false, failure)
        }
        val taskId = submission.taskId
        taskManager.recordSubmitted(
            taskId = taskId,
            prompt = prompt,
            provider = provider,
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            submitResponseRaw = submission.rawResponse,
        )
        if (!waitForCompletion) {
            return@withContext SkillResult(
                success = true,
                output = "视频任务已提交，已加入任务列表后台追踪。task_id: $taskId",
            )
        }

        // Poll for completion (up to 10 minutes, polling every 10s)
        val videoUrl = pollForCompletion(endpoint, apiKey, provider, taskId, maxWaitMs = 600_000L)
        if (videoUrl == null) {
            taskManager.markTimedOut(taskId)
            return@withContext SkillResult(
                success = true,
                output = "视频任务已提交，生成时间较长，已加入长任务列表继续追踪。task_id: $taskId",
            )
        }

        // Download the video
        val outputFile = downloadVideo(videoUrl, prompt)
            ?: run {
                taskManager.markCompleted(taskId, videoUrl)
                return@withContext SkillResult(
                    true,
                    "Video generated but could not download. URL: $videoUrl",
                    data = SkillAttachment.WebPage(videoUrl, "Generated Video", prompt.take(120)),
                )
            }
        taskManager.markCompleted(taskId, videoUrl, outputFile.absolutePath)

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
        provider: VideoTaskProvider,
        prompt: String,
        duration: Int,
        aspectRatio: String,
        model: String?,
        image: String?,
        negativePrompt: String?,
        width: Int?,
        height: Int?,
        numFrames: Int?,
        frameRate: Int?,
        seed: Long?,
        numInferenceSteps: Int?,
        mode: String?,
        images: List<String>,
        extraBody: JsonObject?,
        cloudinaryCloudName: String,
        cloudinaryApiKey: String,
        cloudinaryApiSecret: String,
    ): VideoTaskSubmission? {
        val base = normalizeProviderBase(endpoint)
        return when (provider) {
            VideoTaskProvider.KLING -> submitKling(
                base = base,
                apiKey = apiKey,
                prompt = prompt,
                duration = duration,
                aspectRatio = aspectRatio,
                model = model ?: "kling-v1",
                image = image,
                images = images,
                extraBody = extraBody,
            )
            VideoTaskProvider.AGNES -> submitAgnes(
                base = base,
                apiKey = apiKey,
                prompt = prompt,
                duration = duration,
                aspectRatio = aspectRatio,
                model = model,
                image = image,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                numFrames = numFrames,
                frameRate = frameRate,
                seed = seed,
                numInferenceSteps = numInferenceSteps,
                mode = mode,
                images = images,
                extraBody = extraBody,
                cloudinaryCloudName = cloudinaryCloudName,
                cloudinaryApiKey = cloudinaryApiKey,
                cloudinaryApiSecret = cloudinaryApiSecret,
            )
            VideoTaskProvider.OPENAI_COMPAT -> submitOpenAiCompat(endpoint, apiKey, prompt, duration, aspectRatio, model, image, images, extraBody)
        }
    }

    private fun submitKling(
        base: String,
        apiKey: String,
        prompt: String,
        duration: Int,
        aspectRatio: String,
        model: String,
        image: String?,
        images: List<String>,
        extraBody: JsonObject?,
    ): VideoTaskSubmission? {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("duration", duration)
            addProperty("aspect_ratio", aspectRatio)
            if (!image.isNullOrBlank()) addProperty("image", image)
            if (images.isNotEmpty()) {
                add("images", JsonArray().apply { images.forEach { add(it) } })
            }
            mergeJsonObject(this, extraBody)
        }
        val req = Request.Builder()
            .url("$base/v1/videos/text2video")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        logSubmitRequest("Kling", req.url.toString(), body)
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                logSubmitResponse("Kling", resp.code, raw)
                if (!resp.isSuccessful) {
                    return@use VideoTaskSubmission(
                        taskId = "",
                        rawResponse = raw,
                        errorMessage = extractSubmitError(raw).ifBlank { "Kling submit failed" },
                        httpCode = resp.code,
                    )
                }
                val json = JsonParser.parseString(raw).asJsonObject
                val taskId = json["data"]?.asJsonObject?.get("task_id")?.asString
                    ?: json["task_id"]?.asString
                VideoTaskSubmission(
                    taskId = taskId.orEmpty(),
                    rawResponse = raw,
                    errorMessage = if (taskId.isNullOrBlank()) extractSubmitError(raw) else "",
                    httpCode = resp.code,
                )
            }
        }.getOrElse { error ->
            VideoTaskSubmission(taskId = "", errorMessage = error.message.orEmpty())
        }
    }

    private fun submitOpenAiCompat(
        endpoint: String,
        apiKey: String,
        prompt: String,
        duration: Int,
        aspectRatio: String,
        model: String?,
        image: String?,
        images: List<String>,
        extraBody: JsonObject?,
    ): VideoTaskSubmission? {
        val body = JsonObject().apply {
            if (model != null) addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("duration", duration)
            addProperty("aspect_ratio", aspectRatio)
            addProperty("n", 1)
            if (!image.isNullOrBlank()) addProperty("image", image)
            if (images.isNotEmpty()) {
                add("images", JsonArray().apply { images.forEach { add(it) } })
            }
            mergeJsonObject(this, extraBody)
        }
        val base = VideoTaskRuntime.normalizeOpenAiCompatibleBase(endpoint)
        val req = Request.Builder()
            .url("$base/videos/generations")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        logSubmitRequest("OpenAI-compatible", req.url.toString(), body)
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                logSubmitResponse("OpenAI-compatible", resp.code, raw)
                if (!resp.isSuccessful) {
                    return@use VideoTaskSubmission(
                        taskId = "",
                        rawResponse = raw,
                        errorMessage = extractSubmitError(raw).ifBlank { "OpenAI-compatible video submit failed" },
                        httpCode = resp.code,
                    )
                }
                val json = JsonParser.parseString(raw).asJsonObject
                val taskId = json["id"]?.asString ?: json["task_id"]?.asString
                VideoTaskSubmission(
                    taskId = taskId.orEmpty(),
                    rawResponse = raw,
                    errorMessage = if (taskId.isNullOrBlank()) extractSubmitError(raw) else "",
                    httpCode = resp.code,
                )
            }
        }.getOrElse { error ->
            VideoTaskSubmission(taskId = "", errorMessage = error.message.orEmpty())
        }
    }

    private fun submitAgnes(
        base: String,
        apiKey: String,
        prompt: String,
        duration: Int,
        aspectRatio: String,
        model: String?,
        image: String?,
        negativePrompt: String?,
        width: Int?,
        height: Int?,
        numFrames: Int?,
        frameRate: Int?,
        seed: Long?,
        numInferenceSteps: Int?,
        mode: String?,
        images: List<String>,
        extraBody: JsonObject?,
        cloudinaryCloudName: String,
        cloudinaryApiKey: String,
        cloudinaryApiSecret: String,
    ): VideoTaskSubmission? {
        val resolvedFrameRate = frameRate ?: 24
        val resolvedSize = resolveAgnesSize(aspectRatio, width, height)
        val resolvedNumFrames = normalizeAgnesFrameCount(numFrames ?: (duration * resolvedFrameRate + 1))
        val rawAgnesImageInputs = buildList {
            image?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(images.map { it.trim() }.filter { it.isNotBlank() })
        }
        val agnesImageInputs = runCatching {
            normalizeAgnesImageInputs(
                inputs = rawAgnesImageInputs,
                cloudinaryCloudName = cloudinaryCloudName,
                cloudinaryApiKey = cloudinaryApiKey,
                cloudinaryApiSecret = cloudinaryApiSecret,
            )
        }.getOrElse { error ->
            return VideoTaskSubmission(
                taskId = "",
                errorMessage = error.message ?: "Failed to prepare image input for Agnes video.",
            )
        }
        val invalidImageInput = agnesImageInputs.firstOrNull { !it.isRemoteHttpUrl() }
        if (invalidImageInput != null) {
            return VideoTaskSubmission(
                taskId = "",
                errorMessage = "Agnes image-to-video requires publicly accessible image URLs. Configure Cloudinary so local/chat images can be uploaded first.",
            )
        }
        val agnesExtraBody = extraBody?.deepCopy() ?: JsonObject()
        removeAgnesFrameAliasFields(agnesExtraBody)
        val agnesMode = normalizeAgnesMode(mode, agnesImageInputs)
        val body = JsonObject().apply {
            addProperty("model", model ?: "agnes-video-v2.0")
            addProperty("prompt", prompt)
            when {
                agnesImageInputs.size == 1 -> addProperty("image", agnesImageInputs.first())
                agnesImageInputs.size > 1 -> {
                    addProperty("image", agnesImageInputs.first())
                    agnesExtraBody.add("image", JsonArray().apply { agnesImageInputs.forEach { add(it) } })
                }
            }
            if (!agnesMode.isNullOrBlank()) addProperty("mode", agnesMode)
            if (!negativePrompt.isNullOrBlank()) addProperty("negative_prompt", negativePrompt)
            addProperty("width", resolvedSize.first)
            addProperty("height", resolvedSize.second)
            addProperty("num_frames", resolvedNumFrames)
            addProperty("frame_rate", resolvedFrameRate)
            if (seed != null) addProperty("seed", seed)
            if (numInferenceSteps != null) addProperty("num_inference_steps", numInferenceSteps)
            if (agnesExtraBody.size() > 0) add("extra_body", agnesExtraBody)
        }
        val req = Request.Builder()
            .url("$base/v1/videos")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        logSubmitRequest("Agnes", req.url.toString(), body)
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                logSubmitResponse("Agnes", resp.code, raw)
                if (!resp.isSuccessful) {
                    return@use VideoTaskSubmission(
                        taskId = "",
                        rawResponse = raw,
                        errorMessage = extractSubmitError(raw).ifBlank { "Agnes video submit failed" },
                        httpCode = resp.code,
                    )
                }
                val json = JsonParser.parseString(raw).asJsonObject
                val taskId = json["task_id"]?.asString
                    ?: json["id"]?.asString
                    ?: json["data"]?.asJsonObject?.get("task_id")?.asString
                    ?: json["data"]?.asJsonObject?.get("id")?.asString
                VideoTaskSubmission(
                    taskId = taskId.orEmpty(),
                    rawResponse = raw,
                    errorMessage = if (taskId.isNullOrBlank()) extractSubmitError(raw) else "",
                    httpCode = resp.code,
                )
            }
        }.getOrElse { error ->
            VideoTaskSubmission(taskId = "", errorMessage = error.message.orEmpty())
        }
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private suspend fun pollForCompletion(
        endpoint: String,
        apiKey: String,
        provider: VideoTaskProvider,
        taskId: String,
        maxWaitMs: Long,
    ): String? {
        val base = normalizeProviderBase(endpoint)
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            delay(10_000L)
            val snapshot = VideoTaskRuntime.pollTask(client, endpoint, apiKey, provider, taskId)
            when (snapshot.status) {
                VideoTaskStatuses.COMPLETED -> snapshot.videoUrl?.let { return it }
                VideoTaskStatuses.FAILED -> return null
            }
        }
        return null
    }

    private fun normalizeProviderBase(endpoint: String): String {
        return VideoTaskRuntime.normalizeProviderBase(endpoint)
    }

    private fun resolveAgnesSize(aspectRatio: String, width: Int?, height: Int?): Pair<Int, Int> {
        if (width != null && height != null) return width to height
        return when (aspectRatio.trim()) {
            "9:16" -> 768 to 1152
            "1:1" -> 1024 to 1024
            else -> 1152 to 768
        }
    }

    private fun normalizeAgnesFrameCount(raw: Int): Int {
        val clamped = raw.coerceIn(9, 441)
        val remainder = (clamped - 1) % 8
        return if (remainder == 0) clamped else (clamped - remainder).coerceAtLeast(9)
    }

    private fun normalizeAgnesMode(mode: String?, images: List<String>): String? {
        val normalized = mode?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        return when (normalized) {
            "keyframes", "first_last", "start_end" -> "keyframes"
            "image", "img2video", "image_to_video", "i2v", "ti2vid" -> "ti2vid"
            "text", "text2video", "t2v", "auto", null -> if (images.isNotEmpty()) "ti2vid" else null
            else -> normalized
        }
    }

    private fun removeAgnesFrameAliasFields(body: JsonObject) {
        listOf(
            "first_frame_image",
            "start_frame_image",
            "first_frame",
            "start_image",
            "last_frame_image",
            "end_frame_image",
            "last_frame",
            "end_image",
        ).forEach { body.remove(it) }
    }

    private fun parseFlexibleStringList(raw: String?): List<String> {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            JsonParser.parseString(value).takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
                if (!element.isJsonPrimitive) return@mapNotNull null
                element.asString.trim().takeIf { it.isNotBlank() }
            }
        }.getOrNull()
            ?: value.split('\n', ',').map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun parseJsonObject(raw: String?): JsonObject? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            JsonParser.parseString(value).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull()
    }

    private fun firstStringParam(params: Map<String, Any>, vararg keys: String): String? {
        keys.forEach { key ->
            val value = (params[key] as? String)?.trim()?.takeIf { it.isNotBlank() }
            if (value != null) return value
        }
        return null
    }

    private fun selectGateway(
        snapshot: com.mobileclaw.config.ConfigSnapshot,
        gatewayId: String?,
        gatewayName: String?,
    ): com.mobileclaw.config.GatewayConfig? {
        val id = gatewayId?.trim()?.takeIf { it.isNotBlank() }
        val name = gatewayName?.trim()?.takeIf { it.isNotBlank() }
        return snapshot.gateways.firstOrNull { gateway -> id != null && gateway.id == id }
            ?: snapshot.gateways.firstOrNull { gateway -> name != null && gateway.name.equals(name, ignoreCase = true) }
            ?: snapshot.activeGateway
    }

    private fun mergeJsonObject(target: JsonObject, patch: JsonObject?) {
        patch?.entrySet()?.forEach { (key, value) ->
            target.add(key, value)
        }
    }

    private fun logSubmitRequest(provider: String, url: String, body: JsonObject) {
        Log.d(VIDEO_LOG_TAG, "submit[$provider] url=$url body=${body.toString().take(6_000)}")
    }

    private fun logSubmitResponse(provider: String, httpCode: Int, raw: String) {
        val levelMessage = "submit[$provider] HTTP $httpCode response=${raw.take(6_000)}"
        if (httpCode in 200..299) {
            Log.d(VIDEO_LOG_TAG, levelMessage)
        } else {
            Log.e(VIDEO_LOG_TAG, levelMessage)
        }
    }

    private fun normalizeAgnesImageInputs(
        inputs: List<String>,
        cloudinaryCloudName: String,
        cloudinaryApiKey: String,
        cloudinaryApiSecret: String,
    ): List<String> {
        if (inputs.isEmpty()) return emptyList()
        return inputs.map { input ->
            when {
                input.isRemoteHttpUrl() -> input
                input.isDataUriImage() || input.isContentUri() || input.isLikelyLocalFilePath() -> imageUploader.upload(
                    input = input,
                    cloudinaryCloudName = cloudinaryCloudName,
                    cloudinaryApiKey = cloudinaryApiKey,
                    cloudinaryApiSecret = cloudinaryApiSecret,
                )
                else -> throw IllegalArgumentException("Unsupported Agnes image input. Use http/https URL, content URI, local file path, or data:image URI.")
            }
        }
    }

    private fun String.shouldUseLatestUserImageForVideo(): Boolean {
        val normalized = trim().lowercase()
        if (normalized.isBlank()) return false
        return listOf(
            "图生视频", "图转视频", "图片生成视频", "按图生成视频", "用图生成视频",
            "用这张图", "根据这张图", "基于这张图", "这张图片", "这个图片", "刚才的图",
            "image to video", "image-to-video", "use this image", "from this image",
        ).any { normalized.contains(it) }
    }

    private fun extractSubmitError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return runCatching {
            val json = JsonParser.parseString(trimmed)
            when {
                json.isJsonObject -> {
                    val obj = json.asJsonObject
                    listOf("message", "error", "detail", "msg").firstNotNullOfOrNull { key ->
                        obj.get(key)?.takeIf { !it.isJsonNull }?.let { value ->
                            if (value.isJsonPrimitive) value.asString else value.toString()
                        }
                    } ?: trimmed.take(300)
                }
                else -> trimmed.take(300)
            }
        }.getOrDefault(trimmed.take(300))
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private fun downloadVideo(url: String, prompt: String): File? {
        return VideoTaskRuntime.downloadVideo(
            client = client,
            outputDir = File(context.filesDir, "videos"),
            url = url,
            prompt = prompt,
        )
    }

}
