package app.wishtube

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import app.wishtube.ui.MainApp

class MainActivity : ComponentActivity() {
    private val container get() = (application as App).container
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { MainApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.dataString?.let { container.deepLink.value = it }
    }

    /** Connecting a controller binds PlaybackService so background playback + notification work. */
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        super.onStop()
        if (!container.userPrefs.background.value && !isInPictureInPictureMode) container.player.pause()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    /** Enter Picture-in-Picture when leaving the app while a video is playing on the watch page. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (container.pipEligible && container.userPrefs.pip.value && container.player.isPlaying) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        container.inPip.value = isInPictureInPictureMode
    }
}
