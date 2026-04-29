package com.mobileclaw.skill.builtin

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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GenerateImageSkill(private val config: AgentConfig) : Skill {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override val meta = SkillMeta(
        id = "generate_image",
        name = "Generate Image",
        description = "Generates an image using the AI image generation API and displays it in the chat. " +
            "Use for creating illustrations, diagrams, charts, or any visual content. " +
            "Returns the image directly without saving to a file.",
        parameters = listOf(
            SkillParam("prompt", "string", "Detailed description of the image to generate"),
            SkillParam("model", "string", "Image model to use. E.g. 'dall-e-3', 'dall-e-2', 'flux-pro'. Default: dall-e-3", required = false),
            SkillParam("size", "string", "Image dimensions: '1024x1024', '1024x1792', or '1792x1024'. Default: '1024x1024'", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val prompt = params["prompt"] as? String
            ?: return@withContext SkillResult(false, "prompt is required")
        val size = params["size"] as? String ?: "1024x1024"
        val snapshot = config.snapshot()

        if (snapshot.endpoint.isBlank() || snapshot.apiKey.isBlank()) {
            return@withContext SkillResult(false, "LLM endpoint or API key not configured")
        }

        // Normalize endpoint: strip trailing /v1 or /v1/ to get base URL, then reconstruct
        val base = snapshot.endpoint.trimEnd('/').removeSuffix("/v1")
        val imageUrl = "$base/v1/images/generations"

        // Determine model: use param, fall back to dall-e-3
        val model = (params["model"] as? String)?.takeIf { it.isNotBlank() } ?: "dall-e-3"

        val bodyJson = JsonObject().apply {
            addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("n", 1)
            addProperty("size", size)
            addProperty("response_format", "b64_json")
        }

        val request = Request.Builder()
            .url(imageUrl)
            .header("Authorization", "Bearer ${snapshot.apiKey}")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val body = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            cont.resume(SkillResult(false,
                                "Image API error ${response.code} at $imageUrl\n" +
                                "Model: $model\n" +
                                "Response: ${body.take(500)}"
                            ))
                            return
                        }
                        val json = JsonParser.parseString(body).asJsonObject

                        // Try b64_json first, then url (some providers return url instead)
                        val dataObj = json["data"]?.asJsonArray?.get(0)?.asJsonObject
                        val b64 = dataObj?.get("b64_json")?.asString
                        val imageUrl2 = dataObj?.get("url")?.asString

                        when {
                            b64 != null -> {
                                val dataUri = "data:image/png;base64,$b64"
                                cont.resume(SkillResult(
                                    success = true,
                                    output = "Image generated. Model: $model. Prompt: $prompt",
                                    imageBase64 = dataUri,
                                    data = SkillAttachment.ImageData(dataUri, prompt),
                                ))
                            }
                            imageUrl2 != null -> {
                                // Provider returned a URL — fetch and convert to base64
                                fetchImageAsBase64(imageUrl2)?.let { dataUri ->
                                    cont.resume(SkillResult(
                                        success = true,
                                        output = "Image generated. Model: $model. Prompt: $prompt",
                                        imageBase64 = dataUri,
                                        data = SkillAttachment.ImageData(dataUri, prompt),
                                    ))
                                } ?: cont.resume(SkillResult(true,
                                    "Image generated at: $imageUrl2 (could not fetch for inline display)",
                                ))
                            }
                            else -> cont.resume(SkillResult(false,
                                "No image data in API response. Check that model '$model' supports image generation.\nResponse: ${body.take(300)}"
                            ))
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun fetchImageAsBase64(url: String): String? {
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: return null
                val mime = resp.body?.contentType()?.toString()?.substringBefore(";") ?: "image/png"
                "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        }.getOrNull()
    }
}
