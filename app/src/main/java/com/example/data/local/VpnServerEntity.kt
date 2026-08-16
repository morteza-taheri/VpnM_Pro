package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.VpnServer

@Entity(tableName = "vpn_servers")
data class VpnServerEntity(
    @PrimaryKey val id: String,
    val hostName: String,
    val ip: String,
    val score: Long,
    val pingMs: Int,
    val speedBps: Long,
    val countryLong: String,
    val countryShort: String,
    val numSessions: Int,
    val uptime: Long,
    val totUsers: Long,
    val totTraffic: Long,
    val logType: String,
    val operator: String,
    val comment: String,
    val openVpnConfigData: String?,
    val softEtherTcpPort: Int?,
    val softEtherUdpSupported: Boolean,
    val l2tpSupported: Boolean,
    val openVpnTcpPort: Int?,
    val openVpnUdpPort: Int?,
    val sstpHostname: String?,
    val sstpPort: Int?,
    val source: String,
    val lastSeenTime: Long
) {
    fun toDomain(): VpnServer {
        return VpnServer(
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
            softEtherTcpPort = softEtherTcpPort,
            softEtherUdpSupported = softEtherUdpSupported,
            l2tpSupported = l2tpSupported,
            openVpnTcpPort = openVpnTcpPort,
            openVpnUdpPort = openVpnUdpPort,
            sstpHostname = sstpHostname,
            sstpPort = sstpPort,
            source = source,
            lastSeenTime = lastSeenTime
        )
    }

    companion object {
        fun fromDomain(server: VpnServer): VpnServerEntity {
            return VpnServerEntity(
                id = server.id,
                hostName = server.hostName,
                ip = server.ip,
                score = server.score,
                pingMs = server.pingMs,
                speedBps = server.speedBps,
                countryLong = server.countryLong,
                countryShort = server.countryShort,
                numSessions = server.numSessions,
                uptime = server.uptime,
                totUsers = server.totUsers,
                totTraffic = server.totTraffic,
                logType = server.logType,
                operator = server.operator,
                comment = server.comment,
                openVpnConfigData = server.openVpnConfigData,
                softEtherTcpPort = server.softEtherTcpPort,
                softEtherUdpSupported = server.softEtherUdpSupported,
                l2tpSupported = server.l2tpSupported,
                openVpnTcpPort = server.openVpnTcpPort,
                openVpnUdpPort = server.openVpnUdpPort,
                sstpHostname = server.sstpHostname,
                sstpPort = server.sstpPort,
                source = server.source,
                lastSeenTime = server.lastSeenTime
            )
        }
    }
}
