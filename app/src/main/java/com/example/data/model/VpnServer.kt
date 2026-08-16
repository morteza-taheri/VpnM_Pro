package com.example.data.model

data class VpnServer(
    val id: String,
    val hostName: String,
    val ip: String,
    val score: Long = 0,
    val pingMs: Int = -1,
    val speedBps: Long = 0,
    val countryLong: String = "",
    val countryShort: String = "",
    val numSessions: Int = 0,
    val uptime: Long = 0,
    val totUsers: Long = 0,
    val totTraffic: Long = 0,
    val logType: String = "",
    val operator: String = "",
    val comment: String = "",
    val openVpnConfigData: String? = null,
    val softEtherTcpPort: Int? = null,
    val softEtherUdpSupported: Boolean = false,
    val l2tpSupported: Boolean = false,
    val openVpnTcpPort: Int? = null,
    val openVpnUdpPort: Int? = null,
    val sstpHostname: String? = null,
    val sstpPort: Int? = null,
    val source: String = "VPNGate",
    val lastSeenTime: Long = System.currentTimeMillis()
) {
    val speedMbps: Double
        get() = (speedBps / 1_000_000.0).let { if (it < 0) 0.0 else it }

    val effectivePort: Int
        get() = softEtherTcpPort ?: openVpnTcpPort ?: 443

    val candidatePorts: List<Int>
        get() {
            val list = mutableListOf<Int>()
            softEtherTcpPort?.let { if (it in 1..65535) list.add(it) }
            openVpnTcpPort?.let { if (it in 1..65535 && !list.contains(it)) list.add(it) }
            sstpPort?.let { if (it in 1..65535 && !list.contains(it)) list.add(it) }
            for (p in listOf(443, 995, 1194, 5555, 992, 8888, 1443, 1195, 1980)) {
                if (!list.contains(p)) list.add(p)
            }
            return list
        }

    val isSoftEtherSupported: Boolean
        get() = softEtherTcpPort != null || softEtherUdpSupported
}
