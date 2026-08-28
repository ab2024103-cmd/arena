package net.morsecode.shared.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.ChatBubble
import net.morsecode.shared.ui.components.DeviceListItem
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.SectionHeader

/** Chat hub: device picker -> conversation thread (Section G). */
@Composable
fun ChatScreen(vm: AppViewModel, onNavigate: (net.morsecode.shared.ui.Route) -> Unit) {
    var activePeer by remember { mutableStateOf<net.morsecode.shared.net.DeviceInfo?>(null) }
    val devices by vm.devices.collectAsState()
    val threads by vm.chatRepo.threads.collectAsState()

    val peer = activePeer
    if (peer != null) {
        ChatThread(vm, peer) { activePeer = null }
        return
    }

    val known by vm.knownDevices.collectAsState()
    val history by vm.historyRepo.entries.collectAsState()
    fun displayName(peerId: String): String =
        devices.firstOrNull { it.deviceId == peerId }?.name
            ?: known.firstOrNull { it.deviceId == peerId }?.name
            ?: history.firstOrNull { it.peerDeviceId == peerId }?.peerName
            ?: "Device ${peerId.take(6)}"

    Column(Modifier.fillMaxSize()) {
        if (threads.isNotEmpty()) {
            SectionHeader("Conversations - tap to open")
            threads.forEach { (peerId, messages) ->
                val last = messages.lastOrNull() ?: return@forEach
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable {
                            activePeer = devices.firstOrNull { it.deviceId == peerId }
                                ?: net.morsecode.shared.net.DeviceInfo(peerId, displayName(peerId), "chat", "-", 0)
                        },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(displayName(peerId), style = MaterialTheme.typography.titleSmall)
                            Text(
                                last.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "open",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        SectionHeader("Nearby devices")
        if (devices.isEmpty()) {
            EmptyState("No devices discovered yet.\nChat works with any connected Morse Code device on the same Wi-Fi.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(devices, key = { it.deviceId }) { device ->
                    DeviceListItem(device = device, onClick = { activePeer = device })
                }
            }
        }
    }
}

@Composable
private fun ChatThread(vm: AppViewModel, peer: net.morsecode.shared.net.DeviceInfo, onBack: () -> Unit) {
    val threads by vm.chatRepo.threads.collectAsState()
    val messages = threads[peer.deviceId] ?: emptyList()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(messages, key = { it.messageId }) { ChatBubble(it) }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        vm.sendChat(peer, input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
