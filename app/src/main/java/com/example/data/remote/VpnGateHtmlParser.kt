package com.example.data.remote

import com.example.data.model.VpnServer
import java.util.regex.Pattern

object VpnGateHtmlParser {
    private const val MARKER_SSL_VPN = "SSL-VPN Connect guide"
    private const val MARKER_L2TP = "L2TP/IPsec Connect guide"
    private const val MARKER_OPENVPN = "OpenVPN Config file"
    private const val MARKER_SSTP = "MS-SSTP Connect guide"

    private val HOSTNAME_PATTERN = Pattern.compile("\\b([A-Za-z0-9._-]+\\.opengw\\.net)\\b")
    private val IPV4_PATTERN = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b")
    private val TCP_PORT_PATTERN = Pattern.compile("TCP:\\s*(\\d+)")
    private val UDP_PORT_PATTERN = Pattern.compile("UDP:\\s*(\\d+)")
    private val UDP_SUPPORTED_PATTERN = Pattern.compile("UDP:\\s*Supported")
    private val SSTP_HOSTNAME_PATTERN = Pattern.compile("SSTP Hostname\\s*:\\s*([A-Za-z0-9._-]+\\.opengw\\.net)(?::(\\d+))?")
    private val PING_PATTERN = Pattern.compile("(\\d+)\\s*ms")
    private val SPEED_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(Mbps|Kbps|Gbps)", Pattern.CASE_INSENSITIVE)

    fun parseHtml(htmlText: String, sourceName: String = "Web Mirror"): List<VpnServer> {
        val servers = mutableListOf<VpnServer>()

        // Clean HTML tags into text rows by splitting <tr> elements or lines
        val trRegex = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = trRegex.matcher(htmlText)

        val rows = mutableListOf<String>()
        while (matcher.find()) {
            val rawRow = matcher.group(1) ?: continue
            val cleanRow = rawRow.replaceAllTags(" ")
            if (cleanRow.contains("opengw.net") || cleanRow.contains("SSL-VPN")) {
                rows.add(cleanRow)
            }
        }

        // If no <tr> found, fallback to line-by-line or paragraph processing
        if (rows.isEmpty()) {
            val lines = htmlText.replaceAllTags("\n").split("\n")
            var currentBuffer = StringBuilder()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                currentBuffer.append(" ").append(trimmed)
                if (trimmed.contains(MARKER_SSTP) || trimmed.contains(MARKER_OPENVPN)) {
                    rows.add(currentBuffer.toString())
                    currentBuffer = StringBuilder()
                }
            }
            if (currentBuffer.isNotEmpty()) {
                rows.add(currentBuffer.toString())
            }
        }

        for (rowText in rows) {
            val server = parseRow(rowText, sourceName)
            if (server != null) {
                servers.add(server)
            }
        }

        return servers
    }

    private fun parseRow(rowText: String, sourceName: String): VpnServer? {
        val hostMatcher = HOSTNAME_PATTERN.matcher(rowText)
        val ipMatcher = IPV4_PATTERN.matcher(rowText)

        if (!hostMatcher.find() || !ipMatcher.find()) {
            return null
        }

        val hostName = hostMatcher.group(1) ?: return null
        val ip = ipMatcher.group(1) ?: return null

        val sslVpnIdx = rowText.indexOf(MARKER_SSL_VPN)
        val l2tpIdx = rowText.indexOf(MARKER_L2TP)
        val openVpnIdx = rowText.indexOf(MARKER_OPENVPN)
        val sstpIdx = rowText.indexOf(MARKER_SSTP)

        val markers = mutableListOf<Pair<String, Int>>()
        if (sslVpnIdx != -1) markers.add(MARKER_SSL_VPN to sslVpnIdx)
        if (l2tpIdx != -1) markers.add(MARKER_L2TP to l2tpIdx)
        if (openVpnIdx != -1) markers.add(MARKER_OPENVPN to openVpnIdx)
        if (sstpIdx != -1) markers.add(MARKER_SSTP to sstpIdx)

        val sslVpnSegment = segmentFor(rowText, markers, MARKER_SSL_VPN, sslVpnIdx)
        val openVpnSegment = segmentFor(rowText, markers, MARKER_OPENVPN, openVpnIdx)
        val sstpSegment = segmentFor(rowText, markers, MARKER_SSTP, sstpIdx)

        val sslTcpMatch = TCP_PORT_PATTERN.matcher(sslVpnSegment)
        val softEtherTcpPort = if (sslTcpMatch.find()) sslTcpMatch.group(1)?.toIntOrNull() else 443
        val softEtherUdpSupported = UDP_SUPPORTED_PATTERN.matcher(sslVpnSegment).find()
        val l2tpSupported = l2tpIdx != -1

        val openVpnTcpMatch = TCP_PORT_PATTERN.matcher(openVpnSegment)
        val openVpnUdpMatch = UDP_PORT_PATTERN.matcher(openVpnSegment)
        val openVpnTcpPort = if (openVpnTcpMatch.find()) openVpnTcpMatch.group(1)?.toIntOrNull() else null
        val openVpnUdpPort = if (openVpnUdpMatch.find()) openVpnUdpMatch.group(1)?.toIntOrNull() else null

        val sstpMatch = SSTP_HOSTNAME_PATTERN.matcher(sstpSegment)
        var sstpHostname: String? = null
        var sstpPort: Int? = null
        if (sstpMatch.find()) {
            sstpHostname = sstpMatch.group(1)
            sstpPort = sstpMatch.group(2)?.toIntOrNull() ?: 443
        }

        // Parse Ping
        var pingMs = -1
        val pingMatch = PING_PATTERN.matcher(rowText)
        if (pingMatch.find()) {
            pingMs = pingMatch.group(1)?.toIntOrNull() ?: -1
        }

        // Parse Speed
        var speedBps = 0L
        val speedMatch = SPEED_PATTERN.matcher(rowText)
        if (speedMatch.find()) {
            val valStr = speedMatch.group(1)
            val unit = speedMatch.group(2)?.lowercase() ?: ""
            val valNum = valStr?.toDoubleOrNull() ?: 0.0
            speedBps = when (unit) {
                "gbps" -> (valNum * 1_000_000_000).toLong()
                "mbps" -> (valNum * 1_000_000).toLong()
                "kbps" -> (valNum * 1_000).toLong()
                else -> valNum.toLong()
            }
        }

        // Extract country
        val country = extractCountry(rowText)

        return VpnServer(
            id = "${hostName}_${ip}",
            hostName = hostName,
            ip = ip,
            score = 100000L,
            pingMs = pingMs,
            speedBps = if (speedBps > 0) speedBps else 10_000_000L,
            countryLong = country.first,
            countryShort = country.second,
            softEtherTcpPort = softEtherTcpPort,
            softEtherUdpSupported = softEtherUdpSupported,
            l2tpSupported = l2tpSupported,
            openVpnTcpPort = openVpnTcpPort,
            openVpnUdpPort = openVpnUdpPort,
            sstpHostname = sstpHostname,
            sstpPort = sstpPort,
            source = sourceName,
            lastSeenTime = System.currentTimeMillis()
        )
    }

    private fun segmentFor(text: String, markers: List<Pair<String, Int>>, marker: String, index: Int): String {
        if (index == -1) return ""
        val contentStart = index + marker.length
        val laterPositions = markers.filter { it.second > index }.map { it.second }
        val nextStart = if (laterPositions.isNotEmpty()) laterPositions.minOrNull()!! else text.length
        return if (contentStart <= nextStart && contentStart <= text.length) {
            text.substring(contentStart, minOf(nextStart, text.length))
        } else ""
    }

    private fun String.replaceAllTags(replacement: String): String {
        return this.replace(Regex("<[^>]*>"), replacement).replace(Regex("&nbsp;"), " ").replace(Regex("\\s+"), " ")
    }

    private fun extractCountry(text: String): Pair<String, String> {
        val countries = listOf(
            "Japan" to "JP",
            "United States" to "US",
            "Korea" to "KR",
            "Taiwan" to "TW",
            "Croatia" to "HR",
            "Armenia" to "AM",
            "Germany" to "DE",
            "United Kingdom" to "GB",
            "France" to "FR",
            "Canada" to "CA",
            "Russia" to "RU",
            "Australia" to "AU",
            "Singapore" to "SG",
            "Hong Kong" to "HK",
            "Vietnam" to "VN",
            "Thailand" to "TH"
        )
        for ((longName, code) in countries) {
            if (text.contains(longName, ignoreCase = true) || text.contains("($code)")) {
                return longName to code
            }
        }
        return "Unknown" to "UN"
    }
}
