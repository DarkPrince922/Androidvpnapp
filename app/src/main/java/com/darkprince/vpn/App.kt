package com.darkprince.vpn

import android.app.Application
import com.darkprince.vpn.di.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
