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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import net.morsecode.shared.media.PhotoItem
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.PhotoGridItem
import net.morsecode.shared.ui.components.SectionHeader

@Composable
fun PhotosTab(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    val photos = remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<PhotoItem>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        photos.value = runCatching { ServiceLocator.deps.mediaLibrary.getPhotos() }.getOrDefault(emptyList())
    }

    val filtered = photos.value.filter { query.isBlank() || it.filename.contains(query, true) }
    val groups = net.morsecode.shared.media.DateGrouping.groupByDay(filtered, { it.dateTakenEpochMs })

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search photos") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
        )

        if (selected.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    scope.launch {
                        val files = selected.map {
                            net.morsecode.shared.platform.PickedFile(it.uri, it.filename, it.sizeBytes, "image/*")
                        }
                        vm.pendingSendFiles.value = files
                        selected.clear()
                        onNavigate(Route.Send)
                    }
                }) { Text("Send") }
            }
        }

        if (filtered.isEmpty()) {
            EmptyState("No photos found.\nGrant photo permissions in Settings if prompted.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (header, list) ->
                    item(key = "h_$header") { SectionHeader(header) }
                    val chunks = list.chunked(3)
                    chunks.forEachIndexed { ci, chunk ->
                        item(key = "g_${header}_$ci") {
                            Row(Modifier.padding(horizontal = 8.dp)) {
                                chunk.forEach { photo ->
                                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).padding(2.dp)) {
                                        PhotoGridItem(
                                            uri = photo.uri,
                                            selected = photo in selected,
                                            selecting = selected.isNotEmpty() || false,
                                            onClick = { onNavigate(Route.PhotoViewer(filtered, filtered.indexOf(photo).coerceAtLeast(0))) },
                                            onToggle = { if (photo in selected) selected.remove(photo) else selected.add(photo) },
                                        )
                                    }
                                }
                                repeat(3 - chunk.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
