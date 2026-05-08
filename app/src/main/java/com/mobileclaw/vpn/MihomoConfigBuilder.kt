package com.mobileclaw.vpn

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

object MihomoConfigBuilder {
    const val MIXED_PORT = 10999
    const val HTTP_PORT = 11000
    const val EXTERNAL_CONTROLLER_PORT = 19090

    @Suppress("UNCHECKED_CAST")
    fun build(
        rawYaml: String,
        selectedProxyName: String,
        mixedPort: Int = MIXED_PORT,
        httpPort: Int = HTTP_PORT,
        controllerPort: Int = EXTERNAL_CONTROLLER_PORT,
    ): String {
        val root = (Yaml().load<Any>(rawYaml) as? MutableMap<String, Any?>)
            ?: throw IllegalArgumentException("Invalid Clash config")
        val proxies = root["proxies"] as? List<Map<String, Any?>>
            ?: throw IllegalArgumentException("Clash config has no proxies")
        if (proxies.none { it["name"]?.toString() == selectedProxyName }) {
            throw IllegalArgumentException("Selected proxy not found in Clash config: $selectedProxyName")
        }

        root["mixed-port"] = mixedPort
        root["port"] = httpPort
        root["socks-port"] = 0
        root["redir-port"] = 0
        root["tproxy-port"] = 0
        root["allow-lan"] = false
        root["bind-address"] = "127.0.0.1"
        root["mode"] = "rule"
        root["log-level"] = "debug"
        root["external-controller"] = "127.0.0.1:$controllerPort"
        root["geodata-mode"] = false
        root["geo-auto-update"] = false
        root.remove("tun")
        root.remove("interface-name")
        root.remove("geox-url")
        root.remove("geodata-loader")
        root.remove("geosite-matcher")
        root.remove("rule-providers")
        root.remove("profile")
        root.remove("find-process-mode")

        root["proxy-groups"] = listOf(
            linkedMapOf(
                "name" to "GLOBAL",
                "type" to "select",
                "proxies" to listOf(selectedProxyName, "DIRECT"),
            )
        )
        root["rules"] = listOf("MATCH,GLOBAL")

        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }
        return Yaml(options).dump(root)
    }
}
