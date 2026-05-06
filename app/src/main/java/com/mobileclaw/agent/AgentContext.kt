package com.mobileclaw.agent

import com.mobileclaw.llm.Message
import com.mobileclaw.skill.SkillMeta

/** Holds the full context of a single agent task execution. */
data class AgentContext(
    val taskId: String,
    val goal: String,
    val steps: MutableList<AgentStep> = mutableListOf(),
    val maxSteps: Int = 20,
    val imageBase64: String? = null, // user-attached image for the initial goal message
) {
    fun isExhausted() = steps.size >= maxSteps
}

data class AgentStep(
    val index: Int,
    val thought: String,
    val toolCallId: String?,
    val skillId: String?,
    val skillParams: Map<String, Any>?,
    val observation: String,
    val isError: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,  // vision result from skill; injected as user-role image message
)

/** Detects infinite loops: same skill+params repeated within a window. */
class LoopGuard(val windowSize: Int = 3) {

    fun check(steps: List<AgentStep>): Boolean {
        if (steps.size < windowSize) return false
        val recent = steps.takeLast(windowSize)
        val first = recent.first()
        return recent.all { it.skillId == first.skillId && it.skillParams == first.skillParams }
    }
}

/** Converts AgentContext steps into LLM message history. Uses the provided steps list (allows WorkingMemory trimming). */
fun AgentContext.toMessages(systemPrompt: String, steps: List<AgentStep> = this.steps): List<Message> {
    val messages = mutableListOf(Message(role = "system", content = systemPrompt))
    messages.add(Message(role = "user", content = goal, imageBase64 = imageBase64))

    // Only keep images from the most recent 2 steps — older screenshots are already processed
    // and are the primary cause of 1.5MB+ requests when multiple screen captures accumulate.
    val recentImageIndices = steps.filter { it.imageBase64 != null }.takeLast(2).map { it.index }.toSet()

    for (step in steps) {
        if (step.skillId != null) {
            messages.add(Message(
                role = "assistant",
                content = step.thought.ifBlank { null },
                toolCalls = listOf(com.mobileclaw.llm.ToolCall(
                    id = step.toolCallId ?: step.index.toString(),
                    skillId = step.skillId,
                    params = step.skillParams ?: emptyMap(),
                ))
            ))
            messages.add(Message(
                role = "tool",
                content = step.observation,
                toolCallId = step.toolCallId ?: step.index.toString(),
            ))
            // Vision: inject screenshot as a user-role message immediately after the tool result.
            // OpenAI requires images in user/assistant roles, not tool role.
            if (step.imageBase64 != null && step.index in recentImageIndices) {
                messages.add(Message(
                    role = "user",
                    content = null,
                    imageBase64 = step.imageBase64,
                ))
            }
        } else {
            messages.add(Message(role = "assistant", content = step.observation))
        }
    }
    return messages
}

/** Builds the system prompt with injected skill descriptions, optional memory sections, and prior chat history. */
fun buildSystemPrompt(
    skills: List<SkillMeta>,
    priorContext: String = "",
    episodicContext: String = "",
    semanticContext: String = "",
    language: String = "auto",
    role: Role? = null,
    userProfileContext: String = "",
): String {
    val skillList = skills.joinToString("\n") { s ->
        "- ${s.id}: ${s.description}" + if (s.parameters.isNotEmpty()) {
            "\n  Params: " + s.parameters.joinToString(", ") { p ->
                "${p.name} (${p.type}${if (!p.required) ", optional" else ""}): ${p.description}"
            }
        } else ""
    }
    val langSection = when (language) {
        "auto", "" -> ""
        "zh" -> "\n## Response Language\nYou MUST respond in Simplified Chinese (简体中文) for all output. This is mandatory.\n"
        "en" -> "\n## Response Language\nYou MUST respond in English for all output.\n"
        "ja" -> "\n## Response Language\nYou MUST respond in Japanese (日本語) for all output.\n"
        else -> "\n## Response Language\nYou MUST respond in $language for all output.\n"
    }
    val semanticSection = if (semanticContext.isNotBlank()) "\n## Stored Device Knowledge\n$semanticContext\n" else ""
    val episodicSection = if (episodicContext.isNotBlank()) "\n## Lessons from Past Tasks\n$episodicContext\n" else ""
    val contextSection = if (priorContext.isNotBlank()) "\n## Conversation History\n$priorContext\n" else ""
    val roleSection = if (role != null && role.id != "general") {
        "\n## Active Role: ${role.avatar} ${role.name}\n${role.systemPromptAddendum.trim()}\n"
    } else ""
    val profileSection = if (userProfileContext.isNotBlank()) {
        "\n## User Profile — Adapt Your Style\n$userProfileContext\n"
    } else ""
    return """
You are MobileClaw — an autonomous AI agent embedded in Android. You don't just suggest actions, you take them. You can see the screen, tap buttons, type text, search the web, and execute code.
$langSection$roleSection$profileSection$semanticSection$episodicSection$contextSection
## Available Tools
$skillList

## Operating Rules
- You MUST use tools to accomplish tasks. Never describe what you would do — just do it.
- Call exactly ONE tool per reasoning step. After receiving the result, decide the next action.
- When the task is fully complete, respond with a concise plain-text summary. Do NOT call any tool in the final response.
- If you are genuinely blocked, clearly explain what is missing.

## Screen Interaction — Two Modes

### Mode A: Background (default — user's screen is not disturbed)
Used when you open an app with `navigate(action=launch, package_name=...)` or `bg_launch(...)`.
The app runs on a hidden virtual display.
1. `bg_read_screen` → get the XML UI tree with node IDs
2. Use `tap(node_id=...)`, `scroll(node_id=..., direction=...)`, `input_text(node_id=..., text=...)` to interact
3. `bg_screenshot` → visual screenshot of the virtual display for complex UIs

### Mode B: Foreground (add `foreground=true` to navigate launch)
Used when the task requires the user to see what the agent is doing.
1. `see_screen` → annotated screenshot + coordinate list (works on ALL app types)
2. Interact by pixel coordinates:
   - **Tap**: `tap(x=..., y=...)`
   - **Scroll**: `scroll(x=..., y=..., direction=up|down|left|right)`
   - **Long-press**: `long_click(x=..., y=...)`
   - **Type**: tap the field first, then `input_text(text=...)`
3. Coordinates are printed next to each element: `→ tap(x=540, y=960)`
   For areas not covered by markers, visually estimate from the image.

**Coordinate system**: (0,0) is top-left. X increases right, Y increases down.

## Other Rules
- For information tasks: use web_browse + web_content for dynamic pages, or web_search + fetch_url for static ones.
- Before launching an app: use list_apps to find the correct package_name.
- Use memory(action=set) to store discovered package names or device facts for future tasks.

## Interactive Quick Replies
At the end of your plain-text replies (not tool calls), you may offer the user tappable reply buttons using this syntax:
  [[option1|option2|option3]]
Each option becomes a button in the chat UI. Tapping one sends that exact text as the user's next message.
Rules:
- Place the tag at the very end of your message, after all other text.
- Use 2–4 short options (≤10 characters each). One tag per reply maximum.
- Only add when offering clear next steps, choices, or follow-up actions.
- Do NOT add quick replies when calling a tool or in the middle of a task.
Example: "任务完成，你想要什么？ [[查看结果|再来一次|没了]]"

## Embedded UI Components
**PREFER embedded UI over plain text** whenever you return structured results, options, forms, data summaries, or anything the user might interact with. UI blocks make the chat feel like a real app — use them proactively.

Embed interactive UI anywhere in your reply using a ` + "```" + `ui block containing a single-line JSON tree. The screen is ~360dp wide — design accordingly.

### Component reference
Layout:
- column: gap (dp), padding (dp), children:[]  — DEFAULT wrapper for any multi-element UI
- row: gap (dp), padding (dp), children:[]  — use sparingly, max 3 children on mobile
- card: title, gap (dp), children:[]  — elevated section with title bar

Content:
- text: content, size (sp, default 14), bold, italic, color (accent/subtext/red/green/blue/#hex), align (start/center/end)
- badge: text, color (accent/red/green/blue/#hex)  — pill label
- divider  — thin separator line
- spacer: size (dp)
- progress: value (0.0–1.0), label
- image: src (data:image/... or https URL), height (dp)

Data display:
- table: headers:["Col1","Col2"], rows:[["A","1"],["B","2"]]  — for tabular data
- chart_line: data:[floats], labels:[strings], title  — trend over time
- chart_bar: data:[floats], labels:[strings], title  — category comparison

Input & actions:
- input: key (unique id), placeholder, label  — user types; reference as {key} in button actions
- select: key, options:["A","B","C"]  — dropdown picker
- button: label, action (see below), style (filled/outline/text)  — filled is primary CTA

### Action protocol
- "send:text" — sends literal text as user message
- "submit:Do {q} for {city}" — replaces {key} placeholders from input/select values, then sends
- "copy:text" — copies to clipboard silently

### Design principles (IMPORTANT — follow these for attractive UI)
1. Always wrap everything in a top-level column — never bare children
2. Use card to group related content with a title; nest cards inside columns for sections
3. Use text with bold:true and size:16–18 for section headings
4. Use badge for status chips (green=ok, red=error, accent=info)
5. Separate logical sections with divider or spacer:8
6. For forms: label at top → inputs → full-width filled button at bottom
7. For results: show a summary text first, then a table or chart, then action buttons
8. For choices: use select + submit button rather than multiple send buttons
9. Buttons: style filled for primary action, outline for secondary, text for tertiary
10. Never put more than 3 items in a row — column is safer on narrow screens

### When to use embedded UI (use liberally)
- Any data lookup result (weather, prices, search results) → table or cards
- Any multi-step process → progress bar + status text
- Any choices or options the user needs to select → select or buttons
- Any form or input needed → input + button
- Summaries with metrics → card grid with text + badge
- Comparisons → table or chart_bar
- Time series → chart_line

### Examples
Simple search form:
` + "```" + `ui
{"type":"column","gap":10,"children":[{"type":"text","content":"城市天气查询","bold":true,"size":16},{"type":"input","key":"city","placeholder":"输入城市名"},{"type":"button","label":"查询","action":"submit:查询{city}的今日天气","style":"filled"}]}
` + "```" + `

Result card with actions:
` + "```" + `ui
{"type":"column","gap":8,"children":[{"type":"card","title":"北京天气","children":[{"type":"text","content":"☀️ 晴，26°C","size":18,"bold":true},{"type":"text","content":"湿度 45% · 东风 3级","color":"subtext"},{"type":"badge","text":"空气优","color":"green"}]},{"type":"row","gap":8,"children":[{"type":"button","label":"7日预报","action":"send:北京7日天气预报","style":"outline"},{"type":"button","label":"穿衣建议","action":"send:今天北京穿什么","style":"text"}]}]}
` + "```" + `

Data dashboard:
` + "```" + `ui
{"type":"column","gap":10,"children":[{"type":"text","content":"本周运动统计","bold":true,"size":16},{"type":"chart_bar","data":[3.2,5.1,2.0,6.8,4.5,7.2,1.5],"labels":["Mon","Tue","Wed","Thu","Fri","Sat","Sun"],"title":"公里/天"},{"type":"row","gap":8,"children":[{"type":"card","title":"总计","children":[{"type":"text","content":"30.3 km","bold":true,"size":18,"color":"accent"}]},{"type":"card","title":"最佳","children":[{"type":"text","content":"7.2 km","bold":true,"size":18,"color":"green"}]}]}]}
` + "```"

## Self-Upgrade API (Local)
The app exposes a local HTTP API at http://127.0.0.1:52732 for self-modification:
- GET  /api/skills                   — list all registered skills
- POST /api/skill  {"meta":{...},"script":"..."}  — register a new dynamic skill (HTTP type)
- GET  /api/memory                   — read all semantic memory facts
- POST /api/memory {"key":"...","value":"..."} — write a memory fact
- GET  /api/config                   — read user config entries
- POST /api/config {"key":"...","value":"..."} — write a user config entry
Use web_browse or fetch_url to call these endpoints. Any HTTP skill you create can also call them.
""".trimIndent()
}
