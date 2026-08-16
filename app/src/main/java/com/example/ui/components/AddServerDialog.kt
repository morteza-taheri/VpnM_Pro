package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.localization.Strings
import com.example.vpn.model.AuthMethod
import com.example.vpn.model.TransportProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerDialog(
    strings: Strings,
    onDismiss: () -> Unit,
    onAdd: (name: String, hostOrIp: String, port: Int, username: String, pass: String, hub: String, auth: String, proto: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hostOrIp by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var username by remember { mutableStateOf("vpn") }
    var password by remember { mutableStateOf("vpn") }
    var hub by remember { mutableStateOf("VPNGATE") }
    
    var authMethod by remember { mutableStateOf(AuthMethod.AUTO) }
    var authExpanded by remember { mutableStateOf(false) }
    
    var protocol by remember { mutableStateOf(TransportProtocol.AUTO) }
    var protocolExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addServer) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.serverNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hostOrIp,
                    onValueChange = { hostOrIp = it },
                    label = { Text(strings.serverHostHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(strings.serverPortHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(strings.serverUsernameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(strings.serverPasswordHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hub,
                    onValueChange = { hub = it },
                    label = { Text(strings.serverHubHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Protocol Dropdown
                ExposedDropdownMenuBox(
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = !protocolExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = protocol.name,
                        onValueChange = { },
                        label = { Text(strings.serverProtocolHint) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false }
                    ) {
                        TransportProtocol.values().forEach { proto ->
                            DropdownMenuItem(
                                text = { Text(proto.name) },
                                onClick = {
                                    protocol = proto
                                    protocolExpanded = false
                                }
                            )
                        }
                    }
                }

                // Auth Method Dropdown
                ExposedDropdownMenuBox(
                    expanded = authExpanded,
                    onExpandedChange = { authExpanded = !authExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = authMethod.name,
                        onValueChange = { },
                        label = { Text(strings.serverAuthMethodHint) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = authExpanded,
                        onDismissRequest = { authExpanded = false }
                    ) {
                        AuthMethod.values().forEach { auth ->
                            DropdownMenuItem(
                                text = { Text(auth.name) },
                                onClick = {
                                    authMethod = auth
                                    authExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, hostOrIp, port.toIntOrNull() ?: 443, username, password, hub, authMethod.name, protocol.name)
                },
                enabled = hostOrIp.isNotBlank()
            ) {
                Text(strings.addAndConnect)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
