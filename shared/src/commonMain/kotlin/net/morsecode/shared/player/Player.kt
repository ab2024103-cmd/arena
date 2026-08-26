package net.morsecode.shared.player

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState { IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

/** expect @Composable video player (F.2). Actuals: Media3 / VLCJ. */
@androidx.compose.runtime.Composable
expect fun VideoPlayer(
    uri: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onPlaybackStateChanged: (PlaybackState) -> Unit = {},
)

/** Audio playback abstraction (F.3). */
interface AudioPlaybackController {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val currentUri: StateFlow<String?>
    val currentTitle: StateFlow<String?>
    fun play(uri: String, title: String? = null)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
    fun next()
    fun previous()
}
