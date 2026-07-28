package com.darkprince.vpn.vpn

/**
 * JNI-обёртка hev-socks5-tunnel (TUN → локальный SOCKS).
 * Библиотека собирается скриптом scripts/compile-hevtun.sh с
 * PKGNAME=com/darkprince/vpn/vpn — имена и сигнатуры методов менять нельзя.
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
    @JvmStatic external fun TProxyStopService(): Boolean
    @JvmStatic external fun TProxyIsRunning(): Boolean
    @JvmStatic external fun TProxyGetStats(): LongArray?
}
