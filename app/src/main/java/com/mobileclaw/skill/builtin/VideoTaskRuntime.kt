package com.mobileclaw.skill.builtin

import android.util.Log
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

internal enum class VideoTaskProvider { KLING, AGNES, OPENAI_COMPAT }

internal data class VideoTaskPollSnapshot(
    val status: String,
    val videoUrl: String? = null,
    val errorMessage: String = "",
    val rawResponse: String = "",
)

internal object VideoTaskStatuses {
    const val SUBMITTED = "submitted"
    const val RUNNING = "running"
    const val TIMED_OUT = "timed_out"
    const val COMPLETED = "completed"
    const val DOWNLOADED = "downloaded"
    const val FAILED = "failed"
}

internal const val VIDEO_DOWNLOAD_URL_PENDING_MESSAGE = "Video task finished but download URL is not ready yet"

internal fun isVideoDownloadUrlPending(message: String): Boolean =
    message.startsWith(VIDEO_DOWNLOAD_URL_PENDING_MESSAGE)

internal object VideoTaskRuntime {
    private const val TAG = "VideoTaskRuntime"
    private const val DOWNLOAD_RETRY_COUNT = 3
    private const val DOWNLOAD_RETRY_DELAY_MS = 3_000L

    fun detectProvider(endpoint: String): VideoTaskProvider = when {
        "klingai" in endpoint -> VideoTaskProvider.KLING
        "apihub.agnes-ai.com" in endpoint || "agnes-ai.com" in endpoint -> VideoTaskProvider.AGNES
        else -> VideoTaskProvider.OPENAI_COMPAT
    }

    fun normalizeProviderBase(endpoint: String): String =
        endpoint.trim().trimEnd('/').removeSuffix("/v1")

    fun normalizeOpenAiCompatibleBase(endpoint: String): String {
        val trimmed = endpoint.trim().trimEnd('/')
        if (trimmed.isBlank()) return trimmed
        val hasVersionSuffix = Regex("/v\\d+$", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        return if (hasVersionSuffix) trimmed else "$trimmed/v1"
    }

    fun pollTask(
        client: OkHttpClient,
        endpoint: String,
        apiKey: String,
        provider: VideoTaskProvider,
        taskId: String,
    ): VideoTaskPollSnapshot {
        val base = normalizeProviderBase(endpoint)
        val pollUrl = when (provider) {
            VideoTaskProvider.KLING -> "$base/v1/videos/text2video/$taskId"
            VideoTaskProvider.AGNES -> "$base/v1/videos/$taskId"
            VideoTaskProvider.OPENAI_COMPAT -> "${normalizeOpenAiCompatibleBase(endpoint)}/videos/generations/$taskId"
        }
        return runCatching {
            val req = Request.Builder()
                .url(pollUrl)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return VideoTaskPollSnapshot(
                        status = VideoTaskStatuses.FAILED,
                        errorMessage = "HTTP ${resp.code}: ${body.take(200)}",
                        rawResponse = body,
                    )
                }
                val json = runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject }.getOrNull()
                    ?: return VideoTaskPollSnapshot(
                        status = VideoTaskStatuses.FAILED,
                        errorMessage = "Invalid poll response",
                        rawResponse = body,
                    )
                if (provider == VideoTaskProvider.AGNES) {
                    Log.d(
                        TAG,
                        "Agnes poll taskId=$taskId body=${body.take(1600)}",
                    )
                }
                parsePollSnapshot(json, provider, body)
            }
        }.getOrElse { error ->
            VideoTaskPollSnapshot(
                status = VideoTaskStatuses.RUNNING,
                errorMessage = error.message.orEmpty(),
            )
        }
    }

    fun downloadVideo(
        client: OkHttpClient,
        outputDir: File,
        url: String,
        prompt: String,
    ): File? {
        val safeFilename = prompt.take(30)
            .replace(Regex("[^\\w\\s-]"), "")
            .trim()
            .replace(" ", "_")
            .ifBlank { "video" }
        outputDir.mkdirs()
        repeat(DOWNLOAD_RETRY_COUNT) { attempt ->
            val outFile = File(outputDir, "${safeFilename}_${System.currentTimeMillis()}.mp4")
            val success = runCatching {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.d(TAG, "Video download retry=${attempt + 1} failed http=${resp.code} url=${url.take(160)}")
                        return@use false
                    }
                    val bytes = resp.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        Log.d(TAG, "Video download retry=${attempt + 1} returned empty body url=${url.take(160)}")
                        return@use false
                    }
                    outFile.writeBytes(bytes)
                    true
                }
            }.getOrDefault(false)
            if (success && outFile.exists() && outFile.length() > 0) {
                return outFile
            }
            outFile.delete()
            if (attempt < DOWNLOAD_RETRY_COUNT - 1) {
                Thread.sleep(DOWNLOAD_RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    private fun parsePollSnapshot(
        json: JsonObject,
        provider: VideoTaskProvider,
        rawBody: String,
    ): VideoTaskPollSnapshot = when (provider) {
        VideoTaskProvider.AGNES -> parseAgnes(json, rawBody)
        else -> parseCommon(json, rawBody)
    }

    private fun parseCommon(json: JsonObject, rawBody: String): VideoTaskPollSnapshot {
        val data = json["data"]?.asJsonObject ?: json
        val status = data["task_status"]?.asString ?: data["status"]?.asString ?: ""
        val normalized = status.lowercase()
        if (normalized in setOf("failed", "error")) {
            return VideoTaskPollSnapshot(
                status = VideoTaskStatuses.FAILED,
                errorMessage = data["message"]?.asString ?: json["message"]?.asString ?: "Video generation failed",
                rawResponse = rawBody,
            )
        }
        val done = normalized in setOf("succeed", "completed", "success")
        val url = data["task_result"]?.asJsonObject
            ?.get("videos")?.asJsonArray
            ?.firstOrNull()?.asJsonObject
            ?.get("url")?.asString
            ?: data["videos"]?.asJsonArray?.firstOrNull()?.asJsonObject?.get("url")?.asString
            ?: json["url"]?.asString
            ?: data["data"]?.asJsonArray?.firstOrNull()?.asJsonObject?.get("url")?.asString
        return when {
            done && !url.isNullOrBlank() -> VideoTaskPollSnapshot(VideoTaskStatuses.COMPLETED, url, rawResponse = rawBody)
            done -> VideoTaskPollSnapshot(VideoTaskStatuses.FAILED, errorMessage = "Video completed without a downloadable URL", rawResponse = rawBody)
            else -> VideoTaskPollSnapshot(VideoTaskStatuses.RUNNING, rawResponse = rawBody)
        }
    }

    private fun parseAgnes(json: JsonObject, rawBody: String): VideoTaskPollSnapshot {
        val data = json["data"]?.asJsonObject ?: json
        val status = data["status"]?.asString
            ?: data["task_status"]?.asString
            ?: json["status"]?.asString
            ?: ""
        val normalized = status.lowercase()
        if (normalized in setOf("failed", "error")) {
            return VideoTaskPollSnapshot(
                status = VideoTaskStatuses.FAILED,
                errorMessage = data["message"]?.asString ?: json["message"]?.asString ?: "Video generation failed",
                rawResponse = rawBody,
            )
        }
        val url = extractVideoUrl(data) ?: extractVideoUrl(json)
        return when {
            normalized in setOf("completed", "success", "succeed", "succeeded", "done", "ready", "finished") && !url.isNullOrBlank() ->
                VideoTaskPollSnapshot(VideoTaskStatuses.COMPLETED, url, rawResponse = rawBody)
            normalized in setOf("completed", "success", "succeed", "succeeded", "done", "ready", "finished") ->
                VideoTaskPollSnapshot(
                    VideoTaskStatuses.RUNNING,
                    errorMessage = VIDEO_DOWNLOAD_URL_PENDING_MESSAGE,
                    rawResponse = rawBody,
                )
            else -> VideoTaskPollSnapshot(VideoTaskStatuses.RUNNING, rawResponse = rawBody)
        }
    }

    private fun extractVideoUrl(json: JsonObject?): String? {
        if (json == null) return null
        val directKeys = listOf(
            "video_url",
            "url",
            "download_url",
            "file_url",
            "output_url",
            "result_url",
            "resource_url",
        )
        directKeys.forEach { key ->
            val value = runCatching { json.get(key)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()
            if (value.isLikelyVideoUrl()) return value
        }
        json.entrySet().forEach { (key, value) ->
            val raw = runCatching { value.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString }.getOrNull()
            if (raw.isLikelyVideoUrl() && key.looksLikeVideoField()) {
                return raw
            }
        }
        val objectKeys = listOf("result", "output", "data", "video", "file", "resource")
        objectKeys.forEach { key ->
            val child = runCatching { json.getAsJsonObject(key) }.getOrNull()
            extractVideoUrl(child)?.let { return it }
        }
        json.entrySet().forEach { (_, value) ->
            val child = runCatching { value.asJsonObject }.getOrNull()
            extractVideoUrl(child)?.let { return it }
        }
        val arrayKeys = listOf("videos", "outputs", "data", "results", "files", "resources")
        arrayKeys.forEach { key ->
            val array = runCatching { json.getAsJsonArray(key) }.getOrNull() ?: return@forEach
            array.forEach { element ->
                val nested = runCatching { element.asJsonObject }.getOrNull()
                extractVideoUrl(nested)?.let { return it }
            }
        }
        json.entrySet().forEach { (_, value) ->
            val array = runCatching { value.asJsonArray }.getOrNull() ?: return@forEach
            array.forEach { element ->
                val nested = runCatching { element.asJsonObject }.getOrNull()
                extractVideoUrl(nested)?.let { return it }
            }
        }
        return null
    }

    private fun String?.isLikelyVideoUrl(): Boolean {
        val value = this?.trim().orEmpty()
        if (value.isBlank()) return false
        if (!value.startsWith("http://") && !value.startsWith("https://")) return false
        val lowered = value.lowercase()
        return lowered.contains(".mp4") ||
            lowered.contains(".mov") ||
            lowered.contains(".webm") ||
            lowered.contains("/video") ||
            lowered.contains("video")
    }

    private fun String.looksLikeVideoField(): Boolean {
        val lowered = lowercase()
        return lowered.contains("video") ||
            lowered.contains("url") ||
            lowered.contains("file") ||
            lowered.contains("output") ||
            lowered.contains("result") ||
            lowered.contains("resource")
    }
}
