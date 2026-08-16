package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnServer
import com.example.ui.components.AddServerDialog
import com.example.ui.components.ServerCard
import com.example.ui.components.ServerDetailBottomSheet
import com.example.ui.localization.Strings
import com.example.ui.viewmodel.SortOption
import com.example.ui.viewmodel.VpnViewModel
import com.example.vpn.model.VpnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    viewModel: VpnViewModel,
    vpnState: VpnState,
    strings: Strings,
    onSelectAndConnect: (VpnServer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val servers by viewModel.filteredServers.collectAsState()
    val rawServers by viewModel.rawServers.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedProtocol by viewModel.selectedProtocol.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()

    var showFilterMenu by remember { mutableStateOf(false) }
    var detailServer by remember { mutableStateOf<VpnServer?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectToSelectedServer(context)
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            strings = strings,
            onDismiss = { showAddDialog = false },
            onAdd = { name, hostOrIp, port, username, password, hub, authMethod, protocol ->
                viewModel.addCustomServer(name, hostOrIp, port, username, password, hub, authMethod, protocol) { server ->
                    onSelectAndConnect(server)
                }
                showAddDialog = false
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.servers,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshServers() }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = strings.addServer)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            PaddingValues(horizontal = 16.dp, vertical = 8.dp).let { padding ->
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(strings.searchPlaceholder, fontSize = 14.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter chips & Sort row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    // Protocol Filter Chip
                    FilterChip(
                        selected = selectedProtocol != "ALL",
                        onClick = {
                            viewModel.selectedProtocol.value = when (selectedProtocol) {
                                "ALL" -> "TCP"
                                "TCP" -> "UDP"
                                else -> "ALL"
                            }
                        },
                        label = {
                            Text(
                                when (selectedProtocol) {
                                    "TCP" -> strings.tcpOnly
                                    "UDP" -> strings.udpSupported
                                    else -> strings.allProtocols
                                }
                            )
                        }
                    )
                }

                item {
                    // Sort dropdown button
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showFilterMenu = true },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${strings.sortBy}: ${sortBy.name}")
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("${strings.score} (High to Low)") },
                                onClick = {
                                    viewModel.sortBy.value = SortOption.SCORE
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("${strings.speed} (Fastest)") },
                                onClick = {
                                    viewModel.sortBy.value = SortOption.SPEED
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("${strings.ping} (Lowest)") },
                                onClick = {
                                    viewModel.sortBy.value = SortOption.PING
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.filterCountry) },
                                onClick = {
                                    viewModel.sortBy.value = SortOption.COUNTRY
                                    showFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server Count indicator
            Text(
                text = "${servers.size} ${strings.servers.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // List of servers
            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No VPN servers found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try clearing search filters or pull to refresh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            isSelected = selectedServer?.id == server.id,
                            isConnected = (vpnState is VpnState.Connected) && selectedServer?.id == server.id,
                            onSelect = {
                                viewModel.selectServer(server)
                                detailServer = server
                            },
                            onConnect = {
                                viewModel.selectServer(server)
                                detailServer = server
                            },
                            onShowDetails = {
                                viewModel.selectServer(server)
                                detailServer = server
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    detailServer?.let { server ->
        ServerDetailBottomSheet(
            server = server,
            strings = strings,
            onDismiss = { detailServer = null },
            onConnect = {
                viewModel.selectServer(server)
                viewModel.connectToSelectedServer(context) { intent ->
                    vpnPermissionLauncher.launch(intent)
                }
                onSelectAndConnect(server)
            }
        )
    }
}
