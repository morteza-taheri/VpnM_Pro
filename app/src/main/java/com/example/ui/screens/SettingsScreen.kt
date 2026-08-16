package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.example.ui.localization.Strings
import com.example.ui.viewmodel.VpnViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VpnViewModel,
    strings: Strings,
    onNavigateToSplitTunnel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language by viewModel.language.collectAsState()
    val theme by viewModel.theme.collectAsState()

    var protocolMode by remember { mutableStateOf(viewModel.prefs.getProtocol()) }
    var authMethod by remember { mutableStateOf(viewModel.prefs.getAuthMethod()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.settings,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Split Tunneling Shortcut Card
            Card(
                onClick = onNavigateToSplitTunnel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.splitTunnelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.splitTunnelSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Custom DNS Settings Section
            var dnsMode by remember { mutableStateOf(viewModel.prefs.getDnsMode()) }
            var selectedPresetId by remember { mutableStateOf(viewModel.prefs.getDnsPresetId()) }
            var customPrimary by remember { mutableStateOf(viewModel.prefs.getCustomDnsPrimary()) }
            var customSecondary by remember { mutableStateOf(viewModel.prefs.getCustomDnsSecondary()) }

            SectionCard(
                title = strings.dnsTitle,
                subtitle = strings.dnsSubtitle,
                icon = Icons.Default.Dns
            ) {
                // Mode selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dnsMode == "PRESET",
                        onClick = {
                            dnsMode = "PRESET"
                            viewModel.prefs.setDnsMode("PRESET")
                        },
                        label = { Text(strings.dnsModePreset, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = dnsMode == "CUSTOM",
                        onClick = {
                            dnsMode = "CUSTOM"
                            viewModel.prefs.setDnsMode("CUSTOM")
                        },
                        label = { Text(strings.dnsModeCustom, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (dnsMode == "PRESET") {
                    viewModel.prefs.defaultDnsPresets.forEach { preset ->
                        val isSelected = selectedPresetId == preset.id
                        Card(
                            onClick = {
                                selectedPresetId = preset.id
                                viewModel.prefs.setDnsPresetId(preset.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPresetId = preset.id
                                        viewModel.prefs.setDnsPresetId(preset.id)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (language == "fa") preset.nameFa else preset.nameEn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${preset.primary} • ${preset.secondary}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (language == "fa") preset.descriptionFa else preset.descriptionEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = customPrimary,
                            onValueChange = {
                                customPrimary = it
                                viewModel.prefs.setCustomDnsPrimary(it)
                            },
                            label = { Text(strings.dnsPrimary) },
                            placeholder = { Text(strings.dnsCustomPlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = customSecondary,
                            onValueChange = {
                                customSecondary = it
                                viewModel.prefs.setCustomDnsSecondary(it)
                            },
                            label = { Text(strings.dnsSecondary) },
                            placeholder = { Text("185.51.200.2") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Protocol Settings Section
            SectionCard(
                title = strings.protocolTitle,
                icon = Icons.Default.Tune
            ) {
                listOf(
                    "AUTO" to "Auto Select (TCP / UDP V2 / UDP V1)",
                    "TCP" to "SoftEther Standard TCP (SSL 443/995)",
                    "UDP_V2" to "SoftEther UDP Acceleration (UDP V2)",
                    "UDP_V1" to "SoftEther UDP Legacy (UDP V1)"
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = protocolMode == key,
                            onClick = {
                                protocolMode = key
                                viewModel.prefs.setProtocol(key)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Connection Target Address Mode (Hostname vs IP)
            var connectionHostMode by remember { mutableStateOf(viewModel.prefs.getConnectionHostMode()) }
            SectionCard(
                title = strings.hostModeTitle,
                subtitle = strings.hostModeSubtitle,
                icon = Icons.Default.Dns
            ) {
                listOf(
                    "HOSTNAME" to strings.hostModeHostname,
                    "IP" to strings.hostModeIp
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = connectionHostMode == key,
                            onClick = {
                                connectionHostMode = key
                                viewModel.prefs.setConnectionHostMode(key)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Authentication Method Section
            SectionCard(
                title = strings.authMethodTitle,
                icon = Icons.Default.Security
            ) {
                listOf(
                    "AUTO" to "Auto (SoftEther Standard)",
                    "PASSWORD" to "Standard Hash Authentication (vpn/vpn)",
                    "PLAIN_PASSWORD" to "Plain Password Mode",
                    "ANONYMOUS" to "Anonymous Login"
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = authMethod == key,
                            onClick = {
                                authMethod = key
                                viewModel.prefs.setAuthMethod(key)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Mirror Sources Section
            SectionCard(
                title = strings.sourcesTitle,
                subtitle = strings.sourcesSubtitle,
                icon = Icons.Default.Public
            ) {
                viewModel.prefs.defaultSources.forEach { source ->
                    var isEnabled by remember { mutableStateOf(viewModel.prefs.isSourceEnabled(source.id)) }
                    val nameStr = when (source.nameKey) {
                        "source_official_api" -> strings.sourceOfficialApi
                        "source_official_web" -> strings.sourceOfficialWeb
                        "source_mirror_hr_1" -> strings.sourceMirrorCroatia1
                        "source_mirror_hr_2" -> strings.sourceMirrorCroatia2
                        "source_mirror_hr_3" -> strings.sourceMirrorCroatia3
                        "source_mirror_am_1" -> strings.sourceMirrorArmenia
                        "source_mirror_hr_4" -> strings.sourceMirrorCroatia4
                        "source_mirror_hr_5" -> strings.sourceMirrorCroatia5
                        else -> source.id
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nameStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                isEnabled = checked
                                viewModel.setSourceEnabled(source.id, checked)
                            }
                        )
                    }
                }
            }

            // Language & Theme Section
            SectionCard(
                title = strings.languageTitle,
                icon = Icons.Default.Language
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == "fa") strings.persian else strings.english,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = language == "fa",
                        onCheckedChange = { viewModel.toggleLanguage() }
                    )
                }
            }

            SectionCard(
                title = strings.themeTitle,
                icon = Icons.Default.Palette
            ) {
                listOf(
                    "dark" to strings.darkTheme,
                    "light" to strings.lightTheme,
                    "system" to strings.systemTheme
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == key,
                            onClick = { viewModel.setTheme(key) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Clear Database Section
            val context = LocalContext.current
            SectionCard(
                title = strings.clearDatabase,
                subtitle = strings.clearDatabaseSubtitle,
                icon = Icons.Default.DeleteForever
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.clearLocalDatabase()
                        Toast.makeText(context, strings.databaseCleared, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = strings.clearDatabase, fontWeight = FontWeight.Bold)
                }
            }

            // About Section
            SectionCard(
                title = strings.aboutTitle,
                icon = Icons.Default.Info
            ) {
                Text(
                    text = strings.aboutDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Version 1.0.0 • SoftEther VPN Client Core",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}
