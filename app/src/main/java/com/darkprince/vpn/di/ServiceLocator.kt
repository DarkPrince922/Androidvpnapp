package com.darkprince.vpn.di

import android.content.Context
import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.data.repo.AdminRepository
import com.darkprince.vpn.data.repo.AuthRepository
import com.darkprince.vpn.data.repo.BalanceRepository
import com.darkprince.vpn.data.repo.NewsRepository
import com.darkprince.vpn.data.repo.SubscriptionRepository
import com.darkprince.vpn.data.repo.SupportRepository
import com.darkprince.vpn.data.update.AppUpdater

object ServiceLocator {
    lateinit var appContext: Context
        private set
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
    lateinit var supportRepository: SupportRepository

    lateinit var newsRepository: NewsRepository

    lateinit var adminRepository: AdminRepository
        private set
    lateinit var appUpdater: AppUpdater
        private set

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = AppPrefs(context.applicationContext)
        prefs.warmUp()
        apiClient = ApiClient(prefs)
        authRepository = AuthRepository(apiClient, prefs)
        subscriptionRepository = SubscriptionRepository(apiClient, prefs)
        balanceRepository = BalanceRepository(apiClient)
        supportRepository = SupportRepository(apiClient, prefs, appContext)
        newsRepository = NewsRepository(apiClient, prefs)
        adminRepository = AdminRepository(apiClient, prefs)
        // клиент тот же, что для подписки: при поднятом туннеле он ходит через
        // ядро, поэтому обновление доедет и там, где сайт заблокирован
        appUpdater = AppUpdater(appContext, apiClient.plainOkHttp)
    }
}
