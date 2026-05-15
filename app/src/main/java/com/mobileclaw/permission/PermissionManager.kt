package com.mobileclaw.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.mobileclaw.perception.ClawAccessibilityService

// ── ROM Detection ─────────────────────────────────────────────────────────────

enum class RomType(val displayName: String) {
    MIUI("MIUI (Xiaomi / Redmi / POCO)"),
    EMUI("EMUI / MagicUI (Huawei / Honor)"),
    COLOR_OS("ColorOS (OPPO / Realme)"),
    ORIGIN_OS("OriginOS / FuntouchOS (Vivo)"),
    ONE_UI("One UI (Samsung)"),
    FLYME("Flyme (Meizu)"),
    AOSP("Stock Android / Other"),
}

private fun detectRom(): RomType {
    val brand = Build.BRAND.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val display = Build.DISPLAY.lowercase()
    return when {
        hasProp("ro.miui.ui.version.name") || brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> RomType.MIUI
        hasProp("ro.build.version.emui") || brand.contains("huawei") || brand.contains("honor") -> RomType.EMUI
        hasProp("ro.build.version.opporom") || hasProp("ro.coloros.version") || brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> RomType.COLOR_OS
        hasProp("ro.vivo.os.build.display.id") || brand.contains("vivo") -> RomType.ORIGIN_OS
        brand.contains("samsung") || manufacturer.contains("samsung") -> RomType.ONE_UI
        brand.contains("meizu") -> RomType.FLYME
        else -> RomType.AOSP
    }
}

private fun hasProp(key: String): Boolean = runCatching {
    val process = Runtime.getRuntime().exec("getprop $key")
    val result = process.inputStream.bufferedReader().readLine()?.trim()
    !result.isNullOrEmpty()
}.getOrDefault(false)

// ── Permission Items ──────────────────────────────────────────────────────────

sealed class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    /** True if this permission blocks core agent function */
    val isBlocking: Boolean = true,
    /** True if the permission can be directly requested (vs. manual settings navigation) */
    val canDirectRequest: Boolean = false,
) {
    object Accessibility : PermissionItem(
        id = "accessibility",
        title = "Accessibility Service",
        icon = "accessibility",
        description = "Required to read screen content, detect UI elements, and perform touch actions.",
        isBlocking = true,
    )
    object Overlay : PermissionItem(
        id = "overlay",
        title = "Display Over Other Apps",
        icon = "overlay",
        description = "Required to show the agent status overlay while running tasks on other apps.",
        isBlocking = true,
    )
    object BatteryOptimization : PermissionItem(
        id = "battery",
        title = "Battery Optimization Exemption",
        icon = "battery",
        description = "Prevents Android from killing the agent during long-running tasks. Tap 'Grant' to allow immediately.",
        isBlocking = false,
        canDirectRequest = true,
    )
    object Notification : PermissionItem(
        id = "notification",
        title = "Post Notifications",
        icon = "notification",
        description = "Required to show task progress notifications (Android 13+).",
        isBlocking = false,
        canDirectRequest = true,
    )
    data class RomAutoStart(val romName: String) : PermissionItem(
        id = "rom_autostart",
        title = "Auto-start ($romName)",
        icon = "launch",
        description = "Your ROM ($romName) requires explicit auto-start permission for apps to start in the background. Enable it to prevent the agent from being killed.",
        isBlocking = false,
    )
    data class RomBackgroundPop(val romName: String) : PermissionItem(
        id = "rom_bg_pop",
        title = "Background Pop-up ($romName)",
        icon = "phone",
        description = "Required by $romName for apps to launch UI or show overlay windows from the background.",
        isBlocking = false,
    )
}

// ── Permission Diagnosis ──────────────────────────────────────────────────────

data class PermissionDiagnosis(
    val permissionId: String,
    val summary: String,
    val userAction: String,
)

// ── Manager ───────────────────────────────────────────────────────────────────

class PermissionManager(private val context: Context) {

    val romType: RomType by lazy { detectRom() }

    // ── State checks ──────────────────────────────────────────────────────────

    fun isAccessibilityEnabled(): Boolean = ClawAccessibilityService.isEnabled()

    fun isOverlayEnabled(): Boolean = Settings.canDrawOverlays(context)

    fun isBatteryOptimizationExempt(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isNotificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Core blocking permissions — agent cannot function without these. */
    fun allGranted() = isAccessibilityEnabled() && isOverlayEnabled()

    /** All permissions including optional ones. */
    fun pendingPermissions(): List<PermissionItem> = buildList {
        if (!isAccessibilityEnabled()) add(PermissionItem.Accessibility)
        if (!isOverlayEnabled()) add(PermissionItem.Overlay)
        if (!isBatteryOptimizationExempt()) add(PermissionItem.BatteryOptimization)
        if (!isNotificationGranted()) add(PermissionItem.Notification)
        // ROM-specific: only show if ROM is known to be restrictive
        when (romType) {
            RomType.MIUI -> {
                add(PermissionItem.RomAutoStart(romType.displayName))
                add(PermissionItem.RomBackgroundPop(romType.displayName))
            }
            RomType.EMUI -> add(PermissionItem.RomAutoStart(romType.displayName))
            RomType.COLOR_OS -> {
                add(PermissionItem.RomAutoStart(romType.displayName))
                add(PermissionItem.RomBackgroundPop(romType.displayName))
            }
            RomType.ORIGIN_OS -> add(PermissionItem.RomAutoStart(romType.displayName))
            else -> Unit
        }
    }

    /** Blocking permissions only, for the initial gate screen. */
    fun pendingBlockingPermissions(): List<PermissionItem> =
        pendingPermissions().filter { it.isBlocking }

    // ── Intent builders ───────────────────────────────────────────────────────

    fun openAccessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openOverlaySettings(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openBatteryOptimizationRequest(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openAppDetails(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Returns the best available Intent for a ROM-specific setting, with AOSP fallback. */
    fun openRomSettingFor(item: PermissionItem): Intent {
        val pkg = context.packageName
        return when (item) {
            is PermissionItem.RomAutoStart -> romAutoStartIntent(pkg) ?: openAppDetails()
            is PermissionItem.RomBackgroundPop -> romBackgroundPopIntent(pkg) ?: openOverlaySettings()
            else -> openAppDetails()
        }
    }

    // ── ROM-specific Intents ──────────────────────────────────────────────────

    private fun romAutoStartIntent(pkg: String): Intent? = when (romType) {
        RomType.MIUI -> tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
        ) ?: tryIntent(Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").putExtra("extra_pkgname", pkg))

        RomType.EMUI -> tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
        ) ?: tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
        )

        RomType.COLOR_OS -> tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.FakeActivity"
                )
            }
        ) ?: tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            }
        )

        RomType.ORIGIN_OS -> tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            }
        ) ?: tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
        )

        RomType.ONE_UI -> tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }
        )

        else -> null
    }

    private fun romBackgroundPopIntent(pkg: String): Intent? = when (romType) {
        RomType.MIUI -> tryIntent(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", pkg)
            }
        )
        RomType.COLOR_OS -> tryIntent(
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                )
            }
        )
        else -> null
    }

    private fun tryIntent(intent: Intent): Intent? {
        val canResolve = context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        return if (canResolve) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else null
    }

    // ── Error → Permission Diagnosis ──────────────────────────────────────────

    /**
     * Given an error message from a failed skill execution, returns a
     * [PermissionDiagnosis] describing what permission is likely missing.
     */
    fun diagnoseFromError(error: String): PermissionDiagnosis? {
        val lower = error.lowercase()
        return when {
            "accessibility" in lower || "not available" in lower ->
                PermissionDiagnosis(
                    permissionId = "accessibility",
                    summary = "Accessibility Service is not enabled.",
                    userAction = "Open Settings > Accessibility and enable 'MobileClaw'.",
                )
            "overlay" in lower || "candrawoverlays" in lower || "drawoverother" in lower ->
                PermissionDiagnosis(
                    permissionId = "overlay",
                    summary = "Display Over Other Apps is not granted.",
                    userAction = "Open Settings > Special App Access > Display Over Other Apps and enable MobileClaw.",
                )
            "securityexception" in lower || "permission denied" in lower ->
                PermissionDiagnosis(
                    permissionId = "security",
                    summary = "A system permission was denied.",
                    userAction = "Check MobileClaw's permissions in Settings > Apps > MobileClaw > Permissions.",
                )
            "battery" in lower || "killed" in lower || "doze" in lower ->
                PermissionDiagnosis(
                    permissionId = "battery",
                    summary = "Battery optimization may be terminating the agent.",
                    userAction = "Grant Battery Optimization exemption in Settings > Apps > MobileClaw > Battery.",
                )
            "notification" in lower ->
                PermissionDiagnosis(
                    permissionId = "notification",
                    summary = "Notification permission is not granted.",
                    userAction = "Grant notification permission to MobileClaw.",
                )
            "background" in lower && romType != RomType.AOSP ->
                PermissionDiagnosis(
                    permissionId = "rom_autostart",
                    summary = "Your ROM (${romType.displayName}) is blocking background activity.",
                    userAction = romAutoStartInstructions(),
                )
            else -> null
        }
    }

    /** Human-readable instructions for the current ROM's auto-start setting. */
    fun romAutoStartInstructions(): String = when (romType) {
        RomType.MIUI ->
            "MIUI: Security Center > Permissions > Autostart > Enable MobileClaw. Also check: Manage apps > MobileClaw > Other permissions > Display pop-up window."
        RomType.EMUI ->
            "EMUI: Phone Manager > App Launch > MobileClaw > Manage manually > enable Auto-launch and Run in background."
        RomType.COLOR_OS ->
            "ColorOS: Phone Manager > Privacy Permissions > Startup Manager > Enable MobileClaw. Also check: App Management > MobileClaw > Permissions."
        RomType.ORIGIN_OS ->
            "OriginOS: iManager > App Management > MobileClaw > Background Management > Allow background run."
        RomType.ONE_UI ->
            "One UI: Settings > Apps > MobileClaw > Battery > Unrestricted. Also check: Settings > Device Care > Battery > Background usage limits."
        else ->
            "Settings > Apps > MobileClaw > Battery > Don't optimize. Or check the system's battery/background restriction settings."
    }

    /** Full permission status report string for the agent (PermissionSkill output). */
    fun buildStatusReport(feature: String): String {
        val lines = mutableListOf<String>()
        lines += "# Permission Status (ROM: ${romType.displayName})"
        lines += ""
        lines += "## Core permissions"
        lines += "- Accessibility Service: ${status(isAccessibilityEnabled())}"
        lines += "- Display Over Other Apps: ${status(isOverlayEnabled())}"
        lines += "- Battery Optimization Exempt: ${status(isBatteryOptimizationExempt())}"
        lines += "- Post Notifications: ${status(isNotificationGranted())}"
        lines += ""

        if (romType != RomType.AOSP) {
            lines += "## ROM-specific (${romType.displayName})"
            lines += romAutoStartInstructions()
            lines += ""
        }

        lines += "## Feature diagnosis for: $feature"
        lines += when (feature.lowercase()) {
            "screen_read", "screenshot", "tap", "input" ->
                if (isAccessibilityEnabled()) "✓ Accessibility OK — screen interaction should work."
                else "✗ Accessibility Service is DISABLED. User must enable it in Settings > Accessibility > MobileClaw."
            "overlay", "floating_window" ->
                if (isOverlayEnabled()) "✓ Overlay OK — floating window can be shown."
                else "✗ Overlay permission MISSING. User must grant in Settings > Special App Access > Display Over Other Apps."
            "background", "long_task" ->
                buildString {
                    if (!isBatteryOptimizationExempt()) append("⚠ Battery optimization may kill the agent. ")
                    if (romType != RomType.AOSP) append("ROM restriction (${romType.displayName}) may also block background operation. ")
                    append(if (isBatteryOptimizationExempt()) "Battery exemption OK." else "Request battery exemption.")
                }
            else ->
                "Check individual permissions above. For any permission issue, call check_permissions with the specific feature name."
        }

        lines += ""
        lines += "To fix missing permissions: communicate the specific instruction to the user and ask them to follow the steps."
        return lines.joinToString("\n")
    }

    private fun status(granted: Boolean) = if (granted) "✓ Granted" else "✗ Missing"
}
