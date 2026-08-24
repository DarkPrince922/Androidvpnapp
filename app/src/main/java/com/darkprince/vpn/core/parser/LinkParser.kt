package com.darkprince.vpn.core.parser

import android.util.Base64
import com.darkprince.vpn.core.model.Protocol
import com.darkprince.vpn.core.model.ProxyProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder

/**
 * Парсер подписки Remnawave: содержимое — base64-список ссылок либо
 * ссылки построчно (vless://, vmess://, trojan://, ss://).
 */
object LinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Выходы, которые есть в любом конфиге Xray и узлом не являются: прямой
     * выход в интернет, «чёрная дыра» для заблокированного и резолвер.
     */
    private val SERVICE_OUTBOUNDS = setOf("freedom", "blackhole", "dns", "loopback")

    fun parseSubscriptionContent(content: String): List<ProxyProfile> {
        val text = content.trim()
        // Формат Xray JSON (Happ): массив полных конфигов или один конфиг
        if (text.startsWith("[") || text.startsWith("{")) {
            val fromJson = parseXrayJsonSubscription(text)
            if (fromJson.isNotEmpty()) return fromJson
        }
        val decoded = if (text.startsWith("vless://") || text.startsWith("vmess://") ||
            text.startsWith("trojan://") || text.startsWith("ss://")
        ) {
            text
        } else {
            tryBase64(text) ?: text
        }
        val trimmedDecoded = decoded.trim()
        if (trimmedDecoded.startsWith("[") || trimmedDecoded.startsWith("{")) {
            val fromJson = parseXrayJsonSubscription(trimmedDecoded)
            if (fromJson.isNotEmpty()) return fromJson
        }
        return decoded.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLink(it) }
    }

    /**
     * Подписка в формате Xray JSON: каждый элемент — полный конфиг Xray
     * (remarks, outbounds, routing, balancers…). Конфиг сохраняется целиком
     * в rawConfig, имя берётся из remarks, адрес/протокол — из первого
     * прокси-аутбаунда (для отображения в списке).
     */
    private fun parseXrayJsonSubscription(text: String): List<ProxyProfile> = try {
        val root = json.parseToJsonElement(text)
        val configs = when (root) {
            is kotlinx.serialization.json.JsonArray -> root.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            is kotlinx.serialization.json.JsonObject ->
                if (root.containsKey("outbounds")) listOf(root) else emptyList()
            else -> emptyList()
        }
        configs.mapIndexedNotNull { index, config ->
            val outbounds = config["outbounds"] as? kotlinx.serialization.json.JsonArray
                ?: return@mapIndexedNotNull null
            var protocol = Protocol.VLESS
            var rawProtocol: String? = null
            var address = ""
            var port = 443
            // транспорт и шифрование показываем в списке серверов вместо адреса
            var network = "tcp"
            var security = "none"
            for (outbound in outbounds) {
                val obj = outbound as? kotlinx.serialization.json.JsonObject ?: continue
                val proto = obj["protocol"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue
                // служебные выходы есть в каждом конфиге и узлом не являются
                if (proto in SERVICE_OUTBOUNDS) continue
                protocol = when (proto) {
                    "vless" -> Protocol.VLESS
                    "vmess" -> Protocol.VMESS
                    "trojan" -> Protocol.TROJAN
                    "shadowsocks" -> Protocol.SHADOWSOCKS
                    // панель пишет и «hysteria», и «hysteria2» — это один протокол
                    "hysteria", "hysteria2", "hy2" -> Protocol.HYSTERIA2
                    "tuic" -> Protocol.TUIC
                    "wireguard" -> Protocol.WIREGUARD
                    // Незнакомый протокол раньше просто пропускался, и узел
                    // оставался с заготовкой «vless · tcp» — в списке он врал
                    // про себя. Теперь показываем то, что прислала панель.
                    else -> Protocol.OTHER
                }
                rawProtocol = proto
                val settings = obj["settings"] as? kotlinx.serialization.json.JsonObject
                // vnext — у vless/vmess, servers — у trojan/ss/hysteria2
                val server = (settings?.get("vnext") as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                    ?: (settings?.get("servers") as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                (server as? kotlinx.serialization.json.JsonObject)?.let { s ->
                    address = s["address"]?.jsonPrimitive?.contentOrNull ?: address
                    port = s["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: port
                }
                // Hysteria2 и TUIC кладут адрес прямо в settings, без списка
                address = settings?.get("address")?.jsonPrimitive?.contentOrNull ?: address
                port = settings?.get("port")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: port
                (obj["streamSettings"] as? kotlinx.serialization.json.JsonObject)?.let { stream ->
                    network = stream["network"]?.jsonPrimitive?.contentOrNull ?: network
                    security = stream["security"]?.jsonPrimitive?.contentOrNull ?: security
                }
                break
            }
            val name = config["remarks"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: address.ifBlank { "Конфиг ${index + 1}" }
            // Server Description из панели Remnawave лежит в корне конфига.
            // Оба написания: панель отдаёт camelCase, но в самодельных шаблонах
            // подписки встречается и с подчёркиванием.
            val description =
                (config["serverDescription"] ?: config["server_description"])
                    ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ProxyProfile(
                protocol = protocol,
                name = name,
                address = address.ifBlank { "-" },
                port = port,
                userId = "",
                serverDescription = description,
                rawProtocol = rawProtocol,
                network = network,
                security = security,
                rawConfig = config.toString(),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun tryBase64(text: String): String? = try {
        val cleaned = text.replace("\n", "").replace("\r", "")
        String(Base64.decode(cleaned, Base64.DEFAULT))
    } catch (_: Exception) {
        null
    }

    fun parseLink(link: String): ProxyProfile? = try {
        when {
            link.startsWith("vless://") -> parseVless(link)
            link.startsWith("trojan://") -> parseTrojan(link)
            link.startsWith("vmess://") -> parseVmess(link)
            link.startsWith("ss://") -> parseShadowsocks(link)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun decode(value: String?): String? =
        value?.let { URLDecoder.decode(it, "UTF-8") }

    private fun queryOf(uri: URI): Map<String, String> {
        val raw = uri.rawQuery ?: return emptyMap()
        return raw.split('&').mapNotNull {
            val kv = it.split('=', limit = 2)
            if (kv.size == 2) kv[0] to (decode(kv[1]) ?: "") else null
        }.toMap()
    }

    private fun fragmentName(uri: URI, fallback: String): String =
        decode(uri.rawFragment)?.takeIf { it.isNotBlank() } ?: fallback

    private fun parseVless(link: String): ProxyProfile? {
        val uri = URI(link)
        val host = uri.host ?: return null
        val q = queryOf(uri)
        return ProxyProfile(
            protocol = Protocol.VLESS,
            name = fragmentName(uri, host),
            address = host,
            port = uri.port.takeIf { it > 0 } ?: 443,
            userId = uri.userInfo ?: return null,
            flow = q["flow"]?.takeIf { it.isNotBlank() },
            encryption = q["encryption"] ?: "none",
            network = q["type"] ?: "tcp",
            security = q["security"] ?: "none",
            sni = q["sni"],
            alpn = q["alpn"],
            fingerprint = q["fp"],
            allowInsecure = q["allowInsecure"] == "1" || q["allowInsecure"] == "true",
            publicKey = q["pbk"],
            shortId = q["sid"],
            spiderX = q["spx"],
            host = q["host"],
            path = q["path"],
            serviceName = q["serviceName"],
            grpcMultiMode = q["mode"] == "multi",
            headerType = q["headerType"],
        )
    }

    private fun parseTrojan(link: String): ProxyProfile? {
        val uri = URI(link)
        val host = uri.host ?: return null
        val q = queryOf(uri)
        return ProxyProfile(
            protocol = Protocol.TROJAN,
            name = fragmentName(uri, host),
            address = host,
            port = uri.port.takeIf { it > 0 } ?: 443,
            userId = decode(uri.userInfo) ?: return null,
            network = q["type"] ?: "tcp",
            security = q["security"] ?: "tls",
            sni = q["sni"],
            alpn = q["alpn"],
            fingerprint = q["fp"],
            allowInsecure = q["allowInsecure"] == "1" || q["allowInsecure"] == "true",
            host = q["host"],
            path = q["path"],
            serviceName = q["serviceName"],
            grpcMultiMode = q["mode"] == "multi",
        )
    }

    private fun parseVmess(link: String): ProxyProfile? {
        val payload = link.removePrefix("vmess://")
        val decoded = tryBase64(payload) ?: return null
        val obj = json.parseToJsonElement(decoded).jsonObject
        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull
        val address = str("add") ?: return null
        val tls = str("tls") ?: ""
        return ProxyProfile(
            protocol = Protocol.VMESS,
            name = str("ps") ?: address,
            address = address,
            port = str("port")?.toIntOrNull() ?: 443,
            userId = str("id") ?: return null,
            network = str("net") ?: "tcp",
            security = if (tls == "tls") "tls" else "none",
            sni = str("sni")?.takeIf { it.isNotBlank() },
            alpn = str("alpn")?.takeIf { it.isNotBlank() },
            fingerprint = str("fp")?.takeIf { it.isNotBlank() },
            host = str("host")?.takeIf { it.isNotBlank() },
            path = str("path")?.takeIf { it.isNotBlank() },
            serviceName = str("path")?.takeIf { (str("net") ?: "") == "grpc" },
            headerType = str("type")?.takeIf { it.isNotBlank() && it != "none" },
            vmessSecurity = str("scy") ?: "auto",
        )
    }

    private fun parseShadowsocks(link: String): ProxyProfile? {
        val uri = URI(link)
        // Форма 1: ss://base64(method:password)@host:port#name
        if (uri.host != null && uri.userInfo != null) {
            val userInfo = tryBase64(padBase64(uri.userInfo)) ?: uri.userInfo
            val parts = userInfo.split(':', limit = 2)
            if (parts.size == 2) {
                return ProxyProfile(
                    protocol = Protocol.SHADOWSOCKS,
                    name = fragmentName(uri, uri.host),
                    address = uri.host,
                    port = uri.port.takeIf { it > 0 } ?: 8388,
                    userId = parts[1],
                    encryption = parts[0],
                )
            }
        }
        // Форма 2: ss://base64(method:password@host:port)#name
        val body = link.removePrefix("ss://").substringBefore('#')
        val decoded = tryBase64(padBase64(body)) ?: return null
        val match = Regex("^(.+?):(.+)@(.+):(\\d+)$").find(decoded) ?: return null
        val (method, password, host, port) = match.destructured
        val name = link.substringAfter('#', "").let { decode(it) }?.takeIf { it.isNotBlank() }
        return ProxyProfile(
            protocol = Protocol.SHADOWSOCKS,
            name = name ?: host,
            address = host,
            port = port.toInt(),
            userId = password,
            encryption = method,
        )
    }

    private fun padBase64(value: String): String {
        val cleaned = value.replace('-', '+').replace('_', '/')
        return cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
    }
}
