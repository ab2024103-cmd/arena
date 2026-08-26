package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.DeviceListItem
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.SectionHeader

/** Room / group sharing (Section 8). */
@Composable
fun RoomScreen(vm: AppViewModel) {
    val room by vm.roomManager.room.collectAsState()
    val devices by vm.devices.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val r = room
        if (r == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Create a room", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rooms let you send to many devices at once. Creating a room broadcasts it over mDNS; others join openly (no approval step). The creator leaving ends the room.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { vm.roomManager.createRoom() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Create Room")
                    }
                }
            }

            SectionHeader("Or join a room via a discovered device")
            val roomAdvertisers = devices.filter { it.roomId != null }
            if (roomAdvertisers.isEmpty()) {
                EmptyState("No rooms discovered nearby.")
            } else {
                roomAdvertisers.forEach { advertiser ->
                    DeviceListItem(device = advertiser, onClick = {
                        vm.toast("Joining ${advertiser.name}'s room…")
                        vm.joinRoomOpen(advertiser)
                    })
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (r.isCreator) "Your room" else "Joined room", style = MaterialTheme.typography.titleMedium)
                    Text("Room ID: ${r.roomId.take(12)}…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Members (${r.members.size})", style = MaterialTheme.typography.labelMedium)
                    r.members.forEach { m ->
                        Text("• ${m.name}" + if (m.device_id == vm.profile.deviceId) " (you)" else "")
                    }
                    OutlinedButton(onClick = { vm.roomManager.leaveRoom() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Leave room")
                    }
                }
            }

            Text("Send to room: pick files on the Home → Send screen while the room is active; member targets are used automatically.", style = MaterialTheme.typography.bodyMedium)
            val memberTargets = vm.roomManager.memberTargets(devices)
            if (memberTargets.isNotEmpty()) {
                SectionHeader("Room member devices nearby")
                memberTargets.forEach { d ->
                    DeviceListItem(device = d, onClick = { vm.toast("Use Home → Send with this device") })
                }
            }
        }
    }
}
