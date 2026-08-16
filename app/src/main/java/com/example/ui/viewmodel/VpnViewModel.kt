package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.VpnServer
import com.example.data.repository.FetchResult
import com.example.data.repository.VpnRepository
import com.example.ui.localization.Localization
import com.example.ui.localization.Strings
import com.example.vpn.model.AuthMethod
import com.example.vpn.model.ConnectionConfig
import com.example.vpn.model.TransportProtocol
import com.example.vpn.model.VpnState
import com.softether.SoftEtherVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption {
    PING, SPEED, SCORE, COUNTRY, USERS
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSelected: Boolean = false
)

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VpnRepository(application)
    val prefs = PreferencesManager(application)

    val vpnState: StateFlow<VpnState> = SoftEtherVpnService.vpnState
    val vpnLogs: StateFlow<List<String>> = SoftEtherVpnService.vpnLogs

    fun clearLogs() {
        SoftEtherVpnService.clearLogs()
    }

    fun clearLocalDatabase() {
        viewModelScope.launch {
            repository.clearDatabase()
            _selectedServer.value = null
        }
    }

    fun addCustomServer(
        name: String, 
        hostOrIp: String, 
        port: Int, 
        username: String, 
        password: String, 
        hub: String, 
        authMethod: String, 
        protocol: String, 
        onConnected: (VpnServer) -> Unit
    ) {
        viewModelScope.launch {
            val jsonConfig = org.json.JSONObject()
            jsonConfig.put("username", username)
            jsonConfig.put("password", password)
            jsonConfig.put("hubName", hub)
            jsonConfig.put("authMethod", authMethod)
            jsonConfig.put("protocol", protocol)

            val server = VpnServer(
                id = "custom_${System.currentTimeMillis()}",
                hostName = hostOrIp,
                ip = hostOrIp,
                score = 999999,
                operator = name.ifBlank { "Custom Server" },
                countryLong = "Custom",
                countryShort = "CS",
                softEtherTcpPort = port,
                source = "Custom",
                comment = jsonConfig.toString()
            )
            repository.insertServer(server)
            _selectedServer.value = server
            onConnected(server)
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshResult = MutableStateFlow<FetchResult?>(null)
    val refreshResult: StateFlow<FetchResult?> = _refreshResult

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer

    // Search and Filter controls
    val searchQuery = MutableStateFlow("")
    val selectedCountry = MutableStateFlow("ALL")
    val selectedProtocol = MutableStateFlow("ALL")
    val sortBy = MutableStateFlow(SortOption.SCORE)
    val minSpeedMbps = MutableStateFlow(0f)
    val maxPingMs = MutableStateFlow(1000f)

    // Language & Theme
    val language = prefs.languageFlow
    val theme = prefs.themeFlow

    val strings: Strings
        get() = Localization.get(language.value)

    val rawServers: StateFlow<List<VpnServer>> = repository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverCount: StateFlow<Int> = repository.serverCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastUpdateTime = MutableStateFlow(prefs.getLastUpdateTime())

    // Filtered & Sorted Server list
    val filteredServers: StateFlow<List<VpnServer>> = combine(
        rawServers,
        searchQuery,
        selectedCountry,
        selectedProtocol,
        sortBy,
        minSpeedMbps,
        maxPingMs
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val servers = args[0] as List<VpnServer>
        val query = args[1] as String
        val country = args[2] as String
        val protocol = args[3] as String
        val sort = args[4] as SortOption
        val minSpeed = args[5] as Float
        val maxPing = args[6] as Float

        var list = servers

        // Query filter
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.hostName.lowercase().contains(q) ||
                it.ip.contains(q) ||
                it.countryLong.lowercase().contains(q) ||
                it.countryShort.lowercase().contains(q)
            }
        }

        // Country filter
        if (country != "ALL") {
            list = list.filter { it.countryShort.equals(country, ignoreCase = true) || it.countryLong.equals(country, ignoreCase = true) }
        }

        // Protocol filter
        if (protocol == "TCP") {
            list = list.filter { it.softEtherTcpPort != null }
        } else if (protocol == "UDP") {
            list = list.filter { it.softEtherUdpSupported }
        }

        // Min speed filter
        if (minSpeed > 0f) {
            list = list.filter { it.speedMbps >= minSpeed }
        }

        // Max ping filter
        if (maxPing < 1000f) {
            list = list.filter { it.pingMs in 1..maxPing.toInt() }
        }

        // Sort
        when (sort) {
            SortOption.PING -> list.sortedWith(compareBy<VpnServer> { if (it.pingMs <= 0) 9999 else it.pingMs })
            SortOption.SPEED -> list.sortedByDescending { it.speedBps }
            SortOption.SCORE -> list.sortedByDescending { it.score }
            SortOption.COUNTRY -> list.sortedBy { it.countryLong }
            SortOption.USERS -> list.sortedByDescending { it.numSessions }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Split Tunneling State
    val splitTunnelEnabled = MutableStateFlow(prefs.isSplitTunnelEnabled())
    val splitTunnelMode = MutableStateFlow(prefs.getSplitTunnelMode())
    val splitTunnelPackages = MutableStateFlow(prefs.getSplitTunnelPackages())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    init {
        // Auto select first server when list arrives if none selected
        viewModelScope.launch {
            rawServers.collect { list ->
                if (_selectedServer.value == null && list.isNotEmpty()) {
                    _selectedServer.value = list.first()
                }
            }
        }

        // If DB is empty, trigger initial refresh automatically
        viewModelScope.launch {
            if (repository.allServers.stateIn(viewModelScope).value.isEmpty()) {
                refreshServers()
            }
        }

        loadInstalledApps()
    }

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
    }

    fun refreshServers() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refreshServers()
            _refreshResult.value = result
            _isRefreshing.value = false
            lastUpdateTime.value = prefs.getLastUpdateTime()
        }
    }

    fun connectToSelectedServer(context: Context, onPermissionRequired: ((Intent) -> Unit)? = null) {
        val server = _selectedServer.value ?: return

        val intent = VpnService.prepare(context)
        if (intent != null) {
            onPermissionRequired?.invoke(intent)
            return
        }

        var authMethod = try { AuthMethod.valueOf(prefs.getAuthMethod()) } catch (_: Exception) { AuthMethod.AUTO }
        var transportProtocol = try { TransportProtocol.valueOf(prefs.getProtocol()) } catch (_: Exception) { TransportProtocol.AUTO }
        
        var username = "vpn"
        var password = "vpn"
        var hubName = "VPNGATE" // Note: Currently we don't have hubName in ConnectionConfig, we might need to check this.

        if (server.source == "Custom" && server.comment.isNotBlank()) {
            try {
                val json = org.json.JSONObject(server.comment)
                username = json.optString("username", "vpn")
                password = json.optString("password", "vpn")
                hubName = json.optString("hubName", "VPNGATE")
                authMethod = try { AuthMethod.valueOf(json.optString("authMethod", "AUTO")) } catch (_: Exception) { authMethod }
                transportProtocol = try { TransportProtocol.valueOf(json.optString("protocol", "AUTO")) } catch (_: Exception) { transportProtocol }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        val hostMode = prefs.getConnectionHostMode()
        val effectiveHost = if (hostMode == "IP" && server.ip.isNotBlank()) server.ip else server.hostName

        val config = ConnectionConfig(
            host = effectiveHost,
            ip = server.ip,
            port = server.effectivePort,
            candidatePorts = server.candidatePorts,
            username = username,
            password = password,
            hubName = hubName,
            authMethod = authMethod,
            transportProtocol = transportProtocol
        )

        SoftEtherVpnService.startVpn(context, config)
    }

    fun disconnect(context: Context) {
        SoftEtherVpnService.stopVpn(context)
    }

    fun toggleLanguage() {
        val newLang = if (language.value == "fa") "en" else "fa"
        prefs.setLanguage(newLang)
    }

    fun setTheme(newTheme: String) {
        prefs.setTheme(newTheme)
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        prefs.setSourceEnabled(sourceId, enabled)
    }

    fun setSplitTunnelEnabled(enabled: Boolean) {
        splitTunnelEnabled.value = enabled
        prefs.setSplitTunnelEnabled(enabled)
    }

    fun setSplitTunnelMode(mode: String) {
        splitTunnelMode.value = mode
        prefs.setSplitTunnelMode(mode)
    }

    fun toggleAppPackage(packageName: String) {
        val currentSet = splitTunnelPackages.value.toMutableSet()
        if (currentSet.contains(packageName)) {
            currentSet.remove(packageName)
        } else {
            currentSet.add(packageName)
        }
        splitTunnelPackages.value = currentSet
        prefs.setSplitTunnelPackages(currentSet)

        // Update list UI
        _installedApps.value = _installedApps.value.map {
            if (it.packageName == packageName) it.copy(isSelected = currentSet.contains(packageName)) else it
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val selectedSet = prefs.getSplitTunnelPackages()

            val apps = packages
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                        isSelected = selectedSet.contains(appInfo.packageName)
                    )
                }
                .sortedBy { it.appName }

            withContext(Dispatchers.Main) {
                _installedApps.value = apps
            }
        }
    }
}
