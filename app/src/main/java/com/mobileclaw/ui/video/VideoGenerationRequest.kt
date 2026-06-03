package com.mobileclaw.ui.video

import com.google.gson.JsonArray

data class VideoGenerationRequest(
    val gatewayId: String,
    val gatewayName: String,
    val model: String,
    val prompt: String,
    val mode: String,
    val aspectRatio: String,
    val duration: String,
    val firstFrameUrl: String,
    val lastFrameUrl: String,
    val negativePrompt: String,
    val seed: String,
    val frameRate: String,
    val extraBody: String,
) {
    fun toSkillParams(promptOverride: String? = null): Map<String, Any> {
        val resolvedPrompt = promptOverride?.trim()?.takeIf { it.isNotBlank() } ?: prompt.trim()
        val params = linkedMapOf<String, Any>(
            "gateway_id" to gatewayId,
            "gateway_name" to gatewayName,
            "prompt" to resolvedPrompt,
            "duration" to duration,
            "aspect_ratio" to aspectRatio,
            "wait_for_completion" to "false",
        )
        addIfNotBlank(params, "model", model)
        addIfNotBlank(params, "negative_prompt", negativePrompt)
        addIfNotBlank(params, "seed", seed)
        addIfNotBlank(params, "frame_rate", frameRate)
        val resolvedMode = when {
            mode == "keyframes" || lastFrameUrl.isNotBlank() -> "keyframes"
            mode == "ti2vid" || firstFrameUrl.isNotBlank() -> "ti2vid"
            else -> ""
        }
        addIfNotBlank(params, "mode", resolvedMode)
        addIfNotBlank(params, "first_frame_image", firstFrameUrl)
        addIfNotBlank(params, "last_frame_image", lastFrameUrl)
        if (firstFrameUrl.isNotBlank()) params["image"] = firstFrameUrl.trim()
        val images = listOf(firstFrameUrl, lastFrameUrl)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (images.isNotEmpty()) {
            params["images"] = JsonArray().apply { images.forEach { add(it) } }.toString()
        }
        addIfNotBlank(params, "extra_body", extraBody)
        return params
    }
}

private fun addIfNotBlank(params: MutableMap<String, Any>, key: String, value: String) {
    val normalized = value.trim()
    if (normalized.isNotBlank()) params[key] = normalized
}
