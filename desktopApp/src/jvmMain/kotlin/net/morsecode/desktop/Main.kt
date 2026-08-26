package net.morsecode.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.morsecode.shared.storage.ServiceLocator
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.MorseCodeApp

fun main() {
    ServiceLocator.init(net.morsecode.shared.platform.buildPlatformDeps(null))
    val vm = AppViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    TrayManager.install(vm)

    application {
        val state = rememberWindowState(width = 1100.dp, height = 760.dp)

        Window(
            onCloseRequest = {
                vm.shutdown()
                TrayManager.remove()
                exitApplication()
            },
            title = "Morse Code",
            state = state,
            icon = TrayManager.windowIconPainter(),
        ) {
            // Native drag-and-drop (Section D): dropped files queue a send.
            DragDropHost(vm)

            MorseCodeApp(vm)

            LaunchedEffect(Unit) {
                FirewallDiagnostics.checkOnce { msg -> vm.toast(msg) }
                VlcDiagnostics.checkOnce { msg -> vm.toast(msg) }
            }
        }
    }
}
