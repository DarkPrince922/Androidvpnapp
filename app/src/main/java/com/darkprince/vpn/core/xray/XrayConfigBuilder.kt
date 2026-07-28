package com.darkprince.vpn.core.xray

import com.darkprince.vpn.core.model.Protocol
import com.darkprince.vpn.core.model.ProxyProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Генерация конфигурации Xray-core: локальный SOCKS-инбаунд, аутбаунд по
 * выбранному профилю, статистика для подсчёта трафика.
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 10808

    private val json = Json { prettyPrint = false }

    fun build(profile: ProxyProfile): String {
        val config = buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonObject("stats") { }
            putJsonObject("policy") {
                putJsonObject("levels") {
                    putJsonObject("8") {
                        put("handshake", 4)
                        put("connIdle", 300)
                        put("uplinkOnly", 1)
                        put("downlinkOnly", 1)
                    }
                }
                putJsonObject("system") {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add("1.1.1.1")
                    add("8.8.8.8")
                }
            }
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("tag", "socks")
                    put("listen", "127.0.0.1")
                    put("port", SOCKS_PORT)
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", true)
                    }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        putJsonArray("destOverride") {
                            add("http")
                            add("tls")
                        }
                        put("routeOnly", false)
                    }
                })
            }
            putJsonArray("outbounds") {
                add(buildOutbound(profile))
                add(buildJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                    putJsonObject("settings") { }
                })
                add(buildJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                    putJsonObject("settings") { }
                })
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    add(buildJsonObject {
                        put("type", "field")
                        putJsonArray("ip") {
                            add("10.0.0.0/8")
                            add("172.16.0.0/12")
                            add("192.168.0.0/16")
                            add("127.0.0.0/8")
                            add("169.254.0.0/16")
                            add("100.64.0.0/10")
                        }
                        put("outboundTag", "direct")
                    })
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun buildOutbound(p: ProxyProfile): JsonObject = buildJsonObject {
        put("tag", "proxy")
        when (p.protocol) {
            Protocol.VLESS -> {
                put("protocol", "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(buildJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            putJsonArray("users") {
                                add(buildJsonObject {
                                    put("id", p.userId)
                                    put("encryption", p.encryption ?: "none")
                                    put("level", 8)
                                    p.flow?.let { put("flow", it) }
                                })
                            }
                        })
                    }
                }
            }
            Protocol.VMESS -> {
                put("protocol", "vmess")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(buildJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            putJsonArray("users") {
                                add(buildJsonObject {
                                    put("id", p.userId)
                                    put("security", p.vmessSecurity)
                                    put("alterId", 0)
                                    put("level", 8)
                                })
                            }
                        })
                    }
                }
            }
            Protocol.TROJAN -> {
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            put("password", p.userId)
                            put("level", 8)
                        })
                    }
                }
            }
            Protocol.SHADOWSOCKS -> {
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            put("method", p.encryption ?: "aes-256-gcm")
                            put("password", p.userId)
                            put("level", 8)
                        })
                    }
                }
            }
        }
        put("streamSettings", buildStreamSettings(p))
        putJsonObject("mux") { put("enabled", false) }
    }

    private fun buildStreamSettings(p: ProxyProfile): JsonObject = buildJsonObject {
        put("network", p.network)
        put("security", p.security)

        when (p.security) {
            "tls" -> putJsonObject("tlsSettings") {
                put("serverName", p.sni ?: p.host ?: p.address)
                put("allowInsecure", p.allowInsecure)
                p.fingerprint?.let { put("fingerprint", it) }
                p.alpn?.let { alpn ->
                    putJsonArray("alpn") { alpn.split(',').forEach { add(it.trim()) } }
                }
            }
            "reality" -> putJsonObject("realitySettings") {
                put("serverName", p.sni ?: "")
                put("fingerprint", p.fingerprint ?: "chrome")
                put("publicKey", p.publicKey ?: "")
                put("shortId", p.shortId ?: "")
                put("spiderX", p.spiderX ?: "")
            }
        }

        when (p.network) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", p.path ?: "/")
                p.host?.let { host ->
                    putJsonObject("headers") { put("Host", host) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", p.serviceName ?: "")
                put("multiMode", p.grpcMultiMode)
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                put("path", p.path ?: "/")
                p.host?.let { put("host", it) }
            }
            "xhttp" -> putJsonObject("xhttpSettings") {
                put("path", p.path ?: "/")
                p.host?.let { put("host", it) }
            }
            "tcp" -> if (p.headerType == "http") {
                putJsonObject("tcpSettings") {
                    putJsonObject("header") {
                        put("type", "http")
                        putJsonObject("request") {
                            putJsonArray("path") { add(p.path ?: "/") }
                            putJsonObject("headers") {
                                putJsonArray("Host") { add(p.host ?: "") }
                            }
                        }
                    }
                }
            }
        }
    }
}
