package app.wishtube

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background playback + lock-screen/notification controls. Wraps the SAME ExoPlayer the UI uses,
 * so playback continues when the app is backgrounded (if the user allows it in Settings).
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, (application as App).container.player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = (application as App).container.player
        if (!player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release()   // releases the session only; the player is owned by AppContainer
        session = null
        super.onDestroy()
    }
}
