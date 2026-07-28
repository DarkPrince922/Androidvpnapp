package com.darkprince.vpn.vpn

import android.content.Context
import libv2ray.Libv2ray

/** Одноразовая инициализация окружения ядра Xray (нужна и для VPN, и для пинга). */
object CoreEnv {
    @Volatile private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
                initialized = true
            }
        }
    }
}
