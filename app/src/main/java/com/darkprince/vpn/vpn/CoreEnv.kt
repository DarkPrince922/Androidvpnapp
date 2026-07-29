package com.darkprince.vpn.vpn

import android.content.Context
import com.darkprince.vpn.BuildConfig
import libv2ray.Libv2ray
import java.io.File

/**
 * Одноразовая инициализация окружения ядра Xray (нужна и для VPN, и для
 * пинга): распаковка геоданных из APK и initCoreEnv.
 */
object CoreEnv {
    @Volatile private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                copyGeoAssets(context)
                Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
                initialized = true
            }
        }
    }

    /**
     * Копирует geoip.dat/geosite.dat из assets в filesDir — они нужны ядру
     * для правил роутинга geoip:/geosite:. Повторное копирование — только
     * после обновления приложения (маркер с versionCode).
     */
    private fun copyGeoAssets(context: Context) {
        val marker = File(context.filesDir, "geodata.version")
        val version = BuildConfig.VERSION_CODE.toString()
        val upToDate = marker.takeIf { it.exists() }?.readText() == version
        var copiedAll = true
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val out = File(context.filesDir, name)
            if (upToDate && out.exists()) continue
            try {
                context.assets.open(name).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            } catch (_: Exception) {
                // ассет не вшит в сборку — правила geoip/geosite работать не будут
                copiedAll = false
            }
        }
        if (copiedAll) {
            try {
                marker.writeText(version)
            } catch (_: Exception) {
            }
        }
    }
}
