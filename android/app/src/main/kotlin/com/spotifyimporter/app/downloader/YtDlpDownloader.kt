package com.spotifyimporter.app.downloader

import android.content.Context
import android.util.Log
import com.spotifyimporter.app.model.AudioFormat
import com.spotifyimporter.app.model.AudioSource
import com.spotifyimporter.app.model.DownloadSettings
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A finished, tagged audio file waiting to be published to the Music library. */
data class DownloadedAudio(
    val file: File,
    val displayName: String,
    val mimeType: String
)

/**
 * Wraps yt-dlp + FFmpeg. Produces a fully tagged file in the app cache; the caller is
 * responsible for publishing it (see [MediaStoreSaver]) and deleting the temp file.
 */
class YtDlpDownloader(private val context: Context) {

    private val tagger = AudioTagger()

    @Volatile
    private var engineReady = false

    fun downloadTrack(
        processId: String,
        artist: String,
        title: String,
        album: String,
        coverUrl: String?,
        lyrics: String?,
        syncedLyrics: String?,
        settings: DownloadSettings,
        onProgress: (Int) -> Unit
    ): DownloadedAudio {
        ensureEngineReady()

        val cleanArtist = artist.trim().ifBlank { "Unknown Artist" }
        val cleanTitle = title.trim()

        val searchTarget = if (settings.audioSource == AudioSource.SOUNDCLOUD) {
            "scsearch1:$cleanArtist - $cleanTitle"
        } else {
            "ytsearch1:$cleanArtist - $cleanTitle"
        }

        val token = "${System.currentTimeMillis()}_${processId.hashCode()}"
        val outputTemplate = File(context.cacheDir, "dl_$token.%(ext)s").absolutePath

        val request = YoutubeDLRequest(searchTarget)
        request.addOption("-o", outputTemplate)
        request.addOption("-x")
        request.addOption("--audio-format", settings.audioFormat.extension)
        request.addOption("--audio-quality", settings.audioQuality.value)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--retries", "3")
        request.addOption("--socket-timeout", "30")
        request.addOption("--no-warnings")

        Log.d(TAG, "Starting yt-dlp for: $searchTarget")
        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
            onProgress(progress.toInt().coerceIn(0, 99))
        }

        val downloaded = findDownloadedFile(token, settings.audioFormat.extension)
            ?: throw IllegalStateException("yt-dlp finished but produced no audio file")

        val sanitizedName = sanitize("$cleanArtist - $cleanTitle")
        val extension = settings.audioFormat.extension
        val displayName = "$sanitizedName.$extension"
        val taggedFile = File(context.cacheDir, "tagged_$token.$extension")

        try {
            if (settings.audioFormat == AudioFormat.MP3) {
                tagger.tagMp3File(
                    inputFile = downloaded,
                    outputFile = taggedFile,
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = album,
                    coverUrl = coverUrl,
                    lyrics = if (settings.embedLyrics) lyrics ?: syncedLyrics else null
                )
            } else {
                downloaded.copyTo(taggedFile, overwrite = true)
            }
        } finally {
            downloaded.delete()
        }

        if (!taggedFile.exists() || taggedFile.length() == 0L) {
            throw IllegalStateException("Tagging produced an empty file")
        }

        onProgress(100)
        return DownloadedAudio(
            file = taggedFile,
            displayName = displayName,
            mimeType = settings.audioFormat.mimeType
        )
    }

    @Synchronized
    private fun ensureEngineReady() {
        if (engineReady) return
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        engineReady = true
    }

    /**
     * Downloads the latest yt-dlp release from GitHub. YouTube changes its player often;
     * this lets the app recover from extraction failures without shipping a new APK.
     */
    suspend fun updateEngine(): String = withContext(Dispatchers.IO) {
        try {
            ensureEngineReady()
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Download engine updated"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Download engine is already up to date"
                else -> "Download engine update finished"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Engine update failed", e)
            "Update failed: ${e.localizedMessage ?: "unknown error"}"
        }
    }

    private fun findDownloadedFile(token: String, extension: String): File? {
        val expected = File(context.cacheDir, "dl_$token.$extension")
        if (expected.exists() && expected.length() > 0) return expected

        return context.cacheDir
            .listFiles { file -> file.name.startsWith("dl_$token") && file.isFile && file.length() > 0 }
            ?.maxByOrNull { it.length() }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
            .ifBlank { "track" }
    }

    private companion object {
        const val TAG = "YtDlpDownloader"
    }
}
