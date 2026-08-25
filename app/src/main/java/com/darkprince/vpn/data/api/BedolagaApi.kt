package com.darkprince.vpn.data.api

import com.darkprince.vpn.data.api.dto.AuthResponse
import com.darkprince.vpn.data.api.dto.BalanceResponse
import com.darkprince.vpn.data.api.dto.ConnectionLinkResponse
import com.darkprince.vpn.data.api.dto.DeepLinkPollRequest
import com.darkprince.vpn.data.api.dto.DeepLinkRequestResponse
import com.darkprince.vpn.data.api.dto.EmailLoginRequest
import com.darkprince.vpn.data.api.dto.EmailRegisterRequest
import com.darkprince.vpn.data.api.dto.ForgotPasswordRequest
import com.darkprince.vpn.data.api.dto.LogoutRequest
import com.darkprince.vpn.data.api.dto.NewsArticleDto
import com.darkprince.vpn.data.api.dto.NewsListResponse
import com.darkprince.vpn.data.api.dto.PaymentMethodDto
import com.darkprince.vpn.data.api.dto.DevicesListResponse
import com.darkprince.vpn.data.api.dto.DevicesPurchaseRequest
import com.darkprince.vpn.data.api.dto.RenameDeviceRequest
import com.darkprince.vpn.data.api.dto.PromoActivateRequest
import com.darkprince.vpn.data.api.dto.PromoActivateResponse
import com.darkprince.vpn.data.api.dto.PurchaseTariffRequest
import com.darkprince.vpn.data.api.dto.RawJson
import com.darkprince.vpn.data.api.dto.ReferralInfoResponse
import com.darkprince.vpn.data.api.dto.ReduceDevicesRequest
import com.darkprince.vpn.data.api.dto.RefreshRequest
import com.darkprince.vpn.data.api.dto.RenewRequest
import com.darkprince.vpn.data.api.dto.TrafficPurchaseRequest
import com.darkprince.vpn.data.api.dto.SubscriptionStatusResponse
import com.darkprince.vpn.data.api.dto.SubscriptionsListResponse
import com.darkprince.vpn.data.api.dto.SupportConfigDto
import com.darkprince.vpn.data.api.dto.SupportMediaUploadDto
import com.darkprince.vpn.data.api.dto.SupportMessageCreateRequest
import com.darkprince.vpn.data.api.dto.SupportMessageDto
import com.darkprince.vpn.data.api.dto.SupportTicketCreateRequest
import com.darkprince.vpn.data.api.dto.SupportTicketDetailDto
import com.darkprince.vpn.data.api.dto.SupportTicketListDto
import com.darkprince.vpn.data.api.dto.SupportUnreadCountDto
import com.darkprince.vpn.data.api.dto.TopupRequest
import com.darkprince.vpn.data.api.dto.TopupResponse
import com.darkprince.vpn.data.api.dto.TransactionsResponse
import com.darkprince.vpn.data.api.dto.TrialInfoResponse
import com.darkprince.vpn.data.api.dto.UserDto
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Cabinet API бота Bedolaga (remnawave-bedolaga-telegram-bot v3.33+).
 * Базовый URL задаётся пользователем, пути начинаются с cabinet/.
 */
interface BedolagaApi {

    // --- Авторизация ---

    @POST("cabinet/auth/deeplink/request")
    suspend fun deepLinkRequest(): DeepLinkRequestResponse

    /** 200 — токены, 202 — ожидание подтверждения, 410 — токен истёк. */
    @POST("cabinet/auth/deeplink/poll")
    suspend fun deepLinkPoll(@Body body: DeepLinkPollRequest): Response<AuthResponse>

    @POST("cabinet/auth/email/login")
    suspend fun emailLogin(@Body body: EmailLoginRequest): AuthResponse

    @POST("cabinet/auth/email/register/standalone")
    suspend fun emailRegister(@Body body: EmailRegisterRequest): AuthResponse

    @POST("cabinet/auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): RawJson

    @POST("cabinet/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResponse

    @POST("cabinet/auth/logout")
    suspend fun logout(@Body body: LogoutRequest): RawJson

    @GET("cabinet/auth/me")
    suspend fun me(): UserDto

    // --- Подписка ---

    /** Список подписок пользователя (мультитариф). */
    @GET("cabinet/subscriptions")
    suspend fun subscriptions(): SubscriptionsListResponse

    @GET("cabinet/subscription")
    suspend fun subscription(): SubscriptionStatusResponse

    @GET("cabinet/subscription/connection-link")
    suspend fun connectionLink(): ConnectionLinkResponse

    @GET("cabinet/subscription/trial")
    suspend fun trialInfo(): TrialInfoResponse

    @POST("cabinet/subscription/trial")
    suspend fun activateTrial(@Body body: RawJson): RawJson

    @GET("cabinet/subscription/purchase-options")
    suspend fun purchaseOptions(): JsonElement

    @POST("cabinet/subscription/purchase-tariff")
    suspend fun purchaseTariff(@Body body: PurchaseTariffRequest): RawJson

    // --- Устройства и трафик ---

    // subscription_id — для мультитарифа: какой подписке принадлежат устройства

    @GET("cabinet/subscription/devices")
    suspend fun devices(@Query("subscription_id") subscriptionId: Long? = null): RawJson

    @GET("cabinet/subscription/devices/price")
    suspend fun devicePrice(
        @Query("devices") devices: Int = 1,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @POST("cabinet/subscription/devices/purchase")
    suspend fun purchaseDevices(
        @Body body: DevicesPurchaseRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @GET("cabinet/subscription/devices/reduction-info")
    suspend fun deviceReductionInfo(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @POST("cabinet/subscription/devices/reduce")
    suspend fun reduceDevices(
        @Body body: ReduceDevicesRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    /** Подключённые устройства подписки — с идентификаторами для удаления. */
    @GET("cabinet/subscription/devices")
    suspend fun devicesList(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): DevicesListResponse

    @DELETE("cabinet/subscription/devices/{hwid}")
    suspend fun deleteDevice(
        @Path("hwid") hwid: String,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @DELETE("cabinet/subscription/devices")
    suspend fun deleteAllDevices(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @PATCH("cabinet/subscription/devices/{hwid}/name")
    suspend fun renameDevice(
        @Path("hwid") hwid: String,
        @Body body: RenameDeviceRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @GET("cabinet/subscription/traffic-packages")
    suspend fun trafficPackages(): JsonElement

    @POST("cabinet/subscription/traffic")
    suspend fun purchaseTraffic(@Body body: TrafficPurchaseRequest): RawJson

    @GET("cabinet/subscription/renewal-options")
    suspend fun renewalOptions(): JsonElement

    @POST("cabinet/subscription/renew")
    suspend fun renew(@Body body: RenewRequest): RawJson

    // --- Рефералка и промокоды ---

    // ---------- Новости ----------

    @GET("cabinet/news")
    suspend fun news(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("category") category: String? = null,
    ): NewsListResponse

    @GET("cabinet/news/{slug}")
    suspend fun newsArticle(@Path("slug") slug: String): NewsArticleDto

    @GET("cabinet/referral")
    suspend fun referralInfo(): ReferralInfoResponse

    @POST("cabinet/promocode/activate")
    suspend fun activatePromocode(@Body body: PromoActivateRequest): PromoActivateResponse

    // --- Баланс и оплата ---

    @GET("cabinet/balance")
    suspend fun balance(): BalanceResponse

    @GET("cabinet/balance/payment-methods")
    suspend fun paymentMethods(): JsonElement

    @POST("cabinet/balance/topup")
    suspend fun topup(@Body body: TopupRequest): TopupResponse

    @GET("cabinet/balance/transactions")
    suspend fun transactions(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): TransactionsResponse

    @POST("cabinet/balance/pending-payments/{method}/{paymentId}/check")
    suspend fun checkPendingPayment(
        @Path("method") method: String,
        @Path("paymentId") paymentId: String,
    ): RawJson

    // --- Техподдержка ---

    /** Публичный режим поддержки: тикеты, внешний контакт или оба варианта. */
    @GET("cabinet/info/support-config")
    suspend fun supportConfig(): SupportConfigDto

    @GET("cabinet/tickets")
    suspend fun supportTickets(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("status") status: String? = null,
    ): SupportTicketListDto

    @POST("cabinet/tickets")
    suspend fun createSupportTicket(
        @Body body: SupportTicketCreateRequest,
    ): SupportTicketDetailDto

    @GET("cabinet/tickets/{ticketId}")
    suspend fun supportTicket(
        @Path("ticketId") ticketId: Long,
    ): SupportTicketDetailDto

    @POST("cabinet/tickets/{ticketId}/messages")
    suspend fun addSupportMessage(
        @Path("ticketId") ticketId: Long,
        @Body body: SupportMessageCreateRequest,
    ): SupportMessageDto

    @GET("cabinet/tickets/notifications/unread-count")
    suspend fun supportUnreadCount(): SupportUnreadCountDto

    @POST("cabinet/tickets/notifications/ticket/{ticketId}/read")
    suspend fun markSupportTicketRead(
        @Path("ticketId") ticketId: Long,
    ): RawJson

    @Multipart
    @POST("cabinet/media/upload")
    suspend fun uploadSupportMedia(
        @Part file: MultipartBody.Part,
        @Part("media_type") mediaType: RequestBody,
    ): SupportMediaUploadDto
}
