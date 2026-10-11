package com.darkprince.vpn.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.darkprince.vpn.R
import com.darkprince.vpn.data.api.dto.SubscriptionListItem
import com.darkprince.vpn.data.repo.SubscriptionUserInfo
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.MainActivity
import com.darkprince.vpn.ui.screens.formatKopeks
import kotlinx.coroutines.flow.first

/**
 * Напоминания о конце подписки — по каждой подписке человека.
 *
 * Правила:
 * - за 3 дня и меньше — раз в день, с названием подписки;
 * - автопродление включено и денег хватает (или платит банк по СБП) —
 *   не тормошим зря: одно спокойное «продлится автоматически» накануне;
 * - автопродление включено, а денег не хватает — говорим, сколько
 *   доложить, иначе оно не пройдёт;
 * - суточный тариф пропускаем: у него до конца всегда меньше суток, и
 *   напоминание приходило бы каждый день;
 * - подписка закончилась — говорим один раз;
 * - нажатие ведёт в «Тарифы», где кнопка продления.
 */
internal class ExpiryNotifier(private val context: Context) {
    private val prefs get() = ServiceLocator.prefs

    suspend fun check(cachedInfo: SubscriptionUserInfo?) {
        if (!ServiceLocator.authRepository.isLoggedIn) {
            // гость по чужой ссылке: списка подписок нет, срок — из заголовка подписки
            guestCheck(cachedInfo)
            return
        }
        val subs = ServiceLocator.subscriptionRepository.subscriptions() ?: return
        val balance = runCatching { ServiceLocator.balanceRepository.balance().balanceKopeks }.getOrNull()
        val today = java.time.LocalDate.now().toString()
        val notifiedToday = notifiedToday(today).toMutableSet()
        val expiredDone = prefs.expiredNotifiedFlow.first().orEmpty()
            .split(',').filter { it.isNotBlank() }.toMutableSet()
        val many = subs.size > 1

        for (sub in subs) {
            if (sub.isDaily == true) continue
            val days = sub.endDate?.let(::daysUntil) ?: continue
            val name = if (many) "«${sub.displayName}»" else null

            if (days < 0 || sub.status.equals("expired", ignoreCase = true)) {
                // о конце — один раз на каждую дату окончания
                val mark = "${sub.id}@${sub.endDate}"
                if (mark in expiredDone || days < -3) continue
                expiredDone += mark
                post(
                    id = 100 + sub.id.toInt() % 1000,
                    title = "Подписка закончилась",
                    text = (name?.let { "Подписка $it закончилась. " } ?: "Подписка закончилась. ") +
                        "VPN не работает — продлите её в приложении.",
                )
                continue
            }
            if (days > 3 || sub.id in notifiedToday) continue

            val text = textFor(sub, days, name, balance) ?: continue
            notifiedToday += sub.id
            post(id = 100 + sub.id.toInt() % 1000, title = text.first, text = text.second)
        }

        prefs.setLastExpiryNotifyDay("$today|" + notifiedToday.joinToString(","))
        // помним только свежие окончания, чтобы строка не росла бесконечно
        prefs.setExpiredNotified(expiredDone.toList().takeLast(30).joinToString(","))
    }

    /** Заголовок и текст; null — сегодня молчим. */
    private suspend fun textFor(
        sub: SubscriptionListItem,
        days: Int,
        name: String?,
        balance: Long?,
    ): Pair<String, String>? {
        val whenText = when (days) {
            0 -> "сегодня"
            1 -> "завтра"
            else -> "через $days ${daysWord(days)}"
        }
        val subject = name?.let { "Подписка $it" } ?: "Подписка"

        if (sub.autopayEnabled == true || sub.isTrial != true) {
            val autopay = runCatching { ServiceLocator.subscriptionRepository.autopayInfo(sub.id) }.getOrNull()
            if (autopay?.recurrentOn == true) {
                if (days > 1) return null
                return "Подписка продлится автоматически" to
                    "$subject заканчивается $whenText — оплату спишет банк по СБП, ничего делать не нужно."
            }
            if (autopay?.enabled == true || sub.autopayEnabled == true) {
                val price = runCatching { ServiceLocator.subscriptionRepository.renewalOptions(sub.id) }
                    .getOrNull()?.minOfOrNull { it.priceKopeks }
                if (price != null && balance != null && balance < price) {
                    return "Не хватит на автопродление" to
                        "$subject заканчивается $whenText. Для автопродления на балансе не хватает " +
                        "${formatKopeks(price - balance)} — пополните баланс."
                }
                if (days > 1) return null
                return "Подписка продлится автоматически" to
                    "$subject заканчивается $whenText — спишем оплату с баланса автоматически."
            }
        }
        val title = if (sub.isTrial == true) "Пробный период заканчивается" else "Подписка истекает"
        val action = if (sub.isTrial == true) "Выберите тариф, чтобы VPN продолжил работать."
        else "Продлите её, чтобы не остаться без VPN."
        return title to "$subject заканчивается $whenText. $action"
    }

    private suspend fun notifiedToday(today: String): Set<Long> {
        val raw = prefs.lastExpiryNotifyDayFlow.first().orEmpty()
        if (raw.substringBefore('|') != today) return emptySet()
        return raw.substringAfter('|', "").split(',').mapNotNull { it.toLongOrNull() }.toSet()
    }

    private suspend fun guestCheck(info: SubscriptionUserInfo?) {
        val expire = info?.expireUnix?.takeIf { it > 0 } ?: return
        val days = ((expire * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
        if (days > 3 || days < 0) return
        val today = java.time.LocalDate.now().toString()
        if (prefs.lastExpiryNotifyDayFlow.first()?.substringBefore('|') == today) return
        prefs.setLastExpiryNotifyDay(today)
        post(
            id = 2,
            title = "Подписка истекает",
            text = "Подписка, которой с вами поделились, заканчивается " +
                (if (days == 0) "сегодня" else if (days == 1) "завтра" else "через $days ${daysWord(days)}") +
                ". Напомните владельцу продлить её.",
            openPlans = false,
        )
    }

    private fun post(id: Int, title: String, text: String, openPlans: Boolean = true) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("subscription", "Подписка", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (openPlans) intent.putExtra(MainActivity.EXTRA_OPEN, "plans")
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, "subscription")
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

    private fun daysWord(days: Int): String = when {
        days % 10 == 1 && days % 100 != 11 -> "день"
        days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
        else -> "дней"
    }

    /** Дней до даты окончания по часам телефона; отрицательное — уже прошло. */
    private fun daysUntil(endDate: String): Int? = try {
        val zone = java.time.ZoneId.systemDefault()
        val end = runCatching { java.time.OffsetDateTime.parse(endDate).toInstant() }
            .recoverCatching { java.time.LocalDateTime.parse(endDate).toInstant(java.time.ZoneOffset.UTC) }
            .map { it.atZone(zone).toLocalDate() }
            .getOrElse { java.time.LocalDate.parse(endDate.take(10)) }
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(zone), end).toInt()
    } catch (_: Exception) {
        null
    }
}
