package com.vexmusic.player

import android.app.Application
import android.content.Context
import com.vexmusic.player.audio.EqualizerController
import com.vexmusic.player.audio.VisualizerController
import com.vexmusic.player.data.database.VexDatabase
import com.vexmusic.player.data.preferences.SettingsRepository
import com.vexmusic.player.data.repository.BackupRepository
import com.vexmusic.player.data.repository.HistoryRepository
import com.vexmusic.player.data.repository.MusicRepository
import com.vexmusic.player.data.repository.PlaylistRepository
import com.vexmusic.player.lyrics.provider.EmbeddedLyricsProvider
import com.vexmusic.player.lyrics.provider.KugouProvider
import com.vexmusic.player.lyrics.provider.LrclibProvider
import com.vexmusic.player.lyrics.provider.LyricsOvhProvider
import com.vexmusic.player.lyrics.provider.LyricsProvider
import com.vexmusic.player.lyrics.provider.NetEaseProvider
import com.vexmusic.player.lyrics.provider.PaxsenixProvider
import com.vexmusic.player.lyrics.repository.LyricsRepository
import com.vexmusic.player.lyrics.translation.MyMemoryTranslationProvider
import com.vexmusic.player.player.controller.PlayerController
import com.vexmusic.player.player.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Lightweight manual dependency container — no extra DI framework required. */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: VexDatabase = VexDatabase.get(context)
    val settingsRepository = SettingsRepository(context)
    val musicRepository = MusicRepository(context, database.trackDao())
    val playlistRepository = PlaylistRepository(database.playlistDao(), database.trackDao())
    val historyRepository = HistoryRepository(database.historyDao(), database.searchHistoryDao())

    val providers: List<LyricsProvider> = listOf(
        LrclibProvider(),
        KugouProvider(),
        NetEaseProvider(),
        LyricsOvhProvider(),
        EmbeddedLyricsProvider { query ->
            musicRepository.getTrack(query.trackId)?.let { musicRepository.readEmbeddedLyrics(it) }
        },
        PaxsenixProvider()
    )

    /** Providers that are switched on the first time the app starts. */
    val defaultEnabledProviders: Set<String> =
        setOf("lrclib", "kugou", "netease", "lyricsovh", "embedded")

    val lyricsRepository = LyricsRepository(
        lyricsDao = database.lyricsDao(),
        translationDao = database.translationDao(),
        providers = providers,
        translationProvider = MyMemoryTranslationProvider()
    )

    val backupRepository = BackupRepository(
        context, database, musicRepository, lyricsRepository, settingsRepository
    )

    val equalizerController = EqualizerController()
    val visualizerController = VisualizerController()

    val playerController = PlayerController(context, appScope)

    fun start() {
        playerController.onTrackChanged = { previousId, listenedMs, newId ->
            appScope.launch {
                previousId?.let { id ->
                    musicRepository.getTrack(id)?.let { track ->
                        if (listenedMs > 5_000) historyRepository.record(track, listenedMs)
                    }
                }
                newId?.let { musicRepository.markPlayed(it) }
            }
        }
        playerController.connect()

        // Keep audio effects bound to the live ExoPlayer session.
        appScope.launch {
            PlaybackService.audioSessionIdFlow.collect { sessionId ->
                if (sessionId == 0) {
                    equalizerController.release()
                    visualizerController.stop()
                } else {
                    equalizerController.attach(sessionId)
                    val settings = settingsRepository.settings.first()
                    equalizerController.setEnabled(settings.equalizerEnabled)
                    if (settings.equalizerBands.isNotEmpty()) {
                        equalizerController.applyLevels(settings.equalizerBands)
                    }
                    equalizerController.setBassBoost(settings.bassBoost)
                    equalizerController.setVirtualizer(settings.virtualizer)
                }
            }
        }

        // Persist checkpoints at a low frequency and flush on pause or track changes.
        appScope.launch {
            var lastTrackId: String? = null
            var lastSavedPosition = 0L
            var lastObservedPosition = 0L
            var wasPlaying = false
            playerController.progress.collect { progress ->
                val id = progress.currentTrackId
                if (id != lastTrackId) {
                    if (lastTrackId != null && lastObservedPosition > 0 &&
                        lastObservedPosition != lastSavedPosition
                    ) {
                        musicRepository.savePosition(lastTrackId!!, lastObservedPosition)
                    }
                    lastTrackId = id
                    lastSavedPosition = -1L
                }

                if (id != null && progress.positionMs > 0) {
                    val shouldSave = lastSavedPosition < 0L ||
                        (progress.isPlaying && progress.positionMs - lastSavedPosition >= 5_000L) ||
                        (wasPlaying && !progress.isPlaying)
                    if (shouldSave) {
                        musicRepository.savePosition(id, progress.positionMs)
                        lastSavedPosition = progress.positionMs
                    }
                }

                lastObservedPosition = progress.positionMs
                wasPlaying = progress.isPlaying
            }
        }

        appScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.providersInitialised) {
                settingsRepository.setEnabledProviders(defaultEnabledProviders)
            }
        }
    }
}

class VexMusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
