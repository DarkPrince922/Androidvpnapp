package com.darkprince.vpn

import android.app.Application
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.work.SubscriptionRefreshWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        SubscriptionRefreshWorker.schedule(this)
    }
}
