package com.darkprince.vpn.core.model

import kotlinx.serialization.Serializable

enum class Protocol { VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA2, TUIC, WIREGUARD, OTHER }

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
    /**
     * Server Description — подпись узла из панели Remnawave.
     *
     * Задаётся каждому хосту отдельно и приходит только в формате
     * XRAY_JSON: в ссылке vless:// такого поля нет вовсе.
     */
    val serverDescription: String? = null,
    /**
     * Имя протокола ровно так, как его прислала панель.
     *
     * Нужно, когда протокол нам незнаком: показать «hysteria2» честнее, чем
     * молча выдать узел за vless, как было раньше.
     */
    val rawProtocol: String? = null,
) {
    /**
     * Устойчивое имя узла: по нему запоминается выбор пользователя.
     *
     * Раньше выбранный сервер хранился номером в списке, и это ломалось от
     * любой перестановки узлов в панели: номер оставался прежним, а указывал
     * уже на другую страну. Человек видел «подключено к Германии», хотя
     * выбирал Польшу. Имя вместе с адресом переживает перестановку — а если
     * узел переименуют или уберут, выбор просто сбросится на первый, что
     * честнее молчаливой подмены.
     */
    val key: String get() = "$name|$address:$port"

    /**
     * Как узел выглядит в списке серверов: протокол, шифрование и транспорт.
     * Адрес и домен намеренно не показываем — пользователю они ничего не
     * говорят, а на чужом экране или скриншоте выдают инфраструктуру.
     */
    val transportLabel: String get() = transportParts.joinToString(" · ")

    /**
     * То же самое, но по частям — под отдельные метки в списке.
     *
     * Одной строкой «vless · reality · grpc» приходилось читать целиком, чтобы
     * найти нужное; тремя метками разного цвета протокол, шифрование и
     * транспорт различаются, не читая.
     *
     * У протоколов вроде Hysteria2 своего транспорта в конфиге нет — там сам
     * протокол и есть транспорт, поэтому «tcp» рядом с ним не приписываем: это
     * была бы неправда.
     */
    val transportParts: List<String>
        get() = listOfNotNull(
            protocolLabel,
            security.takeIf { it.isNotBlank() && it != "none" },
            networkLabel.takeIf { carriesOwnTransport.not() },
        )

    /** Протоколы, которые сами являются транспортом: сети поверх них нет. */
    private val carriesOwnTransport: Boolean
        get() = protocol == Protocol.HYSTERIA2 ||
            protocol == Protocol.TUIC ||
            protocol == Protocol.WIREGUARD

    private val protocolLabel: String
        get() = when (protocol) {
            // OTHER означает «панель прислала протокол, которого мы не знаем»:
            // показываем его имя как есть, а не выдуманное «vless»
            Protocol.OTHER -> rawProtocol?.lowercase() ?: "неизвестный"
            Protocol.HYSTERIA2 -> "hysteria2"
            Protocol.SHADOWSOCKS -> "ss"
            else -> protocol.name.lowercase()
        }

    private val networkLabel: String
        get() = when (network.lowercase()) {
            "ws" -> "websocket"
            "grpc" -> "gRPC"
            "xhttp" -> "xhttp"
            "httpupgrade" -> "httpupgrade"
            "h2", "http" -> "http/2"
            "quic" -> "quic"
            "kcp" -> "mkcp"
            "" -> "tcp"
            else -> network.lowercase()
        }
}
