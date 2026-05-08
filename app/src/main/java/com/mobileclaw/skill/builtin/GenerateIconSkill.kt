package com.mobileclaw.skill.builtin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.config.UserConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Generates app icons using configurable Chinese domestic AI image APIs.
 *
 * Supported providers (configured via user_config key "icon_api_provider"):
 *   - dashscope  (通义万象 / Wanx) — default  endpoint: https://dashscope.aliyuncs.com
 *   - cogview    (智谱 CogView)               endpoint: https://open.bigmodel.cn
 *   - openai     (OpenAI / compatible)        uses image_api_endpoint or main endpoint
 *
 * Required user_config keys:
 *   icon_api_provider  — "dashscope" | "cogview" | "openai" (default: dashscope)
 *   icon_api_key       — API key for the icon provider
 *   icon_api_endpoint  — (optional) custom endpoint override
 *
 * Output: 512×512 PNG with rounded corners, saved to filesDir/icons/.
 * Can optionally be applied to a mini-app via apply_to_app.
 */
class GenerateIconSkill(
    private val context: Context,
    private val userConfig: UserConfig,
    private val miniAppStore: MiniAppStore,
) : Skill {

    private val client = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override val meta = SkillMeta(
        id = "generate_icon",
        name = "Generate Icon",
        nameZh = "生成图标",
        description = "Generates a 512×512 app icon using configurable Chinese domestic AI APIs " +
            "(DashScope/Wanx, CogView, or any OpenAI-compatible image API). " +
            "Requires user_config key icon_api_key; optionally icon_api_provider (dashscope|cogview|openai) and icon_api_endpoint. " +
            "Use apply_to_app to set the icon on an existing mini-app.",
        descriptionZh = "使用可配置的国内 AI 图像接口生成 512×512 应用图标（支持通义万象、CogView、OpenAI 兼容接口）。" +
            "需要在 user_config 中设置 icon_api_key。可用 apply_to_app 直接为迷你应用设置图标。",
        parameters = listOf(
            SkillParam("prompt", "string", "Description of the icon to generate, e.g. 'a minimalist calendar icon with blue gradient'"),
            SkillParam("apply_to_app", "string", "App ID to apply the generated icon to (optional)", required = false),
            SkillParam("style", "string", "Icon style hint: 'flat' | 'gradient' | '3d' | 'sketch' (optional, default: flat)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("创作"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val prompt = params["prompt"] as? String
            ?: return@withContext SkillResult(false, "prompt parameter is required")
        val applyToApp = params["apply_to_app"] as? String
        val style = params["style"] as? String ?: "flat"

        val apiKey = userConfig.get("icon_api_key")?.trim()
            ?: return@withContext SkillResult(false,
                "icon_api_key not configured. Set via: user_config(action=set, key=icon_api_key, value=YOUR_KEY)")

        val provider = userConfig.get("icon_api_provider")?.trim()?.lowercase() ?: "dashscope"
        val customEndpoint = userConfig.get("icon_api_endpoint")?.trim()

        // Build a detailed icon prompt
        val fullPrompt = "App icon, $style style, $prompt, square format, clean design, no text, high quality"

        val imageBytes = when (provider) {
            "dashscope" -> generateDashScope(apiKey, customEndpoint, fullPrompt)
            "cogview"   -> generateCogView(apiKey, customEndpoint, fullPrompt)
            else        -> generateOpenAiCompat(apiKey, customEndpoint, fullPrompt)
        } ?: return@withContext SkillResult(false,
            "Failed to generate icon from provider '$provider'. Check icon_api_key and icon_api_endpoint.")

        // Post-process: resize to 512×512 with rounded corners
        val processed = processIcon(imageBytes)
            ?: return@withContext SkillResult(false, "Failed to process generated image")

        // Save to filesDir/icons/
        val iconsDir = File(context.filesDir, "icons").also { it.mkdirs() }
        val safeLabel = prompt.take(24).replace(Regex("[^\\w\\s-]"), "").trim().replace(" ", "_").ifBlank { "icon" }
        val outFile = File(iconsDir, "${safeLabel}_${System.currentTimeMillis()}.png")
        outFile.writeBytes(processed)

        // Optionally apply to app
        if (applyToApp != null) {
            val app = miniAppStore.get(applyToApp)
            if (app != null) {
                miniAppStore.updateIcon(applyToApp, outFile.absolutePath)
            } else {
                return@withContext SkillResult(false,
                    "Icon saved to ${outFile.absolutePath} but app '$applyToApp' not found.")
            }
        }

        val b64 = Base64.encodeToString(processed, Base64.NO_WRAP)
        val dataUri = "data:image/png;base64,$b64"

        val appNote = if (applyToApp != null) " 已应用到应用 '$applyToApp'。" else " 使用 apply_to_app 参数将其设置为某个应用的图标。"
        SkillResult(
            success = true,
            output = "图标已生成：${outFile.name}$appNote",
            imageBase64 = dataUri,
            data = com.mobileclaw.skill.SkillAttachment.ImageData(dataUri, prompt),
        )
    }

    // ── Provider implementations ──────────────────────────────────────────────

    private fun generateDashScope(apiKey: String, customEndpoint: String?, prompt: String): ByteArray? {
        val base = customEndpoint?.trimEnd('/') ?: "https://dashscope.aliyuncs.com"
        // DashScope Wanx image generation (async submit → poll)
        val submitBody = JsonObject().apply {
            addProperty("model", "wanx-v1")
            add("input", JsonObject().apply { addProperty("prompt", prompt) })
            add("parameters", JsonObject().apply {
                addProperty("style", "<flat design>")
                addProperty("size", "512*512")
                addProperty("n", 1)
            })
        }
        val submitReq = Request.Builder()
            .url("$base/api/v1/services/aigc/text2image/image-synthesis")
            .header("Authorization", "Bearer $apiKey")
            .header("X-DashScope-Async", "enable")
            .post(submitBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val taskId = runCatching {
            client.newCall(submitReq).execute().use { resp ->
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                json["output"]?.asJsonObject?.get("task_id")?.asString
            }
        }.getOrNull() ?: return null

        // Poll for result (up to 3 minutes)
        val deadline = System.currentTimeMillis() + 180_000L
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(4_000)
            val result = runCatching {
                val pollReq = Request.Builder()
                    .url("$base/api/v1/tasks/$taskId")
                    .header("Authorization", "Bearer $apiKey")
                    .get().build()
                client.newCall(pollReq).execute().use { resp ->
                    val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                    val output = json["output"]?.asJsonObject ?: return@use null
                    val status = output["task_status"]?.asString ?: ""
                    if (status == "SUCCEEDED") {
                        val url = output["results"]?.asJsonArray?.get(0)?.asJsonObject?.get("url")?.asString
                        url?.let { fetchBytes(it) }
                    } else if (status == "FAILED") null
                    else null // still running
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun generateCogView(apiKey: String, customEndpoint: String?, prompt: String): ByteArray? {
        val base = customEndpoint?.trimEnd('/') ?: "https://open.bigmodel.cn"
        val body = JsonObject().apply {
            addProperty("model", "cogview-3-flash")
            addProperty("prompt", prompt)
            addProperty("size", "512x512")
        }
        val req = Request.Builder()
            .url("$base/api/paas/v4/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                val url = json["data"]?.asJsonArray?.get(0)?.asJsonObject?.get("url")?.asString
                    ?: return@use null
                fetchBytes(url)
            }
        }.getOrNull()
    }

    private fun generateOpenAiCompat(apiKey: String, customEndpoint: String?, prompt: String): ByteArray? {
        val base = customEndpoint?.trimEnd('/') ?: return null
        val body = JsonObject().apply {
            addProperty("model", "dall-e-3")
            addProperty("prompt", prompt)
            addProperty("n", 1)
            addProperty("size", "1024x1024")
            addProperty("response_format", "b64_json")
        }
        val req = Request.Builder()
            .url("$base/v1/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val json = JsonParser.parseString(resp.body?.string()).asJsonObject
                val b64 = json["data"]?.asJsonArray?.get(0)?.asJsonObject?.get("b64_json")?.asString
                    ?: json["data"]?.asJsonArray?.get(0)?.asJsonObject?.get("url")?.asString?.let { fetchBytes(it); return@use null }
                    ?: return@use null
                Base64.decode(b64, Base64.DEFAULT)
            }
        }.getOrNull()
    }

    private fun fetchBytes(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { it.body?.bytes() }
    }.getOrNull()

    // ── Image post-processing ─────────────────────────────────────────────────

    private fun processIcon(imageBytes: ByteArray): ByteArray? = runCatching {
        val src = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        val size = 512
        val radius = size * 0.22f   // ~iOS icon corner radius

        // Scale to 512×512
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)

        // Apply rounded corners via Porter-Duff
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        scaled.recycle()

        val out = java.io.ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.PNG, 100, out)
        output.recycle()
        out.toByteArray()
    }.getOrNull()
}
