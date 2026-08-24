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

object LinkParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSubscriptionContent(content: String): List<ProxyProfile> {
        val text = content.trim()
        if (text.startsWith("[") || text.startsWith("{")) {
            val fromJson = parseXrayJsonSubscription(text)
            if (fromJson.isNotEmpty()) return fromJson
        }
        val decoded = if (text.startsWith("vless://") || text.startsWith("vmess://") ||
            text.startsWith("trojan://") || text.startsWith("ss://")) text else tryBase64(text) ?: text
        val trimmedDecoded = decoded.trim()
        if (trimmedDecoded.startsWith("[") || trimmedDecoded.startsWith("{")) {
            val fromJson = parseXrayJsonSubscription(trimmedDecoded)
            if (fromJson.isNotEmpty()) return fromJson
        }
        return decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { parseLink(it) }
    }

    private fun parseXrayJsonSubscription(text: String): List<ProxyProfile> = try {
        val root = json.parseToJsonElement(text)
        val configs = when (root) {
            is kotlinx.serialization.json.JsonArray -> root.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            is kotlinx.serialization.json.JsonObject -> if (root.containsKey("outbounds")) listOf(root) else emptyList()
            else -> emptyList()
        }
        configs.mapIndexedNotNull { index, config ->
            val outbounds = config["outbounds"] as? kotlinx.serialization.json.JsonArray ?: return@mapIndexedNotNull null
            var protocol = Protocol.VLESS
            var address = ""
            var port = 443
            var network = "tcp"
            var security = "none"
            for (outbound in outbounds) {
                val obj = outbound as? kotlinx.serialization.json.JsonObject ?: continue
                val proto = obj["protocol"]?.jsonPrimitive?.contentOrNull ?: continue
                val parsed = when (proto) {
                    "vless" -> Protocol.VLESS
                    "vmess" -> Protocol.VMESS
                    "trojan" -> Protocol.TROJAN
                    "shadowsocks" -> Protocol.SHADOWSOCKS
                    else -> null
                } ?: continue
                protocol = parsed
                val settings = obj["settings"] as? kotlinx.serialization.json.JsonObject
                val server = (settings?.get("vnext") as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                    ?: (settings?.get("servers") as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                (server as? kotlinx.serialization.json.JsonObject)?.let { s ->
                    address = s["address"]?.jsonPrimitive?.contentOrNull ?: address
                    port = s["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: port
                }
                (obj["streamSettings"] as? kotlinx.serialization.json.JsonObject)?.let { stream ->
                    network = stream["network"]?.jsonPrimitive?.contentOrNull ?: network
                    security = stream["security"]?.jsonPrimitive?.contentOrNull ?: security
                }
                break
            }
            val name = config["remarks"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: address.ifBlank { "Конфиг ${index + 1}" }
            // Remnawave XRAY_JSON puts the host's Server Description at the root.
            // Keep both spellings for compatibility with older/custom templates.
            val description = (config["serverDescription"] ?: config["server_description"])
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ProxyProfile(protocol, name, address.ifBlank { "-" }, port, "",
                network = network, security = security, serverDescription = description,
                rawConfig = config.toString())
        }
    } catch (_: Exception) { emptyList() }

    private fun tryBase64(text: String): String? = try {
        val cleaned = text.replace("\n", "").replace("\r", "")
        String(Base64.decode(cleaned, Base64.DEFAULT))
    } catch (_: Exception) { null }

    fun parseLink(link: String): ProxyProfile? = try {
        when {
            link.startsWith("vless://") -> parseVless(link)
            link.startsWith("trojan://") -> parseTrojan(link)
            link.startsWith("vmess://") -> parseVmess(link)
            link.startsWith("ss://") -> parseShadowsocks(link)
            else -> null
        }
    } catch (_: Exception) { null }

    private fun decode(value: String?): String? = value?.let { URLDecoder.decode(it, "UTF-8") }
    private fun queryOf(uri: URI): Map<String, String> = uri.rawQuery?.split('&')?.mapNotNull {
        val kv = it.split('=', limit = 2); if (kv.size == 2) kv[0] to (decode(kv[1]) ?: "") else null
    }?.toMap() ?: emptyMap()
    private fun fragmentName(uri: URI, fallback: String) = decode(uri.rawFragment)?.takeIf { it.isNotBlank() } ?: fallback

    private fun parseVless(link: String): ProxyProfile? {
        val uri = URI(link); val host = uri.host ?: return null; val q = queryOf(uri)
        return ProxyProfile(Protocol.VLESS, fragmentName(uri, host), host, uri.port.takeIf { it > 0 } ?: 443,
            uri.userInfo ?: return null, flow=q["flow"]?.takeIf { it.isNotBlank() }, encryption=q["encryption"] ?: "none",
            network=q["type"] ?: "tcp", security=q["security"] ?: "none", sni=q["sni"], alpn=q["alpn"], fingerprint=q["fp"],
            allowInsecure=q["allowInsecure"] == "1" || q["allowInsecure"] == "true", publicKey=q["pbk"], shortId=q["sid"],
            spiderX=q["spx"], host=q["host"], path=q["path"], serviceName=q["serviceName"], grpcMultiMode=q["mode"] == "multi", headerType=q["headerType"])
    }
    private fun parseTrojan(link: String): ProxyProfile? {
        val uri=URI(link); val host=uri.host ?: return null; val q=queryOf(uri)
        return ProxyProfile(Protocol.TROJAN, fragmentName(uri,host),host,uri.port.takeIf{it>0}?:443,decode(uri.userInfo)?:return null,
            network=q["type"]?:"tcp",security=q["security"]?:"tls",sni=q["sni"],alpn=q["alpn"],fingerprint=q["fp"],
            allowInsecure=q["allowInsecure"]=="1"||q["allowInsecure"]=="true",host=q["host"],path=q["path"],serviceName=q["serviceName"],grpcMultiMode=q["mode"]=="multi")
    }
    private fun parseVmess(link: String): ProxyProfile? {
        val obj=json.parseToJsonElement(tryBase64(link.removePrefix("vmess://"))?:return null).jsonObject
        fun str(k:String)=obj[k]?.jsonPrimitive?.contentOrNull
        val address=str("add")?:return null; val tls=str("tls")?:""
        return ProxyProfile(Protocol.VMESS,str("ps")?:address,address,str("port")?.toIntOrNull()?:443,str("id")?:return null,
            network=str("net")?:"tcp",security=if(tls=="tls")"tls" else "none",sni=str("sni")?.takeIf{it.isNotBlank()},alpn=str("alpn")?.takeIf{it.isNotBlank()},
            fingerprint=str("fp")?.takeIf{it.isNotBlank()},host=str("host")?.takeIf{it.isNotBlank()},path=str("path")?.takeIf{it.isNotBlank()},
            serviceName=str("path")?.takeIf{(str("net")?:"")=="grpc"},headerType=str("type")?.takeIf{it.isNotBlank()&&it!="none"},vmessSecurity=str("scy")?:"auto")
    }
    private fun parseShadowsocks(link:String):ProxyProfile? {
        val uri=URI(link)
        if(uri.host!=null&&uri.userInfo!=null){val ui=tryBase64(padBase64(uri.userInfo))?:uri.userInfo;val p=ui.split(':',limit=2);if(p.size==2)return ProxyProfile(Protocol.SHADOWSOCKS,fragmentName(uri,uri.host),uri.host,uri.port.takeIf{it>0}?:8388,p[1],encryption=p[0])}
        val decoded=tryBase64(padBase64(link.removePrefix("ss://").substringBefore('#')))?:return null
        val m=Regex("^(.+?):(.+)@(.+):(\\d+)$").find(decoded)?:return null;val(method,password,host,port)=m.destructured
        val name=link.substringAfter('#',"").let{decode(it)}?.takeIf{it.isNotBlank()};return ProxyProfile(Protocol.SHADOWSOCKS,name?:host,host,port.toInt(),password,encryption=method)
    }
    private fun padBase64(value:String):String { val c=value.replace('-','+').replace('_','/'); return c+"=".repeat((4-c.length%4)%4) }
}
