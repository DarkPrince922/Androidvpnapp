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

    lateinit var pendingPurchases: com.darkprince.vpn.data.repo.PendingPurchaseStore
        private set
    lateinit var supportRepository: SupportRepository

    lateinit var newsRepository: NewsRepository

    lateinit var adminRepository: AdminRepository
        private set
    lateinit var giftRepository: com.darkprince.vpn.data.repo.GiftRepository
        private set
    lateinit var promoRepository: com.darkprince.vpn.data.repo.PromoRepository
        private set
    lateinit var referralRepository: com.darkprince.vpn.data.repo.ReferralRepository
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
        pendingPurchases = com.darkprince.vpn.data.repo.PendingPurchaseStore(prefs)
        supportRepository = SupportRepository(apiClient, prefs, appContext)
        newsRepository = NewsRepository(apiClient, prefs)
        adminRepository = AdminRepository(apiClient, prefs)
        giftRepository = com.darkprince.vpn.data.repo.GiftRepository(apiClient)
        promoRepository = com.darkprince.vpn.data.repo.PromoRepository(apiClient)
        referralRepository = com.darkprince.vpn.data.repo.ReferralRepository(apiClient)
        com.darkprince.vpn.data.api.ServiceHealth.init(
            ping = { apiClient.api.branding() },
            // Тем же клиентом, что и подписка: при поднятом туннеле он идёт
            // через ядро — и тогда белые списки ему не мешают, как и кабинету.
            reach = { url ->
                val client = apiClient.plainOkHttp.newBuilder()
                    .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        client.newCall(okhttp3.Request.Builder().url(url).head().build()).execute()
                            .use { true }
                    }.getOrDefault(false)
                }
            },
        )
        // клиент тот же, что для подписки: при поднятом туннеле он ходит через
        // ядро, поэтому обновление доедет и там, где сайт заблокирован
        appUpdater = AppUpdater(appContext, apiClient.plainOkHttp)
    }
}
