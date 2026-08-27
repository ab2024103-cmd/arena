package net.morsecode.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * JVM/desktop video surface. Native in-app video embedding (vlcj Swing
 * interop) is not available inside the shared multiplatform UI, so the
 * desktop app shows a graceful notice. Audio playback remains fully
 * supported via [AudioPlaybackControllerDesktop] (VLCJ, headless).
 */
@Composable
actual fun VideoPlayer(
    uri: String,
    modifier: Modifier,
    onPlaybackStateChanged: (PlaybackState) -> Unit,
) {
    LaunchedEffect(uri) { onPlaybackStateChanged(PlaybackState.ERROR) }
    Box(modifier.fillMaxSize().background(Color(0xFF121218)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("In-app video preview is unavailable on this platform.", color = Color.White)
            Text(
                "The file itself is fully transferred - audio playback is supported.",
                color = Color(0xFF9E9EA7),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
