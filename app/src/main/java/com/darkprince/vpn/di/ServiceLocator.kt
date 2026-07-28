package com.darkprince.vpn.di

import android.content.Context
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.data.repo.AuthRepository
import com.darkprince.vpn.data.repo.BalanceRepository
import com.darkprince.vpn.data.repo.SubscriptionRepository

object ServiceLocator {
    lateinit var prefs: AppPrefs
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var subscriptionRepository: SubscriptionRepository
        private set
    lateinit var balanceRepository: BalanceRepository
        private set

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = AppPrefs(context.applicationContext)
        prefs.warmUp()
        apiClient = ApiClient(prefs)
        authRepository = AuthRepository(apiClient, prefs)
        subscriptionRepository = SubscriptionRepository(apiClient, prefs)
        balanceRepository = BalanceRepository(apiClient)
    }
}
