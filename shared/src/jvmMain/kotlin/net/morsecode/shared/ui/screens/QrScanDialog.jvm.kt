package net.morsecode.shared.ui.screens

import androidx.compose.runtime.Composable
import net.morsecode.shared.ui.AppViewModel

/**
 * JVM/desktop actual for QrScanDialog. There is no camera on desktop, so we
 * fall back to the manual ip:port entry dialog that also exists on Android.
 */
@Composable
actual fun QrScanDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    ManualConnectDialog(vm = vm, onDismiss = onDismiss)
}
