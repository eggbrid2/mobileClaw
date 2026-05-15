package com.mobileclaw.llm

import android.content.Context
import android.net.Uri
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class LocalModelSource(
    val id: String,
    val name: String,
    val url: String,
)

data class LocalModelInfo(
    val id: String,
    val name: String,
    val family: String,
    val fileName: String,
    val url: String,
    val sources: List<LocalModelSource> = emptyList(),
    val sizeBytes: Long,
    val minRamGb: Int,
    val recommendedRamGb: Int,
    val supportsText: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsChatRuntime: Boolean = true,
    val runtimeNote: String = "",
    val installed: Boolean = false,
    val path: String = "",
    val downloadProgress: Float = 0f,
    val downloading: Boolean = false,
    val error: String = "",
) {
    val modelId: String get() = "local:$id"
    val downloadSources: List<LocalModelSource>
        get() = sources.ifEmpty { listOf(LocalModelSource("huggingface", "Hugging Face", url)) }
}

class LocalModelManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val rootDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    private val builtins = listOf(
        LocalModelInfo(
            id = "gemma4-e2b-litert",
            name = "Gemma 4 E2B LiteRT-LM",
            family = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            sources = gemmaSources(
                hfUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
                modelscopeUrl = "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/master/gemma-4-E2B-it.litertlm",
            ),
            sizeBytes = 2_580_000_000L,
            minRamGb = 6,
            recommendedRamGb = 8,
            supportsText = true,
            supportsVision = true,
            supportsAudio = true,
            supportsChatRuntime = true,
            runtimeNote = "litertlm",
        ),
        LocalModelInfo(
            id = "gemma4-e4b-litert",
            name = "Gemma 4 E4B LiteRT-LM",
            family = "Gemma 4 E4B",
            fileName = "gemma-4-E4B-it.litertlm",
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            sources = gemmaSources(
                hfUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
                modelscopeUrl = "https://modelscope.cn/models/litert-community/gemma-4-E4B-it-litert-lm/resolve/master/gemma-4-E4B-it.litertlm",
            ),
            sizeBytes = 3_660_000_000L,
            minRamGb = 8,
            recommendedRamGb = 10,
            supportsText = true,
            supportsVision = true,
            supportsAudio = true,
            supportsChatRuntime = true,
            runtimeNote = "litertlm",
        ),
        LocalModelInfo(
            id = "gemma4-e2b-web-task",
            name = "Gemma 4 E2B Web Task",
            family = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it-web.task",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task?download=true",
            sources = gemmaSources(
                hfUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task?download=true",
                modelscopeUrl = "https://modelscope.cn/models/litert-community/gemma-4-E2B-it-litert-lm/resolve/master/gemma-4-E2B-it-web.task",
            ),
            sizeBytes = 2_003_697_664L,
            minRamGb = 6,
            recommendedRamGb = 8,
            supportsText = true,
            supportsVision = true,
            supportsAudio = true,
            supportsChatRuntime = false,
            runtimeNote = "web.task",
        ),
        LocalModelInfo(
            id = "gemma4-e4b-web-task",
            name = "Gemma 4 E4B Web Task",
            family = "Gemma 4 E4B",
            fileName = "gemma-4-E4B-it-web.task",
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.task?download=true",
            sources = gemmaSources(
                hfUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.task?download=true",
                modelscopeUrl = "https://modelscope.cn/models/litert-community/gemma-4-E4B-it-litert-lm/resolve/master/gemma-4-E4B-it-web.task",
            ),
            sizeBytes = 2_960_000_000L,
            minRamGb = 8,
            recommendedRamGb = 10,
            supportsText = true,
            supportsVision = true,
            supportsAudio = true,
            supportsChatRuntime = false,
            runtimeNote = "web.task",
        )
    )

    private val _models = MutableStateFlow(scan())
    val models: StateFlow<List<LocalModelInfo>> = _models.asStateFlow()

    fun modelPath(id: String): String? {
        val normalized = id.removePrefix("local:")
        return models.value.firstOrNull { it.id == normalized && it.installed && it.supportsChatRuntime }?.path
    }

    fun refresh() {
        _models.value = scan()
    }

    suspend fun download(id: String, token: String = "", sourceUrl: String = "") = withContext(Dispatchers.IO) {
        val model = builtins.firstOrNull { it.id == id } ?: return@withContext
        val downloadUrl = sourceUrl.ifBlank { model.downloadSources.firstOrNull()?.url ?: model.url }
        val dir = File(rootDir, model.id).also { it.mkdirs() }
        val target = File(dir, model.fileName)
        val tmp = File(dir, "${model.fileName}.download")
        update(model.id) { it.copy(downloading = true, error = "", downloadProgress = progressOf(tmp.length(), model.sizeBytes)) }
        runCatching {
            val existing = tmp.takeIf { it.exists() }?.length() ?: 0L
            val reqBuilder = Request.Builder().url(downloadUrl)
            if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")
            if (token.isNotBlank() && downloadUrl.contains("huggingface.co")) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                java.io.FileOutputStream(tmp, existing > 0 && response.code == 206).use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = if (existing > 0 && response.code == 206) existing else 0L
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            update(model.id) { it.copy(downloadProgress = progressOf(downloaded, model.sizeBytes), downloading = true) }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            writeManifest(dir, target)
            refresh()
        }.onFailure { e ->
            update(model.id) { it.copy(downloading = false, error = e.message ?: "Download failed") }
        }
    }

    suspend fun importModel(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val model = builtins.firstOrNull { it.id == id } ?: return@withContext
        val dir = File(rootDir, model.id).also { it.mkdirs() }
        val target = File(dir, model.fileName)
        update(model.id) { it.copy(downloading = true, error = "", downloadProgress = 0f) }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot open selected model file")
            writeManifest(dir, target)
            refresh()
        }.onFailure { e ->
            update(model.id) { it.copy(downloading = false, error = e.message ?: "Import failed") }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(rootDir, id.removePrefix("local:")).deleteRecursively()
        refresh()
    }

    private fun scan(): List<LocalModelInfo> = builtins.map { model ->
        val file = File(File(rootDir, model.id), model.fileName)
        model.copy(
            installed = file.exists() && file.length() > 0L,
            path = file.takeIf { it.exists() }?.absolutePath.orEmpty(),
            downloadProgress = progressOf(file.length(), model.sizeBytes),
            downloading = false,
            error = "",
        )
    }

    private fun update(id: String, transform: (LocalModelInfo) -> LocalModelInfo) {
        _models.value = _models.value.map { if (it.id == id) transform(it) else it }
    }

    private fun writeManifest(dir: File, file: File) {
        val sha = runCatching { sha256(file).take(16) }.getOrDefault("")
        File(dir, "manifest.json").writeText(
            """{"fileName":"${file.name}","sizeBytes":${file.length()},"sha256Prefix":"$sha"}"""
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun progressOf(done: Long, total: Long): Float =
        if (total <= 0L) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    private fun gemmaSources(hfUrl: String, modelscopeUrl: String): List<LocalModelSource> = listOf(
        LocalModelSource("huggingface", "Hugging Face", hfUrl),
        LocalModelSource("modelscope", "ModelScope", modelscopeUrl),
    )
}
