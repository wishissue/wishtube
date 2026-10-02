@file:Suppress("UnsafeOptInUsageError")

package app.wishtube

import android.app.Application
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.flow.MutableStateFlow
import app.wishtube.data.AppDatabase
import app.wishtube.data.Downloads
import app.wishtube.data.LibraryRepository
import app.wishtube.data.Notifier
import app.wishtube.data.UserPrefs
import app.wishtube.sources.Http
import app.wishtube.sources.SourceManager

/** Tiny manual DI container. No cloud, no analytics, no backend. */
class AppContainer(ctx: Context) {
    val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "wishtube.db")
        .fallbackToDestructiveMigration()
        .build()
    val dao = db.dao()
    val prefs = ctx.getSharedPreferences("wishtube", Context.MODE_PRIVATE)
    val userPrefs = UserPrefs(prefs)
    val sources = SourceManager(prefs)
    val library = LibraryRepository(db)
    val notifier = Notifier(ctx)
    val downloads = Downloads(ctx, dao, sources, library, notifier)

    /** One shared player: the watch page, mini-player and background service all use it. */
    val player: ExoPlayer by lazy {
        val dataSourceFactory = OkHttpDataSource.Factory(Http.client)
        @OptIn(UnstableApi::class)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(ctx, mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }
    @Volatile var pipEligible = false
    val inPip = MutableStateFlow(false)
    /** URL waiting to be opened (deep link). */
    val deepLink = MutableStateFlow<String?>(null)
}

class App : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .callFactory(Http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(100L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
