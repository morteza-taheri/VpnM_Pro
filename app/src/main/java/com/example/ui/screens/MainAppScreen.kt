package com.example.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.localization.Localization
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VpnViewModel
import com.example.vpn.model.VpnState

enum class Screen {
    HOME, SERVERS, SETTINGS, SPLIT_TUNNEL
}

@Composable
fun MainAppScreen(
    viewModel: VpnViewModel = viewModel()
) {
    val language by viewModel.language.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val vpnState by viewModel.vpnState.collectAsState()
    val selectedServer by viewModel.selectedServer.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    val strings = Localization.get(language)
    val layoutDirection = if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr

    MyApplicationTheme(appTheme = theme) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Scaffold(
                bottomBar = {
                    if (currentScreen != Screen.SPLIT_TUNNEL) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == Screen.HOME,
                                onClick = { currentScreen = Screen.HOME },
                                icon = { Icon(imageVector = Icons.Default.Home, contentDescription = null) },
                                label = { Text(strings.home) }
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.SERVERS,
                                onClick = { currentScreen = Screen.SERVERS },
                                icon = { Icon(imageVector = Icons.Default.Dns, contentDescription = null) },
                                label = { Text(strings.servers) }
                            )
                            NavigationBarItem(
                                selected = currentScreen == Screen.SETTINGS,
                                onClick = { currentScreen = Screen.SETTINGS },
                                icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                                label = { Text(strings.settings) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        viewModel = viewModel,
                        vpnState = vpnState,
                        selectedServer = selectedServer,
                        strings = strings,
                        onNavigateToServers = { currentScreen = Screen.SERVERS },
                        modifier = Modifier.padding(innerPadding)
                    )
                    Screen.SERVERS -> ServersScreen(
                        viewModel = viewModel,
                        vpnState = vpnState,
                        strings = strings,
                        onSelectAndConnect = { server ->
                            currentScreen = Screen.HOME
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onNavigateToSplitTunnel = { currentScreen = Screen.SPLIT_TUNNEL },
                        modifier = Modifier.padding(innerPadding)
                    )
                    Screen.SPLIT_TUNNEL -> SplitTunnelScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onNavigateBack = { currentScreen = Screen.SETTINGS },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
