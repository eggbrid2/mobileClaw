package com.mobileclaw.ui.image

data class ImageGenerationRequest(
    val gatewayId: String,
    val gatewayName: String,
    val model: String,
    val prompt: String,
    val size: String,
    val quality: String,
) {
    fun toSkillParams(promptOverride: String? = null): Map<String, Any> = linkedMapOf(
        "gateway_id" to gatewayId,
        "gateway_name" to gatewayName,
        "prompt" to (promptOverride?.trim()?.takeIf { it.isNotBlank() } ?: prompt.trim()),
        "model" to model,
        "size" to size,
        "quality" to quality,
    ).filterValues { value -> value.toString().isNotBlank() }
}

enum class ImagePromptAiAction { ENRICH, TRANSLATE }
