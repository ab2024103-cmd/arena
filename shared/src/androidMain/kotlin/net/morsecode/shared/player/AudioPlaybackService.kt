package net.morsecode.shared.player

import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service dedicated to background audio (Section C / F.3). Uses
 * Media3 MediaSessionService (API-agnostic); on very old devices the
 * notification channel is created defensively in onStartCommand.
 */
class AudioPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val controller = AudioPlaybackControllerAndroid.getInstance(this)
        mediaSession = MediaSession.Builder(this, controller.player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
