package com.mobileclaw.skill.builtin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.config.UserConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val CODEX_DESKTOP_ENDPOINT = "codex_desktop_endpoint"
private const val CODEX_DESKTOP_TOKEN = "codex_desktop_token"
private const val CODEX_DESKTOP_CWD = "codex_desktop_cwd"
private const val CODEX_DESKTOP_MODEL = "codex_desktop_model"
private const val CODEX_DESKTOP_PROVIDER = "codex_desktop_provider"
private const val CODEX_DESKTOP_APPROVAL = "codex_desktop_approval"
private const val CODEX_DESKTOP_SANDBOX = "codex_desktop_sandbox"

/**
 * Calls a LAN bridge running on the user's desktop so the phone agent can start
 * and inspect Codex tasks without requiring Codex to run on Android.
 */
class CodexDesktopSkill(
    private val userConfig: UserConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) : Skill {

    private val gson = Gson()

    override val meta = SkillMeta(
        id = "codex_desktop",
        name = "Codex Desktop Bridge",
        nameZh = "电脑 Codex 桥接",
        description = "Controls a Codex CLI bridge running on the user's desktop over LAN. " +
            "Use action=status to verify the bridge, action=run to send a coding task, or action=stop to cancel the active desktop Codex process. " +
            "Requires user config keys codex_desktop_endpoint and codex_desktop_token.",
        descriptionZh = "通过局域网控制电脑上的 Codex CLI 桥接服务。status 检查连接，run 发送编程任务，stop 停止电脑端 Codex 进程。需要先配置 codex_desktop_endpoint 和 codex_desktop_token。",
        parameters = listOf(
            SkillParam("action", "string", "status | run | stop", required = false),
            SkillParam("prompt", "string", "Task prompt to send to desktop Codex when action=run.", required = false),
            SkillParam("cwd", "string", "Optional desktop working directory. Defaults to codex_desktop_cwd config.", required = false),
            SkillParam("model", "string", "Optional Codex model override. Defaults to codex_desktop_model config.", required = false),
            SkillParam("provider", "string", "Optional Codex provider override. Defaults to codex_desktop_provider config.", required = false),
            SkillParam("approval", "string", "Optional Codex approval policy override.", required = false),
            SkillParam("sandbox", "string", "Optional Codex sandbox override.", required = false),
            SkillParam("timeout_seconds", "number", "Optional bridge-side timeout for action=run. Default 120.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        categories = listOf(SkillToolCategory.CODE),
        tags = listOf("codex", "desktop", "code", "电脑", "代码"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val endpoint = userConfig.get(CODEX_DESKTOP_ENDPOINT)
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()
        val token = userConfig.get(CODEX_DESKTOP_TOKEN)?.trim().orEmpty()
        if (endpoint.isBlank() || token.isBlank()) {
            return@withContext SkillResult(
                false,
                "Codex desktop bridge is not configured. Set $CODEX_DESKTOP_ENDPOINT to the desktop bridge URL, " +
                    "for example http://192.168.1.23:52734, and set $CODEX_DESKTOP_TOKEN to the bridge token.",
            )
        }

        when ((params["action"] as? String)?.lowercase()?.ifBlank { "run" } ?: "run") {
            "status", "health" -> getHealth(endpoint, token)
            "stop", "cancel" -> postJson(endpoint, token, "/stop", JsonObject())
            "run", "send" -> {
                val prompt = params["prompt"] as? String
                    ?: return@withContext SkillResult(false, "prompt is required for action=run")
                val body = JsonObject().apply {
                    addProperty("prompt", prompt)
                    val cwd = (params["cwd"] as? String)?.ifBlank { null } ?: userConfig.get(CODEX_DESKTOP_CWD).orEmpty()
                    addProperty("cwd", cwd)
                    add("config", JsonObject().apply {
                        addProperty("cwd", cwd)
                        addProperty("model", (params["model"] as? String)?.ifBlank { null } ?: userConfig.get(CODEX_DESKTOP_MODEL).orEmpty())
                        addProperty("provider", (params["provider"] as? String)?.ifBlank { null } ?: userConfig.get(CODEX_DESKTOP_PROVIDER).orEmpty())
                        addProperty("approval", (params["approval"] as? String)?.ifBlank { null } ?: userConfig.get(CODEX_DESKTOP_APPROVAL).orEmpty())
                        addProperty("sandbox", (params["sandbox"] as? String)?.ifBlank { null } ?: userConfig.get(CODEX_DESKTOP_SANDBOX).orEmpty())
                    })
                    addProperty("timeout_seconds", (params["timeout_seconds"] as? Number)?.toInt() ?: 120)
                }
                postJson(endpoint, token, "/run", body)
            }
            else -> SkillResult(false, "Unsupported action. Use status, run, or stop.")
        }
    }

    private fun getHealth(endpoint: String, token: String): SkillResult {
        val req = Request.Builder()
            .url("$endpoint/health")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return executeRequest(req, "Codex bridge is reachable.")
    }

    private fun postJson(endpoint: String, token: String, path: String, body: JsonObject): SkillResult {
        val req = Request.Builder()
            .url("$endpoint$path")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return executeRequest(req, "Codex bridge request completed.")
    }

    private fun executeRequest(req: Request, fallback: String): SkillResult =
        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return SkillResult(false, "Codex bridge HTTP ${resp.code}: ${text.take(2000)}")
                }
                val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                val ok = json?.get("ok")?.asBoolean ?: true
                val output = json?.get("output")?.asString
                    ?: json?.get("message")?.asString
                    ?: text.ifBlank { fallback }
                SkillResult(ok, output.take(12000))
            }
        }.getOrElse {
            SkillResult(false, "Codex bridge request failed: ${it.message}")
        }
}
