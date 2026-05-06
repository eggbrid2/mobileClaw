package com.mobileclaw.skill.builtin

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GenerateImageSkill(
    private val config: AgentConfig,
    private val userConfig: com.mobileclaw.config.UserConfig? = null,
) : Skill {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override val meta = SkillMeta(
        id = "generate_image",
        name = "Generate Image",
        description = "Generates an image using an AI image generation API and displays it in the chat. " +
            "Supported providers (set via user_config key 'image_api_endpoint'): " +
            "SiliconFlow (https://api.siliconflow.cn), Together.ai (https://api.together.xyz), OpenAI (https://api.openai.com). " +
            "Use model='pollinations' for a free no-key-needed option (Pollinations.ai). " +
            "Set 'image_api_key' in user_config if the image provider uses a different key from the LLM.",
        parameters = listOf(
            SkillParam("prompt", "string", "Detailed description of the image to generate"),
            SkillParam(
                "model", "string",
                "Model to use. " +
                    "SiliconFlow: 'black-forest-labs/FLUX.1-schnell' (free) or 'black-forest-labs/FLUX.1-dev'. " +
                    "Together.ai: 'black-forest-labs/FLUX.1-schnell-Free'. " +
                    "OpenAI: 'dall-e-3' or 'dall-e-2'. " +
                    "Free no-key option: 'pollinations'. " +
                    "Default: dall-e-3",
                required = false,
            ),
            SkillParam("size", "string", "Image dimensions: '1024x1024', '1024x1792', '1792x1024'. Default: '1024x1024'", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "生成图片",
        descriptionZh = "通过 AI 模型生成图片并在聊天中展示。支持 SiliconFlow / Together.ai / OpenAI / Pollinations (免费无需Key)。",
        tags = listOf("创作"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val prompt = params["prompt"] as? String
            ?: return@withContext SkillResult(false, "prompt is required")
        val size = params["size"] as? String ?: "1024x1024"
        val model = (params["model"] as? String)?.takeIf { it.isNotBlank() } ?: "dall-e-3"

        // Pollinations.ai: free, no API key needed
        if (model == "pollinations" || model == "pollinations-flux") {
            return@withContext generateViaPollinatins(prompt, size)
        }

        val snapshot = config.snapshot()
        if (snapshot.endpoint.isBlank() || snapshot.apiKey.isBlank()) {
            return@withContext SkillResult(false, "LLM endpoint or API key not configured")
        }

        // image_api_endpoint: dedicated endpoint for image generation (optional)
        // Falls back to the LLM base endpoint. Claude/Gemini don't support images —
        // set this to SiliconFlow/Together.ai/OpenAI if your LLM provider doesn't have images.
        val imageBase = userConfig?.get("image_api_endpoint")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.endpoint.trimEnd('/').removeSuffix("/v1")
        val imageUrl = "$imageBase/v1/images/generations"

        // image_api_key: separate API key for image provider (optional, falls back to LLM key)
        val imageApiKey = userConfig?.get("image_api_key")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.apiKey

        val bodyJson = JsonObject().apply {
            addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("n", 1)
            addProperty("size", size)
            addProperty("response_format", "b64_json")
        }

        val request = Request.Builder()
            .url(imageUrl)
            .header("Authorization", "Bearer $imageApiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val hint = buildProviderHint(response.code, imageBase, model)
                    return@withContext SkillResult(
                        false,
                        "Image API error ${response.code}\nEndpoint: $imageUrl\nModel: $model\nResponse: ${body.take(300)}$hint",
                    )
                }
                val json = JsonParser.parseString(body).asJsonObject
                val dataObj = json["data"]?.asJsonArray?.get(0)?.asJsonObject
                val b64 = dataObj?.get("b64_json")?.asString
                val imgUrl = dataObj?.get("url")?.asString

                when {
                    b64 != null -> {
                        val dataUri = "data:image/png;base64,$b64"
                        SkillResult(
                            success = true,
                            output = "图片已生成。模型: $model，提示词: $prompt",
                            imageBase64 = dataUri,
                            data = SkillAttachment.ImageData(dataUri, prompt),
                        )
                    }
                    imgUrl != null -> {
                        fetchImageAsBase64(imgUrl)?.let { dataUri ->
                            SkillResult(
                                success = true,
                                output = "图片已生成。模型: $model，提示词: $prompt",
                                imageBase64 = dataUri,
                                data = SkillAttachment.ImageData(dataUri, prompt),
                            )
                        } ?: SkillResult(true, "图片已生成: $imgUrl (无法内联展示，请点击链接查看)")
                    }
                    else -> SkillResult(
                        false,
                        "API 响应中无图片数据，请确认模型 '$model' 支持图片生成。\nResponse: ${body.take(300)}",
                    )
                }
            }
        }.getOrElse { e ->
            val isTimeout = e is java.net.SocketTimeoutException
            SkillResult(
                false,
                if (isTimeout)
                    "图片生成超时 (90s)。请检查网络或考虑使用免费的 Pollinations 方案 (model=pollinations)。"
                else
                    "图片生成失败: ${e.message}\n💡 如果你的 LLM 端点不支持图片生成，请在 user_config 中设置 image_api_endpoint。",
            )
        }
    }

    private fun generateViaPollinatins(prompt: String, size: String): SkillResult {
        val (w, h) = size.split("x").let {
            (it.getOrNull(0)?.toIntOrNull() ?: 1024) to (it.getOrNull(1)?.toIntOrNull() ?: 1024)
        }
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val url = "https://image.pollinations.ai/prompt/$encodedPrompt?model=flux&width=$w&height=$h&nologo=true"
        return runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return SkillResult(false, "Pollinations.ai error ${resp.code}")
                }
                val bytes = resp.body?.bytes()
                    ?: return SkillResult(false, "Pollinations.ai 返回空响应")
                val mime = resp.body?.contentType()?.toString()?.substringBefore(";") ?: "image/jpeg"
                val dataUri = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                SkillResult(
                    success = true,
                    output = "图片已生成 (Pollinations.ai FLUX)。提示词: $prompt",
                    imageBase64 = dataUri,
                    data = SkillAttachment.ImageData(dataUri, prompt),
                )
            }
        }.getOrElse { e ->
            SkillResult(false, "Pollinations.ai 失败: ${e.message}")
        }
    }

    private fun fetchImageAsBase64(url: String): String? = runCatching {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: return null
            val mime = resp.body?.contentType()?.toString()?.substringBefore(";") ?: "image/png"
            "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }.getOrNull()

    private fun buildProviderHint(code: Int, base: String, model: String): String {
        val isLikelyClaude = "anthropic" in base || "claude" in base
        val isLikelyGemini = "google" in base || "gemini" in base || "generativelanguage" in base
        val noImageSupport = isLikelyClaude || isLikelyGemini

        return when {
            noImageSupport ->
                "\n\n💡 ${if (isLikelyClaude) "Claude" else "Gemini"} 端点不支持图片生成。请选择以下任一方案：\n" +
                    "1. 免费无需配置：让 AI 使用 model=pollinations\n" +
                    "2. SiliconFlow (免费FLUX)：user_config 设置 image_api_endpoint=https://api.siliconflow.cn，image_api_key=你的Key\n" +
                    "3. Together.ai (免费FLUX)：image_api_endpoint=https://api.together.xyz，model=black-forest-labs/FLUX.1-schnell-Free\n" +
                    "4. OpenAI DALL-E：image_api_endpoint=https://api.openai.com，model=dall-e-3"
            code == 503 ->
                "\n\n💡 503 通常表示该端点不支持图片生成，请配置 image_api_endpoint。"
            code == 401 || code == 403 ->
                "\n\n💡 认证失败。如果图片 API 使用独立的 Key，请在 user_config 设置 image_api_key。"
            code == 404 ->
                "\n\n💡 端点不存在，请确认 image_api_endpoint 和模型名称正确。"
            else -> ""
        }
    }
}
