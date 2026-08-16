package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.VpnServerEntity
import com.example.data.local.VpnSourceConfig
import com.example.data.model.VpnServer
import com.example.data.remote.VpnGateCsvParser
import com.example.data.remote.VpnGateHtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class FetchResult {
    data class Success(val count: Int) : FetchResult()
    data class Error(val message: String) : FetchResult()
}

class VpnRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.vpnServerDao()
    private val prefs = PreferencesManager(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val allServers: Flow<List<VpnServer>> = dao.getAllServers().map { entities ->
        entities.map { it.toDomain() }
    }

    val serverCount: Flow<Int> = dao.getServerCount()

    suspend fun refreshServers(): FetchResult = withContext(Dispatchers.IO) {
        val enabledSources = prefs.defaultSources.filter { prefs.isSourceEnabled(it.id) }
        if (enabledSources.isEmpty()) {
            return@withContext FetchResult.Error("No server sources enabled in Settings.")
        }

        val allFetchedServers = mutableListOf<VpnServer>()
        val deferredList = enabledSources.map { sourceConfig ->
            async {
                fetchFromSource(sourceConfig)
            }
        }

        val results = deferredList.awaitAll()
        for (serverList in results) {
            allFetchedServers.addAll(serverList)
        }

        if (allFetchedServers.isNotEmpty()) {
            // Process, deduplicate, filter unsupported protocols, and merge servers from multiple sources
            val processedServers = processAndFilterServers(allFetchedServers)

            if (processedServers.isNotEmpty()) {
                val entities = processedServers.map { VpnServerEntity.fromDomain(it) }

                // Keep internal database intact until new list is processed!
                dao.deleteAll()
                dao.insertAll(entities)

                val now = System.currentTimeMillis()
                prefs.setLastUpdateTime(now)

                FetchResult.Success(entities.size)
            } else {
                FetchResult.Error("No servers with supported protocols found in sources.")
            }
        } else {
            // Check if we already have local cached servers
            val existingCount = dao.getAllServersSnapshot().size
            if (existingCount > 0) {
                FetchResult.Error("Could not fetch fresh servers from sources. Displaying $existingCount cached servers.")
            } else {
                FetchResult.Error("Failed to fetch servers. Please check network connection.")
            }
        }
    }

    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        prefs.setLastUpdateTime(0L)
    }

    suspend fun insertServer(server: VpnServer) = withContext(Dispatchers.IO) {
        dao.insertAll(listOf(VpnServerEntity.fromDomain(server)))
    }

    private fun fetchFromSource(sourceConfig: VpnSourceConfig): List<VpnServer> {
        return try {
            val request = Request.Builder()
                .url(sourceConfig.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyText = response.body?.string() ?: return emptyList()

                if (sourceConfig.isCsv) {
                    VpnGateCsvParser.parseCsv(bodyText, sourceConfig.id)
                } else {
                    VpnGateHtmlParser.parseHtml(bodyText, sourceConfig.id)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun processAndFilterServers(rawServers: List<VpnServer>): List<VpnServer> {
        // Step 1: Filter out servers with invalid IPs or unsupported protocols
        val validServers = rawServers.filter { isServerValidAndSupported(it) }

        // Step 2: Group by IP address to remove duplicates across sources and merge attributes
        val groupedByIp = validServers.groupBy { it.ip.trim() }

        val deduplicatedServers = groupedByIp.map { (_, serversForIp) ->
            if (serversForIp.size == 1) {
                serversForIp.first()
            } else {
                mergeServers(serversForIp)
            }
        }

        // Step 3: Sort by score and speed descending
        return deduplicatedServers.sortedWith(
            compareByDescending<VpnServer> { it.score }
                .thenByDescending { it.speedBps }
        )
    }

    private fun isServerValidAndSupported(server: VpnServer): Boolean {
        val trimmedIp = server.ip.trim()
        if (trimmedIp.isBlank() || !isValidIpv4(trimmedIp)) {
            return false
        }

        // Must support at least one valid protocol
        val hasSupportedProtocol = server.softEtherTcpPort != null ||
                server.softEtherUdpSupported ||
                !server.openVpnConfigData.isNullOrBlank() ||
                server.openVpnTcpPort != null ||
                server.openVpnUdpPort != null ||
                server.l2tpSupported ||
                !server.sstpHostname.isNullOrBlank()

        if (!hasSupportedProtocol) {
            return false
        }

        if (server.effectivePort !in 1..65535) {
            return false
        }

        return true
    }

    private fun isValidIpv4(ip: String): Boolean {
        if (ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "255.255.255.255") return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }

    private fun mergeServers(servers: List<VpnServer>): VpnServer {
        val primary = servers.maxByOrNull { it.score } ?: servers.first()

        val hostName = servers.firstOrNull { it.hostName.contains("opengw.net") }?.hostName
            ?: primary.hostName
        val bestScore = servers.maxOf { it.score }
        val bestSpeed = servers.maxOf { it.speedBps }
        val validPings = servers.map { it.pingMs }.filter { it > 0 }
        val bestPing = if (validPings.isNotEmpty()) validPings.minOrNull()!! else primary.pingMs

        val countryLong = servers.firstOrNull { it.countryLong.isNotBlank() && it.countryLong != "Unknown" }?.countryLong ?: primary.countryLong
        val countryShort = servers.firstOrNull { it.countryShort.isNotBlank() && it.countryShort != "UN" }?.countryShort ?: primary.countryShort

        val softEtherTcpPort = servers.mapNotNull { it.softEtherTcpPort }.firstOrNull()
        val softEtherUdpSupported = servers.any { it.softEtherUdpSupported }
        val l2tpSupported = servers.any { it.l2tpSupported }
        val openVpnTcpPort = servers.mapNotNull { it.openVpnTcpPort }.firstOrNull()
        val openVpnUdpPort = servers.mapNotNull { it.openVpnUdpPort }.firstOrNull()
        val openVpnConfigData = servers.mapNotNull { it.openVpnConfigData }.firstOrNull { it.isNotBlank() }
        val sstpHostname = servers.mapNotNull { it.sstpHostname }.firstOrNull { it.isNotBlank() }
        val sstpPort = servers.mapNotNull { it.sstpPort }.firstOrNull()

        val combinedSources = servers.map { it.source }.distinct().joinToString(", ")

        return primary.copy(
            hostName = hostName,
            score = maxOf(primary.score, bestScore),
            speedBps = maxOf(primary.speedBps, bestSpeed),
            pingMs = bestPing,
            countryLong = countryLong,
            countryShort = countryShort,
            softEtherTcpPort = softEtherTcpPort,
            softEtherUdpSupported = softEtherUdpSupported,
            l2tpSupported = l2tpSupported,
            openVpnTcpPort = openVpnTcpPort,
            openVpnUdpPort = openVpnUdpPort,
            openVpnConfigData = openVpnConfigData,
            sstpHostname = sstpHostname,
            sstpPort = sstpPort,
            source = combinedSources
        )
    }
}
