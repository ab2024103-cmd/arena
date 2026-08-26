package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import net.morsecode.shared.media.VideoItem
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.SectionHeader
import net.morsecode.shared.ui.components.VideoListItem

@Composable
fun VideosTab(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    val videos = remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<VideoItem>() }

    LaunchedEffect(Unit) {
        videos.value = runCatching { ServiceLocator.deps.mediaLibrary.getVideos() }.getOrDefault(emptyList())
    }

    val filtered = videos.value.filter { query.isBlank() || it.filename.contains(query, true) }
    val groups = net.morsecode.shared.media.DateGrouping.groupByDay(filtered) { it.dateAddedEpochMs }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search videos") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
        )
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    vm.pendingSendFiles.value = selected.map {
                        net.morsecode.shared.platform.PickedFile(it.uri, it.filename, it.sizeBytes, "video/*")
                    }
                    selected.clear()
                    onNavigate(Route.Send)
                }) { Text("Send") }
            }
        }
        if (filtered.isEmpty()) {
            EmptyState("No videos found.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (header, list) ->
                    item(key = "h_$header") { SectionHeader(header) }
                    items(list, key = { it.uri }) { video ->
                        VideoListItem(
                            item = video,
                            selected = video in selected,
                            selecting = selected.isNotEmpty(),
                            onClick = { onNavigate(Route.VideoPlayerRoute(video.uri, video.filename)) },
                            onToggle = { if (video in selected) selected.remove(video) else selected.add(video) },
                        )
                    }
                }
            }
        }
    }
}
