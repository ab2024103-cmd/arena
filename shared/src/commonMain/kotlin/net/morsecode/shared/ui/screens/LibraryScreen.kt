package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.History
import androidx.compose.material.icons.automirrored.filled.Image
import androidx.compose.material.icons.automirrored.filled.VideoLibrary
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route

private data class LibraryTab(val label: String, val icon: ImageVector)

private val libraryTabs = listOf(
    LibraryTab("History", Icons.AutoMirrored.Filled.History),
    LibraryTab("Apps", Icons.Filled.Apps),
    LibraryTab("Photos", Icons.AutoMirrored.Filled.Image),
    LibraryTab("Videos", Icons.AutoMirrored.Filled.VideoLibrary),
    LibraryTab("Music", Icons.Filled.MusicNote),
    LibraryTab("Files", Icons.Filled.Folder),
)

/** Library hub hosting the History | Apps | Photos | Videos | Music | Files tab row (Section E). */
@Composable
fun LibraryScreen(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            libraryTabs.forEachIndexed { i, tab ->
                Tab(
                    selected = selected == i,
                    onClick = { selected = i },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        when (selected) {
            0 -> HistoryTab(vm)
            1 -> AppsTab(vm, onNavigate)
            2 -> PhotosTab(vm, onNavigate)
            3 -> VideosTab(vm, onNavigate)
            4 -> MusicTab(vm, onNavigate)
            5 -> FilesTab(vm, initialCategory = null, onNavigate = onNavigate)
        }
    }
}
