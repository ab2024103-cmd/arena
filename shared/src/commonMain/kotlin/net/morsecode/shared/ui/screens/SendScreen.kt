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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import net.morsecode.shared.net.DeviceInfo
import net.morsecode.shared.platform.PickedFile
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.DeviceListItem
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.formatBytes
import kotlinx.coroutines.launch

@Composable
fun SendScreen(vm: AppViewModel, onDone: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val picked = remember { mutableStateListOf<PickedFile>() }
    LaunchedEffect(Unit) {
        val pending = vm.pendingSendFiles.value
        if (pending.isNotEmpty()) {
            picked.addAll(pending)
            vm.pendingSendFiles.value = emptyList()
        }
    }
    val selectedTargets = remember { mutableStateListOf<DeviceInfo>() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                if (!picking) {
                    picking = true
                    scope.launch {
                        val files = ServiceLocator.deps.pickFiles("Select files to send")
                        picked.clear()
                        picked.addAll(files)
                        picking = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (picked.isEmpty()) "Choose files" else "Choose different files (${picked.size} selected)")
        }

        if (picked.isNotEmpty()) {
            Card {
                Column(Modifier.padding(12.dp)) {
                    picked.take(8).forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "  ${f.displayName} · ${formatBytes(f.sizeBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                        }
                    }
                    if (picked.size > 8) Text("… and ${picked.size - 8} more", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("Send to", style = MaterialTheme.typography.titleMedium)
        if (devices.isEmpty()) {
            EmptyState("No nearby devices. Both devices must be on the same Wi-Fi.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(devices, key = { it.deviceId }) { device ->
                    val selected = device in selectedTargets
                    DeviceListItem(
                        device = device,
                        onClick = {
                            if (selected) selectedTargets.remove(device) else selectedTargets.add(device)
                        },
                        trailing = {
                            if (selected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "selected",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }

        Button(
            onClick = {
                vm.sendFiles(selectedTargets.toList(), picked.toList())
                onDone()
            },
            enabled = picked.isNotEmpty() && selectedTargets.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send ${picked.size} file(s) to ${selectedTargets.size} device(s)")
        }
    }
}
