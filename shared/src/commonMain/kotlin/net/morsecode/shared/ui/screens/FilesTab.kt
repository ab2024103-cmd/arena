package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.media.FileCategorizer
import net.morsecode.shared.media.GenericFile
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route
import net.morsecode.shared.ui.components.EmptyState
import net.morsecode.shared.ui.components.FileCategoryCard
import net.morsecode.shared.ui.components.GenericFileRow
import net.morsecode.shared.ui.components.StorageUsageBar

@Composable
fun FilesTab(vm: AppViewModel, initialCategory: String?, onNavigate: (Route) -> Unit) {
    val files = remember { mutableStateOf<List<GenericFile>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<GenericFile>() }
    var openCategory by remember { mutableStateOf(initialCategory) }
    var usage by remember { mutableStateOf<net.morsecode.shared.media.StorageUsage?>(null) }

    LaunchedEffect(Unit) {
        files.value = runCatching { ServiceLocator.deps.mediaLibrary.getAllFiles() }.getOrDefault(emptyList())
        usage = runCatching { ServiceLocator.deps.mediaLibrary.getStorageUsage() }.getOrNull()
    }

    val filtered = files.value.filter { query.isBlank() || it.filename.contains(query, true) }

    // Category drill-in
    if (openCategory != null) {
        val catFiles = FileCategorizer.filter(filtered, openCategory!!)
        Column(Modifier.fillMaxSize()) {
            if (selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(onClick = {
                        vm.pendingSendFiles.value = selected.map {
                            net.morsecode.shared.platform.PickedFile(it.uri, it.filename, it.sizeBytes, "application/octet-stream")
                        }
                        selected.clear()
                        onNavigate(Route.Send)
                    }) { Text("Send ${selected.size}") }
                }
            }
            if (catFiles.isEmpty()) {
                EmptyState("No files in this category.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(catFiles, key = { it.uri }) { file ->
                        GenericFileRow(
                            file = file,
                            selected = file in selected,
                            selecting = selected.isNotEmpty(),
                            onClick = { if (file in selected) selected.remove(file) else selected.add(file) },
                            onToggle = { if (file in selected) selected.remove(file) else selected.add(file) },
                        )
                    }
                }
            }
        }
        return
    }

    // Category overview
    val counts = FileCategorizer.counts(files.value)

    Column(Modifier.fillMaxSize()) {
        usage?.let { StorageUsageBar(it.usedBytes, it.totalBytes) }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search all files") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
        )
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(FileCategorizer.CATEGORIES, key = { it.id }) { cat ->
                FileCategoryCard(
                    label = cat.label,
                    count = counts[cat.id] ?: 0,
                    icon = when (cat.id) {
                        "documents" -> Icons.Filled.Description
                        "ebooks" -> Icons.Filled.MenuBook
                        "apks" -> Icons.Filled.InstallMobile
                        "archives" -> Icons.Filled.FolderZip
                        else -> Icons.Filled.Warning
                    },
                    onClick = { openCategory = cat.id },
                )
            }
        }
    }
}

@Composable
fun FilesCategoryScreen(vm: AppViewModel, categoryId: String) {
    FilesTab(vm, initialCategory = categoryId, onNavigate = {})
}
