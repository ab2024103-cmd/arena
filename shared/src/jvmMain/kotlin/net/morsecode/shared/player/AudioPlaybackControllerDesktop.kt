package net.morsecode.shared.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

/** VLCJ headless audio controller (Section F.3, desktop actual). */
class AudioPlaybackControllerDesktop : AudioPlaybackController {
    private val component by lazy { AudioPlayerComponent() }

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
    private var ticker: Thread? = null

    override fun play(uri: String, title: String?) {
        val path = if (uri.startsWith("file:")) java.net.URI(uri).path ?: uri else uri
        if (uri !in queue) {
            queue.add(uri)
            queueIndex = queue.size - 1
        } else {
            queueIndex = queue.indexOf(uri)
        }
        _currentUri.value = uri
        _currentTitle.value = title
        component.mediaPlayer().media().play(path)
        _isPlaying.value = true
        startTicker()
    }

    private fun startTicker() {
        if (ticker?.isAlive == true) return
        ticker = Thread {
            while (true) {
                try {
                    val mp = component.mediaPlayer()
                    if (mp.status().isPlaying) {
                        _currentPositionMs.value = mp.status().time()
                        _durationMs.value = mp.status().length()
                    }
                    Thread.sleep(300)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    break
                }
            }
        }
        ticker?.isDaemon = true
        ticker?.start()
    }

    override fun pause() {
        component.mediaPlayer().controls().pause()
        _isPlaying.value = false
    }

    override fun resume() {
        component.mediaPlayer().controls().play()
        _isPlaying.value = true
    }

    override fun seekTo(positionMs: Long) {
        component.mediaPlayer().controls().setTime(positionMs)
        _currentPositionMs.value = positionMs
    }

    override fun stop() {
        component.mediaPlayer().controls().stop()
        _isPlaying.value = false
        _currentUri.value = null
        _currentTitle.value = null
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
}
