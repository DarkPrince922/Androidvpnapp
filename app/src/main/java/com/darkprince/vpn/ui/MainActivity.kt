package com.darkprince.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.darkprince.vpn.core.qr.QrUtils
import com.darkprince.vpn.data.api.dto.UserDto
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.screens.AppsScreen
import com.darkprince.vpn.ui.screens.BalanceScreen
import com.darkprince.vpn.ui.screens.HomeScreen
import com.darkprince.vpn.ui.screens.LoginScreen
import com.darkprince.vpn.ui.screens.PlansScreen
import com.darkprince.vpn.ui.screens.ReferralScreen
import com.darkprince.vpn.ui.screens.ServersScreen
import com.darkprince.vpn.ui.screens.ShareSubscriptionScreen
import com.darkprince.vpn.ui.screens.SettingsScreen
import com.darkprince.vpn.ui.screens.SetupScreen
import com.darkprince.vpn.ui.theme.AnimatedBackground
import com.darkprince.vpn.ui.theme.AppTheme
import com.darkprince.vpn.ui.vm.AppsViewModel
import com.darkprince.vpn.ui.vm.AuthViewModel
import com.darkprince.vpn.ui.vm.BalanceViewModel
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.ui.vm.PlansViewModel
import com.darkprince.vpn.vpn.XVpnService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

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
        setContent {
            AppTheme {
                AnimatedBackground {
                    AppRoot(
                        activity = this@MainActivity,
                        onConnect = ::connectVpn,
                    )
                }
            }
        }
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
    val prefs = ServiceLocator.prefs

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
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = authState.loggedIn && currentRoute in bottomItems.map { it.route }

    Scaffold(
        // фон рисует AnimatedBackground, поэтому сам Scaffold прозрачный
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Transparent) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
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
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
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
                    onOpenServers = { navController.navigate("servers") },
                )
            }
            composable("servers") {
                val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity)
                ServersScreen(viewModel = homeViewModel)
            }
            composable("plans") {
                val plansViewModel: PlansViewModel = viewModel()
                PlansScreen(viewModel = plansViewModel)
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
                ReferralScreen(onShare = { text -> activity.shareText(text) })
            }
            composable("apps") {
                val appsViewModel: AppsViewModel = viewModel()
                AppsScreen(viewModel = appsViewModel)
            }
            composable("share") {
                ShareSubscriptionScreen(
                    onShareLink = { link -> activity.shareText("Доступ к VPN: $link") },
                    onShareImage = { bitmap, label -> activity.shareQrImage(bitmap, label) },
                )
            }
            composable("settings") {
                val userJson by prefs.userJsonFlow.collectAsState(initial = null)
                val baseUrl by prefs.baseUrlFlow.collectAsState(initial = "")
                val user = userJson?.let {
                    try {
                        ServiceLocator.apiClient.json.decodeFromString(UserDto.serializer(), it)
                    } catch (_: Exception) {
                        null
                    }
                }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                SettingsScreen(
                    user = user,
                    baseUrl = baseUrl,
                    guestMode = authState.guestMode,
                    onOpenReferral = { navController.navigate("referral") },
                    onOpenApps = { navController.navigate("apps") },
                    onOpenShare = { navController.navigate("share") },
                    onLogout = {
                        scope.launch {
                            XVpnService.stop(activity)
                            if (authState.guestMode) {
                                ServiceLocator.subscriptionRepository.exitGuestMode()
                            } else {
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
