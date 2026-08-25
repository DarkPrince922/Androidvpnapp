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
            checkTicketNotifications()
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

    /**
     * Ответ поддержки и — отдельно — новое обращение для админа.
     *
     * Настоящих пушей у приложения нет: Firebase не подключён, а раздаётся
     * оно мимо Play, где сервисов Google может не быть вовсе. Поэтому
     * спрашиваем сами, но не заводим ради этого второй фоновый заход:
     * подписка и так обновляется раз в час, и проверка едет с ней.
     *
     * Кабинет считает непрочитанное сам, отдельной таблицей уведомлений, —
     * сравнивать списки тикетов на телефоне не нужно. Запоминаем, о скольких
     * уже оповестили, иначе одно и то же сообщение всплывало бы каждый час.
     */
    private suspend fun checkTicketNotifications() {
        if (!ServiceLocator.authRepository.isLoggedIn) return
        val prefs = ServiceLocator.prefs

        val unread = try {
            ServiceLocator.supportRepository.unreadCount()
        } catch (_: Exception) {
            null
        }
        if (unread != null) {
            if (unread > prefs.supportNotifiedFlow.first()) {
                notify(
                    id = 3,
                    title = "Поддержка ответила",
                    text = if (unread == 1) "Есть новый ответ по вашему обращению."
                    else "Новых ответов: $unread.",
                )
            }
            prefs.setSupportNotified(unread)
        }

        checkAdminTickets()
    }

    /**
     * Админу — про чужие обращения. Спрашиваем только если человек и правда
     * админ: у остальных этот адрес ответит отказом, и ходить туда каждый час
     * незачем.
     */
    private suspend fun checkAdminTickets() {
        val admin = ServiceLocator.adminRepository
        if (!admin.isAdmin().admin) return
        val prefs = ServiceLocator.prefs

        val unread = try {
            admin.ticketUnreadCount()
        } catch (_: Exception) {
            return
        }
        if (unread > prefs.adminTicketsNotifiedFlow.first()) {
            // текст уведомления кабинет уже собрал — с номером тикета и темой
            val newest = try {
                admin.ticketNotifications().firstOrNull()?.message
            } catch (_: Exception) {
                null
            }
            notify(
                id = 4,
                title = if (unread == 1) "Новое обращение" else "Обращений без ответа: $unread",
                text = newest ?: "Откройте «Панель», чтобы ответить.",
            )
        }
        prefs.setAdminTicketsNotified(unread)
    }

    private fun notify(id: Int, title: String, text: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                "support",
                "Поддержка",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(applicationContext, "support")
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
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
