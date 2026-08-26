package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.QrDisplay
import net.morsecode.shared.ui.formatBytes

/** Web Connect (Section H): browser companion with QR + PIN pairing. */
@Composable
fun WebConnectScreen(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    val status by vm.webServer.status.collectAsState()
    val shared by vm.webServer.sharedFiles.collectAsState()
    var candidates by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    LaunchedEffect(status.running) {
        if (status.running) {
            candidates = runCatching {
                ServiceLocator.deps.mediaLibrary.getAllFiles().take(50).map { it.uri to it.sizeBytes }
            }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Web Connect", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (status.running) "Running at http://${status.lanIp ?: "0.0.0.0"}:${status.port}"
                            else status.error ?: "Off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = status.running,
                        onCheckedChange = { on ->
                            if (on) vm.webServer.start(8080) else vm.webServer.stop()
                        },
                    )
                }

                if (status.running) {
                    Spacer(Modifier.height(12.dp))
                    val url = "http://${status.lanIp}:${status.port}/"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QrDisplay(url + "?token=" + vm.webServer.pairing.qrTokenValue(), 160.dp)
                        Spacer(Modifier.padding(8.dp))
                        Column {
                            Text("Pairing PIN", style = MaterialTheme.typography.labelMedium)
                            Text(status.pin, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Scan the QR, or open the URL and enter the PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Shared this session", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nothing is shared until you choose it. Pick files from your library to expose them to the paired browser.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (shared.isEmpty() && candidates.isEmpty()) {
            EmptyState("Enable Web Connect, then pick files below to share them for this session.")
        }

        LazyColumn(Modifier.weight(1f)) {
            items(shared, key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = true, onCheckedChange = { vm.webServer.removeSharedFile(f.id) })
                    Column(Modifier.weight(1f)) {
                        Text(f.displayName, maxLines = 1)
                        Text(formatBytes(f.sizeBytes), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { vm.webServer.removeSharedFile(f.id) }) { Text("Remove") }
                }
            }
            items(candidates.filter { c -> shared.none { it.uri == c.first } }, key = { it.first }) { (uri, size) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = false, onCheckedChange = { on ->
                        if (on) {
                            val name = uri.substringAfterLast('/')
                            vm.webServer.addSharedFile(
                                net.morsecode.shared.webconnect.SharedSessionFile(
                                    id = net.morsecode.shared.net.Crypto.randomId(),
                                    displayName = name, sizeBytes = size,
                                    mime = "application/octet-stream", uri = uri,
                                ),
                            )
                        }
                    })
                    Text(uri.substringAfterLast('/'), maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
        }

        if (status.running) {
            Button(onClick = { vm.webServer.stop() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop Web Connect (ends all sessions)")
            }
        }
    }
}
