package net.morsecode.shared.ui.screens

import androidx.compose.runtime.Composable
import net.morsecode.shared.ui.AppViewModel
import net.morsecode.shared.ui.Route

@Composable
actual fun PlatformHomeScreen(vm: AppViewModel, onNavigate: (Route) -> Unit) {
    HomeScreen(vm, onNavigate)
}

@Composable
actual fun PlatformSendScreen(vm: AppViewModel, onDone: () -> Unit) {
    SendScreen(vm, onDone)
}
