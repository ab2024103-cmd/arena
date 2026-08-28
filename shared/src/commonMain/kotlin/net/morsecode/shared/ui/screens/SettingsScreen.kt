package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: AppViewModel, onNavigate: ((net.morsecode.shared.ui.Route) -> Unit)? = null) {
    val deviceName by vm.deviceName.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val autoAccept by vm.autoAccept.collectAsState()
    val autoAll by vm.autoAcceptAll.collectAsState()
    val speedLimit by vm.speedLimitKbps.collectAsState()
    val trusted by vm.trustedRepo.devices.collectAsState()
    var nameInput by remember { mutableStateOf(deviceName) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onNavigate != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Web Connect", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pair a PC browser to browse, download and send files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onNavigate(net.morsecode.shared.ui.Route.Web) }) {
                        Text("Open Web Connect")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Device", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameInput, onValueChange = { nameInput = it },
                    label = { Text("Device name") }, singleLine = true,
                )
                TextButton(onClick = { vm.setDeviceName(nameInput.trim().ifEmpty { "Morse Device" }) }) {
                    Text("Save name")
                }
                Text(
                    "ID: ${vm.profile.deviceId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                listOf(
                    Pair(ThemeMode.SYSTEM, "Follow system"),
                    Pair(ThemeMode.LIGHT, "Light"),
                    Pair(ThemeMode.DARK, "Dark"),
                ).forEach { (mode, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == mode, onClick = { vm.setThemeMode(mode) })
                        Text(label)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Auto-accept incoming transfers", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoAccept, onCheckedChange = { vm.setAutoAccept(it) })
                    Spacer(Modifier.padding(4.dp))
                    Text(if (autoAccept) "On" else "Off (default)")
                }
                if (autoAccept) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !autoAll, onClick = { vm.setAutoAcceptAll(false) })
                        Text("Trusted devices only (recommended)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = autoAll, onClick = { vm.setAutoAcceptAll(true) })
                        Text("All devices (extra caution)")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Limit transfer speed", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (speedLimit <= 0) "Unlimited" else "$speedLimit KB/s (global)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = speedLimit.toFloat(),
                    onValueChange = { vm.setSpeedLimit(it.toInt()) },
                    valueRange = 0f..100_000f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.setSpeedLimit(0) }) { Text("Unlimited") }
                    TextButton(onClick = { vm.setSpeedLimit(1024) }) { Text("1 MB/s") }
                    TextButton(onClick = { vm.setSpeedLimit(10240) }) { Text("10 MB/s") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Trusted devices", style = MaterialTheme.typography.titleMedium)
                if (trusted.isEmpty()) {
                    Text(
                        "None yet. After a successful transfer you can choose to trust a device, which skips pairing-token checks in the future.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trusted.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name)
                            Text(d.deviceId.take(16) + "…", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { vm.forgetDevice(d.deviceId) }) { Text("Forget") }
                    }
                    HorizontalDivider()
                }
            }
        }

        Text(
            "Morse Code v1.0.0 · fully offline LAN transfer · no ads, no tracking, no cloud",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
