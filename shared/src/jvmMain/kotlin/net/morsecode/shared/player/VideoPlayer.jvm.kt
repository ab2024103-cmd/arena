package net.morsecode.shared.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * JVM/desktop actual for VideoPlayer. The full VLCJ-backed player (used on the
 * desktop app) is provided through the desktop source set; this JVM-target
 * actual is a lightweight placeholder so the shared module compiles for the
 * plain-JVM target used by tests and the desktop packaging job.
 */
@Composable
actual fun VideoPlayer(
    uri: String,
    modifier: Modifier,
    onPlaybackStateChanged: (PlaybackState) -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Video preview is unavailable on this platform.")
    }
}
