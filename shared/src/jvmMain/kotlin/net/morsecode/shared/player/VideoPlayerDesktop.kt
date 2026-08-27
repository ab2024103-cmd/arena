package net.morsecode.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.SwingPanel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/**
 * VLCJ-backed video player (Section F.2, desktop actual). Requires VLC Media
 * Player installed on the host; if missing, a graceful message is shown
 * instead of failing silently.
 */
@Composable
actual fun VideoPlayer(
    uri: String,
    modifier: Modifier,
    onPlaybackStateChanged: (PlaybackState) -> Unit,
) {
    val vlcAvailable = remember { NativeDiscovery().discover() }
    if (!vlcAvailable) {
        Box(modifier.fillMaxSize().background(Color(0xFF121218)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VLC Media Player is required for video playback on desktop.", color = Color.White)
                Text(
                    "Install it from https://www.videolan.org/ and restart Morse Code.",
                    color = Color(0xFF9E9EA7),
                )
            }
        }
        LaunchedEffect(Unit) { onPlaybackStateChanged(PlaybackState.ERROR) }
        return
    }

    val path = remember(uri) {
        if (uri.startsWith("file:")) java.net.URI(uri).path ?: uri else uri
    }
    val component = remember(path) {
        EmbeddedMediaPlayerComponent().apply {
            mediaPlayer().media().play(path)
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderDrag by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val mp = component.mediaPlayer()
            if (mp != null && mp.status().isPlaying) {
                if (!sliderDrag) position = mp.status().time()
                duration = mp.status().length()
                isPlaying = true
            } else if (duration > 0) {
                isPlaying = false
            }
            delay(250)
        }
    }

    DisposableEffect(path) {
        onPlaybackStateChanged(PlaybackState.BUFFERING)
        onDispose {
            runCatching {
                component.mediaPlayer().controls().stop()
                component.release()
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        SwingPanel(
            factory = { component },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
            Slider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = {
                    sliderDrag = true
                    position = (it * duration).toLong()
                },
                onValueChangeFinished = {
                    component.mediaPlayer().controls().setTime(position)
                    sliderDrag = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val mp = component.mediaPlayer()
                    if (mp.status().isPlaying) {
                        mp.controls().pause(); isPlaying = false
                    } else {
                        mp.controls().play(); isPlaying = true
                    }
                }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                    )
                }
                Text(
                    "${position / 1000}s / ${duration / 1000}s",
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
