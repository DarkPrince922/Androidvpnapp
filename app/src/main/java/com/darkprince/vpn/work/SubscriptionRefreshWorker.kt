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
        val repo = ServiceLocator.subscriptionRepository
        val fresh = try {
            val (_, userInfo) = repo.fetchServers(forceRefresh = true)
            // держим в актуальном состоянии и остальные подписки пользователя
            repo.prefetchAllSubscriptions()
            userInfo
        } catch (_: Exception) {
            null
        }
        // Напоминания — даже если серверы скачать не удалось: срок знает
        // кабинет, и истекающая подписка — как раз частая причина отказа.
        runCatching { ExpiryNotifier(applicationContext).check(fresh ?: repo.cachedServers()?.second) }
        runCatching { checkTicketNotifications() }
        // сеть/сервер недоступны — попробуем в следующем цикле
        return if (fresh != null) Result.success() else Result.retry()
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
                    open = "support",
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
                open = "admin",
            )
        }
        prefs.setAdminTicketsNotified(unread)
    }

    private fun notify(id: Int, title: String, text: String, open: String? = null) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                "support",
                "Поддержка",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        open?.let { intent.putExtra(MainActivity.EXTRA_OPEN, it) }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
