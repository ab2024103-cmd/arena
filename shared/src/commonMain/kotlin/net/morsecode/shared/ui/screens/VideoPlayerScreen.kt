package net.morsecode.shared.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.morsecode.shared.player.PlaybackState
import net.morsecode.shared.player.VideoPlayer
import net.morsecode.shared.ui.AppViewModel

@Composable
fun VideoPlayerScreen(vm: AppViewModel, uri: String) {
    Box(Modifier.fillMaxSize()) {
        VideoPlayer(
            uri = uri,
            modifier = Modifier.fillMaxSize(),
            onPlaybackStateChanged = { },
        )
    }
}
