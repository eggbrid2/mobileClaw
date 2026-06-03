package com.mobileclaw.skill.builtin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.google.gson.JsonParser
import com.mobileclaw.BuildConfig
import com.mobileclaw.config.UserConfig
import com.mobileclaw.vpn.AppHttpProxy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLConnection
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CloudinaryImageUploader(
    private val context: Context,
    private val userConfig: UserConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun uploadIfNeeded(input: String): String {
        val normalized = input.trim()
        if (normalized.isRemoteHttpUrl()) return normalized
        val cloudName = userConfig.get("cloudinary_cloud_name")?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLOUDINARY_CLOUD_NAME
        val apiKey = userConfig.get("cloudinary_api_key")?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = userConfig.get("cloudinary_api_secret")?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLOUDINARY_API_SECRET
        return upload(
            input = normalized,
            cloudinaryCloudName = cloudName,
            cloudinaryApiKey = apiKey,
            cloudinaryApiSecret = apiSecret,
        )
    }

    fun upload(
        input: String,
        cloudinaryCloudName: String,
        cloudinaryApiKey: String,
        cloudinaryApiSecret: String,
    ): String {
        if (cloudinaryCloudName.isBlank() || cloudinaryApiKey.isBlank() || cloudinaryApiSecret.isBlank()) {
            throw IllegalArgumentException(
                "Image-to-video needs Cloudinary upload config first. Set user_config cloudinary_cloud_name, cloudinary_api_key, and cloudinary_api_secret."
            )
        }
        val payload = when {
            input.isDataUriImage() -> decodeDataUriImage(input)
            input.isContentUri() -> readContentUriImagePayload(input)
            input.isLikelyLocalFilePath() -> readLocalImagePayload(input)
            else -> throw IllegalArgumentException("Unsupported local image source for Cloudinary upload.")
        }
        val timestampSeconds = (System.currentTimeMillis() / 1000L).toString()
        val signature = sha1Hex("timestamp=$timestampSeconds$cloudinaryApiSecret")
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_key", cloudinaryApiKey)
            .addFormDataPart("timestamp", timestampSeconds)
            .addFormDataPart("signature", signature)
            .addFormDataPart(
                "file",
                payload.fileName,
                payload.bytes.toRequestBody(payload.mediaType.toMediaType())
            )
            .build()
        val req = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudinaryCloudName/image/upload")
            .post(multipart)
            .build()
        return client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalArgumentException(
                    buildString {
                        append("Cloudinary upload failed")
                        append(" (HTTP ${resp.code})")
                        val details = extractCloudinaryError(raw)
                        if (details.isNotBlank()) {
                            append(": ")
                            append(details)
                        }
                    }
                )
            }
            val json = JsonParser.parseString(raw).asJsonObject
            json["secure_url"]?.asString?.takeIf { it.isNotBlank() }
                ?: json["url"]?.asString?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Cloudinary upload succeeded but no public URL was returned.")
        }
    }

    private fun decodeDataUriImage(input: String): CloudinaryImagePayload {
        val marker = ";base64,"
        val commaIndex = input.indexOf(marker)
        require(commaIndex > 5) { "Invalid data:image URI." }
        val mimeType = input.substringAfter("data:").substringBefore(marker).ifBlank { "image/png" }
        val encoded = input.substring(commaIndex + marker.length)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        return CloudinaryImagePayload(
            bytes = bytes,
            mediaType = mimeType,
            fileName = "mobileclaw-upload.${mimeType.substringAfter('/', "png")}"
        )
    }

    private fun readLocalImagePayload(input: String): CloudinaryImagePayload {
        val file = File(input.removePrefix("file://"))
        require(file.exists() && file.isFile) { "Local image file does not exist: ${file.absolutePath}" }
        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "image/png"
        return CloudinaryImagePayload(
            bytes = file.readBytes(),
            mediaType = mimeType,
            fileName = file.name.ifBlank { "mobileclaw-upload.png" }
        )
    }

    private fun readContentUriImagePayload(input: String): CloudinaryImagePayload {
        val uri = Uri.parse(input)
        val mimeType = context.contentResolver.getType(uri) ?: "image/png"
        val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?: "mobileclaw-upload.${mimeType.substringAfter('/', "png")}"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read image content: $input")
        return CloudinaryImagePayload(
            bytes = bytes,
            mediaType = mimeType,
            fileName = fileName,
        )
    }

    private data class CloudinaryImagePayload(
        val bytes: ByteArray,
        val mediaType: String,
        val fileName: String,
    )

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .proxySelector(AppHttpProxy.proxySelector())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

fun String.isRemoteHttpUrl(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}

fun String.isDataUriImage(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("data:image/") && normalized.contains(";base64,")
}

fun String.isLikelyLocalFilePath(): Boolean {
    val normalized = trim()
    if (normalized.startsWith("file://")) return true
    if (normalized.startsWith("/")) return true
    return normalized.contains("/") && !normalized.contains("://")
}

fun String.isContentUri(): Boolean = trim().startsWith("content://")

private fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun extractCloudinaryError(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        val json = JsonParser.parseString(trimmed)
        when {
            json.isJsonObject -> {
                val obj = json.asJsonObject
                val nestedError = obj["error"]?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                nestedError ?: listOf("message", "detail", "msg").firstNotNullOfOrNull { key ->
                    obj.get(key)?.takeIf { !it.isJsonNull }?.let { value ->
                        if (value.isJsonPrimitive) value.asString else value.toString()
                    }
                } ?: trimmed.take(300)
            }
            else -> trimmed.take(300)
        }
    }.getOrDefault(trimmed.take(300))
}
