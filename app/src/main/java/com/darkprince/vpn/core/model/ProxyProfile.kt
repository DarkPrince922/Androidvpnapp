package com.darkprince.vpn.core.model

import kotlinx.serialization.Serializable

enum class Protocol { VLESS, VMESS, TROJAN, SHADOWSOCKS }

/** Один сервер из подписки Remnawave. */
@Serializable
data class ProxyProfile(
    val protocol: Protocol,
    val name: String,
    val address: String,
    val port: Int,
    val userId: String,
    val flow: String? = null,
    val encryption: String? = null,
    val network: String = "tcp",
    val security: String = "none",
    val sni: String? = null,
    val alpn: String? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val host: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
    val grpcMultiMode: Boolean = false,
    val headerType: String? = null,
    val vmessSecurity: String = "auto",
    /** Server Description из Remnawave XRAY_JSON. */
    val serverDescription: String? = null,
    /** Полный Xray JSON, если сервер пришёл в формате XRAY_JSON. */
    val rawConfig: String? = null,
) {
    val key: String get() = "$name|$address:$port"

    val protocolLabel: String get() = protocol.name

    val securityLabel: String?
        get() = security.takeIf { it.isNotBlank() && it.lowercase() != "none" }?.uppercase()

    val networkLabel: String
        get() = when (network.lowercase()) {
            "ws" -> "WS"
            "grpc" -> "gRPC"
            "xhttp" -> "xHTTP"
            "httpupgrade" -> "HTTPUPGRADE"
            "h2", "http" -> "HTTP/2"
            "quic" -> "QUIC"
            "kcp" -> "mKCP"
            "" -> "TCP"
            else -> network.uppercase()
        }

    val transportLabel: String
        get() = listOfNotNull(protocolLabel, securityLabel, networkLabel).joinToString(" · ")
}
