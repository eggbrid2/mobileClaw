package com.mobileclaw.skill.builtin

import com.mobileclaw.perception.ClawAccessibilityService
import com.mobileclaw.perception.PhoneScreenState
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

/** Returns an AccessibilityRequest failure result when the accessibility service is not running. */
private fun accessibilityNotAvailable(skillName: String) = SkillResult(
    success = false,
    output = "Accessibility service is not enabled. Please grant MobileClaw accessibility access.",
    data = SkillAttachment.AccessibilityRequest(skillName),
)

private fun foregroundStatus(): String =
    "Foreground app: package=${ClawAccessibilityService.getCurrentPackage().ifBlank { "unknown" }}, " +
        "activity=${ClawAccessibilityService.getCurrentActivity().ifBlank { "unknown" }}."

private fun phoneCoordinateStatus(): String = PhoneScreenState.describe()

private fun mapPhonePoint(params: Map<String, Any>, x: Float, y: Float) =
    PhoneScreenState.mapPoint(
        x = x,
        y = y,
        inputWidth = (params["coord_width"] as? Number)?.toFloat(),
        inputHeight = (params["coord_height"] as? Number)?.toFloat(),
    )

class ScreenshotSkill : Skill {
    override val meta = SkillMeta(
        id = "screenshot",
        name = "Take Screenshot",
        description = "Fallback screen perception tool. Captures a raw screenshot for visual analysis. " +
            "Use this when read_screen/XML is empty, accessibility nodes are unavailable, or see_screen marker detection is not useful. " +
            "For normal phone control, prefer see_screen first because it returns coordinates.",
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "截图",
        descriptionZh = "屏幕感知兜底工具。XML/无障碍节点不可用，或视觉标注不可用时截图供分析。",
        tags = listOf("控制"),
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val data = runCatching { ClawAccessibilityService.captureScreenshot() }
            .getOrElse { return SkillResult(success = false, output = "Screenshot failed: ${it.message}") }
        return SkillResult(
            success = true,
            output = "Screenshot captured.\n${phoneCoordinateStatus()}\n${foregroundStatus()}",
            imageBase64 = data.imageBase64,
        )
    }
}

class ReadScreenSkill : Skill {
    override val meta = SkillMeta(
        id = "read_screen",
        name = "Read Screen (XML, Legacy)",
        description = "Returns the current screen UI as structured XML with node IDs. Legacy tool — only use when explicitly debugging accessibility trees. Prefer see_screen for all normal screen reading.",
        type = SkillType.NATIVE,
        injectionLevel = 2,
        nameZh = "读取屏幕（XML）",
        descriptionZh = "以 XML 格式读取屏幕 UI 结构（无障碍旧版）。",
        tags = listOf("控制"),
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val xml = runCatching { ClawAccessibilityService.captureScreenshotXml() }
            .getOrElse { return SkillResult(success = false, output = "Failed to read screen: ${it.message}") }
            ?: return SkillResult(success = false, output = "No UI tree available.")
        // Detect useless XML: no text content and very few nodes
        val hasText = xml.contains(Regex("""text="[^"]+""""))
        val nodeCount = xml.count { it == '<' }
        if (!hasText && nodeCount < 5) {
            return SkillResult(
                success = false,
                output = "Accessibility tree is empty or unreadable (likely Flutter, React Native, WebView, or game). Use see_screen for visual analysis instead.",
            )
        }
        return SkillResult(success = true, output = xml.take(8000))
    }
}

class SeeScreenSkill : Skill {
    override val meta = SkillMeta(
        id = "see_screen",
        name = "See Screen (Vision)",
        description = "PRIMARY screen perception tool. Takes a screenshot with numbered red markers on interactive elements (Set-of-Mark). " +
            "Works on ALL app types: standard Android, Flutter, React Native, games, WebViews. " +
            "Returns the annotated image plus a coordinate list in the exact image coordinate space seen by the model. Use those coordinates with tap(x=..., y=...), " +
            "scroll(x=..., y=..., direction=...), or long_click(x=..., y=...). Call once to inspect the current UI; " +
            "after this tool, take an action instead of calling see_screen again unless the UI has changed.",
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "看屏幕（视觉）",
        descriptionZh = "截图后由视觉模型描述屏幕内容。",
        tags = listOf("控制"),
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val som = runCatching { ClawAccessibilityService.captureScreenshotSom() }
            .getOrElse { return SkillResult(success = false, output = "Screenshot failed: ${it.message}") }
        val nodeMap = runCatching { ClawAccessibilityService.getNodeMap() ?: emptyMap() }.getOrDefault(emptyMap())
        val interactive = nodeMap.filter { it.value.isClickable || it.value.isEditable || it.value.isScrollable }

        val elementList = if (interactive.isEmpty()) {
            "No interactive elements detected in accessibility tree.\n" +
                "Use the image to visually identify elements and tap(x=..., y=...) with estimated coordinates."
        } else {
            val scaleX = som.coordinateSpace.imageWidth.toFloat() / som.coordinateSpace.screenWidth.toFloat()
            val scaleY = som.coordinateSpace.imageHeight.toFloat() / som.coordinateSpace.screenHeight.toFloat()
            "Interactive elements (coordinates are image pixels from the attached screenshot; use directly with tap/scroll/long_click):\n" +
                interactive.entries.take(40).joinToString("\n") { (id, node) ->
                    val cx = (((node.bounds.left + node.bounds.right) / 2f) * scaleX).toInt()
                    val cy = (((node.bounds.top + node.bounds.bottom) / 2f) * scaleY).toInt()
                    val cls = node.className?.substringAfterLast('.') ?: "View"
                    val label = buildString {
                        node.text?.let { append(" \"${it.take(60)}\"") }
                        node.contentDesc?.takeIf { it != node.text?.toString() }
                            ?.let { append(" [${it.take(40)}]") }
                    }
                    val actions = listOfNotNull(
                        if (node.isClickable) "tap" else null,
                        if (node.isEditable) "input" else null,
                        if (node.isScrollable) "scroll" else null,
                    ).joinToString("|")
                    "[$id] $cls$label [$actions] → tap(x=$cx, y=$cy)"
                }
        }

        return SkillResult(
            success = true,
            output = "${interactive.size} interactive elements marked on screenshot.\n${phoneCoordinateStatus()}\n${foregroundStatus()}\n$elementList",
            imageBase64 = som.imageBase64,
        )
    }
}

class TapSkill : Skill {
    override val meta = SkillMeta(
        id = "tap",
        name = "Tap / Click",
        description = "Taps a point on the screen. PRIMARY: use x/y pixel coordinates from see_screen output. " +
            "SECONDARY: use node_id from the marker list if a numbered marker covers the target.",
        parameters = listOf(
            SkillParam("x", "number", "X pixel coordinate to tap (from see_screen output)", required = false),
            SkillParam("y", "number", "Y pixel coordinate to tap (from see_screen output)", required = false),
            SkillParam("coord_width", "number", "Optional width of the screenshot coordinate space used for x/y. Omit when using latest see_screen/screenshot.", required = false),
            SkillParam("coord_height", "number", "Optional height of the screenshot coordinate space used for x/y. Omit when using latest see_screen/screenshot.", required = false),
            SkillParam("node_id", "string", "Marker ID from see_screen (secondary, only if marker is visible)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "点击",
        descriptionZh = "点击屏幕上的指定坐标或元素。",
        tags = listOf("控制"),
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val x = (params["x"] as? Number)?.toFloat()
        val y = (params["y"] as? Number)?.toFloat()
        val nodeId = params["node_id"] as? String

        return when {
            x != null && y != null -> {
                val p = mapPhonePoint(params, x, y)
                ClawAccessibilityService.clickCoordinate(p.x, p.y)
                SkillResult(true, "Tapped image (${x.toInt()}, ${y.toInt()}) as device (${p.x.toInt()}, ${p.y.toInt()}) [${p.note}].\n${foregroundStatus()}")
            }
            nodeId != null -> {
                // Resolve node to center coordinates and gesture-tap (works for all apps).
                // Fall back to accessibility action only if the node can't be found in the
                // main display tree (e.g. it lives on a virtual display).
                val node = ClawAccessibilityService.getNodeMap()?.get(nodeId)
                if (node != null) {
                    val cx = ((node.bounds.left + node.bounds.right) / 2).toFloat()
                    val cy = ((node.bounds.top + node.bounds.bottom) / 2).toFloat()
                    try {
                        ClawAccessibilityService.clickCoordinate(cx, cy)
                        SkillResult(true, "Tapped node $nodeId at device (${cx.toInt()}, ${cy.toInt()}).\n${foregroundStatus()}")
                    } catch (_: Exception) {
                        ClawAccessibilityService.clickNode(nodeId)
                        SkillResult(true, "Tapped node $nodeId (accessibility action).\n${foregroundStatus()}")
                    }
                } else {
                    try {
                        ClawAccessibilityService.clickNode(nodeId)
                        SkillResult(true, "Tapped node $nodeId.\n${foregroundStatus()}")
                    } catch (e: Exception) {
                        SkillResult(false, "Node $nodeId not found. Provide x/y coordinates from see_screen instead.")
                    }
                }
            }
            else -> SkillResult(false, "Provide x and y coordinates (from see_screen output) or a node_id.")
        }
    }
}

class InputTextSkill : Skill {
    override val meta = SkillMeta(
        id = "input_text",
        name = "Input Text",
        description = "Types text into a focused or specified editable field.",
        parameters = listOf(
            SkillParam("text", "string", "Text to type"),
            SkillParam("node_id", "string", "Target node ID (optional, uses focused field if omitted)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "输入文字",
        descriptionZh = "在当前焦点输入框中输入文本。",
        tags = listOf("控制"),
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val text = params["text"] as? String ?: return SkillResult(false, "text is required")
        val nodeId = params["node_id"] as? String
        if (nodeId != null) {
            ClawAccessibilityService.inputText(nodeId, text)
        } else {
            ClawAccessibilityService.inputTextFocused(text)
        }
        return SkillResult(success = true, output = "Typed: $text\n${foregroundStatus()}")
    }
}

class NavigateSkill(
    private val virtualDisplayManager: com.mobileclaw.perception.VirtualDisplayManager? = null,
) : Skill {
    override val meta = SkillMeta(
        id = "navigate",
        name = "Navigate",
        description = "System navigation and app launching. " +
            "action=home: go to home screen. " +
            "action=back: press back. " +
            "action=launch: open an app. By default launches on the virtual background display so the user's screen is not disturbed — " +
            "then use bg_read_screen + tap(node_id=...) to interact, or bg_screenshot for visual analysis. " +
            "Set foreground=true to launch on the main display instead (use see_screen + tap(x,y) afterwards).",
        parameters = listOf(
            SkillParam("action", "string", "'home' | 'back' | 'launch'"),
            SkillParam("package_name", "string", "App package name (required when action=launch)", required = false),
            SkillParam("foreground", "boolean", "Launch on main display instead of virtual display (default false)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "导航",
        descriptionZh = "通过包名或 URL 跳转到指定页面或应用。",
        tags = listOf("控制"),
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        return when (val action = params["action"] as? String) {
            "home" -> { ClawAccessibilityService.goHome(); SkillResult(true, "Went home.\n${foregroundStatus()}") }
            "back" -> { ClawAccessibilityService.goBack(); SkillResult(true, "Went back.\n${foregroundStatus()}") }
            "launch" -> {
                val pkg = params["package_name"] as? String
                    ?: return SkillResult(false, "package_name required for launch")
                val foreground = params["foreground"] as? Boolean ?: false
                if (!foreground && virtualDisplayManager != null) {
                    return runCatching {
                        val id = virtualDisplayManager?.start()
                        virtualDisplayManager?.launchApp(pkg)
                        SkillResult(
                            true,
                            "Launched $pkg on virtual display (displayId=$id). " +
                                "Wait ~2s for it to load, then use bg_read_screen to inspect the UI or bg_screenshot for visual analysis. " +
                                "Interact with tap(node_id=...) using IDs from bg_read_screen.",
                        )
                    }.getOrElse {
                        // Virtual display failed — fall back to foreground
                        ClawAccessibilityService.launchApp(pkg)
                        SkillResult(true, "Launched $pkg on main display (virtual display failed: ${it.message}). Use see_screen + tap(x,y).\n${foregroundStatus()}")
                    }
                }
                ClawAccessibilityService.launchApp(pkg)
                SkillResult(true, "Launched $pkg on main display. Use see_screen then tap(x=..., y=...) to interact.\n${foregroundStatus()}")
            }
            else -> SkillResult(false, "Unknown action: $action. Use home, back, or launch.")
        }
    }
}

class ScrollSkill : Skill {
    override val meta = SkillMeta(
        id = "scroll",
        name = "Scroll / Swipe",
        description = "Swipes the screen in a direction. Use x/y start coordinates from see_screen output. " +
            "For lists and feed content, set x/y to the center of the scrollable area. " +
            "node_id is a secondary option when see_screen marks a scrollable element.",
        parameters = listOf(
            SkillParam("direction", "string", "'up' | 'down' | 'left' | 'right'"),
            SkillParam("x", "number", "X pixel coordinate to start the swipe (center of scroll area)", required = false),
            SkillParam("y", "number", "Y pixel coordinate to start the swipe (center of scroll area)", required = false),
            SkillParam("coord_width", "number", "Optional width of the screenshot coordinate space used for x/y. Omit when using latest see_screen/screenshot.", required = false),
            SkillParam("coord_height", "number", "Optional height of the screenshot coordinate space used for x/y. Omit when using latest see_screen/screenshot.", required = false),
            SkillParam("distance", "number", "Swipe distance in pixels (default 500)", required = false),
            SkillParam("node_id", "string", "Scrollable marker ID from see_screen (secondary)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "滚动 / 滑动",
        descriptionZh = "在屏幕上执行滚动或滑动操作。",
        tags = listOf("控制"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val direction = params["direction"] as? String
            ?: return SkillResult(false, "direction is required")
        val x = (params["x"] as? Number)?.toFloat()
        val y = (params["y"] as? Number)?.toFloat()
        val nodeId = params["node_id"] as? String
        val distance = (params["distance"] as? Number)?.toFloat() ?: 500f

        return when {
            x != null && y != null -> {
                val p = mapPhonePoint(params, x, y)
                val scaledDistance = PhoneScreenState.latest()?.let { space ->
                    if (direction in listOf("up", "down")) distance * space.screenHeight / space.imageHeight
                    else distance * space.screenWidth / space.imageWidth
                } ?: distance
                ClawAccessibilityService.scrollCoordinate(p.x, p.y, direction, scaledDistance)
                SkillResult(true, "Swiped $direction from image (${x.toInt()}, ${y.toInt()}) as device (${p.x.toInt()}, ${p.y.toInt()}) by ${scaledDistance.toInt()}px [${p.note}].\n${foregroundStatus()}")
            }
            nodeId != null -> {
                val node = ClawAccessibilityService.getNodeMap()?.get(nodeId)
                if (node != null) {
                    val cx = ((node.bounds.left + node.bounds.right) / 2).toFloat()
                    val cy = ((node.bounds.top + node.bounds.bottom) / 2).toFloat()
                    try {
                        ClawAccessibilityService.scrollCoordinate(cx, cy, direction, distance)
                        SkillResult(true, "Swiped $direction from center of node $nodeId at device (${cx.toInt()}, ${cy.toInt()}).\n${foregroundStatus()}")
                    } catch (_: Exception) {
                        val scrollDir = if (direction in listOf("up", "left")) "backward" else "forward"
                        ClawAccessibilityService.scrollNode(nodeId, scrollDir)
                        SkillResult(true, "Scrolled node $nodeId $direction (accessibility action).\n${foregroundStatus()}")
                    }
                } else {
                    val scrollDir = if (direction in listOf("up", "left")) "backward" else "forward"
                    try {
                        ClawAccessibilityService.scrollNode(nodeId, scrollDir)
                        SkillResult(true, "Scrolled node $nodeId $direction.\n${foregroundStatus()}")
                    } catch (e: Exception) {
                        SkillResult(false, "Node $nodeId not found. Provide x/y coordinates from see_screen.")
                    }
                }
            }
            else -> SkillResult(false, "Provide x and y coordinates (from see_screen) or a node_id.")
        }
    }
}

class LongClickSkill : Skill {
    override val meta = SkillMeta(
        id = "long_click",
        name = "Long Press",
        description = "Long-presses a point on screen. Use x/y pixel coordinates from see_screen output. " +
            "Useful for context menus, text selection, drag handles, and hold-to-activate actions.",
        parameters = listOf(
            SkillParam("x", "number", "X pixel coordinate to long-press", required = false),
            SkillParam("y", "number", "Y pixel coordinate to long-press", required = false),
            SkillParam("coord_width", "number", "Optional width of the screenshot coordinate space used for x/y. Omit when using latest see_screen/screenshot.", required = false),
            SkillParam("coord_height", "number", "Optional height of the screenshot coordinate space used for x/y. Omit when using latest see_screen/screenshot.", required = false),
            SkillParam("node_id", "string", "Marker ID from see_screen (secondary)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "长按",
        descriptionZh = "长按屏幕上的指定坐标。",
        tags = listOf("控制"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val x = (params["x"] as? Number)?.toFloat()
        val y = (params["y"] as? Number)?.toFloat()
        val nodeId = params["node_id"] as? String

        return when {
            x != null && y != null -> {
                val p = mapPhonePoint(params, x, y)
                ClawAccessibilityService.longClickCoordinate(p.x, p.y)
                SkillResult(true, "Long-pressed image (${x.toInt()}, ${y.toInt()}) as device (${p.x.toInt()}, ${p.y.toInt()}) [${p.note}].\n${foregroundStatus()}")
            }
            nodeId != null -> {
                val node = ClawAccessibilityService.getNodeMap()?.get(nodeId)
                if (node != null) {
                    val cx = ((node.bounds.left + node.bounds.right) / 2).toFloat()
                    val cy = ((node.bounds.top + node.bounds.bottom) / 2).toFloat()
                    try {
                        ClawAccessibilityService.longClickCoordinate(cx, cy)
                        SkillResult(true, "Long-pressed node $nodeId at device (${cx.toInt()}, ${cy.toInt()}).\n${foregroundStatus()}")
                    } catch (_: Exception) {
                        ClawAccessibilityService.longClickNode(nodeId)
                        SkillResult(true, "Long-pressed node $nodeId (accessibility action).\n${foregroundStatus()}")
                    }
                } else {
                    try {
                        ClawAccessibilityService.longClickNode(nodeId)
                        SkillResult(true, "Long-pressed node $nodeId.\n${foregroundStatus()}")
                    } catch (e: Exception) {
                        SkillResult(false, "Node $nodeId not found. Provide x/y coordinates from see_screen.")
                    }
                }
            }
            else -> SkillResult(false, "Provide x and y coordinates (from see_screen output) or a node_id.")
        }
    }
}

class PhoneStatusSkill : Skill {
    override val meta = SkillMeta(
        id = "phone_status",
        name = "Phone Status",
        description = "Returns the current foreground app package/activity and the latest screenshot coordinate mapping. Use after phone actions to verify whether the target app is open.",
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "手机状态",
        descriptionZh = "返回当前前台 App 包名/Activity，以及最近一次截图坐标映射。",
        tags = listOf("控制"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        return SkillResult(true, "${foregroundStatus()}\n${phoneCoordinateStatus()}")
    }
}

class ListAppsSkill : Skill {
    override val meta = SkillMeta(
        id = "list_apps",
        name = "List Installed Apps",
        description = "Returns all installed apps with their package names and display names. Use this before navigate(launch) to find the correct package_name.",
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "查看已安装应用",
        descriptionZh = "列出设备上已安装的所有应用。",
        tags = listOf("控制"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        if (!ClawAccessibilityService.isEnabled()) return accessibilityNotAvailable(meta.name)
        val apps = runCatching { ClawAccessibilityService.listInstalledApps() }
            .getOrElse { return SkillResult(false, "Failed to list apps: ${it.message}") }
        return SkillResult(true, apps.joinToString("\n") { "${it.appName}: ${it.packageName}" })
    }
}
