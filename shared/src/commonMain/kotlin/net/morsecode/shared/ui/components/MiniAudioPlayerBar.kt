package net.morsecode.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.morsecode.shared.ui.AppViewModel

/** Persistent bottom mini-player while audio is active (F.3). */
@Composable
fun MiniAudioPlayerBar(vm: AppViewModel, modifier: Modifier = Modifier) {
    val currentUri by vm.audio.currentUri.collectAsState()
    val isPlaying by vm.audio.isPlaying.collectAsState()
    val position by vm.audio.currentPositionMs.collectAsState()
    val duration by vm.audio.durationMs.collectAsState()
    val title by vm.audio.currentTitle.collectAsState()

    val uri = currentUri ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().padding(bottom = 72.dp),
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    title ?: "Audio",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1,
                )
                IconButton(onClick = { if (isPlaying) vm.audio.pause() else vm.audio.resume() }) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
                }
                IconButton(onClick = { vm.audio.stop() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop")
                }
            }
        }
    }
}
