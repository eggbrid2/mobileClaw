package com.mobileclaw.skill.builtin

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.BuildConfig
import com.mobileclaw.ClawApplication
import com.mobileclaw.config.UserConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private const val PGYER_API_KEY = "pgyer_api_key"
private const val PGYER_APP_KEY = "pgyer_app_key"
private const val PGYER_INSTALL_PASSWORD = "pgyer_install_password"
private const val PGYER_UPLOAD_URL = "https://upload.pgyer.com/apiv2/app/upload"
private const val PGYER_CHECK_URL = "https://www.pgyer.com/apiv2/app/check"

class PgyerReleaseSkill(
    private val app: ClawApplication,
    private val userConfig: UserConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
) : Skill {
    override val meta = SkillMeta(
        id = "pgyer_release",
        name = "Pgyer Release",
        nameZh = "蒲公英发布",
        description = "Checks MobileClaw updates from Pgyer, downloads the latest APK, or uploads an APK to Pgyer. " +
            "Requires user config pgyer_api_key and pgyer_app_key for check/download; upload requires pgyer_api_key and apk_path.",
        descriptionZh = "检查蒲公英上的 MobileClaw 更新、下载最新 APK，或上传 APK 到蒲公英。检查/下载需要 pgyer_api_key 与 pgyer_app_key；上传需要 pgyer_api_key 与 apk_path。",
        parameters = listOf(
            SkillParam("action", "string", "status | check_update | download | upload", required = false),
            SkillParam("api_key", "string", "Optional Pgyer API key override. Defaults to user config pgyer_api_key.", required = false),
            SkillParam("app_key", "string", "Optional Pgyer appKey override. Defaults to user config pgyer_app_key.", required = false),
            SkillParam("apk_path", "string", "Local APK path for action=upload.", required = false),
            SkillParam("update_description", "string", "Release notes for action=upload.", required = false),
            SkillParam("install_type", "number", "Pgyer install type. 1 public, 2 password, 3 invite. Default 1.", required = false),
            SkillParam("install_password", "string", "Optional install password for password-protected Pgyer releases.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        categories = listOf(SkillToolCategory.SYSTEM, SkillToolCategory.CODE),
        tags = listOf("pgyer", "蒲公英", "release", "update", "download", "apk"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        when ((params["action"] as? String)?.lowercase()?.ifBlank { "check_update" } ?: "check_update") {
            "status" -> status(params)
            "check", "check_update", "update" -> checkUpdate(params)
            "download" -> downloadLatest(params)
            "upload" -> uploadApk(params)
            else -> SkillResult(false, "Unsupported action. Use status, check_update, download, or upload.")
        }
    }

    private suspend fun apiKey(params: Map<String, Any>): String =
        (params["api_key"] as? String)?.trim()?.ifBlank { null }
            ?: userConfig.get(PGYER_API_KEY)?.trim().orEmpty()

    private suspend fun appKey(params: Map<String, Any>): String =
        (params["app_key"] as? String)?.trim()?.ifBlank { null }
            ?: userConfig.get(PGYER_APP_KEY)?.trim().orEmpty()

    private suspend fun status(params: Map<String, Any>): SkillResult {
        val hasApiKey = apiKey(params).isNotBlank()
        val hasAppKey = appKey(params).isNotBlank()
        return SkillResult(
            true,
            buildString {
                appendLine("Pgyer release config:")
                appendLine("- pgyer_api_key: ${if (hasApiKey) "configured" else "missing"}")
                appendLine("- pgyer_app_key: ${if (hasAppKey) "configured" else "missing"}")
                appendLine("- current MobileClaw version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("- git: ${BuildConfig.GIT_VERSION} / ${BuildConfig.GIT_BRANCH} / ${BuildConfig.GIT_COMMIT}")
            }.trim(),
        )
    }

    private suspend fun checkUpdate(params: Map<String, Any>): SkillResult {
        val apiKey = apiKey(params)
        val appKey = appKey(params)
        if (apiKey.isBlank() || appKey.isBlank()) {
            return SkillResult(false, "Configure pgyer_api_key and pgyer_app_key first.")
        }
        val json = postPgyerCheck(apiKey, appKey)
            ?: return SkillResult(false, "Pgyer check_update returned an empty response.")
        val data = json["data"]?.asJsonObject ?: json
        val hasNew = data.boolString("buildHaveNewVersion") == "true" ||
            data.string("buildVersionNo").toIntOrNull().let { it != null && it > BuildConfig.VERSION_CODE }
        val downloadUrl = data.string("downloadURL").ifBlank { data.fallbackInstallUrl() }
        val remoteVersion = data.string("buildVersion").ifBlank { data.string("buildVersionNo") }
        return SkillResult(
            true,
            buildString {
                appendLine(if (hasNew) "Pgyer has a newer MobileClaw build." else "No newer Pgyer build detected.")
                appendLine("Current: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Git: ${BuildConfig.GIT_VERSION} / ${BuildConfig.GIT_COMMIT}")
                if (remoteVersion.isNotBlank()) appendLine("Remote: $remoteVersion")
                if (downloadUrl.isNotBlank()) appendLine("Download: $downloadUrl")
                data.string("buildUpdateDescription").takeIf { it.isNotBlank() }?.let { appendLine("Notes: $it") }
            }.trim(),
        )
    }

    private suspend fun downloadLatest(params: Map<String, Any>): SkillResult {
        val apiKey = apiKey(params)
        val appKey = appKey(params)
        if (apiKey.isBlank() || appKey.isBlank()) {
            return SkillResult(false, "Configure pgyer_api_key and pgyer_app_key first.")
        }
        val data = postPgyerCheck(apiKey, appKey)?.get("data")?.asJsonObject
            ?: return SkillResult(false, "Pgyer did not return build data.")
        val url = data.string("downloadURL").ifBlank { data.fallbackInstallUrl() }
        if (url.isBlank()) return SkillResult(false, "Pgyer did not return a downloadable URL.")
        val version = data.string("buildVersion").ifBlank { data.string("buildVersionNo").ifBlank { "latest" } }
        val fileName = "MobileClaw-$version.apk".replace(Regex("""[^\w.\-]+"""), "_")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("MobileClaw $version")
            .setDescription("Downloading MobileClaw from Pgyer")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val manager = app.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
        return SkillResult(true, "Pgyer download started. Download id=$id, file=Downloads/$fileName\n$url")
    }

    private suspend fun uploadApk(params: Map<String, Any>): SkillResult {
        val apiKey = apiKey(params)
        val apkPath = (params["apk_path"] as? String)?.trim().orEmpty()
        if (apiKey.isBlank()) return SkillResult(false, "Configure pgyer_api_key or pass api_key.")
        if (apkPath.isBlank()) return SkillResult(false, "apk_path is required for upload.")
        val apk = File(apkPath)
        if (!apk.exists() || !apk.isFile) return SkillResult(false, "APK does not exist: $apkPath")
        val installType = (params["install_type"] as? Number)?.toInt() ?: 1
        val password = (params["install_password"] as? String)?.trim()?.ifBlank { null }
            ?: userConfig.get(PGYER_INSTALL_PASSWORD)?.trim().orEmpty()
        val description = (params["update_description"] as? String)?.trim().orEmpty()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("_api_key", apiKey)
            .addFormDataPart("buildInstallType", installType.toString())
            .addFormDataPart("buildUpdateDescription", description)
            .apply {
                if (password.isNotBlank()) addFormDataPart("buildPassword", password)
            }
            .addFormDataPart("file", apk.name, apk.asRequestBody("application/vnd.android.package-archive".toMediaType()))
            .build()
        val req = Request.Builder().url(PGYER_UPLOAD_URL).post(body).build()
        return executePgyerRequest(req, "Pgyer upload completed.")
    }

    private fun postPgyerCheck(apiKey: String, appKey: String): JsonObject? {
        val body = FormBody.Builder()
            .add("_api_key", apiKey)
            .add("appKey", appKey)
            .add("buildVersion", BuildConfig.VERSION_NAME)
            .build()
        val req = Request.Builder().url(PGYER_CHECK_URL).post(body).build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return null
                JsonParser.parseString(text).asJsonObject
            }
        }.getOrNull()
    }

    private fun executePgyerRequest(req: Request, fallback: String): SkillResult =
        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return SkillResult(false, "Pgyer HTTP ${resp.code}: ${text.take(2000)}")
                val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                val code = json?.get("code")?.asInt ?: 0
                val message = json?.get("message")?.asString.orEmpty()
                val data = json?.get("data")?.asJsonObject
                val output = buildString {
                    appendLine(message.ifBlank { fallback })
                    data?.string("buildKey")?.takeIf { it.isNotBlank() }?.let { appendLine("Build key: $it") }
                    data?.string("buildVersion")?.takeIf { it.isNotBlank() }?.let { appendLine("Version: $it") }
                    data?.fallbackInstallUrl()?.takeIf { it.isNotBlank() }?.let { appendLine("Install: $it") }
                }.trim()
                SkillResult(code == 0, output.ifBlank { text.take(12000) })
            }
        }.getOrElse {
            SkillResult(false, "Pgyer request failed: ${it.message}")
        }
}

private fun JsonObject.string(key: String): String =
    runCatching { get(key)?.asString.orEmpty() }.getOrDefault("")

private fun JsonObject.boolString(key: String): String =
    string(key).lowercase()

private fun JsonObject.fallbackInstallUrl(): String {
    string("buildShortcutUrl").takeIf { it.isNotBlank() }?.let { return "https://www.pgyer.com/$it" }
    string("buildQRCodeURL").takeIf { it.isNotBlank() }?.let { return it }
    return ""
}
