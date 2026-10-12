package com.darkprince.vpn.data.api

import com.darkprince.vpn.data.api.dto.*
import com.darkprince.vpn.data.api.dto.MergeResultDto
import com.darkprince.vpn.data.api.dto.MergeExecuteRequest
import com.darkprince.vpn.data.api.dto.MergePreviewDto
import com.darkprince.vpn.data.api.dto.MergeCodeRequest
import com.darkprince.vpn.data.api.dto.EmailLinkResponse
import com.darkprince.vpn.data.api.dto.EmailLinkRequest
import com.darkprince.vpn.data.api.dto.AdminAvailableTariffsDto
import com.darkprince.vpn.data.api.dto.AdminBalanceRequest
import com.darkprince.vpn.data.api.dto.AdminSubscriptionAction
import com.darkprince.vpn.data.api.dto.AdminUserDetailDto
import com.darkprince.vpn.data.api.dto.AdminBalanceResponse
import com.darkprince.vpn.data.api.dto.AdminDashboardDto
import com.darkprince.vpn.data.api.dto.AdminDevicesDto
import com.darkprince.vpn.data.api.dto.AdminExtendRequest
import com.darkprince.vpn.data.api.dto.AdminMessageRequest
import com.darkprince.vpn.data.api.dto.AdminPermissionsDto
import com.darkprince.vpn.data.api.dto.AdminPromoRequest
import com.darkprince.vpn.data.api.dto.AdminReplyRequest
import com.darkprince.vpn.data.api.dto.AdminStatusRequest
import com.darkprince.vpn.data.api.dto.AdminTicketDetailDto
import com.darkprince.vpn.data.api.dto.AdminTicketListDto
import com.darkprince.vpn.data.api.dto.AdminTicketNotificationsDto
import com.darkprince.vpn.data.api.dto.AdminTicketStatsDto
import com.darkprince.vpn.data.api.dto.AdminTransactionsDto
import com.darkprince.vpn.data.api.dto.AdminUsersListDto
import com.darkprince.vpn.data.api.dto.AuthResponse
import com.darkprince.vpn.data.api.dto.BalanceResponse
import com.darkprince.vpn.data.api.dto.ConnectionLinkResponse
import com.darkprince.vpn.data.api.dto.DeepLinkPollRequest
import com.darkprince.vpn.data.api.dto.DeepLinkRequestResponse
import com.darkprince.vpn.data.api.dto.EmailLoginRequest
import com.darkprince.vpn.data.api.dto.EmailRegisterRequest
import com.darkprince.vpn.data.api.dto.ForgotPasswordRequest
import com.darkprince.vpn.data.api.dto.IsAdminDto
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

    /** Привязать почту к вошедшему аккаунту (не путать с /register/standalone). */
    @POST("cabinet/auth/email/register")
    suspend fun linkEmail(@Body body: EmailLinkRequest): EmailLinkResponse

    @POST("cabinet/auth/email/merge/verify")
    suspend fun verifyEmailMerge(@Body body: MergeCodeRequest): EmailLinkResponse

    @GET("cabinet/auth/merge/{token}")
    suspend fun mergePreview(@Path("token") token: String): MergePreviewDto

    @POST("cabinet/auth/merge/{token}")
    suspend fun executeMerge(@Path("token") token: String, @Body body: MergeExecuteRequest): MergeResultDto

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

    // subscription_id обязателен при нескольких подписках: без него кабинет
    // сам выбирает «текущую», и продление уходит не в ту, на которую нажали

    @GET("cabinet/subscription/traffic-packages")
    suspend fun trafficPackages(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): JsonElement

    @POST("cabinet/subscription/traffic")
    suspend fun purchaseTraffic(
        @Body body: TrafficPurchaseRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @GET("cabinet/subscription/renewal-options")
    suspend fun renewalOptions(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): JsonElement

    @POST("cabinet/subscription/renew")
    suspend fun renew(
        @Body body: RenewRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    // --- Автопродление, смена тарифа, перевыпуск ссылки ---

    @GET("cabinet/subscription")
    suspend fun subscriptionAutopay(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): SubscriptionAutopayDto

    @PATCH("cabinet/subscription/autopay")
    suspend fun updateAutopay(
        @Body body: AutopayRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): AutopayResponse

    /** 403 — автоплатёж через СБП в боте выключен. */
    @GET("cabinet/subscription/platega-recurrent")
    suspend fun recurrentStatus(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RecurrentStatusDto

    @POST("cabinet/subscription/platega-recurrent/enable")
    suspend fun enableRecurrent(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RecurrentEnableResponse

    @POST("cabinet/subscription/platega-recurrent/cancel")
    suspend fun cancelRecurrent(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    @POST("cabinet/subscription/tariff/switch/preview")
    suspend fun tariffSwitchPreview(
        @Body body: TariffSwitchRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): TariffSwitchPreviewDto

    @POST("cabinet/subscription/tariff/switch")
    suspend fun tariffSwitch(
        @Body body: TariffSwitchRequest,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RawJson

    /** Удалить истёкшую или отключённую подписку. Действующую бот не удаляет. */
    @DELETE("cabinet/subscriptions/{id}")
    suspend fun deleteSubscription(@Path("id") subscriptionId: Long): JsonElement

    @POST("cabinet/subscription/revoke")
    suspend fun revokeSubscription(
        @Query("subscription_id") subscriptionId: Long? = null,
    ): RevokeResponse

    // --- Подарки ---

    @GET("cabinet/gift/config")
    suspend fun giftConfig(): GiftConfigDto

    @POST("cabinet/gift/purchase")
    suspend fun giftPurchase(@Body body: GiftPurchaseRequest): GiftPurchaseResponse

    @GET("cabinet/gift/purchase/{token}")
    suspend fun giftPurchaseStatus(@Path("token") token: String): GiftPurchaseStatusDto

    @GET("cabinet/gift/sent")
    suspend fun giftsSent(): List<SentGiftDto>

    @GET("cabinet/gift/received")
    suspend fun giftsReceived(): List<ReceivedGiftDto>

    @POST("cabinet/gift/activate")
    suspend fun giftActivate(@Body body: GiftActivateRequest): GiftActivateResponse

    // --- Скидки и уровни ---

    @GET("cabinet/promo/offers")
    suspend fun promoOffers(): List<PromoOfferDto>

    @GET("cabinet/promo/active-discount")
    suspend fun activeDiscount(): ActiveDiscountDto

    @POST("cabinet/promo/claim")
    suspend fun claimOffer(@Body body: ClaimOfferRequest): ClaimOfferResponse

    @GET("cabinet/promo/group-discounts")
    suspend fun groupDiscounts(): GroupDiscountsDto

    @GET("cabinet/promo/loyalty-tiers")
    suspend fun loyaltyTiers(): LoyaltyTiersDto

    // --- Рефералка: приглашённые, начисления, вывод ---

    @GET("cabinet/referral/terms")
    suspend fun referralTerms(): ReferralTermsDto

    @GET("cabinet/referral/list")
    suspend fun referralList(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ReferralListDto

    @GET("cabinet/referral/earnings")
    suspend fun referralEarnings(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ReferralEarningsDto

    @GET("cabinet/referral/withdrawal/balance")
    suspend fun withdrawalBalance(): WithdrawalBalanceDto

    @POST("cabinet/referral/withdrawal/create")
    suspend fun createWithdrawal(@Body body: WithdrawalCreateRequest): RawJson

    @GET("cabinet/referral/withdrawal/history")
    suspend fun withdrawalHistory(): WithdrawalListDto

    @POST("cabinet/referral/withdrawal/{id}/cancel")
    suspend fun cancelWithdrawal(@Path("id") id: Long): RawJson

    // --- Рефералка и промокоды ---

    // ---------- Админка ----------

    /** Открытая страница кабинета — ею проверяем, ожил ли он. */
    @GET("cabinet/branding")
    suspend fun branding(): JsonElement

    @GET("cabinet/auth/me/is-admin")
    suspend fun isAdmin(): IsAdminDto

    @GET("cabinet/auth/me/permissions")
    suspend fun adminPermissions(): AdminPermissionsDto

    @GET("cabinet/admin/tickets")
    suspend fun adminTickets(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("status") status: String? = null,
    ): AdminTicketListDto

    @GET("cabinet/admin/tickets/stats")
    suspend fun adminTicketStats(): AdminTicketStatsDto

    @GET("cabinet/admin/tickets/{ticketId}")
    suspend fun adminTicket(@Path("ticketId") ticketId: Long): AdminTicketDetailDto

    @POST("cabinet/admin/tickets/{ticketId}/reply")
    suspend fun adminReply(
        @Path("ticketId") ticketId: Long,
        @Body body: AdminReplyRequest,
    ): JsonElement

    @POST("cabinet/admin/tickets/{ticketId}/status")
    suspend fun adminTicketStatus(
        @Path("ticketId") ticketId: Long,
        @Body body: AdminStatusRequest,
    ): JsonElement

    @GET("cabinet/admin/stats/dashboard")
    suspend fun adminDashboard(): AdminDashboardDto

    @GET("cabinet/admin/users")
    suspend fun adminUsers(
        @Query("search") search: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("subscription_status") subscriptionStatus: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): AdminUsersListDto

    @POST("cabinet/admin/users/{userId}/balance")
    suspend fun adminUpdateBalance(
        @Path("userId") userId: Long,
        @Body body: AdminBalanceRequest,
    ): AdminBalanceResponse

    @POST("cabinet/admin/users/{userId}/subscription")
    suspend fun adminExtendSubscription(
        @Path("userId") userId: Long,
        @Body body: AdminExtendRequest,
    ): JsonElement

    @GET("cabinet/admin/users/{userId}")
    suspend fun adminUser(@Path("userId") userId: Long): AdminUserDetailDto

    @POST("cabinet/admin/users/{userId}/subscription")
    suspend fun adminSubscriptionAction(
        @Path("userId") userId: Long,
        @Body body: AdminSubscriptionAction,
    ): JsonElement

    @GET("cabinet/admin/users/{userId}/available-tariffs")
    suspend fun adminAvailableTariffs(@Path("userId") userId: Long): AdminAvailableTariffsDto

    @POST("cabinet/admin/users/{userId}/block")
    suspend fun adminBlockUser(
        @Path("userId") userId: Long,
        @Query("reason") reason: String? = null,
    ): JsonElement

    @POST("cabinet/admin/users/{userId}/unblock")
    suspend fun adminUnblockUser(@Path("userId") userId: Long): JsonElement

    /** Кого пригласил человек — для проверки заявки на вывод. */
    @GET("cabinet/admin/users/{userId}/referrals")
    suspend fun adminUserReferrals(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 200,
    ): AdminUsersListDto

    /**
     * Устройства одной подписки. Без [subscriptionId] бот отдаёт устройства
     * «основного» аккаунта человека — при нескольких подписках это не те.
     */
    @GET("cabinet/admin/users/{userId}/devices")
    suspend fun adminUserDevices(
        @Path("userId") userId: Long,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): AdminDevicesDto

    @DELETE("cabinet/admin/users/{userId}/devices/{hwid}")
    suspend fun adminDeleteDevice(
        @Path("userId") userId: Long,
        @Path("hwid") hwid: String,
        @Query("subscription_id") subscriptionId: Long? = null,
    ): JsonElement

    @GET("cabinet/admin/users/{userId}/transactions")
    suspend fun adminUserTransactions(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 20,
    ): AdminTransactionsDto

    @POST("cabinet/admin/users/{userId}/send-message")
    suspend fun adminSendMessage(
        @Path("userId") userId: Long,
        @Body body: AdminMessageRequest,
    ): JsonElement

    @POST("cabinet/admin/stats/nodes/{uuid}/restart")
    suspend fun adminRestartNode(@Path("uuid") uuid: String): JsonElement

    @POST("cabinet/admin/promocodes")
    suspend fun adminCreatePromo(@Body body: AdminPromoRequest): JsonElement

    // --- Заявки на вывод ---

    @GET("cabinet/admin/withdrawals")
    suspend fun adminWithdrawals(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
    ): AdminWithdrawalListDto

    @GET("cabinet/admin/withdrawals/{id}")
    suspend fun adminWithdrawal(@Path("id") id: Long): AdminWithdrawalDetailDto

    @POST("cabinet/admin/withdrawals/{id}/approve")
    suspend fun adminApproveWithdrawal(@Path("id") id: Long, @Body body: AdminCommentRequest): JsonElement

    @POST("cabinet/admin/withdrawals/{id}/reject")
    suspend fun adminRejectWithdrawal(@Path("id") id: Long, @Body body: AdminCommentRequest): JsonElement

    @POST("cabinet/admin/withdrawals/{id}/complete")
    suspend fun adminCompleteWithdrawal(@Path("id") id: Long): JsonElement

    // --- Платежи ---

    @GET("cabinet/admin/payments/search")
    suspend fun adminSearchPayments(
        @Query("search") search: String? = null,
        @Query("status_filter") status: String = "all",
        @Query("period") period: String = "7d",
        @Query("per_page") perPage: Int = 50,
    ): AdminPaymentListDto

    @POST("cabinet/admin/payments/{method}/{id}/check")
    suspend fun adminCheckPayment(@Path("method") method: String, @Path("id") id: Long): AdminPaymentCheckDto

    // --- Рассылки ---

    @GET("cabinet/admin/broadcasts/filters")
    suspend fun adminBroadcastFilters(): BroadcastFiltersDto

    @GET("cabinet/admin/broadcasts/buttons")
    suspend fun adminBroadcastButtons(): BroadcastButtonsDto

    @POST("cabinet/admin/broadcasts")
    suspend fun adminCreateBroadcast(@Body body: BroadcastCreateRequest): BroadcastDto

    @GET("cabinet/admin/broadcasts")
    suspend fun adminBroadcasts(@Query("limit") limit: Int = 20): BroadcastListDto

    @POST("cabinet/admin/broadcasts/{id}/stop")
    suspend fun adminStopBroadcast(@Path("id") id: Long): BroadcastDto

    // --- Документы и вопросы ---

    @GET("cabinet/info/faq")
    suspend fun faq(@Query("language") language: String = "ru"): List<FaqPageDto>

    @GET("cabinet/info/rules")
    suspend fun rules(@Query("language") language: String = "ru"): InfoDocumentDto

    @GET("cabinet/info/privacy-policy")
    suspend fun privacyPolicy(@Query("language") language: String = "ru"): InfoDocumentDto

    @GET("cabinet/info/public-offer")
    suspend fun publicOffer(@Query("language") language: String = "ru"): InfoDocumentDto

    @GET("cabinet/info/recurrent-payments")
    suspend fun recurrentTerms(@Query("language") language: String = "ru"): InfoDocumentDto

    @GET("cabinet/info/visibility")
    suspend fun infoVisibility(): InfoVisibilityDto

    // ---------- Новости ----------

    /** Пауза суточного тарифа: одна ручка и ставит, и снимает. */
    @POST("cabinet/subscription/pause")
    suspend fun toggleDailyPause(@Query("subscription_id") subscriptionId: Long?): RawJson

    // --- Напоминания на главной ---

    @GET("cabinet/reminders/active")
    suspend fun activeReminders(@Query("lang") lang: String = "ru"): List<ReminderCardDto>

    @POST("cabinet/reminders/{id}/dismiss")
    suspend fun dismissReminder(@Path("id") id: Long): retrofit2.Response<Unit>

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

    /** То же самое для админа: новые обращения и ответы людей. */
    @GET("cabinet/admin/tickets/notifications/unread-count")
    suspend fun adminTicketUnreadCount(): SupportUnreadCountDto

    @GET("cabinet/admin/tickets/notifications")
    suspend fun adminTicketNotifications(
        @Query("unread_only") unreadOnly: Boolean = true,
        @Query("limit") limit: Int = 20,
    ): AdminTicketNotificationsDto

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
