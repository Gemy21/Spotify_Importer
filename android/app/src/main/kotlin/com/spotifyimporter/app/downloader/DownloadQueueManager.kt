package com.spotifyimporter.app.downloader

import android.content.Context
import android.os.Environment
import android.util.Log
import com.spotifyimporter.app.data.LyricsRepository
import com.spotifyimporter.app.data.SpotifyRepository
import com.spotifyimporter.app.model.DownloadSettings
import com.spotifyimporter.app.model.DownloadStatus
import com.spotifyimporter.app.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class DownloadQueueManager(private val context: Context) {

    private val downloader = YtDlpDownloader(context)
    private val spotifyRepo = SpotifyRepository()
    private val lyricsRepo = LyricsRepository()

    private val _tracksState = MutableStateFlow<List<Track>>(emptyList())
    val tracksState: StateFlow<List<Track>> = _tracksState.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun setTracks(tracks: List<Track>) {
        _tracksState.value = tracks
    }

    fun toggleSelectTrack(trackId: String) {
        _tracksState.value = _tracksState.value.map {
            if (it.id == trackId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAll(select: Boolean) {
        _tracksState.value = _tracksState.value.map { it.copy(isSelected = select) }
    }

    fun startBatchDownload(settings: DownloadSettings) {
        if (_isDownloading.value) return
        _isDownloading.value = true

        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SpotifyImporter"
        )
        if (!musicDir.exists()) musicDir.mkdirs()

        CoroutineScope(Dispatchers.IO).launch {
            val selected = _tracksState.value.filter { it.isSelected && it.status != DownloadStatus.COMPLETED }

            for (track in selected) {
                updateTrack(track.id) { it.copy(status = DownloadStatus.SEARCHING, progress = 5, errorMessage = null) }

                try {
                    // Resolve high-res cover art if missing
                    var coverUrl = track.coverUrl
                    if (coverUrl.isEmpty()) {
                        coverUrl = spotifyRepo.resolveRealCoverUrl(track.artist, track.name)
                    }

                    // Fetch lyrics if requested
                    var lyrics: String? = null
                    var syncedLyrics: String? = null
                    if (settings.embedLyrics || settings.saveLrcFile) {
                        val lyricsRes = lyricsRepo.fetchLyrics(track.artist, track.name)
                        syncedLyrics = lyricsRes.syncedLyrics
                        lyrics = lyricsRes.plainLyrics ?: syncedLyrics
                    }

                    updateTrack(track.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = 15) }

                    // Execute download & tagging
                    downloader.downloadTrack(
                        artist = track.artist,
                        title = track.name,
                        album = track.album,
                        coverUrl = coverUrl.ifEmpty { null },
                        lyrics = lyrics,
                        syncedLyrics = syncedLyrics,
                        settings = settings,
                        targetDir = musicDir,
                        onProgress = { pct ->
                            val status = if (pct >= 95) DownloadStatus.TAGGING else DownloadStatus.DOWNLOADING
                            updateTrack(track.id) { it.copy(status = status, progress = pct) }
                        }
                    )

                    updateTrack(track.id) { it.copy(status = DownloadStatus.COMPLETED, progress = 100) }
                } catch (e: Exception) {
                    Log.e("DownloadQueueManager", "Failed downloading ${track.name}", e)
                    updateTrack(track.id) {
                        it.copy(
                            status = DownloadStatus.ERROR,
                            errorMessage = e.localizedMessage ?: "Download failed"
                        )
                    }
                }
            }

            _isDownloading.value = false
        }
    }

    private fun updateTrack(id: String, transform: (Track) -> Track) {
        _tracksState.value = _tracksState.value.map {
            if (it.id == id) transform(it) else it
        }
    }
}
