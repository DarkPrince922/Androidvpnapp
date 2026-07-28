package com.darkprince.vpn.core.model

import kotlinx.serialization.Serializable

enum class Protocol { VLESS, VMESS, TROJAN, SHADOWSOCKS }

/**
 * Один сервер из подписки Remnawave (разобранная ссылка vless:// и т.п.).
 */
@Serializable
data class ProxyProfile(
    val protocol: Protocol,
    val name: String,
    val address: String,
    val port: Int,
    // vless/vmess: uuid; trojan/ss: password
    val userId: String,
    val flow: String? = null,          // vless: xtls-rprx-vision
    val encryption: String? = null,    // vless: none; ss: метод шифрования
    val network: String = "tcp",       // tcp / ws / grpc / httpupgrade / xhttp
    val security: String = "none",     // none / tls / reality
    val sni: String? = null,
    val alpn: String? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false,
    val publicKey: String? = null,     // reality pbk
    val shortId: String? = null,       // reality sid
    val spiderX: String? = null,       // reality spx
    val host: String? = null,          // ws/httpupgrade/xhttp host
    val path: String? = null,          // ws/httpupgrade/xhttp path
    val serviceName: String? = null,   // grpc
    val grpcMultiMode: Boolean = false,
    val headerType: String? = null,    // tcp http-обфускация
    val vmessSecurity: String = "auto",
    /**
     * Полный конфиг Xray из подписки (формат Xray JSON / Happ). Если задан,
     * используется как есть — с роутингом, правилами и балансировщиками
     * панели; приложение только подменяет inbounds на свой SOCKS.
     */
    val rawConfig: String? = null,
)
