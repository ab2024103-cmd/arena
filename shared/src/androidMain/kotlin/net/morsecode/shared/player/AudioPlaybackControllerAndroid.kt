package net.morsecode.shared.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process ExoPlayer controller (F.3). The foreground
 * [AudioPlaybackService] attaches a MediaSession to the same player for
 * lock-screen / notification controls so playback continues in background.
 */
class AudioPlaybackControllerAndroid private constructor(context: Context) : AudioPlaybackController {
    private val appContext = context.applicationContext

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) _durationMs.value = duration
                    if (state == Player.STATE_ENDED) _isPlaying.value = false
                }
            })
        }
    }

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs
    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs
    private val _currentUri = MutableStateFlow<String?>(null)
    override val currentUri: StateFlow<String?> = _currentUri
    private val _currentTitle = MutableStateFlow<String?>(null)
    override val currentTitle: StateFlow<String?> = _currentTitle

    private val queue = ArrayList<String>()
    private var queueIndex = -1

    override fun play(uri: String, title: String?) {
        if (uri !in queue) {
            queue.add(uri)
            queueIndex = queue.size - 1
        } else {
            queueIndex = queue.indexOf(uri)
        }
        _currentUri.value = uri
        _currentTitle.value = title
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        // start/refresh the foreground service for background playback
        val svc = Intent(appContext, AudioPlaybackService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(svc)
            else appContext.startService(svc)
        } catch (e: Exception) {
            // notification permission may be missing on 33+; playback still works
        }
    }

    override fun pause() {
        player.pause()
        _isPlaying.value = false
    }

    override fun resume() {
        player.play()
        _isPlaying.value = true
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    override fun stop() {
        player.stop()
        _isPlaying.value = false
        _currentUri.value = null
        _currentTitle.value = null
        runCatching { appContext.stopService(Intent(appContext, AudioPlaybackService::class.java)) }
    }

    override fun next() {
        if (queueIndex + 1 < queue.size) {
            queueIndex++
            play(queue[queueIndex])
        }
    }

    override fun previous() {
        if (queueIndex - 1 >= 0) {
            queueIndex--
            play(queue[queueIndex])
        }
    }

    fun tick() {
        if (player.isPlaying) _currentPositionMs.value = player.currentPosition
    }

    companion object {
        @Volatile private var instance: AudioPlaybackControllerAndroid? = null
        fun getInstance(context: Context): AudioPlaybackControllerAndroid =
            instance ?: synchronized(this) {
                instance ?: AudioPlaybackControllerAndroid(context).also { instance = it }
            }
    }
}
