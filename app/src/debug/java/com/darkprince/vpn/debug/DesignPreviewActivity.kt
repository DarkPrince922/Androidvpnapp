package com.darkprince.vpn.debug

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.darkprince.vpn.core.model.Protocol
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.ui.screens.HomeContent
import com.darkprince.vpn.ui.screens.ServersContent
import com.darkprince.vpn.ui.theme.AnimatedBackground
import com.darkprince.vpn.ui.theme.AppTheme
import com.darkprince.vpn.ui.theme.AquaIcon
import com.darkprince.vpn.ui.theme.AquaNavBar
import com.darkprince.vpn.ui.theme.AquaNavItem
import com.darkprince.vpn.ui.theme.NightPalette
import com.darkprince.vpn.ui.vm.HomeUiState
import com.darkprince.vpn.vpn.TrafficStats
import com.darkprince.vpn.vpn.VpnState
import com.darkprince.vpn.vpn.VpnStateStore

/**
 * Отладочный просмотр дизайна: те же экраны, что в приложении, но с
 * поддельными данными. Запуск: `am start -n .../.debug.DesignPreviewActivity
 * --es screen home_off|home_on|servers`.
 */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home_off"
        val on = screen == "home_on" || screen == "home_light"
        VpnStateStore.setState(if (on) VpnState.CONNECTED else VpnState.DISCONNECTED)

        fun node(name: String, protocol: Protocol, security: String, network: String) =
            ProxyProfile(
                protocol = protocol, name = name, address = "x", port = 443, userId = "u",
                security = security, network = network,
            )

        val servers = listOf(
            node("🇩🇪 Германия · Франкфурт", Protocol.VLESS, "reality", "xhttp"),
            node("🇫🇮 Финляндия · Хельсинки", Protocol.VLESS, "tls", "xhttp"),
            node("🇳🇱 Нидерланды · Akenai", Protocol.VLESS, "reality", "tcp"),
            node("🇵🇱 Польша · LTE", Protocol.VLESS, "reality", "tcp"),
            node("🇸🇪 Швеция · Cloudflare", Protocol.VLESS, "tls", "ws"),
            node("🇹🇼 Тайвань · Hysteria2", Protocol.HYSTERIA2, "tls", "tcp"),
        )
        val pings = mapOf(
            servers[0].key to 42L, servers[1].key to 31L, servers[2].key to 76L,
            servers[3].key to 36L, servers[4].key to 185L, servers[5].key to -1L,
        )
        val state = HomeUiState(servers = servers, selectedServer = 0, pings = pings)
        val items = listOf(
            AquaNavItem("home", "Главная", AquaIcon.Home),
            AquaNavItem("servers", "Серверы", AquaIcon.Servers),
            AquaNavItem("plans", "Тарифы", AquaIcon.Cart),
            AquaNavItem("balance", "Баланс", AquaIcon.Wallet),
            AquaNavItem("settings", "Ещё", AquaIcon.Gear),
        )

        setContent {
            AppTheme(if (screen == "home_light") com.darkprince.vpn.ui.theme.IndigoPalette else NightPalette) {
                AnimatedBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            AquaNavBar(items, when (screen) { "servers" -> 1; "settings" -> 4; else -> 0 }, on, {})
                        },
                    ) { inner ->
                        Box(Modifier.fillMaxSize().padding(inner)) {
                            if (screen == "settings") {
                                com.darkprince.vpn.ui.screens.SettingsScreen(
                                    user = null, guestMode = false, usingSharedSubscription = false,
                                    onOpenReferral = {}, onOpenGifts = {}, onOpenPromo = {}, onOpenDocs = {},
                                    onOpenApps = {}, autoConnectMode = "off", onSelectAutoConnect = {},
                                    failover = true, onToggleFailover = {}, killSwitch = false,
                                    onToggleKillSwitch = {}, onOpenVpnSettings = {}, onOpenShare = {},
                                    onOpenDevices = {}, onOpenSupport = {}, onOpenDiagnostics = {},
                                    onOpenNews = {}, newsUnreadCount = 2, supportUnreadCount = 1,
                                    onCreateAccount = {}, onLinkEmail = {}, onDropSharedSubscription = {},
                                    onLogout = {}, selectedThemeId = "night", onSelectTheme = {},
                                )
                            } else if (screen == "servers") {
                                ServersContent(state, null, {}, {}, {})
                            } else {
                                HomeContent(
                                    state = state,
                                    vpnState = if (on) VpnState.CONNECTED else VpnState.DISCONNECTED,
                                    stats = TrafficStats(
                                        uplinkBytes = 90_500_000L,
                                        downlinkBytes = 1_331_000_000L,
                                    ),
                                    vpnError = null,
                                    notice = null,
                                    switchedFrom = null,
                                    connectedSince = SystemClock.elapsedRealtime() - 2_537_000L,
                                    onPowerClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
