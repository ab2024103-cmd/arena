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
import androidx.compose.material3.FilterChip
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
import net.morsecode.shared.media.AppInfo
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.components.AppListItem
import net.morsecode.shared.ui.components.EmptyState

@Composable
fun AppsTab(vm: AppViewModel, onNavigate: (net.morsecode.shared.ui.Route) -> Unit) {
    // Desktop: no meaningful app library (Section D) -> explanatory empty state.
    if (net.morsecode.shared.platform.isDesktopPlatform) {
        EmptyState("The Apps tab lists installed Android apps and is available on the Android app only.")
        return
    }
    val appLibrary = ServiceLocator.deps.appLibrary
    if (appLibrary == null) {
        EmptyState("App library unavailable on this device.")
        return
    }

    val includeSystem by vm.includeSystemApps.collectAsState()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<AppInfo>() }
    val scope = rememberCoroutineScope()
    var extracting by remember { mutableStateOf(false) }

    LaunchedEffect(includeSystem) {
        apps = runCatching { appLibrary.getInstalledApps(includeSystem) }.getOrDefault(emptyList())
    }

    val filtered = apps.filter {
        query.isBlank() || it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, true)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = includeSystem,
                onClick = { vm.setIncludeSystemApps(!includeSystem) },
                label = { Text("System apps") },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
        )

        if (filtered.isEmpty()) {
            EmptyState("No apps found.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        selected = app in selected,
                        onToggle = { if (app in selected) selected.remove(app) else selected.add(app) },
                    )
                }
            }
        }

        Button(
            onClick = {
                extracting = true
                scope.launch {
                    val files = vm.extractApps(selected.toList())
                    vm.pendingSendFiles.value = files
                    extracting = false
                    selected.clear()
                    onNavigate(net.morsecode.shared.ui.Route.Send)
                }
            },
            enabled = selected.isNotEmpty() && !extracting,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(if (extracting) "Extracting APKs…" else "Send ${selected.size} app(s)")
        }
    }
}
