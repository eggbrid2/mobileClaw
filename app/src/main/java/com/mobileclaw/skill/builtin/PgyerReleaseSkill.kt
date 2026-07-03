package com.mobileclaw.skill.builtin

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
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
private const val PGYER_USER_KEY = "pgyer_user_key"
private const val PGYER_INSTALL_PASSWORD = "pgyer_install_password"
private const val PGYER_UPLOAD_URL = "https://upload.pgyer.com/apiv2/app/upload"
private const val PGYER_CHECK_URL = "https://www.pgyer.com/apiv2/app/check"

data class PgyerUpdateInfo(
    val hasNewVersion: Boolean,
    val currentVersion: String,
    val currentVersionCode: Int,
    val remoteVersion: String,
    val remoteVersionCode: Int?,
    val downloadUrl: String,
    val installUrl: String,
    val releaseNotes: String,
)

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
        name = "App Update",
        nameZh = "应用更新",
        description = "Checks MobileClaw updates, downloads the latest APK, or uploads an APK to the configured release channel.",
        descriptionZh = "检查 MobileClaw 更新、下载最新 APK，或上传 APK 到已配置的发布通道。",
        parameters = listOf(
            SkillParam("action", "string", "status | check_update | download | upload", required = false),
            SkillParam("api_key", "string", "Optional release channel API key override.", required = false),
            SkillParam("app_key", "string", "Optional release channel app key override.", required = false),
            SkillParam("user_key", "string", "Optional release channel user key override.", required = false),
            SkillParam("apk_path", "string", "Local APK path for action=upload.", required = false),
            SkillParam("update_description", "string", "Release notes for action=upload.", required = false),
            SkillParam("install_type", "number", "Install type. 1 public, 2 password, 3 invite. Default 1.", required = false),
            SkillParam("install_password", "string", "Optional install password for password-protected releases.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        categories = listOf(SkillToolCategory.SYSTEM, SkillToolCategory.CODE),
        tags = listOf("release", "update", "download", "apk"),
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
                .ifBlank { BuildConfig.PGYER_API_KEY.trim() }

    private suspend fun appKey(params: Map<String, Any>): String =
        (params["app_key"] as? String)?.trim()?.ifBlank { null }
            ?: userConfig.get(PGYER_APP_KEY)?.trim().orEmpty()
                .ifBlank { BuildConfig.PGYER_APP_KEY.trim() }

    private suspend fun userKey(params: Map<String, Any>): String =
        (params["user_key"] as? String)?.trim()?.ifBlank { null }
            ?: userConfig.get(PGYER_USER_KEY)?.trim().orEmpty()
                .ifBlank { BuildConfig.PGYER_USER_KEY.trim() }

    private suspend fun status(params: Map<String, Any>): SkillResult {
        val hasApiKey = apiKey(params).isNotBlank()
        val hasAppKey = appKey(params).isNotBlank()
        val hasUserKey = userKey(params).isNotBlank()
        return SkillResult(
            true,
            buildString {
                appendLine("Release channel config:")
                appendLine("- api_key: ${if (hasApiKey) "configured" else "missing"}")
                appendLine("- app_key: ${if (hasAppKey) "configured" else "missing"}")
                appendLine("- user_key: ${if (hasUserKey) "configured" else "missing"}")
                appendLine("- current MobileClaw version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("- git: ${BuildConfig.GIT_VERSION} / ${BuildConfig.GIT_BRANCH} / ${BuildConfig.GIT_COMMIT}")
            }.trim(),
        )
    }

    private suspend fun checkUpdate(params: Map<String, Any>): SkillResult {
        return checkUpdateInfo(params).fold(
            onSuccess = { info -> SkillResult(true, info.toOutput()) },
            onFailure = { SkillResult(false, it.message ?: "Update check failed.") },
        )
    }

    private suspend fun downloadLatest(params: Map<String, Any>): SkillResult {
        val info = checkUpdateInfo(params).getOrElse {
            return SkillResult(false, it.message ?: "Update service did not return build data.")
        }
        val url = info.downloadUrl.ifBlank { info.installUrl }
        if (url.isBlank()) return SkillResult(false, "Update service did not return a downloadable URL.")
        if (info.downloadUrl.isNotBlank()) {
            return downloadAndOpenInstaller(info)
        }
        val version = info.remoteVersion.ifBlank { info.remoteVersionCode?.toString().orEmpty() }.ifBlank { "latest" }
        val fileName = "MobileClaw-$version.apk".replace(Regex("""[^\w.\-]+"""), "_")
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("MobileClaw $version")
            .setDescription("Downloading MobileClaw update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val manager = app.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
        return SkillResult(true, "Download started. Download id=$id, file=Downloads/$fileName\n$url")
    }

    suspend fun checkUpdateInfo(params: Map<String, Any> = emptyMap()): Result<PgyerUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = apiKey(params)
            val appKey = appKey(params)
            if (apiKey.isBlank() || appKey.isBlank()) {
                error("Configure release channel api_key and app_key first.")
            }
            val json = postPgyerCheck(apiKey, appKey)
                ?: error("Update check returned an empty response.")
            val code = json["code"]?.asInt ?: 0
            if (code != 0) {
                error(json.string("message").ifBlank { "Update service returned code=$code." })
            }
            val data = json["data"]?.asJsonObject ?: json
            val remoteVersionCode = data.string("buildVersionNo").toIntOrNull()
            val remoteVersion = data.string("buildVersion")
                .ifBlank { remoteVersionCode?.toString().orEmpty() }
            val hasNew = data.boolString("buildHaveNewVersion") == "true" ||
                remoteVersionCode?.let { it > BuildConfig.VERSION_CODE } == true
            PgyerUpdateInfo(
                hasNewVersion = hasNew,
                currentVersion = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                remoteVersion = remoteVersion,
                remoteVersionCode = remoteVersionCode,
                downloadUrl = data.string("downloadURL").ifBlank { data.string("downloadUrl") },
                installUrl = data.fallbackInstallUrl(),
                releaseNotes = data.string("buildUpdateDescription"),
            )
        }
    }

    suspend fun downloadAndOpenInstaller(info: PgyerUpdateInfo): SkillResult = withContext(Dispatchers.IO) {
        val directUrl = info.downloadUrl.trim()
        if (directUrl.isBlank()) {
            if (info.installUrl.isNotBlank()) {
                openUri(info.installUrl)
                return@withContext SkillResult(true, "Opened update install page: ${info.installUrl}")
            }
            return@withContext SkillResult(false, "Update service did not return a downloadable APK URL.")
        }

        runCatching {
            val version = info.remoteVersion.ifBlank { info.remoteVersionCode?.toString().orEmpty() }.ifBlank { "latest" }
            val fileName = "MobileClaw-$version.apk".replace(Regex("""[^\w.\-]+"""), "_")
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: app.cacheDir
            dir.mkdirs()
            val apk = File(dir, fileName)
            val req = Request.Builder().url(directUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("APK download HTTP ${resp.code}")
                val body = resp.body ?: error("APK download returned an empty body.")
                apk.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (!apk.exists() || apk.length() <= 0L) error("Downloaded APK is empty.")

            val uri = FileProvider.getUriForFile(app, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(settingsIntent)
                return@withContext SkillResult(
                    true,
                    "APK downloaded to ${apk.absolutePath}. Allow installs from MobileClaw, then run update again to open the installer.",
                )
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.startActivity(intent)
            SkillResult(true, "APK downloaded and installer opened: ${apk.absolutePath}")
        }.getOrElse {
            SkillResult(false, "Update download/install failed: ${it.message}")
        }
    }

    private suspend fun uploadApk(params: Map<String, Any>): SkillResult {
        val apiKey = apiKey(params)
        val apkPath = (params["apk_path"] as? String)?.trim().orEmpty()
        if (apiKey.isBlank()) return SkillResult(false, "Configure release channel api_key or pass api_key.")
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
        return executePgyerRequest(req, "Release upload completed.")
    }

    private fun postPgyerCheck(apiKey: String, appKey: String): JsonObject? {
        val body = FormBody.Builder()
            .add("_api_key", apiKey)
            .add("appKey", appKey)
            .add("buildVersion", BuildConfig.VERSION_NAME)
            .add("buildVersionNo", BuildConfig.VERSION_CODE.toString())
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
                if (!resp.isSuccessful) return SkillResult(false, "Update service HTTP ${resp.code}: ${text.take(2000)}")
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
            SkillResult(false, "Update service request failed: ${it.message}")
        }

    private fun openUri(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }
}

private fun PgyerUpdateInfo.toOutput(): String = buildString {
    appendLine(if (hasNewVersion) "Update service has a newer MobileClaw build." else "No newer build detected.")
    appendLine("Current: $currentVersion ($currentVersionCode)")
    appendLine("Git: ${BuildConfig.GIT_VERSION} / ${BuildConfig.GIT_COMMIT}")
    remoteVersion.takeIf { it.isNotBlank() }?.let { appendLine("Remote: $it") }
    remoteVersionCode?.let { appendLine("Remote code: $it") }
    downloadUrl.takeIf { it.isNotBlank() }?.let { appendLine("Download: $it") }
    installUrl.takeIf { it.isNotBlank() && it != downloadUrl }?.let { appendLine("Install: $it") }
    releaseNotes.takeIf { it.isNotBlank() }?.let { appendLine("Notes: $it") }
}.trim()

private fun JsonObject.string(key: String): String =
    runCatching { get(key)?.asString.orEmpty() }.getOrDefault("")

private fun JsonObject.boolString(key: String): String =
    string(key).lowercase()

private fun JsonObject.fallbackInstallUrl(): String {
    string("appURl").takeIf { it.isNotBlank() }?.let { return it }
    string("appURL").takeIf { it.isNotBlank() }?.let { return it }
    string("appUrl").takeIf { it.isNotBlank() }?.let { return it }
    string("buildShortcutUrl").takeIf { it.isNotBlank() }?.let { return "https://www.pgyer.com/$it" }
    string("buildQRCodeURL").takeIf { it.isNotBlank() }?.let { return it }
    return ""
}
