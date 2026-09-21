package com.saman.tunnel;

public final class HevBridge {
    private static volatile boolean loaded = false;
    private static volatile String loadError = "";

    private HevBridge() {}

    static {
        try {
            System.loadLibrary("hev-socks5-tunnel");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            loadError = t.getClass().getSimpleName() + ": " +
                    (t.getMessage() == null ? "" : t.getMessage());
        }
    }

    private static native boolean TProxyStartService(String configPath, int fd);
    private static native boolean TProxyStopService();
    private static native boolean TProxyIsRunning();
    private static native long[] TProxyGetStats();

    public static boolean isLoaded() {
        return loaded;
    }

    public static String getLoadError() {
        return loadError;
    }

    public static synchronized boolean start(String configPath, int fd) {
        if (!loaded) return false;
        try {
            return TProxyStartService(configPath, fd);
        } catch (Throwable t) {
            loadError = "start: " + t.getClass().getSimpleName() + ": " +
                    (t.getMessage() == null ? "" : t.getMessage());
            return false;
        }
    }

    public static synchronized boolean stop() {
        if (!loaded) return false;
        try {
            return TProxyStopService();
        } catch (Throwable t) {
            loadError = "stop: " + t.getClass().getSimpleName() + ": " +
                    (t.getMessage() == null ? "" : t.getMessage());
            return false;
        }
    }

    public static boolean isRunning() {
        if (!loaded) return false;
        try {
            return TProxyIsRunning();
        } catch (Throwable t) {
            loadError = "isRunning: " + t.getClass().getSimpleName() + ": " +
                    (t.getMessage() == null ? "" : t.getMessage());
            return false;
        }
    }

    public static long[] stats() {
        if (!loaded) return new long[]{0, 0, 0, 0};
        try {
            long[] v = TProxyGetStats();
            return v != null ? v : new long[]{0, 0, 0, 0};
        } catch (Throwable t) {
            loadError = "stats: " + t.getClass().getSimpleName() + ": " +
                    (t.getMessage() == null ? "" : t.getMessage());
            return new long[]{0, 0, 0, 0};
        }
    }
}
