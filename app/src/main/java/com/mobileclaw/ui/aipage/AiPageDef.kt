package com.mobileclaw.ui.aipage

import com.google.gson.JsonObject

/**
 * Definition of an AI-created native Compose page.
 *
 * Stored as JSON in {filesDir}/ai_pages/{id}.json.
 *
 * layout: component tree using the same DSL as DynamicUiRenderer, extended with:
 *   switch, slider, list, conditional, markdown components
 *   All string values support ${expr} template evaluation.
 *
 * actions: map of actionName → list of steps. Button "action" field references these.
 *   Step types: set_state, http, shell, python, sql, notify, vibrate, toast,
 *               launch_app, open_url, share, clipboard_set, navigate_page
 */
data class AiPageDef(
    val id: String,
    val title: String,
    val icon: String = "page",
    val version: Int = 1,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val state: Map<String, String> = emptyMap(),
    val layout: JsonObject = JsonObject(),
    val actions: JsonObject = JsonObject(),
)
