package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.formatBytes

@Composable
fun ReceiveScreen(vm: AppViewModel) {
    val history by vm.historyRepo.entries.collectAsState()
    val received = history.filter { it.direction == "received" }
    val autoAccept by vm.autoAccept.collectAsState()
    val autoAll by vm.autoAcceptAll.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Receiving", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Listening on port 53317. Files save to:\n${vm.fileAdapter.receivedDir()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-accept transfers", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (autoAll) "From all devices" else "Trusted devices only",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoAccept, onCheckedChange = { vm.setAutoAccept(it) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Recently received", style = MaterialTheme.typography.titleMedium)

        if (received.isEmpty()) {
            EmptyState("Nothing received yet.")
        } else {
            LazyColumn {
                items(received, key = { it.id }) { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(h.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                "from ${h.peerName} · ${formatBytes(h.sizeBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            h.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (h.status == "completed") MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
