package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.DeviceListItem
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.IconBubble
import net.morsecode.shared.ui.components.QrDisplay

@Composable
fun HomeScreen(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    val devices by vm.devices.collectAsState()
    var showQr by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshDevices() }

    Column(Modifier.fillMaxSize()) {
        // Header card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(vm.profile.name, style = MaterialTheme.typography.headlineMedium)
                val ip = net.morsecode.shared.webconnect.WebConnectServer.lanAddress()
                Text(
                    "Discoverable on this Wi-Fi network · port ${53317}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    IconBubble(Icons.Filled.Send, "Send") { onNavigate(Route.Send) }
                    IconBubble(Icons.Filled.Share, "Receive") { onNavigate(Route.Receive) }
                    IconBubble(Icons.Filled.QrCode, "My QR") { showQr = vm.newPairingQr() }
                    IconBubble(Icons.Filled.QrCodeScanner, "Scan") { scanning = true }
                    IconBubble(Icons.Filled.GroupAdd, "Room") { onNavigate(Route.Room) }
                    IconBubble(Icons.Filled.TextFields, "Text") { onNavigate(Route.TextShare) }
                }
            }
        }

        Text(
            "Nearby devices",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))

        if (devices.isEmpty()) {
            EmptyState("Searching for nearby Morse Code devices…\nMake sure both devices are on the same Wi-Fi.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(devices, key = { it.deviceId }) { device ->
                    DeviceListItem(device = device, onClick = {
                        vm.connect(device) { ok, err ->
                            vm.toast(if (ok) "Connected to ${device.name}" else "Failed: $err")
                        }
                    })
                }
            }
        }
    }

    showQr?.let { qr ->
        AlertDialog(
            onDismissRequest = { showQr = null; vm.clearPairingQr() },
            title = { Text("Pairing QR") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    QrDisplay(qr, 240.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Let the other device scan this to pair instantly.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = null; vm.clearPairingQr() }) { Text("Done") } },
        )
    }

    if (scanning) {
        QrScanDialog(vm) { scanning = false }
    }

    if (showManual) {
        ManualConnectDialog(vm) { showManual = false }
    }
}

@Composable
expect fun QrScanDialog(vm: AppViewModel, onDismiss: () -> Unit)

@Composable
fun ManualConnectDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var ipPort by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect manually") },
        text = {
            Column {
                Text("Enter the peer address as ip:port", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ipPort, onValueChange = { ipPort = it }, label = { Text("e.g. 192.168.1.20:53317") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parts = ipPort.trim().split(":")
                if (parts.size == 2) {
                    val device = net.morsecode.shared.net.DeviceInfo(
                        deviceId = "manual-${parts[0]}", name = parts[0],
                        deviceType = "manual", ip = parts[0], port = parts[1].toIntOrNull() ?: 53317,
                    )
                    vm.connect(device) { ok, err -> vm.toast(if (ok) "Connected" else "Failed: $err") }
                }
                onDismiss()
            }) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
