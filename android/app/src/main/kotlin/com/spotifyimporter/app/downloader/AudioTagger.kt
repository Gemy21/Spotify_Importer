package com.spotifyimporter.app.downloader

import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class AudioTagger {

    private val client = OkHttpClient.Builder().build()

    fun tagMp3File(
        inputFile: File,
        outputFile: File,
        title: String,
        artist: String,
        album: String,
        coverUrl: String?,
        lyrics: String?
    ) {
        try {
            val mp3file = Mp3File(inputFile.absolutePath)
            val tag = if (mp3file.hasId3v2Tag()) mp3file.id3v2Tag else ID3v24Tag()

            tag.title = title
            tag.artist = artist
            tag.album = album

            if (!lyrics.isNullOrEmpty()) {
                tag.lyrics = lyrics
            }

            // Fetch and embed cover art image
            if (!coverUrl.isNullOrEmpty()) {
                try {
                    val req = Request.Builder().url(coverUrl).build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val imageBytes = res.body?.bytes()
                        if (imageBytes != null && imageBytes.isNotEmpty()) {
                            val mimeType = if (coverUrl.contains(".png", ignoreCase = true)) "image/png" else "image/jpeg"
                            tag.setAlbumImage(imageBytes, mimeType)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            mp3file.id3v2Tag = tag
            mp3file.save(outputFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            // If tagging fails, copy raw file so user still gets audio
            try {
                if (inputFile.exists() && inputFile.absolutePath != outputFile.absolutePath) {
                    inputFile.copyTo(outputFile, overwrite = true)
                }
            } catch (_: Exception) {}
        }
    }
}
