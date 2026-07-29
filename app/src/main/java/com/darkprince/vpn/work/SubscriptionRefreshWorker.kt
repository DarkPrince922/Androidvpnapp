package com.darkprince.vpn.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.darkprince.vpn.R
import com.darkprince.vpn.data.repo.SubscriptionUserInfo
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.MainActivity
import kotlinx.coroutines.flow.first
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
        // гостю подписку тоже нужно обновлять: аккаунта нет, но ссылка есть
        val hasAccount = ServiceLocator.authRepository.isLoggedIn
        val hasSharedSubscription = ServiceLocator.prefs.cachedGuestSubUrl != null
        if (!hasAccount && !hasSharedSubscription) return Result.success()
        return try {
            val (_, userInfo) = ServiceLocator.subscriptionRepository.fetchServers(forceRefresh = true)
            // держим в актуальном состоянии и остальные подписки пользователя
            ServiceLocator.subscriptionRepository.prefetchAllSubscriptions()
            checkExpiryNotification(userInfo)
            Result.success()
        } catch (_: Exception) {
            // сеть/сервер недоступны — попробуем в следующем цикле
            Result.retry()
        }
    }

    /** Уведомление «подписка заканчивается», не чаще раза в день. */
    private suspend fun checkExpiryNotification(userInfo: SubscriptionUserInfo?) {
        val daysLeft = try {
            ServiceLocator.subscriptionRepository.status().daysLeft
        } catch (_: Exception) {
            null
        } ?: userInfo?.expireUnix?.takeIf { it > 0 }?.let {
            ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
        } ?: return

        if (daysLeft > 3 || daysLeft < 0) return
        val today = java.time.LocalDate.now().toString()
        val prefs = ServiceLocator.prefs
        if (prefs.lastExpiryNotifyDayFlow.first() == today) return
        prefs.setLastExpiryNotifyDay(today)

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                "subscription",
                "Подписка",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (daysLeft) {
            0 -> "Подписка заканчивается сегодня. Продлите, чтобы не остаться без защиты."
            1 -> "Подписка заканчивается завтра. Продлите её в приложении."
            else -> "Подписка заканчивается через $daysLeft дн. Продлите её в приложении."
        }
        val notification = Notification.Builder(applicationContext, "subscription")
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("Подписка истекает")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(2, notification)
        } catch (_: SecurityException) {
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
