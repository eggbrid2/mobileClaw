package com.mobileclaw.vpn

import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType

class VpnControlSkill(
    private val vpnManager: VpnManager,
    private val getSelectedProxy: () -> Pair<VpnSubscription, ProxyConfig>?,
) : Skill {

    override val meta = SkillMeta(
        id = "vpn_control",
        name = "VPN Control",
        description = "Start or stop the global VPN proxy. " +
            "action=start: enable VPN with the currently selected proxy. " +
            "action=stop: disconnect VPN. " +
            "action=status: check current VPN state.",
        parameters = listOf(
            SkillParam("action", "string", "'start' | 'stop' | 'status'"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        nameZh = "VPN 控制",
        descriptionZh = "开启、关闭或检查全局 VPN。需要用户先在 VPN 页面授权一次系统 VPN 权限。",
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        return when ((params["action"] as? String)?.lowercase()) {
            "start", "on", "enable", "开启" -> {
                if (vpnManager.prepareIntent() != null) {
                    return SkillResult(
                        false,
                        "VPN permission not yet granted. Please open the VPN page and allow VPN access, then try again.",
                    )
                }
                val selected = getSelectedProxy()
                    ?: return SkillResult(
                        false,
                        "No proxy selected. Please open the VPN page, select a proxy, then try again.",
                    )
                val (sub, proxy) = selected
                vpnManager.startVpn(sub, proxy)
                SkillResult(true, "VPN starting — proxy: ${proxy.name} (${proxy.typeName} ${proxy.server}:${proxy.port})")
            }
            "stop", "off", "disable", "关闭" -> {
                vpnManager.stopVpn()
                SkillResult(true, "VPN stopped.")
            }
            "status", "状态" -> {
                val running = ClawVpnService.isRunning
                val proxy = getSelectedProxy()?.second
                SkillResult(
                    true,
                    if (running)
                        "VPN connected — proxy: ${proxy?.name ?: "unknown"}"
                    else
                        "VPN disconnected.${proxy?.let { " Selected proxy: ${it.name}" } ?: ""}",
                )
            }
            else -> SkillResult(false, "Unknown action. Use start, stop, or status.")
        }
    }
}
