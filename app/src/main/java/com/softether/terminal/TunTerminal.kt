package com.softether.terminal

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * TunTerminal — forwards L3 IP packets between the Android TUN interface and the
 * SoftEther native data channel (nativeSend / nativeReceive).
 *
 * IMPORTANT DATA-PATH CONTRACT (verified against softether-core source):
 *   - nativeSend == softether_send()   expects an L3 IP packet and wraps it in an
 *                                       Ethernet frame INTERNALLY (uses the dynamic
 *                                       client/gateway MAC resolved during DHCP/ARP).
 *   - nativeReceive == softether_receive() returns an L3 IP packet (the 14-byte Ethernet
 *                                       header is already stripped; ARP is answered/filtered
 *                                       in native; only IPv4/IPv6 EtherTypes are delivered).
 *
 * Therefore this class hands L3 IP packets straight through in BOTH directions and MUST NOT
 * build or strip Ethernet frames itself:
 *   - Old RX: write() read bytes[12..13] of the L3 packet (source IP) as an EtherType, never
 *     matched 0x0800/0x86DD and returned 0 -> nothing written to TUN -> received=0.
 *   - Old TX: TUN frames were wrapped in Ethernet here AND again in softether_send ->
 *     frame-in-frame -> dropped by the server.
 */
class TunTerminal(
    private val vpnInterface: ParcelFileDescriptor
) {
    companion object {
        private const val TAG = "TunTerminal"
        private const val BUFFER_SIZE = 65535
        private const val IPV4 = 0x04
        private const val IPV6 = 0x06
    }

    var onPacketReceived: ((ByteArray) -> Unit)? = null

    // Dynamic MACs obtained from DHCP/ARP resolution. The native layer uses these internally to
    // build Ethernet frames; kept here for diagnostics only. Never hardcoded.
    @Volatile var gatewayMac: ByteArray? = null
    @Volatile var clientMac: ByteArray? = null

    @Volatile var isRunning = false
        private set

    // RX counters: incremented ONLY after a packet is successfully written to the TUN interface.
    private var rxPackets = 0L
    private var rxBytes = 0L

    private var readThread: Thread? = null
    private val inputStream = FileInputStream(vpnInterface.fileDescriptor)
    private val outputStream = FileOutputStream(vpnInterface.fileDescriptor)

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "TUN terminal starting (L3 passthrough) GW=${formatMac(gatewayMac)} CLIENT=${formatMac(clientMac)}")
        readThread = Thread({ readLoop() }, "TUN-Reader").apply {
            isDaemon = true
            start()
        }
    }

    private fun readLoop() {
        val ipBuffer = ByteArray(BUFFER_SIZE)
        var txPackets = 0L
        var txBytes = 0L
        while (isRunning) {
            try {
                val ipLen = inputStream.read(ipBuffer)
                if (ipLen < 0) break
                if (ipLen <= 0) continue

                // Guard: IP version nibble (high 4 bits of byte 0) must be 4 or 6.
                val version = (ipBuffer[0].toInt() ushr 4) and 0x0F
                if (version != IPV4 && version != IPV6) {
                    Log.w(TAG, "TX skipped: non-IP packet (version=0x${version.toString(16)}, len=$ipLen)")
                    continue
                }

                txPackets++
                txBytes += ipLen
                val proto = ipBuffer[9].toInt() and 0xFF
                if (txPackets <= 10 || txPackets % 50L == 0L) {
                    val enoughForAddr = if (version == IPV6) ipLen >= 40 else ipLen >= 20
                    if (enoughForAddr) {
                        val (srcIp, dstIp) = ipAddrs(ipBuffer)
                        Log.d(TAG, "TX #$txPackets: IP(v$version) $srcIp -> $dstIp proto=$proto len=$ipLen | " +
                                "TUN->Native L3 (packets=$txPackets bytes=$txBytes)")
                    } else {
                        Log.d(TAG, "TX #$txPackets: short L3 packet version=0x${version.toString(16)} len=$ipLen | " +
                                "TUN->Native L3 (packets=$txPackets bytes=$txBytes)")
                    }
                }

                onPacketReceived?.invoke(ipBuffer.copyOf(ipLen))
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Error in TUN read thread: ${e.message}")
                break
            }
        }
    }

    /**
     * RX direction (SoftEther -> TUN). Writes the L3 IP packet (already decapsulated by
     * nativeReceive) directly into the TUN interface.
     */
    fun write(buffer: ByteArray): Int = write(buffer, 0, buffer.size)

    /** Allocation-free version of [write] writing only the [offset, offset+length) slice. */
    fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0 || offset < 0 || length > buffer.size - offset) return 0
        try {
            outputStream.write(buffer, offset, length)
            outputStream.flush()

            rxPackets++
            rxBytes += length
            val version = (buffer[offset].toInt() ushr 4) and 0x0F
            val proto = buffer[offset + 9].toInt() and 0xFF
            if (rxPackets <= 10 || rxPackets % 50L == 0L) {
                val enoughForAddr = if (version == IPV6) length >= 40 else length >= 20
                if (enoughForAddr) {
                    val (srcIp, dstIp) = ipAddrs(buffer, offset + 12)
                    Log.d(TAG, "RX #$rxPackets: IP(v$version) $srcIp -> $dstIp proto=$proto len=$length | " +
                            "Native->TUN L3 (packets=$rxPackets bytes=$rxBytes)")
                } else {
                    Log.d(TAG, "RX #$rxPackets: short L3 packet version=0x${version.toString(16)} len=$length | " +
                            "Native->TUN L3 (packets=$rxPackets bytes=$rxBytes)")
                }
            }
            return length
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "Error in TUN write: ${e.message}")
            return -1
        }
    }

    fun stop() {
        isRunning = false
        try {
            readThread?.interrupt()
            inputStream.close()
            outputStream.close()
            vpnInterface.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TUN: ${e.message}")
        }
    }

    /**
     * Read source/destination addresses from an L3 packet at the given base offset.
     * IPv4: src @ +12, dst @ +16. IPv6: src @ +8, dst @ +24.
     */
    private fun ipAddrs(buffer: ByteArray, base: Int = 0): Pair<String, String> {
        val version = (buffer[base].toInt() ushr 4) and 0x0F
        return if (version == IPV6) {
            ipv6ToString(buffer, base + 8) to ipv6ToString(buffer, base + 24)
        } else {
            ipv4ToString(buffer, base + 12) to ipv4ToString(buffer, base + 16)
        }
    }

    private fun ipv4ToString(b: ByteArray, off: Int): String =
        "${b[off].toInt() and 0xFF}.${b[off + 1].toInt() and 0xFF}." +
            "${b[off + 2].toInt() and 0xFF}.${b[off + 3].toInt() and 0xFF}"

    private fun ipv6ToString(b: ByteArray, off: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 8) {
            if (i > 0) sb.append(':')
            val h = ((b[off + i * 2].toInt() and 0xFF) shl 8) or (b[off + i * 2 + 1].toInt() and 0xFF)
            sb.append(h.toString(16))
        }
        return sb.toString()
    }

    private fun formatMac(mac: ByteArray?): String {
        if (mac == null) return "null"
        return mac.joinToString(":") { "%02X".format(it) }
    }
}
