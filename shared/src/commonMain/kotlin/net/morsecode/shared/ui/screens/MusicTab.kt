package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.morsecode.shared.media.AudioItem
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.AudioListItem
import net.morsecode.shared.ui.components.EmptyState

@Composable
fun MusicTab(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    val tracks = remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<AudioItem>() }

    LaunchedEffect(Unit) {
        tracks.value = runCatching { ServiceLocator.deps.mediaLibrary.getAudio() }.getOrDefault(emptyList())
    }

    val filtered = tracks.value.filter {
        query.isBlank() || it.filename.contains(query, true) ||
            (it.artist ?: "").contains(query, true) || (it.album ?: "").contains(query, true)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search music") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
        )
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    vm.pendingSendFiles.value = selected.map {
                        net.morsecode.shared.platform.PickedFile(it.uri, it.filename, it.sizeBytes, "audio/*")
                    }
                    selected.clear()
                    onNavigate(Route.Send)
                }) { Text("Send") }
            }
        }
        if (filtered.isEmpty()) {
            EmptyState("No music found.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.uri }) { track ->
                    AudioListItem(
                        item = track,
                        selected = track in selected,
                        selecting = selected.isNotEmpty(),
                        onClick = { onNavigate(Route.AudioPlayerRoute(track)) },
                        onToggle = { if (track in selected) selected.remove(track) else selected.add(track) },
                    )
                }
            }
        }
    }
}
