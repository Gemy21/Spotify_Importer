package com.spotifyimporter.app.downloader

import android.content.Context
import android.util.Log
import com.spotifyimporter.app.model.AudioFormat
import com.spotifyimporter.app.model.AudioQuality
import com.spotifyimporter.app.model.AudioSource
import com.spotifyimporter.app.model.DownloadSettings
import com.yausername.youtubedl.YoutubeDL
import com.yausername.youtubedl.YoutubeDLRequest
import java.io.File

class YtDlpDownloader(private val context: Context) {

    private val tagger = AudioTagger()

    fun downloadTrack(
        artist: String,
        title: String,
        album: String,
        coverUrl: String?,
        lyrics: String?,
        syncedLyrics: String?,
        settings: DownloadSettings,
        targetDir: File,
        onProgress: (Int) -> Unit
    ): File {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()

        // Construct search query or URL
        val searchTarget = if (settings.audioSource == AudioSource.SOUNDCLOUD) {
            "scsearch1:$cleanArtist - $cleanTitle"
        } else {
            "ytsearch1:$cleanArtist - $cleanTitle"
        }

        val tempFileName = "dl_${System.currentTimeMillis()}"
        val tempFile = File(context.cacheDir, "$tempFileName.${settings.audioFormat.extension}")
        val tempOutputTemplate = File(context.cacheDir, "$tempFileName.%(ext)s").absolutePath

        val request = YoutubeDLRequest(searchTarget)
        request.addOption("-o", tempOutputTemplate)
        request.addOption("-x") // Extract audio
        request.addOption("--audio-format", settings.audioFormat.extension)
        request.addOption("--audio-quality", settings.audioQuality.value)
        request.addOption("--no-playlist")

        Log.d("YtDlpDownloader", "Executing yt-dlp request for: $searchTarget")

        YoutubeDL.getInstance().execute(request) { progress, _, _ ->
            onProgress(progress.toInt().coerceIn(0, 99))
        }

        // Locate downloaded temp file
        var downloadedTempFile = tempFile
        if (!downloadedTempFile.exists()) {
            val foundFiles = context.cacheDir.listFiles { _, name -> name.startsWith(tempFileName) }
            if (foundFiles != null && foundFiles.isNotEmpty()) {
                downloadedTempFile = foundFiles.first()
            } else {
                throw Exception("Downloaded file not found in cache")
            }
        }

        // Prepare destination directory and final file name
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val sanitizedTitle = "$cleanArtist - $cleanTitle".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val finalAudioFile = File(targetDir, "$sanitizedTitle.${settings.audioFormat.extension}")

        // Apply ID3 Tagging for MP3
        if (settings.audioFormat == AudioFormat.MP3) {
            tagger.tagMp3File(
                inputFile = downloadedTempFile,
                outputFile = finalAudioFile,
                title = cleanTitle,
                artist = cleanArtist,
                album = album,
                coverUrl = coverUrl,
                lyrics = lyrics ?: syncedLyrics
            )
            downloadedTempFile.delete()
        } else {
            // Move file for M4A/OPUS
            downloadedTempFile.copyTo(finalAudioFile, overwrite = true)
            downloadedTempFile.delete()
        }

        // Save LRC or TXT lyrics file if requested
        if (settings.saveLrcFile) {
            try {
                if (settings.embedLyrics && !syncedLyrics.isNullOrEmpty()) {
                    val lrcFile = File(targetDir, "$sanitizedTitle.lrc")
                    lrcFile.writeText(syncedLyrics)
                } else if (!lyrics.isNullOrEmpty()) {
                    val txtFile = File(targetDir, "$sanitizedTitle.txt")
                    txtFile.writeText(lyrics)
                }
            } catch (e: Exception) {
                Log.e("YtDlpDownloader", "Failed to save lyrics file", e)
            }
        }

        onProgress(100)
        return finalAudioFile
    }
}
