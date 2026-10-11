package com.darkprince.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.lifecycleScope
import com.darkprince.vpn.core.qr.QrUtils
import com.darkprince.vpn.data.api.dto.UserDto
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.screens.AppsScreen
import com.darkprince.vpn.ui.screens.BalanceScreen
import com.darkprince.vpn.ui.screens.DevicesScreen
import com.darkprince.vpn.ui.screens.HomeScreen
import com.darkprince.vpn.ui.screens.LoginScreen
import com.darkprince.vpn.ui.screens.PlansScreen
import com.darkprince.vpn.data.prefs.AppPrefs
import com.darkprince.vpn.ui.screens.AdminPersonScreen
import com.darkprince.vpn.ui.screens.DiagnosticsScreen
import com.darkprince.vpn.ui.screens.AdminScreen
import com.darkprince.vpn.ui.screens.AdminTicketScreen
import com.darkprince.vpn.ui.screens.NewsArticleScreen
import com.darkprince.vpn.ui.screens.NewsScreen
import com.darkprince.vpn.ui.screens.ReferralScreen
import com.darkprince.vpn.ui.screens.ServersScreen
import com.darkprince.vpn.ui.screens.ShareSubscriptionScreen
import com.darkprince.vpn.ui.screens.SettingsScreen
import com.darkprince.vpn.ui.screens.SetupScreen
import com.darkprince.vpn.ui.screens.SupportScreen
import com.darkprince.vpn.ui.screens.SupportTicketScreen
import com.darkprince.vpn.ui.theme.AnimatedBackground
import androidx.compose.ui.unit.dp
import com.darkprince.vpn.ui.theme.AppTheme
import com.darkprince.vpn.ui.theme.LocalPalette
import com.darkprince.vpn.ui.theme.paletteById
import com.darkprince.vpn.ui.theme.NavPillItem
import com.darkprince.vpn.ui.vm.AppsViewModel
import com.darkprince.vpn.ui.vm.AuthViewModel
import com.darkprince.vpn.ui.vm.BalanceViewModel
import com.darkprince.vpn.ui.vm.DevicesViewModel
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.ui.vm.PlansViewModel
import com.darkprince.vpn.work.AutoConnect
import com.darkprince.vpn.ui.vm.AdminViewModel
import com.darkprince.vpn.ui.vm.DiagnosticsViewModel
import com.darkprince.vpn.ui.vm.NewsViewModel
import com.darkprince.vpn.ui.vm.SupportViewModel
import com.darkprince.vpn.vpn.XVpnService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FragmentActivity, а не ComponentActivity: подтверждение личности перед
 * админской вкладкой умеет работать только с ней. Для Compose разницы нет —
 * FragmentActivity и есть ComponentActivity, только с менеджером фрагментов.
 */
class MainActivity : FragmentActivity() {

    private var pendingConnect: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingConnect?.invoke()
        }
        pendingConnect = null
    }

    /** Запускает VPN, при необходимости запросив системное разрешение. */
    private fun connectVpn(start: () -> Unit) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingConnect = start
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            start()
        }
    }

    fun openExternal(url: String) {
        try {
            if (url.startsWith("http")) {
                CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Открыть Telegram по deep-link (нативно или через t.me). */
    fun openTelegram(tgUri: String, webUri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tgUri)))
        } catch (_: ActivityNotFoundException) {
            openExternal(webUri)
        }
    }

    /**
     * Системный экран постоянного VPN.
     *
     * Запрет сети без туннеля включается только там и только руками — за
     * человека это не делает ни одно приложение, и правильно: иначе любое
     * могло бы оставить телефон без интернета молча. Мы доводим до нужного
     * экрана, дальше он решает сам.
     */
    fun openVpnSystemSettings() {
        val intents = listOf(
            Intent("android.net.vpn.SETTINGS"),
            Intent(android.provider.Settings.ACTION_VPN_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
    }

    /** Системное меню «Поделиться». */
    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, null))
    }

    /** Отправка QR-кода картинкой (в мессенджер, почту, галерею). */
    fun shareQrImage(bitmap: Bitmap, caption: String) {
        val uri = QrUtils.saveForSharing(this, bitmap) ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Доступ к VPN ($caption): отсканируйте QR в приложении")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, null))
    }

    /** Распознавание QR с картинки из галереи. */
    private var onImagePicked: ((String?) -> Unit)? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val callback = onImagePicked
        onImagePicked = null
        if (uri == null) {
            callback?.invoke(null)
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                QrUtils.decodeFromImage(this@MainActivity, uri)
            }
            callback?.invoke(decoded)
        }
    }

    fun pickQrImage(onResult: (String?) -> Unit) {
        onImagePicked = onResult
        pickImageLauncher.launch("image/*")
    }

    /** Файл, который пользователь прикрепляет к обращению в поддержку. */
    private var onSupportAttachmentPicked: ((Uri?) -> Unit)? = null

    private val supportAttachmentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val callback = onSupportAttachmentPicked
        onSupportAttachmentPicked = null
        callback?.invoke(uri)
    }

    fun pickSupportAttachment(onResult: (Uri?) -> Unit) {
        onSupportAttachmentPicked = onResult
        supportAttachmentLauncher.launch("*/*")
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    /** Сканирование QR-кода подписки; результат уходит в колбэк. */
    private var onScanResult: ((String) -> Unit)? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { onScanResult?.invoke(it) }
        onScanResult = null
    }

    fun scanSubscriptionQr(onResult: (String) -> Unit) {
        onScanResult = onResult
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код подписки")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        // Фон окна прибит в themes.xml тёмным — его видно, пока Compose не
        // отрисовал первый кадр. При светлой теме это заметная вспышка,
        // поэтому перекрашиваем окно до setContent, по сохранённому значению.
        window.setBackgroundDrawable(
            ColorDrawable(paletteById(ServiceLocator.prefs.cachedTheme).background.toArgb())
        )

        setContent {
            val themeId by ServiceLocator.prefs.themeFlow.collectAsState(
                initial = ServiceLocator.prefs.cachedTheme,
            )
            val palette = paletteById(themeId)

            // Значки системных панелей темой Compose не управляются: на светлой
            // теме белые часы и кнопки просто исчезают. Нижнюю панель заодно
            // перекрашиваем — иначе под приложением остаётся тёмная полоса,
            // прибитая в themes.xml на время запуска.
            val view = LocalView.current
            LaunchedEffect(palette) {
                window.navigationBarColor = palette.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = palette.isLight
                    isAppearanceLightNavigationBars = palette.isLight
                }
            }

            AppTheme(palette) {
                AnimatedBackground {
                    AppRoot(
                        activity = this@MainActivity,
                        onConnect = ::connectVpn,
                    )
                }
            }
        }
    }

    // События кабинета нужны, пока приложение на экране: в фоне их всё
    // равно некому показать, а держать сокет ради этого — тратить батарею.
    override fun onStart() {
        super.onStart()
        com.darkprince.vpn.data.api.CabinetEvents.start()
    }

    override fun onStop() {
        com.darkprince.vpn.data.api.CabinetEvents.stop()
        super.onStop()
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun AppRoot(
    activity: MainActivity,
    onConnect: ((() -> Unit)) -> Unit,
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val supportViewModel: SupportViewModel = viewModel(viewModelStoreOwner = activity)
    val newsViewModel: NewsViewModel = viewModel(viewModelStoreOwner = activity)
    val adminViewModel: AdminViewModel = viewModel(viewModelStoreOwner = activity)
    val adminToolsViewModel: com.darkprince.vpn.ui.vm.AdminToolsViewModel =
        viewModel(viewModelStoreOwner = activity)
    val supportState by supportViewModel.state.collectAsStateWithLifecycle()
    val newsState by newsViewModel.state.collectAsStateWithLifecycle()
    val adminState by adminViewModel.state.collectAsStateWithLifecycle()
    val prefs = ServiceLocator.prefs

    // При входе или выходе очищаем переписку предыдущей сессии.
    LaunchedEffect(authState.loggedIn, authState.guestMode) {
        supportViewModel.sessionChanged()
        newsViewModel.sessionChanged()
        adminViewModel.sessionChanged()
        com.darkprince.vpn.data.api.CabinetEvents.sessionChanged()
    }

    // Самое важное из событий кабинета — коротким сообщением, на каком бы
    // экране человек ни был. Экраны сами обновляются по тем же событиям.
    LaunchedEffect(Unit) {
        com.darkprince.vpn.data.api.CabinetEvents.events.collect { event ->
            when (event.type) {
                "ticket.admin_reply" -> supportViewModel.refreshUnread()
                "ticket.new", "ticket.user_reply" -> if (adminState.isAdmin) adminViewModel.refresh()
            }
            cabinetEventText(event)?.let {
                android.widget.Toast.makeText(activity, it, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // открыть Telegram, когда начата deep-link авторизация
    LaunchedEffect(authState.telegramUri) {
        val tg = authState.telegramUri
        val web = authState.telegramWebUri
        if (tg != null && web != null) {
            activity.openTelegram(tg, web)
            authViewModel.consumeTelegramUri()
        }
    }

    val startDestination = androidx.compose.runtime.remember {
        when {
            prefs.cachedGuestSubUrl != null -> "home"
            prefs.cachedBaseUrl.isBlank() -> "setup"
            !ServiceLocator.authRepository.isLoggedIn -> "login"
            else -> "home"
        }
    }

    // в гостевом режиме кабинет недоступен: покупок и баланса нет
    val bottomItems = if (authState.guestMode) {
        listOf(
            BottomItem("home", "Главная", Icons.Default.Home),
            BottomItem("servers", "Серверы", Icons.Default.Dns),
            BottomItem("settings", "Ещё", Icons.Default.Settings),
        )
    } else {
        listOf(
            BottomItem("home", "Главная", Icons.Default.Home),
            BottomItem("servers", "Серверы", Icons.Default.Dns),
            BottomItem("plans", "Тарифы", Icons.Default.ShoppingCart),
            BottomItem("balance", "Баланс", Icons.Default.AccountBalanceWallet),
            BottomItem("settings", "Ещё", Icons.Default.Settings),
        )
    }.let { items ->
        // Пятый пункт появляется только у админа: у всех остальных панель
        // остаётся прежней.
        if (adminState.isAdmin) {
            items + BottomItem("admin", "Панель", Icons.Default.AdminPanelSettings)
        } else {
            items
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // гостю панель нужна не меньше: без неё нет ни «Ещё», ни выбора приложений
    val showBottomBar = (authState.loggedIn || authState.guestMode) &&
        currentRoute in bottomItems.map { it.route }

    Scaffold(
        // фон рисует AnimatedBackground, поэтому сам Scaffold прозрачный
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                // плавающая панель: скруглённый блок с «таблеткой» под активным
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        // системные кнопки навигации рисуются поверх приложения,
                        // поэтому панель приподнимаем над ними
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(LocalPalette.current.panel.copy(alpha = 0.92f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            RoundedCornerShape(26.dp),
                        )
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    bottomItems.forEach { item ->
                        NavPillItem(
                            selected = currentRoute == item.route,
                            icon = item.icon,
                            label = item.label,
                            onClick = {
                                if (item.route == "home") {
                                    // «Главная» всегда возвращает на корневой экран
                                    if (!navController.popBackStack("home", inclusive = false)) {
                                        navController.navigate("home") { launchSingleTop = true }
                                    }
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            // мягкие переходы вместо резкой смены экранов
            enterTransition = {
                fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 12 }
            },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = {
                fadeIn(tween(280)) + slideInHorizontally(tween(320)) { -it / 12 }
            },
            popExitTransition = { fadeOut(tween(200)) },
        ) {
            composable("setup") {
                SetupScreen(initialUrl = authState.baseUrl) { url ->
                    authViewModel.setBaseUrl(url) {
                        navController.navigate("login") { popUpTo("setup") { inclusive = true } }
                    }
                }
            }
            composable("login") {
                LaunchedEffect(authState.loggedIn, authState.guestMode) {
                    if (authState.loggedIn || authState.guestMode) {
                        navController.navigate("home") { popUpTo("login") { inclusive = true } }
                    }
                }
                LoginScreen(
                    state = authState,
                    onTelegramLogin = { authViewModel.startTelegramAuth() },
                    onCancelTelegram = { authViewModel.cancelTelegramAuth() },
                    onEmailLogin = { email, password -> authViewModel.emailLogin(email, password) },
                    onEmailRegister = { email, password, referral ->
                        authViewModel.emailRegister(email, password, referral)
                    },
                    onForgotPassword = { email -> authViewModel.forgotPassword(email) },
                    onChangeServer = {
                        navController.navigate("setup") { popUpTo("login") { inclusive = true } }
                    },
                    onSubscriptionLink = { link -> authViewModel.loginWithSubscriptionLink(link) },
                    onScanSubscription = {
                        activity.scanSubscriptionQr { link ->
                            authViewModel.loginWithSubscriptionLink(link)
                        }
                    },
                    onPickQrImage = {
                        activity.pickQrImage { decoded ->
                            if (decoded != null) {
                                authViewModel.loginWithSubscriptionLink(decoded)
                            } else {
                                authViewModel.showQrImageError()
                            }
                        }
                    },
                )
            }
            // гость заводит свой аккаунт, не теряя чужую подписку: экран тот же,
            // но выход отсюда — назад в приложение, а не на стартовый вход
            composable("upgrade") {
                val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity)
                LaunchedEffect(authState.loggedIn) {
                    if (authState.loggedIn) {
                        homeViewModel.refresh(forceServers = true)
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                    }
                }
                LoginScreen(
                    state = authState,
                    upgradeMode = true,
                    onBack = { navController.popBackStack() },
                    onTelegramLogin = { authViewModel.startTelegramAuth() },
                    onCancelTelegram = { authViewModel.cancelTelegramAuth() },
                    onEmailLogin = { email, password -> authViewModel.emailLogin(email, password) },
                    onEmailRegister = { email, password, referral ->
                        authViewModel.emailRegister(email, password, referral)
                    },
                    onForgotPassword = { email -> authViewModel.forgotPassword(email) },
                    onChangeServer = {},
                    onScanSubscription = {},
                    onPickQrImage = {},
                    onSubscriptionLink = {},
                )
            }
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity)
                HomeScreen(
                    viewModel = homeViewModel,
                    onConnectClick = {
                        val profile = homeViewModel.selectedProfile()
                        if (profile != null) {
                            onConnect { XVpnService.start(activity, profile) }
                        } else {
                            homeViewModel.refresh(forceServers = true)
                        }
                    },
                    onDisconnectClick = { XVpnService.stop(activity) },
                )
            }
            composable("servers") {
                val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity)
                ServersScreen(viewModel = homeViewModel)
            }
            composable("plans") {
                val plansViewModel: PlansViewModel = viewModel()
                val plansState by plansViewModel.state.collectAsStateWithLifecycle()
                val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity)
                // после покупки подтягиваем свою подписку: у бывшего гостя
                // приложение сразу переходит с чужой ссылки на собственную
                LaunchedEffect(plansState.info) {
                    if (plansState.info != null) homeViewModel.refresh(forceServers = true)
                }
                PlansScreen(
                    viewModel = plansViewModel,
                    // ведём в устройства именно той подписки, из карточки
                    // которой нажали, — выбирать её заново незачем
                    onOpenDevices = { subId -> navController.navigate("devices?sub=$subId") },
                    // как переход по нижней панели: «Баланс» — свой раздел,
                    // а не экран поверх тарифов
                    onNeedTopUp = {
                        navController.navigate("balance") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenUrl = activity::openExternal,
                    onOpenPromo = { navController.navigate("promo") },
                )
                // Ссылку сбросили: старые ключи мертвы. Перекачиваем серверы и,
                // если подключение шло по этой подписке, переподключаемся на
                // новые — иначе VPN остался бы «включён» без связи.
                LaunchedEffect(plansState.revoked) {
                    val revoked = plansState.revoked ?: return@LaunchedEffect
                    plansViewModel.consumeRevoked()
                    val wasOn = com.darkprince.vpn.vpn.VpnStateStore.state.value.let {
                        it == com.darkprince.vpn.vpn.VpnState.CONNECTED ||
                            it == com.darkprince.vpn.vpn.VpnState.CONNECTING
                    }
                    homeViewModel.refresh(forceServers = true)
                    homeViewModel.state.first { !it.loading }
                    if (revoked.working && wasOn) {
                        homeViewModel.selectedProfile()?.let { profile ->
                            onConnect { XVpnService.start(activity, profile) }
                        }
                    }
                }
            }
            composable("balance") {
                val balanceViewModel: BalanceViewModel = viewModel()
                val balanceState by balanceViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(balanceState.openUrl) {
                    balanceState.openUrl?.let {
                        activity.openExternal(it)
                        balanceViewModel.consumeOpenUrl()
                    }
                }
                BalanceScreen(viewModel = balanceViewModel)
            }
            composable("referral") {
                val referralViewModel: com.darkprince.vpn.ui.vm.ReferralViewModel = viewModel()
                ReferralScreen(
                    viewModel = referralViewModel,
                    onShare = { text -> activity.shareText(text) },
                )
            }
            composable("gifts") {
                val giftsViewModel: com.darkprince.vpn.ui.vm.GiftsViewModel = viewModel()
                val giftsState by giftsViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(giftsState.openUrl) {
                    giftsState.openUrl?.let {
                        giftsViewModel.consumeOpenUrl()
                        activity.openExternal(it)
                    }
                }
                com.darkprince.vpn.ui.screens.GiftsScreen(
                    viewModel = giftsViewModel,
                    onShare = { text -> activity.shareText(text) },
                )
            }
            composable("docs") {
                val docsViewModel: com.darkprince.vpn.ui.vm.DocsViewModel = viewModel()
                com.darkprince.vpn.ui.screens.DocsScreen(viewModel = docsViewModel)
            }
            composable("promo") {
                val promoViewModel: com.darkprince.vpn.ui.vm.PromoViewModel = viewModel()
                com.darkprince.vpn.ui.screens.PromoScreen(viewModel = promoViewModel)
            }
            composable("apps") {
                val appsViewModel: AppsViewModel = viewModel()
                AppsScreen(viewModel = appsViewModel)
            }
            composable(
                route = "devices?sub={sub}",
                arguments = listOf(
                    navArgument("sub") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val devicesViewModel: DevicesViewModel = viewModel()
                val subId = entry.arguments?.getLong("sub") ?: -1L
                LaunchedEffect(subId) {
                    if (subId > 0) devicesViewModel.select(subId)
                }
                DevicesScreen(viewModel = devicesViewModel)
            }
            composable("share") {
                ShareSubscriptionScreen(
                    onShareLink = { link -> activity.shareText("Доступ к VPN: $link") },
                    onShareImage = { bitmap, label -> activity.shareQrImage(bitmap, label) },
                )
            }
            composable("support") {
                SupportScreen(
                    viewModel = supportViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTicket = { ticketId -> navController.navigate("support/$ticketId") },
                    onOpenContact = activity::openExternal,
                    onPickAttachment = {
                        activity.pickSupportAttachment { uri ->
                            uri?.let(supportViewModel::chooseAttachment)
                        }
                    },
                )
            }
            composable("support/{ticketId}") { entry ->
                val ticketId = entry.arguments?.getString("ticketId")?.toLongOrNull()
                if (ticketId != null) {
                    SupportTicketScreen(
                        viewModel = supportViewModel,
                        ticketId = ticketId,
                        onBack = { navController.popBackStack() },
                        onOpenAttachment = activity::openExternal,
                        onPickAttachment = {
                            activity.pickSupportAttachment { uri ->
                                uri?.let(supportViewModel::chooseAttachment)
                            }
                        },
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable("admin") {
                AdminGate(
                    activity = activity,
                    unlocked = adminState.unlocked,
                    onUnlocked = adminViewModel::onUnlocked,
                    onCancel = { navController.popBackStack() },
                ) {
                    AdminScreen(
                        viewModel = adminViewModel,
                        tools = adminToolsViewModel,
                        onOpenTicket = { id -> navController.navigate("admin/ticket/$id") },
                        onOpenPerson = { person ->
                            // Человека кладём в состояние, а не в маршрут: в
                            // карточке нужен весь объект из списка, а тащить
                            // его через строку адреса значит собирать заново.
                            adminViewModel.openPerson(person)
                            navController.navigate("admin/person")
                        },
                    )
                }
            }
            composable("admin/person") {
                AdminPersonScreen(
                    viewModel = adminViewModel,
                    onBack = {
                        adminViewModel.closePerson()
                        navController.popBackStack()
                    },
                    onOpenPayments = { query ->
                        adminToolsViewModel.searchFor(query)
                        adminViewModel.setSection(com.darkprince.vpn.ui.vm.AdminSection.PAYMENTS)
                        adminViewModel.closePerson()
                        navController.popBackStack("admin", inclusive = false)
                    },
                )
            }
            composable("admin/ticket/{ticketId}") { entry ->
                entry.arguments?.getString("ticketId")?.toLongOrNull()?.let { id ->
                    AdminTicketScreen(
                        viewModel = adminViewModel,
                        ticketId = id,
                        onBack = { navController.popBackStack() },
                        onOpenPerson = { user ->
                            adminViewModel.openPersonById(user)
                            navController.navigate("admin/person")
                        },
                    )
                }
            }
            composable("email-link") {
                val emailLinkViewModel: com.darkprince.vpn.ui.vm.EmailLinkViewModel = viewModel()
                com.darkprince.vpn.ui.screens.EmailLinkScreen(
                    viewModel = emailLinkViewModel,
                    onDone = { navController.popBackStack() },
                )
            }
            composable("news") {
                NewsScreen(
                    viewModel = newsViewModel,
                    onOpen = { slug -> navController.navigate("news/$slug") },
                )
            }
            composable("news/{slug}") { entry ->
                entry.arguments?.getString("slug")?.let { slug ->
                    NewsArticleScreen(viewModel = newsViewModel, slug = slug)
                }
            }
            composable("diagnostics") {
                val diagnosticsViewModel: DiagnosticsViewModel = viewModel()
                DiagnosticsScreen(viewModel = diagnosticsViewModel)
            }
            composable("settings") {
                val autoConnectMode by prefs.autoConnectFlow
                    .collectAsState(initial = AppPrefs.AUTO_OFF)
                val killSwitch by prefs.killSwitchFlow.collectAsState(initial = false)
                val failover by prefs.failoverFlow.collectAsState(initial = true)
                // Первая проверка идёт при запуске и может не удаться на
                // ровном месте — сети ещё нет, токен просрочен. Переспрашиваем
                // здесь, чтобы одна неудача не прятала панель до перезапуска.
                LaunchedEffect(Unit) { adminViewModel.recheck() }
                val userJson by prefs.userJsonFlow.collectAsState(initial = null)
                val user = userJson?.let {
                    try {
                        ServiceLocator.apiClient.json.decodeFromString(UserDto.serializer(), it)
                    } catch (_: Exception) {
                        null
                    }
                }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity)
                SettingsScreen(
                    user = user,
                    guestMode = authState.guestMode,
                    usingSharedSubscription = authState.usingSharedSubscription,
                    // тему берём из composition, а не читаем настройку заново:
                    // так галочка в списке и экран под ней не разъезжаются
                    selectedThemeId = LocalPalette.current.id,
                    onSelectTheme = { scope.launch { prefs.setTheme(it.id) } },
                    onOpenReferral = { navController.navigate("referral") },
                    onOpenGifts = { navController.navigate("gifts") },
                    onOpenPromo = { navController.navigate("promo") },
                    onOpenDocs = { navController.navigate("docs") },
                    onOpenApps = { navController.navigate("apps") },
                    autoConnectMode = autoConnectMode,
                    onSelectAutoConnect = { mode ->
                        scope.launch {
                            prefs.setAutoConnect(mode)
                            // Расписание правим сразу: настройка без снятого
                            // ожидания оставила бы приложение подключаться
                            // после того, как его об этом просить перестали.
                            AutoConnect.sync(activity.applicationContext, mode)
                        }
                    },
                    failover = failover,
                    onToggleFailover = { scope.launch { prefs.setFailover(it) } },
                    killSwitch = killSwitch,
                    onToggleKillSwitch = { scope.launch { prefs.setKillSwitch(it) } },
                    onOpenVpnSettings = { activity.openVpnSystemSettings() },
                    onOpenShare = { navController.navigate("share") },
                    onOpenDevices = { navController.navigate("devices") },
                    onOpenSupport = { navController.navigate("support") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    supportUnreadCount = supportState.unreadCount,
                    onOpenNews = { navController.navigate("news") },
                    newsUnreadCount = newsState.unread,
                    onCreateAccount = { navController.navigate("upgrade") },
                    onLinkEmail = { navController.navigate("email-link") },
                    onDropSharedSubscription = {
                        scope.launch {
                            XVpnService.stop(activity)
                            XVpnService.forgetProfile(activity)
                            ServiceLocator.subscriptionRepository.exitGuestMode()
                            homeViewModel.refresh(forceServers = true)
                            navController.navigate("home") { popUpTo("home") { inclusive = true } }
                        }
                    },
                    onLogout = {
                        scope.launch {
                            XVpnService.stop(activity)
                            XVpnService.forgetProfile(activity)
                            // чужую подписку отпускаем в любом случае: она привязана
                            // к этому телефону, а не к аккаунту
                            ServiceLocator.subscriptionRepository.exitGuestMode()
                            if (!authState.guestMode) {
                                ServiceLocator.authRepository.logout()
                            }
                            authViewModel.onLoggedOut()
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Что сказать человеку о событии кабинета; null — молча обновить экраны. */
private fun cabinetEventText(event: com.darkprince.vpn.data.api.CabinetEvent): String? {
    fun money(key: String) = event.long(key)?.let { com.darkprince.vpn.ui.screens.formatKopeks(it) }
    return when (event.type) {
        "balance.topup" -> money("amount_kopeks")?.let { "Баланс пополнен на $it" } ?: "Баланс пополнен"
        "autopay.success" -> "Подписка продлена автоматически" +
            (money("amount_kopeks")?.let { ", списано $it" } ?: "")
        "autopay.insufficient_funds" -> "Автопродление не прошло: на балансе не хватает" +
            (event.long("required_kopeks")?.let { req ->
                val have = event.long("balance_kopeks") ?: 0
                " ${com.darkprince.vpn.ui.screens.formatKopeks((req - have).coerceAtLeast(0))}"
            } ?: " денег")
        "autopay.failed" -> "Автопродление не прошло" + (event.text("reason")?.let { ": $it" } ?: "")
        "subscription.expired" -> "Подписка закончилась"
        "ticket.admin_reply" -> "Поддержка ответила на ваше обращение"
        "ticket.new" -> null
        "referral.bonus" -> money("bonus_kopeks")?.let { "Бонус за приглашённого: $it" }
            ?: "Начислен бонус за приглашённого"
        else -> null
    }
}
