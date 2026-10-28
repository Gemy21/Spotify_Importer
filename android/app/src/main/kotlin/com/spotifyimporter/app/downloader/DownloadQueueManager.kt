package com.spotifyimporter.app.downloader

import android.content.Context
import android.util.Log
import com.spotifyimporter.app.data.LyricsRepository
import com.spotifyimporter.app.data.SpotifyRepository
import com.spotifyimporter.app.model.DownloadSettings
import com.spotifyimporter.app.model.DownloadStatus
import com.spotifyimporter.app.model.Track
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the download queue. A single instance is shared by the activity and the
 * foreground service so progress survives configuration changes and backgrounding.
 */
class DownloadQueueManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val downloader = YtDlpDownloader(appContext)
    private val spotifyRepo = SpotifyRepository()
    private val lyricsRepo = LyricsRepository()
    private val saver = MediaStoreSaver(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var lastNotificationAt = 0L

    private val _tracksState = MutableStateFlow<List<Track>>(emptyList())
    val tracksState: StateFlow<List<Track>> = _tracksState.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _overallProgress = MutableStateFlow(0)
    val overallProgress: StateFlow<Int> = _overallProgress.asStateFlow()

    fun setTracks(tracks: List<Track>) {
        _tracksState.value = tracks
        _overallProgress.value = 0
    }

    fun toggleSelectTrack(uid: String) {
        _tracksState.value = _tracksState.value.map {
            if (it.uid == uid) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAll(select: Boolean) {
        _tracksState.value = _tracksState.value.map { it.copy(isSelected = select) }
    }

    fun startBatchDownload(settings: DownloadSettings) {
        if (_isDownloading.value) return

        val selected = _tracksState.value.filter {
            it.isSelected && it.status != DownloadStatus.COMPLETED
        }
        if (selected.isEmpty()) return

        _isDownloading.value = true
        _overallProgress.value = 0
        DownloadNotifications.update(
            "Downloading ${selected.size} tracks",
            "Preparing download…",
            0,
            true
        )
        DownloadService.start(appContext)

        downloadJob = scope.launch {
            try {
                runBatch(selected, settings)
            } catch (e: CancellationException) {
                Log.d(TAG, "Download batch cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download batch failed", e)
            } finally {
                _isDownloading.value = false
                DownloadService.stop(appContext)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        _tracksState.value.filter { it.status.isActive() }.forEach { track ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(track.uid) }
            updateTrack(track.uid) {
                it.copy(status = DownloadStatus.IDLE, progress = 0)
            }
        }
        DownloadNotifications.update(
            "Download cancelled",
            "The current batch was stopped",
            0,
            false
        )
    }

    fun retryFailed(settings: DownloadSettings) {
        if (_isDownloading.value) return
        if (_tracksState.value.none { it.status == DownloadStatus.ERROR }) return

        _tracksState.value = _tracksState.value.map {
            if (it.status == DownloadStatus.ERROR) {
                it.copy(
                    status = DownloadStatus.IDLE,
                    progress = 0,
                    errorMessage = null,
                    isSelected = true
                )
            } else {
                it
            }
        }
        startBatchDownload(settings)
    }

    /** Updates the bundled yt-dlp binary. Call only while no batch is running. */
    suspend fun updateDownloadEngine(): String = downloader.updateEngine()

    private suspend fun runBatch(selected: List<Track>, settings: DownloadSettings) {
        var finished = 0

        for (track in selected) {
            currentCoroutineContext().ensureActive()

            updateTrack(track.uid) {
                it.copy(status = DownloadStatus.SEARCHING, progress = 2, errorMessage = null)
            }
            notifyProgress(finished, 0, selected.size, track)

            try {
                var coverUrl = track.coverUrl
                if (coverUrl.isBlank()) {
                    coverUrl = spotifyRepo.resolveRealCoverUrl(track.artist, track.name)
                }

                var lyrics: String? = null
                var syncedLyrics: String? = null
                if (settings.embedLyrics || settings.saveLrcFile) {
                    val result = lyricsRepo.fetchLyrics(track.artist, track.name)
                    syncedLyrics = result.syncedLyrics
                    lyrics = result.plainLyrics ?: syncedLyrics
                }

                updateTrack(track.uid) {
                    it.copy(status = DownloadStatus.DOWNLOADING, progress = 10)
                }

                val downloaded = downloader.downloadTrack(
                    processId = track.uid,
                    artist = track.artist,
                    title = track.name,
                    album = track.album,
                    coverUrl = coverUrl.ifBlank { null },
                    lyrics = lyrics,
                    syncedLyrics = syncedLyrics,
                    settings = settings
                ) { percent ->
                    val status = if (percent >= 95) {
                        DownloadStatus.TAGGING
                    } else {
                        DownloadStatus.DOWNLOADING
                    }
                    updateTrack(track.uid) { it.copy(status = status, progress = percent) }
                    notifyProgress(finished, percent, selected.size, track)
                }

                currentCoroutineContext().ensureActive()

                val saved = saver.saveAudio(
                    source = downloaded.file,
                    displayName = downloaded.displayName,
                    mimeType = downloaded.mimeType,
                    title = track.name,
                    artist = track.artist,
                    album = track.album
                )
                downloaded.file.delete()

                if (settings.saveLrcFile) {
                    val sidecar = syncedLyrics ?: lyrics
                    if (!sidecar.isNullOrBlank()) {
                        val extension = if (!syncedLyrics.isNullOrBlank()) ".lrc" else ".txt"
                        val sidecarName = downloaded.displayName
                            .substringBeforeLast('.', downloaded.displayName) + extension
                        saver.saveLyrics(sidecar, sidecarName)
                    }
                }

                finished++
                updateTrack(track.uid) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        savedName = saved.displayName,
                        savedUri = saved.uri.toString()
                    )
                }
                notifyProgress(finished, 0, selected.size, null)
            } catch (e: CancellationException) {
                updateTrack(track.uid) {
                    it.copy(status = DownloadStatus.IDLE, progress = 0)
                }
                throw e
            } catch (e: Exception) {
                // Cancelling destroys the yt-dlp process, which surfaces as a plain
                // exception. Re-check cancellation so cancelled tracks are not marked failed.
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Failed downloading ${track.name}", e)
                finished++
                updateTrack(track.uid) {
                    it.copy(
                        status = DownloadStatus.ERROR,
                        progress = 0,
                        errorMessage = e.localizedMessage ?: "Download failed"
                    )
                }
                notifyProgress(finished, 0, selected.size, null)
            }
        }

        DownloadNotifications.update(
            "Download complete",
            "$finished of ${selected.size} tracks processed",
            100,
            false
        )
    }

    private fun notifyProgress(finished: Int, currentPercent: Int, total: Int, track: Track?) {
        val overall = (((finished + currentPercent / 100f) / total) * 100)
            .toInt()
            .coerceIn(0, 100)
        _overallProgress.value = overall

        val now = System.currentTimeMillis()
        if (track != null && currentPercent < 100 && now - lastNotificationAt < 700) return
        lastNotificationAt = now

        val text = if (track != null) {
            "${track.artist} — ${track.name}"
        } else {
            "$finished of $total tracks"
        }
        DownloadNotifications.update(
            "Downloading $finished/$total",
            text,
            overall,
            false
        )
    }

    private fun updateTrack(uid: String, transform: (Track) -> Track) {
        _tracksState.value = _tracksState.value.map {
            if (it.uid == uid) transform(it) else it
        }
    }

    private fun DownloadStatus.isActive(): Boolean {
        return this == DownloadStatus.QUEUED ||
            this == DownloadStatus.SEARCHING ||
            this == DownloadStatus.DOWNLOADING ||
            this == DownloadStatus.TAGGING
    }

    companion object {
        private const val TAG = "DownloadQueueManager"

        @Volatile
        private var instance: DownloadQueueManager? = null

        fun getInstance(context: Context): DownloadQueueManager {
            return instance ?: synchronized(this) {
                instance ?: DownloadQueueManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
