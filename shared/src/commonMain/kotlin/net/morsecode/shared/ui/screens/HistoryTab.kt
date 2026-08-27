package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.media.DateGrouping
import net.morsecode.shared.storage.HistoryEntry
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.formatBytes

@Composable
fun HistoryTab(vm: AppViewModel) {
    val all by vm.historyRepo.entries.collectAsState()
    var direction by remember { mutableStateOf("received") }
    val entries = all.filter { it.direction == direction }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = direction == "sent",
                onClick = { direction = "sent" },
                label = { Text("Sent") },
                leadingIcon = { Icon(Icons.Filled.CallMade, contentDescription = null) },
            )
            FilterChip(
                selected = direction == "received",
                onClick = { direction = "received" },
                label = { Text("Received") },
                leadingIcon = { Icon(Icons.Filled.CallReceived, contentDescription = null) },
            )
        }

        if (entries.isEmpty()) {
            EmptyState(if (direction == "sent") "Nothing sent yet." else "Nothing received yet.")
            return@Column
        }

        val groups = DateGrouping.groupByDay(entries, { it.ts })
        LazyColumn(Modifier.fillMaxSize()) {
            groups.forEach { (header, list) ->
                item(key = "h_$header") {
                    net.morsecode.shared.ui.components.SectionHeader(header)
                }
                items(list, key = { it.id }) { entry ->
                    HistoryRow(entry, vm)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                buildString {
                    append("to ${entry.peerName}")
                    if (entry.kind == "text") append(" · text")
                    if (entry.source != null) append(" · ${entry.source}")
                } + if (entry.direction == "received") "" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.labelMedium)
            Text(
                entry.status,
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.status == "completed") MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.error,
            )
        }
        // Received APKs get an Install button (Android only; hidden on Desktop via platform check)
        if (entry.direction == "received" && entry.filename.endsWith(".apk", true) &&
            !net.morsecode.shared.platform.isDesktopPlatform
        ) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.installReceivedApk(entry) }) { Text("Install") }
        }
    }
}
