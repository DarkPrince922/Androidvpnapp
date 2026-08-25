package com.darkprince.vpn.data.repo

import com.darkprince.vpn.data.api.ApiClient
import com.darkprince.vpn.data.api.dto.AdminBalanceRequest
import com.darkprince.vpn.data.api.dto.AdminBalanceResponse
import com.darkprince.vpn.data.api.dto.AdminDashboardDto
import com.darkprince.vpn.data.api.dto.AdminExtendRequest
import com.darkprince.vpn.data.api.dto.AdminPermissionsDto
import com.darkprince.vpn.data.api.dto.AdminReplyRequest
import com.darkprince.vpn.data.api.dto.AdminStatusRequest
import com.darkprince.vpn.data.api.dto.AdminTicketDetailDto
import com.darkprince.vpn.data.api.dto.AdminTicketDto
import com.darkprince.vpn.data.api.dto.AdminUserDto
import com.darkprince.vpn.data.api.dto.AdminTicketStatsDto
import com.darkprince.vpn.data.prefs.AppPrefs
import retrofit2.HttpException
import java.io.IOException

/**
 * Админская часть кабинета.
 *
 * Права здесь — только про то, что показать: решает всё равно сервер, и
 * устаревший клиент лишнего не сделает, он лишь нарисует кнопку, которая
 * ответит 403. Поэтому права перечитываются при каждом входе во вкладку —
 * так снятая в панели роль исчезает из приложения за секунды, а не когда
 * протухнет токен.
 */
class AdminRepository(
    private val client: ApiClient,
    private val prefs: AppPrefs,
) {
    private val api get() = client.api

    val isLoggedIn: Boolean get() = prefs.cachedRefreshToken != null

    /**
     * Админ ли текущий аккаунт.
     *
     * Ошибку не глотаем: «вкладки нет» и «вкладку не удалось спросить» — это
     * разные вещи, и без различия между ними непонятно, куда смотреть.
     * Ронять из-за неё запуск всё равно нельзя, поэтому причина возвращается
     * рядом с ответом, а не бросается наверх.
     */
    suspend fun isAdmin(): AdminCheck {
        if (!isLoggedIn) return AdminCheck(admin = false, reason = "нет входа в аккаунт")
        return try {
            val admin = api.isAdmin().isAdmin
            AdminCheck(
                admin = admin,
                reason = if (admin) "доступ есть" else "сервер ответил: не админ",
            )
        } catch (error: Exception) {
            AdminCheck(admin = false, reason = adminErrorMessage(error))
        }
    }

    suspend fun permissions(): AdminPermissionsDto = api.adminPermissions()

    suspend fun tickets(status: String? = null, page: Int = 1): List<AdminTicketDto> =
        api.adminTickets(page = page, perPage = PAGE, status = status).items

    suspend fun stats(): AdminTicketStatsDto = api.adminTicketStats()

    suspend fun ticket(id: Long): AdminTicketDetailDto = api.adminTicket(id)

    suspend fun reply(id: Long, message: String) {
        api.adminReply(id, AdminReplyRequest(message))
    }

    suspend fun setStatus(id: Long, status: String) {
        api.adminTicketStatus(id, AdminStatusRequest(status))
    }

    // ---------- сводка и люди ----------

    suspend fun dashboard(): AdminDashboardDto = api.adminDashboard()

    suspend fun users(search: String?): List<AdminUserDto> =
        api.adminUsers(search = search?.takeIf { it.isNotBlank() }, limit = PAGE).users

    suspend fun addBalance(userId: Long, amountKopeks: Long): AdminBalanceResponse =
        api.adminUpdateBalance(userId, AdminBalanceRequest(amountKopeks))

    suspend fun extendSubscription(userId: Long, days: Int) {
        api.adminExtendSubscription(userId, AdminExtendRequest(days = days))
    }

    companion object {
        const val PAGE = 30

        // права, которые нас интересуют
        const val TICKETS_READ = "tickets:read"
        const val TICKETS_REPLY = "tickets:reply"
        const val TICKETS_CLOSE = "tickets:close"
        const val STATS_READ = "stats:read"
        const val USERS_READ = "users:read"
        const val USERS_BALANCE = "users:balance"
        const val USERS_SUBSCRIPTION = "users:subscription"
    }
}

/** Ответ на вопрос «админ ли я» вместе с причиной, если нет. */
data class AdminCheck(val admin: Boolean, val reason: String)

/**
 * Человеческий текст ошибки.
 *
 * 403 здесь — не сбой, а нормальный ответ: роль в панели могли изменить
 * минуту назад, и приложение об этом ещё не знало.
 */
fun adminErrorMessage(error: Throwable): String = when {
    error is IOException -> "Нет соединения"
    error !is HttpException -> error.message ?: "Не удалось выполнить действие"
    error.code() == 401 -> "Сессия истекла. Войдите ещё раз"
    error.code() == 403 -> "Недостаточно прав — роль могли изменить в панели"
    error.code() == 404 -> "Обращение не найдено"
    error.code() == 400 -> "Сервер не принял запрос: проверьте текст"
    else -> "Ошибка сервера (${error.code()})"
}
