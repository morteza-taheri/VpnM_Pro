package com.example.vpn.model

data class ConnectionConfig(
    val host: String,
    val ip: String,
    val port: Int = 443,
    val candidatePorts: List<Int> = emptyList(),
    val username: String = "vpn",
    val password: String = "vpn",
    val hubName: String = "VPN",
    val authMethod: AuthMethod = AuthMethod.AUTO,
    val transportProtocol: TransportProtocol = TransportProtocol.AUTO
)
