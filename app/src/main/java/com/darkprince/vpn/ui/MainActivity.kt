package com.darkprince.vpn.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.darkprince.vpn.data.api.dto.UserDto
import com.darkprince.vpn.di.ServiceLocator
import com.darkprince.vpn.ui.screens.BalanceScreen
import com.darkprince.vpn.ui.screens.HomeScreen
import com.darkprince.vpn.ui.screens.LoginScreen
import com.darkprince.vpn.ui.screens.PlansScreen
import com.darkprince.vpn.ui.screens.ReferralScreen
import com.darkprince.vpn.ui.screens.ServersScreen
import com.darkprince.vpn.ui.screens.SettingsScreen
import com.darkprince.vpn.ui.screens.SetupScreen
import com.darkprince.vpn.ui.theme.AppTheme
import com.darkprince.vpn.ui.vm.AuthViewModel
import com.darkprince.vpn.ui.vm.BalanceViewModel
import com.darkprince.vpn.ui.vm.HomeViewModel
import com.darkprince.vpn.ui.vm.PlansViewModel
import com.darkprince.vpn.vpn.XVpnService
import kotlinx.coroutines.launch

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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
                AppRoot(
                    activity = this,
                    onConnect = ::connectVpn,
                )
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
            prefs.cachedBaseUrl.isBlank() -> "setup"
            !ServiceLocator.authRepository.isLoggedIn -> "login"
            else -> "home"
        }
    }

    val bottomItems = listOf(
        BottomItem("home", "Главная", Icons.Default.Home),
        BottomItem("servers", "Серверы", Icons.Default.Dns),
        BottomItem("plans", "Тарифы", Icons.Default.ShoppingCart),
        BottomItem("balance", "Баланс", Icons.Default.AccountBalanceWallet),
        BottomItem("settings", "Ещё", Icons.Default.Settings),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = authState.loggedIn && currentRoute in bottomItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
        ) {
            composable("setup") {
                SetupScreen(initialUrl = authState.baseUrl) { url ->
                    authViewModel.setBaseUrl(url) {
                        navController.navigate("login") { popUpTo("setup") { inclusive = true } }
                    }
                }
            }
            composable("login") {
                LaunchedEffect(authState.loggedIn) {
                    if (authState.loggedIn) {
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
                    onOpenReferral = { navController.navigate("referral") },
                    onLogout = {
                        scope.launch {
                            XVpnService.stop(activity)
                            ServiceLocator.authRepository.logout()
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
