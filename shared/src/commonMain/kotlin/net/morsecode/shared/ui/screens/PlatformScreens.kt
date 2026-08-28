package net.morsecode.shared.ui.screens

import androidx.compose.runtime.Composable
import net.morsecode.shared.ui.AppViewModel

/**
 * Platform-selectable Home and Send screens: desktop gets the streamlined
 * connect-first layout (reference design), mobile keeps the compact flow.
 */
@Composable
expect fun PlatformHomeScreen(vm: AppViewModel, onNavigate: (net.morsecode.shared.ui.Route) -> Unit)

@Composable
expect fun PlatformSendScreen(vm: AppViewModel, onDone: () -> Unit)
