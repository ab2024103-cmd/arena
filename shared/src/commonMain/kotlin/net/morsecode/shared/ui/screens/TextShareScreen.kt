package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.DeviceListItem
import net.morsecode.shared.ui.components.EmptyState

/** One-off text/link share (Section 11) — distinct from Chat. */
@Composable
fun TextShareScreen(vm: AppViewModel, onDone: () -> Unit) {
    val devices by vm.devices.collectAsState()
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 1_000_000) text = it },
            label = { Text("Text or link to share") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        Text("${text.length} characters", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Text("Send to", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if (devices.isEmpty()) {
            EmptyState("No nearby devices.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(devices, key = { it.deviceId }) { device ->
                    DeviceListItem(device = device, onClick = {
                        vm.sendText(device, text)
                        onDone()
                    })
                }
            }
        }

    }
}
