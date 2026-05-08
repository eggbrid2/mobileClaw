package org.amnezia.awg.hevtunnel;

public final class TProxyService {
    private TProxyService() {
    }

    public static native void TProxyStartService(String configPath, int fd);

    public static native void TProxyStopService();

    public static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }
}
