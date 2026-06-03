package com.mobileclaw.skill.builtin

import android.content.Context
import android.media.MediaScannerConnection
import com.mobileclaw.memory.db.VideoGenerationTaskDao
import com.mobileclaw.memory.db.VideoGenerationTaskEntity
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class VideoGenerationTaskManager(
    private val context: Context,
    private val dao: VideoGenerationTaskDao,
) {
    companion object {
        private const val URL_PENDING_RETRY_COUNT = 3
        private const val URL_PENDING_RETRY_DELAY_MS = 4_000L
    }

    private val client = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun recent(limit: Int = 100): List<VideoGenerationTaskEntity> = withContext(Dispatchers.IO) {
        dao.recent(limit)
    }

    internal suspend fun recordSubmitted(
        taskId: String,
        prompt: String,
        provider: VideoTaskProvider,
        endpoint: String,
        apiKey: String,
        model: String?,
        submitResponseRaw: String = "",
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.get(taskId)
        dao.upsert(
            VideoGenerationTaskEntity(
                taskId = taskId,
                prompt = prompt,
                provider = provider.name,
                endpoint = endpoint,
                apiKey = apiKey,
                model = model.orEmpty(),
                status = existing?.status?.takeIf { it == VideoTaskStatuses.DOWNLOADED } ?: VideoTaskStatuses.SUBMITTED,
                videoUrl = existing?.videoUrl.orEmpty(),
                filePath = existing?.filePath.orEmpty(),
                errorMessage = "",
                submitResponseRaw = submitResponseRaw.ifBlank { existing?.submitResponseRaw.orEmpty() }.take(20_000),
                pollResponseRaw = existing?.pollResponseRaw.orEmpty(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    internal suspend fun recordSubmitFailed(
        prompt: String,
        provider: VideoTaskProvider,
        endpoint: String,
        apiKey: String,
        model: String?,
        errorMessage: String,
        submitResponseRaw: String = "",
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsert(
            VideoGenerationTaskEntity(
                taskId = "submit_failed_$now",
                prompt = prompt,
                provider = provider.name,
                endpoint = endpoint,
                apiKey = apiKey,
                model = model.orEmpty(),
                status = VideoTaskStatuses.FAILED,
                errorMessage = errorMessage.take(400),
                submitResponseRaw = submitResponseRaw.take(20_000),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun markTimedOut(taskId: String) = withContext(Dispatchers.IO) {
        dao.get(taskId)?.let { current ->
            dao.upsert(current.copy(status = VideoTaskStatuses.TIMED_OUT, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun markFailed(taskId: String, message: String) = withContext(Dispatchers.IO) {
        dao.get(taskId)?.let { current ->
            dao.upsert(
                current.copy(
                    status = VideoTaskStatuses.FAILED,
                    errorMessage = message.take(400),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun markCompleted(taskId: String, videoUrl: String, filePath: String = "") = withContext(Dispatchers.IO) {
        dao.get(taskId)?.let { current ->
            dao.upsert(
                current.copy(
                    status = if (filePath.isNotBlank()) VideoTaskStatuses.DOWNLOADED else VideoTaskStatuses.COMPLETED,
                    videoUrl = videoUrl,
                    filePath = filePath,
                    errorMessage = "",
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun refresh(taskId: String): VideoGenerationTaskEntity? = withContext(Dispatchers.IO) {
        val current = dao.get(taskId) ?: return@withContext null
        val provider = runCatching { VideoTaskProvider.valueOf(current.provider) }
            .getOrElse { VideoTaskRuntime.detectProvider(current.endpoint) }
        val snapshot = pollWithPendingUrlRetry(current, provider)
        val now = System.currentTimeMillis()
        val updated = when (snapshot.status) {
            VideoTaskStatuses.RUNNING -> current.copy(
                status = VideoTaskStatuses.RUNNING,
                errorMessage = snapshot.errorMessage.take(400),
                pollResponseRaw = snapshot.rawResponse.take(20_000),
                updatedAt = now,
            )
            VideoTaskStatuses.FAILED -> current.copy(
                status = VideoTaskStatuses.FAILED,
                errorMessage = snapshot.errorMessage.take(400),
                pollResponseRaw = snapshot.rawResponse.take(20_000),
                updatedAt = now,
            )
            VideoTaskStatuses.COMPLETED -> {
                val resolvedUrl = snapshot.videoUrl.orEmpty()
                if (resolvedUrl.isBlank()) {
                    current.copy(
                        status = VideoTaskStatuses.FAILED,
                        errorMessage = "Video completed without a downloadable URL",
                        pollResponseRaw = snapshot.rawResponse.take(20_000),
                        updatedAt = now,
                    )
                } else {
                    val file = VideoTaskRuntime.downloadVideo(
                        client = client,
                        outputDir = File(context.filesDir, "videos"),
                        url = resolvedUrl,
                        prompt = current.prompt,
                    )
                    file?.let {
                        MediaScannerConnection.scanFile(context, arrayOf(it.absolutePath), arrayOf("video/mp4"), null)
                    }
                    current.copy(
                        status = if (file != null) VideoTaskStatuses.DOWNLOADED else VideoTaskStatuses.COMPLETED,
                        videoUrl = resolvedUrl,
                        filePath = file?.absolutePath.orEmpty(),
                        errorMessage = "",
                        pollResponseRaw = snapshot.rawResponse.take(20_000),
                        updatedAt = now,
                    )
                }
            }
            else -> current.copy(status = snapshot.status, updatedAt = now)
        }
        dao.upsert(updated)
        updated
    }

    suspend fun refreshPendingTasks(): List<VideoGenerationTaskEntity> = withContext(Dispatchers.IO) {
        dao.byStatuses(
            listOf(
                VideoTaskStatuses.SUBMITTED,
                VideoTaskStatuses.RUNNING,
                VideoTaskStatuses.TIMED_OUT,
            )
        ).mapNotNull { refresh(it.taskId) }
    }

    private suspend fun pollWithPendingUrlRetry(
        current: VideoGenerationTaskEntity,
        provider: VideoTaskProvider,
    ): VideoTaskPollSnapshot {
        var snapshot = VideoTaskRuntime.pollTask(
            client = client,
            endpoint = current.endpoint,
            apiKey = current.apiKey,
            provider = provider,
            taskId = current.taskId,
        )
        repeat(URL_PENDING_RETRY_COUNT) { index ->
            if (snapshot.status != VideoTaskStatuses.RUNNING || !isVideoDownloadUrlPending(snapshot.errorMessage)) {
                return snapshot
            }
            kotlinx.coroutines.delay(URL_PENDING_RETRY_DELAY_MS * (index + 1))
            snapshot = VideoTaskRuntime.pollTask(
                client = client,
                endpoint = current.endpoint,
                apiKey = current.apiKey,
                provider = provider,
                taskId = current.taskId,
            )
        }
        return snapshot
    }

    suspend fun delete(taskId: String) = withContext(Dispatchers.IO) {
        dao.delete(taskId)
    }
}
