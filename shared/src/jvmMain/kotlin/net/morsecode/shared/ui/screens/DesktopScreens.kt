package net.morsecode.shared.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.morsecode.shared.net.DeviceInfo
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.DeviceListItem
import net.morsecode.shared.ui.components.QrDisplay

// ---------- desktop-only OS helpers (Windows) ----------

private fun isWindows() = System.getProperty("os.name").lowercase().contains("windows")

private fun exec(vararg cmd: String): String = try {
    val p = ProcessBuilder(*cmd).start()
    val out = p.inputStream.bufferedReader().readText()
    p.waitFor()
    out
} catch (e: Exception) {
    ""
}
private fun openHotspotSettings() {
    if (isWindows()) exec("cmd", "/c", "start", "ms-settings:network-mobilehotspot")
}

private fun openNetworkSettings() {
    if (isWindows()) exec("cmd", "/c", "start", "ms-settings:network")
}

private fun firewallRulePresent(): Boolean {
    if (!isWindows()) return true
    val out = exec("netsh", "advfirewall", "firewall", "show", "rule", "name=MorseCode")
    return out.contains("MorseCode", ignoreCase = true)
}

private fun allowViaFirewall(): String {
    if (!isWindows()) return "Not needed on this OS."
    val out = exec(
        "netsh", "advfirewall", "firewall", "add", "rule",
        "name=MorseCode", "dir=in", "action=allow", "protocol=TCP", "localport=53317",
    )
    return if (out.contains("Ok", ignoreCase = true) || out.contains("OK")) {
        "Firewall rule added. Discovery should work now."
    } else {
        "Could not add the rule (admin rights needed).\nRun once as admin:\n" +
            "netsh advfirewall firewall add rule name=\"MorseCode\" dir=in action=allow protocol=TCP localport=53317"
    }
}

private fun diagnoseText(vm: AppViewModel): String = buildString {
    appendLine("Local IP: ${net.morsecode.shared.webconnect.WebConnectServer.lanAddress() ?: "not found (no Wi-Fi/Ethernet?)"}")
    val portFree = try {
        java.net.ServerSocket().use { it.bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), 53317)) }
        "free"
    } catch (_: Exception) {
        "in use (Morse Code is listening - good)"
    }
    appendLine("Port 53317: $portFree")
    appendLine("Windows firewall rule: ${if (firewallRulePresent()) "present" else "MISSING (discovery will be blocked)"}")
    appendLine("Discovered devices right now: ${vm.devices.value.size}")
}

@Composable
actual fun PlatformHomeScreen(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    val discovered by vm.devices.collectAsState()
    val known by vm.knownDevices.collectAsState()
    val devices = remember(discovered, known) { (known + discovered).distinctBy { it.deviceId } }
    var showQr by remember { mutableStateOf(vm.newPairingQr()) }
    var showDiag by remember { mutableStateOf(false) }
    var showFirewallBanner by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (showFirewallBanner && isWindows() && !firewallRulePresent()) {
            Surface(
                color = Color(0xFF5C1A1A),
                contentColor = Color(0xFFFFD9D9),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Your network is set to 'Public', which may block device discovery. " +
                            "Set it to 'Private' in Windows Settings, or allow Morse Code through the firewall.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { scope.launch { openNetworkSettings() } }) { Text("Network settings") }
                    TextButton(onClick = {
                        scope.launch {
                            vm.toast(allowViaFirewall())
                        }
                    }) { Text("Allow via Firewall") }
                    TextButton(onClick = { showFirewallBanner = false }) { Text("Dismiss") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (devices.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No devices found after 8 seconds.", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Try:\n1. Connect both devices to the same WiFi network, OR\n" +
                            "2. Turn on a mobile hotspot on one device and connect the other\n" +
                            "On Windows, if your network is set to 'Public', discovery is blocked by firewall.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        OutlinedButton(onClick = { openHotspotSettings() }) { Text("Open Hotspot Settings") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { showDiag = true }) { Text("Diagnose") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxWidth()) {
            // My QR Code card
            Card(Modifier.weight(1.2f)) {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("My QR Code", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scan to connect from Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(Modifier.height(12.dp))
                    showQr?.let { qr -> QrDisplay(qr, 220.dp) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan with Morse Code on Android (Home > Scan), or use Manual connect with this PC's IP:\n" +
                            (net.morsecode.shared.webconnect.WebConnectServer.lanAddress() ?: "(no network)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showQr = vm.newPairingQr() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text("  Refresh QR")
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            // Quick actions card
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onNavigate(Route.Send) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) { Text("Send Files") }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onNavigate(Route.Send) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Drop files to send", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Save folder: ${ServiceLocator.deps.fileAdapter.receivedDir()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Protocol v1 - Max frame 64 MiB - E2E encrypted - No internet needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (devices.isNotEmpty()) {
            Text("Connected / discovered devices", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f)) {
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

    if (showDiag) {
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text("Connection diagnostics") },
            text = { Text(diagnoseText(vm)) },
            confirmButton = { TextButton(onClick = { showDiag = false }) { Text("Close") } },
        )
    }
}

@Composable
actual fun PlatformSendScreen(vm: AppViewModel, onDone: () -> Unit) {
    val discovered by vm.devices.collectAsState()
    val known by vm.knownDevices.collectAsState()
    val devices = remember(discovered, known) { (known + discovered).distinctBy { it.deviceId } }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<net.morsecode.shared.platform.PickedFile>() }
    val pending by vm.pendingSendFiles.collectAsState()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var selectedTargets by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }

    // Files dropped onto the window or queued from the Files tab arrive here.
    androidx.compose.runtime.LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            picked.addAll(pending)
            vm.pendingSendFiles.value = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Send Files", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pick files, choose device, send over LAN - fully offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // 1. Select files
            Column(Modifier.weight(1.2f)) {
                Text("1. Select Files", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            if (!picking) {
                                picking = true
                                scope.launch {
                                    val files = ServiceLocator.deps.pickFiles("Select files to send")
                                    picked.addAll(files)
                                    picking = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Drag & drop files here")
                        Text(
                            "or click to browse (all drives)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = {
                        if (!picking) {
                            picking = true
                            scope.launch {
                                val files = ServiceLocator.deps.pickFiles("Select files to send")
                                picked.addAll(files)
                                picking = false
                            }
                        }
                    }) { Text("Browse Files") }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (picked.isEmpty()) "No files selected" else "${picked.size} file(s) selected",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            // 2. Select device
            Column(Modifier.weight(1f)) {
                Text("2. Select Device", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (devices.isEmpty()) {
                    Text(
                        "No nearby devices found. Try:\n" +
                            "1. Connect both devices to the same WiFi network, OR\n" +
                            "2. Turn on a mobile hotspot on one device and connect the other\n\n" +
                            "Hotspots block discovery - use the QR / Manual (IP) connect on Home, " +
                            "the device then appears here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        items(devices, key = { it.deviceId }) { device ->
                            val selected = device.deviceId in selectedTargets.map { it.deviceId }
                            DeviceListItem(device = device, onClick = {
                                selectedTargets =
                                    if (selected) selectedTargets.filterNot { it.deviceId == device.deviceId }
                                    else selectedTargets + device
                            }, trailing = {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onDone) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    vm.sendFiles(selectedTargets.toList(), picked.toList())
                    onDone()
                },
                enabled = picked.isNotEmpty() && selectedTargets.isNotEmpty(),
            ) { Text("Send") }
        }
    }
}
