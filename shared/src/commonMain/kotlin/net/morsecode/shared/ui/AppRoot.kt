package net.morsecode.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.morsecode.shared.media.AudioItem
import net.morsecode.shared.media.PhotoItem
import net.morsecode.shared.ui.components.MiniAudioPlayerBar
import net.morsecode.shared.ui.components.TransferProgressBar
import net.morsecode.shared.ui.screens.AudioPlayerScreen
import net.morsecode.shared.ui.screens.ChatScreen
import net.morsecode.shared.ui.screens.FilesCategoryScreen
import net.morsecode.shared.ui.screens.HomeScreen
import net.morsecode.shared.ui.screens.ImageViewerScreen
import net.morsecode.shared.ui.screens.LibraryScreen
import net.morsecode.shared.ui.screens.ReceiveScreen
import net.morsecode.shared.ui.screens.RoomScreen
import net.morsecode.shared.ui.screens.ScreenScaffold
import net.morsecode.shared.ui.screens.SendScreen
import net.morsecode.shared.ui.screens.SettingsScreen
import net.morsecode.shared.ui.screens.TextShareScreen
import net.morsecode.shared.ui.screens.VideoPlayerScreen
import net.morsecode.shared.ui.screens.WebConnectScreen

/** Simple in-app navigation: a stack of sealed routes. */
sealed class Route {
    data object Home : Route()
    data object Library : Route()
    data object Chat : Route()
    data object Web : Route()
    data object Settings : Route()
    data object Send : Route()
    data object Receive : Route()
    data object Room : Route()
    data object TextShare : Route()
    data class PhotoViewer(val photos: List<PhotoItem>, val startIndex: Int) : Route()
    data class VideoPlayerRoute(val uri: String, val title: String) : Route()
    data class AudioPlayerRoute(val item: AudioItem) : Route()
    data class FilesCategory(val categoryId: String, val label: String) : Route()
}

private val bottomTabs = listOf<Route>(Route.Home, Route.Library, Route.Chat, Route.Web, Route.Settings)

@Composable
fun MorseCodeApp(vm: AppViewModel) {
    net.morsecode.shared.ui.theme.MorseTheme(vm.themeMode.collectAsState().value) {
        AppRoot(vm)
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val backStack = remember { mutableStateListOf<Route>(Route.Home) }
    val current = backStack.last()
    val isTab = bottomTabs.any { it::class == current::class }

    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
    val push: (Route) -> Unit = { backStack.add(it) }
    val switchTab: (Route) -> Unit = { backStack.clear(); backStack.add(it) }

    AppBackHandler(enabled = !isTab) { pop() }

    val snackbarMessage by vm.toast.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (net.morsecode.shared.platform.isDesktopPlatform) {
                NavigationRail {
                    bottomTabs.forEach { tab ->
                        val (icon, label) = tabMeta(tab)
                        NavigationRailItem(
                            selected = tab::class == current::class,
                            onClick = { switchTab(tab) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).padding(bottom = if (!isTab) 0.dp else 0.dp)) {
                when (val route = current) {
                    is Route.Home -> HomeScreen(vm, onNavigate = push)
                    is Route.Library -> LibraryScreen(vm, onNavigate = push)
                    is Route.Chat -> ChatScreen(vm, onNavigate = push)
                    is Route.Web -> WebConnectScreen(vm, onNavigate = push)
                    is Route.Settings -> SettingsScreen(vm)
                    is Route.Send -> ScreenScaffold(title = "Send", onBack = pop) {
                        SendScreen(vm, onDone = pop)
                    }
                    is Route.Receive -> ScreenScaffold(title = "Receive", onBack = pop) {
                        ReceiveScreen(vm)
                    }
                    is Route.Room -> ScreenScaffold(title = "Room", onBack = pop) {
                        RoomScreen(vm)
                    }
                    is Route.TextShare -> ScreenScaffold(title = "Send text / link", onBack = pop) {
                        TextShareScreen(vm, onDone = pop)
                    }
                    is Route.PhotoViewer -> ScreenScaffold(
                        title = route.photos.getOrNull(route.startIndex)?.filename ?: "Photo", onBack = pop,
                    ) {
                        ImageViewerScreen(vm, route.photos, route.startIndex, onClose = pop)
                    }
                    is Route.VideoPlayerRoute -> ScreenScaffold(title = route.title, onBack = pop) {
                        VideoPlayerScreen(vm, route.uri)
                    }
                    is Route.AudioPlayerRoute -> ScreenScaffold(title = route.item.filename, onBack = pop) {
                        AudioPlayerScreen(vm, route.item)
                    }
                    is Route.FilesCategory -> ScreenScaffold(title = route.label, onBack = pop) {
                        FilesCategoryScreen(vm, route.categoryId)
                    }
                }
            }
        }

        if (!net.morsecode.shared.platform.isDesktopPlatform && isTab) {
            NavigationBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                bottomTabs.forEach { tab ->
                    val (icon, label) = tabMeta(tab)
                    NavigationBarItem(
                        selected = tab::class == current::class,
                        onClick = { switchTab(tab) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }

        IncomingRequestOverlay(vm)
        IncomingTextOverlay(vm)
        SendProgressOverlay(vm)
        MiniAudioPlayerBar(vm, Modifier.align(Alignment.BottomCenter))

        snackbarMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            ) {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun tabMeta(route: Route): Pair<ImageVector, String> = when (route) {
    is Route.Home -> Pair(Icons.Filled.Home, "Home")
    is Route.Library -> Pair(Icons.Filled.VideoLibrary, "Library")
    is Route.Chat -> Pair(Icons.Filled.Chat, "Chat")
    is Route.Web -> Pair(Icons.Filled.Language, "Web")
    is Route.Settings -> Pair(Icons.Filled.Settings, "Settings")
    else -> Pair(Icons.Filled.Home, "")
}

@Composable
private fun IncomingRequestOverlay(vm: AppViewModel) {
    val request by vm.incomingRequest.collectAsState()
    request?.let { req ->
        val rejectAll = req.files.map { it.file_id }.toSet()
        AlertDialog(
            onDismissRequest = { vm.resolveIncomingRequest(acceptAll = false, rejectedIds = rejectAll) },
            title = { Text("Incoming transfer") },
            text = {
                Column {
                    Text("From: ${req.peerName}")
                    Text("${req.files.size} file(s) · ${formatBytes(req.totalBytes)}")
                    Column(Modifier.padding(top = 8.dp)) {
                        req.files.take(6).forEach { f ->
                            Text("• ${f.filename}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        if (req.files.size > 6) Text("… and ${req.files.size - 6} more")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.resolveIncomingRequest(acceptAll = true) }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { vm.resolveIncomingRequest(acceptAll = false, rejectedIds = rejectAll) }) {
                    Text("Reject")
                }
            },
        )
    }
}

@Composable
private fun IncomingTextOverlay(vm: AppViewModel) {
    val incoming by vm.incomingText.collectAsState()
    incoming?.let { txt ->
        AlertDialog(
            onDismissRequest = { vm.clearIncomingText() },
            title = { Text("Text from ${txt.from}") },
            text = { Text(txt.text, maxLines = 12) },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(txt.text)
                    vm.toast("Copied to clipboard")
                    vm.clearIncomingText()
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearIncomingText() }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SendProgressOverlay(vm: AppViewModel) {
    val progress by vm.sendProgress.collectAsState()
    progress?.let { sp ->
        AlertDialog(
            onDismissRequest = { vm.dismissSendProgress() },
            title = { Text("Sending · ${(sp.overallPercent * 100).toInt()}%") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sp.recipients.forEach { r ->
                        TransferProgressBar(
                            progress = r.percent,
                            state = r.state,
                            speedBps = r.speedBps,
                            filename = r.deviceName + if (r.filename.isNotEmpty()) " · ${r.filename}" else "",
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissSendProgress() }) { Text("Hide") }
            },
        )
    }
}

fun copyToClipboard(text: String) {
    platformCopyToClipboard(text)
}

expect fun platformCopyToClipboard(text: String)
