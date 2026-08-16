package com.example.data.remote

import com.example.data.model.VpnServer
import java.io.BufferedReader
import java.io.StringReader

object VpnGateCsvParser {
    fun parseCsv(csvText: String, sourceName: String = "Official CSV"): List<VpnServer> {
        val servers = mutableListOf<VpnServer>()
        val reader = BufferedReader(StringReader(csvText))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line?.trim() ?: continue
            if (currentLine.isEmpty() || currentLine.startsWith("*") || currentLine.startsWith("#")) {
                continue
            }

            val tokens = currentLine.split(",")
            if (tokens.size < 15) continue

            try {
                val hostName = tokens[0].trim()
                val ip = tokens[1].trim()
                val score = tokens[2].trim().toLongOrNull() ?: 0L
                val pingMs = tokens[3].trim().toIntOrNull() ?: -1
                val speedBps = tokens[4].trim().toLongOrNull() ?: 0L
                val countryLong = tokens[5].trim()
                val countryShort = tokens[6].trim()
                val numSessions = tokens[7].trim().toIntOrNull() ?: 0
                val uptime = tokens[8].trim().toLongOrNull() ?: 0L
                val totUsers = tokens[9].trim().toLongOrNull() ?: 0L
                val totTraffic = tokens[10].trim().toLongOrNull() ?: 0L
                val logType = tokens[11].trim()
                val operator = tokens[12].trim()
                val comment = tokens[13].trim()
                val openVpnConfigData = if (tokens.size > 14) tokens[14].trim() else null

                var parsedTcpPort: Int? = null
                var parsedUdpPort: Int? = null
                var isUdpSupported = true

                if (!openVpnConfigData.isNullOrBlank()) {
                    try {
                        val decodedBytes = android.util.Base64.decode(openVpnConfigData, android.util.Base64.DEFAULT)
                        val configText = String(decodedBytes, Charsets.UTF_8)
                        
                        var currentProto = "tcp"
                        for (rawConfigLine in configText.split("\n")) {
                            val cfgLine = rawConfigLine.trim()
                            if (cfgLine.startsWith("proto ", ignoreCase = true)) {
                                currentProto = cfgLine.substring(6).trim().lowercase()
                            } else if (cfgLine.startsWith("remote ", ignoreCase = true)) {
                                val parts = cfgLine.split("\\s+".toRegex())
                                if (parts.size >= 3) {
                                    val port = parts[2].toIntOrNull()
                                    if (port != null && port in 1..65535) {
                                        val lineProto = if (parts.size >= 4) parts[3].lowercase() else currentProto
                                        if (lineProto.contains("udp")) {
                                            if (parsedUdpPort == null) parsedUdpPort = port
                                        } else {
                                            if (parsedTcpPort == null) parsedTcpPort = port
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                val primaryTcpPort = parsedTcpPort ?: 443
                val primaryUdpPort = parsedUdpPort ?: 1194

                val id = "${hostName}_${ip}"

                val server = VpnServer(
                    id = id,
                    hostName = hostName,
                    ip = ip,
                    score = score,
                    pingMs = pingMs,
                    speedBps = speedBps,
                    countryLong = countryLong,
                    countryShort = countryShort,
                    numSessions = numSessions,
                    uptime = uptime,
                    totUsers = totUsers,
                    totTraffic = totTraffic,
                    logType = logType,
                    operator = operator,
                    comment = comment,
                    openVpnConfigData = openVpnConfigData,
                    softEtherTcpPort = primaryTcpPort,
                    softEtherUdpSupported = isUdpSupported,
                    l2tpSupported = true,
                    openVpnTcpPort = primaryTcpPort,
                    openVpnUdpPort = primaryUdpPort,
                    source = sourceName,
                    lastSeenTime = System.currentTimeMillis()
                )
                servers.add(server)
            } catch (e: Exception) {
                // Ignore malformed rows
            }
        }
        return servers
    }
}
