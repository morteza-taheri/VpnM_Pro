package com.example.vpn.model

data class TrafficStats(
    val bytesRx: Long = 0,
    val bytesTx: Long = 0,
    val speedRxBps: Long = 0,
    val speedTxBps: Long = 0
) {
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bps: Long): String {
        val bytesPerSec = bps
        return when {
            bytesPerSec >= 1_048_576 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }
}
