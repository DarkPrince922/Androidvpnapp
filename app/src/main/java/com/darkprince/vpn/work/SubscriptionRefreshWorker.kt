package com.darkprince.vpn.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.darkprince.vpn.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Фоновое обновление подписки Remnawave раз в час: скачивает свежий список
 * серверов и кладёт в кэш, чтобы приложение всегда стартовало с актуальными
 * конфигами — даже если было закрыто.
 */
class SubscriptionRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.init(applicationContext)
        if (!ServiceLocator.authRepository.isLoggedIn) return Result.success()
        return try {
            ServiceLocator.subscriptionRepository.fetchServers(forceRefresh = true)
            Result.success()
        } catch (_: Exception) {
            // сеть/сервер недоступны — попробуем в следующем цикле
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "subscription_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
