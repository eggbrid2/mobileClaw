package com.mobileclaw.skill.builtin

import android.os.Build
import com.mobileclaw.perception.VirtualDisplayManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

class BgLaunchSkill(private val manager: VirtualDisplayManager) : Skill {
    override val meta = SkillMeta(
        id = "bg_launch",
        name = "Launch App on Virtual Display",
        description = "Creates a hidden virtual display and launches an app on it. The app runs completely invisible to the user. " +
            "After this, use bg_read_screen (XML tree) or bg_screenshot (visual) to observe the app, " +
            "and tap/scroll/input_text with node_id to interact — node-based actions work cross-display.",
        parameters = listOf(
            SkillParam("package_name", "string", "Package name of the app to launch"),
            SkillParam("width", "number", "Virtual display width in pixels (default 1080)", required = false),
            SkillParam("height", "number", "Virtual display height in pixels (default 1920)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "虚拟屏幕启动应用",
        descriptionZh = "在后台虚拟显示器上启动指定应用。",
        tags = listOf("后台"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val pkg = params["package_name"] as? String ?: return SkillResult(false, "package_name is required")
        val width = (params["width"] as? Number)?.toInt() ?: 1080
        val height = (params["height"] as? Number)?.toInt() ?: 1920
        return runCatching {
            val id = manager.start(width, height)
            manager.launchApp(pkg)
            // Wait for app to render, then verify it appeared on the virtual display (not redirected to main screen).
            kotlinx.coroutines.delay(2000)
            val xml = manager.readScreenXml()
            val appearedOnVd = xml.length > 400 && !xml.startsWith("Virtual display")
            val warning = if (!appearedOnVd) {
                "\n\n⚠️ App may have launched on the main screen instead of the virtual display. " +
                "This is a common issue on Chinese ROMs. Call vd_setup for device-specific fix instructions."
            } else ""
            SkillResult(
                success = true,
                output = "Launched $pkg on virtual display (displayId=$id, ${width}x${height}).$warning\n" +
                    "Use bg_read_screen or bg_screenshot to observe.",
            )
        }.getOrElse { e ->
            val isPermission = e is SecurityException || e.message?.contains("permission", ignoreCase = true) == true
            if (isPermission) {
                SkillResult(
                    false,
                    "Virtual display launch blocked by ROM security policy.\n" +
                    "${e.message}\n\n" +
                    "Call vd_setup to get device-specific instructions for enabling this feature.",
                )
            } else {
                SkillResult(false, "Launch failed: ${e.message}")
            }
        }
    }
}

class BgReadScreenSkill(private val manager: VirtualDisplayManager) : Skill {
    override val meta = SkillMeta(
        id = "bg_read_screen",
        name = "Read Virtual Display Screen (XML)",
        description = "Returns the accessibility UI tree of the virtual background display as XML with node IDs. " +
            "Use after bg_launch. Node IDs from this output can be used directly with tap, scroll, input_text. " +
            "If the tree is empty, use bg_screenshot for visual analysis instead.",
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "读取虚拟屏幕（XML）",
        descriptionZh = "以 XML 格式读取虚拟显示器的 UI 结构。",
        tags = listOf("后台"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val xml = manager.readScreenXml()
        val hasText = xml.contains(Regex("""text="[^"]+""""))
        val nodeCount = xml.count { it == '<' }
        return if (hasText || nodeCount >= 5) {
            SkillResult(true, xml.take(8000))
        } else {
            SkillResult(false, "Virtual display tree is empty or unreadable. Use bg_screenshot for visual analysis.")
        }
    }
}

class BgScreenshotSkill(private val manager: VirtualDisplayManager) : Skill {
    override val meta = SkillMeta(
        id = "bg_screenshot",
        name = "Screenshot Virtual Display",
        description = "Captures a screenshot from the hidden virtual display and returns it for visual analysis. " +
            "Use after bg_launch when bg_read_screen returns no useful content (Flutter/game/WebView apps).",
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "虚拟屏幕截图",
        descriptionZh = "对虚拟显示器进行截图。",
        tags = listOf("后台"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val frame = manager.captureFrame()
            ?: return SkillResult(
                success = false,
                output = "No frame available. Ensure bg_launch was called and the app has had time to render (~2s).",
            )
        return SkillResult(
            success = true,
            output = "Virtual display screenshot captured.",
            imageBase64 = frame,
        )
    }
}

class BgStopSkill(private val manager: VirtualDisplayManager) : Skill {
    override val meta = SkillMeta(
        id = "bg_stop",
        name = "Stop Virtual Display",
        description = "Releases the virtual background display and stops all apps running on it. " +
            "Call this when the background task is complete to free resources.",
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "停止虚拟显示器",
        descriptionZh = "停止并关闭虚拟显示器。",
        tags = listOf("后台"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        manager.stop()
        return SkillResult(true, "Virtual display stopped and resources released.")
    }
}

class VirtualDisplaySetupSkill(private val manager: VirtualDisplayManager) : Skill {
    override val meta = SkillMeta(
        id = "vd_setup",
        name = "Virtual Display Setup Guide",
        description = "Tests whether the virtual display is available and returns ROM-specific setup instructions. " +
            "Call this when bg_launch fails or the user reports the virtual display is unavailable.",
        type = SkillType.NATIVE,
        injectionLevel = 2,
        nameZh = "虚拟显示器配置指南",
        descriptionZh = "获取虚拟显示器的安装和配置步骤。",
        tags = listOf("后台"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val testResult = manager.testSupport()
        val romName = detectRom()
        val guide = romGuide(romName)

        return if (testResult.startsWith("ok:")) {
            val id = testResult.substringAfter(":")
            SkillResult(
                success = true,
                output = "✓ 虚拟屏可用 (display #$id，设备: $romName)\n\n$guide",
            )
        } else {
            val error = testResult.substringAfter(":")
            SkillResult(
                success = false,
                output = buildString {
                    appendLine("✗ 虚拟屏不可用: $error")
                    appendLine()
                    appendLine("设备: $romName")
                    appendLine()
                    appendLine(guide)
                    appendLine()
                    appendLine("─────────────────────────")
                    appendLine("📲 也可在应用「设置 → Virtual Display → 设置向导」查看图文步骤和一键复制命令。")
                },
            )
        }
    }

    private fun detectRom(): String {
        val brand = Build.BRAND.lowercase()
        val mfr = Build.MANUFACTURER.lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
                mfr.contains("xiaomi") -> "MIUI (Xiaomi/Redmi/POCO)"
            brand.contains("huawei") || brand.contains("honor") -> "EMUI/HarmonyOS (Huawei/Honor)"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ||
                mfr.contains("oppo") -> "ColorOS (OPPO/Realme/OnePlus)"
            brand.contains("vivo") -> "OriginOS/FuntouchOS (Vivo)"
            brand.contains("samsung") || mfr.contains("samsung") -> "One UI (Samsung)"
            brand.contains("meizu") -> "Flyme (Meizu)"
            else -> "Stock Android (${Build.BRAND})"
        }
    }

    private fun romGuide(rom: String): String = when {
        rom.contains("MIUI") -> """
            📱 MIUI 设置步骤:
            1. 设置 → 我的设备 → 全部参数 → 连点 MIUI 版本7次 → 开发者模式开启
            2. 设置 → 更多设置 → 开发者选项
            3. 开启【自由窗口】(Free-form windows)
            4. 然后重启 app，再试 bg_launch

            如仍不行，ADB 命令（连接电脑）:
              adb shell settings put global enable_freeform_support 1
        """.trimIndent()

        rom.contains("EMUI") || rom.contains("HarmonyOS") -> """
            📱 EMUI/HarmonyOS 设置步骤:
            1. 设置 → 关于手机 → 版本号 连点7次 → 开发者模式开启
            2. 设置 → 系统 → 开发者选项
            3. 开启【多窗口】和【自由窗口】
            4. 然后重启 app，再试 bg_launch

            如仍不行，ADB 命令:
              adb shell settings put global enable_freeform_support 1
        """.trimIndent()

        rom.contains("ColorOS") -> """
            📱 ColorOS 设置步骤 (OPPO/Realme/OnePlus):
            1. 设置 → 关于本机 → 版本号 连点7次 → 开启开发者模式
            2. 设置 → 其他设置 → 开发者选项
            3. 开启【自由窗口】(部分机型叫"强制活动可调整大小")
            4. 重启 app，再试 bg_launch

            如果设置后仍不行（ColorOS 12+ 常见），ADB 命令（连接电脑）:
              adb shell settings put global enable_freeform_support 1
              adb shell settings put global force_desktop_mode_on_external_displays 1

            执行后无需重启，直接重试 bg_launch 即可。
        """.trimIndent()

        rom.contains("OriginOS") || rom.contains("FuntouchOS") -> """
            📱 OriginOS/FuntouchOS 设置步骤:
            1. 设置 → 通用 → 关于手机 → 连点版本号7次 → 开启开发者模式
            2. 设置 → 通用 → 开发者选项
            3. 开启【多任务显示】
            4. 重启 app，再试 bg_launch

            如仍不行，ADB 命令:
              adb shell settings put global enable_freeform_support 1
        """.trimIndent()

        else -> """
            📱 通用设置步骤:
            1. 设置 → 关于手机 → 版本号 连点7次 → 开启开发者选项
            2. 开发者选项 → 开启【自由窗口】或【多窗口】
            3. 如仍不行，ADB 命令（连接电脑）:
               adb shell settings put global enable_freeform_support 1
            4. 重启 app 后再试。
        """.trimIndent()
    }
}
