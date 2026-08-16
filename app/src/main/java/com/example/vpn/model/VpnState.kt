package com.example.vpn.model

sealed class VpnState {
    object Disconnected : VpnState()
    data class Connecting(val message: String = "Connecting...") : VpnState()
    data class Connected(
        val config: ConnectionConfig,
        val durationSeconds: Long = 0,
        val stats: TrafficStats = TrafficStats()
    ) : VpnState()
    data class Disconnecting(val message: String = "Disconnecting...") : VpnState()
    data class Error(val message: String) : VpnState()
}
