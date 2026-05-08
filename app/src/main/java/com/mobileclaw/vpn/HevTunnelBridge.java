package com.mobileclaw.vpn;

import org.amnezia.awg.hevtunnel.TProxyService;

final class HevTunnelBridge {
    private HevTunnelBridge() {
    }

    static void start(String configPath, int tunFd) {
        TProxyService.TProxyStartService(configPath, tunFd);
    }

    static void stop() {
        TProxyService.TProxyStopService();
    }

    static long[] stats() {
        return TProxyService.TProxyGetStats();
    }
}
