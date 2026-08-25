package com.darkprince.vpn

import android.app.Application
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.work.AutoConnect
import com.darkprince.vpn.work.SubscriptionRefreshWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        SubscriptionRefreshWorker.schedule(this)
        // Ожидание сети восстанавливаем при каждом запуске: одноразовое
        // задание живёт до первого срабатывания, и если система успела его
        // выполнить и потерять, поставить новое больше некому.
        AutoConnect.sync(this, ServiceLocator.prefs.autoConnectBlocking())
    }
}
